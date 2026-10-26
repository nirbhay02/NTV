package com.jiotv.androidtv.data.network

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.jiotv.androidtv.data.model.ServerConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Manages the HTTP proxy & stream transport pipeline between ExoPlayer, the local jiotv_go server,
 * and external Jio/Akamai CDNs.
 *
 * Implements architectural resilience patterns from jiotv_go_app:
 * 1. SafeDns: Anti-DNS hijacking fallback to Cloudflare/Google DoH.
 * 2. Manual HTTP redirect handling (preserving Cookies, Referer, User-Agent across 302/307 hops).
 * 3. Akamai __hdnea__ token extraction & Cookie header injection.
 * 4. Broadcaster spoofing headers (plaYtv/JioTV user agent, Android OS tokens) preventing 403 blocks.
 * 5. Trust-All SSL for internal local IP certificates.
 */
class ProxyStreamHandler(private val serverConfig: ServerConfig) {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            synchronized(list) {
                for (c in cookies) {
                    list.removeAll { it.name == c.name }
                    list.add(c)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = cookieStore[url.host] ?: return emptyList()
            return synchronized(list) {
                list.filter { cookie ->
                    if (!cookie.matches(url)) return@filter false
                    // Akamai __hdnea__ token ACL verification:
                    // Only send __hdnea__ cookie if request path matches its ACL, preventing cross-channel 403 blocks
                    if (cookie.name == "__hdnea__" || cookie.value.contains("acl=")) {
                        val acl = cookie.value.substringAfter("acl=").substringBefore("~").removeSuffix("*")
                        if (acl.isNotEmpty() && !url.encodedPath.startsWith(acl)) {
                            return@filter false
                        }
                    }
                    true
                }
            }
        }
    }

    /**
     * Clears all cached cookies so channel switches start with clean session state.
     */
    fun clearCookies() {
        cookieStore.clear()
    }

    val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            // [PERF-OPT-3] Reusable connection pool & HTTP/2 for video chunk downloads
            .connectionPool(ConnectionPool(16, 3, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .cookieJar(cookieJar)
            .dns(SafeDns)
            // [NUVIO-OPT-4] TCP_NODELAY: disables Nagle's packet aggregation delay on video chunks
            .socketFactory(object : javax.net.SocketFactory() {
                private val delegate = javax.net.SocketFactory.getDefault()
                override fun createSocket(): java.net.Socket = delegate.createSocket().apply { tcpNoDelay = true }
                override fun createSocket(host: String?, port: Int): java.net.Socket = delegate.createSocket(host, port).apply { tcpNoDelay = true }
                override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): java.net.Socket = delegate.createSocket(host, port, localHost, localPort).apply { tcpNoDelay = true }
                override fun createSocket(host: java.net.InetAddress?, port: Int): java.net.Socket = delegate.createSocket(host, port).apply { tcpNoDelay = true }
                override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): java.net.Socket = delegate.createSocket(address, port, localAddress, localPort).apply { tcpNoDelay = true }
            })
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)

        // Trust-all SSL context for local network and dev servers
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
            Log.e("ProxyStreamHandler", "Failed to configure trust-all SSL", e)
        }

        builder.addInterceptor { chain ->
            var request = chain.request()
            val url = request.url.toString()
            val reqBuilder = request.newBuilder()

            // If stream URL has __hdnea__ token, ensure Cookie header is passed with it
            if (url.contains("__hdnea__=")) {
                val token = url.substringAfter("__hdnea__=").substringBefore("&")
                reqBuilder.header("Cookie", "__hdnea__=$token")
            }

            // JioTV Official Player User-Agent
            val jioUA = "JioTV/7.0.8 (Linux; Android 13; Pixel 7 Pro Build/TQ1A.221205.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/110.0.5481.64 Mobile Safari/537.36"
            val isJioHost = url.contains("jio.com", ignoreCase = true) ||
                    url.contains("jio.dev", ignoreCase = true) ||
                    url.contains("jiotv", ignoreCase = true) ||
                    url.contains("akamai", ignoreCase = true) ||
                    url.contains("webplay", ignoreCase = true)

            if (isJioHost) {
                if (request.header("User-Agent").isNullOrBlank()) reqBuilder.header("User-Agent", jioUA)
                if (request.header("os").isNullOrBlank()) reqBuilder.header("os", "android")
                if (request.header("devicetype").isNullOrBlank()) reqBuilder.header("devicetype", "phone")
                if (request.header("appname").isNullOrBlank()) reqBuilder.header("appname", "com.jio.jiotv")
                if (request.header("versionCode").isNullOrBlank()) reqBuilder.header("versionCode", "323")
                if (request.header("Origin").isNullOrBlank()) reqBuilder.header("Origin", "https://jiotv.jio.com")
                if (request.header("Referer").isNullOrBlank()) reqBuilder.header("Referer", "https://jiotv.jio.com/")
            } else {
                if (request.header("User-Agent").isNullOrBlank()) {
                    reqBuilder.header("User-Agent", serverConfig.customUserAgent)
                }
            }

            if (request.header("Accept").isNullOrBlank()) reqBuilder.header("Accept", "*/*")
            if (request.header("Connection").isNullOrBlank()) reqBuilder.header("Connection", "keep-alive")

            request = reqBuilder.build()

            // Capture headers to preserve across redirects
            val referer = request.header("Referer")
            val origin = request.header("Origin")
            val userAgent = request.header("User-Agent")
            val cookie = request.header("Cookie")
            val xRequestedWith = request.header("X-Requested-With")

            var response = chain.proceed(request)
            var tryCount = 0

            // Manually follow 302/307 redirects while preserving crucial authentication headers
            while (response.isRedirect && tryCount < 10) {
                var newUrl = response.header("Location") ?: break
                if (!newUrl.startsWith("http://", ignoreCase = true) && !newUrl.startsWith("https://", ignoreCase = true)) {
                    try {
                        val baseHttpUrl = request.url
                        val resolved = baseHttpUrl.resolve(newUrl)
                        if (resolved != null) {
                            newUrl = resolved.toString()
                        }
                    } catch (_: Exception) {}
                }
                response.close()

                val newReqBuilder = request.newBuilder().url(newUrl)
                if (!referer.isNullOrBlank()) newReqBuilder.header("Referer", referer)
                if (!origin.isNullOrBlank()) newReqBuilder.header("Origin", origin)
                if (!userAgent.isNullOrBlank()) newReqBuilder.header("User-Agent", userAgent)
                if (!cookie.isNullOrBlank()) newReqBuilder.header("Cookie", cookie)
                if (!xRequestedWith.isNullOrBlank()) newReqBuilder.header("X-Requested-With", xRequestedWith)

                request = newReqBuilder.build()
                response = chain.proceed(request)
                tryCount++
            }

            response
        }

        builder.build()
    }

    /**
     * Creates an ExoPlayer Media3 DataSource.Factory using OkHttp.
     * Ensures all segment requests pass through the same session, headers, and proxy cookies.
     */
    fun createMedia3DataSourceFactory(): HttpDataSource.Factory {
        return OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(serverConfig.customUserAgent)
    }

    /**
     * Fallback standard Android DefaultHttpDataSource.Factory if OkHttp is not preferred.
     */
    fun createDefaultHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(serverConfig.customUserAgent)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
    }

    // [PERF-OPT-5] Non-blocking pre-warming of adjacent stream connection
    fun warmUpStream(streamUrl: String) {
        if (streamUrl.isBlank()) return
        try {
            val target = serverConfig.resolveStreamUrl(streamUrl)
            val request = Request.Builder()
                .url(target)
                .head()
                .build()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (_: Exception) {}
    }
}
