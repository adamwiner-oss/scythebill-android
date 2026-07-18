package com.scythebill.birdlist.android.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.model.taxa.Taxon

/**
 * `OutlinedTextField` + inline match list backed by [SpeciesSearchViewModel];
 * matches update as the user types (debounced in the ViewModel).
 *
 * The match list is laid out inline (not in a `Popup`/`DropdownMenu`) so it
 * pushes the rest of the screen down instead of floating over the keyboard,
 * and is height-limited so it always leaves room for the keyboard below it.
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

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text("Search species") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (matches.isNotEmpty()) {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "Matches for \"$query\"",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    ) {
                        items(matches) { match ->
                            Text(
                                text = match.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSpeciesSelected(match.taxon)
                                        viewModel.clear()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
