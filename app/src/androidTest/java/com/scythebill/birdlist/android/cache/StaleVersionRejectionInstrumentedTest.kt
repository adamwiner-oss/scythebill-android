package com.scythebill.birdlist.android.cache

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.scythebill.birdlist.android.di.AppContainer
import com.scythebill.birdlist.model.xml.XmlTaxonImport
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A `.bsxm` written by an older Scythebill version parses fine as XML but
 * is tagged with its original `loadedVersion`; Android has no upgrader
 * chain, so [ReportSetLoader] must reject it outright and the caller must
 * never reach [SightingCacheBuilder].
 */
@RunWith(AndroidJUnit4::class)
class StaleVersionRejectionInstrumentedTest {

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
    fun staleVersion_isRejectedAndNoCacheIsWritten() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val taxonomy = openAsset("testTaxonomy.xml").use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { XmlTaxonImport().importTaxa(it)!! }
        }
        val taxonomyMappings = AppContainer().taxonomyMappings()

        // Copy the stale-version fixture out to a real file so it can be
        // read back through a genuine ContentResolver/file Uri, exercising
        // the same code path FileLoadViewModel drives in the app.
        val staleFile = File(context.cacheDir, "staleSightings.xml")
        openAsset("staleSightings.xml").use { input ->
            staleFile.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = Uri.fromFile(staleFile)

        val loader = ReportSetLoader(context.contentResolver, taxonomy, taxonomyMappings)
        val result = loader.load(uri)

        assertThat(result).isInstanceOf(ReportSetLoadResult.VersionMismatch::class.java)
        assertThat((result as ReportSetLoadResult.VersionMismatch).fileVersion).isEqualTo("1.0.0")

        // No cache was ever written for this rejected load.
        assertThat(dao.getMetadata()).isNull()
    }
}
