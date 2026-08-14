package com.scythebill.birdlist.android.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DatePrecision { YEAR, MONTH, DAY }

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayName: String,
    val type: String?,
    val parentId: String?,
    val latitude: Double?,
    val longitude: Double?,
)

@Entity(
    tableName = "location_ancestors",
    primaryKeys = ["locationId", "ancestorId"],
    indices = [Index("ancestorId")],
)
data class LocationAncestorEntity(
    val locationId: String,
    val ancestorId: String,
    val depth: Int,
)

/** A pseudo-location (e.g. "ABA Region") from [com.scythebill.birdlist.model.query.SyntheticLocations]. */
@Entity(tableName = "synthetic_locations")
data class SyntheticLocationEntity(
    @PrimaryKey val id: String,
    val displayName: String,
)

/** Flattened membership: real location ids that fall within a synthetic location. */
@Entity(
    tableName = "synthetic_location_members",
    primaryKeys = ["syntheticId", "locationId"],
    indices = [Index("locationId")],
)
data class SyntheticLocationMemberEntity(
    val syntheticId: String,
    val locationId: String,
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val locationId: String?,
    val startEpochDay: Long,
    val endEpochDay: Long,
)

@Entity(
    tableName = "sightings",
    indices = [Index("locationId"), Index("epochDay"), Index("tripId"), Index("photographed")],
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: String?,
    val epochDay: Long?,
    val datePrecision: DatePrecision?,
    val tripId: String?,
    val sightingStatus: String?,
    val breedingCode: String?,
    val male: Boolean,
    val female: Boolean,
    val immature: Boolean,
    val adult: Boolean,
    val photographed: Boolean,
    val heardOnly: Boolean,
    val approximateNumber: String?,
    val firstForTaxon: Boolean,
    /** Base taxonomy ID the sighting's taxon was resolved against (TaxonUtils.getBaseTaxonomy). */
    val taxonomyId: String,
    /**
     * SightingTaxon.Resolved.getType() after raising every component to
     * species level: SINGLE, SP, or HYBRID. A sp./hybrid of subspecies of
     * the same species collapses to SINGLE here.
     */
    val raisedTaxonType: String,
    /** Sorted, comma-joined candidate species ids; null when raisedTaxonType is SINGLE. */
    val raisedGroupKey: String?,
    /** Joined display name (e.g. "Eastern/Western Warbling-Vireo"); null when SINGLE. */
    val raisedDisplayName: String?,
)

@Entity(tableName = "sighting_details")
data class SightingDetailsEntity(
    @PrimaryKey val sightingId: Long,
    val description: String?,
    val photoUrisJson: String?,
)

@Entity(
    tableName = "sighting_taxa",
    primaryKeys = ["sightingId", "taxonId"],
    indices = [Index("taxonId")],
)
data class SightingTaxonEntity(val sightingId: Long, val taxonId: String)

@Entity(
    tableName = "sighting_users",
    primaryKeys = ["sightingId", "userId"],
    indices = [Index(value = ["userId", "sightingId"])],
)
data class SightingUserEntity(val sightingId: Long, val userId: String)

@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val sourceUri: String,
    val sourceSize: Long,
    val sourceLastModified: Long,
    val taxonomyVersion: String,
    val builtAtEpochMillis: Long,
    /** Bumped whenever the cache's row shape or population logic changes, to force a rebuild. */
    val cacheFormatVersion: Int,
    /**
     * Encoded [com.scythebill.birdlist.android.data.ExtendedTaxonomyDescriptor] list for any
     * taxonomies embedded in the source `.bsxm`, so a cache-hit relaunch can know they exist
     * (and show the "Select taxonomy" menu) without re-parsing the whole file. See
     * [encodeExtendedTaxonomyDescriptors]/[decodeExtendedTaxonomyDescriptors].
     */
    val extendedTaxonomyNamesJson: String? = null,

    /**
     * Encoded [com.scythebill.birdlist.android.data.UserDescriptor] list for the source `.bsxm`'s
     * `UserSet` (null if it has none), so a cache-hit relaunch can know whether the "User
     * preferences" menu entry should be shown without re-parsing the file. See
     * [encodeUserDescriptors]/[decodeUserDescriptors].
     */
    val userNamesJson: String? = null,
)

/**
 * Bump whenever [SightingCacheBuilder]'s output changes in a way that requires
 * discarding caches built by older app versions, even if the source file itself
 * hasn't changed (e.g. a new column needs populating, or a bug in how existing
 * columns were populated is fixed).
 */
const val CACHE_FORMAT_VERSION = 6

/** Row shape produced by [CacheDao.getSightingCountabilityRows]. */
data class SightingCountabilityRow(
    val taxonId: String,
    val sightingStatus: String?,
    val heardOnly: Boolean,
    val raisedTaxonType: String,
)

/** Row shape produced by [CacheDao.queryResults]'s dynamically-built SQL. */
data class QueryResultRow(
    val sightingId: Long,
    val locationId: String?,
    val epochDay: Long?,
    val datePrecision: DatePrecision?,
    val photographed: Boolean,
    val heardOnly: Boolean,
    val sightingStatus: String?,
    val photoUrisJson: String?,
    val taxonId: String,
    val raisedTaxonType: String,
    val raisedGroupKey: String?,
    val raisedDisplayName: String?,
)
