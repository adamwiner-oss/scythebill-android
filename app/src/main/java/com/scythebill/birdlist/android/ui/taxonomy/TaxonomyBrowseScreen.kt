package com.scythebill.birdlist.android.ui.taxonomy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scythebill.birdlist.android.ScythebillApplication
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.ui.common.SightingIndicators
import com.scythebill.birdlist.android.ui.common.ExpandableSection
import com.scythebill.birdlist.android.ui.common.StaticSection
import com.scythebill.birdlist.model.taxa.Species
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import com.scythebill.birdlist.model.taxa.Taxonomy

@Composable
fun TaxonomyBrowseScreen(
    viewModel: TaxonomyBrowseViewModel,
    dao: CacheDao,
    navigateToSpecies: Taxon? = null,
    onNavigationHandled: () -> Unit = {},
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

        is TaxonomyBrowseUiState.Loaded -> TaxonomyBrowseContent(
            state.taxonomy,
            dao,
            navigateToSpecies,
            onNavigationHandled,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaxonomyBrowseContent(
    taxonomy: Taxonomy,
    dao: CacheDao,
    navigateToSpecies: Taxon?,
    onNavigationHandled: () -> Unit,
) {
    var stack by remember(taxonomy) { mutableStateOf(listOf(taxonomy.getRoot())) }
    val current = stack.last()

    var sightedTaxonIds by remember(taxonomy) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(taxonomy) {
        sightedTaxonIds = dao.getSightedTaxonIds(taxonomy.getId()).toSet()
    }

    // Scroll position for each browse level (keyed by taxon id), so that
    // navigating back to a family restores where the user left off.
    val listStates = remember { mutableMapOf<String, LazyListState>() }

    // Set when a species is opened without having browsed there (e.g. from
    // search), so the family screen can scroll it into view once shown.
    var pendingScrollTargetId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = stack.size > 1) {
        stack = stack.dropLast(1)
    }

    LaunchedEffect(navigateToSpecies) {
        navigateToSpecies?.let { species ->
            stack = ancestorChain(species)
            pendingScrollTargetId = species.getId()
            onNavigationHandled()
        }
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

        val listStateKey = current.getId() ?: current.getName() ?: "root"
        val listState = listStates.getOrPut(listStateKey) { LazyListState() }

        LaunchedEffect(current, pendingScrollTargetId) {
            val targetId = pendingScrollTargetId ?: return@LaunchedEffect
            val index = children.indexOfFirst { it.getId() == targetId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
            pendingScrollTargetId = null
        }

        Column(modifier = Modifier.padding(padding)) {
            LazyColumn(state = listState) {
                items(children, key = { it.getId() ?: it.getName() ?: "" }) { child ->
                    TaxonRow(
                        child,
                        sighted = isSighted(child, sightedTaxonIds),
                        onClick = {
                            if (child.getType() == Taxon.Type.species || child.getContents().isNotEmpty()) {
                                stack = stack + child
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun ancestorChain(taxon: Taxon): List<Taxon> {
    val chain = mutableListOf(taxon)
    var parent = taxon.getParent()
    while (parent != null) {
        chain.add(parent)
        parent = parent.getParent()
    }
    // Genera are skipped in the browse hierarchy — jump straight from
    // family to species — so they must not appear in the stack either.
    return chain.reversed().filter { it.getType() != Taxon.Type.genus }
}

/**
 * "Common name (*Scientific name*)", italicizing the scientific part.
 * When [italic], the whole label is italicized (used for unsighted species).
 */
internal fun speciesLabel(taxon: Taxon, italic: Boolean = false): AnnotatedString {
    val commonName = taxon.getCommonName()
    val scientificName = TaxonUtils.getFullName(taxon) ?: taxon.getName() ?: ""
    fun build(): AnnotatedString = buildAnnotatedString {
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
    return if (italic) {
        buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(build())
            }
        }
    } else {
        build()
    }
}

/** Whether [taxon] or any of its descendants (subspecies/groups) has a recorded sighting. */
private fun isSighted(taxon: Taxon, sightedTaxonIds: Set<String>): Boolean {
    fun idsOf(t: Taxon): Sequence<String> =
        sequenceOf(t.getId()).filterNotNull() + t.getContents().asSequence().flatMap { idsOf(it) }
    return idsOf(taxon).any { it in sightedTaxonIds }
}

private enum class SpeciesDetailSection { INFO, GROUPS, SIGHTINGS }

@Composable
private fun SpeciesDetailContent(taxon: Taxon, dao: CacheDao, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: SpeciesDetailViewModel = viewModel(
        key = taxon.getId(),
        factory = SpeciesDetailViewModel.Factory(
            taxon,
            dao,
            (context.applicationContext as ScythebillApplication).container.queryPreferencesStore(context),
        ),
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
                                    trailingContent = if (row.heardOnly || row.introduced || row.photographed) {
                                        { SightingIndicators(row) }
                                    } else null,
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
private fun TaxonRow(taxon: Taxon, sighted: Boolean, onClick: () -> Unit) {
    if (taxon.getType() == Taxon.Type.species) {
        ListItem(
            headlineContent = { Text(speciesLabel(taxon, italic = !sighted)) },
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
