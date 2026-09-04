package com.scythebill.birdlist.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.scythebill.birdlist.android.cache.FileLoadViewModel
import com.scythebill.birdlist.android.data.ExtendedTaxonomyDescriptor
import com.scythebill.birdlist.android.cache.LoadState
import com.scythebill.birdlist.android.transfer.DesktopTransferDownloader
import com.scythebill.birdlist.android.transfer.TransferQrPayload
import com.scythebill.birdlist.android.transfer.TransferUrl
import com.scythebill.birdlist.android.ui.names.LocalNamesPreferencesScreen
import com.scythebill.birdlist.android.ui.query.QueryPreferencesDialog
import com.scythebill.birdlist.android.ui.query.QueryScreen
import com.scythebill.birdlist.android.ui.query.QueryViewModel
import com.scythebill.birdlist.android.ui.search.SpeciesSearchBar
import com.scythebill.birdlist.android.ui.search.SpeciesSearchViewModel
import com.scythebill.birdlist.android.ui.taxonomy.TaxonomyBrowseScreen
import com.scythebill.birdlist.android.ui.taxonomy.TaxonomyBrowseViewModel
import com.scythebill.birdlist.android.ui.users.UserPreferencesScreen
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.Taxonomy
import java.io.File
import kotlinx.coroutines.launch

// Temporarily disabled pending further testing; leave the underlying
// transfer code in place.
private const val RECEIVE_FROM_DESKTOP_ENABLED = false

class MainActivity : ComponentActivity() {
    private val taxonomyViewModel: TaxonomyBrowseViewModel by viewModels {
        TaxonomyBrowseViewModel.Factory(
            (application as ScythebillApplication).activeTaxonomyStore
        )
    }

    private val speciesSearchViewModel: SpeciesSearchViewModel by viewModels {
        SpeciesSearchViewModel.Factory(
            (application as ScythebillApplication).activeTaxonomyStore,
            (application as ScythebillApplication).namesPreferencesStore.namesPreferences,
        )
    }

    private val fileLoadViewModel: FileLoadViewModel by viewModels {
        FileLoadViewModel.Factory(application as ScythebillApplication)
    }

