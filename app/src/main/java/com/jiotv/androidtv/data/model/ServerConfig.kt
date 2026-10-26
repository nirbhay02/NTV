package com.jiotv.androidtv.data.model

/**
 * Configuration for connecting to the local or remote jiotv_go server.
 */
data class ServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 5001,
    val quality: String = "high", // low, medium, high
    val categoryMode: String = "split",
    val customUserAgent: String = "jiotv_go/1.0 (AndroidTV; Media3)",
    val autoRefreshMinutes: Int = 60
) {
    /**
     * Base HTTP URL of the jiotv_go server (e.g., http://127.0.0.1:5001)
     */
    val baseUrl: String
        get() = "http://${host.trim()}:$port"

    /**
     * M3U Playlist endpoint with quality & category options
     */
    val playlistUrl: String
        get() = "$baseUrl/playlist.m3u?q=$quality&c=$categoryMode"

    /**
     * EPG XML endpoint
     */
    val epgUrl: String
        get() = "$baseUrl/epg.xml.gz"

    /**
     * Server health/status endpoint
     */
    val statusUrl: String
        get() = "$baseUrl/"

    /**
     * Resolves stream URL against the backend baseUrl if it's relative or needs proxying.
     */
    fun resolveStreamUrl(streamUrl: String): String {
        val trimmed = streamUrl.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                // If it's an absolute URL, check if it points to a proxy path or CDN
                trimmed
            }
            trimmed.startsWith("/") -> {
                "$baseUrl$trimmed"
            }
            else -> {
                "$baseUrl/$trimmed"
            }
        }
    }
}
