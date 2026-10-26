package com.jiotv.androidtv.data.parser

import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.data.model.ServerConfig
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parser for M3U / M3U8 playlists returned by the jiotv_go server.
 * Handles channel metadata, logo URLs, Zee network identification, and proxy stream URL resolution.
 */
class M3uParser(private val serverConfig: ServerConfig) {

    private val attributeRegex = Regex("""([a-zA-Z0-9\-_]+)="([^"]*)"""")

    /**
     * Parses an M3U string into a list of [Channel] items.
     */
    fun parse(m3uContent: String): List<Channel> {
        val lines = m3uContent.lineSequence()
        return parseLines(lines)
    }

    /**
     * Parses an input stream (efficient for large playlists).
     */
    fun parse(inputStream: InputStream): List<Channel> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        return parseLines(reader.lineSequence())
    }

    private fun parseLines(lines: Sequence<String>): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentTvgId = ""
        var currentTvgName = ""
        var currentLogo: String? = null
        var currentGroup = "Entertainment"
        var currentLanguage = "Hindi"
        var currentName = ""
        var currentChNo: Int? = null
        var currentLicenseKey: String? = null
        var currentLicenseType: String? = null
        var currentManifestType: String? = null
        var channelIndex = 1

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                // Parse attributes
                val attributes = parseAttributes(line)
                currentTvgId = attributes["tvg-id"]
                    ?: attributes["channel-id"]
                    ?: channelIndex.toString()
                currentTvgName = attributes["tvg-name"].orEmpty()
                currentLogo = attributes["tvg-logo"]
                
                val chNo = attributes["tvg-chno"] ?: attributes["chno"] ?: attributes["channel-number"]
                currentChNo = chNo?.toIntOrNull()

                val rawGroup = attributes["group-title"] ?: "Entertainment"
                if (rawGroup.contains(" - ")) {
                    val parts = rawGroup.split(" - ", limit = 2)
                    currentGroup = parts[0].trim()
                    currentLanguage = parts[1].trim()
                } else {
                    currentGroup = rawGroup.trim()
                    currentLanguage = attributes["tvg-language"] ?: "Hindi"
                }

                // Channel name is after the last comma
                val commaIndex = line.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < line.length - 1) {
                    line.substring(commaIndex + 1).trim()
                } else if (currentTvgName.isNotEmpty()) {
                    currentTvgName
                } else {
                    "Channel $channelIndex"
                }
            } else if (line.startsWith("#KODIPROP:", ignoreCase = true)) {
                val prop = line.substringAfter(":").trim()
                val eqIdx = prop.indexOf('=')
                if (eqIdx != -1) {
                    val key = prop.substring(0, eqIdx).trim().lowercase()
                    val value = prop.substring(eqIdx + 1).trim()
                    when {
                        key.contains("license_key") -> currentLicenseKey = value
                        key.contains("license_type") -> currentLicenseType = value
                        key.contains("manifest_type") -> currentManifestType = value
                    }
                }
            } else if (!line.startsWith("#")) {
                // This line contains the stream URL
                val rawUrl = line
                val resolvedUrl = serverConfig.resolveStreamUrl(rawUrl)

                val isHd = ChannelFilter.isHdChannel(currentName, currentGroup)
                val channelNumber = currentChNo ?: channelIndex
                val testChannel = Channel(
                    id = currentTvgId,
                    name = currentName,
                    number = channelNumber,
                    logoUrl = currentLogo?.let { serverConfig.resolveStreamUrl(it) },
                    streamUrl = resolvedUrl,
                    groupTitle = currentGroup,
                    language = currentLanguage,
                    isHd = isHd,
                    tvgName = currentTvgName,
                    licenseKeyUrl = currentLicenseKey,
                    licenseType = currentLicenseType,
                    manifestType = currentManifestType
                )
                val isZee = ChannelFilter.isZeeChannel(testChannel)

                channels.add(testChannel.copy(isZee = isZee))
                channelIndex++

                // Reset per-channel temp state
                currentTvgId = ""
                currentTvgName = ""
                currentLogo = null
                currentGroup = "Entertainment"
                currentLanguage = "Hindi"
                currentName = ""
                currentChNo = null
                currentLicenseKey = null
                currentLicenseType = null
                currentManifestType = null
            }
        }

        return channels
    }

    private fun parseAttributes(extinfLine: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (match in attributeRegex.findAll(extinfLine)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            map[key] = value
        }
        return map
    }
}
