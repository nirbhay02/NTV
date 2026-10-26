package com.jiotv.androidtv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jiotv.androidtv.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jiotv_tv_settings")

/**
 * Handles persistent storage for TV app settings, server endpoints, and favorites.
 */
class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_SERVER_HOST = stringPreferencesKey("server_host")
        private val KEY_SERVER_PORT = intPreferencesKey("server_port")
        private val KEY_STREAM_QUALITY = stringPreferencesKey("stream_quality")
        private val KEY_FAVORITES = stringSetPreferencesKey("favorite_channel_ids")
        private val KEY_LAST_CHANNEL_ID = stringPreferencesKey("last_played_channel_id")
        private val KEY_LOGGED_IN_MOBILE = stringPreferencesKey("logged_in_mobile")
        private val KEY_ENABLED_LANGUAGES = stringSetPreferencesKey("enabled_languages")
        private val KEY_LAST_EPG_SYNC = longPreferencesKey("last_epg_sync_timestamp")
        private val KEY_EPG_URL = stringPreferencesKey("custom_epg_url")
        private val KEY_UPDATE_URL = stringPreferencesKey("custom_update_url")
        private val KEY_RECENT_CHANNELS = stringPreferencesKey("recent_channel_ids_csv")
    }

    val updateUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_UPDATE_URL]
    }

    suspend fun setUpdateUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(KEY_UPDATE_URL)
            } else {
                prefs[KEY_UPDATE_URL] = url.trim()
            }
        }
    }

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        val host = prefs[KEY_SERVER_HOST] ?: "127.0.0.1"
        val port = prefs[KEY_SERVER_PORT] ?: 5001
        val quality = prefs[KEY_STREAM_QUALITY] ?: "high"
        ServerConfig(
            host = host,
            port = port,
            quality = quality
        )
    }

    val lastEpgSyncFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_EPG_SYNC] ?: 0L
    }

    val epgUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_EPG_URL]
    }

    suspend fun saveLastEpgSync(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_EPG_SYNC] = timestamp
        }
    }

    suspend fun setEpgUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(KEY_EPG_URL)
            } else {
                prefs[KEY_EPG_URL] = url.trim()
            }
        }
    }

    val loggedInMobileFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOGGED_IN_MOBILE]
    }

    val favoritesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_FAVORITES] ?: emptySet()
    }

    val enabledLanguagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLED_LANGUAGES] ?: emptySet()
    }

    val lastPlayedChannelIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_CHANNEL_ID]
    }

    val recentChannelIdsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_RECENT_CHANNELS] ?: ""
        if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun updateServerConfig(host: String, port: Int, quality: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_HOST] = host.trim()
            prefs[KEY_SERVER_PORT] = port
            prefs[KEY_STREAM_QUALITY] = quality.trim()
        }
    }

    suspend fun toggleFavorite(channelId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(channelId)) {
                current.remove(channelId)
            } else {
                current.add(channelId)
            }
            prefs[KEY_FAVORITES] = current
        }
    }

    suspend fun saveLastPlayedChannel(channelId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_CHANNEL_ID] = channelId
        }
    }

    suspend fun recordRecentChannel(channelId: String) {
        if (channelId.isBlank()) return
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_RECENT_CHANNELS] ?: ""
            val list = if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val updated = (listOf(channelId) + list.filter { it != channelId }).take(12)
            prefs[KEY_RECENT_CHANNELS] = updated.joinToString(",")
            prefs[KEY_LAST_CHANNEL_ID] = channelId
        }
    }

    suspend fun saveLoginStatus(mobileNumber: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOGGED_IN_MOBILE] = mobileNumber
        }
    }

    suspend fun clearLoginStatus() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_LOGGED_IN_MOBILE)
        }
    }

    suspend fun setEnabledLanguages(languages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLED_LANGUAGES] = languages
        }
    }

    suspend fun toggleLanguage(language: String, allKnownLanguages: Set<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_ENABLED_LANGUAGES]?.toMutableSet()
            if (current == null || current.isEmpty()) {
                // All languages were enabled; disable this one
                val newSet = (allKnownLanguages - language).toMutableSet()
                prefs[KEY_ENABLED_LANGUAGES] = newSet
            } else {
                if (current.contains(language)) {
                    // Prevent removing the very last language
                    if (current.size > 1) {
                        current.remove(language)
                    }
                } else {
                    current.add(language)
                }
                // If user selected all known languages, clear to emptySet (default = all)
                if (current.size >= allKnownLanguages.size && current.containsAll(allKnownLanguages)) {
                    prefs.remove(KEY_ENABLED_LANGUAGES)
                } else {
                    prefs[KEY_ENABLED_LANGUAGES] = current
                }
            }
        }
    }

    suspend fun clearEnabledLanguages() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ENABLED_LANGUAGES)
        }
    }
}
