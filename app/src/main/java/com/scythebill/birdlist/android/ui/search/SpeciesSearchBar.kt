package com.scythebill.birdlist.android.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.scythebill.birdlist.model.taxa.Taxon

/**
 * `OutlinedTextField` + dropdown backed by [SpeciesSearchViewModel]; matches
 * update as the user types (debounced in the ViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesSearchBar(
    viewModel: SpeciesSearchViewModel,
    onSpeciesSelected: (Taxon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val matches by viewModel.matches.collectAsState()

    OutlinedTextField(
        value = query,
        onValueChange = viewModel::onQueryChanged,
        label = { Text("Search species") },
        modifier = modifier.fillMaxWidth(),
    )
    DropdownMenu(
        expanded = matches.isNotEmpty(),
        onDismissRequest = { viewModel.clear() },
        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
    ) {
        matches.forEach { match ->
            DropdownMenuItem(
                text = { Text(match.label) },
                onClick = {
                    onSpeciesSelected(match.taxon)
                    viewModel.clear()
                },
            )
        }
    }
}
