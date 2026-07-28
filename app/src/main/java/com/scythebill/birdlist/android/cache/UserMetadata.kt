package com.scythebill.birdlist.android.cache

import com.scythebill.birdlist.android.data.UserDescriptor
import com.scythebill.birdlist.model.user.UserSet

private const val FIELD_SEPARATOR = ''
private const val RECORD_SEPARATOR = ''

/** Encodes id/display-name pairs for [CacheMetadataEntity.userNamesJson]. */
fun encodeUserDescriptors(userSet: UserSet?): String? {
    val users = userSet?.allUsers() ?: return null
    if (users.isEmpty()) return null
    return users.joinToString(RECORD_SEPARATOR.toString()) { user ->
        "${user.id()}$FIELD_SEPARATOR${user.name() ?: user.abbreviation() ?: user.id()}"
    }
}

/** Inverse of [encodeUserDescriptors]. */
fun decodeUserDescriptors(encoded: String?): List<UserDescriptor> {
    if (encoded.isNullOrEmpty()) return emptyList()
    return encoded.split(RECORD_SEPARATOR).map { record ->
        val (id, name) = record.split(FIELD_SEPARATOR, limit = 2)
        UserDescriptor(id, name)
    }
}
