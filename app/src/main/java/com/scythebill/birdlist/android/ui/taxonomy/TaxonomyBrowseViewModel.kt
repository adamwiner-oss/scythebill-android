package com.scythebill.birdlist.android.ui.taxonomy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scythebill.birdlist.model.taxa.Taxonomy
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

sealed interface TaxonomyBrowseUiState {
    data object Loading : TaxonomyBrowseUiState
    data class Error(val message: String) : TaxonomyBrowseUiState
    data class Loaded(val taxonomy: Taxonomy) : TaxonomyBrowseUiState
}

class TaxonomyBrowseViewModel(
    taxonomyDeferred: Deferred<Taxonomy>
) : ViewModel() {
    var uiState: TaxonomyBrowseUiState by mutableStateOf(TaxonomyBrowseUiState.Loading)
        private set

    init {
        viewModelScope.launch {
            uiState = try {
                TaxonomyBrowseUiState.Loaded(taxonomyDeferred.await())
            } catch (e: Exception) {
                TaxonomyBrowseUiState.Error(e.message ?: "Failed to load taxonomy")
            }
        }
    }

    class Factory(private val taxonomyDeferred: Deferred<Taxonomy>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaxonomyBrowseViewModel(taxonomyDeferred) as T
        }
    }
}
