package com.scythebill.birdlist.android.ui.query

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.android.ScythebillApplication
import com.scythebill.birdlist.android.ui.common.SightingIndicators
import com.scythebill.birdlist.android.ui.common.annotatedLabel
import com.scythebill.birdlist.model.taxa.names.NamesPreferences

@Composable
fun QueryResultsScreen(
    viewModel: QueryViewModel,
    scrollToTaxonId: String? = null,
    onScrollHandled: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val namesState by (context.applicationContext as ScythebillApplication)
        .namesPreferencesStore.state.collectAsState()
    when (val s = state) {
        is QueryResultsUiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is QueryResultsUiState.Error -> Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Query failed: ${s.message}")
        }

        is QueryResultsUiState.Loaded -> {
            if (s.groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No matching sightings")
                }
            } else {
                val listState = rememberLazyListState()
                val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

                LaunchedEffect(scrollToTaxonId, s.groups) {
                    if (scrollToTaxonId == null) return@LaunchedEffect
                    var index = 1
                    for (group in s.groups) {
                        if (group.taxon?.getId() == scrollToTaxonId) {
                            expandedGroups[group.label] = true
                            listState.animateScrollToItem(index)
                            onScrollHandled()
                            break
                        }
                        index += 1 + if (expandedGroups[group.label] == true) group.rows.size else 0
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "species-count") {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "${s.groups.count { it.countable }} species",
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                )
                            },
                        )
                    }
                    s.groups.forEach { group ->
                        val expanded = expandedGroups[group.label] ?: false
                        item(key = "header-${group.label}") {
                            SpeciesHeader(
                                group = group,
                                mode = namesState.scientificOrCommon,
                                expanded = expanded,
                                onToggle = { expandedGroups[group.label] = !expanded },
                            )
                        }
                        if (expanded) {
                            items(group.rows, key = { "${group.label}-${it.sightingId}" }) { row ->
                                ResultRowItem(row)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeciesHeader(
    group: SpeciesGroup,
    mode: NamesPreferences.ScientificOrCommon,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val label = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            if (group.taxon != null) {
                append(group.taxon.annotatedLabel(mode))
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(group.label)
                }
            }
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
            append(" (${group.rows.size})")
        }
    }
    val allHeard = group.rows.isNotEmpty() && group.rows.all { it.heardOnly }
    val allIntroduced = group.rows.isNotEmpty() && group.rows.all { it.introduced }
    val allBvd = group.rows.isNotEmpty() && group.rows.all { it.bvd }
    val allNotCountable = group.rows.isNotEmpty() && group.rows.all { !it.countable }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allHeard) {
                    Text("H", modifier = Modifier.padding(start = 4.dp))
                }
                if (allIntroduced) {
                    Text("I", modifier = Modifier.padding(start = 4.dp))
                }
                if (allNotCountable) {
                    Text("NC", modifier = Modifier.padding(start = 4.dp))
                }
                if (allBvd) {
                    Text("BVD", modifier = Modifier.padding(start = 4.dp))
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .rotate(rotation),
                )
            }
        },
    )
}

@Composable
private fun ResultRowItem(row: ResultRow) {
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
