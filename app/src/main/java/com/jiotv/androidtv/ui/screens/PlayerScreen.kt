package com.jiotv.androidtv.ui.screens

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.jiotv.androidtv.R
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.player.AudioTrackOption
import com.jiotv.androidtv.player.PlayerPlaybackState
import com.jiotv.androidtv.player.VideoTrackOption
import com.jiotv.androidtv.ui.components.ChannelPlate
import com.jiotv.androidtv.ui.components.JText
import com.jiotv.androidtv.ui.components.JtvButton
import com.jiotv.androidtv.ui.components.JtvClickable
import com.jiotv.androidtv.ui.components.JtvClock
import com.jiotv.androidtv.ui.components.JtvProgress
import com.jiotv.androidtv.ui.components.KeyHint
import com.jiotv.androidtv.ui.components.LocalNow
import com.jiotv.androidtv.ui.components.NumberBlock
import com.jiotv.androidtv.ui.components.numberStyle
import com.jiotv.androidtv.ui.components.rememberMinuteClock
import com.jiotv.androidtv.ui.components.textStyle
import com.jiotv.androidtv.ui.components.formatTimeShort
import com.jiotv.androidtv.ui.components.minutesLeft
import com.jiotv.androidtv.ui.components.progress
import com.jiotv.androidtv.ui.theme.Jtv
import com.jiotv.androidtv.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

