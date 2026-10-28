package com.jiotv.androidtv.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.jiotv.androidtv.R
import com.jiotv.androidtv.data.model.Category
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.server.EmbeddedServerState
import com.jiotv.androidtv.ui.components.ChannelPlate
import com.jiotv.androidtv.ui.components.ErrorBanner
import com.jiotv.androidtv.ui.components.JtvButton
import com.jiotv.androidtv.ui.components.JtvClickable
import com.jiotv.androidtv.ui.components.JtvClock
import com.jiotv.androidtv.ui.components.JtvProgress
import com.jiotv.androidtv.ui.components.KeyHint
import com.jiotv.androidtv.ui.components.LoginOtpDialog
import com.jiotv.androidtv.ui.components.NumberBlock
import com.jiotv.androidtv.ui.components.formatTimeShort
import com.jiotv.androidtv.ui.components.minutesLeft
import com.jiotv.androidtv.ui.components.progress
import com.jiotv.androidtv.ui.components.rememberMinuteClock
import com.jiotv.androidtv.ui.components.textStyle
import com.jiotv.androidtv.ui.navigation.Screen
import com.jiotv.androidtv.ui.theme.Jtv
import com.jiotv.androidtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private fun getGenreColor(groupTitle: String, isZee: Boolean): Color {
    val lower = groupTitle.lowercase()
    return when {
        isZee -> Color(0xFFA855F7) // Royal Purple for Zee Network
        lower.contains("sport") -> Color(0xFF10B981) // Vibrant Emerald
        lower.contains("news") -> Color(0xFFEF4444) // Live Crimson
        lower.contains("movie") || lower.contains("cinema") -> Color(0xFFF59E0B) // Cinema Amber Gold
        lower.contains("music") -> Color(0xFFEC4899) // Neon Magenta
        lower.contains("kid") || lower.contains("cartoon") -> Color(0xFF06B6D4) // Sky Cyan
        lower.contains("infotainment") || lower.contains("documentary") -> Color(0xFF14B8A6) // Teal
        else -> Color(0xFF38BDF8) // Electric Azure / Cyan
    }
}

