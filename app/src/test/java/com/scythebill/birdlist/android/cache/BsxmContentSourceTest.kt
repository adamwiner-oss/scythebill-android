package com.scythebill.birdlist.android.cache

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BsxmContentSourceTest {

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `plain xml passes through unchanged`() {
        val xml = "<reportSet></reportSet>"
        val reader = BsxmContentSource.openReader(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        assertEquals(xml, reader.readText())
    }

    @Test
    fun `zip with exactly one bsxm entry is unwrapped`() {
        val xml = "<reportSet>one</reportSet>"
        val bytes = zipOf("data.bsxm" to xml)
        val reader = BsxmContentSource.openReader(ByteArrayInputStream(bytes))
        assertEquals(xml, reader.readText())
    }

    @Test
    fun `zip with no bsxm entries throws`() {
        val bytes = zipOf("readme.txt" to "not a report")
        assertThrows(IllegalStateException::class.java) {
            BsxmContentSource.openReader(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `zip with multiple bsxm entries throws`() {
        val bytes = zipOf("a.bsxm" to "<reportSet>a</reportSet>", "b.bsxm" to "<reportSet>b</reportSet>")
        assertThrows(IllegalStateException::class.java) {
            BsxmContentSource.openReader(ByteArrayInputStream(bytes))
        }
    }
}
