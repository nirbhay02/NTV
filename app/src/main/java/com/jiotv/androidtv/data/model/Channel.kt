package com.jiotv.androidtv.data.model

import androidx.compose.runtime.Immutable

/**
 * Represents a live television channel parsed from the jiotv_go M3U playlist.
 */
// [NUVIO-OPT-2] Mark model @Immutable so Compose skips unnecessary card recompositions during scrolling
@Immutable
data class Channel(
    val id: String,
    val name: String,
    val number: Int = 0,
    val logoUrl: String? = null,
    val streamUrl: String,
    val groupTitle: String = "Entertainment",
    val language: String = "Hindi",
    val isHd: Boolean = false,
    val isZee: Boolean = false,
    val tvgName: String? = null,
    val currentProgram: String? = null,
    val isFavorite: Boolean = false,
    val licenseKeyUrl: String? = null,
    val licenseType: String? = null,
    val manifestType: String? = null
) {
    val isDrm: Boolean
        get() = !licenseKeyUrl.isNullOrEmpty() ||
                streamUrl.contains("/live/mpd/", ignoreCase = true) ||
                licenseType?.contains("widevine", ignoreCase = true) == true

    /**
     * Display label e.g., "101. Zee TV HD"
     */
    val displayTitle: String
        get() = if (number > 0) "$number. $name" else name

    /**
     * Quality badge string (e.g., "HD" or "SD")
     */
    val qualityBadge: String
        get() = if (isHd) "HD" else "SD"
}
