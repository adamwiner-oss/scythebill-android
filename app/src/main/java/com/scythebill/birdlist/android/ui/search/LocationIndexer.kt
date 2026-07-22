package com.scythebill.birdlist.android.ui.search

import com.google.common.collect.ImmutableMultimap
import com.scythebill.birdlist.android.cache.LocationEntity
import com.scythebill.birdlist.android.cache.SyntheticLocationEntity
import com.scythebill.birdlist.model.util.Indexer

private val ALTERNATE_INDEX_ENTRIES: ImmutableMultimap<String, String> =
    ImmutableMultimap.builder<String, String>()
        .put("Mount", "Mt")
        .put("Mountain", "Mt")
        .put("Mt", "Mount")
        .put("Fort", "Ft")
        .put("Ft", "Fort")
        .put("St", "Saint")
        .put("Ste", "Sainte")
        .put("Saint", "St")
        .build()

/**
 * Builds a typeahead [Indexer] over [locations], keyed by location id —
 * ported from desktop's `LocationIdToString.addToLocationIndex()`. Indexes
 * each location's display and model names, plus the combo of each with its
 * parent's names, so a search like "Springfield Illinois" narrows to the
 * right one among same-named locations.
 */
fun buildLocationIndexer(locations: List<LocationEntity>): Indexer<String> {
    val index = Indexer<String>()
    index.setAlternateIndexEntries(ALTERNATE_INDEX_ENTRIES)
    val byId = locations.associateBy { it.id }
    for (loc in locations) {
        addToLocationIndex(index, loc, byId)
    }
    return index
}

/** Adds [syntheticLocations] (e.g. "ABA Region") to an existing location [index], keyed by id. */
fun addSyntheticLocationsToIndex(
    index: Indexer<String>,
    syntheticLocations: List<SyntheticLocationEntity>,
) {
    for (loc in syntheticLocations) {
        index.add(loc.displayName, loc.id)
    }
}

private fun addToLocationIndex(
    index: Indexer<String>,
    loc: LocationEntity,
    byId: Map<String, LocationEntity>,
) {
    index.add(loc.displayName, loc.id)
    val sameDisplayName = loc.displayName == loc.name
    if (!sameDisplayName) {
        index.add(loc.name, loc.id)
    }

    val parent = loc.parentId?.let { byId[it] }
    if (parent != null) {
        index.add("${loc.displayName} ${parent.displayName}", loc.id)
        if (parent.displayName != parent.name) {
            index.add("${loc.displayName} ${parent.name}", loc.id)
            if (!sameDisplayName) {
                index.add("${loc.name} ${parent.name}", loc.id)
            }
        }
    }
}
