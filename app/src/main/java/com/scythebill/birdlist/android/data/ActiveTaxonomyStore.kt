package com.scythebill.birdlist.android.data

import com.scythebill.birdlist.android.di.AppContainer
import com.scythebill.birdlist.model.taxa.Taxonomy
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight id/name pair for an embedded extended taxonomy, cheap to persist and read
 * back without reparsing the source `.bsxm` file. */
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

    /** Extended taxonomies parsed so far this session, keyed by id, so switching back to one
     * already loaded (whether via the initial full parse or a later partial parse) is instant. */
    private val extendedTaxonomyCache = mutableMapOf<String, Taxonomy>()

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
        extendedTaxonomyCache.clear()
        for (taxonomy in taxonomies) {
            ensureIndexed(taxonomy)
            extendedTaxonomyCache[taxonomy.getId()] = taxonomy
        }
        _extendedTaxonomyDescriptors.value = taxonomies.map {
            ExtendedTaxonomyDescriptor(it.getId(), it.getName())
        }
        baseTaxonomy?.let { _activeTaxonomy.value = it }
    }

    /** An already-parsed extended taxonomy for [id], if any, avoiding a reparse. */
    fun getCachedExtendedTaxonomy(id: String): Taxonomy? = extendedTaxonomyCache[id]

    /** Indexes and caches a [Taxonomy] parsed on demand (see `ExtendedTaxonomyOnlyLoader`). */
    suspend fun cacheExtendedTaxonomy(taxonomy: Taxonomy): Taxonomy {
        ensureIndexed(taxonomy)
        extendedTaxonomyCache[taxonomy.getId()] = taxonomy
        return taxonomy
    }

    /**
     * Cheap counterpart to [setExtendedTaxonomies] for a cache-hit relaunch: makes the
     * "Select taxonomy" menu entry available immediately from cached (id, name) metadata,
     * without parsing the source file. The real [Taxonomy] objects are loaded lazily later,
     * one at a time, via [cacheExtendedTaxonomy] (see `FileLoadViewModel.loadExtendedTaxonomy`).
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
