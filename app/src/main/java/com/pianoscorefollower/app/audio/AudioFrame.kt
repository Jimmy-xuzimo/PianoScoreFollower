package com.pianoscorefollower.app.audio

/**
 * One analysed hop of microphone audio.
 *
 * [chroma] is owned by the analyzer and is overwritten by the next frame, so any
 * consumer that keeps it must copy first.
 */
data class AudioFrame(
    val levelDb: Float,
    val peakDb: Float,
    val noiseFloorDb: Float,
    val isOnset: Boolean,
    val flux: Float,
    val peakMagnitude: Float,
    val chroma: FloatArray,
    /**
     * Fundamental of the loudest sounding note, and how much of the spectrum agrees
     * with its harmonic series. Only populated while pitch detection is switched on,
     * which the calibration screen does and the score follower does not.
     */
    val pitchHz: Float = 0f,
    val pitchConfidence: Float = 0f,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return levelDb == other.levelDb && isOnset == other.isOnset && flux == other.flux
    }

    override fun hashCode(): Int {
        var result = levelDb.hashCode()
        result = 31 * result + isOnset.hashCode()
        result = 31 * result + flux.hashCode()
        return result
    }
}
