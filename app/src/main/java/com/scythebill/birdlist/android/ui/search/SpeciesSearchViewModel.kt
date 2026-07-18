package com.scythebill.birdlist.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.stream.Collectors

data class SpeciesMatch(val taxon: Taxon, val label: String)

/**
 * Debounced species search over the reused desktop `Indexer`s, queried in
 * the naming-mode order from [speciesIndexerGroups].
 */
class SpeciesSearchViewModel(
    private val taxonomyDeferred: Deferred<Taxonomy>,
    private val namesPreferences: NamesPreferences = NamesPreferences(),
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _matches = MutableStateFlow<List<SpeciesMatch>>(emptyList())
    val matches: StateFlow<List<SpeciesMatch>> = _matches.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _matches.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            val taxonomy = taxonomyDeferred.await()
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
            _matches.value = ids.take(MAX_MATCHES).mapNotNull { id ->
                taxonomy.getTaxon(id)?.let { taxon -> SpeciesMatch(taxon, taxonSearchLabel(taxon)) }
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _query.value = ""
        _matches.value = emptyList()
    }

    class Factory(
        private val taxonomyDeferred: Deferred<Taxonomy>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpeciesSearchViewModel(taxonomyDeferred) as T
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 200L
        const val MAX_MATCHES = 20
    }
}
