package com.scythebill.birdlist.android.ui.query

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.cache.LoadState
import com.scythebill.birdlist.android.cache.LocationEntity
import com.scythebill.birdlist.android.cache.QueryResultRow
import com.scythebill.birdlist.android.cache.SyntheticLocationEntity
import com.scythebill.birdlist.android.cache.buildLocationDisplayNames
import com.scythebill.birdlist.android.cache.decodePhotoUris
import com.scythebill.birdlist.android.data.ActiveTaxonomyStore
import com.scythebill.birdlist.android.data.NamesPreferencesState
import com.scythebill.birdlist.android.data.QueryPreferencesStore
import com.scythebill.birdlist.android.data.UserFilterStore
import com.scythebill.birdlist.android.ui.common.formatDate
import com.scythebill.birdlist.android.ui.common.formatDateRange
import com.scythebill.birdlist.android.ui.common.formattedLabel
import com.scythebill.birdlist.android.ui.search.LocationIndexer
import com.scythebill.birdlist.android.ui.search.addSyntheticLocationsToIndex
import com.scythebill.birdlist.android.ui.search.buildLocationIndexer
import com.scythebill.birdlist.model.sighting.SightingInfo
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
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

data class SpeciesGroup(
    val taxon: Taxon?,
    val label: String,
    val rows: List<ResultRow>,
    val groupTaxonIds: Set<String>,
    val sortIndex: Int,
) {
    val countable: Boolean get() = rows.any { it.countable }
}

data class ResultRow(
    val sightingId: Long,
    val locationName: String,
    val dateLabel: String,
    val photographed: Boolean,
    val heardOnly: Boolean,
    val introduced: Boolean,
    val bvd: Boolean,
    val countable: Boolean,
    val photoUrls: List<String>,
    val subspeciesLabel: String? = null,
    val ambiguousBadge: String? = null,
)

