package com.scythebill.birdlist.android.ui.search

import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
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

/** "Common Name (Scientific name)", falling back to whatever name is available. */
fun taxonSearchLabel(taxon: Taxon): String {
    val scientific = TaxonUtils.getFullName(taxon) ?: taxon.getName()
    val common = taxon.getCommonName()
    return when {
        common != null && scientific != null -> "$common ($scientific)"
        else -> common ?: scientific ?: ""
    }
}
