package com.scythebill.birdlist.android.transfer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransferQrPayloadTest {

    @Test
    fun `extracts the ip field`() {
        val raw = """{"ip":"http://192.168.1.5:54321/transfer/abc","local":"http://Adams-MacBook.local:54321/transfer/abc"}"""
        assertThat(TransferQrPayload.parseIpUrl(raw)).isEqualTo("http://192.168.1.5:54321/transfer/abc")
    }

    @Test
    fun `extracts the ip field when local is absent`() {
        val raw = """{"ip":"http://192.168.1.5:54321/transfer/abc"}"""
        assertThat(TransferQrPayload.parseIpUrl(raw)).isEqualTo("http://192.168.1.5:54321/transfer/abc")
    }

    @Test
    fun `extracts the ip field when local is null`() {
        val raw = """{"ip":"http://192.168.1.5:54321/transfer/abc","local":null}"""
        assertThat(TransferQrPayload.parseIpUrl(raw)).isEqualTo("http://192.168.1.5:54321/transfer/abc")
    }

    @Test
    fun `returns null for a bare URL, not JSON`() {
        assertThat(TransferQrPayload.parseIpUrl("http://192.168.1.5:54321/transfer/abc")).isNull()
    }

    @Test
    fun `returns null when the ip field is missing`() {
        assertThat(TransferQrPayload.parseIpUrl("""{"local":"http://foo.local:1/transfer/abc"}""")).isNull()
    }

    @Test
    fun `returns null for malformed JSON`() {
        assertThat(TransferQrPayload.parseIpUrl("not json")).isNull()
    }
}
