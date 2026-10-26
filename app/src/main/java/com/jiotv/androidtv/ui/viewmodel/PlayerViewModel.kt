package com.jiotv.androidtv.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.data.model.EpgProgram
import com.jiotv.androidtv.data.model.ServerConfig
import com.jiotv.androidtv.data.repository.ChannelRepository
import com.jiotv.androidtv.data.repository.EpgRepository
import com.jiotv.androidtv.data.repository.ServerConfigRepository
import com.jiotv.androidtv.data.repository.ZeeRepository
import com.jiotv.androidtv.player.AudioTrackOption
import com.jiotv.androidtv.player.PlayerPlaybackState
import com.jiotv.androidtv.player.TvPlayerManager
import com.jiotv.androidtv.player.VideoTrackOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@UnstableApi
class PlayerViewModel(
    private val context: Context,
    private val channelRepository: ChannelRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val epgRepository: EpgRepository
) : ViewModel() {

    val epgMap: StateFlow<Map<String, List<EpgProgram>>> = epgRepository.epgMap

    fun getCurrentProgram(channelId: String): EpgProgram? = epgRepository.getCurrentProgram(channelId)

    private var playerManager: TvPlayerManager? = null
    private var playbackStateCollectorJob: Job? = null

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    private val _exoPlayer = MutableStateFlow<ExoPlayer?>(null)
    val exoPlayer: StateFlow<ExoPlayer?> = _exoPlayer.asStateFlow()

    val allChannels: StateFlow<List<Channel>> = channelRepository.channels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeCategoryId: String? = null

    private val _isQuickGuideVisible = MutableStateFlow(false)
    val isQuickGuideVisible: StateFlow<Boolean> = _isQuickGuideVisible.asStateFlow()

    private val _isTrackSelectorVisible = MutableStateFlow(false)
    val isTrackSelectorVisible: StateFlow<Boolean> = _isTrackSelectorVisible.asStateFlow()

    fun initPlayer(initialChannelId: String, categoryId: String? = null) {
        activeCategoryId = categoryId
        viewModelScope.launch {
            val serverConfig = serverConfigRepository.serverConfig.first()
            if (playerManager == null) {
                val manager = TvPlayerManager(context, serverConfig, viewModelScope)
                playerManager = manager
                _exoPlayer.value = manager.getPlayer()

                playbackStateCollectorJob?.cancel()
                playbackStateCollectorJob = viewModelScope.launch {
                    manager.playbackState.collect { state ->
                        _playbackState.value = state
                    }
                }
            } else {
                _exoPlayer.value = playerManager?.getPlayer()
            }

            var list = channelRepository.channels.first()
            if (list.isEmpty()) {
                val refreshResult = channelRepository.refreshChannels()
                list = refreshResult.getOrDefault(emptyList())
            }

            val channel = list.find {
                it.id == initialChannelId ||
                it.id == "zee_${initialChannelId.removePrefix("zee_")}" ||
                it.id.removePrefix("zee_") == initialChannelId.removePrefix("zee_")
            } ?: list.find {
                it.tvgName.equals(initialChannelId, ignoreCase = true)
            } ?: list.firstOrNull()

            channel?.let {
                playChannel(it)
            }
        }
    }

    private var channelSwitchJob: Job? = null

    fun playChannel(channel: Channel) {
        channelSwitchJob?.cancel()
        // Immediately show the new channel in OSD for snappy set-top box style zapping
        _playbackState.value = _playbackState.value.copy(
            currentChannel = channel,
            isBuffering = true,
            isOsdVisible = true
        )

        channelSwitchJob = viewModelScope.launch {
            val resolvedChannel = if (channel.isZee || channel.id.startsWith("zee_")) {
                ZeeRepository.getUpdatedChannel(context, channel)
            } else {
                channel
            }

            if (playerManager == null) {
                val serverConfig = serverConfigRepository.serverConfig.first()
                val manager = TvPlayerManager(context, serverConfig, viewModelScope)
                playerManager = manager
                _exoPlayer.value = manager.getPlayer()

                playbackStateCollectorJob?.cancel()
                playbackStateCollectorJob = viewModelScope.launch {
                    manager.playbackState.collect { state ->
                        _playbackState.value = state
                    }
                }
                manager.playChannel(resolvedChannel)
            } else {
                playerManager?.playChannel(resolvedChannel)
            }
            channelRepository.saveLastPlayed(resolvedChannel.id)
        }
    }

    fun switchNextChannel() {
        val currentId = _playbackState.value.currentChannel?.id
            ?: playerManager?.playbackState?.value?.currentChannel?.id
            ?: return
        val nextChannel = channelRepository.getNextChannel(currentId, activeCategoryId) ?: return
        playChannel(nextChannel)
    }

    fun switchPreviousChannel() {
        val currentId = _playbackState.value.currentChannel?.id
            ?: playerManager?.playbackState?.value?.currentChannel?.id
            ?: return
        val prevChannel = channelRepository.getPreviousChannel(currentId, activeCategoryId) ?: return
        playChannel(prevChannel)
    }

    fun togglePlayPause() {
        playerManager?.togglePlayPause()
    }

    fun toggleOsd() {
        playerManager?.toggleOsd()
    }

    fun showOsd() {
        playerManager?.showOsdTemporarily()
    }

    fun hideOsd() {
        playerManager?.hideOsd()
    }

    fun toggleQuickGuide() {
        _isQuickGuideVisible.value = !_isQuickGuideVisible.value
        if (_isQuickGuideVisible.value) {
            _isTrackSelectorVisible.value = false
        }
    }

    fun hideQuickGuide() {
        _isQuickGuideVisible.value = false
    }

    fun toggleTrackSelector() {
        _isTrackSelectorVisible.value = !_isTrackSelectorVisible.value
        if (_isTrackSelectorVisible.value) {
            _isQuickGuideVisible.value = false
            hideOsd()
        }
    }

    fun hideTrackSelector() {
        _isTrackSelectorVisible.value = false
    }

    fun selectVideoTrack(option: VideoTrackOption) {
        playerManager?.selectVideoTrack(option)
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        playerManager?.selectAudioTrack(option)
    }

    fun getExoPlayer(): ExoPlayer? = _exoPlayer.value ?: playerManager?.getPlayer()

    fun stopPlayer() {
        playbackStateCollectorJob?.cancel()
        playbackStateCollectorJob = null
        _exoPlayer.value = null
        playerManager?.release()
        playerManager = null
        _isQuickGuideVisible.value = false
        _isTrackSelectorVisible.value = false
        _playbackState.value = PlayerPlaybackState()
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayer()
    }
}