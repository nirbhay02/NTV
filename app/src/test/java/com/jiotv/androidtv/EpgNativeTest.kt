package com.jiotv.androidtv

import com.jiotv.androidtv.data.model.EpgProgram
import com.jiotv.androidtv.data.repository.EpgRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgNativeTest {

    @Test
    fun testResolveJioChannelId() {
        // Direct digits extraction
        val digits = "zee_167".filter { it.isDigit() }.trimStart('0')
        assertEquals("167", digits)

        val digits2 = "0289".filter { it.isDigit() }.trimStart('0')
        assertEquals("289", digits2)
    }

    @Test
    fun testIsMovieChannelDetection() {
        assertTrue(EpgRepository.isMovieChannel("Sony Max HD"))
        assertTrue(EpgRepository.isMovieChannel("Star Gold Romance"))
        assertTrue(EpgRepository.isMovieChannel("Zee Cinema"))
        assertTrue(EpgRepository.isMovieChannel("&pictures"))
        assertTrue(EpgRepository.isMovieChannel("Colors Cineplex"))
        assertTrue(EpgRepository.isMovieChannel("Star Movies HD"))
        assertTrue(EpgRepository.isMovieChannel("B4U Movies"))
        assertTrue(EpgRepository.isMovieChannel("Goldmines Action"))

        assertFalse(EpgRepository.isMovieChannel("Aaj Tak"))
        assertFalse(EpgRepository.isMovieChannel("Sony SAB"))
        assertFalse(EpgRepository.isMovieChannel("Star Plus"))
        assertFalse(EpgRepository.isMovieChannel("Sony Sports Ten 1 HD"))
    }

    @Test
    fun testMovieChannelsNeverProjectExpiredSchedules() {
        val now = System.currentTimeMillis()
        val pastStart = now - (3 * 24 * 3600_000L) // 3 days ago
        val pastStop = pastStart + (2 * 3600_000L)

        val expiredMovieProgram = EpgProgram(
            channelId = "289",
            title = "Old Movie From 3 Days Ago",
            startEpochMs = pastStart,
            stopEpochMs = pastStop
        )

        // Movie channels should reject forward projection of expired feeds
        val resultMovie = EpgRepository.projectProgramsToToday(
            listOf(expiredMovieProgram),
            now = now,
            channelName = "Sony Max HD"
        )
        assertTrue("Movie channels must never project expired schedules forward", resultMovie.isEmpty())

        // Non-movie channels (e.g. daily news/soap) are allowed to project
        val resultNews = EpgRepository.projectProgramsToToday(
            listOf(expiredMovieProgram),
            now = now,
            channelName = "Aaj Tak"
        )
        assertTrue("Non-movie channels can project daily schedules", resultNews.isNotEmpty())
        assertTrue("Projected time should be around now", resultNews.first().stopEpochMs > now)
    }
}
