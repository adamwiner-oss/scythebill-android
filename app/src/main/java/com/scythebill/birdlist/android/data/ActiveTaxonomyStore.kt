package com.scythebill.birdlist.android.data

import com.scythebill.birdlist.android.di.AppContainer
import com.scythebill.birdlist.model.taxa.Taxonomy
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight id/name pair for an embedded extended taxonomy, cheap to persist and read
 * back without re-parsing the source `.bsxm` file. */
data class ExtendedTaxonomyDescriptor(val id: String, val name: String)

/**
 * Tracks which [Taxonomy] browsing/search/reports currently operate
 * against: the app-wide base (eBird/Clements) taxonomy by default, or one of
 * the "extended" taxonomies embedded in the currently loaded `.bsxm` file
 * (e.g. a mammal checklist), once selected via the hamburger menu.
 */
class ActiveTaxonomyStore(private val container: AppContainer) {
    private val _activeTaxonomy = MutableStateFlow<Taxonomy?>(null)
    val activeTaxonomy: StateFlow<Taxonomy?> = _activeTaxonomy.asStateFlow()

    private val _extendedTaxonomies = MutableStateFlow<List<Taxonomy>>(emptyList())
    val extendedTaxonomies: StateFlow<List<Taxonomy>> = _extendedTaxonomies.asStateFlow()

    /**
     * Known id/name pairs for embedded extended taxonomies, available immediately even on a
     * cache-hit relaunch (from cached metadata) before the actual [Taxonomy] objects have been
     * parsed. Drives whether the "Select taxonomy" menu item is shown.
     */
    private val _extendedTaxonomyDescriptors = MutableStateFlow<List<ExtendedTaxonomyDescriptor>>(emptyList())
    val extendedTaxonomyDescriptors: StateFlow<List<ExtendedTaxonomyDescriptor>> =
        _extendedTaxonomyDescriptors.asStateFlow()

    var baseTaxonomy: Taxonomy? = null
        private set
    private val indexedTaxonomies = Collections.newSetFromMap(java.util.IdentityHashMap<Taxonomy, Boolean>())

    /** Called once at startup after the base taxonomy finishes loading/indexing. */
    suspend fun setBaseTaxonomy(taxonomy: Taxonomy) {
        baseTaxonomy = taxonomy
        indexedTaxonomies += taxonomy
        if (_activeTaxonomy.value == null) {
            _activeTaxonomy.value = taxonomy
        }
    }

    /**
     * Called after each `.bsxm` load. Resets the active taxonomy back to
     * the base taxonomy and pre-builds search indexes for every extended
     * taxonomy so switching to one later is instant.
     */
    suspend fun setExtendedTaxonomies(taxonomies: List<Taxonomy>) {
        for (taxonomy in taxonomies) {
            ensureIndexed(taxonomy)
        }
        _extendedTaxonomies.value = taxonomies
        _extendedTaxonomyDescriptors.value = taxonomies.map {
            ExtendedTaxonomyDescriptor(it.getId(), it.getName())
        }
        baseTaxonomy?.let { _activeTaxonomy.value = it }
    }

    /**
     * Cheap counterpart to [setExtendedTaxonomies] for a cache-hit relaunch: makes the
     * "Select taxonomy" menu entry available immediately from cached (id, name) metadata,
     * without parsing the source file. The real [Taxonomy] objects are loaded lazily later
     * (see `FileLoadViewModel.ensureExtendedTaxonomiesLoaded`), which then calls
     * [setExtendedTaxonomies] to replace this placeholder state.
     */
    fun setKnownExtendedTaxonomyDescriptors(descriptors: List<ExtendedTaxonomyDescriptor>) {
        _extendedTaxonomyDescriptors.value = descriptors
    }

    suspend fun selectTaxonomy(taxonomy: Taxonomy) {
        ensureIndexed(taxonomy)
        _activeTaxonomy.value = taxonomy
    }

    private suspend fun ensureIndexed(taxonomy: Taxonomy) {
        if (indexedTaxonomies.add(taxonomy)) {
            container.buildSpeciesIndexes(taxonomy)
        }
    }
}
