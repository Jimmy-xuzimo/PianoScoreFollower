package com.pianoscorefollower.app.viewer

import android.webkit.WebView

/**
 * Thin command channel from Kotlin into the viewer page.
 * Every call is marshalled onto the WebView thread.
 */
class ScoreViewerController {

    private var pageReady = false
    private var pendingLoad: Int? = null
    fun placeCursor(tick: Long) = evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.setCursorTick($tick);")
    fun ensureVisible(measure: Int) = evaluate("window.PianoScoreViewer.ensureVisible($measure);")
    var generation = 0; private set
    private var layoutVersion = 0
    fun parseEvent(raw: String): ViewerEvent? {
        val json = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return null
        if (json.optString("type") != "viewerReady") {
            if (json.optInt("generation", -1) != generation) return null
            val layout = json.optInt("layoutVersion")
            if (layout < layoutVersion) return null
            layoutVersion = layout
        }
        val event = ViewerEvent.parse(raw)
        if (event == ViewerEvent.ViewerReady) { pageReady = true; pendingLoad?.let { pendingLoad = null; loadCurrentScore(it) } }
        return event
    }
    fun navigation(mode: String, allowed: Boolean) = evaluate("window.PianoScoreViewer.setNavigation(${org.json.JSONObject.quote(mode)},$allowed);")
    fun setNotationMode(mode: String) = evaluate("window.PianoScoreViewer.setNotationMode(${org.json.JSONObject.quote(mode)});")
    fun setPracticeSpeed(speed: Float) = evaluate("window.PianoScoreViewer.setPracticeSpeed($speed);")
    fun followPosition(tick: Long, anchor: Long?, measure: Int, mode: String, allowed: Boolean) = evaluate(
        "var v=window.PianoScoreViewer;v.setNavigation(${org.json.JSONObject.quote(mode)},$allowed);v.setFollowAnchor(${anchor ?: "null"});" +
        (if(anchor!=null) "v.setCursorTick($tick);" else "") +
        (if(allowed && mode=="page") "v.ensureVisible($measure);" else ""))
    fun followAnchor(tick: Long?) = evaluate("window.PianoScoreViewer.setFollowAnchor(${tick ?: "null"});")
    fun resumeFollow() = evaluate("window.PianoScoreViewer.resumeFollow();")
    fun ensureSoundFont() = evaluate("window.PianoScoreViewer.ensureSoundFont();")
    fun setStaffs(staffs: List<Int>) = evaluate("window.PianoScoreViewer.setStaffs(${staffs.joinToString(prefix = "[", postfix = "]")});")
    fun setTracks(tracks: List<Int>) = evaluate("window.PianoScoreViewer.setTracks(${tracks.joinToString(prefix = "[", postfix = "]")});")
    private var webView: WebView? = null

    fun attach(view: WebView) {
        webView = view
    }

    fun detach(view: WebView) {
        if (webView === view) { webView = null; pageReady = false }
    }

    fun isAttached(): Boolean = webView != null

    private fun evaluate(script: String) {
        val view = webView ?: return
        view.post { view.evaluateJavascript("if(window.PianoScoreViewer){$script}", null) }
    }

    fun loadCurrentScore(nextGeneration: Int = 0) {
        generation = nextGeneration; layoutVersion = 0;
        if (!pageReady) { pendingLoad = nextGeneration; return }
        evaluate("window.PianoScoreViewer&&window.PianoScoreViewer.loadScore('/score/current?v=$generation',$generation);")
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
