package com.jiotv.androidtv.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.jiotv.androidtv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    
    // Default GitHub repository releases API endpoint for nirbhay02/NTV
    const val DEFAULT_GITHUB_RELEASE_URL = "https://api.github.com/repos/nirbhay02/NTV/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val tagName: String,
        val title: String,
        val changelog: String,
        val downloadUrl: String,
        val apkSize: Long,
        val isUpdateAvailable: Boolean
    )

    fun getCurrentVersionName(context: Context): String {
        return try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.2.0"
            }.getOrDefault("1.2.0")
        }
    }

    fun getCurrentVersionCode(): Int {
        return try {
            BuildConfig.VERSION_CODE
        } catch (_: Exception) {
            3
        }
    }

    /**
     * Parses semantic version string (e.g., "1.5.3" or "v1.5.3") into a comparable integer list.
     */
    private fun parseVersion(v: String): List<Int> {
        val core = v.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        return core.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    }

    /** "2.0-beta.3" -> 3, "2.0-beta" -> 1, "2.0" (stable) -> null. */
    private fun preReleaseNumber(v: String): Int? {
        val pre = v.trim().substringAfter('-', "")
        if (pre.isEmpty()) return null
        return pre.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 1
    }

    fun isPreRelease(version: String) = preReleaseNumber(version) != null

    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersion(remoteVersion)
        val currentParts = parseVersion(currentVersion)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        val rp = preReleaseNumber(remoteVersion)
        val cp = preReleaseNumber(currentVersion)
        return when {
            cp == null -> false
            rp == null -> true
            else -> rp > cp
        }
    }

    /**
     * Checks remote endpoint for updates. Supports:
     * 1. GitHub Releases API (https://api.github.com/...)
     * 2. Custom JSON schema (raw URL with versionCode, versionName, changelog, assets)
     */
    suspend fun checkForUpdate(
        context: Context,
        customUrl: String? = null
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = if (!customUrl.isNullOrBlank()) customUrl.trim() else DEFAULT_GITHUB_RELEASE_URL
            val url = URL(endpoint)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "NTV-AndroidTV/${getCurrentVersionName(context)}")
            connection.setRequestProperty("Accept", "application/json, application/vnd.github.v3+json")

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(responseText)

                // Detect if GitHub release format or custom update.json
                val isGitHub = rootJson.has("tag_name")

                val remoteVersionName: String
                val remoteVersionCode: Int
                val tagName: String
                val title: String
                val changelog: String
                var apkDownloadUrl = ""
                var apkSize = 0L

                if (isGitHub) {
                    tagName = rootJson.optString("tag_name", "")
                    remoteVersionName = tagName.removePrefix("v").removePrefix("V")
                    remoteVersionCode = 0 // GitHub releases don't explicitly carry versionCode
                    title = rootJson.optString("name", "NTV Update v$remoteVersionName")
                    changelog = rootJson.optString("body", "Bug fixes and improvements")

                    // Smart ABI asset selection
                    val assets = rootJson.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        val chosenAsset = selectBestApkAsset(assets)
                        if (chosenAsset != null) {
                            apkDownloadUrl = chosenAsset.optString("browser_download_url", "")
                            apkSize = chosenAsset.optLong("size", 0L)
                        }
                    }
                } else {
                    // Custom update.json format
                    remoteVersionName = rootJson.optString("versionName", "1.0.0")
                    remoteVersionCode = rootJson.optInt("versionCode", 0)
                    tagName = "v$remoteVersionName"
                    title = rootJson.optString("title", "NTV Update v$remoteVersionName")
                    changelog = rootJson.optString("changelog", "Bug fixes and improvements")

                    apkDownloadUrl = selectBestCustomApkUrl(rootJson)
                    apkSize = rootJson.optLong("size", 0L)
                }

                if (apkDownloadUrl.isEmpty()) {
                    Log.w(TAG, "No suitable APK asset found in release response")
                    return@withContext Result.success(null)
                }

                val currentVersionName = getCurrentVersionName(context)
                val currentVersionCode = getCurrentVersionCode()

                val isAvailable = if (remoteVersionCode > 0) {
                    remoteVersionCode > currentVersionCode
                } else {
                    isNewerVersion(remoteVersionName, currentVersionName)
                }

                val updateInfo = UpdateInfo(
                    versionName = remoteVersionName,
                    versionCode = remoteVersionCode,
                    tagName = tagName,
                    title = title,
                    changelog = changelog,
                    downloadUrl = apkDownloadUrl,
                    apkSize = apkSize,
                    isUpdateAvailable = isAvailable
                )
                Result.success(updateInfo)
            } else if (responseCode == 404) {
                Log.w(TAG, "No release found at $endpoint (HTTP 404)")
                Result.failure(Exception("No release found (HTTP 404). Ensure the GitHub repository is set to Public and a Release is published under Releases."))
            } else {
                Log.e(TAG, "Update check failed with HTTP $responseCode")
                Result.failure(Exception("HTTP $responseCode from update server"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Inspects GitHub assets and selects the best matching APK for the target device architecture:
     * 1. On Fire TV / 32-bit: Prefer armv7 / firetv APK (~11.9 MB)
     * 2. On 64-bit TV / Tablet: Prefer arm64 APK (~11.6 MB)
     * 3. Fallback: Universal APK or any .apk found
     */
    private fun selectBestApkAsset(assets: JSONArray): JSONObject? {
        val apkAssets = mutableListOf<JSONObject>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkAssets.add(asset)
            }
        }
        if (apkAssets.isEmpty()) return null

        val abis = Build.SUPPORTED_ABIS
        val is64Bit = abis.any { it.startsWith("arm64") }
        val is32BitArm = abis.any { it.startsWith("armeabi") }

        if (is64Bit) {
            val arm64 = apkAssets.firstOrNull { it.optString("name").contains("arm64", ignoreCase = true) }
            if (arm64 != null) return arm64
        }
        if (is32BitArm) {
            val armv7 = apkAssets.firstOrNull {
                val n = it.optString("name").lowercase()
                n.contains("armv7") || n.contains("firetv") || n.contains("armeabi")
            }
            if (armv7 != null) return armv7
        }

        // Return universal or first available
        return apkAssets.firstOrNull { it.optString("name").contains("universal", ignoreCase = true) }
            ?: apkAssets.first()
    }

    private fun selectBestCustomApkUrl(rootJson: JSONObject): String {
        val assets = rootJson.optJSONObject("assets")
        val abis = Build.SUPPORTED_ABIS
        if (assets != null) {
            val is64Bit = abis.any { it.startsWith("arm64") }
            if (is64Bit && assets.has("arm64")) {
                return assets.optString("arm64", "")
            }
            val is32Bit = abis.any { it.startsWith("armeabi") }
            if (is32Bit && (assets.has("armv7") || assets.has("firetv"))) {
                return assets.optString("firetv", assets.optString("armv7", ""))
            }
            if (assets.has("universal")) {
                return assets.optString("universal", "")
            }
        }
        return rootJson.optString("apkUrl", "")
    }

    /**
     * Downloads APK with automatic redirect handling (e.g. GitHub to AWS S3)
     * and streams to private cache directory.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progress: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetDir = context.getExternalFilesDir(null) ?: context.cacheDir
        val targetFile = File(targetDir, "NTV_update.apk")
        if (targetFile.exists()) targetFile.delete()

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            var redirectCount = 0
            var currentUrl = downloadUrl
            var conn: HttpURLConnection

            while (true) {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "NTV-AndroidTV/${getCurrentVersionName(context)}")

                val status = conn.responseCode
                if (status in 300..399) {
                    val newUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (newUrl == null || redirectCount++ > 8) {
                        return@withContext Result.failure(Exception("Too many redirects downloading APK"))
                    }
                    currentUrl = newUrl
                } else if (status in 200..299) {
                    connection = conn
                    break
                } else {
                    return@withContext Result.failure(Exception("Failed to download APK: HTTP $status"))
                }
            }

            val totalBytes = connection.contentLength.toLong()
            inputStream = connection.inputStream
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(32 * 1024)
            var bytesDownloaded = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                bytesDownloaded += read
                val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
                onProgress(bytesDownloaded, totalBytes, progress)
            }
            outputStream.flush()
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            if (targetFile.exists()) targetFile.delete()
            Result.failure(e)
        } finally {
            runCatching { outputStream?.close() }
            runCatching { inputStream?.close() }
            connection?.disconnect()
        }
    }

    /**
     * Prompts the system package installer to update the app.
     * Uses FileProvider for secure URI granting.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) return Result.failure(Exception("APK file does not exist"))

            // On Android 8.0+ verify unknown app install permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return Result.failure(Exception("Please grant permission to install unknown apps, then retry"))
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            Result.failure(e)
        }
    }
}
