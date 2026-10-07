package com.pianoscorefollower.app.audio

import kotlin.math.cos

internal data class OnsetFrame(
    val flux: Float,
    val threshold: Float,
    val isOnset: Boolean,
)

/**
 * Spectral-flux onset detector.
 *
 * A frame is flagged when its flux clears a median-based adaptive threshold, is
 * still climbing (so we latch the attack rather than the decay) and sits outside
 * the refractory gap. The median tracks the local noise level, which keeps a
 * single loud chord from masking the notes that follow it.
 */
internal class OnsetDetector(
    private val hopSize: Int = AudioConfig.HOP_SIZE,
    private val fftSize: Int = AudioConfig.FFT_SIZE,
    sampleRate: Int = AudioConfig.SAMPLE_RATE,
) {

    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5 - 0.5 * cos(2.0 * Math.PI * i / (fftSize - 1))).toFloat()
    }
    private val real = FloatArray(fftSize)
    private val imaginary = FloatArray(fftSize)
    private val previousMagnitude = FloatArray(fftSize / 2)

    private val history = FloatArray(HISTORY_SIZE)
    private val sortScratch = FloatArray(HISTORY_SIZE)
    private var historyCount = 0
    private var historyCursor = 0

    private var frameIndex = 0
    private var lastOnsetFrame = -1_000
    private var previousFlux = 0f

    private val refractoryFrames = (
        (sampleRate * AudioConfig.MIN_ONSET_GAP_MS) / 1000.0 / hopSize
        ).toInt().coerceAtLeast(1)

    /** Magnitude spectrum of the last processed frame; valid until the next [process]. */
    val spectrum: FloatArray
        get() = fft.magnitude

    fun reset() {
        previousMagnitude.fill(0f)
        historyCount = 0
        historyCursor = 0
        frameIndex = 0
        lastOnsetFrame = -1_000
        previousFlux = 0f
    }

    /**
     * [windowSamples] must hold exactly [fftSize] samples ending at the current hop.
     * [gateOpen] is false while the input sits at the noise floor; the flux history
     * still advances so the detector is warm the moment playing starts.
     */
    fun process(windowSamples: FloatArray, gateOpen: Boolean): OnsetFrame {
        for (i in 0 until fftSize) {
            real[i] = windowSamples[i] * window[i]
            imaginary[i] = 0f
        }
        fft.forward(real, imaginary)

        val magnitude = fft.magnitude
        var flux = 0f
        for (bin in 0 until fftSize / 2) {
            val delta = magnitude[bin] - previousMagnitude[bin]
            if (delta > 0f) flux += delta
            previousMagnitude[bin] = magnitude[bin]
        }

        val threshold = medianOfHistory() * THRESHOLD_MULTIPLIER + THRESHOLD_OFFSET
        val isOnset = gateOpen &&
            frameIndex >= WARMUP_FRAMES &&
            flux > threshold &&
            flux > previousFlux &&
            frameIndex - lastOnsetFrame >= refractoryFrames

        if (isOnset) lastOnsetFrame = frameIndex
        pushHistory(flux)
        previousFlux = flux
        frameIndex++

        return OnsetFrame(flux = flux, threshold = threshold, isOnset = isOnset)
    }

    private fun pushHistory(value: Float) {
        history[historyCursor] = value
        historyCursor = (historyCursor + 1) % HISTORY_SIZE
        if (historyCount < HISTORY_SIZE) historyCount++
    }

    private fun medianOfHistory(): Float {
        if (historyCount == 0) return 0f
        System.arraycopy(history, 0, sortScratch, 0, historyCount)
        java.util.Arrays.sort(sortScratch, 0, historyCount)
        val middle = historyCount / 2
        return if (historyCount % 2 == 1) {
            sortScratch[middle]
        } else {
            0.5f * (sortScratch[middle - 1] + sortScratch[middle])
        }
    }

    private companion object {
        const val HISTORY_SIZE = 24
        const val THRESHOLD_MULTIPLIER = 2.2f
        const val THRESHOLD_OFFSET = 0.015f
        const val WARMUP_FRAMES = 4
    }
}
