package com.scythebill.birdlist.android.ui.search

import com.scythebill.birdlist.android.ui.common.formattedLabel
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import com.scythebill.birdlist.model.util.Indexer

/**
 * Ordered indexer groups to search, picked per
 * [NamesPreferences.scientificOrCommon] — ported from desktop's
 * `SpeciesIndexerPanelConfigurer` (naming-mode selection only; the Swing
 * wiring isn't ported).
 */
fun speciesIndexerGroups(
    taxonomy: Taxonomy,
    namesPreferences: NamesPreferences,
): List<Indexer<String>> {
    val scientific = listOfNotNull(taxonomy.scientificIndexer)
    val common = listOfNotNull(taxonomy.localizedCommonIndexer, taxonomy.commonIndexer)
    return when (namesPreferences.scientificOrCommon) {
        NamesPreferences.ScientificOrCommon.COMMON_FIRST -> common + scientific
        NamesPreferences.ScientificOrCommon.SCIENTIFIC_FIRST -> scientific + common
        NamesPreferences.ScientificOrCommon.SCIENTIFIC_ONLY -> scientific
        NamesPreferences.ScientificOrCommon.COMMON_ONLY -> common
    }
}

/** The taxon's name formatted per [NamesPreferences.scientificOrCommon]. */
fun taxonSearchLabel(taxon: Taxon, namesPreferences: NamesPreferences): String =
    taxon.formattedLabel(namesPreferences.scientificOrCommon)
