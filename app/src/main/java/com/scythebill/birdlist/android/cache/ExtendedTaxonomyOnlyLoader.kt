package com.scythebill.birdlist.android.cache

import android.content.ContentResolver
import android.net.Uri
import com.scythebill.birdlist.model.taxa.Taxonomy
import com.scythebill.birdlist.model.xml.ExtendedTaxonomyNodeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import com.scythebill.xml.BaseNodeParser
import com.scythebill.xml.NodeParser
import com.scythebill.xml.ParseContext
import com.scythebill.xml.TreeBuilder
import com.scythebill.xml.XmlAttributes

/**
 * Parses a single embedded extended `<taxonomy>` element out of a `.bsxm`
 * file, ignoring everything else: locations, trips, sightings, checklists,
 * prefs, and any *other* embedded extended taxonomies. Reuses the shared
 * `:xml-parser`/`:model` parsing infrastructure ([TreeBuilder],
 * [ExtendedTaxonomyNodeParser]) so the result matches what a full
 * [XmlReportSetImport] parse would produce for that taxonomy, without
 * building the rest of the [com.scythebill.birdlist.model.sighting.ReportSet]
 * object graph.
 */
class ExtendedTaxonomyOnlyLoader(private val contentResolver: ContentResolver) {

    suspend fun load(uri: Uri, targetId: String): Taxonomy? = withContext(Dispatchers.IO) {
        BsxmContentSource.openReader(contentResolver, uri).use { reader ->
            TreeBuilder<Taxonomy>(null, Taxonomy::class)
                .parse(InputSource(reader), TargetTaxonomyParser(targetId))
        }
    }

    private class TargetTaxonomyParser(private val targetId: String) : BaseNodeParser() {
        private var result: Taxonomy? = null

        override fun startChildElement(
            context: ParseContext,
            namespaceURI: String?,
            localName: String,
            attrs: XmlAttributes,
        ): NodeParser {
            if (localName == ExtendedTaxonomyNodeParser.ELEMENT_TAXONOMY &&
                attrs.getValue("id") == targetId
            ) {
                ExtendedTaxonomyNodeParser.startChecklistParsing(context)
                return ExtendedTaxonomyNodeParser()
            }
            // Everything else (locations, trips, sightings, checklists, prefs,
            // other extended taxonomies, etc.) is skipped without building any
            // objects for it.
            return BaseNodeParser.getIgnoreParser()
        }

        override fun addCompletedChild(
            context: ParseContext,
            namespaceURI: String?,
            localName: String,
            child: Any?,
        ) {
            if (child is Taxonomy) {
                ExtendedTaxonomyNodeParser.endChecklistParsing(context)
                result = child
            }
        }

        override fun endElement(
            context: ParseContext,
            namespaceURI: String?,
            localName: String,
        ): Any? = result
    }
}
