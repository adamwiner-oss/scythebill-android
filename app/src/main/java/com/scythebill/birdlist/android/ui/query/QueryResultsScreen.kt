package com.scythebill.birdlist.android.ui.query

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun QueryResultsScreen(viewModel: QueryViewModel) {
    val state by viewModel.uiState.collectAsState()
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    s.groups.forEach { group ->
                        item(key = "header-${group.label}") {
                            SpeciesHeader(group)
                        }
                        items(group.rows, key = { "${group.label}-${it.sightingId}" }) { row ->
                            ResultRowItem(row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeciesHeader(group: SpeciesGroup) {
    val commonName = group.taxon?.getCommonName()
    val scientificName = group.taxon?.let {
        com.scythebill.birdlist.model.taxa.TaxonUtils.getFullName(it)
    } ?: group.label
    val label = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            if (commonName != null) {
                append(commonName)
                append(" (")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)) {
                    append(scientificName)
                }
                append(")")
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(scientificName)
                }
            }
        }
    }
    ListItem(headlineContent = { Text(label) })
}

@Composable
private fun ResultRowItem(row: ResultRow) {
    ListItem(
        headlineContent = { Text(row.locationName) },
        supportingContent = { Text(row.dateLabel) },
        trailingContent = if (row.photographed) {
            { Icon(Icons.Filled.Star, contentDescription = "Photographed") }
        } else null,
    )
}
