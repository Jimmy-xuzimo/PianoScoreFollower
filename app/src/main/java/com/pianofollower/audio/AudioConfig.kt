package com.pianofollower.audio

import kotlin.math.ln

/**
 * Tuning shared by the capture pipeline and the DSP stages that consume it.
 */
object AudioConfig {

    /** Preferred capture rate; the first entry of [SUPPORTED_SAMPLE_RATES]. */
    const val SAMPLE_RATE = 44_100

    /**
     * Rates we are willing to capture at, in order of preference.
     *
     * Every stage maps FFT bins to pitches through this rate, so analysing 48 kHz
     * audio as if it were 44.1 kHz shifts the whole keyboard up by roughly a
     * semitone and a half — every note lands on the wrong pitch class. The device
     * tells us the rate it actually opened, and that value has to travel with the
     * samples rather than being assumed.
     */
    val SUPPORTED_SAMPLE_RATES = intArrayOf(44_100, 48_000)

    /** Samples consumed per analysis step. Also the overlap hop of the FFT window. */
    const val HOP_SIZE = 1024

    /** FFT window length. Power of two, and at least twice [HOP_SIZE]. */
    const val FFT_SIZE = 2048

    /**
     * Window used by the pitch-class (chroma) stage, kept separate from [FFT_SIZE].
     *
     * A Hann window's effective resolution is about two bins, so the 2048-point
     * transform used for onsets smears everything below roughly F#5 into its
     * neighbours and the bass lands on the wrong pitch class. 8192 points bring the
     * resolution down to ~10 Hz, which is enough for the range that carries the
     * melody. It is deliberately longer than the onset window: precise attack timing
     * and precise pitch are opposing requirements, so each stage gets its own.
     */
    const val CHROMA_FFT_SIZE = 8192

    /** Level-meter floor and ceiling, in dBFS. */
    const val MIN_LEVEL_DB = -72f
    const val MAX_LEVEL_DB = -3f

    /** Two onsets closer than this are treated as one. */
    const val MIN_ONSET_GAP_MS = 70

    fun toDb(amplitude: Float): Float =
        if (amplitude <= 1e-7f) MIN_LEVEL_DB
        else (20.0 * ln(amplitude.toDouble()) / ln(10.0)).toFloat()

    fun normalizedLevel(db: Float): Float =
        ((db - MIN_LEVEL_DB) / (MAX_LEVEL_DB - MIN_LEVEL_DB)).coerceIn(0f, 1f)
}
