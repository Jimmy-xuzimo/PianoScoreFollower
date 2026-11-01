package com.pianoscorefollower.app.performance

/** All timestamps are monotonic milliseconds, never civil clock time. */
data class PerformanceObservation(
    val timeMs: Long,
    val pitches: FloatArray,
    val isAttack: Boolean,
    val active: Boolean,
    val exact: Boolean = false,
    val analysisLatencyMs: Long = 185,
    val rearticulation: Boolean = true,
    /** Exact Note On group, separate from pedal-latched sounding keys. */
    val attacks: FloatArray? = null,
)

interface PerformanceInput {
    var listener: ((PerformanceObservation) -> Unit)?
    fun close()
}

enum class FollowStatus { WAITING, FOLLOWING, LOST, LOCATING }

data class FollowPosition(
    val eventIndex: Int = 0,
    val tick: Long = 0,
    val measure: Int = 0,
    val occurrence: Int = 0,
    val tempo: Float = 120f,
    val confidence: Float = 0f,
    val status: FollowStatus = FollowStatus.WAITING,
    val similarity: Float = 0f,
    /** Score expectations, never treated as observed or scored notes. */
    val anticipatedNotes:List<Int> = emptyList(),
)
