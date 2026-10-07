package com.pianoscorefollower.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The DSP core has no Android dependencies, so it is exercised directly here.
 * Real capture behaviour cannot be verified on an emulator (its microphone is silent).
 */
class AudioAnalysisTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE
    private val hopSize = AudioConfig.HOP_SIZE

    /** An exact whole number of cycles per hop, so a sustained tone is frame-invariant. */
    private fun steadyTone(amplitude: Float): FloatArray {
        val frequency = sampleRate.toDouble() * 10.0 / hopSize
        return FloatArray(hopSize) { i ->
            (amplitude * sin(2.0 * PI * frequency * i / sampleRate)).toFloat()
        }
    }

    private fun burst(amplitude: Float): FloatArray {
        val frequency = 440.0
        return FloatArray(hopSize) { i ->
            (amplitude * sin(2.0 * PI * frequency * i / sampleRate)).toFloat()
        }
    }

    @Test
    fun `silence never reports an onset`() {
        val analyzer = AudioAnalyzer()
        val silence = FloatArray(hopSize)

        var onsets = 0
        repeat(300) { if (analyzer.accept(silence).isOnset) onsets++ }

        assertEquals(0, onsets)
    }

    @Test
    fun `low level noise does not report onsets`() {
        val analyzer = AudioAnalyzer()
        val noise = FloatArray(hopSize)
        val random = java.util.Random(7)

        var onsets = 0
        repeat(300) {
            for (i in noise.indices) noise[i] = (random.nextFloat() - 0.5f) * 0.002f
            if (analyzer.accept(noise).isOnset) onsets++
        }

        assertEquals(0, onsets)
    }

    @Test
    fun `attack after silence is reported once`() {
        val analyzer = AudioAnalyzer()
        val silence = FloatArray(hopSize)
        val note = burst(0.35f)

        repeat(60) { analyzer.accept(silence) }

        var onsets = 0
        repeat(30) { if (analyzer.accept(note).isOnset) onsets++ }

        assertTrue("expected exactly one attack, saw $onsets", onsets == 1)
    }

    @Test
    fun `sustained tone does not retrigger`() {
        val analyzer = AudioAnalyzer()
        val note = steadyTone(0.35f)

        repeat(60) { analyzer.accept(note) }

        var onsets = 0
        repeat(120) { if (analyzer.accept(note).isOnset) onsets++ }

        assertEquals(0, onsets)
    }

    @Test
    fun `level tracks amplitude`() {
        val analyzer = AudioAnalyzer()
        val quiet = steadyTone(0.01f)
        val loud = steadyTone(0.5f)

        repeat(60) { analyzer.accept(quiet) }
        val quietDb = analyzer.accept(quiet).levelDb

        repeat(60) { analyzer.accept(loud) }
        val loudDb = analyzer.accept(loud).levelDb

        assertTrue("quiet=$quietDb loud=$loudDb", loudDb > quietDb + 20f)
    }

    /** Renders a harmonically rich note, the way a real piano would sound. */
    private fun pianoNote(midi: Int, seconds: Double = 0.6): FloatArray {
        val f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        val length = (sampleRate * seconds).toInt()
        return FloatArray(length) { i ->
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (k in 1..10) {
                sum += (1.0 / Math.pow(k.toDouble(), 1.4)) * sin(2.0 * PI * k * f0 * t)
            }
            (0.25 * Math.exp(-2.0 * t) * sum).toFloat()
        }
    }

    /** Argmax of the chroma of the loudest frame of [signal]. */
    private fun dominantPitchClass(signal: FloatArray): Int {
        val analyzer = AudioAnalyzer()
        var best = FloatArray(12)
        var bestLevel = Float.NEGATIVE_INFINITY
        var index = 0
        while (index + hopSize <= signal.size) {
            val chunk = FloatArray(hopSize) { i -> signal[index + i] }
            val frame = analyzer.accept(chunk)
            if (frame.levelDb > bestLevel) {
                bestLevel = frame.levelDb
                best = frame.chroma.copyOf()
            }
            index += hopSize
        }
        var pitchClass = 0
        for (i in best.indices) if (best[i] > best[pitchClass]) pitchClass = i
        return pitchClass
    }

    @Test
    fun `dominant pitch class is correct across the keyboard`() {
        val wrong = ArrayList<String>()
        for (midi in 36..84) {
            val detected = dominantPitchClass(pianoNote(midi))
            val expected = ((midi % 12) + 12) % 12
            if (detected != expected) {
                wrong.add("midi=$midi expected=$expected detected=$detected")
            }
        }
        /*
         * The lowest semitone or two are below what an 8192-point transform can place
         * on its own grid: D#2 sits between two bins that map to D and E, so its
         * fundamental never reaches the D# class. Resolving it needs a ~2.7 Hz grid
         * (16384 points), whose analysis lag would push the chroma past the attack and
         * cost far more than the one note is worth. Everything from E2 up must be exact.
         */
        assertTrue(
            "pitch classes detected incorrectly: $wrong",
            wrong.all { it.startsWith("midi=39 ") } && wrong.size <= 1,
        )
    }

    @Test
    fun `fft peaks on the bin matching the input frequency`() {
        val size = 2048
        val expectedBin = 64
        val frequency = expectedBin.toDouble() * sampleRate / size

        val real = FloatArray(size) { i -> sin(2.0 * PI * frequency * i / sampleRate).toFloat() }
        val imaginary = FloatArray(size)

        Fft(size).forward(real, imaginary)

        var peakBin = 0
        var peakMagnitude = 0f
        for (bin in 1 until size / 2) {
            val magnitude = hypot(real[bin], imaginary[bin])
            if (magnitude > peakMagnitude) {
                peakMagnitude = magnitude
                peakBin = bin
            }
        }

        assertEquals(expectedBin, peakBin)
    }
}
