package com.scythebill.birdlist.android.cache

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.base.Optional as GuavaOptional
import com.google.common.truth.Truth.assertThat
import com.scythebill.birdlist.android.di.AppContainer
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import com.scythebill.birdlist.model.xml.XmlReportSetImport
import java.io.InputStreamReader
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start-scale check from the plan's Verification section: a `.bsxm`
 * with tens of thousands of sightings should finish taxonomy + report-set
 * loading in a reasonable time. `large_sample.bsxm` (~30k sightings) is
 * synthetic - generated against the real bundled `taxon.xml` species ids
 * since no genuine current-version (19.0.0) sample of that size exists -
 * but it exercises the exact same [XmlReportSetImport] /
 * [SightingCacheBuilder] pipeline the app runs on launch, off the main
 * thread, so a hang here would be the same hang users would see.
 */
@RunWith(AndroidJUnit4::class)
class LargeReportSetLoadInstrumentedTest {

    private lateinit var db: ScythebillDatabase
    private lateinit var dao: CacheDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ScythebillDatabase::class.java).build()
        dao = db.cacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun openAsset(name: String) =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name)

    @Test
    fun coldStart_loadsLargeReportSetWithoutHanging() = runTest {
        val container = AppContainer()
        val taxonomy = container.loadTaxonomy(NamesPreferences())
        val taxonomyMappings = container.taxonomyMappings()

        var sightingCount = 0
        val parseMillis = measureTimeMillis {
            val reportSet = openAsset("large_sample.bsxm").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    XmlReportSetImport().importReportSet(
                        reader, taxonomy, GuavaOptional.absent(), taxonomyMappings
                    )
                }
            }
            sightingCount = reportSet.sightings.size

            val buildMillis = measureTimeMillis {
                SightingCacheBuilder(dao).rebuild(
                    reportSet = reportSet,
                    sourceUri = "content://test/large_sample.bsxm",
                    sourceSize = 1_895_287L,
                    sourceLastModified = 0L,
                    taxonomyVersion = reportSet.loadedVersion,
                )
            }
            // ANRs are triggered by >5s of main-thread blocking; this whole
            // pipeline already runs off the main thread in the app (see
            // ScythebillApplication's applicationScope / FileLoadViewModel's
            // Dispatchers.IO usage), so the bar here is just "finishes
            // promptly enough to not feel broken to a user waiting on a
            // loading screen".
            assertThat(buildMillis).isLessThan(30_000L)
        }
        assertThat(parseMillis).isLessThan(60_000L)

        assertThat(sightingCount).isGreaterThan(25_000)
        assertThat(dao.getMetadata()).isNotNull()
        assertThat(rawSightingCount()).isEqualTo(sightingCount.toLong())
    }

    private fun rawSightingCount(): Long =
        db.openHelper.readableDatabase
            .query(androidx.sqlite.db.SimpleSQLiteQuery("SELECT COUNT(*) FROM sightings"))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
}