/**
 * Modern Glassmorphic 3-Pane Home Screen for Android TV:
 * [Left Rail: Categories] · [Center List: Channels] · [Right Pane: Live Preview & Hero Details]
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onChannelSelected: (Channel) -> Unit
) {
    val c = Jtv.colors
    val categories by viewModel.categories.collectAsState()
    val allChannels by viewModel.channels.collectAsState()
    val focusedChannel by viewModel.focusedChannel.collectAsState()
    val epgMap by viewModel.epgMap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val serverState by viewModel.serverState.collectAsState()
    val serverConfig by viewModel.serverConfig.collectAsState()
    val loggedInMobile by viewModel.loggedInMobile.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        LoginOtpDialog(
            serverConfig = serverConfig,
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { mobile ->
                showLoginDialog = false
                viewModel.onLoginSuccess(mobile)
            }
        )
    }

    val now = rememberMinuteClock()
    val selectedCategoryKey by viewModel.selectedCategoryId.collectAsState()

    // Initialize default category if not already set
    LaunchedEffect(categories) {
        if (selectedCategoryKey.isEmpty() && categories.isNotEmpty()) {
            val defaultCat = categories.find { it.id == "zee_network" } ?: categories.firstOrNull()
            if (defaultCat != null) {
                viewModel.selectCategory(defaultCat.id)
            }
        }
    }

    val currentCategory = categories.find { it.id == selectedCategoryKey }
        ?: categories.firstOrNull()

    val displayedChannels = currentCategory?.channels ?: allChannels

    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()

    val lastSelectedChannelId by viewModel.lastSelectedChannelId.collectAsState()
    val targetChannelId = viewModel.getSelectedChannelForCategory(selectedCategoryKey)
        ?: lastSelectedChannelId
        ?: focusedChannel?.id
        ?: displayedChannels.firstOrNull()?.id

    val targetFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        showExitDialog = true
    }

    // Keep active category scrolled into view
    LaunchedEffect(selectedCategoryKey, categories.size) {
        if (selectedCategoryKey.isNotEmpty() && categories.isNotEmpty()) {
            val catIndex = categories.indexOfFirst { it.id == selectedCategoryKey }
            if (catIndex >= 0) {
                categoryListState.animateScrollToItem(catIndex)
            }
        }
    }

    // Scroll and focus on target channel when entering category or returning to dashboard
    LaunchedEffect(selectedCategoryKey, targetChannelId, displayedChannels.size) {
        if (displayedChannels.isNotEmpty() && targetChannelId != null) {
            val idx = displayedChannels.indexOfFirst { it.id == targetChannelId }
            if (idx >= 0) {
                val targetChannel = displayedChannels[idx]
                viewModel.onChannelFocused(targetChannel)
                val scrollIdx = (idx - 2).coerceAtLeast(0)
                channelListState.scrollToItem(scrollIdx)
                delay(120)
                try {
                    targetFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    // Dynamic Ambient Lighting Color based on focused channel
    val currentGenreColor = remember(focusedChannel?.groupTitle, focusedChannel?.isZee) {
        if (focusedChannel != null) getGenreColor(focusedChannel!!.groupTitle, focusedChannel!!.isZee) else Color(0xFF00D2FF)
    }
    val animatedAmbientColor by animateColorAsState(
        targetValue = currentGenreColor,
        animationSpec = tween(durationMillis = 600),
        label = "ambient_glow"
    )

    // ───────────────────────── Root Canvas with Dynamic Ambient Glow ─────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C12))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        animatedAmbientColor.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(Float.POSITIVE_INFINITY, 0f),
                    radius = 1200f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0E1626).copy(alpha = 0.40f),
                        Color.Transparent
                    ),
                    center = Offset(0f, 1500f),
                    radius = 900f
                )
            )
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ───────────────────────── Top Bar (Glassmorphic) ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: App branding + Dynamic Status Badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ntv_splash_logo),
                            contentDescription = "NTV Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = "NTV",
                        style = textStyle(24.sp, FontWeight.ExtraBold),
                        color = Color.White
                    )

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00D2FF), Color(0xFF0284C7))
                                ),
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            style = textStyle(10.sp, FontWeight.ExtraBold),
                            color = Color(0xFF031622)
                        )
                    }

                    Spacer(Modifier.width(18.dp))

                    // Embedded Server Status Glass Pill
                    val isReady = (serverState as? EmbeddedServerState.Running)?.isReady == true
                    val serverColor = when (serverState) {
                        is EmbeddedServerState.Running -> if (isReady) Color(0xFF22C55E) else Color(0xFFEAB308)
                        is EmbeddedServerState.Starting -> Color(0xFFEAB308)
                        is EmbeddedServerState.PreparingBinary -> Color(0xFF38BDF8)
                        else -> Color(0xFFEF4444)
                    }
                    val serverText = when (serverState) {
                        is EmbeddedServerState.Running -> if (isReady) "Server: Online (5001)" else "Server: Binding..."
                        is EmbeddedServerState.Starting -> "Server: Starting..."
                        is EmbeddedServerState.PreparingBinary -> "Server: Preparing..."
                        else -> "Server: Stopped"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF141724).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Glowing status indicator dot
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(serverColor.copy(alpha = 0.28f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.5.dp)
                                    .background(serverColor, CircleShape)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = serverText,
                            style = textStyle(12.sp, FontWeight.SemiBold),
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Jio Status / Login Button (Glass Pill)
                    val isJioLoggedIn = loggedInMobile != null
                    val jioColor = if (isJioLoggedIn) Color(0xFF22C55E) else Color(0xFFEF4444)
                    val jioText = if (isJioLoggedIn) "JIO: Connected" else "JIO: Log In"

                    JtvClickable(
                        onClick = { showLoginDialog = true },
                        container = Color(0xFF141724).copy(alpha = 0.85f),
                        focusedContainer = Color.White,
                        shape = RoundedCornerShape(20.dp),
                    ) { focused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (focused) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(jioColor.copy(alpha = 0.28f), CircleShape)
                                    )
                                Box(
                                    modifier = Modifier
                                        .size(6.5.dp)
                                        .background(jioColor, CircleShape)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = jioText,
                                style = textStyle(12.sp, FontWeight.SemiBold),
                                color = if (focused) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                // Right: Navigation Buttons (Search, Settings) + Live Digital Clock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    JtvButton(
                        text = "Search",
                        icon = Icons.Default.Search,
                        onClick = { onNavigate(Screen.Search.route) },
                        fontSize = 13.sp,
                        minHeight = 36.dp
                    )
                    JtvButton(
                        text = "Settings",
                        icon = Icons.Default.Settings,
                        onClick = { onNavigate(Screen.Settings.route) },
                        fontSize = 13.sp,
                        minHeight = 36.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    JtvClock(now = now, size = 18.sp)
                }
            }

            // ───────────────────────── Main 3-Pane Content Area ─────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading && categories.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00D2FF),
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                    error != null && categories.isEmpty() -> {
                        ErrorBanner(
                            errorMessage = error ?: "Unable to fetch channels",
                            onRetry = { viewModel.loadChannels() },
                            onOpenSettings = { onNavigate(Screen.Settings.route) }
                        )
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // ── Pane 1: Category Column (Width ~200dp) ──
                            Column(
                                modifier = Modifier
                                    .width(200.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF11141E).copy(alpha = 0.85f))
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(Color(0xFF00D2FF), CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "CATEGORIES",
                                        style = textStyle(11.sp, FontWeight.ExtraBold),
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                LazyColumn(
                                    state = categoryListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(
                                        items = categories,
                                        key = { it.id },
                                        contentType = { "category_row" }
                                    ) { cat ->
                                        val isSelected = cat.id == selectedCategoryKey
                                        JtvClickable(
                                            onClick = {
                                                viewModel.selectCategory(cat.id)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            focusedScale = 1.02f,
                                            container = if (isSelected) Color(0xFF1E2436).copy(alpha = 0.9f) else Color.Transparent,
                                            focusedContainer = Color.White,
                                            shape = RoundedCornerShape(10.dp)
                                        ) { focused ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (isSelected && !focused) {
                                                            Modifier.border(
                                                                1.dp,
                                                                Color(0xFF00D2FF).copy(alpha = 0.35f),
                                                                RoundedCornerShape(10.dp)
                                                            )
                                                        } else Modifier
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    if (isSelected && !focused) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(3.5.dp)
                                                                .height(16.dp)
                                                                .background(Color(0xFF00D2FF), RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                    }
                                                    Text(
                                                        text = cat.title,
                                                        style = textStyle(
                                                            14.sp,
                                                            if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (focused) Color(0xFF0F172A) else if (isSelected) Color(0xFF00D2FF) else Color(0xFFE2E8F0),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(Modifier.width(6.dp))

                                                // Channel Count Pill Badge
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (focused) Color(0xFF0F172A).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "${cat.channels.size}",
                                                        style = textStyle(11.sp, FontWeight.SemiBold),
                                                        color = if (focused) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Pane 2: Channel List (Width ~345dp) ──
                            Column(
                                modifier = Modifier
                                    .width(345.dp)
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp, start = 4.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = currentCategory?.title?.uppercase() ?: "CHANNELS",
                                        style = textStyle(11.sp, FontWeight.ExtraBold),
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = "${displayedChannels.size} channels",
                                        style = textStyle(11.sp, FontWeight.SemiBold),
                                        color = Color(0xFF64748B)
                                    )
                                }

                                LazyColumn(
                                    state = channelListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(bottom = 12.dp)
                                ) {
                                    items(
                                        items = displayedChannels,
                                        key = { it.id },
                                        contentType = { "channel_row" }
                                    ) { channel ->
                                        val isTarget = channel.id == targetChannelId
                                        val genreColor = getGenreColor(channel.groupTitle, channel.isZee)

                                        LaunchedEffect(channel.id) {
                                            viewModel.fetchNativeEpgIfMissing(channel.id, channel.name)
                                        }

                                        JtvClickable(
                                            onClick = {
                                                viewModel.setLastSelectedChannel(channel)
                                                onChannelSelected(channel)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(if (isTarget) Modifier.focusRequester(targetFocusRequester) else Modifier),
                                            focusedScale = 1.03f,
                                            container = Color(0xFF131622).copy(alpha = 0.75f),
                                            focusedContainer = Color(0xFF1E2436),
                                            shape = RoundedCornerShape(10.dp)
                                        ) { focused ->
                                            if (focused) {
                                                LaunchedEffect(channel.id) {
                                                    viewModel.fetchNativeEpgIfMissing(channel.id, channel.name)
                                                    viewModel.onChannelFocused(channel)
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(
                                                        width = if (focused) 1.8.dp else 1.dp,
                                                        color = if (focused) genreColor.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.05f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .then(
                                                        if (focused) {
                                                            Modifier.background(
                                                                Brush.horizontalGradient(
                                                                    listOf(genreColor.copy(alpha = 0.22f), Color(0xFF1E2436))
                                                                ),
                                                                RoundedCornerShape(10.dp)
                                                            )
                                                        } else Modifier
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (focused) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(3.5.dp)
                                                            .height(24.dp)
                                                            .background(genreColor, RoundedCornerShape(2.dp))
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                }

                                                // Channel Number Badge
                                                NumberBlock(
                                                    number = channel.number,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.widthIn(min = 40.dp)
                                                )

                                                Spacer(Modifier.width(10.dp))

                                                // High-contrast glossy logo plate
                                                Box(
                                                    modifier = Modifier
                                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                        .clip(RoundedCornerShape(8.dp))
                                                ) {
                                                    ChannelPlate(
                                                        logoUrl = channel.logoUrl.orEmpty(),
                                                        width = 46.dp,
                                                        height = 34.dp
                                                    )
                                                }

                                                Spacer(Modifier.width(12.dp))

                                                // Channel Name & Info
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = channel.name,
                                                            style = textStyle(15.sp, FontWeight.Bold),
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (channel.isHd) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(
                                                                        Brush.horizontalGradient(
                                                                            listOf(Color(0xFF00D2FF), Color(0xFF0284C7))
                                                                        ),
                                                                        RoundedCornerShape(3.dp)
                                                                    )
                                                                    .padding(horizontal = 4.5.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = "HD",
                                                                    style = textStyle(9.sp, FontWeight.ExtraBold),
                                                                    color = Color(0xFF031622)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    val chProg = viewModel.getCurrentProgram(channel.id, channel.name)?.title
                                                        ?: channel.currentProgram
                                                        ?: "${channel.language} • ${channel.groupTitle}"
                                                    Text(
                                                        text = chProg,
                                                        style = textStyle(12.sp, FontWeight.Normal),
                                                        color = if (focused) Color(0xFFCBD5E1) else Color(0xFF94A3B8),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                if (channel.isFavorite) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = "Favorite",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = Color(0xFFFBBF24) // Gold Star
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Pane 3: Right Detail & Live Preview Hero Panel (Weight 1f) ──
                            val selected = focusedChannel ?: displayedChannels.firstOrNull()
                            val selectedGenreColor = if (selected != null) getGenreColor(selected.groupTitle, selected.isZee) else Color(0xFF00D2FF)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF0F121B))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                selectedGenreColor.copy(alpha = 0.28f),
                                                Color(0xFF131724).copy(alpha = 0.90f),
                                                Color(0xFF0A0C13)
                                            ),
                                            center = Offset(Float.POSITIVE_INFINITY, 0f),
                                            radius = 1100f
                                        )
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        brush = Brush.linearGradient(
                                            listOf(
                                                selectedGenreColor.copy(alpha = 0.45f),
                                                Color.White.copy(alpha = 0.12f),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                if (selected != null) {
                                    Column(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // Scrollable upper content taking all available weight
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            // Header row with Floating Showcase Plate + Big Watermark Number
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .border(
                                                            1.2.dp,
                                                            Brush.linearGradient(
                                                                listOf(Color.White.copy(alpha = 0.20f), selectedGenreColor.copy(alpha = 0.35f))
                                                            ),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .clip(RoundedCornerShape(12.dp))
                                                ) {
                                                    ChannelPlate(
                                                        logoUrl = selected.logoUrl.orEmpty(),
                                                        width = 135.dp,
                                                        height = 82.dp
                                                    )
                                                }

                                                NumberBlock(
                                                    number = selected.number,
                                                    fontSize = 26.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }

                                            Spacer(Modifier.height(10.dp))

                                            // Channel Title
                                            Text(
                                                text = selected.name,
                                                style = textStyle(22.sp, FontWeight.ExtraBold),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(Modifier.height(5.dp))

                                            // Metadata Badges Row
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (selected.isHd) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    listOf(Color(0xFF00D2FF), Color(0xFF0284C7))
                                                                ),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "1080p Full HD",
                                                            style = textStyle(10.sp, FontWeight.ExtraBold),
                                                            color = Color(0xFF031622)
                                                        )
                                                    }
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF1E2436).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                        .border(1.dp, selectedGenreColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = selected.groupTitle,
                                                        style = textStyle(10.sp, FontWeight.Bold),
                                                        color = Color(0xFFE2E8F0)
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF1E2436).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = selected.language,
                                                        style = textStyle(10.sp, FontWeight.SemiBold),
                                                        color = Color(0xFFCBD5E1)
                                                    )
                                                }

                                                if (selected.isZee) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFF8B5CF6), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "ClearKey DRM",
                                                            style = textStyle(10.sp, FontWeight.ExtraBold),
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            LaunchedEffect(selected.id) {
                                                viewModel.fetchNativeEpgIfMissing(selected.id, selected.name)
                                            }

                                            // Current Program Card with real-time EPG
                                            val nowNext = remember(selected.id, epgMap.size, now) {
                                                viewModel.getNowNext(selected.id, selected.name)
                                            }
                                            val currentEpg = nowNext.now ?: viewModel.getCurrentProgram(selected.id, selected.name)
                                            val nextEpg = nowNext.next
                                            val programTitle = currentEpg?.title?.takeIf { it.isNotBlank() }
                                                ?: selected.currentProgram
                                                ?: "Live Broadcast"
                                            val hasRealTimes = currentEpg != null && currentEpg.startEpochMs > 0 && currentEpg.stopEpochMs > 0
                                            val timeRangeText = if (hasRealTimes) {
                                                val start = formatTimeShort(currentEpg!!.startEpochMs)
                                                val stop = formatTimeShort(currentEpg.stopEpochMs)
                                                val mins = currentEpg.minutesLeft(now)
                                                "$start - $stop · ${mins}m left"
                                            } else {
                                                "Live Stream"
                                            }
                                            val progressFraction = currentEpg?.progress(now) ?: 0.5f

                                            // Glassmorphic NOW PLAYING Card
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF161A26).copy(alpha = 0.88f))
                                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(7.dp)
                                                                    .background(Color(0xFFEF4444), CircleShape)
                                                            )
                                                            Spacer(Modifier.width(5.dp))
                                                            Text(
                                                                text = "NOW PLAYING",
                                                                style = textStyle(10.sp, FontWeight.ExtraBold),
                                                                color = Color(0xFFEF4444)
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                                                                .padding(horizontal = 7.dp, vertical = 1.5.dp)
                                                        ) {
                                                            Text(
                                                                text = timeRangeText,
                                                                style = textStyle(10.sp, FontWeight.SemiBold),
                                                                color = Color(0xFF94A3B8)
                                                            )
                                                        }
                                                    }

                                                    Spacer(Modifier.height(5.dp))

                                                    Text(
                                                        text = programTitle,
                                                        style = textStyle(16.sp, FontWeight.Bold),
                                                        color = Color.White,
                                                        maxLines = if (nextEpg != null) 1 else 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (!currentEpg?.description.isNullOrBlank()) {
                                                        Spacer(Modifier.height(3.dp))
                                                        Text(
                                                            text = currentEpg!!.description,
                                                            style = textStyle(11.5.sp, FontWeight.Normal),
                                                            color = Color(0xFF94A3B8),
                                                            maxLines = if (nextEpg != null) 1 else 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Spacer(Modifier.height(6.dp))

                                                    JtvProgress(
                                                        fraction = progressFraction,
                                                        color = Color(0xFF00D2FF),
                                                        track = Color.White.copy(alpha = 0.08f),
                                                        height = 3.5.dp
                                                    )
                                                }
                                            }

                                            // UP NEXT Program Card
                                            if (nextEpg != null) {
                                                Spacer(Modifier.height(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(selectedGenreColor.copy(alpha = 0.08f))
                                                        .border(
                                                            1.dp,
                                                            selectedGenreColor.copy(alpha = 0.20f),
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(6.dp)
                                                                        .background(selectedGenreColor, CircleShape)
                                                                )
                                                                Spacer(Modifier.width(5.dp))
                                                                Text(
                                                                    text = "UP NEXT",
                                                                    style = textStyle(9.5.sp, FontWeight.ExtraBold),
                                                                    color = selectedGenreColor
                                                                )
                                                            }
                                                            if (nextEpg.startEpochMs > 0) {
                                                                Text(
                                                                    text = formatTimeShort(nextEpg.startEpochMs),
                                                                    style = textStyle(9.5.sp, FontWeight.SemiBold),
                                                                    color = Color(0xFF94A3B8)
                                                                )
                                                            }
                                                        }

                                                        Spacer(Modifier.height(3.dp))

                                                        Text(
                                                            text = nextEpg.title,
                                                            style = textStyle(13.sp, FontWeight.SemiBold),
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        if (!nextEpg.description.isNullOrBlank()) {
                                                            Spacer(Modifier.height(2.dp))
                                                            Text(
                                                                text = nextEpg.description,
                                                                style = textStyle(10.5.sp, FontWeight.Normal),
                                                                color = Color(0xFF94A3B8),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(10.dp))

                                        // Action buttons at bottom of detail panel - GUARANTEED FULL HEIGHT
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            JtvButton(
                                                text = "Watch Live",
                                                icon = Icons.Default.PlayArrow,
                                                primary = true,
                                                onClick = { onChannelSelected(selected) },
                                                modifier = Modifier.weight(1.35f),
                                                minHeight = 44.dp,
                                                fillMaxWidth = true
                                            )

                                            JtvButton(
                                                text = if (selected.isFavorite) "Favorite" else "Add Fav",
                                                icon = if (selected.isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                                                primary = false,
                                                onClick = { viewModel.toggleFavorite(selected.id) },
                                                modifier = Modifier.weight(1f),
                                                minHeight = 44.dp,
                                                fillMaxWidth = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ───────────────────────── Bottom Key Hints (Frosted Bar) ─────────────────────────
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF11141E).copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                KeyHint(
                    items = listOf(
                        "OK" to "Watch Live",
                        "▲▼" to "Browse Channels",
                        "◄►" to "Switch Category",
                        "Hold OK" to "Toggle Favorite",
                        "Back" to "Exit App"
                    ),
                    keyColor = Color(0xFF00D2FF),
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }

    if (showExitDialog) {
        Dialog(onDismissRequest = { showExitDialog = false }) {
            val exitFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(120)
                try {
                    exitFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }

            Box(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF131724))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Exit",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Exit NTV?",
                        style = textStyle(22.sp, FontWeight.ExtraBold),
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to exit the application?",
                        style = textStyle(14.sp),
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        JtvButton(
                            text = "Yes, Exit",
                            primary = true,
                            fillMaxWidth = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(exitFocusRequester),
                            onClick = {
                                (context as? Activity)?.finishAffinity()
                            }
                        )
                        JtvButton(
                            text = "Cancel",
                            primary = false,
                            fillMaxWidth = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showExitDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}
