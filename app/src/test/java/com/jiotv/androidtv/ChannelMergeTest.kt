package com.jiotv.androidtv

import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.data.repository.ChannelRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMergeTest {

    @Test
    fun testMergeChannels_prioritizesThirdPartyZeeAndServerNonZee() {
        val serverChannels = listOf(
            Channel(
                id = "504",
                name = "Zee News",
                number = 504,
                streamUrl = "http://127.0.0.1:5001/live/504.m3u8",
                groupTitle = "News",
                isZee = false
            ),
            Channel(
                id = "442",
                name = "Zee 24 Taas",
                number = 442,
                streamUrl = "http://127.0.0.1:5001/live/442.m3u8",
                groupTitle = "News",
                isZee = false
            ),
            Channel(
                id = "101",
                name = "Star Plus",
                number = 101,
                streamUrl = "http://127.0.0.1:5001/live/101.m3u8",
                groupTitle = "Entertainment",
                isZee = false
            )
        )

        val thirdParty = listOf(
            Channel(
                id = "zee_167",
                name = "Zee TV HD",
                number = 167,
                streamUrl = "https://jiotvpllive.cdn.jio.com/bpk-tv/ZeeTVHD_BTS/WDVLive/index.mpd",
                groupTitle = "Zee Network",
                isZee = true
            ),
            Channel(
                id = "zee_1351",
                name = "Zee TV",
                number = 1351,
                streamUrl = "https://jiotvpllive.cdn.jio.com/bpk-tv/ZeeTV_BTS/WDVLive/index.mpd",
                groupTitle = "Zee Network",
                isZee = true
            ),
            Channel(
                id = "zee_504",
                name = "Zee News",
                number = 504,
                streamUrl = "https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8",
                groupTitle = "Zee Network",
                isZee = true
            )
        )

        val merged = ChannelRepository.mergeChannels(serverChannels, thirdParty)

        // Total channels: 5 (Star Plus, Zee TV HD, Zee TV, Zee 24 Taas, Zee News)
        assertEquals(5, merged.size)

        // Zee News should use the reliable CloudFront stream
        val zeeNews = merged.first { it.number == 504 }
        assertEquals("https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8", zeeNews.streamUrl)
        assertTrue("Zee News should be marked isZee", zeeNews.isZee)
        assertEquals("Zee Network", zeeNews.groupTitle)

        // Zee TV HD must be present and marked isZee with native DASH stream
        val zeeTvHd = merged.first { it.number == 167 }
        assertEquals("zee_167", zeeTvHd.id)
        assertEquals("https://jiotvpllive.cdn.jio.com/bpk-tv/ZeeTVHD_BTS/WDVLive/index.mpd", zeeTvHd.streamUrl)
        assertTrue("Zee TV HD should be marked isZee", zeeTvHd.isZee)

        // Zee TV SD must also be present
        val zeeTvSd = merged.first { it.number == 1351 }
        assertEquals("zee_1351", zeeTvSd.id)
        assertEquals("https://jiotvpllive.cdn.jio.com/bpk-tv/ZeeTV_BTS/WDVLive/index.mpd", zeeTvSd.streamUrl)

        // Server non-Zee channel (Star Plus) must be present with server stream
        val starPlus = merged.first { it.number == 101 }
        assertEquals("101", starPlus.id)
        assertEquals("http://127.0.0.1:5001/live/101.m3u8", starPlus.streamUrl)
    }
}
