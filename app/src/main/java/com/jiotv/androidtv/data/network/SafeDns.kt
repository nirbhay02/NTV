package com.jiotv.androidtv.data.network

import android.annotation.SuppressLint
import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Robust DNS resolver with DNS-over-HTTPS fallback (Cloudflare & Google DoH)
 * preventing ISP DNS hijacking (e.g. Jio/Airtel 49.44.*.* or 49.45.*.* blockpage IPs).
 * Ported from official JioTV-Go (jiotv_go_app).
 */
object SafeDns : Dns {
    private const val TAG = "SafeDns"

    private val bootstrapClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)

        try {
            val trustAllCerts = arrayOf<TrustManager>(
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure trust-all SSL for bootstrapClient", e)
        }
        builder.build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // Direct local or internal resolution
        if (hostname == "localhost" || hostname == "127.0.0.1" || hostname.endsWith(".local") ||
            hostname.contains("jio.com") || hostname.contains("jio.dev") || hostname.contains("jiotv")
        ) {
            val addrs = Dns.SYSTEM.lookup(hostname)
            // [NUVIO-OPT-4] IPv4-first ordering: prevents 2-3s IPv6 timeout latency on Indian ISPs
            return addrs.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
        }

        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val isHijacked = addresses.any { addr ->
                val ip = addr.hostAddress ?: ""
                ip.startsWith("49.44.") || ip.startsWith("49.45.")
            }
            if (!isHijacked) {
                // [NUVIO-OPT-4] IPv4-first ordering
                return addresses.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
            }
            Log.d(TAG, "Detected DNS hijacking (ISP blockpage IP) for $hostname. Falling back to DNS-over-HTTPS.")
        } catch (e: Exception) {
            Log.d(TAG, "System DNS lookup failed for $hostname. Falling back to DNS-over-HTTPS.")
        }

        val ips = mutableListOf<String>()

        // 1. Cloudflare DNS-over-HTTPS
        try {
            val request = Request.Builder()
                .url("https://1.1.1.1/dns-query?name=$hostname&type=A")
                .header("Accept", "application/dns-json")
                .build()
            bootstrapClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string().orEmpty()
                    val regex = Regex("""\"data\"\s*:\s*\"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\"""")
                    regex.findAll(json).forEach { match ->
                        val ip = match.groupValues[1]
                        if (!ip.startsWith("49.44.") && !ip.startsWith("49.45.")) {
                            ips.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloudflare DoH failed for $hostname: ${e.message}")
        }

        // 2. Google DNS-over-HTTPS fallback
        if (ips.isEmpty()) {
            try {
                val request = Request.Builder()
                    .url("https://8.8.8.8/resolve?name=$hostname&type=A")
                    .build()
                bootstrapClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string().orEmpty()
                        val regex = Regex("""\"data\"\s*:\s*\"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\"""")
                        regex.findAll(json).forEach { match ->
                            val ip = match.groupValues[1]
                            if (!ip.startsWith("49.44.") && !ip.startsWith("49.45.")) {
                                ips.add(ip)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google DoH failed for $hostname: ${e.message}")
            }
        }

        if (ips.isNotEmpty()) {
            return ips.map { InetAddress.getByName(it) }
        }

        // Ultimate fallback
        return Dns.SYSTEM.lookup(hostname).sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
    }
}
