package com.scythebill.birdlist.android.ui.search

import com.scythebill.birdlist.android.ui.common.formattedLabel
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import com.scythebill.birdlist.model.util.AlternateName
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

/**
 * A single step in a species search: either a regular name indexer, or an
 * alternate-name indexer whose matches must be qualified with the alternate
 * name that matched (see [IndexerPanel][com.scythebill.birdlist.ui.components.IndexerPanel]
 * on desktop, which formats these as "Official Name (Alternate Name)").
 */
sealed interface SpeciesIndexerStep {
    data class Names(val indexer: Indexer<String>) : SpeciesIndexerStep
    data class AlternateNames(val indexer: Indexer<AlternateName<String>>) : SpeciesIndexerStep
}

/**
 * Ordered search steps: [speciesIndexerGroups], then the alternate common
 * and scientific indexers.
 */
fun speciesIndexerSearchSteps(
    taxonomy: Taxonomy,
    namesPreferences: NamesPreferences,
): List<SpeciesIndexerStep> {
    val regular = speciesIndexerGroups(taxonomy, namesPreferences)
        .map { SpeciesIndexerStep.Names(it) }
    val alternate = listOfNotNull(taxonomy.alternateCommonIndexer, taxonomy.alternateScientificIndexer)
        .map { SpeciesIndexerStep.AlternateNames(it) }
    return regular + alternate
}

/**
 * The taxon's name formatted per [NamesPreferences.scientificOrCommon]; if
 * [alternateName] is non-null (the taxon was found via an alternate-name
 * indexer), it's appended as "Official Name (Alternate Name)".
 */
fun taxonSearchLabel(
    taxon: Taxon,
    namesPreferences: NamesPreferences,
    alternateName: String? = null,
): String {
    val officialName = taxon.formattedLabel(namesPreferences.scientificOrCommon)
    return if (alternateName != null) "$officialName ($alternateName)" else officialName
}
