package com.scythebill.birdlist.android.ui.names

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.android.data.NamesPreferencesStore
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import kotlinx.coroutines.launch

/** Full screen, reachable from the hamburger menu, to choose the locale used for local names. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LocalNamesPreferencesScreen(
    store: NamesPreferencesStore,
    onBack: () -> Unit,
) {
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Name preferences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
            item {
                Text(
                    "Name display",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(NamesPreferences.ScientificOrCommon.values().toList()) { mode ->
                ListItem(
                    headlineContent = { Text(mode.label()) },
                    leadingContent = {
                        RadioButton(
                            selected = state.scientificOrCommon == mode,
                            onClick = { scope.launch { store.setScientificOrCommon(mode) } },
                        )
                    },
                    modifier = Modifier.clickable { scope.launch { store.setScientificOrCommon(mode) } },
                )
            }
            item { HorizontalDivider() }
            item {
                Text(
                    "Local names locale",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(NamesPreferences.AvailableClementsLocale.values().toList()) { locale ->
                ListItem(
                    headlineContent = { Text(locale.toString()) },
                    leadingContent = {
                        RadioButton(
                            selected = state.clementsLocale == locale.code(),
                            onClick = { scope.launch { store.setClementsLocale(locale.code()) } },
                        )
                    },
                    modifier = Modifier.clickable { scope.launch { store.setClementsLocale(locale.code()) } },
                )
            }
        }
    }
}

private fun NamesPreferences.ScientificOrCommon.label(): String = when (this) {
    NamesPreferences.ScientificOrCommon.COMMON_FIRST -> "Common name, then scientific name"
    NamesPreferences.ScientificOrCommon.SCIENTIFIC_FIRST -> "Scientific name, then common name"
    NamesPreferences.ScientificOrCommon.SCIENTIFIC_ONLY -> "Scientific name only"
    NamesPreferences.ScientificOrCommon.COMMON_ONLY -> "Common name only"
}
