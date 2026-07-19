package com.scythebill.birdlist.android.ui.query

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.android.cache.LocationEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun QueryBuilderScreen(viewModel: QueryViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        LocationFieldRow(
            state = LocationFieldRowState(viewModel),
        )
        DateFieldRow(viewModel)
        PhotographedFieldRow(viewModel)
    }
}

private class LocationFieldRowState(val viewModel: QueryViewModel)

@Composable
private fun LocationFieldRow(state: LocationFieldRowState) {
    var enabled by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(LocationFieldState.LocationMode.IN) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LocationEntity?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }

    fun publish() {
        state.viewModel.setLocationField(
            LocationFieldState(enabled = enabled, locationId = selected?.id, mode = mode)
        )
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    publish()
                },
            )
            Text("Location")
            if (enabled) {
                Box2 {
                    Button(onClick = { modeMenuExpanded = true }) {
                        Text(if (mode == LocationFieldState.LocationMode.IN) "In" else "Not in")
                    }
                    DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("In") }, onClick = {
                            mode = LocationFieldState.LocationMode.IN
                            modeMenuExpanded = false
                            publish()
                        })
                        DropdownMenuItem(text = { Text("Not in") }, onClick = {
                            mode = LocationFieldState.LocationMode.NOT_IN
                            modeMenuExpanded = false
                            publish()
                        })
                    }
                }
            }
        }
        if (enabled) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selected = null
                        expanded = it.isNotBlank()
                        publish()
                    },
                    label = { Text("Search locations") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val matches = state.viewModel.locations
                    .filter {
                        (state.viewModel.locationDisplayNames[it.id] ?: it.displayName)
                            .contains(query, ignoreCase = true)
                    }
                    .take(20)
                if (expanded && matches.isNotEmpty()) {
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                        ) {
                            items(matches) { location ->
                                val displayName = state.viewModel.locationDisplayNames[location.id]
                                    ?: location.displayName
                                Text(
                                    displayName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = location
                                            query = displayName
                                            expanded = false
                                            publish()
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
}

@Composable
private fun DateFieldRow(viewModel: QueryViewModel) {
    var enabled by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(DateFieldState.DateMode.BETWEEN) }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    fun publish() {
        viewModel.setDateField(DateFieldState(enabled = enabled, mode = mode, from = from, to = to))
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    publish()
                },
            )
            Text("Date")
            if (enabled) {
                Box2 {
                    Button(onClick = { modeMenuExpanded = true }) {
                        Text(mode.displayName)
                    }
                    DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                        DateFieldState.DateMode.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(m.displayName) }, onClick = {
                                mode = m
                                modeMenuExpanded = false
                                publish()
                            })
                        }
                    }
                }
            }
        }
        if (enabled) {
            val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (mode) {
                    DateFieldState.DateMode.ON -> {
                        FilterChip(
                            selected = from != null,
                            onClick = { showFromPicker = true },
                            label = { Text(from?.format(fmt) ?: "Pick date") },
                        )
                    }
                    DateFieldState.DateMode.BETWEEN -> {
                        FilterChip(
                            selected = from != null,
                            onClick = { showFromPicker = true },
                            label = { Text(from?.format(fmt) ?: "From") },
                        )
                        FilterChip(
                            selected = to != null,
                            onClick = { showToPicker = true },
                            label = { Text(to?.format(fmt) ?: "To") },
                        )
                    }
                    DateFieldState.DateMode.AFTER -> {
                        FilterChip(
                            selected = from != null,
                            onClick = { showFromPicker = true },
                            label = { Text(from?.format(fmt) ?: "After") },
                        )
                    }
                    DateFieldState.DateMode.BEFORE -> {
                        FilterChip(
                            selected = to != null,
                            onClick = { showToPicker = true },
                            label = { Text(to?.format(fmt) ?: "Before") },
                        )
                    }
                    DateFieldState.DateMode.THIS_YEAR -> {}
                }
            }
        }
    }

    if (showFromPicker) {
        DatePickerDialogPicker(
            initial = from,
            onDismiss = { showFromPicker = false },
            onPicked = {
                from = it
                showFromPicker = false
                publish()
            },
        )
    }
    if (showToPicker) {
        DatePickerDialogPicker(
            initial = to,
            onDismiss = { showToPicker = false },
            onPicked = {
                to = it
                showToPicker = false
                publish()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogPicker(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onPicked(date)
                } ?: onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun PhotographedFieldRow(viewModel: QueryViewModel) {
    var enabled by remember { mutableStateOf(false) }
    var hasPhoto by remember { mutableStateOf(true) }

    fun publish() {
        viewModel.setPhotographedField(PhotographedFieldState(enabled = enabled, hasPhoto = hasPhoto))
    }

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                publish()
            },
        )
        Text("Photographed")
        if (enabled) {
            FilterChip(
                selected = hasPhoto,
                onClick = {
                    hasPhoto = true
                    publish()
                },
                label = { Text("Yes") },
            )
            FilterChip(
                selected = !hasPhoto,
                onClick = {
                    hasPhoto = false
                    publish()
                },
                label = { Text("No") },
            )
        }
    }
}


@Composable
private fun Box2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box { content() }
}
