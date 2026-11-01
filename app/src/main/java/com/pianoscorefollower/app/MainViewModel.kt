package com.pianoscorefollower.app

import android.app.Application
import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianoscorefollower.app.follower.ScoreEvent
import com.pianoscorefollower.app.follower.ScoreTimeline
import com.pianoscorefollower.app.score.LoadedScore
import com.pianoscorefollower.app.score.ScoreFormat
import com.pianoscorefollower.app.score.ScoreStore
import com.pianoscorefollower.app.viewer.ViewerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val MAX_SCORE_BYTES = 32L * 1024 * 1024
private const val CHROMA_BINS = 12

data class ScoreUiState(
    val loadedScore: LoadedScore? = null,
    val library: List<com.pianoscorefollower.app.score.LibraryEntry> = emptyList(),
    val libraryId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val scoreTitle: String = "",
    val trackNames: List<String> = emptyList(),
    val measureCount: Int = 0,
    val pageCount: Int = 0,
    /** Page the viewer is currently scrolled to. */
    val currentPage: Int = 0,
    /** Page holding the follower's cursor, which may lead [currentPage] during a turn. */
    val cursorPage: Int = 0,
    val isLastMeasureOfPage: Boolean = false,
    val autoPageTurn: Boolean = true,
    val autoLocate: Boolean = false,
    val currentMeasure: Int = 0,
    val isViewerAttached: Boolean = false,
    val isScoreRendered: Boolean = false,
    val loadGeneration: Int = 0,
    val timeline: ScoreTimeline = ScoreTimeline.EMPTY,
    val tempoBpm: Float = DEFAULT_TEMPO_BPM,
    val seekTick: Long = 0L,
    val playbackTick: Long = 0L,
    val userBrowsing: Boolean = false,
    val navigationMode: String = "line",
    val isPlaying: Boolean = false,
    /** True once the soundfont is loaded, i.e. playback would actually produce audio. */
    val isPlayerReady: Boolean = false,
    val playerError: String? = null,
) {
    val canFollow: Boolean get() = !timeline.isEmpty

    /**
     * Transport controls unlock once the score is laid out. Audio may still be
     * warming up, in which case pressing play reports that through [playerError].
     */
    val canPlay: Boolean get() = isScoreRendered

    /**
     * The score is on screen but the soundfont has not been decoded yet. Distinct
     * from a failure, so the transport can show progress instead of a dead end.
     */
    val isPlayerLoading: Boolean
        get() = isScoreRendered && !isPlayerReady && playerError == null
}

