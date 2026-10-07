package com.pianoscorefollower.app.audio

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Folds a spectrum into 12 pitch classes.
 *
 * Runs its own transform on [AudioConfig.CHROMA_FFT_SIZE] samples rather than reusing
 * the onset detector's short window: pitch resolution and attack timing pull in
 * opposite directions, and the short window leaves everything below the treble on the
 * wrong semitone.
 *
 * Only bins inside the piano's range contribute. Magnitudes are square-root compressed
 * before summing, otherwise a loud low fundamental dominates every frame and the chroma
 * stops discriminating between chords. Energy below a fraction of the loudest bin is
 * dropped as leakage, so a quiet room does not pad every pitch class equally. The
 * result is L2 normalised, making similarity independent of playing volume.
 */
internal class ChromaExtractor(
    sampleRate: Int = AudioConfig.SAMPLE_RATE,
    private val fftSize: Int = AudioConfig.CHROMA_FFT_SIZE,
) {

    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5 - 0.5 * cos(2.0 * Math.PI * i / (fftSize - 1))).toFloat()
    }
    private val real = FloatArray(fftSize)
    private val imaginary = FloatArray(fftSize)
    private val binPitchClass = IntArray(fftSize / 2) { -1 }
    private val binWeight = FloatArray(fftSize / 2)
    private val chroma = FloatArray(PITCH_CLASSES)

    init {
        for (bin in 1 until fftSize / 2) {
            val frequency = bin * sampleRate.toDouble() / fftSize
            if (frequency < MIN_FREQUENCY_HZ || frequency > MAX_FREQUENCY_HZ) {
                continue
            }
            val midi = 69.0 + 12.0 * ln(frequency / 440.0) / ln(2.0)
            binPitchClass[bin] = ((Math.round(midi).toInt() % PITCH_CLASSES) + PITCH_CLASSES) % PITCH_CLASSES
            /*
             * Piano partials fall off roughly as 1/f, so without this a note's third
             * or fifth harmonic outweighs its own fundamental and the pitch class is
             * decided by whichever partial happens to be loudest — the bass then
             * reports a fifth above what was played. Lifting low frequencies back up
             * keeps the fundamental in charge of the pitch class it names.
             */
            binWeight[bin] = sqrt(REFERENCE_HZ / frequency)
                .toFloat()
                .coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        }
    }

    /** Magnitude spectrum of the last [extract] call; valid until the next one. */
    val spectrum: FloatArray
        get() = fft.magnitude

    /**
     * [samples] must hold exactly [fftSize] samples. Returns the internal buffer, which
     * is overwritten on the next call.
     */
    fun extract(samples: FloatArray): FloatArray {
        for (i in 0 until fftSize) {
            real[i] = samples[i] * window[i]
            imaginary[i] = 0f
        }
        fft.forward(real, imaginary)

        val magnitude = fft.magnitude
        var peak = 0f
        for (bin in 1 until fftSize / 2) {
            if (binPitchClass[bin] >= 0 && magnitude[bin] > peak) peak = magnitude[bin]
        }
        val floor = peak * FLOOR_RATIO

        java.util.Arrays.fill(chroma, 0f)
        for (bin in 1 until fftSize / 2) {
            val pitchClass = binPitchClass[bin]
            if (pitchClass < 0) continue
            val excess = magnitude[bin] - floor
            if (excess <= 0f) continue
            chroma[pitchClass] += sqrt(excess) * binWeight[bin]
        }

        /*
         * Strip the broadband pedestal before normalising. Window leakage, room tone and
         * pedal resonance lift every pitch class by roughly the same amount, and it is
         * that common offset — not the note itself — that dominates the sum. Leaving it
         * in halves the coverage of a correctly played note and pushes a real match down
         * to the accept threshold, which is the "I played it and nothing happened" case.
         * Subtracting the mean turns the vector into a contrast reading: one note comes
         * out nearly one-hot, a chord keeps all of its members, and noise collapses to
         * zero instead of masquerading as twelve weak notes.
         */
        var mean = 0f
        for (value in chroma) mean += value
        mean /= PITCH_CLASSES

        var sumSquares = 0f
        for (i in chroma.indices) {
            val contrast = (chroma[i] - mean).coerceAtLeast(0f)
            chroma[i] = contrast
            sumSquares += contrast * contrast
        }
        if (sumSquares > 0f) {
            val inverse = 1f / sqrt(sumSquares)
            for (i in chroma.indices) chroma[i] *= inverse
        }

        return chroma
    }

    private companion object {
        const val PITCH_CLASSES = 12

        /** Roughly A1..C7 — wide enough for piano writing, narrow enough to skip rumble. */
        const val MIN_FREQUENCY_HZ = 55.0
        const val MAX_FREQUENCY_HZ = 2093.0

        /** Bins quieter than this fraction of the loudest one are window leakage. */
        const val FLOOR_RATIO = 1e-3f

        /** Frequency whose partials are neither boosted nor attenuated. */
        const val REFERENCE_HZ = 440.0
        const val MIN_WEIGHT = 0.35f
        const val MAX_WEIGHT = 3.0f
    }
}
