package com.jiotv.androidtv.data.repository

import com.jiotv.androidtv.data.local.AppPreferences
import com.jiotv.androidtv.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow

class ServerConfigRepository(private val appPreferences: AppPreferences) {

    val serverConfig: Flow<ServerConfig> = appPreferences.serverConfigFlow

    suspend fun updateConfig(host: String, port: Int, quality: String) {
        appPreferences.updateServerConfig(host, port, quality)
    }
}
