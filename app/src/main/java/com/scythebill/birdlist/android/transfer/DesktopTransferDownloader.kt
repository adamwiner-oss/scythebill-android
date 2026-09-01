package com.scythebill.birdlist.android.transfer

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the `.bsxm` bytes served by the desktop "send to phone" server
 * at a [TransferUrl]-validated [url], streaming them into app-private
 * storage the same way `MainActivity.copyToAppPrivateStorage` handles
 * ACTION_VIEW Uris.
 */
object DesktopTransferDownloader {
    suspend fun download(url: URL, destFile: File) = withContext(Dispatchers.IO) {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            val status = connection.responseCode
            check(status == HttpURLConnection.HTTP_OK) { "Desktop returned HTTP $status" }
            connection.inputStream.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }
}
