package com.scythebill.birdlist.android.ui.taxonomy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scythebill.birdlist.android.data.ActiveTaxonomyStore
import com.scythebill.birdlist.model.taxa.Taxonomy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

sealed interface TaxonomyBrowseUiState {
    data object Loading : TaxonomyBrowseUiState
    data class Error(val message: String) : TaxonomyBrowseUiState
    data class Loaded(val taxonomy: Taxonomy) : TaxonomyBrowseUiState
}

class TaxonomyBrowseViewModel(
    activeTaxonomyStore: ActiveTaxonomyStore
) : ViewModel() {
    var uiState: TaxonomyBrowseUiState by mutableStateOf(TaxonomyBrowseUiState.Loading)
        private set

    init {
        viewModelScope.launch {
            activeTaxonomyStore.activeTaxonomy.filterNotNull().collect { taxonomy ->
                uiState = TaxonomyBrowseUiState.Loaded(taxonomy)
            }
        }
    }

    class Factory(private val activeTaxonomyStore: ActiveTaxonomyStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaxonomyBrowseViewModel(activeTaxonomyStore) as T
        }
    }
}
