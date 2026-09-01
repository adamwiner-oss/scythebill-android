package com.scythebill.birdlist.android.transfer

/**
 * Parses the QR payload the desktop "send to phone" server encodes:
 * `{"ip": "<numeric-IP URL>", "local": "<.local URL, or absent/null>"}`.
 * Android has no built-in mDNS resolution, so only [ip] is used here —
 * `local` exists for iOS, which does resolve `.local` hostnames natively.
 *
 * Uses a targeted regex rather than a JSON library: `org.json` (bundled in
 * the Android SDK) throws in plain JUnit tests without Robolectric, and the
 * payload's shape is small and fixed enough not to need a real parser.
 */
object TransferQrPayload {
    private val IP_FIELD = Regex(""""ip"\s*:\s*"([^"]*)"""")

    fun parseIpUrl(raw: String): String? {
        return IP_FIELD.find(raw)?.groupValues?.get(1)
    }
}
