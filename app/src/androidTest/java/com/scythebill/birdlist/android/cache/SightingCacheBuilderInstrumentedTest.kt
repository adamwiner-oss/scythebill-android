package com.scythebill.birdlist.android.cache

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.base.Optional as GuavaOptional
import com.google.common.truth.Truth.assertThat
import com.scythebill.birdlist.android.di.AppContainer
import com.scythebill.birdlist.model.xml.XmlReportSetImport
import com.scythebill.birdlist.model.xml.XmlTaxonImport
import java.io.InputStreamReader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parses `testSightings.xml` (6 hand-checked sightings against a small
 * fixture taxonomy) and runs it through [SightingCacheBuilder], then
 * asserts the flattened Room rows against values worked out by hand from
 * that fixture - see the per-assertion comments below for the reasoning.
 */
@RunWith(AndroidJUnit4::class)
class SightingCacheBuilderInstrumentedTest {

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

    private fun rawCount(sql: String): Long =
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql)).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    @Test
    fun rebuild_flattensReportSetIntoExpectedRows() = runTest {
        val taxonomy = openAsset("testTaxonomy.xml").use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { XmlTaxonImport().importTaxa(it)!! }
        }
        val taxonomyMappings = AppContainer().taxonomyMappings()
        val reportSet = openAsset("testSightings.xml").use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                XmlReportSetImport().importReportSet(
                    reader, taxonomy, GuavaOptional.absent(), taxonomyMappings
                )
            }
        }

        SightingCacheBuilder(dao).rebuild(
            reportSet = reportSet,
            sourceUri = "content://test/testSightings.xml",
            sourceSize = 1234L,
            sourceLastModified = 5678L,
            taxonomyVersion = reportSet.loadedVersion,
        )

        // locations: country "unst" plus its 3 direct children (wa, withtab,
        // notab), all defined in testSightings.xml's <locations> block.
        assertThat(dao.getAllLocations()).hasSize(4)

        // All 6 sightings are recorded at loc="wa" directly.
        assertThat(dao.sightingsUnderLocation("wa")).hasSize(6)
        // The ancestor-closure join also rolls them up to the parent
        // country "unst" - this is the location-subtree-count behavior.
        assertThat(dao.sightingsUnderLocation("unst")).hasSize(6)
        // A sibling location with no sightings under it stays empty.
        assertThat(dao.sightingsUnderLocation("withtab")).isEmpty()

        // location_ancestors: each of the 4 locations is its own depth-0
        // ancestor (4 rows), plus the 3 direct children of "unst" each add
        // one depth-1 row back to "unst" (3 rows) = 7 total.
        assertThat(rawCount("SELECT COUNT(*) FROM location_ancestors")).isEqualTo(7L)

        // sighting_taxa: sighting 1 (taxon="spRheapen") contributes 1 row;
        // the other 5 sightings (sp="spRheaame,spRheapen") contribute 2
        // rows each = 1 + 5*2 = 11 total.
        assertThat(rawCount("SELECT COUNT(*) FROM sighting_taxa")).isEqualTo(11L)

        // firstForTaxon ("lifer") is computed per distinct taxon-id-set:
        // - {spRheapen} has only sighting 1 (1998-09-19) -> lifer.
        // - {spRheaame, spRheapen} has 5 sightings; the earliest-dated one
        //   is date="2000" (2000-01-01), earlier than date="2000-11"
        //   (2000-11-01) and the 2001/2002/2003 sightings -> lifer.
        // So exactly 2 of the 6 sightings should be flagged.
        assertThat(rawCount("SELECT COUNT(*) FROM sightings WHERE firstForTaxon = 1")).isEqualTo(2L)
        assertThat(rawCount("SELECT COUNT(*) FROM sightings")).isEqualTo(6L)

        val metadata = dao.getMetadata()
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.taxonomyVersion).isEqualTo(reportSet.loadedVersion)
    }
}
