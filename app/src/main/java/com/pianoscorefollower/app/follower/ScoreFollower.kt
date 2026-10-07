package com.pianoscorefollower.app.follower

import kotlin.math.exp
import kotlin.math.sqrt

/** Cosine similarity of two L2-normalised chroma vectors. Returns 0 for empty input. */
internal fun chromaSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.isEmpty() || b.isEmpty()) return 0f
    var dot = 0f
    var normA = 0f
    var normB = 0f
    val size = minOf(a.size, b.size)
    for (i in 0 until size) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    if (normA <= 0f || normB <= 0f) return 0f
    return dot / (sqrt(normA) * sqrt(normB))
}

/**
 * Fraction of the played energy that lands on pitch classes the score event contains.
 *
 * Cosine alone rejects a player who sounds only part of a chord: one note of a
 * three-note chord scores at most 1/sqrt(3) = 0.58 against the full target, which sits
 * right on the accept threshold, and a four-note chord fails outright. That is exactly
 * the "I played the note and nothing happened" case. Coverage asks the question that
 * actually matters here — are the notes being played the ones written down? — and is
 * indifferent to chord tones the player has not reached yet. For a single-note event it
 * equals the cosine, so monophonic playing behaves exactly as before.
 */
internal fun chromaCoverage(measured: FloatArray, target: FloatArray): Float {
    val size = minOf(measured.size, target.size)
    var total = 0f
    var inside = 0f
    for (i in 0 until size) {
        val energy = if (measured[i] > 0f) measured[i] else 0f
        total += energy
        if (target[i] > 0f) inside += energy
    }
    if (total <= 0f) return 0f
    return inside / total
}

/** How well a frame matches a score event, blending pitch shape and note coverage. */
internal fun chromaMatch(measured: FloatArray, target: FloatArray): Float =
    0.5f * chromaSimilarity(measured, target) + 0.5f * chromaCoverage(measured, target)

/**
 * Online alignment between the played chroma stream and the score's event list.
 *
 * The player is tracked with a forward-moving position window rather than a full
 * dynamic-programming alignment: on a tablet we need a position within a frame or
 * two, and a windowed search plus an onset-driven step is both cheap and stable
 * for monophonic-to-chordal piano playing.
 *
 * Confidence is a leaky accumulator: every trusted onset adds, every rejected one
 * subtracts, and silence bleeds it away on a wall-clock time constant. Decaying per
 * frame would make the value depend on the hop rate, which is an implementation
 * detail rather than a property of the performance.
 */
