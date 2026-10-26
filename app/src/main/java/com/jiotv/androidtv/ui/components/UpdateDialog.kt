package com.jiotv.androidtv.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.jiotv.androidtv.data.update.AppUpdateManager
import com.jiotv.androidtv.ui.theme.ErrorRed
import com.jiotv.androidtv.ui.theme.FocusBorder
import com.jiotv.androidtv.ui.theme.JioBlue
import com.jiotv.androidtv.ui.theme.LiveGreen
import com.jiotv.androidtv.ui.theme.SurfaceDark
import com.jiotv.androidtv.ui.theme.SurfaceDarkVariant
import com.jiotv.androidtv.ui.theme.TextPrimary
import com.jiotv.androidtv.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdateDialog(
    updateInfo: AppUpdateManager.UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    errorMessage: String?,
    onDownloadAndInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { initialFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = { /* prevent click-through */ }),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, Color(0xFF2C303E), RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            // Header Row: Icon + Title + Version Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(JioBlue.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = JioBlue,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "New Version Available",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "An update is ready for your Android TV",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Version Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LiveGreen.copy(alpha = 0.15f))
                        .border(1.dp, LiveGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v${updateInfo.versionName}",
                        color = LiveGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // What's New Section
            Text(
                text = "WHAT'S NEW",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F1117))
                    .border(1.dp, Color(0xFF1E2230), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = updateInfo.changelog.ifBlank { "• Performance optimizations and bug fixes." },
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // File size indicator
            if (updateInfo.apkSize > 0) {
                val sizeMb = String.format(Locale.US, "%.1f MB", updateInfo.apkSize / (1024.0 * 1024.0))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Download size: $sizeMb (optimized for this device)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Downloading Progress State
            if (isDownloading) {
                Spacer(modifier = Modifier.height(18.dp))
                val dlMb = String.format(Locale.US, "%.1f", downloadedBytes / (1024.0 * 1024.0))
                val totMb = if (totalBytes > 0) String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0)) else ""
                val percent = (downloadProgress * 100).toInt().coerceIn(0, 100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloading update... ($dlMb / $totMb)",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "$percent%",
                        color = FocusBorder,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = FocusBorder,
                    trackColor = Color(0xFF1E2230)
                )
            }

            // Error display
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = ErrorRed,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (!isDownloading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Remind Later Button
                    TvDialogButton(
                        text = "Later",
                        isPrimary = false,
                        onClick = onDismiss
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    // Update Now Button (Focused by default)
                    TvDialogButton(
                        text = "Update Now",
                        isPrimary = true,
                        modifier = Modifier.focusRequester(initialFocus),
                        onClick = onDownloadAndInstall
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = FocusBorder,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Preparing installation package, please wait...",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvDialogButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> FocusBorder
        isPrimary -> JioBlue
        else -> SurfaceDarkVariant
    }

    val txtColor = when {
        isFocused -> Color.Black
        isPrimary -> Color.White
        else -> TextPrimary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = txtColor,
            fontSize = 14.sp,
            fontWeight = if (isPrimary || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}
