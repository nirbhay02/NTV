package com.jiotv.androidtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.jiotv.androidtv.ui.components.UpdateDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.jiotv.androidtv.data.parser.LanguageUtils
import com.jiotv.androidtv.server.EmbeddedServerState
import com.jiotv.androidtv.ui.components.LoginOtpDialog
import com.jiotv.androidtv.ui.components.TvTopBar
import com.jiotv.androidtv.ui.navigation.Screen
import com.jiotv.androidtv.ui.theme.BackgroundDark
import com.jiotv.androidtv.ui.theme.ErrorRed
import com.jiotv.androidtv.ui.theme.FocusBorder
import com.jiotv.androidtv.ui.theme.JioBlue
import com.jiotv.androidtv.ui.theme.LiveGreen
import com.jiotv.androidtv.ui.theme.SurfaceDark
import com.jiotv.androidtv.ui.theme.SurfaceDarkVariant
import com.jiotv.androidtv.ui.theme.TextPrimary
import com.jiotv.androidtv.ui.theme.TextSecondary
import com.jiotv.androidtv.ui.theme.ZeeGold
import com.jiotv.androidtv.ui.viewmodel.SettingsViewModel
import com.jiotv.androidtv.ui.viewmodel.TestConnectionState

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (String) -> Unit
) {
    val serverConfig by viewModel.serverConfig.collectAsState()
    val embeddedState by viewModel.embeddedServerState.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val loggedInMobile by viewModel.loggedInMobile.collectAsState()

    val enabledLanguages by viewModel.enabledLanguages.collectAsState()
    val availableLanguages by viewModel.availableLanguages.collectAsState()
    val totalChannelsCount by viewModel.totalChannelsCount.collectAsState()
    val visibleChannelsCount by viewModel.visibleChannelsCount.collectAsState()
    val isEpgSyncing by viewModel.isEpgSyncing.collectAsState()
    val epgChannelCount by viewModel.epgChannelCount.collectAsState()

    var qualitySelection by remember(serverConfig.quality) { mutableStateOf(serverConfig.quality) }
    var showLoginDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Update state
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()
    val downloadedBytes by viewModel.downloadedBytes.collectAsState()
    val totalBytes by viewModel.totalBytes.collectAsState()
    val updateStatusMessage by viewModel.updateStatusMessage.collectAsState()
    val updateError by viewModel.updateError.collectAsState()

    var showUpdateDialogManually by remember { mutableStateOf(false) }

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

    // OTA Update Dialog
    updateInfo?.let { info ->
        if (info.isUpdateAvailable && showUpdateDialogManually) {
            UpdateDialog(
                updateInfo = info,
                isDownloading = isDownloadingUpdate,
                downloadProgress = updateProgress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                errorMessage = updateError,
                onDownloadAndInstall = { viewModel.downloadAndInstallUpdate(context) },
                onDismiss = {
                    showUpdateDialogManually = false
                    viewModel.dismissUpdateDialog()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TvTopBar(
            currentRoute = Screen.Settings.route,
            onNavigate = onNavigate
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
                .verticalScroll(scrollState)
        ) {
            // ─── SECTION 1: Embedded Server Status ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = JioBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Embedded NTV Server",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        // Status Badge
                        val (badgeText, badgeColor) = when (embeddedState) {
                            is EmbeddedServerState.Running -> "ONLINE" to LiveGreen
                            is EmbeddedServerState.Starting -> "STARTING..." to ZeeGold
                            is EmbeddedServerState.PreparingBinary -> "PREPARING..." to JioBlue
                            is EmbeddedServerState.Stopped -> "STOPPED" to TextSecondary
                            is EmbeddedServerState.Error -> "ERROR" to ErrorRed
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "The app embeds the native Go backend directly inside itself. No PC, Termux, or external device required.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsButton(
                            label = "Start Server",
                            icon = Icons.Default.PlayArrow,
                            onClick = { viewModel.startEmbeddedServer() }
                        )
                        SettingsButton(
                            label = "Stop Server",
                            icon = Icons.Default.Stop,
                            onClick = { viewModel.stopEmbeddedServer() }
                        )
                        SettingsButton(
                            label = "Restart Server",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.restartEmbeddedServer() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── SECTION 2: Stream Quality ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Stream Quality (HLS Bitrate)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sets default adaptive bitrate profile for TV streaming.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("high", "medium", "low").forEach { q ->
                            val isSel = qualitySelection == q
                            SettingsChoiceButton(
                                label = q.uppercase(),
                                isSelected = isSel,
                                onClick = {
                                    qualitySelection = q
                                    viewModel.updateServerConfig(serverConfig.host, serverConfig.port, q)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── SECTION 3: Channel Language Filter ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = JioBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Channel Language Filter",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Hide channels of unselected languages across TV Guide, Search, and Player",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Status Badge
                        val isAllActive = enabledLanguages.isEmpty()
                        val badgeText = if (isAllActive) "ALL LANGUAGES ACTIVE" else "${enabledLanguages.size} LANGUAGES ACTIVE"
                        val badgeColor = if (isAllActive) LiveGreen else JioBlue

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.18f))
                                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Channel visibility counter summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1117))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (totalChannelsCount > 0) {
                                "Currently showing $visibleChannelsCount of $totalChannelsCount total channels"
                            } else {
                                "Loading channels..."
                            },
                            fontSize = 13.sp,
                            color = FocusBorder,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Preset Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isHindiEngSelected = enabledLanguages.size == 2 &&
                                enabledLanguages.contains("Hindi") &&
                                enabledLanguages.contains("English")

                        SettingsPresetButton(
                            label = "⭐ Hindi + English Only",
                            isSelected = isHindiEngSelected,
                            accentColor = FocusBorder,
                            onClick = { viewModel.setHindiEnglishOnly() }
                        )

                        SettingsPresetButton(
                            label = "✓ Show All Languages",
                            isSelected = enabledLanguages.isEmpty(),
                            accentColor = LiveGreen,
                            onClick = { viewModel.enableAllLanguages() }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "SELECT LANGUAGES TO DISPLAY:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val displayLanguages = if (availableLanguages.isNotEmpty()) {
                        availableLanguages
                    } else {
                        LanguageUtils.POPULAR_LANGUAGES.map { it to 0 }
                    }

                    displayLanguages.chunked(3).forEach { rowLangs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowLangs.forEach { (lang, count) ->
                                val isChecked = enabledLanguages.isEmpty() || enabledLanguages.contains(lang)
                                LanguageToggleCard(
                                    language = lang,
                                    count = count,
                                    isChecked = isChecked,
                                    onToggle = { viewModel.toggleLanguage(lang) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowLangs.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── SECTION 4: Account & Channel Reload ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Jio Account & Playlists",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val mobile = loggedInMobile
                    Text(
                        text = if (!mobile.isNullOrEmpty()) "Logged in as: +91 $mobile" else "Not logged in. You can login with Jio OTP to unlock full access.",
                        fontSize = 13.sp,
                        color = if (!mobile.isNullOrEmpty()) LiveGreen else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsButton(
                            label = if (!mobile.isNullOrEmpty()) "Switch Account" else "Login via OTP",
                            icon = Icons.Default.Lock,
                            onClick = { showLoginDialog = true }
                        )
                        if (!mobile.isNullOrEmpty()) {
                            SettingsButton(
                                label = "Logout",
                                icon = Icons.Default.Stop,
                                onClick = { viewModel.logout() }
                            )
                        }
                        SettingsButton(
                            label = "Reload Channels",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.reloadChannels() }
                        )
                        SettingsButton(
                            label = if (isEpgSyncing) "Syncing EPG..." else if (epgChannelCount > 0) "Refresh EPG ($epgChannelCount)" else "Sync EPG Guide",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.refreshEpg() }
                        )
                        SettingsButton(
                            label = "Test Connection",
                            icon = Icons.Default.CheckCircle,
                            onClick = { viewModel.testConnection(serverConfig.host, serverConfig.port) }
                        )
                    }

                    when (val state = testState) {
                        is TestConnectionState.Testing -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = JioBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting to NTV server...", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        is TestConnectionState.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(state.message, fontSize = 13.sp, color = LiveGreen, fontWeight = FontWeight.Medium)
                        }
                        is TestConnectionState.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(state.message, fontSize = 13.sp, color = ErrorRed, fontWeight = FontWeight.Medium)
                        }
                        is TestConnectionState.Idle -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── SECTION 4: Live Server Logs ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Embedded Server Console Logs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1117))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (consoleLogs.isEmpty()) {
                            Text(
                                text = "No server logs yet. Start the embedded server above to see live stdout messages.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Column {
                                consoleLogs.forEach { logLine ->
                                    Text(
                                        text = logLine,
                                        color = if (logLine.contains("ERROR", ignoreCase = true)) ErrorRed else TextSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── SECTION 5: Software Updates & System Info ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Software Updates & Version",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Over-the-air automatic updates for Android TV and Fire TV",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        // Version badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(JioBlue.copy(alpha = 0.2f))
                                .border(1.dp, JioBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "v${viewModel.currentVersionName} (Build ${viewModel.currentVersionCode})",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Device Architecture Information
                    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "universal"
                    val archLabel = when {
                        primaryAbi.startsWith("arm64") -> "ARM64 (64-bit Android TV / Tablet)"
                        primaryAbi.startsWith("armeabi") -> "ARMv7 (32-bit Fire TV / TV Stick)"
                        else -> primaryAbi
                    }

                    Text(
                        text = "Device Architecture: $archLabel",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Message Display
                    if (!updateStatusMessage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F1117))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCheckingUpdate || isDownloadingUpdate) {
                                    CircularProgressIndicator(
                                        color = FocusBorder,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = updateStatusMessage ?: "",
                                    color = if (updateError != null) ErrorRed else TextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsButton(
                            label = if (isCheckingUpdate) "Checking..." else "Check for Updates",
                            icon = Icons.Default.Refresh,
                            onClick = {
                                viewModel.checkForUpdates(manual = true)
                                showUpdateDialogManually = true
                            }
                        )

                        updateInfo?.let { info ->
                            if (info.isUpdateAvailable) {
                                SettingsButton(
                                    label = "Install Update v${info.versionName}",
                                    icon = Icons.Default.Download,
                                    onClick = {
                                        showUpdateDialogManually = true
                                        viewModel.downloadAndInstallUpdate(context)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) FocusBorder else SurfaceDarkVariant)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else TextPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (isFocused) Color.Black else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsChoiceButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> FocusBorder
        isSelected -> JioBlue
        else -> SurfaceDarkVariant
    }

    val txtColor = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> TextSecondary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = txtColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsPresetButton(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> FocusBorder
        isSelected -> accentColor.copy(alpha = 0.22f)
        else -> SurfaceDarkVariant
    }

    val borderColor = when {
        isFocused -> Color.White
        isSelected -> accentColor
        else -> Color.Transparent
    }

    val txtColor = when {
        isFocused -> Color.Black
        isSelected -> accentColor
        else -> TextPrimary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(if (isFocused || isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = txtColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LanguageToggleCard(
    language: String,
    count: Int,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> FocusBorder.copy(alpha = 0.22f)
        isChecked -> SurfaceDarkVariant
        else -> Color(0xFF141721)
    }

    val borderColor = when {
        isFocused -> FocusBorder
        isChecked -> JioBlue.copy(alpha = 0.5f)
        else -> Color(0xFF232736)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Checkbox visual indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isChecked && isFocused -> FocusBorder
                            isChecked -> JioBlue
                            else -> Color(0xFF1E2230)
                        }
                    )
                    .border(
                        1.dp,
                        if (isFocused) Color.White else if (isChecked) JioBlue else Color(0xFF3E455E),
                        RoundedCornerShape(4.dp)
                    )
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isFocused) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = language,
                color = when {
                    isFocused -> Color.White
                    isChecked -> TextPrimary
                    else -> TextSecondary.copy(alpha = 0.6f)
                },
                fontSize = 14.sp,
                fontWeight = if (isChecked || isFocused) FontWeight.Bold else FontWeight.Normal
            )
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0F1117))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    fontSize = 11.sp,
                    color = if (isChecked) FocusBorder else TextSecondary.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
