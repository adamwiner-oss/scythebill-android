package com.scythebill.birdlist.android.cache

import android.net.Uri
import java.io.File
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scythebill.birdlist.android.ScythebillApplication
import com.scythebill.birdlist.android.data.PickedFilePreferences
import com.scythebill.birdlist.android.data.UserDescriptor
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.user.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class LoadState {
    data object Idle : LoadState()
    data object Loading : LoadState()
    data object Building : LoadState()
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

    /** Set once a file has been (fast-path or full) loaded, for [loadExtendedTaxonomy]. */
    private var lastLoadedUri: Uri? = null

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
            application.activeTaxonomyStore.setKnownExtendedTaxonomyDescriptors(
                decodeExtendedTaxonomyDescriptors(existingMetadata.extendedTaxonomyNamesJson)
            )
            application.userFilterStore.setAvailableUsers(
                decodeUserDescriptors(existingMetadata.userNamesJson)
            )
            _loadState.value = LoadState.Ready
            return
        }

        parseAndRebuild(uri, size, lastModified)
    }

    private suspend fun parseAndRebuild(uri: Uri, size: Long, lastModified: Long) {
        _loadState.value = LoadState.Building
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
                    taxonomyVersion = result.reportSet.getLoadedVersion(),
                )
                application.activeTaxonomyStore.setExtendedTaxonomies(
                    result.reportSet.extendedTaxonomies().toList()
                )
                application.userFilterStore.setAvailableUsers(
                    result.reportSet.getUserSet()?.allUsers()
                        ?.sortedWith(User.comparator())
                        ?.map { UserDescriptor(it.id(), it.name() ?: it.abbreviation() ?: it.id()) }
                        ?: emptyList()
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
     * Lazily parses just the single extended taxonomy identified by [id], e.g. when the
     * user opens "Select taxonomy" and picks one. Uses [ExtendedTaxonomyOnlyLoader] to scan
     * only that `<taxonomy>` element in the source `.bsxm`, ignoring sightings, locations,
     * trips, and any other embedded extended taxonomies, rather than re-running the full
     * [ReportSetLoader] parse. Returns `null` if no file is loaded, the id isn't found, or
     * parsing fails. Cheap (a no-op cache lookup) if this taxonomy was already parsed, e.g.
     * as part of the initial full-file import.
     */
    suspend fun loadExtendedTaxonomy(id: String): Taxonomy? {
        application.activeTaxonomyStore.getCachedExtendedTaxonomy(id)?.let { return it }
        val uri = lastLoadedUri ?: return null

        val taxonomy = ExtendedTaxonomyOnlyLoader(application.contentResolver).load(uri, id)
            ?: return null
        return application.activeTaxonomyStore.cacheExtendedTaxonomy(taxonomy)
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
