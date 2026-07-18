package com.scythebill.birdlist.android.ui.query

import com.scythebill.birdlist.model.sighting.SightingInfo.SightingStatus
import com.scythebill.birdlist.model.taxa.Species
import com.scythebill.birdlist.model.taxa.Taxon

/**
 * Controls which sightings count toward a report's species total. Mirrors
 * desktop's QueryPreferences. No UI exists yet to change these defaults.
 */
data class QueryPreferences(
    val countIntroduced: Boolean = true,
    val countHeardOnly: Boolean = true,
    val countRestrained: Boolean = false,
    val countUndescribed: Boolean = false,
) {
    fun isCountable(sightingStatus: String?, heardOnly: Boolean, taxon: Taxon?): Boolean {
        if (sightingStatus != null && sightingStatus in NEVER_COUNTABLE_STATUSES) return false
        if (sightingStatus == SightingStatus.INTRODUCED.name && !countIntroduced) return false
        if (sightingStatus == SightingStatus.RESTRAINED.name && !countRestrained) return false
        if (heardOnly && !countHeardOnly) return false
        if (!countUndescribed && taxon?.getStatus() == Species.Status.UN) return false
        return true
    }

    companion object {
        val DEFAULT = QueryPreferences()

        private val NEVER_COUNTABLE_STATUSES: Set<String> = setOf(
            SightingStatus.INTRODUCED_NOT_ESTABLISHED,
            SightingStatus.ID_UNCERTAIN,
            SightingStatus.UNSATISFACTORY_VIEWS,
            SightingStatus.DEAD,
            SightingStatus.NOT_BY_ME,
            SightingStatus.SIGNS,
            SightingStatus.DOMESTIC,
        ).map { it.name }.toSet()
    }
}