/** Translucent obsidian plate background matching Fenyx v2-lab (~85% opacity) */
private val StrapBg = Color(0xD9141416)
private val StrapBorder = Color(0x2BFFFFFF)
private val LiveGreen = Color(0xFF22C55E)

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun PlayerScreen(
    channelId: String,
    categoryId: String? = null,
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val activePlayer by viewModel.exoPlayer.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val allChannels by viewModel.allChannels.collectAsState()
    val isQuickGuideVisible by viewModel.isQuickGuideVisible.collectAsState()
    val isTrackSelectorVisible by viewModel.isTrackSelectorVisible.collectAsState()
    val numericInput by viewModel.numericInput.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val now = rememberMinuteClock()

    // Initialize player with selected channel
    LaunchedEffect(channelId, categoryId) {
        viewModel.initPlayer(channelId, categoryId)
        for (attempt in 1..8) {
            delay(50L)
            try {
                focusRequester.requestFocus()
                break
            } catch (_: Exception) {}
        }
    }

    // Regain focus when drawers close so D-pad UP/DOWN works immediately
    LaunchedEffect(isQuickGuideVisible, isTrackSelectorVisible) {
        if (!isQuickGuideVisible && !isTrackSelectorVisible) {
            for (attempt in 1..8) {
                delay(50L)
                try {
                    focusRequester.requestFocus()
                    break
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-hide OSD after 5 seconds
    LaunchedEffect(playbackState.isOsdVisible) {
        if (playbackState.isOsdVisible) {
            delay(5000)
            viewModel.hideOsd()
        }
    }

    // Clean up player when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayer()
        }
    }

    // Intercept Back button
    BackHandler {
        if (isTrackSelectorVisible) {
            viewModel.hideTrackSelector()
        } else if (isQuickGuideVisible) {
            viewModel.hideQuickGuide()
        } else {
            onNavigateBack()
        }
    }

    CompositionLocalProvider(LocalNow provides now) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable(!isTrackSelectorVisible && !isQuickGuideVisible)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (isTrackSelectorVisible || isQuickGuideVisible) {
                            // When either drawer is open, let Compose handle list focus and navigation
                            if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                                if (isTrackSelectorVisible) viewModel.hideTrackSelector()
                                if (isQuickGuideVisible) viewModel.hideQuickGuide()
                                true
                            } else {
                                false
                            }
                        } else {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_CHANNEL_UP -> {
                                    viewModel.switchPreviousChannel()
                                    viewModel.showOsd()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                    viewModel.switchNextChannel()
                                    viewModel.showOsd()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    viewModel.toggleQuickGuide()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    viewModel.toggleTrackSelector()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    viewModel.toggleOsd()
                                    true
                                }
                                KeyEvent.KEYCODE_MENU,
                                KeyEvent.KEYCODE_GUIDE -> {
                                    viewModel.toggleQuickGuide()
                                    true
                                }
                                KeyEvent.KEYCODE_INFO -> {
                                    viewModel.toggleOsd()
                                    true
                                }
                                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                                    val digit = keyEvent.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_0
                                    viewModel.onNumberKeyPressed(digit)
                                    true
                                }
                                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                                    val digit = keyEvent.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_NUMPAD_0
                                    viewModel.onNumberKeyPressed(digit)
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                KeyEvent.KEYCODE_MEDIA_PLAY,
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    viewModel.togglePlayPause()
                                    true
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    if (isTrackSelectorVisible) {
                                        viewModel.hideTrackSelector()
                                        true
                                    } else if (isQuickGuideVisible) {
                                        viewModel.hideQuickGuide()
                                        true
                                    } else {
                                        onNavigateBack()
                                        true
                                    }
                                }
                                else -> false
                            }
                        }
                    } else {
                        false
                    }
                }
        ) {
            // Fullscreen ExoPlayer Surface
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isFocusable = false
                        isFocusableInTouchMode = false
                        player = activePlayer
                    }
                },
                update = { playerView ->
                    if (playerView.player != activePlayer) {
                        playerView.player = activePlayer
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                }
            )

            // Buffering Spinner Overlay (Fenyx obsidian glass style)
            if (playbackState.isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(StrapBg)
                            .border(1.dp, StrapBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Jtv.colors.acc,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = playbackState.currentChannel?.name ?: "Loading stream...",
                            style = textStyle(16.sp, FontWeight.SemiBold),
                            color = Jtv.colors.tx
                        )
                    }
                }
            }


            // Stream Error Overlay (Fenyx ErrorPanel style)
            if (playbackState.errorMessage != null && !playbackState.isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(StrapBg)
                            .border(1.dp, Jtv.colors.acc.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Jtv.colors.acc,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Stream Unavailable",
                            style = textStyle(20.sp, FontWeight.Bold),
                            color = Jtv.colors.tx
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playbackState.errorMessage.orEmpty(),
                            style = textStyle(14.sp),
                            color = Jtv.colors.t2,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            JtvButton(
                                text = "Retry Stream",
                                icon = Icons.Default.Refresh,
                                primary = true,
                                onClick = {
                                    playbackState.currentChannel?.let { viewModel.playChannel(it) }
                                }
                            )
                            JtvButton(
                                text = "Next Channel",
                                onClick = {
                                    viewModel.switchNextChannel()
                                }
                            )
                        }
                    }
                }
            }

            // Numeric Channel Entry Badge (Set-Top Box direct number typing)
            if (numericInput.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 48.dp)
                        .background(StrapBg, RoundedCornerShape(12.dp))
                        .border(1.5.dp, Jtv.colors.acc, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CH ",
                            style = textStyle(16.sp, FontWeight.Medium),
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = numericInput,
                            style = textStyle(32.sp, FontWeight.Bold),
                            color = Jtv.colors.acc
                        )
                    }
                }
            }

            // On-Screen Display (OSD) Overlay (Fenyx Top Clock + Bottom Strap)
            AnimatedVisibility(
                visible = playbackState.isOsdVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                FenyxPlayerOsdOverlay(
                    playbackState = playbackState,
                    now = now,
                    viewModel = viewModel
                )
            }

            // Quick Channel Guide Sliding Drawer (Fenyx 2-pane / rail style)
            AnimatedVisibility(
                visible = isQuickGuideVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(420.dp)
            ) {
                FenyxQuickChannelGuide(
                    channels = allChannels,
                    currentChannel = playbackState.currentChannel,
                    viewModel = viewModel,
                    onSelectChannel = { channel ->
                        viewModel.playChannel(channel)
                        viewModel.hideQuickGuide()
                        viewModel.showOsd()
                    },
                    onClose = { viewModel.hideQuickGuide() }
                )
            }

            // Stream Settings (Video Quality & Audio Stream) Sliding Drawer (from right)
            AnimatedVisibility(
                visible = isTrackSelectorVisible,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(420.dp)
            ) {
                FenyxTrackSelectorDrawer(
                    playbackState = playbackState,
                    onSelectVideoTrack = { option ->
                        viewModel.selectVideoTrack(option)
                    },
                    onSelectAudioTrack = { option ->
                        viewModel.selectAudioTrack(option)
                    },
                    onClose = { viewModel.hideTrackSelector() }
                )
            }
        }
    }
}