private data class QueryInputs(
    val location: LocationFieldState,
    val date: DateFieldState,
    val photographed: PhotographedFieldState,
    val queryPreferences: QueryPreferences,
    val taxonomy: Taxonomy,
    val selectedUserId: String?,
    val namesMode: NamesPreferences.ScientificOrCommon = NamesPreferences.ScientificOrCommon.COMMON_FIRST,
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QueryViewModel(
    private val dao: CacheDao,
    private val activeTaxonomyStore: ActiveTaxonomyStore,
    private val queryPreferencesStore: QueryPreferencesStore? = null,
    private val loadState: Flow<LoadState> = flowOf(LoadState.Ready),
    namesState: Flow<NamesPreferencesState> = flowOf(
        NamesPreferencesState("en", NamesPreferences.ScientificOrCommon.COMMON_FIRST)
    ),
    private val userFilterStore: UserFilterStore? = null,
) : ViewModel() {

    private val queryPreferencesFlow: Flow<QueryPreferences> =
        queryPreferencesStore?.preferencesFlow ?: flowOf(QueryPreferences.DEFAULT)

    // Common names are read straight off the shared Taxon/Taxonomy objects,
    // so a locale change doesn't need this flow's value to take effect —
    // but the naming-mode ("Common (Scientific)" vs "Scientific (Common)")
    // does need it, and it also forces results to be recomputed whenever
    // the locale changes.
    private val namesStateFlow: Flow<NamesPreferencesState> = namesState

    private val selectedUserIdFlow: Flow<String?> = userFilterStore?.selectedUserId ?: flowOf(null)

    private val locationField = MutableStateFlow(LocationFieldState())
    private val dateField = MutableStateFlow(DateFieldState())
    private val photographedField = MutableStateFlow(PhotographedFieldState())

    /**
     * Current field values, exposed so [QueryBuilderScreen]'s rows can seed
     * their local UI state from the last-published value when they're
     * recomposed from scratch (e.g. after the query builder is collapsed
     * and re-expanded), instead of resetting to defaults.
     */
    val currentLocationField: LocationFieldState get() = locationField.value
    val currentDateField: DateFieldState get() = dateField.value
    val currentPhotographedField: PhotographedFieldState get() = photographedField.value

    var locations: List<LocationEntity> by mutableStateOf(emptyList())
        private set

    var syntheticLocations: List<SyntheticLocationEntity> by mutableStateOf(emptyList())
        private set

    var locationDisplayNames: Map<String, String> by mutableStateOf(emptyMap())
        private set

    var locationIndexer: LocationIndexer by mutableStateOf(LocationIndexer())
        private set

    init {
        // Re-read whenever a file (re-)finishes loading, not just once at
        // construction: this ViewModel is Activity-scoped and can outlive a
        // cache rebuild triggered by the first file pick or by opening a
        // different file later.
        viewModelScope.launch {
            loadState.collect { state ->
                if (state == LoadState.Ready) {
                    reloadLocations()
                }
            }
        }
    }

    private suspend fun reloadLocations() {
        locations = withContext(Dispatchers.IO) { dao.getAllLocations() }
        syntheticLocations = withContext(Dispatchers.IO) { dao.getAllSyntheticLocations() }
        locationDisplayNames = buildLocationDisplayNames(locations) +
            syntheticLocations.associate { it.id to it.displayName }
        val index = buildLocationIndexer(locations)
        addSyntheticLocationsToIndex(index, syntheticLocations)
        locationIndexer = index
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
        combine(
            locationField,
            dateField,
            photographedField,
            queryPreferencesFlow,
        ) { location, date, photographed, queryPreferences ->
            Triple(location, date, photographed) to queryPreferences
        }
            .combine(activeTaxonomyStore.activeTaxonomy.filterNotNull()) { (fields, queryPreferences), taxonomy ->
                val (location, date, photographed) = fields
                QueryInputs(location, date, photographed, queryPreferences, taxonomy, selectedUserId = null)
            }
            .combine(namesStateFlow) { inputs, namesState -> inputs.copy(namesMode = namesState.scientificOrCommon) }
            .combine(selectedUserIdFlow) { inputs, selectedUserId -> inputs.copy(selectedUserId = selectedUserId) }
            // Re-run on every (re-)load, not just when a field/taxonomy/etc.
            // changes — activeTaxonomy stays the same object across file
            // loads when the taxonomy version is unchanged, so without this
            // the query would keep showing results from the previous file.
            .combine(loadState.filter { it == LoadState.Ready }) { inputs, _ -> inputs }
            .debounce(250)
            .mapLatest { inputs ->
                runQuery(
                    inputs.location,
                    inputs.date,
                    inputs.photographed,
                    inputs.queryPreferences,
                    inputs.taxonomy,
                    inputs.selectedUserId,
                    inputs.namesMode,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueryResultsUiState.Loading)

    /** Ids of species with a sighting in the current report results. */
    val reportTaxonIds: StateFlow<Set<String>> = uiState
        .map { (it as? QueryResultsUiState.Loaded)?.groups?.flatMap { g -> g.groupTaxonIds }?.toSet() ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private suspend fun runQuery(
        location: LocationFieldState,
        date: DateFieldState,
        photographed: PhotographedFieldState,
        queryPreferences: QueryPreferences,
        taxonomy: Taxonomy,
        selectedUserId: String?,
        namesMode: NamesPreferences.ScientificOrCommon,
    ): QueryResultsUiState = withContext(Dispatchers.IO) {
        try {
            val baseTaxonomyId = TaxonUtils.getBaseTaxonomy(taxonomy)!!.getId()

            val clauses = listOfNotNull(
                location.clause(),
                date.clause(),
                photographed.clause(),
                userFilterClause(selectedUserId),
            )
            val whereSql = if (clauses.isEmpty()) "1=1" else clauses.joinToString(" AND ") { it.first }
            val args = listOf<Any>(baseTaxonomyId) + clauses.flatMap { it.second }
            val sql = """
                SELECT s.id AS sightingId, s.locationId, s.epochDay, s.datePrecision,
                       s.photographed, s.heardOnly, s.sightingStatus, sd.photoUrisJson, st.taxonId,
                       s.raisedTaxonType, s.raisedGroupKey, s.raisedDisplayName, t.displayName AS tripDisplayName,
                       t.startEpochDay AS tripStartEpochDay, t.endEpochDay AS tripEndEpochDay
                FROM sightings s
                JOIN sighting_taxa st ON st.sightingId = s.id
                LEFT JOIN sighting_details sd ON sd.sightingId = s.id
                LEFT JOIN trips t ON t.id = s.tripId
                WHERE s.taxonomyId = ? AND $whereSql
                ORDER BY s.epochDay DESC
            """.trimIndent()
            val rows = dao.queryResults(SimpleSQLiteQuery(sql, args.toTypedArray()))

            val locationNames = buildLocationDisplayNames(locations)

            val groups = rows.groupBy { row -> row.raisedGroupKey ?: raisedSpeciesId(taxonomy, row.taxonId) }
                .map { (groupKey, groupRows) ->
                    val ambiguous = groupRows.first().raisedGroupKey != null
                    val taxon = if (ambiguous) null else taxonomy.getTaxon(groupKey)
                    val label = if (ambiguous) {
                        groupRows.first().raisedDisplayName ?: groupKey
                    } else {
                        speciesLabel(taxon, groupKey, namesMode)
                    }
                    val groupTaxonIds = if (ambiguous) groupKey.split(",").toSet() else setOfNotNull(taxon?.getId())
                    val sortIndex = if (ambiguous) {
                        groupTaxonIds.mapNotNull { taxonomy.getTaxon(it)?.getTaxonomyIndex() }
                            .minOrNull() ?: Int.MAX_VALUE
                    } else {
                        taxon?.getTaxonomyIndex() ?: Int.MAX_VALUE
                    }
                    SpeciesGroup(
                        taxon = taxon,
                        label = label,
                        groupTaxonIds = groupTaxonIds,
                        sortIndex = sortIndex,
                        rows = groupRows
                            .distinctBy { it.sightingId }
                            .sortedByDescending { it.epochDay ?: Long.MIN_VALUE }
                            .map { row ->
                                ResultRow(
                                    sightingId = row.sightingId,
                                    locationName = row.tripDisplayName
                                        ?: locationNames[row.locationId ?: ""] ?: row.locationId ?: "",
                                    dateLabel = if (row.tripDisplayName != null) {
                                        formatDateRange(row.tripStartEpochDay, row.tripEndEpochDay)
                                    } else {
                                        formatDate(row.epochDay, row.datePrecision)
                                    },
                                    photographed = row.photographed,
                                    heardOnly = row.heardOnly,
                                    introduced = row.sightingStatus == SightingInfo.SightingStatus.INTRODUCED.name,
                                    bvd = row.sightingStatus == SightingInfo.SightingStatus.BETTER_VIEW_DESIRED.name,
                                    countable = row.raisedTaxonType == "SINGLE" && queryPreferences.isCountable(
                                        sightingStatus = row.sightingStatus,
                                        heardOnly = row.heardOnly,
                                        taxon = taxonomy.getTaxon(row.taxonId),
                                    ),
                                    photoUrls = decodePhotoUris(row.photoUrisJson)
                                        .filter { it.startsWith("http://") || it.startsWith("https://") },
                                    subspeciesLabel = if (!ambiguous && row.taxonId != taxon?.getId()) {
                                        taxonomy.getTaxon(row.taxonId)?.getName()
                                    } else {
                                        null
                                    },
                                    ambiguousBadge = ambiguousBadgeFor(row.raisedTaxonType),
                                )
                            },
                    )
                }
                .sortedWith(
                    compareBy<SpeciesGroup> { it.sortIndex }
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

    private fun ambiguousBadgeFor(raisedTaxonType: String): String? = when (raisedTaxonType) {
        "SP" -> "sp."
        "HYBRID" -> "hybrid"
        else -> null
    }

    private fun speciesLabel(
        taxon: Taxon?,
        taxonId: String,
        namesMode: NamesPreferences.ScientificOrCommon,
    ): String {
        if (taxon == null) return taxonId
        return taxon.formattedLabel(namesMode)
    }

    class Factory(
        private val dao: CacheDao,
        private val activeTaxonomyStore: ActiveTaxonomyStore,
        private val queryPreferencesStore: QueryPreferencesStore,
        private val loadState: Flow<LoadState>,
        private val namesState: Flow<NamesPreferencesState>,
        private val userFilterStore: UserFilterStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QueryViewModel(
                dao,
                activeTaxonomyStore,
                queryPreferencesStore,
                loadState,
                namesState,
                userFilterStore,
            ) as T
        }
    }
}
