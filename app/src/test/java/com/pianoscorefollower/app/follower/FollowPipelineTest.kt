package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.audio.AudioAnalyzer
import com.pianoscorefollower.app.audio.AudioConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end check of the microphone path: a synthesised piano performance is pushed
 * through the same DSP the capture thread uses and into the follower, and the cursor
 * must walk the score. The emulator's microphone is silent, so this is the only place
 * the alignment behaviour can be verified without hardware.
 */
class FollowPipelineTest {

    private val sampleRate = AudioConfig.SAMPLE_RATE
    private val hop = AudioConfig.HOP_SIZE

    /** Milliseconds of audio covered by one analysis hop. */
    private val hopMs = hop * 1000L / sampleRate

    private fun oneHot(pitchClass: Int): FloatArray = FloatArray(12).also { it[pitchClass] = 1f }

    /**
     * Renders a continuous performance: each note gets a decaying harmonic stack and a
     * short gap after it, so the onset detector sees a real attack rather than a
     * steady tone. Continuous phase matters — slicing a repeating buffer would smear
     * the spectrum across pitch classes and invalidate the measurement.
     */
    private fun perform(notes: List<Double>): FloatArray {
        val hold = sampleRate / 3
        val gap = sampleRate / 6
        // The room has to be heard before the first note: the noise floor is seeded
        // from the opening frames, and a signal that starts mid-note would look like
        // a room that is already that loud.
        val lead = sampleRate / 2
        val signal = FloatArray(lead + (hold + gap) * notes.size)

        notes.forEachIndexed { index, f0 ->
            val start = lead + index * (hold + gap)
            for (i in 0 until hold) {
                val t = i.toDouble() / sampleRate
                val envelope = Math.exp(-4.0 * t)
                var sum = 0.0
                for (k in 1..8) {
                    val amplitude = 1.0 / Math.pow(k.toDouble(), 1.4)
                    sum += amplitude * Math.sin(2.0 * Math.PI * k * f0 * t)
                }
                signal[start + i] = (0.22 * envelope * sum).toFloat()
            }
        }
        return signal
    }

    private fun timeline(): ScoreTimeline = ScoreTimeline(
        ticksPerQuarter = 960,
        events = listOf(
            ScoreEvent(tick = 0L, measure = 0, chroma = oneHot(0), noteCount = 1, isMeasureStart = true),
            ScoreEvent(tick = 960L, measure = 1, chroma = oneHot(4), noteCount = 1, isMeasureStart = true),
            ScoreEvent(tick = 1920L, measure = 2, chroma = oneHot(7), noteCount = 1, isMeasureStart = true),
        ),
        endTick = 2880L,
        measureCount = 3,
    )

    /** Drives the analyzer over [signal] and reports the measures the follower reached. */
    private fun follow(signal: FloatArray): List<Int> {
        val analyzer = AudioAnalyzer()
        val follower = ScoreFollower().also {
            it.load(timeline())
            it.setTempo(100f)
        }

        val visited = ArrayList<Int>()
        val base = 1_700_000_000_000L
        val hopCount = signal.size / hop

        for (index in 0 until hopCount) {
            val chunk = FloatArray(hop) { i -> signal[index * hop + i] }
            val frame = analyzer.accept(chunk)
            val nowMs = base + index * hopMs

            if (follower.onFrame(frame.chroma, frame.isOnset, nowMs)) {
                follower.onOnsetMatched(nowMs)
                if (visited.lastOrNull() != follower.currentMeasure) {
                    visited.add(follower.currentMeasure)
                }
            }
        }
        return visited
    }

    @Test
    fun `a played C-E-G performance walks the cursor through the score`() {
        // C4, E4, G4 -> pitch classes 0, 4, 7, matching the score events in order.
        val signal = perform(listOf(261.6256, 329.6276, 391.9954))

        val visited = follow(signal)

        assertTrue("cursor never moved past the first event: $visited", visited.size >= 3)
        assertEquals(listOf(0, 1, 2), visited.take(3))
    }

    @Test
    fun `forward search stays inside its window when the opening note is wrong`() {
        // A twelve-event score whose only G sits at the very end. Playing G over and
        // over lets the follower look ahead, but the window must keep it from running
        // away to the final event.
        val analyzer = AudioAnalyzer()
        val events = (0 until 12).map { index ->
            ScoreEvent(
                tick = index * 960L,
                measure = index,
                chroma = oneHot(index),
                noteCount = 1,
                isMeasureStart = true,
            )
        }
        val follower = ScoreFollower().also {
            it.load(ScoreTimeline(960, events, endTick = 12 * 960L, measureCount = 12))
            it.setTempo(100f)
        }

        val base = 1_700_000_000_000L
        val signal = perform(listOf(391.9954, 391.9954, 391.9954, 391.9954))
        for (index in 0 until signal.size / hop) {
            val chunk = FloatArray(hop) { i -> signal[index * hop + i] }
            val frame = analyzer.accept(chunk)
            val nowMs = base + index * hopMs
            if (follower.onFrame(frame.chroma, frame.isOnset, nowMs)) {
                follower.onOnsetMatched(nowMs)
            }
        }

        // G is pitch class 7, so the best in-window match is event 7 — never event 11.
        assertEquals(7, follower.currentMeasure)
    }

    @Test
    fun `silence alone never moves the cursor`() {
        val visited = follow(FloatArray(sampleRate))

        assertTrue("cursor moved on silence: $visited", visited.isEmpty())
    }
}
