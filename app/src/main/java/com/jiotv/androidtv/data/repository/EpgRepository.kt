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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

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

            if (isExpired || isCacheEmpty) {
                Log.d(tag, "EPG cache requires update (age: ${age / 3600_000}h, empty: $isCacheEmpty). Syncing...")
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
            val parsed = epgParser.parse(stream, isGzip = isGzip)
            currentProgramCache.clear()
            _epgMap.value = parsed
            Log.d(tag, "Successfully loaded EPG cache for ${parsed.size} channels.")
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

        // 2. Fenyx / community standard JioTV EPG source
        candidateUrls.add(PRIMARY_EPG_URL)
        candidateUrls.add(FALLBACK_EPG_URL)

        // 3. Local jiotv_go server companion EPG endpoint
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
                        val testParse = epgParser.parse(FileInputStream(tempFile), isGzip = isGz)
                        if (testParse.isNotEmpty()) {
                            // Atomic replace
                            if (cacheFile.exists()) cacheFile.delete()
                            tempFile.renameTo(cacheFile)

                            currentProgramCache.clear()
                            _epgMap.value = testParse
                            val now = System.currentTimeMillis()
                            _lastSyncTimestamp.value = now
                            appPreferences.saveLastEpgSync(now)

                            Log.d(tag, "Successfully synced EPG (${testParse.size} channels) from $url")
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

    private val currentProgramCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, EpgProgram?>>()

    /**
     * Looks up the currently airing program for a channel at [timestamp].
     * Uses an ultra-fast O(1) in-memory cache to ensure 60fps scroll performance on Android TV / Fire TV.
     */
    fun getCurrentProgram(channelId: String, timestamp: Long = System.currentTimeMillis()): EpgProgram? {
        val cached = currentProgramCache[channelId]
        if (cached != null) {
            val (cachedAt, prog) = cached
            if (prog != null && timestamp >= prog.startEpochMs && timestamp < prog.stopEpochMs) {
                return prog
            }
            if (Math.abs(timestamp - cachedAt) < 60_000L) {
                return prog
            }
        }

        val prog = computeCurrentProgram(channelId, timestamp)
        currentProgramCache[channelId] = Pair(timestamp, prog)
        return prog
    }

    private fun computeCurrentProgram(channelId: String, timestamp: Long): EpgProgram? {
        val programs = getProgramsForChannel(channelId)
        if (programs.isEmpty()) return null

        // 1. Direct match: program actively running right now
        val direct = programs.firstOrNull { it.startEpochMs <= timestamp && timestamp < it.stopEpochMs }
        if (direct != null) return direct

        // 2. Upcoming program today starting within 2 hours
        val upcoming = programs.firstOrNull { it.startEpochMs > timestamp }
        if (upcoming != null && (upcoming.startEpochMs - timestamp) <= 2 * 3600_000L) {
            return upcoming
        }

        // 3. 24h schedule projection fallback:
        // Community EPG feeds often update once daily or have yesterday's programs.
        // If a program aired 24h ago in this time slot on this channel, project it to today.
        val oneDayMs = 24 * 3600_000L
        val shiftedHit = programs.firstOrNull { prog ->
            val s24 = prog.startEpochMs + oneDayMs
            val e24 = prog.stopEpochMs + oneDayMs
            timestamp in s24 until e24
        }
        if (shiftedHit != null) {
            return shiftedHit.copy(
                startEpochMs = shiftedHit.startEpochMs + oneDayMs,
                stopEpochMs = shiftedHit.stopEpochMs + oneDayMs
            )
        }

        // 4. Fallback to latest program if it ended recently or first available
        return programs.lastOrNull { it.startEpochMs <= timestamp } ?: programs.firstOrNull()
    }

    /**
     * Returns the full list of programs available for a channel.
     */
    fun getProgramsForChannel(channelId: String): List<EpgProgram> {
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

        return emptyList()
    }

    /**
     * Returns the Now/Next/Later guide block for a channel.
     */
    fun getNowNext(channelId: String, timestamp: Long = System.currentTimeMillis()): NowNext {
        val programs = getProgramsForChannel(channelId)
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
        const val PRIMARY_EPG_URL = "https://mitthu786.github.io/tvepg/jiotv/epg.xml.gz"
        const val FALLBACK_EPG_URL = "https://avkb.short.gy/jioepg.xml.gz"

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
    }
}
