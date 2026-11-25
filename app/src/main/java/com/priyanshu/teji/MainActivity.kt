package com.priyanshu.teji

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.ByteArrayInputStream

/**
 * Teji: the paper trading web app inside a WebView.
 * The page is served from the APK's assets at https://teji.app/ and every
 * request to https://teji.app/api/... is answered on the phone by [LiveApi],
 * so live NSE and crypto prices work without a laptop.
 */
class MainActivity : Activity() {

    companion object {
        const val HOST = "teji.app"
        private const val LIGHT_BG = 0xFFEDF0F4.toInt()
        private const val DARK_BG = 0xFF0A0F17.toInt()
    }

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private val api = LiveApi()

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        root.setBackgroundColor(LIGHT_BG)
        web = WebView(this)
        web.setBackgroundColor(LIGHT_BG)
        root.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)

        // Draw behind the system bars and pad the content ourselves, so the
        // status bar takes the app's background colour on every Android version.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            mediaPlaybackRequiresUserGesture = true
        }
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.addJavascriptInterface(Bridge(), "TejiNative")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.host != HOST) return null
                val path = url.path ?: "/"
                return if (path.startsWith("/api/")) api.handle(path, url) else asset(path)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == HOST) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: Exception) {
                    // no app can open this link
                }
                return true
            }
        }
        web.loadUrl("https://$HOST/index.html")
    }

    private fun asset(path: String): WebResourceResponse {
        val name = path.trimStart('/').ifEmpty { "index.html" }
        val mime = when (name.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
        return try {
            WebResourceResponse(mime, "utf-8", assets.open(name))
        } catch (e: Exception) {
            WebResourceResponse(
                "text/plain", "utf-8", 404, "Not Found",
                mapOf("Cache-Control" to "no-store"), ByteArrayInputStream("Not found".toByteArray())
            )
        }
    }

    private fun applyBars(dark: Boolean) {
        val bg = if (dark) DARK_BG else LIGHT_BG
        root.setBackgroundColor(bg)
        web.setBackgroundColor(bg)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = bg
            @Suppress("DEPRECATION")
            window.navigationBarColor = bg
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (dark) 0 else
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun setTheme(dark: Boolean) {
            runOnUiThread { applyBars(dark) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.tejiBack && window.tejiBack()) ? 'yes' : 'no'") { result ->
            if (result == null || !result.contains("yes")) finish()
        }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        api.shutdown()
        web.destroy()
        super.onDestroy()
    }
}
