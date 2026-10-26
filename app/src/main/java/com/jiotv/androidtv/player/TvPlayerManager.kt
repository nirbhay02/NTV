package com.jiotv.androidtv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.data.model.ServerConfig
import com.jiotv.androidtv.data.network.ProxyStreamHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Video resolution / stream quality option.
 */
data class VideoTrackOption(
    val id: String,
    val label: String,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    val isSelected: Boolean = false,
    val trackGroup: TrackGroup? = null,
    val trackIndex: Int = -1
)

/**
 * Audio language / stream track option.
 */
data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val trackGroup: TrackGroup? = null,
    val trackIndex: Int = -1
)

/**
 * State representing player playback status.
 */
data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentChannel: Channel? = null,
    val resolution: String = "HD",
    val errorMessage: String? = null,
    val isOsdVisible: Boolean = true,
    val isMiniSurfingVisible: Boolean = false,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    val availableVideoTracks: List<VideoTrackOption> = emptyList(),
    val selectedVideoTrack: VideoTrackOption? = null,
    val availableAudioTracks: List<AudioTrackOption> = emptyList(),
    val selectedAudioTrack: AudioTrackOption? = null
)

/**
 * Robust Media3 ExoPlayer wrapper for Android TV streaming.
 * Rebuilt completely around official JioTV-Go (jiotv_go_app) architecture:
 * 1. Widevine Security Level L3 (Software DRM) preventing TV hardware decoder crashes.
 * 2. OmniMediaDrmCallback for robust local & remote license requests with automatic retries.
 * 3. 2-attempt DRM-to-HLS fallback when Widevine/MPD is unavailable or rejected.
 * 4. Sub-second channel zapping (600ms start buffer) and 12 Mbps initial bandwidth seed.
 * 5. SafeDns and header-preserving HTTP redirects via ProxyStreamHandler.
 */
