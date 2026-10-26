package com.jiotv.androidtv.server

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages the embedded native jiotv_go Go binary process running directly on the TV device.
 * Completely eliminates any dependency on an external computer, Docker container, or Termux.
 */
class EmbeddedServerManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverProcess: Process? = null
    private var healthCheckJob: Job? = null
    private var logCollectorJob: Job? = null

    private val _serverState = MutableStateFlow<EmbeddedServerState>(EmbeddedServerState.Stopped)
    val serverState: StateFlow<EmbeddedServerState> = _serverState.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs: StateFlow<List<String>> = _consoleLogs.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "EmbeddedServerManager"
        const val DEFAULT_PORT = 5001
        const val DEFAULT_HOST = "127.0.0.1"

        @Volatile
        private var instance: EmbeddedServerManager? = null

        fun getInstance(context: Context): EmbeddedServerManager {
            return instance ?: synchronized(this) {
                instance ?: EmbeddedServerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getTargetArchitecture(): String {
        val is64Bit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.Process.is64Bit()
        } else {
            File("/system/bin/linker64").exists()
        }

        val supportedAbis = Build.SUPPORTED_ABIS
        for (abi in supportedAbis) {
            when {
                abi.contains("arm64", ignoreCase = true) -> {
                    // Fire TV Sticks often have a 64-bit kernel but 32-bit userland without linker64.
                    // Only select arm64 if 64-bit userland is actually available!
                    if (is64Bit && File("/system/bin/linker64").exists()) {
                        return "arm64"
                    }
                }
                abi.contains("armeabi", ignoreCase = true) -> return "arm"
                abi.contains("x86_64", ignoreCase = true) -> {
                    if (is64Bit && File("/system/bin/linker64").exists()) {
                        return "amd64"
                    }
                }
                abi.contains("x86", ignoreCase = true) -> return "386"
            }
        }
        return "arm"
    }

    private fun isElfMatchingTargetArch(file: File, targetArch: String): Boolean {
        if (!file.exists() || file.length() < 100) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(6)
                val read = stream.read(header)
                if (read >= 6 && header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) {
                    val elfClass = header[4].toInt() // 1 = 32-bit, 2 = 64-bit
                    when (targetArch) {
                        "arm", "386" -> elfClass == 1
                        "arm64", "amd64" -> elfClass == 2
                        else -> true
                    }
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun startServer(port: Int = DEFAULT_PORT) {
        if (_serverState.value is EmbeddedServerState.Running && (_serverState.value as EmbeddedServerState.Running).isReady) {
            Log.d(TAG, "Server already running and ready")
            return
        }

        scope.launch {
            try {
                _serverState.value = EmbeddedServerState.PreparingBinary(0, "Locating NTV streaming engine...")
                val arch = getTargetArchitecture()
                appendLog("System ABI detected: ${Build.SUPPORTED_ABIS.joinToString(", ")} -> Target: $arch (is64Bit=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.os.Process.is64Bit() else "legacy"})")

                val binaryFile = prepareBinary()
                if (binaryFile == null || !binaryFile.exists()) {
                    _serverState.value = EmbeddedServerState.Error("Failed to obtain NTV streaming engine.")
                    appendLog("ERROR: Executable binary could not be prepared.")
                    return@launch
                }

                _serverState.value = EmbeddedServerState.Starting
                appendLog("Launching jiotv_go binary: ${binaryFile.absolutePath} on $DEFAULT_HOST:$port...")

                // Terminate any previous orphan jiotv_go processes to avoid 'bind: address already in use'
                killExistingBinaries()

                // Setup jiotv_go working directories matching official jiotv_go_app
                val jtvDir = File(context.filesDir, "jiotv_go").apply { mkdirs() }
                File(context.filesDir, ".jiotv_go").apply { mkdirs() }

                val configFile = File(jtvDir, "jtv_config.json")
                val configJson = JSONObject().apply {
                    put("epg", false)
                    put("debug", true)
                    put("disable_ts_handler", false)
                    put("disable_logout", false)
                    put("drm", true)
                    put("title", "NTV")
                    put("disable_url_encryption", false)
                    put("path_prefix", context.filesDir.absolutePath)
                    put("proxy", "")
                }
                configFile.writeText(configJson.toString(2))
                // Also write to context.filesDir directly
                File(context.filesDir, "jtv_config.json").writeText(configJson.toString(2))

                // Environment variables
                val envMap = HashMap(System.getenv()).apply {
                    put("HOME", context.filesDir.absolutePath)
                    put("TMPDIR", context.cacheDir.absolutePath)
                    put("JIOTV_GO_PORT", port.toString())
                    put("JIOTV_GO_HOST", "0.0.0.0")
                    put("GODEBUG", "netdns=cgo")
                    // [PERF-OPT-1] Constrain embedded Go runtime memory to safeguard budget TV RAM
                    put("GOMEMLIMIT", "48MiB")
                    put("GOGC", "50")
                    val existingPath = get("PATH") ?: "/system/bin:/system/xbin"
                    put("PATH", "$existingPath:${context.filesDir.absolutePath}/bin")
                }

                val command = listOf(
                    binaryFile.absolutePath,
                    "--config", configFile.absolutePath,
                    "run",
                    "--host", "0.0.0.0",
                    "--port", port.toString(),
                    "--public"
                )
                appendLog("Executing: ${command.joinToString(" ")}")

                // Prioritize direct ProcessBuilder so serverProcess is directly bound to jiotv_go (not an intermediate sh shell)
                var process = try {
                    ProcessBuilder(command).apply {
                        directory(context.filesDir)
                        environment().putAll(envMap)
                        redirectErrorStream(true)
                    }.start()
                } catch (pbEx: Exception) {
                    appendLog("Direct ProcessBuilder failed (${pbEx.message}), attempting shell fallback...")
                    null
                }

                if (process == null || (!process.isAlive && process.exitValue() != 0)) {
                    val envp = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
                    val execCommand = "${binaryFile.absolutePath} --config \"${configFile.absolutePath}\" run --host 0.0.0.0 --port $port --public"
                    process = Runtime.getRuntime().exec(arrayOf("sh", "-c", execCommand), envp, context.filesDir)
                }

                serverProcess = process
                startLogCollection(process)

                val pid = try {
                    val field = process.javaClass.getDeclaredField("pid")
                    field.isAccessible = true
                    field.get(process)?.toString()?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    0L
                }

                _serverState.value = EmbeddedServerState.Running(
                    host = DEFAULT_HOST,
                    port = port,
                    pid = pid,
                    isReady = false
                )
                appendLog("Process spawned successfully (PID: $pid). Checking HTTP socket...")

                waitForServerReady(DEFAULT_HOST, port, pid)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch embedded native server", e)
                appendLog("ERROR: Native server launch failed: ${e.localizedMessage}")
                _serverState.value = EmbeddedServerState.Error("Server launch failed: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun prepareBinary(): File? = withContext(Dispatchers.IO) {
        val arch = getTargetArchitecture()

        // Priority 1: Check nativeLibraryDir (extracted from jniLibs during APK installation)
        val nativeLibBinary = File(context.applicationInfo.nativeLibraryDir, "libjiotv_go.so")
        if (nativeLibBinary.exists() && nativeLibBinary.length() > 500_000 && isElfMatchingTargetArch(nativeLibBinary, arch)) {
            makeExecutable(nativeLibBinary)
            appendLog("Found compatible native executable in nativeLibraryDir (${nativeLibBinary.length() / 1024} KB).")
            return@withContext nativeLibBinary
        }

        // Priority 2: Check filesDir/bin/jiotv_go
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val targetBinary = File(binDir, "jiotv_go")

        if (targetBinary.exists() && targetBinary.length() > 500_000 && isElfMatchingTargetArch(targetBinary, arch)) {
            makeExecutable(targetBinary)
            appendLog("Found existing compatible jiotv_go executable in filesDir (${targetBinary.length() / 1024} KB).")
            return@withContext targetBinary
        }

        // Priority 3: Extract from bundled assets
        val assetPath = "bin/$arch/jiotv_go"

        try {
            val assetList = context.assets.list("bin/$arch").orEmpty()
            if ("jiotv_go" in assetList) {
                appendLog("Extracting bundled binary from assets ($assetPath)...")
                _serverState.value = EmbeddedServerState.PreparingBinary(20, "Extracting bundled native binary...")

                context.assets.open(assetPath).use { input ->
                    FileOutputStream(targetBinary).use { output ->
                        input.copyTo(output)
                    }
                }
                makeExecutable(targetBinary)
                appendLog("Successfully extracted binary (${targetBinary.length() / 1024} KB).")
                return@withContext targetBinary
            }
        } catch (e: Exception) {
            Log.w(TAG, "Asset extraction error for $assetPath: ${e.message}")
        }

        // Priority 4: Fallback download if necessary
        val downloadUrl = "https://github.com/jiotv-go/jiotv_go/releases/latest/download/jiotv_go-android-$arch"
        appendLog("Downloading jiotv_go native binary for android-$arch...")
        _serverState.value = EmbeddedServerState.PreparingBinary(10, "Downloading NTV engine ($arch)...")

        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                appendLog("Download failed: HTTP ${response.code}")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetBinary).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (contentLength > 0) {
                            val percent = (downloadedBytes * 100 / contentLength).toInt()
                            _serverState.value = EmbeddedServerState.PreparingBinary(
                                percent,
                                "Downloading NTV engine ($percent%)..."
                            )
                        }
                    }
                }
            }

            makeExecutable(targetBinary)
            appendLog("Download complete (${targetBinary.length() / 1024} KB).")
            targetBinary
        } catch (e: Exception) {
            appendLog("Download error: ${e.message}")
            null
        }
    }

    private fun makeExecutable(file: File) {
        try {
            file.setExecutable(true, false)
            file.setReadable(true, false)
            Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
        } catch (_: Exception) {}
    }

    private fun startLogCollection(process: Process) {
        logCollectorJob?.cancel()
        logCollectorJob = scope.launch {
            // Read stdout
            launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String? = null
                    while (isActive && reader.readLine().also { line = it } != null) {
                        line?.let { appendLog(it) }
                    }
                } catch (_: Exception) {}
            }
            // Read stderr
            launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.errorStream))
                    var line: String? = null
                    while (isActive && reader.readLine().also { line = it } != null) {
                        line?.let { appendLog("[ERR] $it") }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun waitForServerReady(host: String, port: Int, pid: Long) {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            val startTime = System.currentTimeMillis()
            val timeout = 30_000L // 30 seconds

            while (isActive && System.currentTimeMillis() - startTime < timeout) {
                delay(600)

                // Check if process terminated unexpectedly
                val proc = serverProcess
                if (proc != null && !proc.isAlive) {
                    val exit = proc.exitValue()
                    appendLog("Server process exited with code $exit before binding socket. Check console logs above.")
                    _serverState.value = EmbeddedServerState.Error("Process exited with code $exit")
                    return@launch
                }

                for (testHost in listOf(host, "127.0.0.1", "localhost")) {
                    try {
                        val request = Request.Builder()
                            .url("http://$testHost:$port/")
                            .get()
                            .build()
                        val response = okHttpClient.newCall(request).execute()
                        val code = response.code
                        response.close()

                        // Any HTTP code (even 302, 401, 404) means the server is bound and serving HTTP requests
                        if (code in 100..499) {
                            appendLog("SUCCESS: jiotv_go server verified ready at http://$testHost:$port (HTTP $code)")
                            _serverState.value = EmbeddedServerState.Running(
                                host = testHost,
                                port = port,
                                pid = pid,
                                isReady = true
                            )
                            return@launch
                        }
                    } catch (_: Exception) {}
                }
            }

            appendLog("Server socket check timeout (process PID: $pid). Check console logs for details.")
            _serverState.value = EmbeddedServerState.Running(
                host = host,
                port = port,
                pid = pid,
                isReady = false
            )
        }
    }

    private fun killExistingBinaries() {
        try {
            Runtime.getRuntime().exec(arrayOf("pkill", "-9", "-f", "jiotv_go")).waitFor()
        } catch (_: Exception) {}
    }

    fun stopServer() {
        healthCheckJob?.cancel()
        logCollectorJob?.cancel()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serverProcess?.destroyForcibly()
            } else {
                serverProcess?.destroy()
            }
            serverProcess = null
        } catch (_: Exception) {}

        killExistingBinaries()

        _serverState.value = EmbeddedServerState.Stopped
        appendLog("Server stopped.")
    }

    fun restartServer(port: Int = DEFAULT_PORT) {
        stopServer()
        scope.launch {
            delay(800)
            startServer(port)
        }
    }

    private fun appendLog(line: String) {
        Log.i(TAG, line)
        val current = _consoleLogs.value.toMutableList()
        current.add("[${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}] $line")
        if (current.size > 200) {
            current.removeAt(0)
        }
        _consoleLogs.value = current
    }
}
