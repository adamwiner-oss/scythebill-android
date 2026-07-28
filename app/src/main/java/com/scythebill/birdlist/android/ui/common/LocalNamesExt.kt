package com.scythebill.birdlist.android.ui.common

import com.scythebill.birdlist.model.taxa.Taxon

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
