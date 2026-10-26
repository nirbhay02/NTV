package com.jiotv.androidtv.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiotv.androidtv.data.model.ServerConfig
import com.jiotv.androidtv.data.network.JioTvApiService
import com.jiotv.androidtv.data.network.ProxyStreamHandler
import com.jiotv.androidtv.data.repository.ChannelRepository
import com.jiotv.androidtv.data.repository.ServerConfigRepository
import com.jiotv.androidtv.server.EmbeddedServerManager
import com.jiotv.androidtv.server.EmbeddedServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.jiotv.androidtv.data.update.AppUpdateManager
import java.io.File

class SettingsViewModel(
    private val context: Context,
    private val serverConfigRepository: ServerConfigRepository,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val serverManager = EmbeddedServerManager.getInstance(context)

    val serverConfig: StateFlow<ServerConfig> = serverConfigRepository.serverConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerConfig())

    val embeddedServerState: StateFlow<EmbeddedServerState> = serverManager.serverState
    val consoleLogs: StateFlow<List<String>> = serverManager.consoleLogs

    val loggedInMobile: StateFlow<String?> = channelRepository.loggedInMobile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Language filter state
    val enabledLanguages: StateFlow<Set<String>> = channelRepository.enabledLanguages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val availableLanguages: StateFlow<List<Pair<String, Int>>> = channelRepository.availableLanguages

    val totalChannelsCount: StateFlow<Int> = channelRepository.totalRawChannelsCount

    val visibleChannelsCount: StateFlow<Int> = channelRepository.channels
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val epgRepository = com.jiotv.androidtv.JioTvApp.instance.epgRepository

    val epgChannelCount: StateFlow<Int> = epgRepository.epgMap
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isEpgSyncing: StateFlow<Boolean> = epgRepository.isSyncing
    val lastEpgSync: StateFlow<Long> = epgRepository.lastSyncTimestamp

    fun refreshEpg() {
        viewModelScope.launch {
            epgRepository.refreshEpg(force = true)
        }
    }

    private val _testState = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testState: StateFlow<TestConnectionState> = _testState.asStateFlow()

    fun startEmbeddedServer() {
        serverManager.startServer()
    }

    fun stopEmbeddedServer() {
        serverManager.stopServer()
    }

    fun restartEmbeddedServer() {
        serverManager.restartServer()
    }

    fun updateServerConfig(host: String, port: Int, quality: String) {
        viewModelScope.launch {
            serverConfigRepository.updateConfig(host, port, quality)
            val updatedConfig = ServerConfig(host = host, port = port, quality = quality)
            channelRepository.refreshChannels(updatedConfig)
        }
    }

    fun testConnection(host: String, port: Int) {
        viewModelScope.launch {
            _testState.value = TestConnectionState.Testing
            val config = ServerConfig(host = host, port = port)
            val proxyHandler = ProxyStreamHandler(config)
            val apiService = JioTvApiService(proxyHandler, config)

            val healthResult = apiService.checkServerHealth()
            if (healthResult.isSuccess) {
                _testState.value = TestConnectionState.Success(
                    "Successfully connected to NTV server at http://$host:$port"
                )
            } else {
                val err = healthResult.exceptionOrNull()?.localizedMessage ?: "Connection timed out"
                _testState.value = TestConnectionState.Error("Connection failed: $err")
            }
        }
    }

    fun reloadChannels() {
        viewModelScope.launch {
            channelRepository.refreshChannels()
        }
    }

    fun onLoginSuccess(mobileNumber: String) {
        viewModelScope.launch {
            channelRepository.saveLoginStatus(mobileNumber)
            channelRepository.refreshChannels()
        }
    }

    fun logout() {
        viewModelScope.launch {
            channelRepository.logout()
        }
    }

    fun toggleLanguage(language: String) {
        viewModelScope.launch {
            channelRepository.toggleLanguage(language)
        }
    }

    fun setHindiEnglishOnly() {
        viewModelScope.launch {
            channelRepository.setHindiEnglishOnly()
        }
    }

    fun enableAllLanguages() {
        viewModelScope.launch {
            channelRepository.enableAllLanguages()
        }
    }

    // ── App Update Management ──
    private val _updateInfo = MutableStateFlow<AppUpdateManager.UpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateManager.UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _updateProgress = MutableStateFlow(0f)
    val updateProgress: StateFlow<Float> = _updateProgress.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    private val _downloadedApkFile = MutableStateFlow<File?>(null)

    val currentVersionName: String = AppUpdateManager.getCurrentVersionName(context)
    val currentVersionCode: Int = AppUpdateManager.getCurrentVersionCode()

    fun checkForUpdates(manual: Boolean = false) {
        if (_isCheckingUpdate.value || _isDownloadingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateError.value = null
            if (manual) _updateStatusMessage.value = "Checking for new versions..."
            val result = AppUpdateManager.checkForUpdate(context)
            result.onSuccess { info ->
                _updateInfo.value = info
                if (info != null && info.isUpdateAvailable) {
                    _updateStatusMessage.value = "New update available: v${info.versionName}"
                } else if (manual) {
                    _updateStatusMessage.value = "App is up to date ($currentVersionName)"
                }
            }.onFailure { err ->
                if (manual) {
                    _updateError.value = "Failed to check update: ${err.localizedMessage}"
                    _updateStatusMessage.value = "Update check failed"
                }
            }
            _isCheckingUpdate.value = false
        }
    }

    fun downloadAndInstallUpdate(ctx: Context) {
        val info = _updateInfo.value ?: return
        if (_isDownloadingUpdate.value) return

        val existing = _downloadedApkFile.value
        if (existing != null && existing.exists()) {
            AppUpdateManager.installApk(ctx, existing)
            return
        }

        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _updateError.value = null
            _updateProgress.value = 0f
            _downloadedBytes.value = 0L
            _totalBytes.value = info.apkSize
            _updateStatusMessage.value = "Downloading update..."

            val result = AppUpdateManager.downloadApk(
                context = ctx,
                downloadUrl = info.downloadUrl,
                onProgress = { downloaded, total, progress ->
                    _downloadedBytes.value = downloaded
                    _totalBytes.value = total
                    _updateProgress.value = progress
                }
            )

            result.onSuccess { file ->
                _downloadedApkFile.value = file
                _isDownloadingUpdate.value = false
                _updateStatusMessage.value = "Download complete, launching installer..."
                AppUpdateManager.installApk(ctx, file)
            }.onFailure { err ->
                _isDownloadingUpdate.value = false
                _updateError.value = "Download failed: ${err.localizedMessage}"
                _updateStatusMessage.value = "Download failed"
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }
}
