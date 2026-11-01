package com.pianoscorefollower.app.follower

/** Playback ticks include repeats; measure/notationTick always identify printed music. */
data class GraphEvent(
    val tick: Long,
    val duration: Long,
    val measure: Int,
    val occurrence: Int,
    val notationTick: Long,
    val pitches: IntArray,
    val sounding: IntArray = pitches,
    val tempo: Float = 120f,
    val voices: List<ScoreVoiceNote> = emptyList(),
) {
    val feature = FloatArray(128).also { a -> sounding.forEach { if (it in 0..127) a[it] = 1f } }
}

data class ScoreGraph(val events: List<GraphEvent>, val quarter: Int = 960, val endTick: Long = 0) {
    companion object {
        fun from(timeline: ScoreTimeline): ScoreGraph = ScoreGraph(
            timeline.events.map { e -> GraphEvent(e.tick, e.duration.toLong(), e.measure,
                e.occurrence, e.notationTick, e.midiNotes, e.soundingNotes, e.tempo, e.voices) },
            timeline.ticksPerQuarter, timeline.endTick,
        )
    }
}
