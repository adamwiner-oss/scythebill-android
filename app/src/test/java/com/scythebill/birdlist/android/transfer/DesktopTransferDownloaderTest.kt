package com.scythebill.birdlist.android.transfer

import com.google.common.truth.Truth.assertThat
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopTransferDownloaderTest {

    private val tempFolder = TemporaryFolder().apply { create() }
    private lateinit var server: FakeHttpServer

    @Before
    fun setUp() {
        server = FakeHttpServer()
    }

    @After
    fun tearDown() {
        server.stop()
        tempFolder.delete()
    }

    @Test
    fun `downloads response bytes to the destination file`() = runBlocking {
        val body = "<reportSet>hello</reportSet>".toByteArray(Charsets.UTF_8)
        server.respond("/transfer/tok", 200, body)
        server.start()

        val dest = tempFolder.newFile("received.bsxm")
        val url = URL("http://127.0.0.1:${server.port}/transfer/tok")
        DesktopTransferDownloader.download(url, dest)

        assertThat(dest.readBytes()).isEqualTo(body)
    }

    @Test
    fun `throws when the server returns a non-200 status`() = runBlocking {
        server.respond("/transfer/tok", 404)
        server.start()

        val dest = tempFolder.newFile("received.bsxm")
        val url = URL("http://127.0.0.1:${server.port}/transfer/tok")
        var threw = false
        try {
            DesktopTransferDownloader.download(url, dest)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertThat(threw).isTrue()
    }
}
