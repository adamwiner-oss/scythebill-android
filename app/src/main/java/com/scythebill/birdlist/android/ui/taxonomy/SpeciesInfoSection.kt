package com.scythebill.birdlist.android.ui.taxonomy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.scythebill.birdlist.model.taxa.Species
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils

/** Human-readable label for an IUCN-style conservation status. */
private fun Species.Status.displayName(): String = when (this) {
    Species.Status.LC -> "Least Concern"
    Species.Status.NE -> "Not Evaluated"
    Species.Status.DD -> "Data Deficient"
    Species.Status.NT -> "Near Threatened"
    Species.Status.VU -> "Vulnerable"
    Species.Status.EN -> "Endangered"
    Species.Status.CR -> "Critically Endangered"
    Species.Status.EW -> "Extinct in the Wild"
    Species.Status.EX -> "Extinct"
    Species.Status.IN -> "Feral form"
    Species.Status.DO -> "Domestic form"
    Species.Status.UN -> "Undescribed"
}

/**
 * Species-info content: name, status, alternate names, range, taxonomic
 * info, and links to a taxon-account page and Avibase — ported from the
 * desktop app's `SpeciesInfoDescriber`, minus the parts that don't apply on
 * Android (Clements/IOC cross-reference, checklist status, range map).
 */
@Composable
fun SpeciesInfoSection(taxon: Taxon, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val species = taxon as? Species

    Column(modifier = modifier) {
        val status = species?.getStatus()
        if (status != null && status != Species.Status.LC) {
            Text(
                status.displayName(),
                modifier = Modifier.padding(top = 8.dp),
                fontWeight = FontWeight.Bold,
            )
        }

        val alternateNames = species?.alternateCommonNames
        if (!alternateNames.isNullOrEmpty()) {
            LabeledText("Alternate names:", alternateNames.joinToString(" / "))
        }

        val range = species?.let { TaxonUtils.getRange(it) }
        if (range != null) {
            LabeledText("Range:", range)
        }

        val taxonomicInfo = species?.taxonomicInfo
        if (taxonomicInfo != null) {
            LabeledText("Taxonomy:", taxonomicInfo)
        }

        val accountUrl = taxon.getAccountId()?.let { taxon.getTaxonomy()?.getTaxonAccountUrl(it) }
        if (accountUrl != null) {
            LinkText(
                text = "eBird account",
                modifier = Modifier.padding(top = 8.dp),
                onClick = { uriHandler.openUri(accountUrl) },
            )
        }

        val conceptId = taxon.getConceptId()
        if (conceptId != null) {
            LinkText(
                text = "Avibase",
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    uriHandler.openUri("https://avibase.bsc-eoc.org/species.jsp?avibaseid=$conceptId")
                },
            )
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(value)
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clickable(onClick = onClick),
        style = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
}
