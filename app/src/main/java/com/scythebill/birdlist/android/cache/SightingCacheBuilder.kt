package com.scythebill.birdlist.android.cache

import com.scythebill.birdlist.model.date.PartialDate
import com.scythebill.birdlist.model.date.PartialField
import com.scythebill.birdlist.model.query.SyntheticLocations
import com.scythebill.birdlist.model.sighting.Location
import com.scythebill.birdlist.model.sighting.ReportSet
import com.scythebill.birdlist.model.sighting.Sighting
import com.scythebill.birdlist.model.sighting.SightingTaxon
import com.scythebill.birdlist.model.sighting.Trips
import com.scythebill.birdlist.model.taxa.Taxon
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stage 2 of the two-stage load: flatten an in-memory [ReportSet] into the
 * persistent Room cache so later query/browse screens run indexed SQL
 * instead of re-scanning [ReportSet.getSightings] on every filter change.
 *
 * Named to avoid a simple-name clash with Guava's `CacheBuilder`, already
 * imported by model code.
 */
class SightingCacheBuilder(private val dao: CacheDao) {

    suspend fun rebuild(
        reportSet: ReportSet,
        sourceUri: String,
        sourceSize: Long,
        sourceLastModified: Long,
        taxonomyVersion: String,
    ) = withContext(Dispatchers.Default) {
        dao.clearAll()

        val allLocations = mutableListOf<Location>()
        collectLocations(reportSet.locations.rootLocations(), allLocations)
        allLocations += reportSet.locations.restoredLocations().values

        dao.insertLocations(
            allLocations.map { loc ->
                LocationEntity(
                    id = loc.getId()!!,
                    name = loc.getModelName(),
                    displayName = loc.getDisplayName(),
                    type = loc.getType()?.name,
                    parentId = loc.getParent()?.getId(),
                    latitude = loc.getLatLong()?.latitudeAsDouble(),
                    longitude = loc.getLatLong()?.longitudeAsDouble(),
                )
            }
        )
        dao.insertLocationAncestors(buildAncestorClosure(allLocations))

        val syntheticLocations = SyntheticLocations(reportSet.locations)
        val allLocationIds = allLocations.map { it.getId()!! }
        val syntheticEntities = mutableListOf<SyntheticLocationEntity>()
        val syntheticMemberEntities = mutableListOf<SyntheticLocationMemberEntity>()
        for (synthetic in syntheticLocations.locations()) {
            val syntheticId = synthetic.getId()!!
            syntheticEntities += SyntheticLocationEntity(syntheticId, synthetic.getDisplayName())
            val predicate = synthetic.isInLocationPredicate()
            for (locationId in allLocationIds) {
                if (predicate.apply(locationId)) {
                    syntheticMemberEntities += SyntheticLocationMemberEntity(syntheticId, locationId)
                }
            }
        }
        dao.insertSyntheticLocations(syntheticEntities)
        dao.insertSyntheticLocationMembers(syntheticMemberEntities)

        dao.insertTrips(
            reportSet.trips.allTrips().map { trip ->
                TripEntity(
                    id = trip.id(),
                    name = trip.name(),
                    locationId = trip.locationId(),
                    startEpochDay = epochDayOf(trip.startDate()),
                    endEpochDay = epochDayOf(trip.endDate()),
                    displayName = Trips.nameWithDate(trip, Locale.getDefault()),
                )
            }
        )

        data class Prepared(val sighting: Sighting, val epochDay: Long?, val precision: DatePrecision?)

        val prepared = reportSet.sightings.map { s ->
            val (day, precision) = datePartsOf(s)
            Prepared(s, day, precision)
        }

        // Precompute "lifer" status: no existing implementation to reuse
        // (QueryDefinition.QueryAnnotation.LIFER is declared but never
        // actually computed anywhere in the model code), so this groups
        // sightings by taxon id and marks the earliest-dated one per group.
        val firstForTaxon = HashSet<Sighting>()
        prepared
            .groupBy { it.sighting.taxon.getIds().toSet() }
            .values
            .forEach { group ->
                group.filter { it.epochDay != null }
                    .minByOrNull { it.epochDay!! }
                    ?.let { firstForTaxon += it.sighting }
            }

        val sightingEntities = prepared.map { p ->
            val info = p.sighting.sightingInfo
            val resolved = p.sighting.taxon.resolve(p.sighting.taxonomy)!!
                .resolveParentOfType(Taxon.Type.species)
            val raisedType = resolved.getType()
            val (groupKey, displayName) = if (raisedType == SightingTaxon.Type.SINGLE) {
                null to null
            } else {
                resolved.getTaxa().mapNotNull { taxon: Taxon -> taxon.getId() }.sorted().joinToString(",") to
                    resolved.getPreferredSingleName()
            }
            SightingEntity(
                locationId = p.sighting.locationId,
                epochDay = p.epochDay,
                datePrecision = p.precision,
                tripId = p.sighting.trip?.id(),
                sightingStatus = info?.sightingStatus?.name,
                breedingCode = info?.breedingBirdCode?.name,
                male = info?.isMale ?: false,
                female = info?.isFemale ?: false,
                immature = info?.isImmature ?: false,
                adult = info?.isAdult ?: false,
                photographed = info?.isPhotographed ?: false,
                heardOnly = info?.isHeardOnly ?: false,
                approximateNumber = info?.number?.toString(),
                firstForTaxon = p.sighting in firstForTaxon,
                taxonomyId = p.sighting.taxonomy.getId(),
                raisedTaxonType = raisedType.name,
                raisedGroupKey = groupKey,
                raisedDisplayName = displayName,
            )
        }
        val ids = dao.insertSightings(sightingEntities)

        val detailEntities = mutableListOf<SightingDetailsEntity>()
        val taxonRows = mutableListOf<SightingTaxonEntity>()
        val userRows = mutableListOf<SightingUserEntity>()
        prepared.forEachIndexed { idx, p ->
            val id = ids[idx]
            val info = p.sighting.sightingInfo
            if (info != null && (!info.description.isNullOrEmpty() || info.photos.isNotEmpty())) {
                detailEntities += SightingDetailsEntity(
                    sightingId = id,
                    description = info.description,
                    photoUrisJson = encodePhotos(info.photos.map { it.uri.toString() }),
                )
            }
            p.sighting.taxon.getIds().forEach { taxonId -> taxonRows += SightingTaxonEntity(id, taxonId) }
            info?.users?.forEach { user -> userRows += SightingUserEntity(id, user.id()) }
        }
        dao.insertSightingDetails(detailEntities)
        dao.insertSightingTaxa(taxonRows)
        dao.insertSightingUsers(userRows)

        dao.setMetadata(
            CacheMetadataEntity(
                sourceUri = sourceUri,
                sourceSize = sourceSize,
                sourceLastModified = sourceLastModified,
                taxonomyVersion = taxonomyVersion,
                builtAtEpochMillis = System.currentTimeMillis(),
                cacheFormatVersion = CACHE_FORMAT_VERSION,
                extendedTaxonomyNamesJson = encodeExtendedTaxonomyDescriptors(reportSet.extendedTaxonomies()),
                userNamesJson = encodeUserDescriptors(reportSet.getUserSet()),
            )
        )
    }

