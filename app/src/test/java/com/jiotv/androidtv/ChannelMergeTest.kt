package com.jiotv.androidtv

import com.jiotv.androidtv.data.model.Channel
import com.jiotv.androidtv.data.repository.ChannelRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMergeTest {

    @Test
    fun testMergeChannels_prioritizesServerChannelsAndMarksZee() {
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
                streamUrl = "https://d1rc86nwwc9fag.cloudfront.net/260723/zeetvapachd_2500/chunks.m3u8",
                groupTitle = "Zee Network",
                isZee = true
            ),
            Channel(
                id = "zee_504",
                name = "Zee News",
                number = 504,
                streamUrl = "https://jiotvpllive.cdn.jio.com/bpk-tv/Zee_News_BTS/WDVLive/index.mpd",
                groupTitle = "Zee Network",
                isZee = true
            )
        )

        val merged = ChannelRepository.mergeChannels(serverChannels, thirdParty)

        // Total channels: 4 (Star Plus, Zee TV HD, Zee 24 Taas, Zee News)
        assertEquals(4, merged.size)

        // Zee News should retain the server's working streamUrl!
        val zeeNews = merged.first { it.number == 504 }
        assertEquals("http://127.0.0.1:5001/live/504.m3u8", zeeNews.streamUrl)
        assertEquals("504", zeeNews.id)
        assertTrue("Zee News should be marked isZee", zeeNews.isZee)
        assertEquals("Zee Network", zeeNews.groupTitle)

        // Zee TV HD (not in serverChannels) should be included from thirdParty
        val zeeTv = merged.first { it.number == 167 }
        assertEquals("zee_167", zeeTv.id)
        assertEquals("https://d1rc86nwwc9fag.cloudfront.net/260723/zeetvapachd_2500/chunks.m3u8", zeeTv.streamUrl)
        assertTrue("Zee TV HD should be marked isZee", zeeTv.isZee)
    }
}
