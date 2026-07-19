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
 * Parses `testSightings.xml` (7 hand-checked sightings against a small
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

        // All 7 sightings are recorded at loc="wa" directly.
        assertThat(dao.sightingsUnderLocation("wa")).hasSize(7)
        // The ancestor-closure join also rolls them up to the parent
        // country "unst" - this is the location-subtree-count behavior.
        assertThat(dao.sightingsUnderLocation("unst")).hasSize(7)
        // A sibling location with no sightings under it stays empty.
        assertThat(dao.sightingsUnderLocation("withtab")).isEmpty()

        // location_ancestors: each of the 4 locations is its own depth-0
        // ancestor (4 rows), plus the 3 direct children of "unst" each add
        // one depth-1 row back to "unst" (3 rows) = 7 total.
        assertThat(rawCount("SELECT COUNT(*) FROM location_ancestors")).isEqualTo(7L)

        // sighting_taxa: sighting 1 (taxon="spRheapen") contributes 1 row;
        // 5 sightings (sp/hybrid="spRheaame,spRheapen") contribute 2 rows
        // each; 1 sighting (sp="sspRheaameame,sspRheaameint", two
        // subspecies of the same species) contributes 2 rows.
        // 1 + 5*2 + 2 = 13 total.
        assertThat(rawCount("SELECT COUNT(*) FROM sighting_taxa")).isEqualTo(13L)

        // firstForTaxon ("lifer") is computed per distinct taxon-id-set:
        // - {spRheapen} has only sighting 1 (1998-09-19) -> lifer.
        // - {spRheaame, spRheapen} has 5 sightings; the earliest-dated one
        //   is date="2000" (2000-01-01), earlier than date="2000-11"
        //   (2000-11-01) and the 2001/2002/2003 sightings -> lifer.
        // - {sspRheaameame, sspRheaameint} has only the date="2004"
        //   sighting -> lifer.
        // So exactly 3 of the 7 sightings should be flagged.
        assertThat(rawCount("SELECT COUNT(*) FROM sightings WHERE firstForTaxon = 1")).isEqualTo(3L)
        assertThat(rawCount("SELECT COUNT(*) FROM sightings")).isEqualTo(7L)

        // raisedTaxonType/raisedGroupKey/raisedDisplayName are computed by
        // raising each sighting's taxon to species level
        // (SightingTaxon.resolve().resolveParentOfType(species)):
        // - The plain single sighting stays SINGLE.
        // - The sp="sspRheaameame,sspRheaameint" sighting names two
        //   subspecies of the SAME species (Greater Rhea), so once raised
        //   it collapses to SINGLE too - it must NOT be counted as SP here,
        //   which is the critical countability nuance this cache column
        //   exists to capture.
        assertThat(rawCount("SELECT COUNT(*) FROM sightings WHERE raisedTaxonType = 'SINGLE'"))
            .isEqualTo(2L)
        // The 5 sp="spRheaame,spRheapen"/hybrid="spRheaame,spRheapen"
        // sightings name two genuinely different species (Greater Rhea vs
        // Lesser Rhea), so raising does not collapse them: 4 stay SP, 1
        // stays HYBRID.
        assertThat(rawCount("SELECT COUNT(*) FROM sightings WHERE raisedTaxonType = 'SP'"))
            .isEqualTo(4L)
        assertThat(rawCount("SELECT COUNT(*) FROM sightings WHERE raisedTaxonType = 'HYBRID'"))
            .isEqualTo(1L)

        val spSighting = dao.sightingsUnderLocation("wa").first { it.raisedTaxonType == "SP" }
        assertThat(spSighting.raisedGroupKey).isEqualTo("spRheaame,spRheapen")
        assertThat(spSighting.raisedDisplayName).isNotNull()

        val hybridSighting = dao.sightingsUnderLocation("wa").first { it.raisedTaxonType == "HYBRID" }
        assertThat(hybridSighting.raisedGroupKey).isEqualTo("spRheaame,spRheapen")
        assertThat(hybridSighting.raisedDisplayName).isNotNull()
        // sp. joins names with "/", hybrid joins with " x " - the two
        // should not collapse to the same display string.
        assertThat(hybridSighting.raisedDisplayName).isNotEqualTo(spSighting.raisedDisplayName)

        val metadata = dao.getMetadata()
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.taxonomyVersion).isEqualTo(reportSet.loadedVersion)
    }
}
