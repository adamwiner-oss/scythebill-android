package com.scythebill.birdlist.android.cache

import android.net.Uri
import java.io.File
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scythebill.birdlist.android.ScythebillApplication
import com.scythebill.birdlist.android.data.PickedFilePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class LoadState {
    data object Idle : LoadState()
    data object Loading : LoadState()
    data class VersionError(val message: String) : LoadState()
    data class ParseError(val message: String) : LoadState()
    data object Ready : LoadState()
}

/**
 * Orchestrates the two-stage load (parse via [ReportSetLoader], flatten via
 * [SightingCacheBuilder]) and the cache-invalidation check against
 * [CacheDao]'s persisted metadata.
 */
class FileLoadViewModel(
    private val application: ScythebillApplication,
    private val cacheDao: CacheDao,
    private val pickedFilePreferences: PickedFilePreferences,
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState

    /** Set once a file has been (fast-path or full) loaded, for [ensureExtendedTaxonomiesLoaded]. */
    private var lastLoadedUri: Uri? = null

    /** True once the full parse has run for [lastLoadedUri] and real [Taxonomy]s are known. */
    private var extendedTaxonomiesFullyLoaded = false

    /**
     * Loads the last-picked file, if any, so it's covered by the caller's
     * single startup loading screen. Suspends rather than launching its own
     * coroutine so the caller can sequence it after [ScythebillApplication]'s
     * `taxonomyDeferred`.
     */
    suspend fun checkForPickedFileOnLaunch() {
        val storedUri = pickedFilePreferences.pickedUriFlow.firstOrNull() ?: return
        loadFrom(Uri.parse(storedUri))
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            pickedFilePreferences.setPickedUri(uri.toString())
            loadFrom(uri)
        }
    }

    fun reportError(message: String) {
        _loadState.value = LoadState.ParseError(message)
    }

    private suspend fun loadFrom(uri: Uri) {
        _loadState.value = LoadState.Loading

        val docFile = if (uri.scheme == "file") {
            val path = uri.path
            if (path == null) {
                _loadState.value = LoadState.ParseError("Could not resolve $uri")
                return
            }
            DocumentFile.fromFile(File(path))
        } else {
            DocumentFile.fromSingleUri(application, uri)
        }
        if (docFile == null) {
            _loadState.value = LoadState.ParseError("Could not resolve $uri")
            return
        }
        val size = docFile.length()
        val lastModified = docFile.lastModified()

        val existingMetadata = cacheDao.getMetadata()
        val cacheHit = existingMetadata != null &&
            existingMetadata.sourceUri == uri.toString() &&
            existingMetadata.sourceSize == size &&
            existingMetadata.sourceLastModified == lastModified &&
            existingMetadata.cacheFormatVersion == CACHE_FORMAT_VERSION

        lastLoadedUri = uri

        if (cacheHit) {
            // Fast path: skip the expensive XML parse entirely. Extended-taxonomy
            // availability is known from cached metadata; the real Taxonomy objects
            // are parsed lazily only if the user opens "Select taxonomy".
            extendedTaxonomiesFullyLoaded = false
            application.activeTaxonomyStore.setKnownExtendedTaxonomyDescriptors(
                decodeExtendedTaxonomyDescriptors(existingMetadata?.extendedTaxonomyNamesJson)
            )
            _loadState.value = LoadState.Ready
            return
        }

        parseAndRebuild(uri, size, lastModified)
    }

    private suspend fun parseAndRebuild(uri: Uri, size: Long, lastModified: Long) {
        val taxonomy = application.taxonomyDeferred.await()
        val taxonomyMappings = application.container.taxonomyMappings()
        val loader = ReportSetLoader(application.contentResolver, taxonomy, taxonomyMappings)

        when (val result = loader.load(uri)) {
            is ReportSetLoadResult.Success -> {
                SightingCacheBuilder(cacheDao).rebuild(
                    reportSet = result.reportSet,
                    sourceUri = uri.toString(),
                    sourceSize = size,
                    sourceLastModified = lastModified,
                    taxonomyVersion = result.reportSet.loadedVersion,
                )
                extendedTaxonomiesFullyLoaded = true
                application.activeTaxonomyStore.setExtendedTaxonomies(
                    result.reportSet.extendedTaxonomies().toList()
                )
                _loadState.value = LoadState.Ready
            }
            is ReportSetLoadResult.VersionTooNew,
            is ReportSetLoadResult.VersionMismatch -> {
                _loadState.value = LoadState.VersionError(
                    "This file was created with an incompatible Scythebill version. " +
                        "Please open it in Scythebill desktop first to update it."
                )
            }
            is ReportSetLoadResult.ParseFailure -> {
                val message = result.throwable.message
                _loadState.value = LoadState.ParseError(
                    if (message == "IOC taxonomy must be present during upgrades") {
                        ".bsxm file must be upgraded with desktop Scythebill"
                    } else {
                        message ?: "Failed to load file"
                    }
                )
            }
        }
    }

    /**
     * Lazily runs the full parse (without rebuilding the sighting cache, since a cache-hit
     * relaunch means the cache is already up to date) so real [com.scythebill.birdlist.model.taxa.Taxonomy]
     * objects become available for switching, e.g. when the user opens "Select taxonomy".
     * No-op if the descriptors were already backed by a full parse, or no file is loaded.
     */
    suspend fun ensureExtendedTaxonomiesLoaded() {
        if (extendedTaxonomiesFullyLoaded) return
        val uri = lastLoadedUri ?: return

        val taxonomy = application.taxonomyDeferred.await()
        val taxonomyMappings = application.container.taxonomyMappings()
        val loader = ReportSetLoader(application.contentResolver, taxonomy, taxonomyMappings)

        val result = loader.load(uri)
        if (result is ReportSetLoadResult.Success) {
            extendedTaxonomiesFullyLoaded = true
            application.activeTaxonomyStore.setExtendedTaxonomies(
                result.reportSet.extendedTaxonomies().toList()
            )
        }
    }

    class Factory(
        private val application: ScythebillApplication,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FileLoadViewModel(
                application = application,
                cacheDao = application.container.cacheDao(application),
                pickedFilePreferences = application.container.pickedFilePreferences(application),
            ) as T
        }
    }
}
