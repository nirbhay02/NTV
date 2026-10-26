package com.jiotv.androidtv

import android.app.Application
import com.jiotv.androidtv.data.local.AppPreferences
import com.jiotv.androidtv.data.repository.ChannelRepository
import com.jiotv.androidtv.data.repository.EpgRepository
import com.jiotv.androidtv.data.repository.ServerConfigRepository

class JioTvApp : Application() {

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

    companion object {
        lateinit var instance: JioTvApp
            private set
    }
}
