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
    val currentMeasure: Int = 0,
    val isViewerAttached: Boolean = false,
    val isScoreRendered: Boolean = false,
    val loadGeneration: Int = 0,
    val timeline: ScoreTimeline = ScoreTimeline.EMPTY,
    val tempoBpm: Float = DEFAULT_TEMPO_BPM,
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScoreUiState())
    val uiState: StateFlow<ScoreUiState> = _uiState.asStateFlow()

    private val scoreStore = ScoreStore(application)

    /*
     * The last score is put back on disk and restored here, so a MIDI or MusicXML
     * import survives the process being killed instead of having to be found again on
     * every launch. The restore is skipped if the player has already imported
     * something in the meantime.
     */
    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { scoreStore.load() } ?: return@launch
            _uiState.update { current ->
                if (current.loadedScore != null || current.isLoading) {
                    current
                } else {
                    current.copy(
                        loadedScore = restored,
                        statusMessage = "已恢复上次打开的乐谱",
                        loadGeneration = current.loadGeneration + 1,
                    )
                }
            }
        }
    }

    fun importScore(resolver: ContentResolver, uri: Uri) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = "正在读取乐谱…") }
        viewModelScope.launch {
            applyResult(withContext(Dispatchers.IO) { readScore(resolver, uri) })
        }
    }

    fun loadSampleScore(assets: AssetManager, assetPath: String, title: String) {
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
            applyResult(result)
        }
    }

    private fun applyResult(result: Result<LoadedScore>) {
        result.fold(
            onSuccess = { score ->
                persistScore(score)
                _uiState.update {
                    it.copy(
                        loadedScore = score,
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
                        isPlaying = false,
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

            is ViewerEvent.PlayerState -> _uiState.update {
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
        _uiState.update { it.copy(isViewerAttached = false) }
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) { scoreStore.clear() }
        }
        _uiState.update {
            it.copy(
                loadedScore = null,
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

    private fun persistScore(score: LoadedScore) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { scoreStore.save(score) }
        }
    }

    fun onPageChangedByUser(page: Int) {
        _uiState.update { it.copy(currentPage = page.coerceIn(0, maxOf(0, it.pageCount - 1))) }
    }

    fun setAutoPageTurn(enabled: Boolean) {
        _uiState.update { it.copy(autoPageTurn = enabled) }
    }

    private fun readScore(resolver: ContentResolver, uri: Uri): Result<LoadedScore> {
        return try {
            val displayName = queryDisplayName(resolver, uri) ?: "未命名乐谱"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
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
                    if (pitchClass in 0 until CHROMA_BINS) chroma[pitchClass] = 1f
                }
                var sumSquares = 0f
                for (value in chroma) sumSquares += value * value
                if (sumSquares <= 0f) return@forEachIndexed
                val inverse = 1f / sqrt(sumSquares)
                for (i in chroma.indices) chroma[i] *= inverse

                events.add(
                    ScoreEvent(
                        tick = event.tick,
                        measure = bar.index,
                        chroma = chroma,
                        noteCount = event.notes.size,
                        isMeasureStart = indexInBar == 0,
                    )
                )
                endTick = maxOf(endTick, event.tick + event.duration)
            }
        }

        return ScoreTimeline(
            ticksPerQuarter = structure.ticksPerQuarter,
            events = events,
            endTick = endTick,
            measureCount = structure.bars.size,
        )
    }
}
