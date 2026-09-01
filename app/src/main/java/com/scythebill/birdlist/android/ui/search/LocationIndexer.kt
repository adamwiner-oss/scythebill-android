package com.scythebill.birdlist.android.ui.search

import com.scythebill.birdlist.android.cache.LocationEntity
import com.scythebill.birdlist.android.cache.SyntheticLocationEntity
import com.scythebill.birdlist.model.util.Indexer

private val ALTERNATE_INDEX_ENTRIES: Map<String, List<String>> = mapOf(
    "Mount" to listOf("Mt"),
    "Mountain" to listOf("Mt"),
    "Mt" to listOf("Mount"),
    "Fort" to listOf("Ft"),
    "Ft" to listOf("Fort"),
    "St" to listOf("Saint"),
    "Ste" to listOf("Sainte"),
    "Saint" to listOf("St"),
)

/** Coarse kind of location, used to order results within an index tier. */
enum class LocationKind {
    REGION,
    COUNTRY,
    SYNTHETIC,
    OTHER,
}

/**
 * A typeahead index over locations, built as an ordered series of [Indexer]
 * tiers of increasing complexity. [find] queries every tier and appends
 * their results in tier order, so a query is led by matches from the
 * simplest strategy - e.g. "US" leads with "United States" matching by its
 * own name, ahead of a later tier that would also match "Uruguay" via its
 * parent's name ("Uruguay South America"). Within a tier, results are
 * sorted by [LocationKind] - regions first, then countries, then synthetic
 * locations, then everything else.
 */
class LocationIndexer {
    private val simple = indexer()
    private val withParent = indexer()
    private val tiers = listOf(simple, withParent)
    private val kindById = HashMap<String, LocationKind>()

    private fun indexer() = Indexer<String>().apply { setAlternateIndexEntries(ALTERNATE_INDEX_ENTRIES) }

    fun find(query: String): Collection<String> {
        val results = LinkedHashSet<String>()
        for (tier in tiers) {
            results.addAll(tier.find(query).sortedBy { kindOf(it).ordinal })
        }
        return results
    }

    private fun kindOf(id: String) = kindById[id] ?: LocationKind.OTHER

    /** Indexes [name] under the simplest tier - a location's own name(s). */
    fun addSimple(name: String, id: String, kind: LocationKind = LocationKind.OTHER) {
        simple.add(name, id)
        kindById[id] = kind
    }

    /** Indexes [name] under a more complex tier - e.g. a name combined with its parent's. */
    fun addWithParent(name: String, id: String, kind: LocationKind = LocationKind.OTHER) {
        withParent.add(name, id)
        kindById[id] = kind
    }
}

/**
 * Builds a typeahead [LocationIndexer] over [locations], keyed by location
 * id - ported from desktop's `LocationIdToString.addToLocationIndex()`.
 */
fun buildLocationIndexer(locations: List<LocationEntity>): LocationIndexer {
    val index = LocationIndexer()
    val byId = locations.associateBy { it.id }
    for (loc in locations) {
        addToLocationIndex(index, loc, byId)
    }
    return index
}

/** Adds [syntheticLocations] (e.g. "ABA Region") to an existing location [index], keyed by id. */
fun addSyntheticLocationsToIndex(
    index: LocationIndexer,
    syntheticLocations: List<SyntheticLocationEntity>,
) {
    for (loc in syntheticLocations) {
        index.addSimple(loc.displayName, loc.id, LocationKind.SYNTHETIC)
    }
}

private fun kindOf(loc: LocationEntity): LocationKind = when (loc.type) {
    "region" -> LocationKind.REGION
    "country" -> LocationKind.COUNTRY
    else -> LocationKind.OTHER
}

private fun addToLocationIndex(
    index: LocationIndexer,
    loc: LocationEntity,
    byId: Map<String, LocationEntity>,
) {
    val kind = kindOf(loc)
    index.addSimple(loc.displayName, loc.id, kind)
    val sameDisplayName = loc.displayName == loc.name
    if (!sameDisplayName) {
        index.addSimple(loc.name, loc.id, kind)
    }
}
