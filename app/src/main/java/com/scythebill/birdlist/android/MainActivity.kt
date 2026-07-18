package com.scythebill.birdlist.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.android.cache.FileLoadViewModel
import com.scythebill.birdlist.android.cache.LoadState
import com.scythebill.birdlist.android.ui.query.QueryScreen
import com.scythebill.birdlist.android.ui.query.QueryViewModel
import com.scythebill.birdlist.android.ui.search.SpeciesSearchViewModel
import com.scythebill.birdlist.android.ui.taxonomy.TaxonomyBrowseScreen
import com.scythebill.birdlist.android.ui.taxonomy.TaxonomyBrowseViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    private val taxonomyViewModel: TaxonomyBrowseViewModel by viewModels {
        TaxonomyBrowseViewModel.Factory(
            (application as ScythebillApplication).taxonomyDeferred
        )
    }

    private val speciesSearchViewModel: SpeciesSearchViewModel by viewModels {
        SpeciesSearchViewModel.Factory(
            (application as ScythebillApplication).taxonomyDeferred
        )
    }

    private val fileLoadViewModel: FileLoadViewModel by viewModels {
        FileLoadViewModel.Factory(application as ScythebillApplication)
    }

    private val queryViewModel: QueryViewModel by viewModels {
        QueryViewModel.Factory(
            (application as ScythebillApplication).container.cacheDao(application),
            (application as ScythebillApplication).taxonomyDeferred,
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
                    // re-parses anything already loaded.
                    var startupReady by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        (application as ScythebillApplication).taxonomyDeferred.await()
                        fileLoadViewModel.checkForPickedFileOnLaunch()
                        startupReady = true
                    }
                    if (!startupReady) {
                        StartupLoadingScreen()
                    } else {
                        Column(modifier = Modifier.safeDrawingPadding()) {
                            LoadFileBar(
                                fileLoadViewModel = fileLoadViewModel,
                                onPickFile = { openDocumentLauncher.launch(arrayOf("*/*")) },
                            )
                            var tab by remember { mutableStateOf(MainTab.TAXONOMY) }
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
                                MainTab.TAXONOMY -> TaxonomyBrowseScreen(taxonomyViewModel, speciesSearchViewModel)
                                MainTab.QUERY -> QueryScreen(queryViewModel)
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
            } catch (e: SecurityException) {
                copyToAppPrivateStorage(uri)
            }
        } catch (e: Exception) {
            fileLoadViewModel.reportError(e.message ?: "Could not open $uri")
            return
        }
        fileLoadViewModel.onFileSelected(persistableUri)
    }

    /**
     * Some senders/apps hand ACTION_VIEW an Uri that can't be persisted
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
    TAXONOMY("Taxonomy"),
    QUERY("Query"),
}

@Composable
private fun StartupLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Loading...", modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun LoadFileBar(
    fileLoadViewModel: FileLoadViewModel,
    onPickFile: () -> Unit,
) {
    val state by fileLoadViewModel.loadState.collectAsState()
    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = onPickFile) {
            Text("Pick .bsxm file")
        }
        Text(
            when (state) {
                is LoadState.Idle -> "No file loaded"
                is LoadState.Loading -> "Loading..."
                is LoadState.Ready -> "Cache ready"
                is LoadState.VersionError -> (state as LoadState.VersionError).message
                is LoadState.ParseError -> (state as LoadState.ParseError).message
            }
        )
    }
}
