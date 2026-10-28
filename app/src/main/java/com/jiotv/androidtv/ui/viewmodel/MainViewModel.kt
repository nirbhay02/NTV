package com.jiotv.androidtv.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiotv.androidtv.data.model.Category
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.data.model.EpgProgram
import com.jiotv.androidtv.data.model.ServerConfig
import com.jiotv.androidtv.data.repository.ChannelRepository
import com.jiotv.androidtv.data.repository.EpgRepository
import com.jiotv.androidtv.data.repository.ServerConfigRepository
import com.jiotv.androidtv.data.network.ProxyStreamHandler
import com.jiotv.androidtv.server.EmbeddedServerManager
import com.jiotv.androidtv.server.EmbeddedServerState
import com.jiotv.androidtv.ui.components.NowNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val context: Context,
    private val channelRepository: ChannelRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val epgRepository: EpgRepository
) : ViewModel() {

    private val embeddedServerManager = EmbeddedServerManager.getInstance(context)

    val epgMap: StateFlow<Map<String, List<EpgProgram>>> = epgRepository.epgMap
    val isEpgSyncing: StateFlow<Boolean> = epgRepository.isSyncing

    fun getCurrentProgram(channelId: String, channelName: String? = null): EpgProgram? =
        epgRepository.getCurrentProgram(channelId, channelName = channelName)

    fun getNowNext(channelId: String, channelName: String? = null): NowNext =
        epgRepository.getNowNext(channelId, channelName = channelName)

    fun fetchNativeEpgIfMissing(channelId: String, channelName: String? = null) {
        epgRepository.fetchNativeEpgIfMissing(channelId, channelName)
    }

    fun refreshEpg(force: Boolean = true) {
        viewModelScope.launch {
            epgRepository.refreshEpg(force)
        }
    }

    val serverState: StateFlow<EmbeddedServerState> = embeddedServerManager.serverState

    val serverConfig: StateFlow<ServerConfig> = serverConfigRepository.serverConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerConfig())

    val loggedInMobile: StateFlow<String?> = channelRepository.loggedInMobile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val channels: StateFlow<List<Channel>> = channelRepository.channels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = channelRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoading: StateFlow<Boolean> = channelRepository.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val error: StateFlow<String?> = channelRepository.error
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _focusedChannel = MutableStateFlow<Channel?>(null)
    val focusedChannel: StateFlow<Channel?> = _focusedChannel.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String>("")
    val selectedCategoryId: StateFlow<String> = _selectedCategoryId.asStateFlow()

    private val _lastSelectedChannelId = MutableStateFlow<String?>(null)
    val lastSelectedChannelId: StateFlow<String?> = _lastSelectedChannelId.asStateFlow()

    // Tracks the active/last focused channel ID for each category
    private val categorySelectedChannels = mutableMapOf<String, String>()

    fun getSelectedChannelForCategory(categoryId: String): String? {
        return categorySelectedChannels[categoryId] ?: _lastSelectedChannelId.value
    }

    fun setLastSelectedChannel(channel: Channel) {
        _focusedChannel.value = channel
        _lastSelectedChannelId.value = channel.id
        if (_selectedCategoryId.value.isNotEmpty()) {
            categorySelectedChannels[_selectedCategoryId.value] = channel.id
        }
    }

    fun selectCategory(categoryId: String) {
        _selectedCategoryId.value = categoryId
        val rememberedId = categorySelectedChannels[categoryId]
        if (rememberedId != null) {
            val matched = channels.value.find { it.id == rememberedId }
            if (matched != null) {
                _focusedChannel.value = matched
                _lastSelectedChannelId.value = matched.id
            }
        } else {
            val cat = categories.value.find { it.id == categoryId }
            val first = cat?.channels?.firstOrNull()
            if (first != null) {
                _focusedChannel.value = first
                _lastSelectedChannelId.value = first.id
            }
        }
    }

    init {
        loadChannels()
        observeServerReady()
    }

    private fun observeServerReady() {
        viewModelScope.launch {
            embeddedServerManager.serverState.collect { state ->
                if (state is EmbeddedServerState.Running && state.isReady) {
                    // When the server becomes online and ready, if only Zee channels or no channels are present, reload channels
                    val current = channels.value
                    if (current.isEmpty() || current.none { !it.isZee }) {
                        loadChannels()
                    }
                }
            }
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            val result = channelRepository.refreshChannels()
            if (result.isSuccess) {
                val list = result.getOrNull().orEmpty()
                val cats = categories.value
                if (_selectedCategoryId.value.isEmpty() && cats.isNotEmpty()) {
                    val defaultZee = cats.find { it.id == "zee_network" } ?: cats.firstOrNull()
                    _selectedCategoryId.value = defaultZee?.id ?: ""
                }
                if (_focusedChannel.value == null && list.isNotEmpty()) {
                    val defaultZee = channelRepository.getZeeChannels().firstOrNull()
                    _focusedChannel.value = defaultZee ?: list.firstOrNull()
                }
            }
        }
    }

    fun restartServer() {
        embeddedServerManager.restartServer()
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            loadChannels()
        }
    }

    fun onLoginSuccess(mobile: String) {
        viewModelScope.launch {
            channelRepository.saveLoginStatus(mobile)
            loadChannels()
        }
    }

    private var sharedProxyHandler: ProxyStreamHandler? = null
    private var focusWarmupJob: Job? = null

    fun onChannelFocused(channel: Channel) {
        _focusedChannel.value = channel
        _lastSelectedChannelId.value = channel.id
        if (_selectedCategoryId.value.isNotEmpty()) {
            categorySelectedChannels[_selectedCategoryId.value] = channel.id
        }

        // Active pre-warming: When user rests focus on a channel card, prime the stream in background
        focusWarmupJob?.cancel()
        focusWarmupJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250) // 250ms debounce
            val isLocal = channel.streamUrl.contains("127.0.0.1") || channel.streamUrl.contains("localhost")
            if (channel.streamUrl.isNotBlank() && !channel.isZee && isLocal) {
                val config = serverConfig.value
                val isHd = channel.isHd || channel.name.contains("HD", ignoreCase = true) || channel.groupTitle.contains("HD", ignoreCase = true)
                val warmupUrl = if (isHd) {
                    "${config.baseUrl}/live/mpd/${channel.id}"
                } else {
                    "${config.baseUrl}/live/${config.quality}/${channel.id}.m3u8"
                }
                if (sharedProxyHandler == null) {
                    sharedProxyHandler = ProxyStreamHandler(config)
                }
                sharedProxyHandler?.warmUpStream(warmupUrl)
            }
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(channelId)
        }
    }
}
