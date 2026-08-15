package com.scythebill.birdlist.android.cache

private val TYPE_LABELS = mapOf(
    "park" to "Park",
    "town" to "Town",
    "city" to "City",
    "county" to "County",
    "state" to "State/Province",
    "country" to "Country",
)

private val COUNTRY_ABBREVIATIONS = mapOf(
    "United States" to "USA",
    "Papua New Guinea" to "PNG",
)

/**
 * Ported from desktop's `LocationIdToString.getString(LocationSet, String)`
 * (non-verbose, standalone case only): disambiguates locations that share
 * a display name with another location, by appending either the location's
 * type or its parent's name.
 */
object LocationIdToString {
    fun getString(
        byId: Map<String, LocationEntity>,
        byDisplayName: Map<String, List<LocationEntity>>,
        id: String,
    ): String {
        val location = byId.getValue(id)
        val name = COUNTRY_ABBREVIATIONS[location.displayName] ?: location.displayName
        val sameName = byDisplayName[location.displayName] ?: listOf(location)
        if (sameName.size == 1) {
            return name
        }

        // See if the type is enough (e.g. "San Francisco" is both a city
        // and a county).
        val canBeDisambiguatedByType = sameName.none {
            it.id != location.id && it.type == location.type
        }

        return if (canBeDisambiguatedByType || location.parentId == null) {
            withType(location, name)
        } else {
            // Type isn't enough - use the parent name. Typically, this
            // will be sufficient.
            val parent = byId.getValue(location.parentId)
            "$name (${parent.displayName})"
        }
    }

    private fun withType(location: LocationEntity, name: String): String {
        val typeLabel = location.type?.let { TYPE_LABELS[it] } ?: return name
        return "$name ($typeLabel)"
    }
}
