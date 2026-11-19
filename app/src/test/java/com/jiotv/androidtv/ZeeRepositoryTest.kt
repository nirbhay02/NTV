package com.jiotv.androidtv

import com.jiotv.androidtv.data.repository.ZeeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeeRepositoryTest {

    @Test
    fun testParseM3u_retainsIndianDashMpdAndClearKey() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="167" tvg-name="Zee TV HD" tvg-logo="https://img.media.jio.com/zeetv.jpg" group-title="Entertainment", Zee TV HD
            #KODIPROP:inputstream=inputstream.adaptive
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=7b3d2290787255ac9cdaf43ae31eacce:d0edfa8ebd655926c5e2932d8c59b3d1
            #EXTVLCOPT:http-cookie=__hdnea__=st=1000~exp=2000~acl=/*~hmac=expiredtoken
            https://jiotvpllive.cdn.jio.com/bpk-tv/ZeeTVHD_BTS/WDVLive/index.mpd
        """.trimIndent()

        val parsed = ZeeRepository.parseM3u(sampleM3u)
        assertEquals(1, parsed.size)
        val zeeTv = parsed[0]
        assertEquals(167, zeeTv.channelNumber)
        assertEquals("Zee TV HD", zeeTv.name)
        // Uses native Jio CDN MPD URL and ClearKey DRM pair
        assertTrue(zeeTv.streamUrl.contains("jiotvpllive.cdn.jio.com/bpk-tv/ZeeTVHD_BTS/WDVLive/index.mpd"))
        assertTrue(zeeTv.isMpd)
        assertEquals("7b3d2290787255ac9cdaf43ae31eacce:d0edfa8ebd655926c5e2932d8c59b3d1", zeeTv.licenseKey)
    }

    @Test
    fun testParseM3u_newsHlsStream() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="504" tvg-name="Zee News" group-title="News", Zee News
            https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8
        """.trimIndent()

        val parsed = ZeeRepository.parseM3u(sampleM3u)
        assertEquals(1, parsed.size)
        val zeeNews = parsed[0]
        assertEquals(504, zeeNews.channelNumber)
        assertEquals("Zee News", zeeNews.name)
        assertEquals("https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8", zeeNews.streamUrl)
        assertFalse(zeeNews.isMpd)
    }

    @Test
    fun testIsTokenExpired() {
        val pastSec = (System.currentTimeMillis() / 1000L) - 3600L
        val futureSec = (System.currentTimeMillis() / 1000L) + 3600L

        val expiredUrl = "https://cdn.jio.com/stream.mpd?__hdnea__=st=1000~exp=$pastSec~acl=/*~hmac=abc"
        val validUrl = "https://cdn.jio.com/stream.mpd?__hdnea__=st=1000~exp=$futureSec~acl=/*~hmac=abc"
        val nonManagedUrl = "https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8"

        assertTrue(ZeeRepository.isTokenExpired(expiredUrl, null))
        assertFalse(ZeeRepository.isTokenExpired(validUrl, null))
        assertFalse(ZeeRepository.isTokenExpired(nonManagedUrl, null))
    }

    @Test
    fun testGetFallbackStreamUrl() {
        // Zee TV HD (167) falls back to Jio server
        val fallback167 = ZeeRepository.getFallbackStreamUrl("zee_167", 167, "http://127.0.0.1:5001")
        assertEquals("http://127.0.0.1:5001/live/167.m3u8", fallback167)

        // Zee News (504) has CloudFront fallback
        val fallback504 = ZeeRepository.getFallbackStreamUrl("504", 504, "http://127.0.0.1:5001")
        assertEquals("https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8", fallback504)

        // A Jio channel without CloudFront fallback falls back to Jio server
        val fallbackJio = ZeeRepository.getFallbackStreamUrl("zee_488", 488, "http://127.0.0.1:5001")
        assertEquals("http://127.0.0.1:5001/live/488.m3u8", fallbackJio)
    }

    @Test
    fun testGetCloudFrontStream() {
        // Domestic entertainment/movie channels should NOT return CloudFront streams
        assertNull(ZeeRepository.getCloudFrontStream("167", 167, "Zee TV HD"))
        assertNull(ZeeRepository.getCloudFrontStream("1351", 1351, "Zee TV"))
        assertNull(ZeeRepository.getCloudFrontStream("165", 165, "Zee Cinema HD"))

        // News channels should return Indian CloudFront news streams
        assertEquals("https://dknttpxmr0dwf.cloudfront.net/index_57.m3u8", ZeeRepository.getCloudFrontStream("504", 504, "Zee News"))
        assertEquals("https://dgrvlduwztkd4.cloudfront.net/index_1.m3u8", ZeeRepository.getCloudFrontStream("442", 442, "Zee 24 Taas"))
    }

    @Test
    fun testIsZeeChannelName() {
        assertTrue(ZeeRepository.isZeeChannelName("Zee TV HD"))
        assertTrue(ZeeRepository.isZeeChannelName("Zee Cinema"))
        assertTrue(ZeeRepository.isZeeChannelName("&pictures"))
        assertTrue(ZeeRepository.isZeeChannelName("And TV HD"))
        assertTrue(ZeeRepository.isZeeChannelName("Zing"))
        assertTrue(ZeeRepository.isZeeChannelName("Zee 24 Taas"))

        assertFalse(ZeeRepository.isZeeChannelName("Star Sports 1"))
        assertFalse(ZeeRepository.isZeeChannelName("Sony MAX"))
        assertFalse(ZeeRepository.isZeeChannelName("Aaj Tak"))
    }

    @Test
    fun testClearKeyToJwkJson() {
        val clearKey = "7b3d2290787255ac9cdaf43ae31eacce:d0edfa8ebd655926c5e2932d8c59b3d1"
        val jwk = ZeeRepository.clearKeyToJwkJson(clearKey)
        assertTrue(jwk.contains("\"kty\":\"oct\""))
        assertTrue(jwk.contains("\"kid\":"))
        assertTrue(jwk.contains("\"k\":"))
    }
}
