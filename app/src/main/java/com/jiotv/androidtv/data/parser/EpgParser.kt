package com.jiotv.androidtv.data.parser

import android.util.Xml
import com.jiotv.androidtv.data.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

data class EpgData(
    val programsByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val nameToChannelId: Map<String, String> = emptyMap()
)

class EpgParser {

    /**
     * Parses XMLTV XML/GZIP input stream into an [EpgData] containing programs and channel display names.
     */
    // Rolling 72-hour retention window: preserves past 48-72h schedule data for 24h recurring time slot projection
    fun parseData(
        inputStream: InputStream,
        isGzip: Boolean = false,
        minStopEpoch: Long = System.currentTimeMillis() - 72 * 3600_000L,
        maxStartEpoch: Long = System.currentTimeMillis() + 48 * 3600_000L
    ): EpgData {
        val programsByChannel = mutableMapOf<String, MutableList<EpgProgram>>()
        val nameToChannelId = mutableMapOf<String, String>()
        val stream = if (isGzip) GZIPInputStream(inputStream) else inputStream
        stream.use { s ->
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(s, "UTF-8")
            }
            var eventType = parser.eventType
            var currentChannelTagId: String? = null
            var currentProgrammeChannelId: String? = null
            var currentStartEpoch = 0L
            var currentStopEpoch = 0L
            var currentTitle = ""
            var currentDesc = ""
            var currentCategory: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "channel" -> {
                                currentChannelTagId = parser.getAttributeValue(null, "id")
                            }
                            "display-name" -> {
                                val dName = parser.nextText()
                                val tagId = currentChannelTagId
                                if (tagId != null && dName.isNotBlank()) {
                                    val cleanName = dName.lowercase().replace(Regex("[^a-z0-9]"), "")
                                    if (cleanName.isNotBlank()) {
                                        fun putMapping(k: String) {
                                            val ex = nameToChannelId[k]
                                            // Prefer pure numeric JioTV IDs over prefixed IDs (ts/sony/sun)
                                            if (ex == null || (!ex.all { it.isDigit() } && tagId.all { it.isDigit() })) {
                                                nameToChannelId[k] = tagId
                                            }
                                        }
                                        putMapping(cleanName)
                                        if (cleanName.endsWith("hd")) {
                                            putMapping(cleanName.removeSuffix("hd"))
                                        }
                                        if (cleanName.contains("sports")) {
                                            putMapping(cleanName.replace("sports", ""))
                                            putMapping(cleanName.replace("sports", "").removeSuffix("hd"))
                                        }
                                        if (cleanName.contains("sportsten")) {
                                            putMapping(cleanName.replace("sportsten", "ten"))
                                        }
                                    }
                                }
                            }
                            "programme" -> {
                                currentProgrammeChannelId = parser.getAttributeValue(null, "channel")
                                val startStr = parser.getAttributeValue(null, "start")
                                val stopStr = parser.getAttributeValue(null, "stop")
                                currentStartEpoch = parseFastXmltvDate(startStr)
                                currentStopEpoch = parseFastXmltvDate(stopStr)
                                currentTitle = ""
                                currentDesc = ""
                                currentCategory = null
                            }
                            "title" -> currentTitle = parser.nextText()
                            "desc" -> currentDesc = parser.nextText()
                            "category" -> currentCategory = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "channel") {
                            currentChannelTagId = null
                        } else if (tagName == "programme" && currentProgrammeChannelId != null) {
                            if ((minStopEpoch <= 0L || currentStopEpoch >= minStopEpoch) &&
                                (maxStartEpoch <= 0L || currentStartEpoch <= maxStartEpoch)) {
                                val trimmedTitle = currentTitle.trim()
                                if (trimmedTitle.isNotEmpty()) {
                                    val program = EpgProgram(
                                        channelId = currentProgrammeChannelId,
                                        title = trimmedTitle,
                                        description = currentDesc.trim(),
                                        startEpochMs = currentStartEpoch,
                                        stopEpochMs = currentStopEpoch,
                                        category = currentCategory?.trim()
                                    )
                                    programsByChannel.getOrPut(currentProgrammeChannelId) { mutableListOf() }.add(program)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return EpgData(programsByChannel, nameToChannelId)
    }

    /**
     * Parses XMLTV XML/GZIP input stream into a map of channelId -> List<EpgProgram>.
     */
    fun parse(
        inputStream: InputStream,
        isGzip: Boolean = false,
        minStopEpoch: Long = System.currentTimeMillis() - 72 * 3600_000L,
        maxStartEpoch: Long = System.currentTimeMillis() + 48 * 3600_000L
    ): Map<String, List<EpgProgram>> = parseData(inputStream, isGzip, minStopEpoch, maxStartEpoch).programsByChannel

    companion object {
        /**
         * Ultra-fast thread-safe parser for XMLTV date timestamps (e.g., "20261025170800 +0000" or "20260711183000 +0530").
         */
        fun parseFastXmltvDate(dateStr: String?): Long {
            if (dateStr.isNullOrBlank()) return 0L
            val s = dateStr.trim()
            if (s.length < 14) return 0L
            return try {
                val year = s.substring(0, 4).toInt()
                val month = s.substring(4, 6).toInt()
                val day = s.substring(6, 8).toInt()
                val hour = s.substring(8, 10).toInt()
                val min = s.substring(10, 12).toInt()
                val sec = s.substring(12, 14).toInt()

                var offsetMillis = 0L
                if (s.length >= 19) {
                    val tzPart = s.substring(14).trim()
                    if (tzPart.length >= 5 && (tzPart[0] == '+' || tzPart[0] == '-')) {
                        val sign = if (tzPart[0] == '+') 1 else -1
                        val tzHour = tzPart.substring(1, 3).toInt()
                        val tzMin = tzPart.substring(3, 5).toInt()
                        offsetMillis = sign * ((tzHour * 60L + tzMin) * 60_000L)
                    }
                }

                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                    set(year, month - 1, day, hour, min, sec)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis - offsetMillis
            } catch (_: Exception) {
                0L
            }
        }
    }
}
