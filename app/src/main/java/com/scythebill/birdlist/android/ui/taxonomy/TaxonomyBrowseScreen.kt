package com.scythebill.birdlist.android.ui.taxonomy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scythebill.birdlist.android.ScythebillApplication
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.cache.LoadState
import com.scythebill.birdlist.android.ui.common.SightingIndicators
import com.scythebill.birdlist.android.ui.common.ExpandableSection
import com.scythebill.birdlist.android.ui.common.StaticSection
import com.scythebill.birdlist.android.ui.common.annotatedLabel
import com.scythebill.birdlist.android.ui.common.namePartsFor
import com.scythebill.birdlist.android.ui.query.QueryPreferences
import com.scythebill.birdlist.model.taxa.Species
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import kotlinx.coroutines.flow.first

@Composable
fun TaxonomyBrowseScreen(
    viewModel: TaxonomyBrowseViewModel,
    dao: CacheDao,
    loadState: LoadState,
    navigateToSpecies: Taxon? = null,
    onNavigationHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    // Names are read straight off the shared Taxon/Taxonomy objects rather
    // than passed as Compose state, so a locale/naming-mode change on its
    // own wouldn't normally trigger recomposition here — `key` forces the
    // whole subtree to rebuild when it changes.
    val namesState by (context.applicationContext as ScythebillApplication)
        .namesPreferencesStore.state.collectAsState()
    val selectedUserId by (context.applicationContext as ScythebillApplication)
        .userFilterStore.selectedUserId.collectAsState()

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

        is TaxonomyBrowseUiState.Loaded -> {
            val taxonomy = state.taxonomy
            // Saved as taxon ids (Taxon itself isn't Parcelable/Serializable) so the
            // browse position survives rotation instead of resetting to the root.
            // Hoisted above the `key(namesState)` below so a name-preference change
            // (which forces that subtree to be recreated) doesn't reset it too.
            val stackSaver = remember(taxonomy) {
                Saver<List<Taxon>, List<String>>(
                    save = { list -> list.mapNotNull { it.getId() } },
                    restore = { ids ->
                        ids.mapNotNull { taxonomy.getTaxon(it) }.ifEmpty { listOf(taxonomy.getRoot()) }
                    },
                )
            }
            var stack by rememberSaveable(taxonomy, stateSaver = stackSaver) {
                mutableStateOf(listOf(taxonomy.getRoot()))
            }

            // Scroll position for each browse level (keyed by taxon id), so that
            // navigating back to a family restores where the user left off. The
            // LazyListState instances themselves aren't saveable across process
            // recreation (e.g. rotation), so the raw index/offset pairs are mirrored
            // into rememberSaveable state and used to seed each LazyListState.
            val scrollPositionsSaver = remember {
                Saver<Map<String, Pair<Int, Int>>, Map<String, List<Int>>>(
                    save = { positions -> positions.mapValues { (_, v) -> listOf(v.first, v.second) } },
                    restore = { saved -> saved.mapValues { (_, v) -> v[0] to v[1] } },
                )
            }
            var scrollPositions by rememberSaveable(stateSaver = scrollPositionsSaver) {
                mutableStateOf(emptyMap<String, Pair<Int, Int>>())
            }
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

            key(namesState) {
                TaxonomyBrowseContent(
                    taxonomy,
                    dao,
                    loadState,
                    selectedUserId,
                    namesState.scientificOrCommon,
                    stack = stack,
                    onStackChange = { stack = it },
                    scrollPositions = scrollPositions,
                    onScrollPositionsChange = { scrollPositions = it },
                    listStates = listStates,
                    pendingScrollTargetId = pendingScrollTargetId,
                    onPendingScrollTargetIdChange = { pendingScrollTargetId = it },
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaxonomyBrowseContent(
    taxonomy: Taxonomy,
    dao: CacheDao,
    loadState: LoadState,
    selectedUserId: String?,
    mode: NamesPreferences.ScientificOrCommon,
    stack: List<Taxon>,
    onStackChange: (List<Taxon>) -> Unit,
    scrollPositions: Map<String, Pair<Int, Int>>,
    onScrollPositionsChange: (Map<String, Pair<Int, Int>>) -> Unit,
    listStates: MutableMap<String, LazyListState>,
    pendingScrollTargetId: String?,
    onPendingScrollTargetIdChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val current = stack.last()

    // Keyed on loadState (not just taxonomy) since the taxonomy object is
    // reused across file loads when the taxonomy version is unchanged —
    // without this, sighted-species indicators would keep showing the
    // previously loaded file's data.
    var sightedTaxonIds by remember(taxonomy, loadState, selectedUserId) { mutableStateOf<Set<String>>(emptySet()) }
    var countableSightedTaxonIds by remember(taxonomy, loadState, selectedUserId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(taxonomy, loadState, selectedUserId) {
        sightedTaxonIds = dao.getSightedTaxonIds(taxonomy.getId(), selectedUserId).toSet()

        val queryPreferences = (context.applicationContext as ScythebillApplication)
            .container.queryPreferencesStore(context).preferencesFlow.first()
        countableSightedTaxonIds = dao.getSightingCountabilityRows(taxonomy.getId(), selectedUserId)
            .filter { row ->
                row.raisedTaxonType == "SINGLE" && queryPreferences.isCountable(
                    sightingStatus = row.sightingStatus,
                    heardOnly = row.heardOnly,
                    taxon = taxonomy.getTaxon(row.taxonId),
                )
            }
            .map { it.taxonId }
            .toSet()
    }

    val isSpecies = current.getType() == Taxon.Type.species
    // The species detail page always shows both names — a "scientific/common
    // name only" preference only trims the secondary name in list contexts.
    val titleMode = if (isSpecies) detailMode(mode) else mode

    // A family's genera are skipped in the browse hierarchy — jump
    // straight from family to all of its species.
    val children = if (current.getType() == Taxon.Type.family) {
        TaxonUtils.getDescendantsOfType(current, Taxon.Type.species)
    } else {
        current.getContents()
    }

    // For a family, show how many of its species have a countable sighting,
    // e.g. "(10/21)".
    val familyCounts: Pair<Int, Int>? = if (current.getType() == Taxon.Type.family) {
        children.count { isSighted(it, countableSightedTaxonIds) } to children.size
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (familyCounts != null) {
                            buildAnnotatedString {
                                append(combinedLabel(current, titleMode))
                                append(" (${familyCounts.first}/${familyCounts.second})")
                            }
                        } else {
                            combinedLabel(current, titleMode)
                        }
                    )
                },
                navigationIcon = {
                    if (stack.size > 1) {
                        IconButton(onClick = { onStackChange(stack.dropLast(1)) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isSpecies) {
            SpeciesDetailContent(current, titleMode, dao, selectedUserId, modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val listStateKey = current.getId() ?: current.getName() ?: "root"
        val listState = listStates.getOrPut(listStateKey) {
            val (index, offset) = scrollPositions[listStateKey] ?: (0 to 0)
            LazyListState(firstVisibleItemIndex = index, firstVisibleItemScrollOffset = offset)
        }

        LaunchedEffect(listStateKey) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { position -> onScrollPositionsChange(scrollPositions + (listStateKey to position)) }
        }

        LaunchedEffect(current, pendingScrollTargetId) {
            val targetId = pendingScrollTargetId ?: return@LaunchedEffect
            val index = children.indexOfFirst { it.getId() == targetId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
            onPendingScrollTargetIdChange(null)
        }

        Column(modifier = Modifier.padding(padding)) {
            LazyColumn(state = listState) {
                items(children, key = { it.getId() ?: it.getName() ?: "" }) { child ->
                    TaxonRow(
                        child,
                        mode,
                        sighted = isSighted(child, sightedTaxonIds),
                        onClick = {
                            if (child.getType() == Taxon.Type.species || child.getContents().isNotEmpty()) {
                                onStackChange(stack + child)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The species detail page always shows both scientific and common names,
 * so a "scientific only"/"common only" preference is treated as just
 * choosing which name comes first.
 */
private fun detailMode(mode: NamesPreferences.ScientificOrCommon): NamesPreferences.ScientificOrCommon =
    when (mode) {
        NamesPreferences.ScientificOrCommon.SCIENTIFIC_ONLY -> NamesPreferences.ScientificOrCommon.SCIENTIFIC_FIRST
        NamesPreferences.ScientificOrCommon.COMMON_ONLY -> NamesPreferences.ScientificOrCommon.COMMON_FIRST
        else -> mode
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

internal enum class SpeciesLabelType {
    None,
    Italic,
    Medium
}

/**
 * The taxon's name formatted per [mode], italicizing the scientific part.
 */
internal fun combinedLabel(
    taxon: Taxon,
    mode: NamesPreferences.ScientificOrCommon,
    speciesLabelType: SpeciesLabelType = SpeciesLabelType.None,
): AnnotatedString {
    fun build(): AnnotatedString = taxon.annotatedLabel(mode)
    return when (speciesLabelType) {
        SpeciesLabelType.Italic -> {
            buildAnnotatedString {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(build())
                }
            }
        }
        SpeciesLabelType.Medium -> {
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(build())
                }
            }
        }
        else -> {
            build()
        }
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
private fun SpeciesDetailContent(
    taxon: Taxon,
    mode: NamesPreferences.ScientificOrCommon,
    dao: CacheDao,
    selectedUserId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SpeciesDetailViewModel = viewModel(
        key = taxon.getId().toString() + selectedUserId,
        factory = SpeciesDetailViewModel.Factory(
            taxon,
            dao,
            (context.applicationContext as ScythebillApplication).container.queryPreferencesStore(context),
            selectedUserId,
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
                                        TaxonWithRange(node.group, mode)
                                        Column(modifier = Modifier.padding(start = 24.dp)) {
                                            node.subspecies.forEach { subspecies ->
                                                TaxonWithRange(subspecies, mode)
                                            }
                                        }
                                    }
                                    is GroupOrSubspeciesNode.Subspecies -> TaxonWithRange(node.taxon, mode)
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
                                    trailingContent = if (row.heardOnly || row.introduced || row.photographed || row.ambiguousBadge != null || !row.countable || row.bvd) {
                                        {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (row.ambiguousBadge != null) {
                                                    Text(
                                                        "(${row.ambiguousBadge})",
                                                        modifier = Modifier.padding(end = 4.dp),
                                                        fontStyle = FontStyle.Italic,
                                                    )
                                                }
                                                SightingIndicators(row)
                                            }
                                        }
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
private fun TaxonWithRange(taxon: Taxon, mode: NamesPreferences.ScientificOrCommon, modifier: Modifier = Modifier) {
    val range = (taxon as? Species)?.let { TaxonUtils.getRange(it) }
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(combinedLabel(taxon, mode))
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
private fun TaxonRow(
    taxon: Taxon,
    mode: NamesPreferences.ScientificOrCommon,
    sighted: Boolean,
    onClick: () -> Unit,
) {
    if (taxon.getType() == Taxon.Type.species) {
        ListItem(
            headlineContent = { Text(combinedLabel(taxon, mode,
                if (sighted) SpeciesLabelType.Medium else  SpeciesLabelType.Italic)) },
            modifier = Modifier.clickable(onClick = onClick)
        )
    } else {
        val parts = taxon.namePartsFor(mode)
        ListItem(
            headlineContent = { Text(parts.primary, fontWeight = FontWeight.Medium) },
            supportingContent = if (parts.secondary != null) {
                { Text(parts.secondary) }
            } else null,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}
