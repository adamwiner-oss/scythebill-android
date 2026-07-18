package com.scythebill.birdlist.android.ui.taxonomy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.ui.common.formatDate
import com.scythebill.birdlist.android.ui.query.ResultRow
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SpeciesDetailUiState {
    data object Loading : SpeciesDetailUiState
    data class Error(val message: String) : SpeciesDetailUiState
    data class Loaded(
        val groupsOrSubspecies: List<Taxon>,
        val sightings: List<ResultRow>,
    ) : SpeciesDetailUiState
}

/**
 * Loads the parts of a species detail page that require a database query:
 * its subspecies/groups, and sightings of the species and all of its
 * subspecies/groups combined, reverse-chronological.
 */
class SpeciesDetailViewModel(
    private val taxon: Taxon,
    private val dao: CacheDao,
) : ViewModel() {

    var uiState: SpeciesDetailUiState by mutableStateOf(SpeciesDetailUiState.Loading)
        private set

    init {
        viewModelScope.launch {
            uiState = load()
        }
    }

    private suspend fun load(): SpeciesDetailUiState = withContext(Dispatchers.IO) {
        try {
            val groupsOrSubspecies =
                TaxonUtils.getDescendantsOfType(taxon, Taxon.Type.group) +
                    TaxonUtils.getDescendantsOfType(taxon, Taxon.Type.subspecies)

            val taxonIds = (listOfNotNull(taxon.getId()) + groupsOrSubspecies.mapNotNull { it.getId() })
                .distinct()

            val sightings = if (taxonIds.isEmpty()) {
                emptyList()
            } else {
                val placeholders = taxonIds.joinToString(",") { "?" }
                val sql = """
                    SELECT s.id AS sightingId, s.locationId, s.epochDay, s.datePrecision,
                           s.photographed, st.taxonId
                    FROM sightings s
                    JOIN sighting_taxa st ON st.sightingId = s.id
                    WHERE st.taxonId IN ($placeholders)
                    ORDER BY s.epochDay DESC
                """.trimIndent()
                val rows = dao.queryResults(SimpleSQLiteQuery(sql, taxonIds.toTypedArray()))
                val locationNames = dao.getAllLocations().associate { it.id to it.displayName }
                rows
                    .sortedByDescending { it.epochDay ?: Long.MIN_VALUE }
                    .map { row ->
                        ResultRow(
                            sightingId = row.sightingId,
                            locationName = locationNames[row.locationId] ?: row.locationId,
                            dateLabel = formatDate(row.epochDay, row.datePrecision),
                            photographed = row.photographed,
                        )
                    }
            }

            SpeciesDetailUiState.Loaded(groupsOrSubspecies, sightings)
        } catch (e: Exception) {
            SpeciesDetailUiState.Error(e.message ?: "Failed to load species detail")
        }
    }

    class Factory(
        private val taxon: Taxon,
        private val dao: CacheDao,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpeciesDetailViewModel(taxon, dao) as T
        }
    }
}