    private fun collectLocations(roots: Collection<Location>, out: MutableList<Location>) {
        for (loc in roots) {
            out += loc
            collectLocations(loc.contents(), out)
        }
    }

    private fun buildAncestorClosure(locations: List<Location>): List<LocationAncestorEntity> {
        val result = mutableListOf<LocationAncestorEntity>()
        for (loc in locations) {
            var cur: Location? = loc
            var depth = 0
            while (cur != null) {
                result += LocationAncestorEntity(loc.getId()!!, cur.getId()!!, depth)
                cur = cur.getParent()
                depth++
            }
        }
        return result
    }

    private fun encodePhotos(uris: List<String>): String? {
        if (uris.isEmpty()) return null
        return uris.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
    }

    private fun epochDayOf(partial: PartialDate?): Long =
        partial?.let { datePartsOfPartial(it).first } ?: 0L

    private fun datePartsOf(sighting: Sighting): Pair<Long?, DatePrecision?> {
        val partial = sighting.storedDateAsPartial
            ?: sighting.singleDateAsPartial
            ?: sighting.trip?.startDate()
            ?: return null to null
        return datePartsOfPartial(partial).let { it.first to it.second }
    }

    private fun datePartsOfPartial(partial: PartialDate): Pair<Long, DatePrecision> {
        val hasDay = partial.isSupported(PartialField.DAY)
        val hasMonth = partial.isSupported(PartialField.MONTH)
        val year = partial.get(PartialField.YEAR)
        val month = if (hasMonth) partial.get(PartialField.MONTH) else 1
        val day = if (hasDay) partial.get(PartialField.DAY) else 1
        val epochDay = java.time.LocalDate.of(year, month, day).toEpochDay()
        val precision = if (hasDay) DatePrecision.DAY else if (hasMonth) DatePrecision.MONTH else DatePrecision.YEAR
        return epochDay to precision
    }
}
