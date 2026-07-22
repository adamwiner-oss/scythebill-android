package com.scythebill.birdlist.android.cache

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.util.zip.ZipInputStream

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/**
 * Opens a [Reader] over the `.bsxm` XML content at [uri], transparently
 * unwrapping a `.zip` wrapper if present. A zip must contain exactly one
 * `.bsxm` entry; zero or more than one is treated as an error.
 */
object BsxmContentSource {
    fun openReader(contentResolver: ContentResolver, uri: Uri): Reader {
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open $uri")
        return openReader(input)
    }

    internal fun openReader(input: InputStream): Reader {
        val buffered = BufferedInputStream(input)

        buffered.mark(ZIP_MAGIC.size)
        val header = ByteArray(ZIP_MAGIC.size)
        val read = buffered.read(header)
        buffered.reset()

        if (read != ZIP_MAGIC.size || !header.contentEquals(ZIP_MAGIC)) {
            return InputStreamReader(buffered, Charsets.UTF_8)
        }

        return buffered.use { zipStream ->
            var bsxmContent: String? = null
            var matchCount = 0
            ZipInputStream(zipStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".bsxm")) {
                        matchCount++
                        if (matchCount == 1) {
                            bsxmContent = zis.reader(Charsets.UTF_8).readText()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (matchCount != 1) {
                throw IllegalStateException(
                    if (matchCount == 0) {
                        "Zip file does not contain a .bsxm file"
                    } else {
                        "Zip file contains more than one .bsxm file"
                    }
                )
            }
            StringReader(bsxmContent!!)
        }
    }
}
