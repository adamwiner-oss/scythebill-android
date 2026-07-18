package com.scythebill.birdlist.android.ui.query

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.android.data.QueryPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Dialog, reachable from the hamburger menu, to edit which sightings count toward reports. */
@Composable
fun QueryPreferencesDialog(
    store: QueryPreferencesStore,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val preferences by store.preferencesFlow.collectAsState(initial = QueryPreferences.DEFAULT)

    fun update(newPreferences: QueryPreferences) {
        scope.launch { store.update(newPreferences) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report preferences") },
        text = {
            Column {
                QueryPreferenceSwitch(
                    label = "Count introduced species",
                    checked = preferences.countIntroduced,
                    onCheckedChange = { update(preferences.copy(countIntroduced = it)) },
                )
                QueryPreferenceSwitch(
                    label = "Count heard-only sightings",
                    checked = preferences.countHeardOnly,
                    onCheckedChange = { update(preferences.copy(countHeardOnly = it)) },
                )
                QueryPreferenceSwitch(
                    label = "Count restrained sightings",
                    checked = preferences.countRestrained,
                    onCheckedChange = { update(preferences.copy(countRestrained = it)) },
                )
                QueryPreferenceSwitch(
                    label = "Count undescribed taxa",
                    checked = preferences.countUndescribed,
                    onCheckedChange = { update(preferences.copy(countUndescribed = it)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun QueryPreferenceSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.padding(vertical = 0.dp),
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
