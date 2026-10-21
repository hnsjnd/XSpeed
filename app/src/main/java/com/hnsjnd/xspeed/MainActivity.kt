package com.hnsjnd.xspeed

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar

    private var speedJs: String = ""

    companion object {
        // Mobile X home — looks like official timeline (For You / Following)
        private const val HOME_URL = "https://x.com/home"
        // Chrome Android UA — login + timeline work more reliably than default WebView UA
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        speedJs = assets.open("xspeed.js").bufferedReader().use { it.readText() }

        setupWebView()
        setupSwipeRefresh()
        setupBack()

        val startUrl = intent?.data?.toString()?.takeIf { isXUrl(it) } ?: HOME_URL
        webView.loadUrl(startUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.takeIf { isXUrl(it) }?.let { webView.loadUrl(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = USER_AGENT
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url?.toString() ?: return false
                return handleUrl(url, view)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrl(url, view)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                CookieManager.getInstance().flush()
                injectSpeedJs()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // Open in same WebView (OAuth / login popups)
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = webView
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun handleUrl(url: String, view: WebView): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return false
        return when {
            isXHost(host) -> {
                // Stay in WebView
                false
            }
            host.contains("twitter.com") || host.contains("x.com") ||
                host.contains("t.co") || host.contains("twimg.com") ||
                host.contains("cdn.syndication.twimg.com") ||
                host.contains("api.twitter.com") ||
                host.contains("accounts.google.com") || // Google OAuth if used
                host.contains("appleid.apple.com") -> {
                false
            }
            url.startsWith("intent:") || url.startsWith("twitter:") || url.startsWith("x:") -> {
                // Ignore app deep-link intents that would leave WebView
                true
            }
            else -> {
                // External http(s) — open in browser
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                }
                true
            }
        }
    }

    private fun isXHost(host: String): Boolean {
        return host == "x.com" || host.endsWith(".x.com") ||
            host == "twitter.com" || host.endsWith(".twitter.com") ||
            host == "t.co" || host.endsWith(".twimg.com")
    }

    private fun isXUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            isXHost(host)
        } catch (_: Exception) {
            false
        }
    }

    private fun injectSpeedJs() {
        if (speedJs.isBlank()) return
        // Guard re-entry; script itself also checks window.__xspeedInjected
        webView.evaluateJavascript(speedJs, null)
        // SPA may replace DOM after first paint — reinject shortly after
        webView.postDelayed({ webView.evaluateJavascript(speedJs, null) }, 800)
        webView.postDelayed({ webView.evaluateJavascript(speedJs, null) }, 2000)
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(0xFF1D9BF0.toInt())
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.BLACK)
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
        // Avoid fighting vertical scroll inside WebView
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0
        }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        // Re-inject after resume (SPA may have changed)
        webView.post { injectSpeedJs() }
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        webView.destroy()
        super.onDestroy()
    }
}
