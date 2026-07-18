package com.scythebill.birdlist.android.cache

private val COUNTRY_ABBREVIATIONS = mapOf(
    "United States" to "USA",
    "Papua New Guinea" to "PNG",
)

/**
 * Builds a map from location id to a "Location name, State name, Country
 * name" display string, found by walking each location's parent chain for
 * the first state- and country-typed ancestor.
 */
fun buildLocationDisplayNames(locations: List<LocationEntity>): Map<String, String> {
    val byId = locations.associateBy { it.id }
    return locations.associate { it.id to hierarchicalDisplayName(it, byId) }
}

private fun hierarchicalDisplayName(
    location: LocationEntity,
    byId: Map<String, LocationEntity>,
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
        location.displayName,
        state?.displayName,
        country?.displayName?.let { COUNTRY_ABBREVIATIONS[it] ?: it },
    ).joinToString(", ")
}
