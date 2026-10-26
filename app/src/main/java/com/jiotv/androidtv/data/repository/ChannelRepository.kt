package com.jiotv.androidtv.data.repository

import android.content.Context
import android.util.Log
import com.jiotv.androidtv.data.local.AppPreferences
import com.jiotv.androidtv.data.model.Category
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.data.model.ServerConfig
import com.jiotv.androidtv.data.network.JioTvApiService
import com.jiotv.androidtv.data.network.ProxyStreamHandler
import com.jiotv.androidtv.data.parser.ChannelFilter
import com.jiotv.androidtv.data.parser.LanguageUtils
import com.jiotv.androidtv.data.parser.M3uParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single source of truth for channels, categories, and Zee-specific filters.
 */
class ChannelRepository(
    private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val repoScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Raw unfiltered channels fetched from server + Zee
    private var rawCombinedChannels: List<Channel> = emptyList()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: Flow<List<Channel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: Flow<List<Category>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: Flow<String?> = _error.asStateFlow()

    val favorites: Flow<Set<String>> = appPreferences.favoritesFlow
    val loggedInMobile: Flow<String?> = appPreferences.loggedInMobileFlow
    val enabledLanguages: Flow<Set<String>> = appPreferences.enabledLanguagesFlow

    // Available languages dynamically computed from loaded channels with channel counts
    private val _availableLanguages = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val availableLanguages: StateFlow<List<Pair<String, Int>>> = _availableLanguages.asStateFlow()

    private val _totalRawChannelsCount = MutableStateFlow(0)
    val totalRawChannelsCount: StateFlow<Int> = _totalRawChannelsCount.asStateFlow()

    init {
        repoScope.launch {
            appPreferences.enabledLanguagesFlow.collect {
                if (rawCombinedChannels.isNotEmpty()) {
                    applyFilters()
                }
            }
        }
    }

    suspend fun saveLoginStatus(mobileNumber: String) {
        appPreferences.saveLoginStatus(mobileNumber)
    }

    suspend fun logout() {
        appPreferences.clearLoginStatus()
        try {
            val serverConfig = appPreferences.serverConfigFlow.first()
            val proxyHandler = ProxyStreamHandler(serverConfig)
            val apiService = JioTvApiService(proxyHandler, serverConfig)
            apiService.logout()
        } catch (_: Exception) {}
        refreshChannels()
    }

    suspend fun setEnabledLanguages(languages: Set<String>) {
        appPreferences.setEnabledLanguages(languages)
        applyFilters()
    }

    suspend fun toggleLanguage(language: String) {
        val allKnown = _availableLanguages.value.map { it.first }.toSet()
        appPreferences.toggleLanguage(language, allKnown)
        applyFilters()
    }

    suspend fun setHindiEnglishOnly() {
        appPreferences.setEnabledLanguages(setOf("Hindi", "English"))
        applyFilters()
    }

    suspend fun enableAllLanguages() {
        appPreferences.clearEnabledLanguages()
        applyFilters()
    }

    private suspend fun applyFilters() {
        if (rawCombinedChannels.isEmpty()) return

        val favs = appPreferences.favoritesFlow.first()
        val enabledLangs = appPreferences.enabledLanguagesFlow.first()

        val filtered = if (enabledLangs.isEmpty()) {
            rawCombinedChannels
        } else {
            rawCombinedChannels.filter { LanguageUtils.isAllowed(it.language, enabledLangs) }
        }

        val enrichedChannels = filtered.map { channel ->
            channel.copy(isFavorite = favs.contains(channel.id))
        }

        val organized = ChannelFilter.organizeCategories(enrichedChannels, favs)
        _categories.value = organized
        val allFromCats = organized.find { it.id == "all_channels" }?.channels ?: enrichedChannels
        _channels.value = allFromCats
    }

    private fun computeAvailableLanguages(channelsList: List<Channel>): List<Pair<String, Int>> {
        if (channelsList.isEmpty()) return emptyList()
        val langCounts = mutableMapOf<String, Int>()
        for (c in channelsList) {
            val canon = LanguageUtils.canonical(c.language)
            langCounts[canon] = (langCounts[canon] ?: 0) + 1
        }
        return langCounts.toList().sortedWith(compareBy({
            val idx = LanguageUtils.POPULAR_LANGUAGES.indexOf(it.first)
            if (idx >= 0) idx else 999
        }, { it.first }))
    }

    /**
     * Loads or reloads channels from the jiotv_go server merged with Zee & third-party streams.
     */
    suspend fun refreshChannels(config: ServerConfig? = null): Result<List<Channel>> {
        _isLoading.value = true
        _error.value = null

        val serverConfig = config ?: appPreferences.serverConfigFlow.first()
        val proxyHandler = ProxyStreamHandler(serverConfig)
        val apiService = JioTvApiService(proxyHandler, serverConfig)

        var serverChannels = emptyList<Channel>()
        var lastException: Throwable? = null

        for (attempt in 1..3) {
            val result = apiService.fetchPlaylist()
            if (result.isSuccess) {
                val m3uContent = result.getOrThrow()
                val parser = M3uParser(serverConfig)
                serverChannels = parser.parse(m3uContent)
                break
            } else {
                lastException = result.exceptionOrNull()
                if (attempt < 3) {
                    delay(1000)
                }
            }
        }

        // Fetch Zee Network & third-party playlists (with automatic offline fallback)
        val zeeChannels = try {
            ZeeRepository.loadChannels(context, network = true)
        } catch (e: Exception) {
            Log.w("ChannelRepository", "Failed to load remote Zee channels, using offline fallback", e)
            ZeeRepository.loadChannels(context, network = false)
        }

        val combinedChannels = mergeChannels(serverChannels, zeeChannels)
        val distinctChannels = combinedChannels.distinctBy { it.id }

        rawCombinedChannels = distinctChannels
        _totalRawChannelsCount.value = distinctChannels.size
        _availableLanguages.value = computeAvailableLanguages(distinctChannels)

        applyFilters()
        _isLoading.value = false

        val currentList = _channels.value
        if (currentList.isNotEmpty()) {
            return Result.success(currentList)
        }

        val errorMsg = lastException?.localizedMessage
            ?: "Unable to load channels from server or Zee playlist"
        _error.value = errorMsg
        return Result.failure(Exception(errorMsg))
    }

    private fun mergeChannels(serverChannels: List<Channel>, thirdParty: List<Channel>): List<Channel> {
        if (thirdParty.isEmpty()) return serverChannels
        if (serverChannels.isEmpty()) return thirdParty

        val result = mutableListOf<Channel>()
        val addedCleanNames = mutableSetOf<String>()
        val addedIds = mutableSetOf<String>()

        fun clean(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "")

        // 1. Prioritize all curated third-party channels FIRST (Zee Network ClearKey DRM streams & Sports)
        for (tp in thirdParty) {
            val cName = clean(tp.name)
            if (cName.isNotEmpty() && cName in addedCleanNames) continue
            result.add(tp)
            addedIds.add(tp.id)
            if (cName.isNotEmpty()) addedCleanNames.add(cName)
        }

        // 2. Add server channels from JioTV Go server that are not already covered
        for (sc in serverChannels) {
            val cName = clean(sc.name)
            // If it's a Zee channel and already covered in thirdParty, skip server's version
            if (sc.isZee || ChannelFilter.isZeeChannel(sc)) {
                if (cName in addedCleanNames || sc.id in addedIds) {
                    continue
                }
            }
            if (sc.id in addedIds || (cName.isNotEmpty() && cName in addedCleanNames)) {
                continue
            }
            result.add(sc)
            addedIds.add(sc.id)
            if (cName.isNotEmpty()) addedCleanNames.add(cName)
        }

        return result.sortedBy { if (it.number > 0) it.number else 9999 }
    }

    fun getZeeChannels(): List<Channel> =
        _channels.value.filter { ChannelFilter.isZeeChannel(it) }

    fun getNextChannel(currentId: String, categoryId: String? = null): Channel? {
        val list = getChannelListForCategory(currentId, categoryId)
        if (list.isEmpty()) return null
        val cleanId = currentId.removePrefix("zee_")
        val currentIndex = list.indexOfFirst {
            it.id == currentId || it.id == "zee_$cleanId" || it.id.removePrefix("zee_") == cleanId
        }
        if (currentIndex == -1) return list.firstOrNull()
        val nextIndex = (currentIndex + 1) % list.size
        return list[nextIndex]
    }

    fun getPreviousChannel(currentId: String, categoryId: String? = null): Channel? {
        val list = getChannelListForCategory(currentId, categoryId)
        if (list.isEmpty()) return null
        val cleanId = currentId.removePrefix("zee_")
        val currentIndex = list.indexOfFirst {
            it.id == currentId || it.id == "zee_$cleanId" || it.id.removePrefix("zee_") == cleanId
        }
        if (currentIndex == -1) return list.lastOrNull()
        val prevIndex = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
        return list[prevIndex]
    }

    fun getChannelListForCategory(currentId: String, categoryId: String? = null): List<Channel> {
        val cats = _categories.value
        if (!categoryId.isNullOrEmpty() && categoryId != "all_channels") {
            val matchedCat = cats.find { it.id == categoryId }
            if (matchedCat != null && matchedCat.channels.isNotEmpty()) {
                return matchedCat.channels
            }
        }
        // Auto-detect which category the current channel belongs to
        val cleanId = currentId.removePrefix("zee_")
        val matchedCat = cats.firstOrNull { cat ->
            cat.id != "all_channels" && cat.id != "favorites" && cat.channels.any {
                it.id == currentId || it.id == "zee_$cleanId" || it.id.removePrefix("zee_") == cleanId
            }
        }
        if (matchedCat != null && matchedCat.channels.isNotEmpty()) {
            return matchedCat.channels
        }
        return _channels.value
    }

    fun searchChannels(query: String): List<Channel> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return _channels.value

        return _channels.value.filter { channel ->
            channel.name.lowercase().contains(q) ||
            channel.groupTitle.lowercase().contains(q) ||
            channel.number.toString() == q ||
            (channel.isZee && q.contains("zee"))
        }
    }

    suspend fun toggleFavorite(channelId: String) {
        appPreferences.toggleFavorite(channelId)
        val favs = appPreferences.favoritesFlow.first()
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(isFavorite = favs.contains(it.id)) else it
        }
        _categories.value = ChannelFilter.organizeCategories(_channels.value, favs)
    }

    suspend fun saveLastPlayed(channelId: String) {
        appPreferences.saveLastPlayedChannel(channelId)
    }
}
