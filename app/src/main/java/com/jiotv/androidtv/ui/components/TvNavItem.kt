package com.jiotv.androidtv.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

data class TvNavItem(
    val title: String,
    val route: String,
    val icon: ImageVector,
    val isZeeHighlight: Boolean = false
)
