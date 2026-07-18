package com.scythebill.birdlist.android.ui.taxonomy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.ui.common.ExpandableSection
import com.scythebill.birdlist.android.ui.common.StaticSection
import com.scythebill.birdlist.android.ui.search.SpeciesSearchBar
import com.scythebill.birdlist.android.ui.search.SpeciesSearchViewModel
import com.scythebill.birdlist.model.taxa.Species
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import com.scythebill.birdlist.model.taxa.Taxonomy

@Composable
fun TaxonomyBrowseScreen(
    viewModel: TaxonomyBrowseViewModel,
    speciesSearchViewModel: SpeciesSearchViewModel,
    dao: CacheDao,
) {
    when (val state = viewModel.uiState) {
        is TaxonomyBrowseUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        is TaxonomyBrowseUiState.Error -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Failed to load taxonomy: ${state.message}")
        }

        is TaxonomyBrowseUiState.Loaded -> TaxonomyBrowseContent(state.taxonomy, speciesSearchViewModel, dao)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaxonomyBrowseContent(
    taxonomy: Taxonomy,
    speciesSearchViewModel: SpeciesSearchViewModel,
    dao: CacheDao,
) {
    var stack by remember { mutableStateOf(listOf(taxonomy.getRoot())) }
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) {
        stack = stack.dropLast(1)
    }

    val isSpecies = current.getType() == Taxon.Type.species

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSpecies) {
                        Text(speciesLabel(current))
                    } else {
                        Text(current.getName() ?: taxonomy.getName())
                    }
                },
                navigationIcon = {
                    if (stack.size > 1) {
                        IconButton(onClick = { stack = stack.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isSpecies) {
            SpeciesDetailContent(current, dao, modifier = Modifier.padding(padding))
            return@Scaffold
        }

        // A family's genera are skipped in the browse hierarchy — jump
        // straight from family to all of its species.
        val children = if (current.getType() == Taxon.Type.family) {
            TaxonUtils.getDescendantsOfType(current, Taxon.Type.species)
        } else {
            current.getContents()
        }

        Column(modifier = Modifier.padding(padding)) {
            SpeciesSearchBar(
                viewModel = speciesSearchViewModel,
                onSpeciesSelected = { taxon -> stack = stack + taxon },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn {
                items(children, key = { it.getId() ?: it.getName() ?: "" }) { child ->
                    TaxonRow(child, onClick = {
                        if (child.getType() == Taxon.Type.species || child.getContents().isNotEmpty()) {
                            stack = stack + child
                        }
                    })
                }
            }
        }
    }
}

/** "Common name (*Scientific name*)", italicizing the scientific part. */
internal fun speciesLabel(taxon: Taxon): AnnotatedString {
    val commonName = taxon.getCommonName()
    val scientificName = TaxonUtils.getFullName(taxon) ?: taxon.getName() ?: ""
    return buildAnnotatedString {
        if (commonName != null) {
            append(commonName)
            append(" (")
        }
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(scientificName)
        }
        if (commonName != null) {
            append(")")
        }
    }
}

private enum class SpeciesDetailSection { INFO, GROUPS, SIGHTINGS }

@Composable
private fun SpeciesDetailContent(taxon: Taxon, dao: CacheDao, modifier: Modifier = Modifier) {
    val viewModel: SpeciesDetailViewModel = viewModel(
        key = taxon.getId(),
        factory = SpeciesDetailViewModel.Factory(taxon, dao),
    )
    var expandedSection: SpeciesDetailSection? by remember(taxon.getId()) {
        mutableStateOf(SpeciesDetailSection.INFO)
    }
    fun toggle(section: SpeciesDetailSection) {
        expandedSection = if (expandedSection == section) null else section
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ExpandableSection(
            title = "Species Info",
            expanded = expandedSection == SpeciesDetailSection.INFO,
            onToggle = { toggle(SpeciesDetailSection.INFO) },
        ) {
            SpeciesInfoSection(taxon)
        }

        when (val state = viewModel.uiState) {
            is SpeciesDetailUiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is SpeciesDetailUiState.Error -> Text("Failed to load species detail: ${state.message}")

            is SpeciesDetailUiState.Loaded -> {
                if (state.groupsOrSubspecies.isEmpty()) {
                    StaticSection(title = "Subspecies/Groups", valueText = "Monotypic")
                } else {
                    ExpandableSection(
                        title = "Subspecies/Groups",
                        expanded = expandedSection == SpeciesDetailSection.GROUPS,
                        onToggle = { toggle(SpeciesDetailSection.GROUPS) },
                    ) {
                        Column {
                            state.groupsOrSubspecies.forEach { node ->
                                when (node) {
                                    is GroupOrSubspeciesNode.Group -> {
                                        TaxonWithRange(node.group)
                                        Column(modifier = Modifier.padding(start = 24.dp)) {
                                            node.subspecies.forEach { subspecies ->
                                                TaxonWithRange(subspecies)
                                            }
                                        }
                                    }
                                    is GroupOrSubspeciesNode.Subspecies -> TaxonWithRange(node.taxon)
                                }
                            }
                        }
                    }
                }

                if (state.sightings.isEmpty()) {
                    StaticSection(title = "Sightings", valueText = "Not recorded")
                } else {
                    ExpandableSection(
                        title = "Sightings (${state.sightings.size})",
                        expanded = expandedSection == SpeciesDetailSection.SIGHTINGS,
                        onToggle = { toggle(SpeciesDetailSection.SIGHTINGS) },
                    ) {
                        Column {
                            state.sightings.forEach { row ->
                                ListItem(
                                    headlineContent = { Text(row.locationName) },
                                    supportingContent = {
                                        Text(
                                            if (row.subspeciesLabel != null) {
                                                "${row.dateLabel} · ${row.subspeciesLabel}"
                                            } else {
                                                row.dateLabel
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxonWithRange(taxon: Taxon, modifier: Modifier = Modifier) {
    val range = (taxon as? Species)?.let { TaxonUtils.getRange(it) }
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(speciesLabel(taxon))
        if (range != null) {
            Text(
                range,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaxonRow(taxon: Taxon, onClick: () -> Unit) {
    if (taxon.getType() == Taxon.Type.species) {
        ListItem(
            headlineContent = { Text(speciesLabel(taxon)) },
            modifier = Modifier.clickable(onClick = onClick)
        )
    } else {
        val commonName = taxon.getCommonName()
        ListItem(
            headlineContent = { Text(taxon.getName() ?: "") },
            supportingContent = if (commonName != null) {
                { Text(commonName) }
            } else null,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}