class ScoreFollower(
    private val config: FollowerConfig = FollowerConfig(),
) {

    private var timeline: ScoreTimeline = ScoreTimeline.EMPTY
    private var position = 0
    private var matchedTick = 0L
    private var lastMeasure = 0
    private var lastSimilarity = 0f
    private var confidence = 0f
    private var bpm = 100f
    private var lastMatchAtMs = 0L
    private var lastDecayAtMs = 0L

    val currentIndex: Int get() = position
    val currentTick: Long get() = matchedTick
    val currentMeasure: Int get() = lastMeasure
    val similarity: Float get() = lastSimilarity
    val confidenceScore: Float get() = confidence
    val isReady: Boolean get() = !timeline.isEmpty

    /** Pitch classes the current target event expects, ascending. Drives the readout. */
    val targetPitchClasses: IntArray
        get() {
            if (position !in timeline.events.indices) return IntArray(0)
            val chroma = timeline.events[position].chroma
            var count = 0
            for (value in chroma) if (value > 0f) count++
            val result = IntArray(count)
            var cursor = 0
            for (pitchClass in chroma.indices) {
                if (chroma[pitchClass] > 0f) result[cursor++] = pitchClass
            }
            return result
        }

    fun load(timeline: ScoreTimeline) {
        this.timeline = timeline
        reset()
    }

    fun reset() {
        position = 0
        matchedTick = timeline.events.firstOrNull()?.tick ?: 0L
        lastMeasure = timeline.events.firstOrNull()?.measure ?: 0
        lastSimilarity = 0f
        confidence = 0f
        lastMatchAtMs = 0L
        lastDecayAtMs = 0L
    }

    fun setTempo(bpm: Float) {
        if (bpm > 1f) this.bpm = bpm
    }

    /**
     * Feeds one analysis frame.
     *
     * A frame only moves the position when it carries an onset: sustained notes keep
     * the chroma roughly constant, so advancing on every frame would let the cursor
     * drift ahead of the player during held chords.
     */
    fun onFrame(chroma: FloatArray, isOnset: Boolean, nowMs: Long): Boolean {
        if (timeline.isEmpty) return false

        decayConfidence(nowMs)
        if (!isOnset) return false

        val windowEnd = minOf(timeline.events.size, position + config.searchWindow)
        var bestIndex = position
        var bestScore = -1f
        for (index in position until windowEnd) {
            val score = chromaMatch(chroma, timeline.events[index].chroma)
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        lastSimilarity = bestScore

        if (bestScore < config.acceptThreshold) {
            // No credible match: the note is outside the score, so trust the cursor
            // a little less rather than jumping it to a wrong bar.
            confidence = (confidence - config.rejectPenalty).coerceAtLeast(0f)
            return false
        }

        position = bestIndex
        matchedTick = timeline.events[bestIndex].tick
        lastMeasure = timeline.events[bestIndex].measure
        confidence = (confidence + config.confidenceGain).coerceAtMost(1f)
        return true
    }

    /**
     * Predicted tick at the current wall clock, used to keep the cursor moving
     * between onsets instead of freezing on the last matched note. The advance is
     * capped well short of the next event so a pause never races the cursor ahead.
     */
    fun predictedTick(nowMs: Long): Long {
        if (timeline.isEmpty) return 0L

        val event = timeline.events[position]
        if (lastMatchAtMs == 0L) return event.tick

        val nextTick = if (position + 1 < timeline.events.size) {
            timeline.events[position + 1].tick
        } else {
            timeline.endTick
        }
        if (nextTick <= event.tick) return event.tick

        val maxAdvance = ((nextTick - event.tick) * MAX_EXTRAPOLATION).toLong()
        val elapsedSeconds = (nowMs - lastMatchAtMs).coerceAtLeast(0L) / 1000f
        val elapsedTicks = (elapsedSeconds / secondsPerTick()).toLong()
        return event.tick + elapsedTicks.coerceIn(0L, maxAdvance)
    }

    fun onOnsetMatched(nowMs: Long) {
        lastMatchAtMs = nowMs
    }

    private fun decayConfidence(nowMs: Long) {
        if (lastDecayAtMs == 0L) {
            lastDecayAtMs = nowMs
            return
        }
        val elapsedMs = nowMs - lastDecayAtMs
        if (elapsedMs <= 0L) return
        lastDecayAtMs = nowMs
        confidence *= exp(-elapsedMs / config.confidenceDecayMs).toFloat()
    }

    private fun secondsPerTick(): Float = 60f / bpm / timeline.ticksPerQuarter

    private companion object {
        /** How far towards the next event the cursor may extrapolate while waiting. */
        const val MAX_EXTRAPOLATION = 0.6f
    }
}

data class FollowerConfig(
    /** How many upcoming events an onset may match against. */
    val searchWindow: Int = 12,
    /** Minimum match score for an onset to be trusted. */
    val acceptThreshold: Float = 0.5f,
    /** Confidence added by each trusted onset. */
    val confidenceGain: Float = 0.35f,
    /** Confidence removed when an onset matches nothing in the search window. */
    val rejectPenalty: Float = 0.2f,
    /** Time constant over which silence bleeds confidence away, in milliseconds. */
    val confidenceDecayMs: Float = 2500f,
)
