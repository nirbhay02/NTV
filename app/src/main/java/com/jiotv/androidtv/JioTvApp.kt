package com.jiotv.androidtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jiotv.androidtv.data.local.AppPreferences
import com.jiotv.androidtv.data.repository.ChannelRepository
import com.jiotv.androidtv.data.repository.EpgRepository
import com.jiotv.androidtv.data.repository.ServerConfigRepository

class JioTvApp : Application(), ImageLoaderFactory {

    lateinit var appPreferences: AppPreferences
        private set

    lateinit var serverConfigRepository: ServerConfigRepository
        private set

    lateinit var channelRepository: ChannelRepository
        private set

    lateinit var epgRepository: EpgRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        appPreferences = AppPreferences(applicationContext)
        serverConfigRepository = ServerConfigRepository(appPreferences)
        channelRepository = ChannelRepository(this, appPreferences)
        epgRepository = EpgRepository(this, appPreferences)
    }

    // [PERF-OPT-2] TV-optimized Coil image loader with bounded memory & disk cache
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Cap memory cache at 15% RAM to prevent GC pauses
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("ntv_logo_cache"))
                    .maxSizeBytes(40L * 1024 * 1024) // 40 MB disk cache for static logos
                    .build()
            }
            .crossfade(false) // Saves GPU rendering passes on Android TV panels
            .respectCacheHeaders(false) // Aggressively reuse downloaded channel logos
            // [NUVIO-OPT-1] 50% RAM reduction: RGB_565 uses 2 bytes/px vs 4 bytes/px (ARGB_8888); hardware bitmaps in GPU
            .allowRgb565(true)
            .allowHardware(true)
            .build()
    }

    companion object {
        lateinit var instance: JioTvApp
            private set
    }
}
