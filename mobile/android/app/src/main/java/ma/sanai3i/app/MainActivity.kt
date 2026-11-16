package ma.sanai3i.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Thin shell around the Sanaï3i web app (served by the Spring Boot backend).
 * - the web app stays inside the WebView
 * - tel:, WhatsApp, mailto: and every other website open in the matching Android app
 * - hardware back goes back in the web app history before closing the app
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var offline: View
    private val baseUri: Uri by lazy { Uri.parse(BuildConfig.BASE_URL) }

    // Location for the map: the web page asks, Android asks the user once, the answer goes back to the page.
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            geoCallback?.invoke(geoOrigin, result.values.any { it }, false)
            geoCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true          // the app keeps the login token and language in localStorage
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.setGeolocationEnabled(true)
            webViewClient = ShellClient()
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    if (Uri.parse(origin).host != baseUri.host) {   // only our own site gets the location
                        callback.invoke(origin, false, false)
                        return
                    }
                    val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        .any { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }
                    if (granted) {
                        callback.invoke(origin, true, false)
                    } else {
                        geoOrigin = origin
                        geoCallback = callback
                        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            }
        }
        offline = buildOfflineView()
        offline.visibility = View.GONE

        setContentView(FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(-1, -1))
            addView(offline, FrameLayout.LayoutParams(-1, -1))
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(BuildConfig.BASE_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun buildOfflineView(): View {
        val message = TextView(this).apply {
            text = getString(R.string.offline_message)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val retry = Button(this).apply {
            text = getString(R.string.retry)
            setOnClickListener {
                offline.visibility = View.GONE
                webView.loadUrl(BuildConfig.BASE_URL)
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(0xFFF4F6F2.toInt())
            addView(message)
            addView(retry)
        }
    }

    private fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // no app can handle it (for example no dialer on a tablet): ignore
        }
    }

    private inner class ShellClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val sameSite = (uri.scheme == "http" || uri.scheme == "https") &&
                uri.host == baseUri.host && uri.port == baseUri.port
            if (sameSite) return false            // stay in the app
            openExternally(uri)                   // tel:, mailto:, wa.me, other websites...
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            offline.visibility = View.GONE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) offline.visibility = View.VISIBLE
        }
    }
}
