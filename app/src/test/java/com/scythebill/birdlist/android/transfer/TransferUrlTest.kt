package com.scythebill.birdlist.android.transfer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransferUrlTest {

    @Test
    fun `accepts a well-formed private-IP transfer URL`() {
        val url = TransferUrl.parse("http://192.168.1.42:51234/transfer/abc-123")
        assertThat(url).isNotNull()
    }

    @Test
    fun `accepts each private range`() {
        assertThat(TransferUrl.parse("http://10.0.0.5:8080/transfer/tok")).isNotNull()
        assertThat(TransferUrl.parse("http://172.16.0.5:8080/transfer/tok")).isNotNull()
        assertThat(TransferUrl.parse("http://172.31.255.255:8080/transfer/tok")).isNotNull()
        assertThat(TransferUrl.parse("http://192.168.0.1:8080/transfer/tok")).isNotNull()
    }

    @Test
    fun `rejects a public-IP host`() {
        assertThat(TransferUrl.parse("http://8.8.8.8:8080/transfer/tok")).isNull()
    }

    @Test
    fun `rejects a non-private 172 range`() {
        assertThat(TransferUrl.parse("http://172.32.0.5:8080/transfer/tok")).isNull()
    }

    @Test
    fun `rejects https scheme`() {
        assertThat(TransferUrl.parse("https://192.168.1.42:51234/transfer/abc-123")).isNull()
    }

    @Test
    fun `rejects a hostname instead of an IP`() {
        assertThat(TransferUrl.parse("http://desktop.local:51234/transfer/abc-123")).isNull()
    }

    @Test
    fun `rejects wrong path`() {
        assertThat(TransferUrl.parse("http://192.168.1.42:51234/other/abc-123")).isNull()
    }

    @Test
    fun `rejects malformed URLs`() {
        assertThat(TransferUrl.parse("not a url")).isNull()
    }
}
