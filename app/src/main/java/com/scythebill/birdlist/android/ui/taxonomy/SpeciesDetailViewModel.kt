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
import com.scythebill.birdlist.android.ui.common.formatDate
import com.scythebill.birdlist.android.ui.query.ResultRow
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import kotlinx.coroutines.Dispatchers
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
            val groupsOrSubspecies = buildGroupsOrSubspecies(taxon)

            val descendantTaxa = groupsOrSubspecies.flatMap { node ->
                when (node) {
                    is GroupOrSubspeciesNode.Group -> listOf(node.group) + node.subspecies
                    is GroupOrSubspeciesNode.Subspecies -> listOf(node.taxon)
                }
            }
            val speciesId = taxon.getId()
            val subspeciesOrGroupLabels = descendantTaxa
                .mapNotNull { it.getId() }
                .associateWith { id -> descendantTaxa.first { it.getId() == id }.getName() }
            val taxonIds = (listOfNotNull(speciesId) + descendantTaxa.mapNotNull { it.getId() })
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
                val locationNames = buildLocationDisplayNames(dao.getAllLocations())
                rows
                    .sortedByDescending { it.epochDay ?: Long.MIN_VALUE }
                    .map { row ->
                        ResultRow(
                            sightingId = row.sightingId,
                            locationName = locationNames[row.locationId] ?: row.locationId,
                            dateLabel = formatDate(row.epochDay, row.datePrecision),
                            photographed = row.photographed,
                            subspeciesLabel = if (row.taxonId != speciesId) {
                                subspeciesOrGroupLabels[row.taxonId]
                            } else {
                                null
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SpeciesDetailViewModel(taxon, dao) as T
        }
    }
}
