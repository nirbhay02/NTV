package com.jiotv.androidtv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.jiotv.androidtv.server.EmbeddedServerManager
import com.jiotv.androidtv.ui.navigation.TvNavigation
import com.jiotv.androidtv.ui.theme.NtvTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jiotv.androidtv.ui.components.UpdateDialog
import com.jiotv.androidtv.ui.viewmodel.MainViewModel
import com.jiotv.androidtv.ui.viewmodel.PlayerViewModel
import com.jiotv.androidtv.ui.viewmodel.SettingsViewModel

@UnstableApi
class MainActivity : ComponentActivity() {

    private var playerViewModel: PlayerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch embedded jiotv_go streaming proxy server directly in background
        EmbeddedServerManager.getInstance(this).startServer()

        val app = JioTvApp.instance
        val channelRepo = app.channelRepository
        val serverRepo = app.serverConfigRepository
        val epgRepo = app.epgRepository

        val mainViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(applicationContext, channelRepo, serverRepo, epgRepo) as T
                }
            }
        )[MainViewModel::class.java]

        val playerViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(applicationContext, channelRepo, serverRepo, epgRepo) as T
                }
            }
        )[PlayerViewModel::class.java]
        this.playerViewModel = playerViewModel

        val settingsViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(applicationContext, serverRepo, channelRepo) as T
                }
            }
        )[SettingsViewModel::class.java]

        // Silent background check for updates on startup
        settingsViewModel.checkForUpdates(manual = false)

        setContent {
            NtvTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()
                    TvNavigation(
                        navController = navController,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        settingsViewModel = settingsViewModel
                    )

                    // OTA Update Dialog overlay on startup when a new build is detected
                    val updateInfo by settingsViewModel.updateInfo.collectAsState()
                    val isDownloading by settingsViewModel.isDownloadingUpdate.collectAsState()
                    val downloadProgress by settingsViewModel.updateProgress.collectAsState()
                    val downloadedBytes by settingsViewModel.downloadedBytes.collectAsState()
                    val totalBytes by settingsViewModel.totalBytes.collectAsState()
                    val updateError by settingsViewModel.updateError.collectAsState()
                    var showUpdateDialog by remember { mutableStateOf(true) }

                    updateInfo?.let { info ->
                        if (info.isUpdateAvailable && showUpdateDialog) {
                            UpdateDialog(
                                updateInfo = info,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                errorMessage = updateError,
                                onDownloadAndInstall = {
                                    settingsViewModel.downloadAndInstallUpdate(this@MainActivity)
                                },
                                onDismiss = {
                                    showUpdateDialog = false
                                    settingsViewModel.dismissUpdateDialog()
                                }
                            )
                        }
                    }
                }
            }
        }

        makeFullScreen()
    }

    private fun makeFullScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val pvm = playerViewModel
        if (pvm != null && pvm.playbackState.value.currentChannel != null && !pvm.isQuickGuideVisible.value && !pvm.isTrackSelectorVisible.value) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                        pvm.switchPreviousChannel()
                        pvm.showOsd()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        pvm.switchNextChannel()
                        pvm.showOsd()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        pvm.toggleTrackSelector()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        pvm.togglePlayPause()
                        return true
                    }
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_GUIDE -> {
                        pvm.toggleQuickGuide()
                        return true
                    }
                    KeyEvent.KEYCODE_INFO -> {
                        pvm.toggleOsd()
                        return true
                    }
                    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                        val digit = event.keyCode - KeyEvent.KEYCODE_0
                        pvm.onNumberKeyPressed(digit)
                        return true
                    }
                    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                        val digit = event.keyCode - KeyEvent.KEYCODE_NUMPAD_0
                        pvm.onNumberKeyPressed(digit)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
