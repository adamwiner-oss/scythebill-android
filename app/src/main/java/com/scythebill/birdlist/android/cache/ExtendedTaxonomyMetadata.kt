package com.scythebill.birdlist.android.cache

import com.scythebill.birdlist.android.data.ExtendedTaxonomyDescriptor
import com.scythebill.birdlist.model.taxa.Taxonomy

private const val FIELD_SEPARATOR = ''
private const val RECORD_SEPARATOR = ''

/** Encodes id/name pairs for [CacheMetadataEntity.extendedTaxonomyNamesJson]. */
fun encodeExtendedTaxonomyDescriptors(taxonomies: Collection<Taxonomy>): String? {
    if (taxonomies.isEmpty()) return null
    return taxonomies.joinToString(RECORD_SEPARATOR.toString()) { taxonomy ->
        "${taxonomy.getId()}$FIELD_SEPARATOR${taxonomy.getName()}"
    }
}

/** Inverse of [encodeExtendedTaxonomyDescriptors]. */
fun decodeExtendedTaxonomyDescriptors(encoded: String?): List<ExtendedTaxonomyDescriptor> {
    if (encoded.isNullOrEmpty()) return emptyList()
    return encoded.split(RECORD_SEPARATOR).map { record ->
        val (id, name) = record.split(FIELD_SEPARATOR, limit = 2)
        ExtendedTaxonomyDescriptor(id, name)
    }
}
