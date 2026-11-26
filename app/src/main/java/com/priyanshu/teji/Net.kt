package com.priyanshu.teji

import android.content.Context
import com.google.android.gms.net.CronetProviderInstaller
import org.chromium.net.CronetEngine
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP for live prices. Uses Chrome's network engine (Cronet, from Google Play
 * services) when available, so data sites treat the app like the Chrome browser.
 * Falls back to the standard Java client.
 */
object Net {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    @Volatile private var engine: CronetEngine? = null
    @Volatile var lastError: String = ""
        private set

    @Volatile private var started = false

    fun init(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        try {
            CronetProviderInstaller.installProvider(app).addOnCompleteListener {
                try {
                    engine = CronetEngine.Builder(app)
                        .enableHttp2(true)
                        .enableQuic(true)
                        .enableBrotli(true)
                        .build()
                } catch (e: Throwable) {
                    engine = null
                }
            }
        } catch (e: Throwable) {
            engine = null
        }
    }

    val usingChromeEngine: Boolean get() = engine != null

    private fun open(url: String): HttpURLConnection {
        val e = engine
        if (e != null) {
            try {
                return e.openConnection(URL(url)) as HttpURLConnection
            } catch (t: Throwable) {
                // fall through to the Java client
            }
        }
        return URL(url).openConnection() as HttpURLConnection
    }

    fun get(url: String): String {
        val c = open(url)
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json,text/plain,*/*")
        c.setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                lastError = "HTTP $code from ${URL(url).host}"
                throw IOException(lastError)
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            lastError = (e.message ?: "network error").take(120)
            throw e
        } finally {
            c.disconnect()
        }
    }
}
