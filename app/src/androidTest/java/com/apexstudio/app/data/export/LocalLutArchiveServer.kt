package com.apexstudio.app.data.export

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket

/** One-request, loopback-only HTTP fixture for exercising the real pack downloader. */
internal class LocalLutArchiveServer(private val archiveBytes: ByteArray) : Closeable {
    private val socket = ServerSocket(0, 1, InetAddress.getByName("localhost"))
    val url: String = "http://localhost:${socket.localPort}/lut-filters-vol-01.zip"

    @Volatile
    private var requestLine: String? = null

    @Volatile
    private var serverFailure: Throwable? = null

    private val worker = Thread({
        try {
            socket.accept().use { connection ->
                connection.soTimeout = 30_000
                val request = connection.getInputStream().bufferedReader(Charsets.US_ASCII)
                requestLine = request.readLine()
                while (!request.readLine().isNullOrEmpty()) Unit

                val responseHeaders = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/zip\r\n")
                    append("Content-Length: ${archiveBytes.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(Charsets.US_ASCII)
                connection.getOutputStream().apply {
                    write(responseHeaders)
                    write(archiveBytes)
                    flush()
                }
            }
        } catch (failure: Throwable) {
            if (!socket.isClosed) serverFailure = failure
        }
    }, "apexstudio-lut-pack-http-fixture").apply {
        isDaemon = true
        start()
    }

    fun awaitServed() {
        worker.join(30_000L)
        check(!worker.isAlive) { "Timed out waiting for the LUT archive request" }
        serverFailure?.let { throw IllegalStateException("Local LUT archive server failed", it) }
        check(requestLine?.startsWith("GET /lut-filters-vol-01.zip ") == true) {
            "Unexpected LUT archive request: $requestLine"
        }
    }

    override fun close() {
        if (!socket.isClosed) socket.close()
        if (worker.isAlive) worker.interrupt()
        worker.join(1_000L)
    }
}
