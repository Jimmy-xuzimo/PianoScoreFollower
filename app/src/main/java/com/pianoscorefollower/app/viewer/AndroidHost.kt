package com.pianoscorefollower.app.viewer

import android.webkit.JavascriptInterface

/**
 * Bridge exposed to the viewer page as `window.AndroidHost`.
 * JavaScript calls arrive on a WebView-owned thread, so the handler must
 * dispatch to whatever thread the caller needs.
 */
class AndroidHost(private val onEvent: (String) -> Unit) {

    @JavascriptInterface
    fun postEvent(json: String) {
        onEvent(json)
    }
}
