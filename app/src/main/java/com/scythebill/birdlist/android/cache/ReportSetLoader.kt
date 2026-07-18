package com.scythebill.birdlist.android.cache

import android.content.ContentResolver
import android.net.Uri
import com.google.common.base.Optional as GuavaOptional
import com.scythebill.birdlist.model.sighting.ReportSet
import com.scythebill.birdlist.model.sighting.ReportSets
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.TaxonomyMappings
import com.scythebill.birdlist.model.xml.VersionTooNewException
import com.scythebill.birdlist.model.xml.XmlReportSetImport
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ReportSetLoadResult {
    data class Success(val reportSet: ReportSet) : ReportSetLoadResult()
    data class VersionTooNew(val fileVersion: String) : ReportSetLoadResult()
    data class VersionMismatch(val fileVersion: String?) : ReportSetLoadResult()
    data class ParseFailure(val throwable: Throwable) : ReportSetLoadResult()
}

/**
 * Stage 1 of the two-stage load: parse a `.bsxm` file into an in-memory
 * [ReportSet], reusing [XmlReportSetImport] verbatim, then enforce the
 * strict version check (no upgrader support on Android).
 */
class ReportSetLoader(
    private val contentResolver: ContentResolver,
    private val taxonomy: Taxonomy,
    private val taxonomyMappings: TaxonomyMappings,
) {
    suspend fun load(uri: Uri): ReportSetLoadResult = withContext(Dispatchers.IO) {
        try {
            val reportSet = contentResolver.openInputStream(uri)?.use { input ->
                InputStreamReader(input, Charsets.UTF_8).use { reader ->
                    XmlReportSetImport().importReportSet(
                        reader, taxonomy, GuavaOptional.absent(), taxonomyMappings
                    )
                }
            } ?: return@withContext ReportSetLoadResult.ParseFailure(
                IllegalStateException("Could not open $uri")
            )

            val loadedVersion = reportSet.loadedVersion
            if (loadedVersion != ReportSets.VERSION_FORMAT_CURRENT) {
                return@withContext ReportSetLoadResult.VersionMismatch(loadedVersion)
            }
            ReportSetLoadResult.Success(reportSet)
        } catch (e: VersionTooNewException) {
            ReportSetLoadResult.VersionTooNew(e.version)
        } catch (e: Exception) {
            ReportSetLoadResult.ParseFailure(e)
        }
    }
}
