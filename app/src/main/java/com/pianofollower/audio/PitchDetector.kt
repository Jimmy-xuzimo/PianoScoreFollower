package com.pianofollower.audio

import kotlin.math.abs
import kotlin.math.ln

/** Fundamental-frequency estimate for a single analysis window. */
internal data class PitchReading(
    val frequencyHz: Float,
    val confidence: Float,
) {
    companion object {
        val SILENT = PitchReading(0f, 0f)
    }
}

/**
 * Harmonic-product-spectrum pitch detector for one sounding note.
 *
 * Built for the calibration screen rather than the score follower: the follower only
 * needs pitch classes, but "play middle C and we learn your piano's offset" needs the
 * actual fundamental, octave included.
 *
 * The spectrum is combined as a sum of logarithms rather than a product of
 * magnitudes. Four multiplied bins of a few thousand overflow a Float, and the log
 * form ranks candidates identically without the range problem. A subharmonic guard
 * then walks the winning bin down to a half or a third when that lower partial is
 * nearly as well supported, which is what stops a piano's strong second harmonic from
 * being reported as the fundamental an octave up.
 */
internal class PitchDetector(
    private val sampleRate: Int,
    private val fftSize: Int,
) {

    private val logSpectrum = FloatArray(fftSize / 2)
    private val topValues = FloatArray(CONFIDENCE_PARTIALS)
    private val topBins = IntArray(CONFIDENCE_PARTIALS)

    fun estimate(magnitude: FloatArray): PitchReading {
        val limit = minOf(magnitude.size, fftSize / 2)
        if (limit <= 1) return PitchReading.SILENT

        val minBin = ((MIN_HZ * fftSize) / sampleRate).toInt().coerceAtLeast(1)
        // Every candidate needs all [HARMONICS] partials to fit, otherwise a high bin
        // would be scored on fewer terms than a low one and the comparison is unfair.
        val maxBin = ((MAX_HZ * fftSize) / sampleRate).toInt()
            .coerceAtMost((limit - 1) / HARMONICS)
        if (maxBin <= minBin) return PitchReading.SILENT

        var peak = 0f
        for (bin in 1 until limit) if (magnitude[bin] > peak) peak = magnitude[bin]
        if (peak <= 0f) return PitchReading.SILENT

        val floor = peak * SPECTRUM_FLOOR_RATIO
        for (bin in 0 until limit) {
            logSpectrum[bin] = ln((magnitude[bin] + floor).toDouble()).toFloat()
        }

        var bestBin = minBin
        var bestScore = hpsScore(minBin, limit)
        for (bin in minBin + 1..maxBin) {
            val score = hpsScore(bin, limit)
            if (score > bestScore) {
                bestScore = score
                bestBin = bin
            }
        }

        var chosen = bestBin
        for (divisor in 2..MAX_SUBHARMONIC_DIVISOR) {
            val candidate = bestBin / divisor
            if (candidate < minBin) continue
            if (hpsScore(candidate, limit) >= bestScore - SUBHARMONIC_MARGIN) chosen = candidate
        }

        val frequency = refineBin(chosen, limit) * sampleRate / fftSize
        if (frequency < MIN_HZ || frequency > MAX_HZ) return PitchReading.SILENT

        return PitchReading(frequency, harmonicConfidence(magnitude, limit, frequency))
    }

    private fun hpsScore(bin: Int, limit: Int): Float {
        var score = 0f
        for (harmonic in 1..HARMONICS) {
            val index = bin * harmonic
            if (index >= limit) break
            score += logSpectrum[index]
        }
        return score
    }

    /** Sub-bin peak position from the three points around [bin]. */
    private fun refineBin(bin: Int, limit: Int): Float {
        if (bin <= 1 || bin + 1 >= limit) return bin.toFloat()
        val left = hpsScore(bin - 1, limit)
        val center = hpsScore(bin, limit)
        val right = hpsScore(bin + 1, limit)
        val denominator = left - 2f * center + right
        if (abs(denominator) < 1e-6f) return bin.toFloat()
        val delta = 0.5f * (left - right) / denominator
        return bin + delta.coerceIn(-0.5f, 0.5f)
    }

    /**
     * Share of the loudest partials that actually sit on the harmonic series of [f0].
     *
     * A struck string puts its loudest bins at integer multiples of one frequency;
     * room noise spreads them anywhere. Measuring that agreement is a far better
     * "is this a note" signal than the raw peak height, which a passing rumble can
     * match.
     */
    private fun harmonicConfidence(magnitude: FloatArray, limit: Int, f0: Float): Float {
        java.util.Arrays.fill(topValues, 0f)
        java.util.Arrays.fill(topBins, 0)

        for (bin in 1 until limit) {
            val value = magnitude[bin]
            if (value <= topValues[CONFIDENCE_PARTIALS - 1]) continue
            var slot = CONFIDENCE_PARTIALS - 1
            while (slot > 0 && topValues[slot - 1] < value) {
                topValues[slot] = topValues[slot - 1]
                topBins[slot] = topBins[slot - 1]
                slot--
            }
            topValues[slot] = value
            topBins[slot] = bin
        }

        var total = 0f
        var harmonic = 0f
        for (slot in 0 until CONFIDENCE_PARTIALS) {
            val value = topValues[slot]
            if (value <= 0f) continue
            total += value
            val frequency = topBins[slot] * sampleRate.toFloat() / fftSize
            val ratio = frequency / f0
            val nearest = Math.round(ratio).coerceAtLeast(1)
            val cents = 1200f * (ln((ratio / nearest).toDouble()) / LN2).toFloat()
            if (abs(cents) <= PARTIAL_TOLERANCE_CENTS) harmonic += value
        }
        if (total <= 0f) return 0f
        return (harmonic / total).coerceIn(0f, 1f)
    }

    private companion object {
        /**
         * The full 88-key compass: A0 is 27.5 Hz and C8 is 4186 Hz. The previous
         * 55 Hz..2100 Hz window silently dropped the bottom octave and the top
         * octave, which is why low left-hand notes and the highest treble keys
         * never appeared on the tuner.
         */
        const val MIN_HZ = 27.0f
        const val MAX_HZ = 4200f

        const val HARMONICS = 4
        const val MAX_SUBHARMONIC_DIVISOR = 3

        /** Log-domain slack a lower partial may trail the winner by and still be preferred. */
        const val SUBHARMONIC_MARGIN = 0.8f

        /** Bins quieter than this share of the loudest one are treated as absent. */
        const val SPECTRUM_FLOOR_RATIO = 1e-3f

        const val CONFIDENCE_PARTIALS = 6
        const val PARTIAL_TOLERANCE_CENTS = 60f

        val LN2 = ln(2.0)
    }
}