@UnstableApi
class TvPlayerManager(
    private val context: Context,
    private val serverConfig: ServerConfig,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TvPlayerManager"
    }

    private val proxyHandler = ProxyStreamHandler(serverConfig)
    private var exoPlayer: ExoPlayer? = null
    private var retryJob: Job? = null
    private var osdHideJob: Job? = null
    private var miniSurfingHideJob: Job? = null

    // DRM state tracking (ported from jiotv_go_app OmniPlayerScreen)
    private var useDrm: Boolean = true
    private var drmRetryCount: Int = 0

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState = _playbackState.asStateFlow()

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        // Amlogic & TV SoC Audio Sink Fix: Prevents audio drift, muted sound, or channel switch pops
        val audioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return audioSink
            }
        }.apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            // [NUVIO-OPT-3] Hardware-First Video Decoder Priority: forces dedicated hardware VPU decoders first
            setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder
                )
                if (mimeType.startsWith("video/")) {
                    decoders.sortedBy { decoder ->
                        if (!decoder.hardwareAccelerated || decoder.name.startsWith("c2.android.") || decoder.name.startsWith("omx.google.")) 1 else 0
                    }
                } else {
                    decoders
                }
            }
        }

        // Sub-second zapping load control with robust live playback cushion
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // Min buffer: 15s smooth live playback cushion
                60000,  // Max buffer: 60s
                600,    // Buffer required to START: 600ms for instant channel zapping (sub-second)
                5000    // Buffer required after rebuffer: 5s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(8000, true)
            .build()

        // Seed optimistic 12 Mbps initial bandwidth estimate so ExoPlayer starts immediately at top resolution
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(C.TRACK_TYPE_VIDEO, 12_000_000L)
            .build()

        val adaptiveFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 3_000,
            /* maxDurationForQualityDecreaseMs = */ 20_000,
            /* minDurationToRetainAfterDiscardMs = */ 15_000,
            /* bandwidthFraction = */ 0.9f
        )

        // TV-optimized track selector:
        // 1. setTunnelingEnabled(false): Tunneling is the primary cause of black screens on Amlogic/MediaTek TV SoCs.
        // 2. setAllowVideoNonSeamlessAdaptiveness(false): Prevents secure AVC decoder re-initialization mid-stream.
        val trackSelector = DefaultTrackSelector(context, adaptiveFactory).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("hi")
                .setPreferredTextLanguage("en")
                .setTunnelingEnabled(false)
                .setAllowVideoNonSeamlessAdaptiveness(false)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setViewportSizeToPhysicalDisplaySize(this@TvPlayerManager.context, true)
                .build()
        }

        // Gentle live playback speed control:
        // Tolerates 1s offset before touching speed, keeping adjustments within ±0.5%
        // Prevents frame rate oscillation (24 -> 25 -> 24 fps) which causes periodic judder/blinking on TV panels.
        val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(0.995f)
            .setFallbackMaxPlaybackSpeed(1.005f)
            .setMaxLiveOffsetErrorMsForUnitSpeed(1_000)
            .setMinUpdateIntervalMs(5_000)
            .setProportionalControlFactor(0.1f)
            .build()

        val httpDataSourceFactory = proxyHandler.createMedia3DataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLivePlaybackSpeedControl(liveSpeedControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .experimentalSetDynamicSchedulingEnabled(false)
            .build().apply {
                playWhenReady = true
                addListener(createPlayerListener())
            }
    }

    private fun createPlayerListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _playbackState.value = _playbackState.value.copy(
                        isBuffering = true,
                        errorMessage = null
                    )
                }
                Player.STATE_READY -> {
                    // Reset DRM retry count on successful playback
                    drmRetryCount = 0
                    _playbackState.value = _playbackState.value.copy(
                        isBuffering = false,
                        errorMessage = null
                    )
                    Log.i(TAG, "Playback STATE_READY for ${_playbackState.value.currentChannel?.name}")
                }
                Player.STATE_ENDED -> {
                    reconnectStream()
                }
                Player.STATE_IDLE -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
        }

        override fun onRenderedFirstFrame() {
            Log.i(TAG, "First video frame rendered successfully on surface!")
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val hasVideo = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            val hasAudio = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            Log.d(TAG, "Tracks changed: hasVideo=$hasVideo, hasAudio=$hasAudio")
            updateAvailableTracks(tracks)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val label = when {
                videoSize.height >= 1080 -> "1080p FHD"
                videoSize.height >= 720 -> "720p HD"
                videoSize.height > 0 -> "${videoSize.height}p"
                else -> "Live"
            }
            _playbackState.value = _playbackState.value.copy(resolution = label)
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val rootCause = generateSequence(cause) { it.cause }.lastOrNull() ?: cause ?: error
            val causeMsg = rootCause.localizedMessage ?: error.localizedMessage ?: error.errorCodeName

            Log.e(TAG, "Playback error [${error.errorCodeName} / ${error.errorCode}]: $causeMsg", error)

            val currentChannel = _playbackState.value.currentChannel

            // jiotv_go_app DRM Auto-Fallback Engine (only for official JioTV Widevine streams)
            val isClearKey = currentChannel?.licenseKeyUrl?.startsWith("jwk:", ignoreCase = true) == true || currentChannel?.isZee == true
            if (useDrm && currentChannel != null && !isClearKey) {
                drmRetryCount++
                if (drmRetryCount >= 2) {
                    Log.w(TAG, "DRM failed 2 times for '${currentChannel.name}', falling back to clean HLS stream")
                    useDrm = false
                    playChannel(currentChannel, forceHlsFallback = true)
                    return
                } else {
                    Log.w(TAG, "DRM failed $drmRetryCount time(s), retrying...")
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    return
                }
            }

            val is403 = (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                    (error.message?.contains("403") == true || error.message?.contains("401") == true)) ||
                    causeMsg.contains("403") || causeMsg.contains("401") || causeMsg.contains("Unauthorized", ignoreCase = true)

            val isDrm = causeMsg.contains("drm", ignoreCase = true) ||
                    causeMsg.contains("widevine", ignoreCase = true) ||
                    causeMsg.contains("UnsupportedScheme", ignoreCase = true)

            val errorDisplay = when {
                is403 -> "Jio Session Expired / Unauthorized. Please log out & re-login via OTP in Settings."
                isDrm -> "DRM License Error: $causeMsg"
                else -> "Stream error: $causeMsg"
            }

            _playbackState.value = _playbackState.value.copy(
                isBuffering = false,
                errorMessage = errorDisplay
            )

            scheduleRetry()
        }
    }

    // [PERF-OPT-5] Non-blocking pre-warming of adjacent channel stream connection
    fun warmUpStream(url: String) {
        proxyHandler.warmUpStream(url)
    }

    /**
     * Normalizes playback URL and configures MediaItem/MediaSource with jiotv_go_app mechanics.
     */
    fun playChannel(channel: Channel, forceHlsFallback: Boolean = false) {
        retryJob?.cancel()
        val player = exoPlayer ?: return

        if (forceHlsFallback) {
            useDrm = false
        } else {
            useDrm = true
            drmRetryCount = 0
        }

        // Clean up previous channel state & stale cookies (prevents cross-channel 403 on Akamai/Zee)
        player.stop()
        player.clearMediaItems()
        proxyHandler.clearCookies()

        _playbackState.value = _playbackState.value.copy(
            currentChannel = channel,
            isBuffering = true,
            isOsdVisible = true,
            errorMessage = null
        )

        val rawUrl = serverConfig.resolveStreamUrl(channel.streamUrl)
        val channelId = channel.id.ifEmpty {
            rawUrl.substringAfterLast("/").substringBefore("?").substringBefore(".")
        }

        // URL Normalization (ported from jiotv_go_app HelperUtils.normalizePlaybackUrl)
        var playbackUrl = rawUrl
        val playMatch = Regex(""".*/play/(\d+)$""").find(playbackUrl)
        if (playMatch != null) {
            val id = playMatch.groupValues[1]
            playbackUrl = "${serverConfig.baseUrl}/live/$id.m3u8"
        }

        var resolvedLicenseUrl = channel.licenseKeyUrl
        val isLocalChannel = playbackUrl.contains("127.0.0.1") || playbackUrl.contains("localhost")

        if (useDrm && (channel.isDrm || playbackUrl.contains("/live/mpd/") || channel.manifestType.equals("mpd", ignoreCase = true))) {
            if (isLocalChannel) {
                playbackUrl = "${serverConfig.baseUrl}/live/mpd/$channelId"
                if (resolvedLicenseUrl.isNullOrBlank()) {
                    resolvedLicenseUrl = "${serverConfig.baseUrl}/live/key/$channelId"
                }
            }
        }

        // Force HLS when DRM is disabled or falling back
        if (!useDrm) {
            resolvedLicenseUrl = null
            if (playbackUrl.contains("/live/mpd/")) {
                playbackUrl = "${serverConfig.baseUrl}/live/${serverConfig.quality}/$channelId.m3u8"
            }
        }

        val isDrm = useDrm && !resolvedLicenseUrl.isNullOrBlank()
        val isDash = isDrm || playbackUrl.contains("/live/mpd/") || playbackUrl.contains(".mpd", ignoreCase = true)
        val mimeType = if (isDash) MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8

        Log.d(TAG, "Preparing '${channel.name}' (DRM: $isDrm, DASH: $isDash, URL: $playbackUrl, License: $resolvedLicenseUrl)")

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(playbackUrl))
            .setMediaId(channelId)
            .setMimeType(mimeType)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(15000)
                    .build()
            )

        val normalizedHeaders = mutableMapOf(
            "User-Agent" to "JioTV/7.0.8 (Linux; Android 13; Pixel 7 Pro Build/TQ1A.221205.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/110.0.5481.64 Mobile Safari/537.36",
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Origin" to serverConfig.baseUrl,
            "Referer" to "${serverConfig.baseUrl}/"
        )

        // Only attach Widevine DRM on DASH streams when device supports it
        val isWidevineSupported = try {
            FrameworkMediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)
        } catch (_: Throwable) {
            false
        }

        var drmSessionManager: DefaultDrmSessionManager? = null

        if (isDrm && !resolvedLicenseUrl.isNullOrBlank()) {
            val isJwk = resolvedLicenseUrl.startsWith("jwk:", ignoreCase = true)

            if (isJwk) {
                // ClearKey DRM for Zee Network & third-party DASH MPD channels
                Log.d(TAG, "Configuring ClearKey DRM for '${channel.name}'")
                val jwkJson = resolvedLicenseUrl.substringAfter("jwk:").substringAfter("JWK:")
                val drmCallback = LocalMediaDrmCallback(jwkJson.toByteArray(Charsets.UTF_8))
                drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)
            } else if (isWidevineSupported) {
                // Widevine DRM L3 for official JioTV streams
                val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(resolvedLicenseUrl)
                    .setLicenseRequestHeaders(normalizedHeaders)
                    .setPlayClearContentWithoutKey(true)
                    .setMultiSession(true)
                    .build()
                mediaItemBuilder.setDrmConfiguration(drmConfig)

                // CRITICAL jiotv_go_app FIX: Force Widevine Security Level "L3" (Software DRM)
                // This prevents Android TV hardware crypto path crashes and unexpected runtime errors!
                val drmCallback = OmniMediaDrmCallback(resolvedLicenseUrl, normalizedHeaders, proxyHandler.okHttpClient)
                drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID) { uuid ->
                        FrameworkMediaDrm.newInstance(uuid).apply {
                            try {
                                setPropertyString("securityLevel", "L3")
                            } catch (_: Exception) {}
                        }
                    }
                    .setMultiSession(true)
                    .build(drmCallback)
            }
        }

        val mediaItem = mediaItemBuilder.build()
        val dataSourceFactory = proxyHandler.createMedia3DataSourceFactory()

        val mediaSource = if (isDash) {
            val dashFactory = DashMediaSource.Factory(dataSourceFactory)
            if (drmSessionManager != null) {
                dashFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
            dashFactory.createMediaSource(mediaItem)
        } else {
            HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(false)
                .createMediaSource(mediaItem)
        }

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()

        showOsdTemporarily()
    }

    /**
     * Toggles play/pause state.
     */
    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    /**
     * Displays On-Screen Display (OSD) and automatically hides it after duration.
     */
    fun showOsdTemporarily(durationMs: Long = 5000L) {
        _playbackState.value = _playbackState.value.copy(isOsdVisible = true)
        osdHideJob?.cancel()
        osdHideJob = scope.launch {
            delay(durationMs)
            _playbackState.value = _playbackState.value.copy(isOsdVisible = false)
        }
    }

    fun toggleOsd() {
        val currentlyVisible = _playbackState.value.isOsdVisible
        if (currentlyVisible) {
            osdHideJob?.cancel()
            _playbackState.value = _playbackState.value.copy(isOsdVisible = false)
        } else {
            showOsdTemporarily()
        }
    }

    fun hideOsd() {
        osdHideJob?.cancel()
        _playbackState.value = _playbackState.value.copy(isOsdVisible = false)
    }

    /**
     * Displays compact Mini Channel Surfing Banner (pill) and auto-hides after duration.
     */
    fun showMiniSurfingTemporarily(durationMs: Long = 3500L) {
        _playbackState.value = _playbackState.value.copy(
            isMiniSurfingVisible = true,
            isOsdVisible = false
        )
        miniSurfingHideJob?.cancel()
        miniSurfingHideJob = scope.launch {
            delay(durationMs)
            _playbackState.value = _playbackState.value.copy(isMiniSurfingVisible = false)
        }
    }

    fun hideMiniSurfing() {
        miniSurfingHideJob?.cancel()
        _playbackState.value = _playbackState.value.copy(isMiniSurfingVisible = false)
    }

    /**
     * Cycles through AspectRatio modes: FIT -> FILL (Stretch) -> ZOOM (Crop 16:9) -> FIT.
     */
    fun toggleAspectRatio(): Int {
        val nextMode = when (_playbackState.value.resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        _playbackState.value = _playbackState.value.copy(resizeMode = nextMode)
        return nextMode
    }

    fun setResizeMode(mode: Int) {
        _playbackState.value = _playbackState.value.copy(resizeMode = mode)
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = scope.launch(Dispatchers.Main) {
            delay(3000)
            reconnectStream()
        }
    }

    private fun reconnectStream() {
        val currentChannel = _playbackState.value.currentChannel ?: return
        playChannel(currentChannel)
    }

    fun updateAvailableTracks(tracks: Tracks) {
        val player = exoPlayer ?: return
        val videoOptions = mutableListOf<VideoTrackOption>()
        val audioOptions = mutableListOf<AudioTrackOption>()

        val overrides = player.trackSelectionParameters.overrides
        val hasVideoOverride = overrides.values.any { it.type == C.TRACK_TYPE_VIDEO }

        // Always add Auto for Video (adaptive bitrate streaming)
        videoOptions.add(
            VideoTrackOption(
                id = "auto",
                label = "Auto (Recommended)",
                isSelected = !hasVideoOverride
            )
        )

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        if (group.isTrackSupported(i)) {
                            val format = group.getTrackFormat(i)
                            val height = format.height
                            val width = format.width
                            val bitrate = format.bitrate
                            val isSelected = hasVideoOverride && group.isTrackSelected(i)

                            val label = when {
                                height >= 2160 -> "4K Ultra HD (2160p)"
                                height >= 1080 -> "1080p Full HD"
                                height >= 720 -> "720p HD"
                                height >= 576 -> "576p SD"
                                height >= 480 -> "480p SD"
                                height >= 360 -> "360p"
                                height > 0 -> "${height}p"
                                bitrate > 0 -> "${bitrate / 1000} kbps"
                                else -> "Video Track ${i + 1}"
                            }

                            videoOptions.add(
                                VideoTrackOption(
                                    id = "video_${group.mediaTrackGroup.id}_$i",
                                    label = label,
                                    width = width,
                                    height = height,
                                    bitrate = bitrate,
                                    isSelected = isSelected,
                                    trackGroup = group.mediaTrackGroup,
                                    trackIndex = i
                                )
                            )
                        }
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        if (group.isTrackSupported(i)) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language
                            val labelText = format.label
                            val isSelected = group.isTrackSelected(i)

                            val langDisplay = if (!lang.isNullOrBlank()) {
                                try {
                                    val loc = java.util.Locale(lang)
                                    val d = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
                                    if (d.isNotBlank()) d.replaceFirstChar { it.uppercase() } else lang.uppercase()
                                } catch (_: Exception) {
                                    lang.uppercase()
                                }
                            } else null

                            val cleanLabel = when {
                                !labelText.isNullOrBlank() && !langDisplay.isNullOrBlank() && !labelText.equals(langDisplay, ignoreCase = true) ->
                                    "$langDisplay ($labelText)"
                                !langDisplay.isNullOrBlank() -> langDisplay
                                !labelText.isNullOrBlank() -> labelText
                                else -> "Audio Track ${audioOptions.size + 1}"
                            }

                            audioOptions.add(
                                AudioTrackOption(
                                    id = "audio_${group.mediaTrackGroup.id}_$i",
                                    label = cleanLabel,
                                    language = lang,
                                    isSelected = isSelected,
                                    trackGroup = group.mediaTrackGroup,
                                    trackIndex = i
                                )
                            )
                        }
                    }
                }
            }
        }

        // Deduplicate video tracks by height so multiple representations don't clutter the list
        val distinctVideoOptions = mutableListOf<VideoTrackOption>()
        val seenHeights = mutableSetOf<Int>()
        for (opt in videoOptions) {
            if (opt.id == "auto") {
                distinctVideoOptions.add(opt)
            } else if (opt.height > 0) {
                if (seenHeights.add(opt.height)) {
                    distinctVideoOptions.add(opt)
                }
            } else {
                distinctVideoOptions.add(opt)
            }
        }

        val sortedVideoOptions = distinctVideoOptions.sortedWith { a, b ->
            if (a.id == "auto") -1
            else if (b.id == "auto") 1
            else b.height.compareTo(a.height)
        }

        val activeVideo = sortedVideoOptions.find { it.isSelected } ?: sortedVideoOptions.firstOrNull()
        val activeAudio = audioOptions.find { it.isSelected } ?: audioOptions.firstOrNull()

        _playbackState.value = _playbackState.value.copy(
            availableVideoTracks = sortedVideoOptions,
            selectedVideoTrack = activeVideo,
            availableAudioTracks = audioOptions,
            selectedAudioTrack = activeAudio
        )
    }

    fun selectVideoTrack(option: VideoTrackOption) {
        val player = exoPlayer ?: return
        if (option.id == "auto" || option.trackGroup == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
        } else {
            val override = TrackSelectionOverride(option.trackGroup, option.trackIndex)
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .build()
        }
        player.currentTracks.let { updateAvailableTracks(it) }
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        val player = exoPlayer ?: return
        val group = option.trackGroup ?: return
        val override = TrackSelectionOverride(group, option.trackIndex)
        val builder = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
        if (!option.language.isNullOrBlank()) {
            builder.setPreferredAudioLanguage(option.language)
        }
        player.trackSelectionParameters = builder.build()
        player.currentTracks.let { updateAvailableTracks(it) }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun release() {
        osdHideJob?.cancel()
        miniSurfingHideJob?.cancel()
        retryJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