    private val queryViewModel: QueryViewModel by viewModels {
        QueryViewModel.Factory(
            (application as ScythebillApplication).container.cacheDao(application),
            (application as ScythebillApplication).activeTaxonomyStore,
            (application as ScythebillApplication).container.queryPreferencesStore(application),
            fileLoadViewModel.loadState,
            (application as ScythebillApplication).namesPreferencesStore.state,
            (application as ScythebillApplication).userFilterStore,
        )
    }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { handleIncomingUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleViewIntent(intent)
        setContent {
            MaterialTheme {
                Surface {
                    // Sequences taxonomy load then the last-picked-file check
                    // behind a single startup loading screen. Both are keyed
                    // off application-scoped work (taxonomyDeferred, and the
                    // cache-metadata short-circuit in FileLoadViewModel), so
                    // re-running this on rotation is cheap and never
                    // reparses anything already loaded.
                    var startupReady by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        (application as ScythebillApplication).taxonomyDeferred.await()
                        fileLoadViewModel.checkForPickedFileOnLaunch()
                        startupReady = true
                    }
                    val loadState by fileLoadViewModel.loadState.collectAsState()
                    if (!startupReady) {
                        StartupLoadingScreen(loadState)
                    } else {
                        Box(modifier = Modifier.safeDrawingPadding()) {
                          var taxonomySwitching by remember { mutableStateOf(false) }
                          var localNamesPreferencesExpanded by remember { mutableStateOf(false) }
                          var userPreferencesExpanded by remember { mutableStateOf(false) }
                          // Hoisted above the preferences-screen overlays below, rather than
                          // remembered inside the Column: the Column stays composed the whole
                          // time (preferences screens are drawn on top of it, not in place of
                          // it), but hoisting here keeps this state stable regardless.
                          var tab by rememberSaveable { mutableStateOf(MainTab.TAXONOMY) }
                          var searchExpanded by remember { mutableStateOf(false) }
                          var navigateToSpecies by remember { mutableStateOf<Taxon?>(null) }
                          var scrollToTaxonId by remember { mutableStateOf<String?>(null) }
                          Column {
                            val reportTaxonIds by queryViewModel.reportTaxonIds.collectAsState()
                            LaunchedEffect(tab, reportTaxonIds) {
                                speciesSearchViewModel.setAllowedPredicate(
                                    if (tab == MainTab.QUERY) {
                                        { taxon -> reportTaxonIds.contains(taxon.getId()) }
                                    } else {
                                        { true }
                                    }
                                )
                            }

                            var reportPreferencesExpanded by remember { mutableStateOf(false) }
                            if (reportPreferencesExpanded) {
                                QueryPreferencesDialog(
                                    store = (application as ScythebillApplication).container
                                        .queryPreferencesStore(application),
                                    scope = rememberCoroutineScope(),
                                    onDismiss = { reportPreferencesExpanded = false },
                                )
                            }

                            val activeTaxonomyStore = (application as ScythebillApplication).activeTaxonomyStore
                            val activeTaxonomy by activeTaxonomyStore.activeTaxonomy.collectAsState()
                            val extendedTaxonomyDescriptors by
                                activeTaxonomyStore.extendedTaxonomyDescriptors.collectAsState()
                            val availableUsers by
                                (application as ScythebillApplication).userFilterStore.availableUsers.collectAsState()
                            var taxonomySelectionExpanded by remember { mutableStateOf(false) }
                            if (taxonomySelectionExpanded) {
                                TaxonomySelectionDialog(
                                    baseTaxonomy = activeTaxonomyStore.baseTaxonomy,
                                    extendedTaxonomyDescriptors = extendedTaxonomyDescriptors,
                                    activeTaxonomy = activeTaxonomy,
                                    onSelectBase = { taxonomy ->
                                        taxonomySelectionExpanded = false
                                        lifecycleScope.launch {
                                            activeTaxonomyStore.selectTaxonomy(taxonomy)
                                        }
                                    },
                                    onSelectDescriptor = { descriptor ->
                                        taxonomySelectionExpanded = false
                                        taxonomySwitching = true
                                        lifecycleScope.launch {
                                            fileLoadViewModel.loadExtendedTaxonomy(descriptor.id)
                                                ?.let { activeTaxonomyStore.selectTaxonomy(it) }
                                            taxonomySwitching = false
                                        }
                                    },
                                    onDismiss = { taxonomySelectionExpanded = false },
                                )
                            }

                            if (loadState !is LoadState.Ready) {
                                LoadFileBar(
                                    fileLoadViewModel = fileLoadViewModel,
                                    onPickFile = { openDocumentLauncher.launch(arrayOf("*/*")) },
                                    onReceiveFromDesktop = { startReceiveFromDesktopScan() },
                                )
                            } else {
                                AppTopBar(
                                    onPickFile = { openDocumentLauncher.launch(arrayOf("*/*")) },
                                    onReceiveFromDesktop = { startReceiveFromDesktopScan() },
                                    onEditReportPreferences = { reportPreferencesExpanded = true },
                                    onEditLocalNames = { localNamesPreferencesExpanded = true },
                                    hasUsers = availableUsers.isNotEmpty(),
                                    onEditUsers = { userPreferencesExpanded = true },
                                    hasExtendedTaxonomies = extendedTaxonomyDescriptors.isNotEmpty(),
                                    onSelectTaxonomy = { taxonomySelectionExpanded = true },
                                    searchExpanded = searchExpanded,
                                    onSearchToggle = { searchExpanded = !searchExpanded },
                                    speciesSearchViewModel = speciesSearchViewModel,
                                    onSpeciesSelected = { taxon ->
                                        if (tab == MainTab.QUERY) {
                                            scrollToTaxonId = taxon.getId()
                                        } else {
                                            navigateToSpecies = taxon
                                        }
                                        searchExpanded = false
                                    },
                                )
                            }
                            TabRow(selectedTabIndex = tab.ordinal) {
                                MainTab.entries.forEach { t ->
                                    Tab(
                                        selected = tab == t,
                                        onClick = { tab = t },
                                        text = { Text(t.label) },
                                    )
                                }
                            }
                            when (tab) {
                                MainTab.TAXONOMY -> TaxonomyBrowseScreen(
                                    taxonomyViewModel,
                                    (application as ScythebillApplication).container.cacheDao(application),
                                    loadState = loadState,
                                    navigateToSpecies = navigateToSpecies,
                                    onNavigationHandled = { navigateToSpecies = null },
                                )
                                MainTab.QUERY -> QueryScreen(
                                    queryViewModel,
                                    scrollToTaxonId = scrollToTaxonId,
                                    onScrollHandled = { scrollToTaxonId = null },
                                )
                            }
                          }
                          if (localNamesPreferencesExpanded) {
                              LocalNamesPreferencesScreen(
                                  store = (application as ScythebillApplication).namesPreferencesStore,
                                  onBack = { localNamesPreferencesExpanded = false },
                              )
                          }
                          if (userPreferencesExpanded) {
                              UserPreferencesScreen(
                                  store = (application as ScythebillApplication).userFilterStore,
                                  onBack = { userPreferencesExpanded = false },
                              )
                          }
                          if (taxonomySwitching ||
                              loadState is LoadState.Loading ||
                              loadState is LoadState.Building
                          ) {
                              LoadingOverlay(
                                  message = if (loadState is LoadState.Building) {
                                      "Building the cache..."
                                  } else {
                                      "Loading..."
                                  },
                              )
                          }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /**
     * Starts a Play Services Code Scanner scan — its UI runs out-of-process
     * in Play Services, so this never declares or requests CAMERA.
     */
    private fun startReceiveFromDesktopScan() {
        // A report set already showing the hamburger-menu AppTopBar (LoadState.Ready)
        // must stay showing it if the scan is canceled or fails — reportError()
        // would otherwise flip loadState away from Ready and fall back to the
        // two-button LoadFileBar, discarding the still-loaded file. In that case
        // surface the failure as a transient Toast instead of a state change.
        val hadLoadedFile = fileLoadViewModel.loadState.value is LoadState.Ready
        fun reportScanError(message: String) {
            if (hadLoadedFile) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } else {
                fileLoadViewModel.reportError(message)
            }
        }

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val url = barcode.rawValue
                    ?.let { TransferQrPayload.parseIpUrl(it) }
                    ?.let { TransferUrl.parse(it) }
                if (url == null) {
                    reportScanError("That QR code isn't a Scythebill transfer link")
                    return@addOnSuccessListener
                }
                lifecycleScope.launch {
                    val destFile = File(filesDir, "received.bsxm")
                    try {
                        DesktopTransferDownloader.download(url, destFile)
                        fileLoadViewModel.onFileSelected(Uri.fromFile(destFile))
                    } catch (e: Exception) {
                        reportScanError(e.message ?: "Could not download file from desktop")
                    }
                }
            }
            .addOnFailureListener { e ->
                reportScanError(e.message ?: "QR scan failed")
            }
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { handleIncomingUri(it) }
        }
    }

    private fun handleIncomingUri(uri: Uri) {
        val persistableUri: Uri = try {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                uri
            } catch (_: SecurityException) {
                copyToAppPrivateStorage(uri)
            }
        } catch (e: Exception) {
            fileLoadViewModel.reportError(e.message ?: "Could not open $uri")
            return
        }
        fileLoadViewModel.onFileSelected(persistableUri)
    }

    /**
     * Some senders/apps hand ACTION_VIEW a Uri that can't be persisted
     * (one-shot content Uris). Copy the bytes into app-private storage so
     * the cache-invalidation check always has a stable file to compare
     * against, even without a persistable permission.
     */
    private fun copyToAppPrivateStorage(sourceUri: Uri): Uri {
        val destFile = File(filesDir, "imported.bsxm")
        contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Could not open $sourceUri" }
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(destFile)
    }
}

private enum class MainTab(val label: String) {
    TAXONOMY("Species"),
    QUERY("Reports"),
}

@Composable
private fun StartupLoadingScreen(loadState: LoadState = LoadState.Loading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                if (loadState is LoadState.Building) "Building the cache..." else "Loading...",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                message,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun LoadFileBar(
    fileLoadViewModel: FileLoadViewModel,
    onPickFile: () -> Unit,
    onReceiveFromDesktop: () -> Unit,
) {
    val state by fileLoadViewModel.loadState.collectAsState()
    Column(modifier = Modifier.padding(16.dp)) {
        Row {
            Button(onClick = onPickFile) {
                Text("Pick .bsxm file")
            }
            if (RECEIVE_FROM_DESKTOP_ENABLED) {
                Button(
                    onClick = onReceiveFromDesktop,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Receive from desktop")
                }
            }
        }
        Text(
            when (state) {
                is LoadState.Idle -> "No file loaded"
                is LoadState.Loading -> "Loading..."
                is LoadState.Building -> "Building the cache..."
                is LoadState.Ready -> "Cache ready"
                is LoadState.VersionError -> (state as LoadState.VersionError).message
                is LoadState.ParseError -> (state as LoadState.ParseError).message
            }
        )
    }
}

@Composable
private fun AppTopBar(
    onPickFile: () -> Unit,
    onReceiveFromDesktop: () -> Unit,
    onEditReportPreferences: () -> Unit,
    onEditLocalNames: () -> Unit,
    hasUsers: Boolean,
    onEditUsers: () -> Unit,
    hasExtendedTaxonomies: Boolean,
    onSelectTaxonomy: () -> Unit,
    searchExpanded: Boolean,
    onSearchToggle: () -> Unit,
    speciesSearchViewModel: SpeciesSearchViewModel,
    onSpeciesSelected: (Taxon) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (hasExtendedTaxonomies) {
                    DropdownMenuItem(
                        text = { Text("Select taxonomy") },
                        onClick = {
                            menuExpanded = false
                            onSelectTaxonomy()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Report preferences") },
                    onClick = {
                        menuExpanded = false
                        onEditReportPreferences()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Name preferences") },
                    onClick = {
                        menuExpanded = false
                        onEditLocalNames()
                    },
                )
                if (hasUsers) {
                    DropdownMenuItem(
                        text = { Text("Observer preferences") },
                        onClick = {
                            menuExpanded = false
                            onEditUsers()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Change .bsxm file") },
                    onClick = {
                        menuExpanded = false
                        onPickFile()
                    },
                )
                if (RECEIVE_FROM_DESKTOP_ENABLED) {
                    DropdownMenuItem(
                        text = { Text("Receive from desktop") },
                        onClick = {
                            menuExpanded = false
                            onReceiveFromDesktop()
                        },
                    )
                }
            }
            IconButton(onClick = {
                if (searchExpanded) speciesSearchViewModel.clear()
                onSearchToggle()
            }) {
                if (searchExpanded) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                } else {
                    Icon(Icons.Filled.Search, contentDescription = "Search species")
                }
            }
        }
        if (searchExpanded) {
            SpeciesSearchBar(
                viewModel = speciesSearchViewModel,
                onSpeciesSelected = onSpeciesSelected,
            )
        }
    }
}

@Composable
private fun TaxonomySelectionDialog(
    baseTaxonomy: Taxonomy?,
    extendedTaxonomyDescriptors: List<ExtendedTaxonomyDescriptor>,
    activeTaxonomy: Taxonomy?,
    onSelectBase: (Taxonomy) -> Unit,
    onSelectDescriptor: (ExtendedTaxonomyDescriptor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select taxonomy") },
        text = {
            Column {
                baseTaxonomy?.let { taxonomy ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = taxonomy === activeTaxonomy,
                            onClick = { onSelectBase(taxonomy) },
                        )
                        Text(taxonomy.getName())
                    }
                }
                extendedTaxonomyDescriptors.forEach { descriptor ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = descriptor.id == activeTaxonomy?.getId(),
                            onClick = { onSelectDescriptor(descriptor) },
                        )
                        Text(descriptor.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