/**
 * Fenyx-styled On-Screen Display (OSD) Overlay
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FenyxPlayerOsdOverlay(
    playbackState: PlayerPlaybackState,
    now: Long,
    viewModel: PlayerViewModel
) {
    val channel = playbackState.currentChannel ?: return
    val c = Jtv.colors
    val epgMap by viewModel.epgMap.collectAsState()
    val currentEpg = remember(channel.id, epgMap.size, now) {
        viewModel.getCurrentProgram(channel.id)
    }
    val programTitle = currentEpg?.title?.takeIf { it.isNotBlank() }
        ?: channel.currentProgram
        ?: "${channel.groupTitle} · ${channel.language}"
    val hasRealTimes = currentEpg != null && currentEpg.startEpochMs > 0 && currentEpg.stopEpochMs > 0
    val timeRangeText = if (hasRealTimes) {
        val start = formatTimeShort(currentEpg!!.startEpochMs)
        val stop = formatTimeShort(currentEpg.stopEpochMs)
        val mins = currentEpg.minutesLeft(now)
        "$start - $stop ($mins m left)"
    } else {
        "Live Stream"
    }
    val progressFraction = currentEpg?.progress(now) ?: 0.65f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.70f),
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        // TOP-RIGHT: Digital Clock Plaque
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(8.dp))
                .background(StrapBg)
                .border(1.dp, StrapBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            JtvClock(now = now, size = 20.sp)
        }

        // TOP-LEFT: Status & DRM Badges Plaque
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(8.dp))
                .background(StrapBg)
                .border(1.dp, StrapBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LiveGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE",
                    style = textStyle(12.sp, FontWeight.Black),
                    color = LiveGreen
                )
            }

            // Resolution / Quality Badge
            val qualityLabel = playbackState.selectedVideoTrack?.label ?: playbackState.resolution
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.s2)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = qualityLabel,
                    style = textStyle(11.sp, FontWeight.Bold),
                    color = c.tx
                )
            }

            // Audio Language Badge if available
            val audioLabel = playbackState.selectedAudioTrack?.label
            if (!audioLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.s2)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = audioLabel,
                        style = textStyle(11.sp, FontWeight.Bold),
                        color = c.t2
                    )
                }
            }

            // DRM / Protection Badge
            val drmText = when {
                channel.isZee -> "ClearKey DRM"
                channel.isDrm -> "Widevine L3"
                else -> "HLS Direct"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (channel.isZee) c.acc.copy(alpha = 0.2f) else c.s2)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = drmText,
                    style = textStyle(11.sp, FontWeight.Bold),
                    color = if (channel.isZee) c.acc else c.t2
                )
            }

            if (channel.isZee) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.acc)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ZEE NETWORK",
                        style = textStyle(10.sp, FontWeight.Black),
                        color = c.accTx
                    )
                }
            }
        }

        // BOTTOM STRAP: Channel details, Live progress, and Remote Key Hints
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Channel Info Plate (Fenyx low-and-wide card)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StrapBg)
                    .border(1.dp, StrapBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel Logo Plate
                ChannelPlate(
                    logoUrl = channel.logoUrl.orEmpty(),
                    width = 84.dp,
                    height = 56.dp
                )

                Spacer(modifier = Modifier.width(18.dp))

                // Number + Channel Name + Category
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.number > 0) {
                            NumberBlock(number = channel.number, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = channel.name,
                            style = textStyle(22.sp, FontWeight.Bold),
                            color = c.tx,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = programTitle,
                        style = textStyle(15.sp, FontWeight.Medium),
                        color = c.tx,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!currentEpg?.description.isNullOrBlank()) {
                        Text(
                            text = currentEpg!!.description,
                            style = textStyle(12.sp, FontWeight.Normal),
                            color = c.t2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Live Stream Progress & Times
                Column(
                    modifier = Modifier.width(280.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeRangeText,
                            style = textStyle(13.sp, FontWeight.SemiBold),
                            color = c.t2
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.acc.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ON AIR",
                                style = textStyle(11.sp, FontWeight.Bold),
                                color = c.acc
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    JtvProgress(
                        fraction = progressFraction,
                        height = 4.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Remote Key Navigation Hints
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StrapBg)
                    .border(1.dp, StrapBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                KeyHint(
                    items = listOf(
                        "▲ / ▼" to "Switch Channel",
                        "◄" to "Quick Guide",
                        "►" to "Quality & Audio",
                        "OK" to "Toggle Info",
                        "Back" to "Dashboard"
                    )
                )
            }
        }
    }
}

/**
 * Fenyx-styled Quick Channel Guide (sliding left drawer)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FenyxQuickChannelGuide(
    channels: List<Channel>,
    currentChannel: Channel?,
    viewModel: PlayerViewModel,
    onSelectChannel: (Channel) -> Unit,
    onClose: () -> Unit
) {
    val c = Jtv.colors
    var selectedGroup by remember { mutableStateOf("All") }
    val categories = remember(channels) {
        listOf("All") + channels.map { it.groupTitle }.filter { it.isNotBlank() }.distinct()
    }

    val filteredChannels = remember(channels, selectedGroup) {
        if (selectedGroup == "All") channels
        else channels.filter { it.groupTitle.equals(selectedGroup, ignoreCase = true) }
    }

    val targetIdx = remember(currentChannel, filteredChannels) {
        val idx = filteredChannels.indexOfFirst { it.id == currentChannel?.id }
        if (idx >= 0) idx else 0
    }

    val initialScrollIdx = remember(targetIdx) {
        (targetIdx - 2).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIdx)
    val initialFocusRequester = remember { FocusRequester() }

    // Scroll to current channel on initial open and focus target channel immediately
    LaunchedEffect(currentChannel, filteredChannels) {
        if (currentChannel != null) {
            val idx = filteredChannels.indexOfFirst { it.id == currentChannel.id }
            if (idx >= 0) {
                listState.scrollToItem((idx - 2).coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(Unit) {
        for (attempt in 1..10) {
            delay(50L)
            try {
                initialFocusRequester.requestFocus()
                break
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .border(width = 1.dp, color = c.line)
            .padding(16.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Channels",
                style = textStyle(22.sp, FontWeight.Bold),
                color = c.tx
            )
            Text(
                text = "${filteredChannels.size} available",
                style = textStyle(13.sp),
                color = c.t2
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedGroup.equals(cat, ignoreCase = true)
                JtvClickable(
                    onClick = { selectedGroup = cat },
                    focusedScale = 1.0f,
                    container = if (isSelected) c.acc else c.s1,
                    focusedContainer = c.inv,
                    shape = RoundedCornerShape(6.dp)
                ) { focused ->
                    Text(
                        text = cat,
                        style = textStyle(13.sp, if (isSelected || focused) FontWeight.Bold else FontWeight.Medium),
                        color = if (focused) c.invTx else if (isSelected) c.accTx else c.tx,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Channel list
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = filteredChannels,
                key = { _, channel -> channel.id },
                contentType = { _, _ -> "quick_channel_row" }
            ) { index, channel ->
                val isPlaying = currentChannel?.id == channel.id
                QuickChannelItem(
                    channel = channel,
                    isPlaying = isPlaying,
                    viewModel = viewModel,
                    focusRequester = if (index == targetIdx) initialFocusRequester else null,
                    onSelect = { onSelectChannel(channel) }
                )
            }
        }
    }
}

/**
 * Individual Channel item in Quick Channel Guide with Fenyx inverted focus plate
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickChannelItem(
    channel: Channel,
    isPlaying: Boolean,
    viewModel: PlayerViewModel,
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit
) {
    val c = Jtv.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(8.dp)

    JtvClickable(
        onClick = onSelect,
        interactionSource = interactionSource,
        focusedScale = 1.0f,
        container = if (isPlaying) c.s2 else c.s1,
        focusedContainer = c.inv,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Number block or amber indicator
            if (channel.number > 0) {
                Text(
                    text = channel.number.toString(),
                    style = numberStyle(14.sp),
                    color = if (focused) c.invTx else c.acc,
                    modifier = Modifier.width(36.dp)
                )
            }

            // Channel mini plate
            ChannelPlate(
                logoUrl = channel.logoUrl.orEmpty(),
                width = 48.dp,
                height = 32.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Channel name & category / current EPG program
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = textStyle(14.sp, if (focused || isPlaying) FontWeight.Bold else FontWeight.Medium),
                    color = if (focused) c.invTx else c.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val chProg = viewModel.getCurrentProgram(channel.id)?.title ?: channel.groupTitle
                Text(
                    text = chProg,
                    style = textStyle(11.sp),
                    color = if (focused) Color(0xFF555555) else c.t2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Playing / Zee badge
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (focused) c.invTx else c.acc)
                )
            } else if (channel.isZee) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (focused) c.invTx else c.acc)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "ZEE",
                        style = textStyle(9.sp, FontWeight.Black),
                        color = if (focused) c.inv else c.accTx
                    )
                }
            }
        }
    }
}

/**
 * Fenyx-styled Stream Settings Drawer (Video Quality & Audio Language)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FenyxTrackSelectorDrawer(
    playbackState: PlayerPlaybackState,
    onSelectVideoTrack: (VideoTrackOption) -> Unit,
    onSelectAudioTrack: (AudioTrackOption) -> Unit,
    onClose: () -> Unit
) {
    val c = Jtv.colors
    val scrollState = rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        for (attempt in 1..10) {
            delay(50L)
            try {
                initialFocusRequester.requestFocus()
                break
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(playbackState.availableVideoTracks.size, playbackState.availableAudioTracks.size) {
        for (attempt in 1..8) {
            delay(50L)
            try {
                initialFocusRequester.requestFocus()
                break
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .border(width = 1.dp, color = c.line)
            .padding(20.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Stream Settings",
                    style = textStyle(22.sp, FontWeight.Bold),
                    color = c.tx
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = playbackState.currentChannel?.name ?: "Current Channel",
                    style = textStyle(13.sp, FontWeight.Medium),
                    color = c.acc
                )
            }
            Text(
                text = "Back to close",
                style = textStyle(12.sp),
                color = c.t2
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // VIDEO QUALITY SECTION
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "VIDEO QUALITY",
                        style = textStyle(13.sp, FontWeight.Bold),
                        color = c.t2
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${playbackState.availableVideoTracks.size} options)",
                        style = textStyle(11.sp),
                        color = c.t2.copy(alpha = 0.7f)
                    )
                }
            }

            if (playbackState.availableVideoTracks.isEmpty()) {
                item {
                    Text(
                        text = "Current stream: ${playbackState.resolution} (Default)",
                        style = textStyle(14.sp),
                        color = c.t2,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                val selectedVideoIdx = playbackState.availableVideoTracks.indexOfFirst { it.isSelected }
                val targetVideoIdx = if (selectedVideoIdx >= 0) selectedVideoIdx else 0
                itemsIndexed(playbackState.availableVideoTracks) { index, option ->
                    val isSelected = option.isSelected
                    val isTarget = index == targetVideoIdx
                    JtvClickable(
                        onClick = { onSelectVideoTrack(option) },
                        container = if (isSelected) c.s2 else c.s1,
                        focusedContainer = c.inv,
                        focusedScale = 1.0f,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isTarget) Modifier.focusRequester(initialFocusRequester) else Modifier)
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (focused) c.invTx
                                            else if (isSelected) c.acc
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (focused) c.invTx else if (isSelected) c.acc else c.t2,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option.label,
                                    style = textStyle(
                                        15.sp,
                                        if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (focused) c.invTx else if (isSelected) c.acc else c.tx
                                )
                            }
                            if (option.bitrate > 0) {
                                Text(
                                    text = "${option.bitrate / 1000} kbps",
                                    style = textStyle(12.sp),
                                    color = if (focused) c.invTx.copy(alpha = 0.8f) else c.t2
                                )
                            }
                        }
                    }
                }
            }

            // AUDIO STREAM SECTION
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "AUDIO STREAM",
                        style = textStyle(13.sp, FontWeight.Bold),
                        color = c.t2
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${playbackState.availableAudioTracks.size} options)",
                        style = textStyle(11.sp),
                        color = c.t2.copy(alpha = 0.7f)
                    )
                }
            }

            if (playbackState.availableAudioTracks.isEmpty()) {
                item {
                    Text(
                        text = "Default broadcaster audio stream",
                        style = textStyle(14.sp),
                        color = c.t2,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                val selectedAudioIdx = playbackState.availableAudioTracks.indexOfFirst { it.isSelected }
                val targetAudioIdx = if (selectedAudioIdx >= 0) selectedAudioIdx else 0
                itemsIndexed(playbackState.availableAudioTracks) { index, option ->
                    val isSelected = option.isSelected
                    val isTarget = playbackState.availableVideoTracks.isEmpty() && (index == targetAudioIdx)
                    JtvClickable(
                        onClick = { onSelectAudioTrack(option) },
                        container = if (isSelected) c.s2 else c.s1,
                        focusedContainer = c.inv,
                        focusedScale = 1.0f,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isTarget) Modifier.focusRequester(initialFocusRequester) else Modifier)
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (focused) c.invTx
                                            else if (isSelected) c.acc
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (focused) c.invTx else if (isSelected) c.acc else c.t2,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option.label,
                                    style = textStyle(
                                        15.sp,
                                        if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (focused) c.invTx else if (isSelected) c.acc else c.tx
                                )
                            }
                            if (!option.language.isNullOrBlank()) {
                                Text(
                                    text = option.language.uppercase(),
                                    style = textStyle(12.sp, FontWeight.SemiBold),
                                    color = if (focused) c.invTx.copy(alpha = 0.8f) else c.t2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
