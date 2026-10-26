package com.jiotv.androidtv.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jiotv.androidtv.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class JioTvApiService(
    private val proxyHandler: ProxyStreamHandler,
    private val serverConfig: ServerConfig
) {
    private val client: OkHttpClient = proxyHandler.okHttpClient
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchPlaylist(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(serverConfig.playlistUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to load playlist: HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isEmpty()) {
                    return@withContext Result.failure(IOException("Empty playlist received from server"))
                }
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkServerHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(serverConfig.statusUrl).head().build()
            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful || response.code == 404 || response.code == 302
                Result.success(ok)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOtp(mobileNumber: String): Result<String> = withContext(Dispatchers.IO) {
        val digits10 = mobileNumber.trim().removePrefix("+91").removePrefix("91").trim()
        val num = "+91$digits10"
        try {
            val payload = JsonObject().apply {
                addProperty("number", num)
                addProperty("mobile", num)
                addProperty("phone", num)
            }
            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("${serverConfig.baseUrl}/login/sendOTP").post(body).build()
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                Log.d("JioTvApiService", "sendOtp [num=$num] HTTP ${response.code}: $respBody")
                if (response.isSuccessful || response.code == 200) {
                    Result.success(respBody)
                } else {
                    val userMsg = parseErrorMessage(respBody)
                    Result.failure(IOException(userMsg))
                }
            }
        } catch (e: Exception) {
            Log.w("JioTvApiService", "sendOtp failed for $num: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(mobileNumber: String, otp: String): Result<String> = withContext(Dispatchers.IO) {
        val digits10 = mobileNumber.trim().removePrefix("+91").removePrefix("91").trim()
        val cleanOtp = otp.trim()
        val num = "+91$digits10"
        try {
            val payload = JsonObject().apply {
                addProperty("number", num)
                addProperty("mobile", num)
                addProperty("otp", cleanOtp)
                addProperty("code", cleanOtp)
            }
            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url("${serverConfig.baseUrl}/login/verifyOTP").post(body).build()
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                Log.d("JioTvApiService", "verifyOtp [num=$num] HTTP ${response.code}: $respBody")
                if (response.isSuccessful || response.code == 200) {
                    Result.success(respBody)
                } else {
                    val userMsg = parseErrorMessage(respBody)
                    Result.failure(IOException(userMsg))
                }
            }
        } catch (e: Exception) {
            Log.w("JioTvApiService", "verifyOtp failed for $num: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${serverConfig.baseUrl}/logout").get().build()
            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(respBody: String): String {
        if ("1042" in respBody || respBody.contains("unverified OTP", ignoreCase = true) || respBody.contains("5 minutes", ignoreCase = true)) {
            return "Too many unverified OTP requests. Please wait 5 minutes before trying again."
        }
        if ("1002" in respBody || respBody.contains("Please login with a Jio phone number", ignoreCase = true)) {
            return "Please enter a valid Jio SIM mobile number."
        }
        if (respBody.contains("Internal server error", ignoreCase = true)) {
            return "Invalid or expired OTP. Please check the 6-digit OTP code received on your phone."
        }
        return try {
            val json = gson.fromJson(respBody, JsonObject::class.java)
            val msg = json?.get("message")?.asString ?: json?.get("error")?.asString
            if (msg != null && "body:" in msg) {
                val innerJsonStr = msg.substringAfter("body:").trim()
                val innerJson = gson.fromJson(innerJsonStr, JsonObject::class.java)
                innerJson?.get("message")?.asString?.takeIf { it.isNotEmpty() } ?: msg
            } else {
                msg ?: "Request failed. Please try again."
            }
        } catch (e: Exception) {
            "Request failed. Please try again."
        }
    }
}
