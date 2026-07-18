package com.scythebill.birdlist.android.ui.query

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.cache.LocationEntity
import com.scythebill.birdlist.android.cache.QueryResultRow
import com.scythebill.birdlist.android.ui.common.formatDate
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import com.scythebill.birdlist.model.taxa.Taxonomy
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface QueryResultsUiState {
    data object Loading : QueryResultsUiState
    data class Error(val message: String) : QueryResultsUiState
    data class Loaded(val groups: List<SpeciesGroup>) : QueryResultsUiState
}

data class SpeciesGroup(val taxon: Taxon?, val label: String, val rows: List<ResultRow>)

data class ResultRow(
    val sightingId: Long,
    val locationName: String,
    val dateLabel: String,
    val photographed: Boolean,
    val subspeciesLabel: String? = null,
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QueryViewModel(
    private val dao: CacheDao,
    private val taxonomyDeferred: Deferred<Taxonomy>,
) : ViewModel() {

    private val locationField = MutableStateFlow(LocationFieldState())
    private val dateField = MutableStateFlow(DateFieldState())
    private val photographedField = MutableStateFlow(PhotographedFieldState())

    var locations: List<LocationEntity> by mutableStateOf(emptyList())
        private set

    private val locationsDeferred: Deferred<List<LocationEntity>> =
        viewModelScope.async(Dispatchers.IO) { dao.getAllLocations() }

    init {
        viewModelScope.launch {
            locations = locationsDeferred.await()
        }
    }

    fun setLocationField(state: LocationFieldState) {
        locationField.value = state
    }

    fun setDateField(state: DateFieldState) {
        dateField.value = state
    }

    fun setPhotographedField(state: PhotographedFieldState) {
        photographedField.value = state
    }

    val uiState: StateFlow<QueryResultsUiState> =
        combine(locationField, dateField, photographedField) { location, date, photographed ->
            Triple(location, date, photographed)
        }
            .debounce(250)
            .mapLatest { (location, date, photographed) ->
                runQuery(location, date, photographed)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueryResultsUiState.Loading)

    /** Ids of species with a sighting in the current report results. */
    val reportTaxonIds: StateFlow<Set<String>> = uiState
        .map { (it as? QueryResultsUiState.Loaded)?.groups?.mapNotNull { g -> g.taxon?.getId() }?.toSet() ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private suspend fun runQuery(
        location: LocationFieldState,
        date: DateFieldState,
        photographed: PhotographedFieldState,
    ): QueryResultsUiState = withContext(Dispatchers.IO) {
        try {
            val clauses = listOfNotNull(location.clause(), date.clause(), photographed.clause())
            val whereSql = if (clauses.isEmpty()) "1=1" else clauses.joinToString(" AND ") { it.first }
            val args = clauses.flatMap { it.second }
            val sql = """
                SELECT s.id AS sightingId, s.locationId, s.epochDay, s.datePrecision,
                       s.photographed, st.taxonId
                FROM sightings s
                JOIN sighting_taxa st ON st.sightingId = s.id
                WHERE $whereSql
                ORDER BY s.epochDay DESC
            """.trimIndent()
            val rows = dao.queryResults(SimpleSQLiteQuery(sql, args.toTypedArray()))

            val taxonomy = taxonomyDeferred.await()
            val locationNames = locationsDeferred.await().associate { it.id to it.displayName }

            val groups = rows.groupBy { raisedSpeciesId(taxonomy, it.taxonId) }
                .map { (taxonId, taxonRows) ->
                    val taxon = taxonomy.getTaxon(taxonId)
                    val label = speciesLabel(taxon, taxonId)
                    SpeciesGroup(
                        taxon = taxon,
                        label = label,
                        rows = taxonRows
                            .sortedByDescending { it.epochDay ?: Long.MIN_VALUE }
                            .map { row ->
                                ResultRow(
                                    sightingId = row.sightingId,
                                    locationName = locationNames[row.locationId] ?: row.locationId,
                                    dateLabel = formatDate(row.epochDay, row.datePrecision),
                                    photographed = row.photographed,
                                )
                            },
                    )
                }
                .sortedWith(
                    compareBy<SpeciesGroup> { it.taxon?.getTaxonomyIndex() ?: Int.MAX_VALUE }
                        .thenBy { it.label }
                )

            QueryResultsUiState.Loaded(groups)
        } catch (e: Exception) {
            QueryResultsUiState.Error(e.message ?: "Query failed")
        }
    }

    /**
     * Sightings tagged with a subspecies or "sp."/group taxon are, by
     * default, rolled up to their parent species so results group at the
     * species level like the rest of the query UI.
     */
    private fun raisedSpeciesId(taxonomy: Taxonomy, taxonId: String): String {
        var taxon: Taxon? = taxonomy.getTaxon(taxonId) ?: return taxonId
        while (taxon != null &&
            (taxon.getType() == Taxon.Type.subspecies || taxon.getType() == Taxon.Type.group)
        ) {
            taxon = taxon.getParent()
        }
        return taxon?.getId() ?: taxonId
    }

    private fun speciesLabel(taxon: Taxon?, taxonId: String): String {
        if (taxon == null) return taxonId
        val commonName = taxon.getCommonName()
        val scientificName = TaxonUtils.getFullName(taxon) ?: taxon.getName() ?: taxonId
        return if (commonName != null) "$commonName ($scientificName)" else scientificName
    }

    class Factory(
        private val dao: CacheDao,
        private val taxonomyDeferred: Deferred<Taxonomy>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QueryViewModel(dao, taxonomyDeferred) as T
        }
    }
}
