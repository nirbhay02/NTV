package com.jiotv.androidtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.ui.components.ChannelPlate
import com.jiotv.androidtv.ui.components.JtvClickable
import com.jiotv.androidtv.ui.components.NumberBlock
import com.jiotv.androidtv.ui.components.TvTopBar
import com.jiotv.androidtv.ui.navigation.Screen
import com.jiotv.androidtv.ui.theme.BackgroundDark
import com.jiotv.androidtv.ui.theme.FocusBorder
import com.jiotv.androidtv.ui.theme.Jtv
import com.jiotv.androidtv.ui.theme.SurfaceDark
import com.jiotv.androidtv.ui.theme.TextPrimary
import com.jiotv.androidtv.ui.theme.TextSecondary
import com.jiotv.androidtv.ui.theme.ZeeGold
import com.jiotv.androidtv.ui.viewmodel.MainViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ZeeChannelsScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onChannelSelected: (Channel) -> Unit
) {
    val channels by viewModel.channels.collectAsState()
    val zeeChannels = channels.filter { it.isZee }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Navigation Header
        TvTopBar(
            currentRoute = Screen.ZeeChannels.route,
            onNavigate = onNavigate
        )

        // Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF2A153A), Color(0xFF160E2A), BackgroundDark)
                    )
                )
                .border(1.dp, ZeeGold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZeeGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ZEE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column {
                    Text(
                        text = "Zee Network Channels Hub",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${zeeChannels.size} Premium Zee channels with ClearKey DRM playback",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Channels Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = zeeChannels,
                key = { index, item -> "${item.id}_$index" }
            ) { _, channel ->
                JtvClickable(
                    onClick = { onChannelSelected(channel) },
                    shape = RoundedCornerShape(12.dp),
                    container = SurfaceDark,
                    focusedContainer = FocusBorder
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelPlate(
                            logoUrl = channel.logoUrl.orEmpty(),
                            width = 64.dp,
                            height = 48.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.name,
                                color = if (focused) Color.Black else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NumberBlock(number = channel.number, fontSize = 11.sp)
                                if (channel.isHd) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "HD",
                                        color = if (focused) Color.Black else ZeeGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
