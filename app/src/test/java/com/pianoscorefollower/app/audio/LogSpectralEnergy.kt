/* Adapted from Matchmaker's LogSpectralEnergyProcessor (Apache-2.0).
 * Copyright and pinned source: third_party/matchmaker/NOTICE. */
package com.pianoscorefollower.app.audio

import kotlin.math.*

/** Causal 46ms spectral energy difference. Never estimates individual piano notes.
 * Windows begin at sample zero, matching librosa(center=false), with no leading pad.
 * Instances are confined to one worker. The returned arrays belong to the caller. */
class LogSpectralEnergy(val sampleRate: Int = 44100, val hopSize: Int = 1024) {
    val fftSize = 2.0.pow(round(log2(2048.0 / 44100 * sampleRate))).toInt()
    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize)
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val weights = FloatArray(fftSize) { (.54 - .46 * cos(2 * PI * it / (fftSize - 1))).toFloat() }
    private val cross = (2 / (2.0.pow(1.0 / 12) - 1)).toInt()
    private val offset = Math.rint(log2(cross.toDouble() * sampleRate / fftSize / 440) * 12 + 69).toInt()
    private val bins = IntArray(fftSize / 2 + 1) { k ->
        if (k <= cross) k else cross + Math.rint(min(127.0, log2(k.toDouble() * sampleRate / fftSize / 440) * 12 + 69)).toInt() - offset
    }
    val dimension = bins.last() + 1
    private val previous = FloatArray(dimension)
    private var filled = 0
    private var hop = 0
    var samplesRead: Long = 0; private set

    fun accept(samples: FloatArray, onFrame: (FloatArray, Long, Float) -> Unit) {
        for (sample in samples) {
            window[filled++] = sample
            samplesRead++
            if (filled == fftSize) {
                for (i in window.indices) { real[i] = window[i] * weights[i]; imag[i] = 0f }
                fft.forward(real, imag)
                val spectrum = FloatArray(dimension)
                for (k in bins.indices) spectrum[bins[k]] += real[k] * real[k] + imag[k] * imag[k]
                val feature = FloatArray(dimension)
                var norm = 0.0
                for (i in feature.indices) {
                    feature[i] = max(0f, spectrum[i] - previous[i])
                    norm += feature[i].toDouble() * feature[i]
                    previous[i] = spectrum[i]
                }
                if (norm > 1e-30) { val scale = sqrt(norm).toFloat(); for (i in feature.indices) feature[i] /= scale }
                var power = 0.0
                for (x in window) power += x.toDouble() * x
                val db = (10 * log10(max(1e-18, power / fftSize))).toFloat()
                onFrame(feature, samplesRead, db)
                System.arraycopy(window, hopSize, window, 0, fftSize - hopSize)
                filled = fftSize - hopSize
                hop++
            }
        }
    }
}
