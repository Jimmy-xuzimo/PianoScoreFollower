package com.pianofollower.follower

/**
 * The score reduced to a list of onset events the follower can align against.
 * Times are alphaTab ticks (960 per quarter note), which is the same unit the
 * viewer's cursor uses.
 */
data class ScoreTimeline(
    val ticksPerQuarter: Int,
    val events: List<ScoreEvent>,
    val endTick: Long,
    val measureCount: Int,
) {

    val isEmpty: Boolean get() = events.isEmpty()

    /** Duration in seconds of one quarter note, i.e. the score's base tempo. */
    fun secondsPerQuarter(bpm: Float): Float = 60f / bpm

    /** start/end tick pairs, flattened; -1 marks a measure without notated events. */
    private val measureBounds: LongArray by lazy { computeMeasureBounds() }

    /**
     * How far the player is through [measure] at [tick], in 0..1.
     *
     * Returns 1 for measures we cannot bound, so a missing range never blocks the
     * caller waiting for the end of a page.
     */
    fun progressInMeasure(measure: Int, tick: Long): Float {
        if (measure < 0 || measure >= measureCount) return 1f
        val start = measureBounds[measure * 2]
        val end = measureBounds[measure * 2 + 1]
        if (start < 0L || end <= start) return 1f
        return ((tick - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    private fun computeMeasureBounds(): LongArray {
        val bounds = LongArray(measureCount * 2) { -1L }

        for (event in events) {
            val measure = event.measure
            if (measure < 0 || measure >= measureCount) continue
            val slot = measure * 2
            if (bounds[slot] < 0L || event.tick < bounds[slot]) bounds[slot] = event.tick
        }

        for (measure in 0 until measureCount) {
            val start = bounds[measure * 2]
            if (start < 0L) continue
            var next = -1L
            for (candidate in measure + 1 until measureCount) {
                if (bounds[candidate * 2] >= 0L) {
                    next = bounds[candidate * 2]
                    break
                }
            }
            bounds[measure * 2 + 1] = if (next >= 0L) next else maxOf(endTick, start)
        }

        return bounds
    }

    companion object {
        val EMPTY = ScoreTimeline(ticksPerQuarter = 960, events = emptyList(), endTick = 0L, measureCount = 0)
    }
}

/**
 * One notated attack. [chroma] holds the pitch classes sounding at this event and
 * [measure] is zero-based, matching what the viewer reports back.
 */
data class ScoreEvent(
    val tick: Long,
    val measure: Int,
    val chroma: FloatArray,
    val noteCount: Int,
    val isMeasureStart: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScoreEvent) return false
        return tick == other.tick && measure == other.measure && noteCount == other.noteCount
    }

    override fun hashCode(): Int {
        var result = tick.hashCode()
        result = 31 * result + measure
        result = 31 * result + noteCount
        return result
    }
}
