package com.pianoscorefollower.app.audio

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * In-place iterative radix-2 complex FFT. [size] must be a power of two.
 * Twiddle factors are precomputed once; no allocation happens per transform.
 */
internal class Fft(val size: Int) {

    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val bitReverse = IntArray(size)

    /** Magnitude of the lower half of the last transform, reused between callers. */
    val magnitude = FloatArray(size / 2)

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        for (i in 0 until size / 2) {
            val angle = -2.0 * Math.PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) {
            bitReverse[i] = Integer.reverse(i) ushr (32 - bits)
        }
    }

    fun forward(real: FloatArray, imag: FloatArray) {
        for (i in 0 until size) {
            val j = bitReverse[i]
            if (j > i) {
                var swap = real[i]; real[i] = real[j]; real[j] = swap
                swap = imag[i]; imag[i] = imag[j]; imag[j] = swap
            }
        }

        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var blockStart = 0
            while (blockStart < size) {
                var j = blockStart
                var twiddle = 0
                while (j < blockStart + half) {
                    val k = j + half
                    val tRe = real[k] * cosTable[twiddle] - imag[k] * sinTable[twiddle]
                    val tIm = real[k] * sinTable[twiddle] + imag[k] * cosTable[twiddle]
                    real[k] = real[j] - tRe
                    imag[k] = imag[j] - tIm
                    real[j] += tRe
                    imag[j] += tIm
                    j++
                    twiddle += step
                }
                blockStart += length
            }
            length = length shl 1
        }

        for (bin in magnitude.indices) {
            magnitude[bin] = hypot(real[bin], imag[bin])
        }
    }
}
