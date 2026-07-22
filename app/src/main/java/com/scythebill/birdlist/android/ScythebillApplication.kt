package com.scythebill.birdlist.android

import android.app.Application
import com.scythebill.birdlist.android.data.ActiveTaxonomyStore
import com.scythebill.birdlist.android.di.AppContainer
import com.scythebill.birdlist.model.taxa.Taxonomy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Kicks off a single process-scoped taxonomy load in [onCreate], so
 * `Activity`/`ViewModel` recreation (e.g. rotation) reuses the same
 * [Deferred] instead of reloading `taxon.xml`.
 *
 * [applicationScope] is exposed so other startup work (e.g. the initial
 * `.bsxm` load in `FileLoadViewModel`) can also be sequenced off a
 * process-scoped lifetime instead of a `ViewModel`'s, keeping the single
 * startup loading screen driven by one scope end to end.
 */
class ScythebillApplication : Application() {
    val container = AppContainer()
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val activeTaxonomyStore = ActiveTaxonomyStore(container)

    lateinit var taxonomyDeferred: Deferred<Taxonomy>
        private set

    override fun onCreate() {
        super.onCreate()
        taxonomyDeferred = applicationScope.async {
            val taxonomy = container.loadTaxonomy()
            container.buildSpeciesIndexes(taxonomy)
            activeTaxonomyStore.setBaseTaxonomy(taxonomy)
            taxonomy
        }
    }
}
