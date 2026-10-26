package com.jiotv.androidtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.jiotv.androidtv.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.jiotv.androidtv.data.model.Category
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.ui.components.ChannelPlate
import com.jiotv.androidtv.ui.components.ErrorBanner
import com.jiotv.androidtv.ui.components.JtvButton
import com.jiotv.androidtv.ui.components.JtvClickable
import com.jiotv.androidtv.ui.components.JtvClock
import com.jiotv.androidtv.ui.components.JtvProgress
import com.jiotv.androidtv.ui.components.KeyHint
import com.jiotv.androidtv.ui.components.NumberBlock
import com.jiotv.androidtv.ui.components.rememberMinuteClock
import com.jiotv.androidtv.ui.components.textStyle
import com.jiotv.androidtv.server.EmbeddedServerState
import com.jiotv.androidtv.ui.components.LoginOtpDialog
import com.jiotv.androidtv.ui.components.formatTimeShort
import com.jiotv.androidtv.ui.components.minutesLeft
import com.jiotv.androidtv.ui.components.progress
import com.jiotv.androidtv.ui.navigation.Screen
import com.jiotv.androidtv.ui.theme.Jtv
import com.jiotv.androidtv.ui.viewmodel.MainViewModel

/**
 * Fenyx v2 "Everyday" 3-Pane Home Screen for Android TV:
 * [Left Rail: Categories] · [Center List: Channels] · [Right Pane: Live Preview & Details]
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // ───────────────────────── Top Bar ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: App branding + Status Pills
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ntv_splash_logo),
                    contentDescription = "NTV Logo",
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "NTV",
                    style = textStyle(24.sp, FontWeight.Bold),
                    color = c.tx
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(c.acc, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE",
                        style = textStyle(11.sp, FontWeight.Bold),
                        color = c.accTx
                    )
                }
                Spacer(Modifier.width(16.dp))

                // Server Status Badge
                val isReady = (serverState as? EmbeddedServerState.Running)?.isReady == true
                val serverColor = when (serverState) {
                    is EmbeddedServerState.Running -> if (isReady) Color(0xFF22C55E) else Color(0xFFEAB308)
                    is EmbeddedServerState.Starting -> Color(0xFFEAB308)
                    is EmbeddedServerState.PreparingBinary -> Color(0xFF3B82F6)
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
                        .background(c.plate, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(serverColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = serverText,
                        style = textStyle(12.sp, FontWeight.Medium),
                        color = c.tx
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Jio Status / Login Button (Pill with green/red dot indicator matching server style)
                val isJioLoggedIn = loggedInMobile != null
                val jioColor = if (isJioLoggedIn) Color(0xFF22C55E) else Color(0xFFEF4444)
                val jioText = if (isJioLoggedIn) "JIO: Connected" else "JIO: Log In"

                JtvClickable(
                    onClick = { showLoginDialog = true },
                    container = c.plate,
                    focusedContainer = c.inv,
                    shape = RoundedCornerShape(16.dp),
                ) { focused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(jioColor, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = jioText,
                            style = textStyle(12.sp, FontWeight.Medium),
                            color = if (focused) c.invTx else c.tx
                        )
                    }
                }
            }

            // Right: Navigation Buttons (Search, Settings) + Live Clock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                JtvButton(
                    text = "Search",
                    icon = Icons.Default.Search,
                    onClick = { onNavigate(Screen.Search.route) },
                    fontSize = 13.sp,
                    minHeight = 34.dp
                )
                JtvButton(
                    text = "Settings",
                    icon = Icons.Default.Settings,
                    onClick = { onNavigate(Screen.Settings.route) },
                    fontSize = 13.sp,
                    minHeight = 34.dp
                )
                Spacer(Modifier.width(4.dp))
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
                            color = c.acc,
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
                        // ── Pane 1: Category Column (Width ~190dp) ──
                        Column(
                            modifier = Modifier
                                .width(190.dp)
                                .fillMaxHeight()
                                .background(c.s1, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "CATEGORIES",
                                style = textStyle(12.sp, FontWeight.Bold),
                                color = c.t3,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )

                            LazyColumn(
                                state = categoryListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                        focusedScale = 1.0f,
                                        container = if (isSelected) c.s2 else Color.Transparent,
                                        focusedContainer = c.inv,
                                        shape = RoundedCornerShape(8.dp)
                                    ) { focused ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cat.title,
                                                style = textStyle(14.sp, if (isSelected || focused) FontWeight.Bold else FontWeight.Medium),
                                                color = if (focused) c.invTx else if (isSelected) c.acc else c.tx,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "${cat.channels.size}",
                                                style = textStyle(12.sp, FontWeight.Normal),
                                                color = if (focused) c.invTx.copy(alpha = 0.7f) else c.t3
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Pane 2: Channel List (Width ~340dp) ──
                        Column(
                            modifier = Modifier
                                .width(340.dp)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp, start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = currentCategory?.title?.uppercase() ?: "CHANNELS",
                                    style = textStyle(12.sp, FontWeight.Bold),
                                    color = c.t3
                                )
                                Text(
                                    text = "${displayedChannels.size} channels",
                                    style = textStyle(12.sp, FontWeight.Normal),
                                    color = c.t3
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

                                    JtvClickable(
                                        onClick = {
                                            viewModel.setLastSelectedChannel(channel)
                                            onChannelSelected(channel)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (isTarget) Modifier.focusRequester(targetFocusRequester) else Modifier),
                                        focusedScale = 1.0f,
                                        container = c.s1.copy(alpha = 0.6f),
                                        focusedContainer = c.inv,
                                        shape = RoundedCornerShape(8.dp)
                                    ) { focused ->
                                        if (focused) {
                                            LaunchedEffect(channel.id) {
                                                viewModel.onChannelFocused(channel)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Channel number badge
                                            NumberBlock(
                                                number = channel.number,
                                                fontSize = 13.sp,
                                                modifier = Modifier.widthIn(min = 40.dp)
                                            )

                                            Spacer(Modifier.width(10.dp))

                                            // Channel logo plate
                                            ChannelPlate(
                                                logoUrl = channel.logoUrl.orEmpty(),
                                                width = 46.dp,
                                                height = 34.dp
                                            )

                                            Spacer(Modifier.width(12.dp))

                                            // Channel Name & Info
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = channel.name,
                                                        style = textStyle(15.sp, FontWeight.SemiBold),
                                                        color = if (focused) c.invTx else c.tx,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (channel.isHd) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    if (focused) c.invTx else c.acc,
                                                                    RoundedCornerShape(3.dp)
                                                                )
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = "HD",
                                                                style = textStyle(9.sp, FontWeight.Bold),
                                                                color = if (focused) c.inv else c.accTx
                                                            )
                                                        }
                                                    }
                                                }

                                                val chProg = viewModel.getCurrentProgram(channel.id)?.title
                                                    ?: channel.currentProgram
                                                    ?: "${channel.language} • ${channel.groupTitle}"
                                                Text(
                                                    text = chProg,
                                                    style = textStyle(12.sp, FontWeight.Normal),
                                                    color = if (focused) c.invTx.copy(alpha = 0.75f) else c.t2,
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
                                                    tint = if (focused) c.invTx else c.acc
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Pane 3: Right Detail & Live Preview Card (Weight 1f) ──
                        val selected = focusedChannel ?: displayedChannels.firstOrNull()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(c.s1, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            if (selected != null) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        // Header row with Plate + Number
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            ChannelPlate(
                                                logoUrl = selected.logoUrl.orEmpty(),
                                                width = 140.dp,
                                                height = 90.dp
                                            )

                                            NumberBlock(
                                                number = selected.number,
                                                fontSize = 28.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }

                                        Spacer(Modifier.height(14.dp))

                                        // Channel Title
                                        Text(
                                            text = selected.name,
                                            style = textStyle(26.sp, FontWeight.Bold),
                                            color = c.tx,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(Modifier.height(6.dp))

                                        // Badges row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (selected.isHd) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(c.acc, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "1080p HD",
                                                        style = textStyle(11.sp, FontWeight.Bold),
                                                        color = c.accTx
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .background(c.s2, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = selected.groupTitle,
                                                    style = textStyle(11.sp, FontWeight.SemiBold),
                                                    color = c.t2
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .background(c.s2, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = selected.language,
                                                    style = textStyle(11.sp, FontWeight.SemiBold),
                                                    color = c.t2
                                                )
                                            }

                                            if (selected.isZee) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF8B5CF6), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "ClearKey",
                                                        style = textStyle(11.sp, FontWeight.Bold),
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(16.dp))

                                        // Current Program Card with real-time EPG
                                        val currentEpg = remember(selected.id, epgMap.size, now) {
                                            viewModel.getCurrentProgram(selected.id)
                                        }
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

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(c.s2, RoundedCornerShape(10.dp))
                                                .padding(14.dp)
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
                                                                .size(8.dp)
                                                                .background(Color(0xFFE5484D), CircleShape)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = "NOW PLAYING",
                                                            style = textStyle(11.sp, FontWeight.Bold),
                                                            color = Color(0xFFE5484D)
                                                        )
                                                    }
                                                    Text(
                                                        text = timeRangeText,
                                                        style = textStyle(11.sp, FontWeight.Medium),
                                                        color = c.t2
                                                    )
                                                }

                                                Spacer(Modifier.height(6.dp))

                                                Text(
                                                    text = programTitle,
                                                    style = textStyle(17.sp, FontWeight.SemiBold),
                                                    color = c.tx,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (!currentEpg?.description.isNullOrBlank()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = currentEpg!!.description,
                                                        style = textStyle(12.sp, FontWeight.Normal),
                                                        color = c.t2,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(Modifier.height(8.dp))

                                                JtvProgress(
                                                    fraction = progressFraction,
                                                    color = c.acc,
                                                    track = c.line,
                                                    height = 3.dp
                                                )
                                            }
                                        }
                                    }

                                    // Action buttons at bottom of detail panel
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        JtvButton(
                                            text = "Watch Live",
                                            icon = Icons.Default.PlayArrow,
                                            primary = true,
                                            onClick = { onChannelSelected(selected) },
                                            modifier = Modifier.weight(1.35f),
                                            fillMaxWidth = true
                                        )

                                        JtvButton(
                                            text = if (selected.isFavorite) "Favorite" else "Add Fav",
                                            icon = if (selected.isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                                            primary = false,
                                            onClick = { viewModel.toggleFavorite(selected.id) },
                                            modifier = Modifier.weight(1f),
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

        // ───────────────────────── Bottom Key Hints ─────────────────────────
        Spacer(Modifier.height(8.dp))
        KeyHint(
            items = listOf(
                "OK" to "Watch Live",
                "▲▼" to "Browse Channels",
                "◄►" to "Switch Category",
                "Hold OK" to "Toggle Favorite",
                "Back" to "Exit App"
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.s1)
                    .border(1.dp, c.line, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Exit",
                        tint = c.acc,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Exit NTV?",
                        style = textStyle(22.sp, FontWeight.Bold),
                        color = c.tx
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to exit the application?",
                        style = textStyle(14.sp),
                        color = c.t2,
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
