package com.jiotv.androidtv.data.repository

import android.content.Context
import android.util.Log
import com.jiotv.androidtv.data.local.AppPreferences
import com.jiotv.androidtv.data.model.EpgProgram
import com.jiotv.androidtv.data.parser.EpgParser
import com.jiotv.androidtv.ui.components.NowNext
import com.jiotv.androidtv.ui.components.nowNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Repository responsible for downloading, caching, parsing, and serving Electronic Program Guide (EPG)
 * data for live TV channels.
 *
 * Automatically refreshes every 24 hours from the primary community EPG source used by Fenyx.
 */
class EpgRepository(
    private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val tag = "EpgRepository"
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val epgParser = EpgParser()
    private val cacheFile = File(context.cacheDir, "epg_cache.xml.gz")

    private val _epgMap = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epgMap: StateFlow<Map<String, List<EpgProgram>>> = _epgMap.asStateFlow()

    private val _nameToIdMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val nameToIdMap: StateFlow<Map<String, String>> = _nameToIdMap.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    // Native JioTV Real-Time EPG Cache (zero artificial projection, directly from Jio CDN)
    private val nativeEpgCache = ConcurrentHashMap<String, Pair<Long, List<EpgProgram>>>()
    private val fetchingChannels = ConcurrentHashMap.newKeySet<String>()
    private val epgFetchSemaphore = Semaphore(4)

    init {
        repoScope.launch {
            val lastSync = appPreferences.lastEpgSyncFlow.first()
            _lastSyncTimestamp.value = lastSync

            // 1. Instantly load from local flash cache if available
            loadFromDiskCache()

            // 2. Check if refresh is needed (older than 24 hours or missing cache)
            val now = System.currentTimeMillis()
            val age = now - lastSync
            val isExpired = age >= 24L * 3600_000L
            val isCacheEmpty = _epgMap.value.isEmpty()
            val isInsufficient = _epgMap.value.size < 1500

            if (isExpired || isCacheEmpty || isInsufficient) {
                Log.d(tag, "EPG cache requires update (age: ${age / 3600_000}h, empty: $isCacheEmpty, count: ${_epgMap.value.size}). Syncing from mitthu786/tvepg...")
                refreshEpg(force = false)
            } else {
                Log.d(tag, "EPG cache is fresh (age: ${age / 3600_000}h). Loaded ${_epgMap.value.size} channels.")
            }

            // 3. Start background periodic check (checks once every hour)
            startPeriodic24HourWorker()
        }
    }

    /**
     * Periodic background check loop: checks every hour if 24 hours have elapsed since last sync.
     */
    private fun startPeriodic24HourWorker() {
        repoScope.launch {
            while (isActive) {
                delay(3600_000L) // Wait 1 hour
                val lastSync = _lastSyncTimestamp.value
                val age = System.currentTimeMillis() - lastSync
                if (age >= 24L * 3600_000L) {
                    Log.d(tag, "24-hour cycle elapsed since last EPG sync. Refreshing in background...")
                    refreshEpg(force = false)
                }
            }
        }
    }

    /**
     * Loads and parses the locally cached EPG file.
     */
    private suspend fun loadFromDiskCache() = withContext(Dispatchers.IO) {
        if (!cacheFile.exists() || cacheFile.length() <= 0) return@withContext
        try {
            Log.d(tag, "Loading EPG from local cache file (${cacheFile.length() / 1024} KB)...")
            val isGzip = isGzipStream(cacheFile)
            val stream = FileInputStream(cacheFile)
            val data = epgParser.parseData(stream, isGzip = isGzip)
            currentProgramCache.clear()
            _epgMap.value = data.programsByChannel
            _nameToIdMap.value = data.nameToChannelId
            Log.d(tag, "Successfully loaded EPG cache for ${data.programsByChannel.size} channels.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse local EPG cache: ${e.message}")
        }
    }

    /**
     * Downloads fresh EPG data, saves to disk cache, and updates in-memory map.
     */
    suspend fun refreshEpg(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value && !force) return@withContext false
        _isSyncing.value = true

        val candidateUrls = mutableListOf<String>()

        // 1. User custom EPG URL if set in preferences
        val customUrl = appPreferences.epgUrlFlow.first()
        if (!customUrl.isNullOrBlank()) {
            candidateUrls.add(customUrl.trim())
        }

        // 2. mitthu786/tvepg All-in-One EPG source (JioTV, Tata Play, ZEE5, SonyLIV, Sun NXT)
        candidateUrls.add(PRIMARY_EPG_URL)
        candidateUrls.add(SECONDARY_EPG_URL)
        candidateUrls.add(SHORT_AIO_EPG_URL)

        // 3. mitthu786/tvepg Dedicated JioTV fallback
        candidateUrls.add(JIOTV_EPG_URL)
        candidateUrls.add(JIOTV_PAGES_URL)
        candidateUrls.add(SHORT_JIOTV_EPG_URL)

        // 4. Local jiotv_go server companion EPG endpoint
        try {
            val serverConfig = appPreferences.serverConfigFlow.first()
            candidateUrls.add(serverConfig.epgUrl)
        } catch (_: Exception) {}

        var success = false
        val tempFile = File(context.cacheDir, "epg_temp_${System.currentTimeMillis()}.gz")

        for (url in candidateUrls) {
            Log.d(tag, "Attempting EPG download from: $url")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android TV; NTV/1.2)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(tag, "HTTP ${response.code} from $url")
                        return@use
                    }

                    val body = response.body ?: return@use
                    FileOutputStream(tempFile).use { output ->
                        body.byteStream().copyTo(output)
                    }

                    if (tempFile.length() > 1024) { // At least 1KB valid data
                        val isGz = isGzipStream(tempFile)
                        val testData = epgParser.parseData(FileInputStream(tempFile), isGzip = isGz)
                        if (testData.programsByChannel.isNotEmpty()) {
                            // Atomic replace
                            if (cacheFile.exists()) cacheFile.delete()
                            tempFile.renameTo(cacheFile)

                            currentProgramCache.clear()
                            _epgMap.value = testData.programsByChannel
                            _nameToIdMap.value = testData.nameToChannelId
                            val now = System.currentTimeMillis()
                            _lastSyncTimestamp.value = now
                            appPreferences.saveLastEpgSync(now)

                            Log.d(tag, "Successfully synced EPG (${testData.programsByChannel.size} channels, ${testData.nameToChannelId.size} aliases) from $url")
                            success = true
                            return@withContext true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "EPG download error from $url: ${e.message}")
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }

            if (success) break
        }

        _isSyncing.value = false
        return@withContext success
    }

    private val currentProgramCache = ConcurrentHashMap<String, Pair<Long, EpgProgram?>>()

    /**
     * Resolves the canonical Jio channel ID for API queries.
     */
    fun resolveJioChannelId(channelId: String, channelName: String? = null): String {
        // 1. Try matching by channel name against known Jio aliases if available
        if (!channelName.isNullOrBlank()) {
            val clean = channelName.lowercase().replace(Regex("[^a-z0-9]"), "")
            val candidates = listOf(
                clean,
                clean.removeSuffix("hd"),
                clean + "hd",
                clean.removeSuffix("sd"),
                clean.replace("sports", ""),
                clean.replace("sports", "").removeSuffix("hd")
            )
            for (cand in candidates) {
                val mapped = _nameToIdMap.value[cand]
                if (mapped != null && mapped.all { it.isDigit() }) {
                    return mapped
                }
            }
        }

        // 2. Direct digits extraction (e.g. "zee_167" -> "167", "0289" -> "289")
        val digits = channelId.filter { it.isDigit() }.trimStart('0')
        if (digits.isNotEmpty()) {
            return digits
        }

        return channelId
    }

    /**
     * Jio's official real-time EPG for one channel and day offset (0 = today, 1 = tomorrow).
     * If offset 0 has already ended (Jio API midnight lag), automatically fetches tomorrow and merges.
     */
    suspend fun getNativeEpgForChannel(channelId: String, offset: Int = 0): List<EpgProgram> {
        val day = fetchNativeDay(channelId, offset)
        val now = System.currentTimeMillis()
        if (offset != 0 || day.any { it.stopEpochMs > now }) return day
        val tomorrow = fetchNativeDay(channelId, 1)
        return (day + tomorrow).distinctBy { it.startEpochMs }.sortedBy { it.startEpochMs }
    }

    private suspend fun fetchNativeDay(channelId: String, offset: Int): List<EpgProgram> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://jiotvapi.cdn.jio.com/apis/v1.3/getepg/get?offset=$offset&channel_id=$channelId&langId=6")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "okhttp/4.12.0")
            connection.setRequestProperty("appname", "RJIL_JioTV")
            connection.setRequestProperty("os", "android")
            connection.setRequestProperty("devicetype", "phone")
            if (connection.responseCode in 200..299) {
                val isGzip = "gzip".equals(connection.contentEncoding, ignoreCase = true)
                val rawStream = connection.inputStream
                val stream = if (isGzip && rawStream != null) GZIPInputStream(rawStream) else rawStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = JSONObject(text)
                val epgArray = json.optJSONArray("epg") ?: return@withContext emptyList()
                val programs = mutableListOf<EpgProgram>()
                for (i in 0 until epgArray.length()) {
                    val obj = epgArray.getJSONObject(i)
                    val title = obj.optString("showname", "").trim()
                    val desc = obj.optString("description", "").trim()
                    val startMs = obj.optLong("startEpoch", 0L)
                    val stopMs = obj.optLong("endEpoch", 0L)
                    val srno = if (obj.has("srno")) obj.optString("srno") else null
                    val showId = if (obj.has("showId")) obj.optString("showId") else null
                    val showtime = if (obj.has("showtime")) obj.optString("showtime") else null
                    val catchup = obj.optBoolean("isCatchupAvailable", false)
                    val genre = firstGenre(obj)
                    if (title.isNotEmpty() && startMs > 0 && stopMs > 0) {
                        programs.add(
                            EpgProgram(
                                channelId = channelId,
                                title = title,
                                description = desc,
                                startEpochMs = startMs,
                                stopEpochMs = stopMs,
                                category = genre,
                                srno = srno,
                                showId = showId,
                                showtime = showtime,
                                isCatchup = catchup
                            )
                        )
                    }
                }
                return@withContext programs
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch native EPG for channel $channelId (offset $offset): ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return@withContext emptyList()
    }

    private fun firstGenre(obj: JSONObject): String? {
        val genres = obj.optJSONArray("showGenre") ?: return null
        for (i in 0 until genres.length()) {
            val g = genres.optString(i, "").trim()
            if (g.isNotEmpty()) return g
        }
        return null
    }

    /**
     * Asynchronously fetches native Jio EPG for visible channels on-demand.
     * Controlled by a 4-permit semaphore to prevent flooding weak TV devices while scrolling.
     */
    fun fetchNativeEpgIfMissing(channelId: String, channelName: String? = null) {
        val resolvedId = resolveJioChannelId(channelId, channelName)
        val now = System.currentTimeMillis()
        val cached = nativeEpgCache[resolvedId] ?: nativeEpgCache[channelId]
        if (cached != null && (now - cached.first < 30 * 60 * 1000L)) {
            return
        }
        if (fetchingChannels.contains(resolvedId)) return

        fetchingChannels.add(resolvedId)
        repoScope.launch {
            try {
                epgFetchSemaphore.withPermit {
                    val programs = getNativeEpgForChannel(resolvedId)
                    if (programs.isNotEmpty()) {
                        val timestamp = System.currentTimeMillis()
                        nativeEpgCache[resolvedId] = Pair(timestamp, programs)
                        if (resolvedId != channelId) {
                            nativeEpgCache[channelId] = Pair(timestamp, programs)
                        }
                        currentProgramCache.remove(channelId)
                        if (channelName != null) currentProgramCache.remove("$channelId:$channelName")
                        currentProgramCache.remove(resolvedId)

                        val updated = _epgMap.value.toMutableMap()
                        updated[channelId] = programs
                        if (resolvedId != channelId) {
                            updated[resolvedId] = programs
                        }
                        _epgMap.value = updated
                        Log.d(tag, "Native Jio EPG loaded for '$channelName' (ch=$channelId, id=$resolvedId): ${programs.size} programs")
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error in fetchNativeEpgIfMissing for $channelId: ${e.message}")
            } finally {
                fetchingChannels.remove(resolvedId)
            }
        }
    }

    /**
     * Looks up the currently airing program for a channel at [timestamp].
     * Prioritizes the authentic Native Jio EPG directly from Jio servers.
     */
    fun getCurrentProgram(channelId: String, timestamp: Long = System.currentTimeMillis(), channelName: String? = null): EpgProgram? {
        val cacheKey = if (!channelName.isNullOrBlank()) "$channelId:$channelName" else channelId
        val cached = currentProgramCache[cacheKey]
        if (cached != null) {
            val (cachedAt, prog) = cached
            if (prog != null && timestamp >= prog.startEpochMs && timestamp < prog.stopEpochMs) {
                return prog
            }
            if (Math.abs(timestamp - cachedAt) < 30_000L) {
                return prog
            }
        }

        // 1. Native JioTV Real-Time EPG (Highest accuracy, zero artificial projection)
        val resolvedId = resolveJioChannelId(channelId, channelName)
        val nativeEntry = nativeEpgCache[resolvedId] ?: nativeEpgCache[channelId]
        if (nativeEntry != null) {
            val progs = nativeEntry.second
            val direct = progs.firstOrNull { it.startEpochMs <= timestamp && timestamp < it.stopEpochMs }
            if (direct != null) {
                currentProgramCache[cacheKey] = Pair(timestamp, direct)
                return direct
            }
            val upcoming = progs.firstOrNull { it.startEpochMs > timestamp }
            if (upcoming != null && (upcoming.startEpochMs - timestamp) <= 2 * 3600_000L) {
                currentProgramCache[cacheKey] = Pair(timestamp, upcoming)
                return upcoming
            }
            val fallback = progs.lastOrNull { it.startEpochMs <= timestamp } ?: progs.firstOrNull()
            if (fallback != null) {
                currentProgramCache[cacheKey] = Pair(timestamp, fallback)
                return fallback
            }
        } else {
            // Kick off background fetch so it updates reactively
            fetchNativeEpgIfMissing(channelId, channelName)
        }

        // 2. Fallback to XMLTV static guide
        val prog = computeCurrentProgram(channelId, timestamp, channelName)
        currentProgramCache[cacheKey] = Pair(timestamp, prog)
        return prog
    }

    private fun computeCurrentProgram(channelId: String, timestamp: Long, channelName: String? = null): EpgProgram? {
        val programs = getProgramsForChannel(channelId, channelName, project = true)
        if (programs.isEmpty()) return null

        val direct = programs.firstOrNull { it.startEpochMs <= timestamp && timestamp < it.stopEpochMs }
        if (direct != null) return direct

        val upcoming = programs.firstOrNull { it.startEpochMs > timestamp }
        if (upcoming != null && (upcoming.startEpochMs - timestamp) <= 2 * 3600_000L) {
            return upcoming
        }

        return programs.lastOrNull { it.startEpochMs <= timestamp } ?: programs.firstOrNull()
    }

    fun projectProgramsToToday(programs: List<EpgProgram>, now: Long = System.currentTimeMillis(), channelName: String? = null): List<EpgProgram> {
        return Companion.projectProgramsToToday(programs, now, channelName)
    }

    /**
     * Returns the full list of programs available for a channel.
     */
    fun getProgramsForChannel(
        channelId: String,
        channelName: String? = null,
        project: Boolean = true
    ): List<EpgProgram> {
        // Prioritize native Jio EPG if available
        val resolvedId = resolveJioChannelId(channelId, channelName)
        val nativeEntry = nativeEpgCache[resolvedId] ?: nativeEpgCache[channelId]
        if (nativeEntry != null && nativeEntry.second.isNotEmpty()) {
            return nativeEntry.second
        }

        val raw = getRawProgramsForChannel(channelId, channelName)
        return if (project) projectProgramsToToday(raw, channelName = channelName) else raw
    }

    private fun getRawProgramsForChannel(channelId: String, channelName: String? = null): List<EpgProgram> {
        val map = _epgMap.value
        if (map.isEmpty()) return emptyList()

        // 1. Direct match with channel ID
        map[channelId]?.let { if (it.isNotEmpty()) return it }

        // 2. Stripped prefix match (e.g. "zee_144" -> "144")
        val stripped = channelId.removePrefix("zee_").removePrefix("tvg-").trim()
        map[stripped]?.let { if (it.isNotEmpty()) return it }

        // 3. Pure digits match
        val digits = channelId.filter { it.isDigit() }
        if (digits.isNotEmpty()) {
            map[digits]?.let { if (it.isNotEmpty()) return it }
            val noZeroes = digits.trimStart('0')
            if (noZeroes.isNotEmpty()) {
                map[noZeroes]?.let { if (it.isNotEmpty()) return it }
            }
        }

        // 4. Normalized display name match with intelligent aliases
        if (!channelName.isNullOrBlank()) {
            val cleanName = channelName.lowercase().replace(Regex("[^a-z0-9]"), "")
            val candidates = listOf(
                cleanName,
                cleanName.removeSuffix("hd"),
                cleanName + "hd",
                cleanName.replace("sports", ""),
                cleanName.replace("sports", "").removeSuffix("hd"),
                cleanName.replace("sportsten", "ten"),
                cleanName.replace("ten", "sportsten"),
                cleanName.replace("ten", "sportsten").removeSuffix("hd")
            )
            for (cand in candidates) {
                val mappedId = _nameToIdMap.value[cand]
                if (mappedId != null) {
                    map[mappedId]?.let { if (it.isNotEmpty()) return it }
                }
            }
        }

        return emptyList()
    }

    /**
     * Returns the Now/Next/Later guide block for a channel.
     * Uses the Native Jio EPG if available, falling back to XMLTV.
     */
    fun getNowNext(
        channelId: String,
        timestamp: Long = System.currentTimeMillis(),
        channelName: String? = null
    ): NowNext {
        val resolvedId = resolveJioChannelId(channelId, channelName)
        val nativeEntry = nativeEpgCache[resolvedId] ?: nativeEpgCache[channelId]
        if (nativeEntry != null && nativeEntry.second.isNotEmpty()) {
            return nowNext(nativeEntry.second, timestamp)
        }

        fetchNativeEpgIfMissing(channelId, channelName)
        val programs = getProgramsForChannel(channelId, channelName, project = true)
        return nowNext(programs, timestamp)
    }


    private fun isGzipStream(file: File): Boolean {
        if (!file.exists() || file.length() < 2) return false
        return try {
            FileInputStream(file).use { fis ->
                val b1 = fis.read()
                val b2 = fis.read()
                b1 == 0x1f && b2 == 0x8b
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        // mitthu786/tvepg: All-In-One OTT EPG (JioTV, Tata Play, ZEE5, SonyLIV, Sun NXT - 2200+ channels)
        const val PRIMARY_EPG_URL = "https://raw.githubusercontent.com/mitthu786/tvepg/main/epg.xml.gz"
        const val SECONDARY_EPG_URL = "https://mitthu786.github.io/tvepg/epg.xml.gz"
        const val SHORT_AIO_EPG_URL = "https://avkb.short.gy/epg.xml.gz"

        // mitthu786/tvepg: Dedicated JioTV 2-day guide fallbacks
        const val JIOTV_EPG_URL = "https://raw.githubusercontent.com/mitthu786/tvepg/main/jiotv/epg.xml.gz"
        const val JIOTV_PAGES_URL = "https://mitthu786.github.io/tvepg/jiotv/epg.xml.gz"
        const val SHORT_JIOTV_EPG_URL = "https://avkb.short.gy/jioepg.xml.gz"

        const val FALLBACK_EPG_URL = SECONDARY_EPG_URL

        /**
         * Timestamp parser used by the XMLTV EPG pipeline and unit tests.
         */
        fun parseXmltvMillis(raw: String?, fmt: SimpleDateFormat? = null): Long {
            if (raw.isNullOrBlank()) return 0L
            if (fmt != null) {
                return try {
                    fmt.parse(raw.trim())?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }
            return EpgParser.parseFastXmltvDate(raw)
        }

        /**
         * Cleans cast string by removing role parentheticals (mirrors Fenyx helper).
         */
        fun shortCast(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.split(",")
                .map { item ->
                    val paren = item.indexOf('(')
                    if (paren > 0) item.substring(0, paren).trim() else item.trim()
                }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
        }

        /**
         * Returns at most [max] names joined by comma.
         */
        fun shortNames(raw: String?, max: Int = 3): String {
            if (raw.isNullOrBlank()) return ""
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(max)
                .joinToString(", ")
        }

        fun isMovieChannel(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val n = name.lowercase()
            return n.contains("cinema") || n.contains("max") || n.contains("gold") ||
                   n.contains("pictures") || n.contains("movie") || n.contains("film") ||
                   n.contains("cineplex") || n.contains("action") || n.contains("bollywood") ||
                   n.contains("talkies")
        }

        /**
         * Projects a channel's program schedule to the current day if the XMLTV feed is from yesterday.
         * Note: Movie channels are never projected forward because movies do not repeat on fixed 24h slots.
         */
        fun projectProgramsToToday(programs: List<EpgProgram>, now: Long = System.currentTimeMillis(), channelName: String? = null): List<EpgProgram> {
            if (programs.isEmpty()) return emptyList()
            val hasActiveOrFuture = programs.any { it.stopEpochMs > now }
            if (hasActiveOrFuture) return programs

            // Movie channels air different movies daily: never project expired schedules forward!
            if (isMovieChannel(channelName)) return emptyList()

            val maxStop = programs.maxOfOrNull { it.stopEpochMs } ?: return programs
            if (maxStop >= now) return programs

            val oneDayMs = 24 * 3600_000L
            val diffMs = now - maxStop
            val daysToShift = (diffMs / oneDayMs) + 1
            val shiftMs = daysToShift * oneDayMs

            return programs.map { prog ->
                prog.copy(
                    startEpochMs = prog.startEpochMs + shiftMs,
                    stopEpochMs = prog.stopEpochMs + shiftMs
                )
            }
        }
    }
}
