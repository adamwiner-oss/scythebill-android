package com.scythebill.birdlist.android.xml

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.base.Optional as GuavaOptional
import com.google.common.truth.Truth.assertThat
import com.scythebill.birdlist.android.di.AppContainer
import com.scythebill.birdlist.model.sighting.ReportSets
import com.scythebill.birdlist.model.xml.XmlReportSetImport
import com.scythebill.birdlist.model.xml.XmlTaxonImport
import java.io.InputStreamReader
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real device/emulator test (not a desktop-JVM unit test) so the SAX
 * provider actually in play is Android's own XML stack, not the JVM's -
 * this is the difference the ported `model`/`xml-parser` JUnit suite can't
 * catch on its own.
 */
@RunWith(AndroidJUnit4::class)
class XmlImportPipelineInstrumentedTest {

    private fun openAsset(name: String) =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name)

    @Test
    fun taxonomyThenReportSet_importEndToEnd() {
        val taxonomy = openAsset("testTaxonomy.xml").use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                XmlTaxonImport().importTaxa(reader)
            }
        }
        assertThat(taxonomy).isNotNull()

        val taxonomyMappings = AppContainer().taxonomyMappings()
        val reportSet = openAsset("testSightings.xml").use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                XmlReportSetImport().importReportSet(
                    reader, taxonomy, GuavaOptional.absent(), taxonomyMappings
                )
            }
        }

        assertThat(reportSet.sightings).hasSize(6)
        assertThat(reportSet.loadedVersion).isEqualTo(ReportSets.VERSION_FORMAT_CURRENT)
    }
}
