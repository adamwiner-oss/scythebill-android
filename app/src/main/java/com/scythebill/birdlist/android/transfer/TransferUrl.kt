package com.scythebill.birdlist.android.transfer

import java.net.URL

/**
 * Validates that a scanned QR payload matches the exact shape the desktop
 * "send to phone" server produces — `http://<private-ip>:<port>/transfer/<token>`
 * — before any network request is made.
 */
object TransferUrl {
    private val TOKEN_PATH = Regex("^/transfer/[A-Za-z0-9-]+$")

    fun parse(raw: String): URL? {
        val url = try {
            URL(raw)
        } catch (_: Exception) {
            return null
        }
        if (url.protocol != "http") return null
        if (!TOKEN_PATH.matches(url.path)) return null
        if (!isPrivateIPv4(url.host)) return null
        return url
    }

    private fun isPrivateIPv4(host: String): Boolean {
        val parts = host.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b, _, _) = parts
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
    }
}