const val DEFAULT_TEMPO_BPM = 100f

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val performancePreferences=application.getSharedPreferences("performance",0)
    private val _uiState = MutableStateFlow(ScoreUiState(
        autoPageTurn=performancePreferences.getBoolean("autoPageTurn",true),
        autoLocate=performancePreferences.getBoolean("autoLocate",false),
        navigationMode=performancePreferences.getString("navigationMode","line").takeIf { it=="line" || it=="page" } ?: "line"))
    val uiState: StateFlow<ScoreUiState> = _uiState.asStateFlow()

    private val library = com.pianoscorefollower.app.score.ScoreLibrary(application)
    private var requestVersion = 0
    private val storage = Dispatchers.IO.limitedParallelism(1)

    /*
     * The last score is put back on disk and restored here, so a MIDI or MusicXML
     * import survives the process being killed instead of having to be found again on
     * every launch. The restore is skipped if the player has already imported
     * something in the meantime.
     */
    init {
        if (BuildConfig.DEBUG) DebugHooks.scoreCommand = { action, argument ->
            when (action) {
                "loadSample" -> loadSampleScore(application.assets, argument, argument.substringAfterLast('/'))
                "loadFile" -> {
                    val root = application.getExternalFilesDir(null)?.canonicalFile
                    val file = java.io.File(argument).canonicalFile
                    if (root != null && file.toPath().startsWith(root.toPath())) {
                        val request = ++requestVersion
                        viewModelScope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching {
                                val bytes = file.readBytes(); require(bytes.size <= MAX_SCORE_BYTES)
                                LoadedScore.prepare(file.name, ScoreFormat.detect(bytes,file.name), bytes)
                            } }
                            if (request == requestVersion) applyResult(result)
                        }
                    }
                }
                "dump" -> {
                    val value = _uiState.value
                    val j = org.json.JSONObject().put("title",value.scoreTitle).put("ready",value.isScoreRendered)
                        .put("events",value.timeline.events.size).put("pages",value.pageCount).put("seek",value.seekTick)
                        .put("library",value.library.size).put("playing",value.isPlaying).put("generation",value.loadGeneration)
                        .put("navigation",value.navigationMode).put("autoPageTurn",value.autoPageTurn).put("autoLocate",value.autoLocate)
                    java.io.File(application.getExternalFilesDir(null),"debug-score.json").writeText(j.toString())
                }
            }
        }
        viewModelScope.launch {
            runCatching { withContext(storage) { library.migrate(application) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage="旧版数据迁移失败，副本已保留：${error.message}") } }
            runCatching { withContext(storage) { library.list() } }
                .onSuccess { items ->
                    _uiState.update { it.copy(library = items) }
                }.onFailure { e -> _uiState.update { it.copy(errorMessage = "曲库恢复失败：${e.message}") } }
        }
    }
    fun refreshLibrary() { viewModelScope.launch {
        runCatching { withContext(storage) { library.list() } }.onSuccess { items -> _uiState.update { it.copy(library = items) } }
            .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
    } }
    fun openLibrary(id: String) { val request = ++requestVersion; viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val result = runCatching { withContext(storage) { library.open(id) } }
        result.onSuccess { score ->
            if (request != requestVersion) return@onSuccess
            val tick = _uiState.value.library.firstOrNull { it.id == id }?.tick ?: 0
            applyResult(Result.success(score), false)
            _uiState.update { it.copy(libraryId = id, seekTick = tick) }
        }.onFailure { e -> if (request == requestVersion) _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
    } }
    fun rememberPosition(tick: Long, updateAnchor: Boolean = true) {
        if (updateAnchor) _uiState.update { it.copy(seekTick = tick) }
        val id = _uiState.value.libraryId ?: return
        viewModelScope.launch { runCatching { withContext(storage) { library.position(id, tick) } }
            .onFailure { e -> _uiState.update { it.copy(errorMessage = "保存位置失败：${e.message}") } } }
    }
    fun deleteLibrary(id: String) { viewModelScope.launch {
        runCatching { withContext(storage) { library.delete(id) } }.onSuccess {
            if (_uiState.value.libraryId == id) clearLoadedScore()
            refreshLibrary()
        }.onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
    } }
    fun renameLibrary(id: String, title: String) { viewModelScope.launch {
        runCatching { withContext(storage) { library.rename(id,title) } }.onSuccess { refreshLibrary() }
            .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
    } }
    fun loadRecording(session: com.pianoscorefollower.app.midi.RecordingSession) {
        val request = ++requestVersion
        _uiState.update { it.copy(isLoading = true, statusMessage = "正在生成琴谱…") }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val raw = com.pianoscorefollower.app.midi.MidiRecordingWriter.write(session)
                val xml = com.pianoscorefollower.app.score.MidiToMusicXml.convert(raw, session.options.grid, true).toByteArray()
                LoadedScore(session.title, ScoreFormat.MIDI, ScoreFormat.MUSIC_XML, xml, originalBytes = raw)
            } }
            if (request == requestVersion) applyResult(result)
        }
    }

    fun importScore(resolver: ContentResolver, uri: Uri) {
        val request = ++requestVersion
        _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "正在读取乐谱…") }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { readScore(resolver, uri) }
            if (request == requestVersion) applyResult(result)
        }
    }

    fun loadSampleScore(assets: AssetManager, assetPath: String, title: String) {
        val request = ++requestVersion
        _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "正在载入示例乐谱…") }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = assets.open(assetPath).use { it.readBytes() }
                    LoadedScore.prepare(
                        displayName = title,
                        sourceFormat = ScoreFormat.detect(bytes, assetPath),
                        bytes = bytes,
                    )
                }
            }
            if (request == requestVersion) applyResult(result)
        }
    }

    private fun applyResult(result: Result<LoadedScore>, persist: Boolean = true) {
        result.fold(
            onSuccess = { score ->
                if (persist) persistScore(score)
                _uiState.update {
                    it.copy(
                        loadedScore = score, libraryId = null, scoreTitle = score.displayName,
                        trackNames = emptyList(), measureCount = 0,
                        isLoading = false,
                        statusMessage = "已导入 ${score.displayName}（${score.sourceFormat.displayName}）",
                        errorMessage = null,
                        isScoreRendered = false,
                        pageCount = 0,
                        currentPage = 0,
                        cursorPage = 0,
                        isLastMeasureOfPage = false,
                        currentMeasure = 0,
                        loadGeneration = it.loadGeneration + 1,
                        timeline = ScoreTimeline.EMPTY,
                        tempoBpm = DEFAULT_TEMPO_BPM,
                        isPlaying = false, seekTick = 0, playbackTick = 0, userBrowsing = false,
                        isPlayerReady = false,
                        playerError = null,
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = error.message ?: "导入失败",
                    )
                }
            },
        )
    }

    fun onViewerEvent(event: ViewerEvent) {
        when (event) {
            is ViewerEvent.PlaybackPosition -> _uiState.update { it.copy(playbackTick=event.tick) }
            is ViewerEvent.SeekRequested -> _uiState.update { it.copy(seekTick = event.tick, userBrowsing = false) }
            ViewerEvent.UserBrowsing -> _uiState.update { it.copy(userBrowsing = true) }
            is ViewerEvent.ViewerReady -> _uiState.update { it.copy(isViewerAttached = true) }

            is ViewerEvent.ScoreLoaded -> _uiState.update {
                it.copy(
                    scoreTitle = event.title.ifBlank { it.loadedScore?.displayName.orEmpty() },
                    trackNames = event.trackNames,
                    measureCount = event.measureCount,
                    statusMessage = null,
                )
            }

            is ViewerEvent.RenderFinished -> _uiState.update {
                it.copy(
                    isScoreRendered = true,
                    pageCount = event.pageCount,
                    measureCount = if (event.measureCount > 0) event.measureCount else it.measureCount,
                    statusMessage = "乐谱已就绪 · ${event.pageCount} 页 / ${event.measureCount} 小节",
                )
            }

            is ViewerEvent.ScoreStructure -> _uiState.update {
                it.copy(
                    timeline = buildTimeline(event),
                    tempoBpm = event.bars.firstOrNull { bar -> bar.tempo > 1f }?.tempo ?: DEFAULT_TEMPO_BPM,
                )
            }

            is ViewerEvent.PositionChanged -> _uiState.update {
                it.copy(
                    currentMeasure = event.measure,
                    cursorPage = event.page.coerceAtLeast(0),
                    isLastMeasureOfPage = event.isLastMeasureOfPage,
                    pageCount = if (event.pageCount > 0) event.pageCount else it.pageCount,
                )
            }

            is ViewerEvent.PageChanged -> _uiState.update {
                it.copy(
                    currentPage = event.page.coerceAtLeast(0),
                    pageCount = if (event.pageCount > 0) event.pageCount else it.pageCount,
                )
            }

            is ViewerEvent.PlayerState -> {
                val wasPlaying=_uiState.value.isPlaying
                _uiState.update {
                when (event.state) {
                    // alphaTab fires `ready` before the soundfont has arrived, so only
                    // the soundfont event means audio can actually be produced.
                    "soundfontLoaded" -> it.copy(isPlayerReady = true, playerError = null)

                    "soundfontFailed" -> it.copy(
                        isPlayerReady = false,
                        isPlaying = false,
                        playerError = "音源加载失败，播放功能不可用",
                    )

                    "playing" -> it.copy(isPlaying = true)

                    "paused" -> it.copy(isPlaying = false)

                    else -> it
                }
                }
                if(event.state=="paused" && wasPlaying) rememberPosition(_uiState.value.playbackTick)
            }

            is ViewerEvent.Failure -> _uiState.update {
                it.copy(isLoading = false, statusMessage = null, errorMessage = event.message)
            }
        }
    }

    /**
     * Clears the failure so the transport goes back to its loading state while the
     * viewer rebuilds its player.
     */
    fun onSoundFontRetry() {
        _uiState.update { it.copy(isPlayerReady = false, playerError = null) }
    }

    /** The viewer WebView left the screen; a rebuilt one has to be told to load again. */
    fun onViewerDetached() {
        _uiState.update { it.copy(isViewerAttached = false, isScoreRendered = false,
            isPlayerReady = false, isPlaying = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Forgets the score that was restored on launch and drops it from disk. Importing
     * a different file replaces the stored one anyway; this is the explicit "delete"
     * the player asked for.
     */
    fun clearLoadedScore() {
        requestVersion++
        _uiState.update {
            it.copy(
                loadedScore = null, libraryId = null, seekTick = 0,
                isLoading = false,
                statusMessage = null,
                errorMessage = null,
                scoreTitle = "",
                trackNames = emptyList(),
                measureCount = 0,
                pageCount = 0,
                currentPage = 0,
                cursorPage = 0,
                isLastMeasureOfPage = false,
                currentMeasure = 0,
                isScoreRendered = false,
                timeline = ScoreTimeline.EMPTY,
                isPlaying = false,
                isPlayerReady = false,
                playerError = null,
                loadGeneration = it.loadGeneration + 1,
            )
        }
    }

    private fun persistScore(score: LoadedScore) { viewModelScope.launch {
        runCatching { withContext(storage) { library.save(score) } }
            .onSuccess { id -> _uiState.update { if (it.loadedScore === score) it.copy(libraryId = id) else it }; refreshLibrary() }
            .onFailure { e -> _uiState.update { it.copy(errorMessage = "保存乐谱失败：${e.message}") } }
    } }

    fun onPageChangedByUser(page: Int) {
        _uiState.update { it.copy(currentPage = page.coerceIn(0, maxOf(0, it.pageCount - 1)), userBrowsing = true) }
    }

    fun resumeFollow() { _uiState.update { it.copy(userBrowsing = false) } }
    fun setNavigationMode(mode: String) {
        require(mode=="line" || mode=="page")
        _uiState.update { it.copy(navigationMode = mode) }
        performancePreferences.edit().putString("navigationMode",mode).apply()
    }
    fun setAutoLocate(enabled: Boolean) {
        _uiState.update { it.copy(autoLocate=enabled) }
        performancePreferences.edit().putBoolean("autoLocate",enabled).apply()
    }
    fun setAutoPageTurn(enabled: Boolean) {
        _uiState.update { it.copy(autoPageTurn = enabled) }
        performancePreferences.edit().putBoolean("autoPageTurn",enabled).apply()
    }

    private fun readScore(resolver: ContentResolver, uri: Uri): Result<LoadedScore> {
        return try {
            val displayName = queryDisplayName(resolver, uri) ?: "未命名乐谱"
            val bytes = resolver.openInputStream(uri)?.use { stream ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16384)
                while (true) { val n = stream.read(buffer); if (n < 0) break
                    if (output.size().toLong() + n > MAX_SCORE_BYTES) throw IllegalArgumentException("乐谱上限为 32 MB")
                    output.write(buffer,0,n) }
                output.toByteArray()
            }
                ?: return Result.failure(IllegalStateException("无法打开所选文件"))

            if (bytes.isEmpty()) {
                return Result.failure(IllegalStateException("文件内容为空"))
            }
            if (bytes.size > MAX_SCORE_BYTES) {
                return Result.failure(
                    IllegalStateException("文件过大（${bytes.size / 1024 / 1024} MB），上限为 32 MB")
                )
            }

            val format = ScoreFormat.detect(bytes, displayName)
            if (format == ScoreFormat.UNKNOWN) {
                return Result.failure(
                    IllegalStateException("无法识别的格式，请选择 MIDI（.mid/.midi）或 MusicXML（.musicxml/.xml/.mxl）")
                )
            }

            Result.success(
                LoadedScore.prepare(
                    displayName = displayName,
                    sourceFormat = format,
                    bytes = bytes,
                )
            )
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.message ?: "读取文件失败", error))
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts the viewer's flattened structure into the alignment target. Each event
     * becomes a unit-length chroma vector, so the follower can score it against a
     * normalised microphone frame with a plain dot product.
     */
    private fun buildTimeline(structure: ViewerEvent.ScoreStructure): ScoreTimeline {
        val events = ArrayList<ScoreEvent>()
        var endTick = 0L

        for (bar in structure.bars) {
            bar.events.forEachIndexed { indexInBar, event ->
                val chroma = FloatArray(CHROMA_BINS)
                for (pitchClass in event.notes) {
                    if (pitchClass in 0..127) chroma[pitchClass % 12] = 1f
                }
                var sumSquares = 0f
                for (value in chroma) sumSquares += value * value

                val inverse = if (sumSquares > 0f) 1f / sqrt(sumSquares) else 0f
                for (i in chroma.indices) chroma[i] *= inverse

                events.add(
                    ScoreEvent(
                        tick = event.tick,
                        measure = bar.index,
                        chroma = chroma,
                        noteCount = event.notes.size,
                        isMeasureStart = indexInBar == 0,
                        midiNotes = event.notes, soundingNotes = event.sounding,
                        duration = event.duration, notationTick = event.notationTick,
                        occurrence = bar.occurrence, tempo = event.tempo, voices = event.voices,
                    )
                )
                endTick = maxOf(endTick, event.tick + event.duration)
            }
        }

        return ScoreTimeline(
            ticksPerQuarter = structure.ticksPerQuarter,
            events = events,
            endTick = maxOf(endTick, structure.bars.maxOfOrNull { it.end } ?: 0),
            measureCount = (structure.bars.maxOfOrNull { it.index } ?: -1) + 1,
        )
    }
}
