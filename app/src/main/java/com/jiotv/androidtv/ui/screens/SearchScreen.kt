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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jiotv.androidtv.ui.theme.JioBlue
import com.jiotv.androidtv.ui.theme.SurfaceDark
import com.jiotv.androidtv.ui.theme.SurfaceDarkVariant
import com.jiotv.androidtv.ui.theme.TextPrimary
import com.jiotv.androidtv.ui.theme.TextSecondary
import com.jiotv.androidtv.ui.theme.ZeeGold
import com.jiotv.androidtv.ui.viewmodel.MainViewModel

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.jiotv.androidtv.ui.theme.TvPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onChannelSelected: (Channel) -> Unit
) {
    val allChannels by viewModel.channels.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("All") }
    val focusManager = LocalFocusManager.current

    val tags = listOf("All", "Zee", "Sports", "Movies", "News", "Entertainment", "Kids", "HD")

    val filteredChannels = remember(allChannels, selectedTag, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val tagFiltered = when (selectedTag) {
            "All" -> allChannels
            "Zee" -> allChannels.filter { it.isZee }
            "Sports" -> allChannels.filter { it.groupTitle.contains("Sports", ignoreCase = true) || it.name.contains("Sports", ignoreCase = true) }
            "Movies" -> allChannels.filter { it.groupTitle.contains("Movies", ignoreCase = true) || it.name.contains("Cinema", ignoreCase = true) || it.name.contains("Movie", ignoreCase = true) }
            "News" -> allChannels.filter { it.groupTitle.contains("News", ignoreCase = true) }
            "Entertainment" -> allChannels.filter { it.groupTitle.contains("Entertainment", ignoreCase = true) }
            "Kids" -> allChannels.filter { it.groupTitle.contains("Kids", ignoreCase = true) || it.name.contains("Cartoon", ignoreCase = true) || it.name.contains("Nick", ignoreCase = true) || it.name.contains("Pogo", ignoreCase = true) }
            "HD" -> allChannels.filter { it.isHd }
            else -> allChannels
        }
        if (query.isEmpty()) {
            tagFiltered
        } else {
            tagFiltered.filter { channel ->
                channel.name.lowercase().contains(query) ||
                channel.number.toString().contains(query) ||
                channel.groupTitle.lowercase().contains(query) ||
                channel.language.lowercase().contains(query) ||
                channel.tvgName?.lowercase()?.contains(query) == true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TvTopBar(
            currentRoute = Screen.Search.route,
            onNavigate = onNavigate
        )

        // ── Interactive Search Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val searchInteractionSource = remember { MutableInteractionSource() }
            val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSearchFocused) SurfaceDarkVariant else SurfaceDark)
                    .border(
                        width = if (isSearchFocused) 2.dp else 1.dp,
                        color = if (isSearchFocused) TvPrimary else SurfaceDarkVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Channels",
                    tint = if (isSearchFocused) TvPrimary else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search channels by name, number, or category...",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusable(interactionSource = searchInteractionSource),
                        interactionSource = searchInteractionSource,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(TvPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                        })
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceDarkVariant)
                            .clickable { searchQuery = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Filter Tags Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Quick Filter Categories",
                fontSize = 14.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tags.forEach { tag ->
                    val isSelected = selectedTag == tag
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
                            .clickable(interactionSource = interactionSource, indication = null) {
                                selectedTag = tag
                            }
                            .focusable(interactionSource = interactionSource)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tag,
                            color = txtColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${filteredChannels.size} channels found",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No channels matching \"$searchQuery\"" else "No channels found",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Try searching for a different channel name, number, or category.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Results Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = filteredChannels,
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
}


