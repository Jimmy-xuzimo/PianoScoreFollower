package com.pianofollower.follower

import com.pianofollower.audio.AudioFrame
import com.pianofollower.audio.ListeningEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FollowState(
    val isFollowing: Boolean = false,
    /** Tick the viewer should draw the cursor at. */
    val cursorTick: Long = 0L,
    val measure: Int = 0,
    val confidence: Float = 0f,
    /** Chroma of the most recent frame, normalised to unit length. */
    val chroma: List<Float> = List(CHROMA_BINS) { 0f },
    /** Loudest pitch classes of the most recent frame, strongest first. */
    val topPitchClasses: List<Int> = emptyList(),
    /** Pitch classes the current target event expects, for the on-screen comparison. */
    val targetPitchClasses: List<Int> = emptyList(),
    /** Match score of the most recent onset, in 0..1. */
    val bestSimilarity: Float = 0f,
    val isOnset: Boolean = false,
    val onsetPulse: Long = 0L,
    val isReady: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FollowState) return false
        return isFollowing == other.isFollowing &&
            cursorTick == other.cursorTick &&
            measure == other.measure &&
            confidence == other.confidence &&
            chroma == other.chroma &&
            topPitchClasses == other.topPitchClasses &&
            targetPitchClasses == other.targetPitchClasses &&
            bestSimilarity == other.bestSimilarity &&
            isOnset == other.isOnset &&
            onsetPulse == other.onsetPulse &&
            isReady == other.isReady
    }

    override fun hashCode(): Int {
        var result = isFollowing.hashCode()
        result = 31 * result + cursorTick.hashCode()
        result = 31 * result + measure
        result = 31 * result + confidence.hashCode()
        result = 31 * result + bestSimilarity.hashCode()
        return result
    }

    companion object {
        const val CHROMA_BINS = 12
    }
}

/**
 * Connects the microphone stream to [ScoreFollower] and publishes the cursor.
 *
 * Runs on the capture thread; the follower's search window is small enough that the
 * extra work per frame is a few hundred multiply-adds.
 */
class FollowerEngine {

    private val _state = MutableStateFlow(FollowState())
    val state: StateFlow<FollowState> = _state.asStateFlow()

    private val follower = ScoreFollower()
    private var listening: ListeningEngine? = null
    private var onsetPulse = 0L

    fun attach(listeningEngine: ListeningEngine, timeline: ScoreTimeline, bpm: Float) {
        detach()
        follower.load(timeline)
        follower.setTempo(bpm)
        onsetPulse = 0L

        _state.update {
            it.copy(
                isFollowing = true,
                cursorTick = follower.currentTick,
                measure = follower.currentMeasure,
                confidence = 0f,
                isOnset = false,
                isReady = !timeline.isEmpty,
            )
        }

        listening = listeningEngine
        listeningEngine.frameListener = ::onFrame
    }

    fun detach() {
        listening?.frameListener = null
        listening = null
        _state.update { it.copy(isFollowing = false, confidence = 0f, isOnset = false) }
    }

    fun seekToStart() {
        follower.reset()
        _state.update {
            it.copy(
                cursorTick = follower.currentTick,
                measure = follower.currentMeasure,
                confidence = 0f,
            )
        }
    }

    private fun onFrame(frame: AudioFrame) {
        val chroma = frame.chroma
        // Chroma is L2-normalised, so the strongest bin is ~0.29 even on silence.
        // Whether anything is actually sounding can only be read off the level.
        val sounding = frame.levelDb >= frame.noiseFloorDb + DOMINANT_GATE_MARGIN_DB &&
            frame.levelDb >= MIN_DOMINANT_LEVEL_DB

        val now = System.currentTimeMillis()
        val matched = follower.onFrame(chroma, frame.isOnset, now)
        if (matched) {
            follower.onOnsetMatched(now)
            onsetPulse++
        }

        _state.update {
            it.copy(
                cursorTick = follower.predictedTick(now),
                measure = follower.currentMeasure,
                confidence = follower.confidenceScore,
                chroma = chroma.toList(),
                topPitchClasses = if (sounding) topPitchClasses(chroma) else emptyList(),
                targetPitchClasses = follower.targetPitchClasses.toList(),
                bestSimilarity = follower.similarity,
                isOnset = matched,
                onsetPulse = onsetPulse,
            )
        }
    }

    /**
     * Loudest pitch classes, strongest first.
     *
     * The cutoff is relative to the strongest class, not absolute: striking one piano
     * key sounds faintly of its own harmonics (a C carries some G and E), so a fixed
     * floor would report three notes where the player played one and make a correct
     * reading look like a wrong one.
     */
    private fun topPitchClasses(chroma: FloatArray): List<Int> {
        var peak = 0f
        for (value in chroma) if (value > peak) peak = value
        if (peak <= 0f) return emptyList()

        val cutoff = peak * TOP_PITCH_CLASS_RATIO
        return chroma.indices
            .filter { chroma[it] >= cutoff }
            .sortedByDescending { chroma[it] }
            .take(TOP_PITCH_CLASSES)
    }

    private companion object {
        /** Same margin the onset detector uses to decide the input is above the room. */
        const val DOMINANT_GATE_MARGIN_DB = 6f
        const val MIN_DOMINANT_LEVEL_DB = -60f
        const val TOP_PITCH_CLASSES = 3

        /** A class is only named if it carries at least this share of the loudest one. */
        const val TOP_PITCH_CLASS_RATIO = 0.4f
    }
}
