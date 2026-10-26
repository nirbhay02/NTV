package com.jiotv.androidtv.data.model

/**
 * Electronic Program Guide (EPG) metadata for TV schedule.
 */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String = "",
    val startEpochMs: Long = 0L,
    val stopEpochMs: Long = 0L,
    val category: String? = null
) {
    val startMs: Long get() = startEpochMs
    val stopMs: Long get() = stopEpochMs

    val isCurrentlyAiring: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now in startEpochMs..stopEpochMs
        }

    val progressPercent: Float
        get() {
            val now = System.currentTimeMillis()
            if (stopEpochMs <= startEpochMs) return 0f
            val elapsed = now - startEpochMs
            val total = stopEpochMs - startEpochMs
            return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}
