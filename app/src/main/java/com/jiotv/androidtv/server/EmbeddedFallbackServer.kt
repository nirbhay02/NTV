package com.jiotv.androidtv.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class EmbeddedFallbackServer private constructor() {

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    companion object {
        private const val TAG = "EmbeddedFallbackServer"
        val instance: EmbeddedFallbackServer by lazy { EmbeddedFallbackServer() }
    }

    fun start(scope: CoroutineScope, port: Int) {
        if (serverJob != null && serverSocket?.isClosed == false) {
            return
        }

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket
                Log.i(TAG, "Fallback HTTP server listening on port $port")

                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind fallback server socket: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream())

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"

            when {
                path.startsWith("/playlist.m3u") -> {
                    val m3u = generateSampleM3u()
                    sendResponse(writer, 200, "OK", "application/vnd.apple.mpegurl", m3u)
                }
                path == "/status" || path == "/ping" -> {
                    sendResponse(writer, 200, "OK", "application/json", """{"status":"ok","mode":"fallback"}""")
                }
                else -> {
                    sendResponse(writer, 200, "OK", "text/plain", "JioTV AndroidTV Fallback Server Active")
                }
            }
        }
    }

    private fun sendResponse(
        writer: PrintWriter,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        writer.println("HTTP/1.1 $statusCode $statusText")
        writer.println("Content-Type: $contentType; charset=utf-8")
        writer.println("Content-Length: ${body.toByteArray().size}")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Connection: close")
        writer.println()
        writer.print(body)
        writer.flush()
    }

    private fun generateSampleM3u(): String {
        return """
            #EXTM3U
            #EXTINF:-1 tvg-id="101" tvg-name="Zee TV HD" tvg-logo="https://raw.githubusercontent.com/jiotv-go/jiotv_go/main/assets/zee_tv.png" group-title="Entertainment - Hindi", Zee TV HD
            http://127.0.0.1:5001/render.m3u8?channel=101
            #EXTINF:-1 tvg-id="102" tvg-name="Zee Cinema HD" tvg-logo="https://raw.githubusercontent.com/jiotv-go/jiotv_go/main/assets/zee_cinema.png" group-title="Movies - Hindi", Zee Cinema HD
            http://127.0.0.1:5001/render.m3u8?channel=102
            #EXTINF:-1 tvg-id="103" tvg-name="&pictures HD" tvg-logo="https://raw.githubusercontent.com/jiotv-go/jiotv_go/main/assets/andpictures.png" group-title="Movies - Hindi", &pictures HD
            http://127.0.0.1:5001/render.m3u8?channel=103
        """.trimIndent()
    }

    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
        } catch (_: Exception) {}
    }
}
