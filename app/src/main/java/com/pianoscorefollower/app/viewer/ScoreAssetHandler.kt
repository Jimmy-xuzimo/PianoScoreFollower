package com.pianoscorefollower.app.viewer

import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Serves the currently loaded score to the viewer page over the asset-loader
 * origin, so the page can `fetch()` it as an ArrayBuffer and hand it to alphaTab.
 */
class ScoreAssetHandler(
    private val scoreBytes: () -> ByteArray?,
) : androidx.webkit.WebViewAssetLoader.PathHandler {

    override fun handle(path: String): android.webkit.WebResourceResponse {
        val bytes = scoreBytes()
            ?: return android.webkit.WebResourceResponse(
                "text/plain",
                "utf-8",
                404,
                "Not Found",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream(ByteArray(0)) as InputStream,
            )

        return android.webkit.WebResourceResponse(
            "application/octet-stream",
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(bytes) as InputStream,
        )
    }
}
