package com.jiotv.androidtv.data.model

data class Category(
    val id: String,
    val title: String,
    val iconName: String? = null,
    val channels: List<Channel> = emptyList(),
    val isPriority: Boolean = false
)
