package com.scythebill.birdlist.android.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.scythebill.birdlist.model.taxa.Taxon
import com.scythebill.birdlist.model.taxa.TaxonUtils
import com.scythebill.birdlist.model.taxa.names.NamesPreferences

/**
 * The common name for [this] taxon in the user's chosen local-names locale
 * (see [com.scythebill.birdlist.android.data.NamesPreferencesStore]),
 * falling back to the taxonomy's built-in English name. Null if the taxon
 * has no common name at all, matching [Taxon.getCommonName]'s own contract.
 */
fun Taxon.localizedCommonName(): String? {
    val ownName = getCommonName() ?: return null
    return getTaxonomy()?.localNames?.getCommonName(this) ?: ownName
}

/**
 * The primary/secondary name to show for this taxon (a species, family,
 * order, etc.) given mode. Whichever part is the scientific name is
 * flagged so callers can italicize just that part; falls back to the
 * scientific name alone when there's no common name to pair it with.
 */
data class TaxonNameParts(
    val primary: String,
    val primaryIsScientific: Boolean,
    val secondary: String?,
    val secondaryIsScientific: Boolean,
)

fun Taxon.namePartsFor(mode: NamesPreferences.ScientificOrCommon): TaxonNameParts {
    val common = localizedCommonName()
    val scientific = TaxonUtils.getFullName(this) ?: getName() ?: ""
    return when (mode) {
        NamesPreferences.ScientificOrCommon.SCIENTIFIC_ONLY ->
            TaxonNameParts(scientific, true, null, false)
        NamesPreferences.ScientificOrCommon.COMMON_ONLY ->
            if (common != null) TaxonNameParts(common, false, null, false)
            else TaxonNameParts(scientific, true, null, false)
        NamesPreferences.ScientificOrCommon.COMMON_FIRST ->
            if (common != null) TaxonNameParts(common, false, scientific, true)
            else TaxonNameParts(scientific, true, null, false)
        NamesPreferences.ScientificOrCommon.SCIENTIFIC_FIRST ->
            if (common != null) TaxonNameParts(scientific, true, common, false)
            else TaxonNameParts(scientific, true, null, false)
    }
}

/** "Primary (Secondary)" (or just "Primary" when there's no secondary), as plain text. */
fun Taxon.formattedLabel(mode: NamesPreferences.ScientificOrCommon): String {
    val parts = namePartsFor(mode)
    return if (parts.secondary != null) "${parts.primary} (${parts.secondary})" else parts.primary
}

/** Same as [formattedLabel], but italicizing whichever part is the scientific name. */
fun Taxon.annotatedLabel(mode: NamesPreferences.ScientificOrCommon): AnnotatedString {
    val parts = namePartsFor(mode)
    return buildAnnotatedString {
        fun appendPart(text: String, italic: Boolean) {
            if (italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text) } else append(text)
        }
        appendPart(parts.primary, parts.primaryIsScientific)
        if (parts.secondary != null) {
            append(" (")
            appendPart(parts.secondary, parts.secondaryIsScientific)
            append(")")
        }
    }
}
