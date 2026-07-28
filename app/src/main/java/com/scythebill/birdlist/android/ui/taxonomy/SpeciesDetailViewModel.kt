package com.scythebill.birdlist.android.ui.taxonomy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.cache.buildLocationDisplayNames
import com.scythebill.birdlist.android.cache.decodePhotoUris
import com.scythebill.birdlist.android.data.QueryPreferencesStore
import com.scythebill.birdlist.android.ui.common.formatDate
import com.scythebill.birdlist.android.ui.query.QueryPreferences
import com.scythebill.birdlist.android.ui.query.ResultRow
import com.scythebill.birdlist.android.ui.query.userFilterClause
import com.scythebill.birdlist.model.sighting.SightingInfo
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A group with its nested subspecies, or a standalone subspecies with no group. */
sealed interface GroupOrSubspeciesNode {
    data class Group(val group: Taxon, val subspecies: List<Taxon>) : GroupOrSubspeciesNode
    data class Subspecies(val taxon: Taxon) : GroupOrSubspeciesNode
}

sealed interface SpeciesDetailUiState {
    data object Loading : SpeciesDetailUiState
    data class Error(val message: String) : SpeciesDetailUiState
    data class Loaded(
        val groupsOrSubspecies: List<GroupOrSubspeciesNode>,
        val sightings: List<ResultRow>,
    ) : SpeciesDetailUiState
}

/**
 * Walks the direct tree below [taxon], collecting groups (with their nested
 * subspecies) and ungrouped subspecies in tree order.
 */
private fun buildGroupsOrSubspecies(taxon: Taxon): List<GroupOrSubspeciesNode> {
    val result = mutableListOf<GroupOrSubspeciesNode>()

    fun visit(current: Taxon) {
        for (child in current.getContents()) {
            when (child.getType()) {
                Taxon.Type.group -> {
                    val subspecies = TaxonUtils.getDescendantsOfType(child, Taxon.Type.subspecies)
                    result.add(GroupOrSubspeciesNode.Group(child, subspecies))
                }
                Taxon.Type.subspecies -> result.add(GroupOrSubspeciesNode.Subspecies(child))
                else -> visit(child)
            }
        }
    }

    visit(taxon)
    return result
}

/**
 * Loads the parts of a species detail page that require a database query:
 * its subspecies/groups, and sightings of the species and all of its
 * subspecies/groups combined, reverse-chronological.
 */
class SpeciesDetailViewModel(
    private val taxon: Taxon,
    private val dao: CacheDao,
    private val queryPreferencesStore: QueryPreferencesStore? = null,
    private val selectedUserId: String? = null,
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
            val queryPreferences = queryPreferencesStore?.preferencesFlow?.first() ?: QueryPreferences.DEFAULT
            val groupsOrSubspecies = buildGroupsOrSubspecies(taxon)

            val descendantTaxa = groupsOrSubspecies.flatMap { node ->
                when (node) {
                    is GroupOrSubspeciesNode.Group -> listOf(node.group) + node.subspecies
                    is GroupOrSubspeciesNode.Subspecies -> listOf(node.taxon)
                }
            }
            val speciesId = taxon.getId()
            val taxaById = (listOf(taxon) + descendantTaxa).associateBy { it.getId() }
            val subspeciesOrGroupLabels = descendantTaxa
                .mapNotNull { it.getId() }
                .associateWith { id -> descendantTaxa.first { it.getId() == id }.getName() }
            val taxonIds = (listOfNotNull(speciesId) + descendantTaxa.mapNotNull { it.getId() })
                .distinct()

            val sightings = if (taxonIds.isEmpty()) {
                emptyList()
            } else {
                val taxonomyId = taxon.getTaxonomy()?.getId()
                val placeholders = taxonIds.joinToString(",") { "?" }
                val taxonomyClause = if (taxonomyId != null) "AND s.taxonomyId = ?" else ""
                val userFilter = userFilterClause(selectedUserId)
                val userClause = if (userFilter != null) "AND ${userFilter.first}" else ""
                val sql = """
                    SELECT s.id AS sightingId, s.locationId, s.epochDay, s.datePrecision,
                           s.photographed, s.heardOnly, s.sightingStatus, sd.photoUrisJson, st.taxonId,
                           s.raisedTaxonType, s.raisedGroupKey, s.raisedDisplayName
                    FROM sightings s
                    JOIN sighting_taxa st ON st.sightingId = s.id
                    LEFT JOIN sighting_details sd ON sd.sightingId = s.id
                    WHERE st.taxonId IN ($placeholders) $taxonomyClause $userClause
                    ORDER BY s.epochDay DESC
                """.trimIndent()
                val args: Array<Any> = (taxonIds + listOfNotNull(taxonomyId) + (userFilter?.second ?: emptyList())).toTypedArray()
                val rows = dao.queryResults(SimpleSQLiteQuery(sql, args))
                val locationNames = buildLocationDisplayNames(dao.getAllLocations())
                rows
                    .distinctBy { it.sightingId }
                    .sortedByDescending { it.epochDay ?: Long.MIN_VALUE }
                    .map { row ->
                        ResultRow(
                            sightingId = row.sightingId,
                            locationName = locationNames[row.locationId ?: ""] ?: row.locationId ?: "",
                            dateLabel = formatDate(row.epochDay, row.datePrecision),
                            photographed = row.photographed,
                            heardOnly = row.heardOnly,
                            introduced = row.sightingStatus == SightingInfo.SightingStatus.INTRODUCED.name,
                            bvd = row.sightingStatus == SightingInfo.SightingStatus.BETTER_VIEW_DESIRED.name,
                            countable = row.raisedTaxonType == "SINGLE" && queryPreferences.isCountable(
                                sightingStatus = row.sightingStatus,
                                heardOnly = row.heardOnly,
                                taxon = taxaById[row.taxonId],
                            ),
                            photoUrls = decodePhotoUris(row.photoUrisJson)
                                .filter { it.startsWith("http://") || it.startsWith("https://") },
                            subspeciesLabel = if (row.taxonId != speciesId) {
                                subspeciesOrGroupLabels[row.taxonId]
                            } else {
                                null
                            },
                            ambiguousBadge = when (row.raisedTaxonType) {
                                "SP" -> "sp."
                                "HYBRID" -> "hybrid"
                                else -> null
                            },
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
        private val queryPreferencesStore: QueryPreferencesStore,
        private val selectedUserId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpeciesDetailViewModel(taxon, dao, queryPreferencesStore, selectedUserId) as T
        }
    }
}
