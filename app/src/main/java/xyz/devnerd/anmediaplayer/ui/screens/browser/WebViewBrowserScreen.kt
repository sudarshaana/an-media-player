package xyz.devnerd.anmediaplayer.ui.screens.browser

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import xyz.devnerd.anmediaplayer.data.EntryType
import xyz.devnerd.anmediaplayer.data.Server
import xyz.devnerd.anmediaplayer.data.source.classify

/**
 * Fallback for servers with no detectable directory-listing structure
 * (parser == "WEB"): browse the raw site like a normal mobile browser.
 * Direct links to video files are intercepted and handed to the player
 * instead of loading in-page. Page <video> fullscreen (e.g. embedded JS
 * players) is honored via onShowCustomView/onHideCustomView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewBrowserScreen(
    server: Server,
    onPlay: (String) -> Unit,
    onUp: () -> Unit,
    onNavVisible: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as? Activity
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(1f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler { if (canGoBack) webViewRef?.goBack() else onUp() }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = { if (canGoBack) webViewRef?.goBack() else onUp() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                }
            },
        )
        if (progress < 1f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                var customView: android.view.View? = null
                var customViewCallback: WebChromeClient.CustomViewCallback? = null
                val root = FrameLayout(context)
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                        when {
                            scrollY <= 0 -> onNavVisible(true)
                            scrollY > oldScrollY + 4 -> onNavVisible(false)
                            scrollY < oldScrollY - 4 -> onNavVisible(true)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            val name = Uri.decode(url.substringBefore('?').substringAfterLast('/'))
                            val isVideo = name.contains('.') && classify(name, isDir = false) == EntryType.VIDEO
                            if (isVideo) {
                                onPlay(url)
                                return true
                            }
                            return false
                        }

                        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                            if (server.auth && !server.user.isNullOrBlank()) {
                                handler.proceed(server.user, server.password ?: "")
                            } else {
                                super.onReceivedHttpAuthRequest(view, handler, host, realm)
                            }
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            canGoBack = view.canGoBack()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress / 100f
                        }

                        // Embedded <video> fullscreen request: swap the WebView out for
                        // the browser-supplied fullscreen view and hide system bars.
                        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                            if (customView != null) {
                                callback.onCustomViewHidden()
                                return
                            }
                            customView = view
                            customViewCallback = callback
                            this@apply.visibility = android.view.View.GONE
                            root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            activity?.window?.let { w ->
                                WindowCompat.getInsetsController(w, w.decorView).hide(WindowInsetsCompat.Type.systemBars())
                            }
                            onNavVisible(false)
                        }

                        override fun onHideCustomView() {
                            val view = customView ?: return
                            root.removeView(view)
                            customView = null
                            this@apply.visibility = android.view.View.VISIBLE
                            activity?.window?.let { w ->
                                val controller = WindowCompat.getInsetsController(w, w.decorView)
                                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.show(WindowInsetsCompat.Type.systemBars())
                            }
                            customViewCallback?.onCustomViewHidden()
                            customViewCallback = null
                        }
                    }
                    webViewRef = this
                    loadUrl(server.url)
                }
                root.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                root
            },
        )
    }
}
