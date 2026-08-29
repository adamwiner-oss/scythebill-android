package com.scythebill.birdlist.android.di

import android.content.Context
import com.scythebill.birdlist.android.cache.CacheDao
import com.scythebill.birdlist.android.cache.ScythebillDatabase
import com.scythebill.birdlist.android.data.PickedFilePreferences
import com.scythebill.birdlist.android.data.QueryPreferencesStore
import com.scythebill.birdlist.android.data.UserPreferencesStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.scythebill.birdlist.model.taxa.IocUpgradeLoader
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.taxa.TaxonomyImpl
import com.scythebill.birdlist.model.taxa.TaxonomyMappingLoader
import com.scythebill.birdlist.model.taxa.TaxonomyMappings
import com.scythebill.birdlist.model.taxa.names.LocalNamesImpl
import com.scythebill.birdlist.model.taxa.names.NamesLoaders
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import com.scythebill.birdlist.model.util.TaxonomyIndexer
import com.scythebill.birdlist.model.xml.XmlTaxonImport
import java.io.InputStreamReader
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manual composition root, replacing the desktop app's Guice `CommonModule`.
 * Constructs only what has an actual caller; see
 * `app/docs/ioc-extended-taxonomy-avoidance.md` for what must never be
 * constructed here.
 */
class AppContainer {

    /**
     * `TaxonomyMappings` is a required constructor argument of
     * `XmlReportSetImport`, but its only use is remapping sightings tagged
     * with an old taxonomy version during `.bsxm` import. Android requires
     * every `.bsxm` file to already be at the current taxonomy version, so
     * that remapping path is unreachable here — the real
     * `TaxonomyMappingLoader`/`IocUpgradeLoader` history (desktop-only,
     * `ApplicationModule`) doesn't need to be ported.
     */
    fun taxonomyMappings(): TaxonomyMappings {
        // Checklists.enumerateChecklistFiles() enumerates its own jar via
        // ProtectionDomain.getCodeSource(), which is always null under ART —
        // constructing a real Checklists crashes on Android. TaxonomyMappings's
        // `checklists` param is nullable for exactly this reason: it's only
        // forwarded to getTracker() (the taxonomy-upgrade path, unreachable
        // here since every .bsxm we load is already at the current
        // taxonomy/version), so passing null is safe.
        return TaxonomyMappings(emptyJavaSet(), emptyJavaSet(), null)
    }

    /**
     * Reads `taxon.xml` off the classpath (in `:model`'s resources,
     * per Sub-plan 0/1) and parses it via `XmlTaxonImport.importTaxa`
     * — the plain-`Taxonomy` entry point only; see
     * `app/docs/ioc-extended-taxonomy-avoidance.md` for why
     * `importMappedTaxa` must never be called here.
     */
    suspend fun loadTaxonomy(namesPreferences: NamesPreferences): Taxonomy = withContext(Dispatchers.IO) {
        val stream = Taxonomy::class.java.getResourceAsStream("/taxon.xml")
            ?: error("taxon.xml not found on classpath")
        val taxonomy = InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            XmlTaxonImport().importTaxa(reader)
                ?: error("importTaxa returned null")
        }
        // Bundled as classpath resources (via :model's resources, per the
        // taxon.xml precedent above) rather than Android assets, so the
        // same NamesLoaders.fromUrls classloader lookup desktop uses works
        // unchanged.
        (taxonomy as TaxonomyImpl).localNames = LocalNamesImpl(
            NamesLoaders.fromUrls("multilingual/clements-names-%s.csv"),
            namesPreferences,
            NamesPreferences.LocaleOption.CLEMENTS,
        )
        taxonomy
    }

    /**
     * Builds the common/scientific-name search indexes for [taxonomy] and
     * sets them onto it, via the desktop `TaxonomyIndexer`/`IndexerBuilder`
     * reused verbatim from `:model`.
     */
    suspend fun buildSpeciesIndexes(taxonomy: Taxonomy): Unit = withContext(Dispatchers.Default) {
        val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))
        try {
            TaxonomyIndexer(Futures.immediateFuture(taxonomy)).load(executor).get()
        } finally {
            executor.shutdown()
        }
    }

    fun cacheDao(context: Context): CacheDao = ScythebillDatabase.get(context).cacheDao()

    fun pickedFilePreferences(context: Context): PickedFilePreferences =
        PickedFilePreferences(context)

    fun queryPreferencesStore(context: Context): QueryPreferencesStore =
        QueryPreferencesStore(context)

    fun userPreferencesStore(context: Context): UserPreferencesStore =
        UserPreferencesStore(context)

    // TaxonomyMappings' constructor is written against the raw
    // `java.util.Set<T>` type (a Guice multibinding idiom), which Kotlin
    // does not treat as interchangeable with `kotlin.collections.Set<T>`.
    @Suppress("UNCHECKED_CAST")
    private fun <T> emptyJavaSet(): java.util.Set<T> =
        java.util.Collections.emptySet<T>() as java.util.Set<T>
}
