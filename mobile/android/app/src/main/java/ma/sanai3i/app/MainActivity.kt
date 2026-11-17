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
import android.webkit.JavascriptInterface
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
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
    // <input type="file"> (photos, videos) and the camera
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhoto: Uri? = null
    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val cb = fileCallback ?: return@registerForActivityResult
            fileCallback = null
            var uris: Array<Uri>? = null
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val data = res.data
                val clip = data?.clipData
                uris = when {
                    clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                    data?.data != null -> arrayOf(data.data!!)
                    cameraPhoto != null -> arrayOf(cameraPhoto!!)
                    else -> null
                }
            }
            cb.onReceiveValue(uris)
        }

    // Microphone for voice notes: asked when the page starts recording.
    private var pendingMic: PermissionRequest? = null
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val req = pendingMic ?: return@registerForActivityResult
            pendingMic = null
            if (granted) req.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else req.deny()
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing to do */ }

    /** Exposed to the web app as window.BrikolApp: the push token for this device. */
    /** Host of the page shown in the WebView; the bridge answers only our own site. */
    @Volatile private var pageHost: String? = null

    inner class Bridge {
        @JavascriptInterface
        fun pushToken(): String = if (pageHost == baseUri.host) PushService.token(this@MainActivity) else ""
    }

    private fun setupPush() {
        val ready = try { FirebaseApp.getApps(this).isNotEmpty() || FirebaseApp.initializeApp(this) != null } catch (e: Exception) { false }
        if (!ready) return                              // no google-services.json in this build
        PushService.ensureChannel(this)
        PushService.tokenListener = { tok -> runOnUiThread { sendTokenToPage(tok) } }
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { tok ->
                PushService.saveToken(this, tok)
                sendTokenToPage(tok)
            }
        } catch (e: Exception) {
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun sendTokenToPage(tok: String) {
        if (::webView.isInitialized) {
            val safe = tok.replace("\\", "").replace("'", "")
            webView.evaluateJavascript("window.onBrikolPushToken && window.onBrikolPushToken('$safe')", null)
        }
    }

    /** A tapped notification opens its page inside the app. */
    private fun openLink(intent: Intent?) {
        val link = intent?.getStringExtra("link") ?: return
        if (!link.startsWith("/")) return
        webView.loadUrl(baseUri.scheme + "://" + baseUri.authority + link)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

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
            // lets the web app know it runs in the app (e.g. hide "Sign in with Google", blocked in WebViews)
            settings.userAgentString = settings.userAgentString + " Sanai3iApp/Android"
            addJavascriptInterface(Bridge(), "BrikolApp")
            webViewClient = ShellClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>,
                                               params: WebChromeClient.FileChooserParams): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    cameraPhoto = null
                    val types = params.acceptTypes.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
                    val pick = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    if (types.isNotEmpty()) pick.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                    if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    val extras = mutableListOf<Intent>()
                    val wantsImage = types.isEmpty() || types.any { it.startsWith("image") }
                    val wantsVideo = types.any { it.startsWith("video") }
                    if (wantsImage) {
                        try {
                            val dir = File(cacheDir, "camera").apply { mkdirs() }
                            val photo = File.createTempFile("photo_", ".jpg", dir)
                            cameraPhoto = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", photo)
                            extras += Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                .putExtra(MediaStore.EXTRA_OUTPUT, cameraPhoto)
                                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        } catch (e: Exception) {
                            cameraPhoto = null
                        }
                    }
                    if (wantsVideo) extras += Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_DURATION_LIMIT, 90)
                    val chooser = Intent.createChooser(pick, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
                    return try {
                        fileChooser.launch(chooser)
                        true
                    } catch (e: ActivityNotFoundException) {
                        fileCallback = null
                        false
                    }
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    val ours = request.origin.host == baseUri.host
                    if (!ours || !request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        request.deny()
                        return
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        pendingMic = request
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

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
        } else if (intent?.getStringExtra("link") != null) {
            openLink(intent)
        } else {
            webView.loadUrl(BuildConfig.BASE_URL)
        }
        setupPush()
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
            pageHost = url?.let { Uri.parse(it).host }
            offline.visibility = View.GONE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) offline.visibility = View.VISIBLE
        }
    }
}
