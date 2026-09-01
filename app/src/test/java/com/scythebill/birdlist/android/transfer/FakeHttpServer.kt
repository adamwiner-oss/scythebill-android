package com.scythebill.birdlist.android.transfer

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Minimal single-connection-at-a-time HTTP/1.1 server for tests, built on
 * plain [ServerSocket] rather than [com.sun.net.httpserver.HttpServer] — the
 * `jdk.httpserver` module isn't on the classpath the Android Gradle Plugin
 * builds for unit-test compilation, so that class fails to resolve there
 * even though it ships in the JDK.
 */
class FakeHttpServer {
    private val serverSocket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val responses = mutableMapOf<String, Response>()
    private var acceptThread: Thread? = null

    val port: Int get() = serverSocket.localPort

    private class Response(val status: Int, val body: ByteArray)

    fun respond(path: String, status: Int, body: ByteArray = ByteArray(0)) {
        responses[path] = Response(status, body)
    }

    fun start() {
        acceptThread = Thread {
            while (!serverSocket.isClosed) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    break
                }
                socket.use { handle(it) }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun handle(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        while (true) {
            val line = reader.readLine()
            if (line.isNullOrEmpty()) break
        }
        val path = requestLine.split(" ").getOrNull(1) ?: "/"
        val response = responses[path]
        val status = response?.status ?: 404
        val body = response?.body ?: ByteArray(0)

        val output = socket.getOutputStream()
        val head = "HTTP/1.1 $status ${reasonPhrase(status)}\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(head.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun reasonPhrase(status: Int) = when (status) {
        200 -> "OK"
        404 -> "Not Found"
        else -> "Unknown"
    }

    fun stop() {
        serverSocket.close()
        acceptThread?.join(2_000)
    }
}
