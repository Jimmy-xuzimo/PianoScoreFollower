package com.pianoscorefollower.app.viewer

import android.webkit.WebView

/**
 * Thin command channel from Kotlin into the viewer page.
 * Every call is marshalled onto the WebView thread.
 */
class ScoreViewerController {

    private var webView: WebView? = null

    fun attach(view: WebView) {
        webView = view
    }

    fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    fun isAttached(): Boolean = webView != null

    private fun evaluate(script: String) {
        val view = webView ?: return
        view.post { view.evaluateJavascript(script, null) }
    }

    fun loadCurrentScore() {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.loadScore('/score/current?v=${System.currentTimeMillis()}');")
    }

    fun setCursorTick(tick: Long) {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.setCursorTick($tick);")
    }

    fun hideCursor() {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.hideCursor();")
    }

    fun scrollToPage(page: Int) {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.scrollToPage($page);")
    }

    fun scrollToMasterBar(masterBarIndex: Int) {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.scrollToMasterBar($masterBarIndex);")
    }

    fun setScale(scale: Float) {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.setScale($scale);")
    }

    fun togglePlay() {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.togglePlay();")
    }

    fun play() {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.play();")
    }

    fun pause() {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.pause();")
    }

    fun stopPlayback() {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.stop();")
    }

    /** Rebuilds the player so alphaTab fetches the soundfont again after a failure. */
    fun retrySoundFont() {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.retrySoundFont();")
    }

    fun seekTo(tick: Long) {
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.seekTo($tick);")
    }

    fun showHint(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("'", "\\'")
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.showHint('$escaped');")
    }
}
