package com.scythebill.birdlist.android.cache

/**
 * Builds a map from location id to a "Location name, State name, Country
 * name" display string, found by walking each location's parent chain for
 * the first state- and country-typed ancestor.
 */
fun buildLocationDisplayNames(locations: List<LocationEntity>): Map<String, String> {
    val byId = locations.associateBy { it.id }
    val byDisplayName = locations.groupBy { it.displayName }
    return locations.associate { it.id to hierarchicalDisplayName(it, byId, byDisplayName) }
}

private fun hierarchicalDisplayName(
    location: LocationEntity,
    byId: Map<String, LocationEntity>,
    byDisplayName: Map<String, List<LocationEntity>>,
): String {
    var state: LocationEntity? = null
    var country: LocationEntity? = null
    var current = location.parentId?.let { byId[it] }
    while (current != null) {
        if (state == null && current.type == "state") state = current
        if (country == null && current.type == "country") country = current
        current = current.parentId?.let { byId[it] }
    }
    return listOfNotNull(
        LocationIdToString.getString(byId, byDisplayName, location.id),
        state?.displayName,
        country?.let { c -> LocationIdToString.getString(byId, byDisplayName, c.id) },
    ).joinToString(", ")
}
