package com.scythebill.birdlist.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scythebill.birdlist.android.data.ActiveTaxonomyStore
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.stream.Collectors

data class SpeciesMatch(val taxon: Taxon, val label: String)

/**
 * Debounced species search over the reused desktop `Indexer`s, queried in
 * the naming-mode order from [speciesIndexerGroups].
 */
class SpeciesSearchViewModel(
    private val activeTaxonomyStore: ActiveTaxonomyStore,
    private val namesPreferences: NamesPreferences = NamesPreferences(),
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _rawMatches = MutableStateFlow<List<SpeciesMatch>>(emptyList())

    private val _allowedPredicate = MutableStateFlow<(Taxon) -> Boolean>({ true })

    val matches: StateFlow<List<SpeciesMatch>> =
        combine(_rawMatches, _allowedPredicate) { all, predicate ->
            all.filter { predicate(it.taxon) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            activeTaxonomyStore.activeTaxonomy.filterNotNull().collect {
                clear()
            }
        }
    }

    /** Restricts [matches] to taxa satisfying [predicate]; `{ true }` allows all. */
    fun setAllowedPredicate(predicate: (Taxon) -> Boolean) {
        _allowedPredicate.value = predicate
    }

    fun onQueryChanged(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _rawMatches.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            val taxonomy = activeTaxonomyStore.activeTaxonomy.value ?: return@launch
            val ids = LinkedHashSet<String>()
            for (indexer in speciesIndexerGroups(taxonomy, namesPreferences)) {
                // At least for now, only show species (the taxonomy indexers index
                // everything) - the user can see the groups/subspecies
                val found = indexer.find(text)
                ids.addAll(found.stream().filter { id ->
                    taxonomy.getTaxon(id)?.getType() == Taxon.Type.species
                }.collect(Collectors.toList()))

                if (ids.size >= MAX_MATCHES) break
            }
            _rawMatches.value = ids.take(MAX_MATCHES).mapNotNull { id ->
                taxonomy.getTaxon(id)?.let { taxon -> SpeciesMatch(taxon, taxonSearchLabel(taxon, namesPreferences)) }
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _query.value = ""
        _rawMatches.value = emptyList()
    }

    class Factory(
        private val activeTaxonomyStore: ActiveTaxonomyStore,
        private val namesPreferences: NamesPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpeciesSearchViewModel(activeTaxonomyStore, namesPreferences) as T
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 200L
        const val MAX_MATCHES = 100
    }
}
