package com.pianoscorefollower.app.image

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEFAULT_SCROLL_DURATION_SECONDS = 180
const val MIN_SCROLL_DURATION_SECONDS = 20
const val MAX_SCROLL_DURATION_SECONDS = 1800

data class ImageScoreState(
    val pages: List<ImageScorePage> = emptyList(),
    val isImporting: Boolean = false,
    /** Wall-clock time the whole stack should take to scroll past, in seconds. */
    val totalDurationSeconds: Int = DEFAULT_SCROLL_DURATION_SECONDS,
    val isPlaying: Boolean = false,
    /** Scroll position in pixels, kept only so the view can be restored. */
    val scrollOffset: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val hasPages: Boolean get() = pages.isNotEmpty()
}

class ImageScoreViewModel(application: Application) : AndroidViewModel(application) {

    private val importer = ImageScoreImporter(application)
    private val store = ImageScoreStore(application)
    private val _uiState = MutableStateFlow(ImageScoreState())
    val uiState: StateFlow<ImageScoreState> = _uiState.asStateFlow()

    private var nextId = 1L
    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { store.load() }
            nextId = (snapshot.pages.maxOfOrNull { it.id } ?: 0L) + 1L
            _uiState.update {
                it.copy(
                    pages = snapshot.pages,
                    totalDurationSeconds = snapshot.durationSeconds,
                    scrollOffset = snapshot.scrollOffset,
                )
            }
        }
    }

    fun importPages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update {
            it.copy(isImporting = true, isPlaying = false, errorMessage = null, statusMessage = "正在导入…")
        }

        viewModelScope.launch {
            val result = importer.import(uris, nextId) { message ->
                _uiState.update { it.copy(statusMessage = message) }
            }
            result.fold(
                onSuccess = { imported ->
                    nextId = maxOf(nextId, (imported.maxOfOrNull { it.id } ?: 0L) + 1L)
                    _uiState.update { current ->
                        val merged = current.pages + imported
                        current.copy(
                            pages = merged,
                            isImporting = false,
                            isPlaying = false,
                            scrollOffset = 0,
                            statusMessage = "已导入 ${imported.size} 页，共 ${merged.size} 页",
                            errorMessage = null,
                        )
                    }
                    schedulePersist()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = null,
                            errorMessage = error.message ?: "导入失败",
                        )
                    }
                },
            )
        }
    }

    /** Moves the page at [from] so that it sits at [to]. */
    fun movePage(from: Int, to: Int) {
        _uiState.update { state ->
            if (from !in state.pages.indices || to !in state.pages.indices || from == to) return@update state
            val reordered = state.pages.toMutableList()
            reordered.add(to, reordered.removeAt(from))
            state.copy(pages = reordered, scrollOffset = 0, isPlaying = false)
        }
        schedulePersist()
    }

    fun removePage(id: Long) {
        _uiState.update { state ->
            val page = state.pages.firstOrNull { it.id == id } ?: return@update state
            importer.deletePage(page)
            state.copy(
                pages = state.pages.filterNot { it.id == id },
                scrollOffset = 0,
                isPlaying = false,
            )
        }
        schedulePersist()
    }

    fun clearPages() {
        importer.clearAll()
        store.clear()
        _uiState.update {
            it.copy(pages = emptyList(), isPlaying = false, scrollOffset = 0, statusMessage = "已清空图片谱")
        }
        schedulePersist()
    }

    fun setDurationSeconds(seconds: Int) {
        _uiState.update {
            it.copy(totalDurationSeconds = seconds.coerceIn(MIN_SCROLL_DURATION_SECONDS, MAX_SCROLL_DURATION_SECONDS))
        }
        schedulePersist()
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun saveScrollOffset(offset: Int) {
        _uiState.update { if (it.scrollOffset == offset) it else it.copy(scrollOffset = offset) }
        schedulePersist()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    /**
     * Writes the current stack to disk, coalescing bursts.
     *
     * Scrolling reports an offset continuously, and reordering a stack touches the
     * state once per tap, so the write is deferred a moment and only the last state
     * of a burst reaches the index file.
     */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            val state = _uiState.value
            withContext(Dispatchers.IO) {
                store.save(state.pages, state.totalDurationSeconds, state.scrollOffset)
            }
        }
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 400L
    }
}
