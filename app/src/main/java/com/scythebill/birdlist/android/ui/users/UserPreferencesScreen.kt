package com.scythebill.birdlist.android.ui.users

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.scythebill.birdlist.android.data.UserDescriptor
import com.scythebill.birdlist.android.data.UserFilterStore
import kotlinx.coroutines.launch

/** Full screen, reachable from the hamburger menu, to choose which user's sightings all
 * queries are limited to (or "All users"). Only shown when the loaded `.bsxm` has a UserSet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UserPreferencesScreen(
    store: UserFilterStore,
    onBack: () -> Unit,
) {
    val availableUsers by store.availableUsers.collectAsState()
    val selectedUserId by store.selectedUserId.collectAsState()
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Observer preferences") },
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
                ListItem(
                    headlineContent = { Text("All observers") },
                    leadingContent = {
                        RadioButton(
                            selected = selectedUserId == null,
                            onClick = { scope.launch { store.selectUser(null) } },
                        )
                    },
                    modifier = Modifier.clickable { scope.launch { store.selectUser(null) } },
                )
            }
            items(availableUsers) { user: UserDescriptor ->
                ListItem(
                    headlineContent = { Text(user.name) },
                    leadingContent = {
                        RadioButton(
                            selected = selectedUserId == user.id,
                            onClick = { scope.launch { store.selectUser(user.id) } },
                        )
                    },
                    modifier = Modifier.clickable { scope.launch { store.selectUser(user.id) } },
                )
            }
        }
    }
}
