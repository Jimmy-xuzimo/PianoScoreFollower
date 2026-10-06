package com.pianofollower.viewer

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

const val ASSET_DOMAIN = "appassets.androidplatform.net"

private const val VIEWER_LOG_TAG = "ScoreViewer"

private const val VIEWER_URL = "https://$ASSET_DOMAIN/assets/scoreviewer/index.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScoreViewerWebView(
    controller: ScoreViewerController,
    scoreBytesProvider: () -> ByteArray?,
    onEvent: (ViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainHandler = Handler(Looper.getMainLooper())
    val currentOnEvent by rememberUpdatedState(onEvent)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain(ASSET_DOMAIN)
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/score/", ScoreAssetHandler(scoreBytesProvider))
                .build()

            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.textZoom = 100

                addJavascriptInterface(
                    AndroidHost { raw ->
                        mainHandler.post {
                            ViewerEvent.parse(raw)?.let { currentOnEvent(it) }
                        }
                    },
                    "AndroidHost",
                )

                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val response = assetLoader.shouldInterceptRequest(request.url) ?: return null
                        /*
                         * The bundled viewer assets ship inside the APK and change on every
                         * install, so letting the WebView keep a heuristic copy means a fresh
                         * build can silently run against last version's JavaScript.
                         */
                        response.responseHeaders = response.responseHeaders.orEmpty() +
                            ("Cache-Control" to "no-store")
                        return response
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d(VIEWER_LOG_TAG, "${message.message()} @${message.lineNumber()}")
                        return true
                    }
                }

                loadUrl(VIEWER_URL)
                controller.attach(this)
            }
        },
    )
}
