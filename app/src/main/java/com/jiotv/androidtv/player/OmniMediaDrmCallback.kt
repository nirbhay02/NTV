package com.jiotv.androidtv.player

import android.util.Base64
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm.KeyRequest
import androidx.media3.exoplayer.drm.ExoMediaDrm.ProvisionRequest
import androidx.media3.exoplayer.drm.MediaDrmCallback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Custom MediaDrmCallback ported from official JioTV-Go (jiotv_go_app).
 * Handles:
 * 1. Widevine provisioning and license requests via the configured OkHttpClient.
 * 2. Automatic base64 data: URL decoding for ClearKey/local keys.
 * 3. Intelligent Content-Type detection (JSON vs binary octet-stream).
 * 4. Automatic retry on transient CDN errors (500, 502, 503, 504, 403, 429).
 */
@UnstableApi
class OmniMediaDrmCallback(
    private val defaultLicenseUrl: String,
    private val headers: Map<String, String>,
    private val httpClient: OkHttpClient
) : MediaDrmCallback {

    companion object {
        private const val TAG = "OmniMediaDrmCallback"
    }

    override fun executeProvisionRequest(uuid: UUID, request: ProvisionRequest): ByteArray {
        val rawUrl = request.defaultUrl
        val signedRequest = try {
            String(request.data, Charsets.UTF_8)
        } catch (_: Exception) {
            Base64.encodeToString(request.data, Base64.NO_WRAP)
        }
        val url = if (rawUrl.contains("?")) {
            "$rawUrl&signedRequest=$signedRequest"
        } else {
            "$rawUrl?signedRequest=$signedRequest"
        }
        val okRequest = Request.Builder()
            .url(url)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "DRM Provision Request: $url")
        return httpClient.newCall(okRequest).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "DRM Provision Error ${response.code}")
                throw Exception("Provisioning failed: ${response.code}")
            }
            response.body?.bytes() ?: throw Exception("Empty provisioning response")
        }
    }

    override fun executeKeyRequest(uuid: UUID, request: KeyRequest): ByteArray {
        var licenseUrl = request.licenseServerUrl
        if (licenseUrl.isNullOrEmpty() || licenseUrl.contains("provisioning.widevine.com")) {
            licenseUrl = defaultLicenseUrl
        }

        if (licenseUrl.startsWith("jwk:", ignoreCase = true)) {
            val jwkJson = licenseUrl.substringAfter("jwk:").substringAfter("JWK:")
            return jwkJson.toByteArray(Charsets.UTF_8)
        }

        if (licenseUrl.startsWith("data:", ignoreCase = true)) {
            return try {
                val base64Data = licenseUrl.substringAfter("base64,", "")
                Base64.decode(base64Data, Base64.DEFAULT)
            } catch (e: Exception) {
                throw Exception("Failed to decode local data license URI", e)
            }
        }

        val requestData = request.data
        val contentType = if (requestData.isNotEmpty() && requestData[0].toInt().toChar() == '{') {
            "application/json"
        } else {
            "application/octet-stream"
        }

        val builder = Request.Builder()
            .url(licenseUrl)
            .post(requestData.toRequestBody(contentType.toMediaType()))

        headers.forEach { (k, v) ->
            builder.header(k, v)
        }

        if (!headers.containsKey("Content-Type")) {
            builder.header("Content-Type", contentType)
        }

        val okRequest = builder.build()
        Log.d(TAG, "DRM Key Request: POST $licenseUrl (Type: $contentType)")

        var retryCount = 0
        val maxRetries = 4
        while (retryCount <= maxRetries) {
            try {
                val currentCall = httpClient.newCall(okRequest)
                currentCall.execute().use { response ->
                    val bodyBytes = response.body?.bytes() ?: throw Exception("Empty license response")

                    if (response.isSuccessful) {
                        return bodyBytes
                    }

                    // Retry on common server errors or 403 (picky proxies)
                    if (response.code in listOf(500, 502, 503, 504, 403, 429)) {
                        if (retryCount < maxRetries) {
                            val errBody = String(bodyBytes.take(256).toByteArray()).filter { it.code in 32..126 }
                            Log.w(TAG, "DRM Server Error ${response.code} (Attempt ${retryCount + 1}/$maxRetries) for $licenseUrl: $errBody")
                            retryCount++
                            Thread.sleep(1000L + (retryCount * 800L))
                            return@use
                        }
                    }

                    if (!response.isSuccessful) {
                        val errBody = String(bodyBytes.take(256).toByteArray()).filter { it.code in 32..126 }
                        Log.e(TAG, "DRM Final Error ${response.code}: $errBody")
                        throw Exception("License server error: ${response.code}")
                    }

                    return bodyBytes
                }
            } catch (e: Exception) {
                if (retryCount < maxRetries && (e is java.io.IOException || e is java.net.SocketTimeoutException)) {
                    Log.w(TAG, "DRM Network Error: ${e.message}, retrying (${retryCount + 1}/$maxRetries)...")
                    retryCount++
                    Thread.sleep(1200)
                } else {
                    Log.e(TAG, "DRM Exception: ${e.message}")
                    throw e
                }
            }
        }
        throw Exception("DRM Key Request failed after maximum retries")
    }
}
