package com.scythebill.birdlist.android.cache

/** Decodes the JSON string array produced by `SightingCacheBuilder.encodePhotos`. */
fun decodePhotoUris(json: String?): List<String> {
    if (json.isNullOrEmpty()) return emptyList()
    val matches = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(json)
    return matches.map { it.groupValues[1].replace("\\\"", "\"") }.toList()
}
