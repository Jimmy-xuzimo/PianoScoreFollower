package com.pianofollower.follower

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreFollowerTest {

    /** Realistic wall clock: the follower treats 0 as "no match yet". */
    private val base = 1_700_000_000_000L

    private fun oneHot(pitchClass: Int): FloatArray = FloatArray(12).also { it[pitchClass] = 1f }

    /** Unit-length chroma holding every listed pitch class. */
    private fun chord(vararg pitchClasses: Int): FloatArray {
        val chroma = FloatArray(12)
        for (pitchClass in pitchClasses) chroma[pitchClass] = 1f
        var sumSquares = 0f
        for (value in chroma) sumSquares += value * value
        val inverse = 1f / kotlin.math.sqrt(sumSquares)
        for (i in chroma.indices) chroma[i] *= inverse
        return chroma
    }

    /** Three events one quarter apart: C4, E4, G4 in measures 0, 1, 2. */
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

    private fun follower(): ScoreFollower = ScoreFollower().also {
        it.load(timeline())
        it.setTempo(60f)
    }

    @Test
    fun `matching onsets advance the cursor and accumulate confidence`() {
        val follower = follower()

        assertTrue(follower.onFrame(oneHot(0), isOnset = true, nowMs = base))
        assertEquals(0, follower.currentMeasure)

        assertTrue(follower.onFrame(oneHot(4), isOnset = true, nowMs = base + 100))
        assertEquals(1, follower.currentMeasure)

        assertTrue(follower.onFrame(oneHot(7), isOnset = true, nowMs = base + 200))
        assertEquals(2, follower.currentMeasure)
        assertEquals(2, follower.currentIndex)

        // 3 accepted onsets at 0.35 each saturate the accumulator at 1.
        assertEquals(1f, follower.confidenceScore, 1e-3f)
    }

    @Test
    fun `sustained frames do not move the cursor`() {
        val follower = follower()
        follower.onFrame(oneHot(0), isOnset = true, nowMs = base)

        repeat(20) { frame ->
            assertFalse(follower.onFrame(oneHot(0), isOnset = false, nowMs = base + 10L * frame))
        }
        assertEquals(0, follower.currentIndex)
    }

    @Test
    fun `an onset matching nothing lowers confidence`() {
        val follower = follower()
        follower.onFrame(oneHot(0), isOnset = true, nowMs = base)
        val afterMatch = follower.confidenceScore

        // Pitch class 1 is not in the score, so nothing in the window is credible.
        assertFalse(follower.onFrame(oneHot(1), isOnset = true, nowMs = base + 10))
        assertTrue(follower.confidenceScore < afterMatch)
        // The cursor stays put rather than jumping to a wrong bar.
        assertEquals(0, follower.currentIndex)
    }

    @Test
    fun `silence bleeds confidence away over wall-clock time`() {
        val follower = follower()
        follower.onFrame(oneHot(0), isOnset = true, nowMs = base)
        follower.onFrame(oneHot(4), isOnset = true, nowMs = base)
        val peak = follower.confidenceScore

        // Four decay time constants (2.5 s each) leaves ~2% of the original value.
        follower.onFrame(oneHot(4), isOnset = false, nowMs = base + 10_000)

        assertTrue(follower.confidenceScore < peak * 0.1f)
    }

    @Test
    fun `predicted tick never races past the next event`() {
        val follower = follower()
        follower.onFrame(oneHot(0), isOnset = true, nowMs = base)
        follower.onOnsetMatched(base)

        // 60 bpm, 960 ticks per quarter: a quarter lasts a second. Asking a minute
        // later must still be capped short of the next event (60% of the gap).
        assertEquals(576L, follower.predictedTick(base + 60_000))
    }

    @Test
    fun `predicted tick tracks the player between onsets`() {
        val follower = follower()
        follower.onFrame(oneHot(0), isOnset = true, nowMs = base)
        follower.onOnsetMatched(base)

        // 0.25 s at 60 bpm is a quarter of a quarter note, i.e. 240 ticks. The
        // seconds-per-tick division is float, so allow a tick of rounding either way.
        val predicted = follower.predictedTick(base + 250)
        assertTrue("expected ~240 ticks but was $predicted", predicted in 238L..240L)
    }

    @Test
    fun `empty timeline reports not ready and ignores frames`() {
        val follower = ScoreFollower()
        follower.load(ScoreTimeline.EMPTY)

        assertFalse(follower.isReady)
        assertFalse(follower.onFrame(oneHot(0), isOnset = true, nowMs = base))
        assertEquals(0L, follower.predictedTick(base + 1000))
    }

    /** A four-note chord in measure 0, then a single D in measure 1. */
    private fun chordTimeline(): ScoreTimeline = ScoreTimeline(
        ticksPerQuarter = 960,
        events = listOf(
            ScoreEvent(tick = 0L, measure = 0, chroma = chord(0, 4, 7, 11), noteCount = 4, isMeasureStart = true),
            ScoreEvent(tick = 960L, measure = 1, chroma = oneHot(2), noteCount = 1, isMeasureStart = true),
        ),
        endTick = 1920L,
        measureCount = 2,
    )

    @Test
    fun `coverage counts only pitch classes the event contains`() {
        val target = chord(0, 4, 7, 11)

        assertEquals(1f, chromaCoverage(oneHot(0), target), 1e-3f)
        assertEquals(0f, chromaCoverage(oneHot(1), target), 1e-3f)

        val halfOutside = FloatArray(12).also { it[0] = 1f; it[1] = 1f }
        assertEquals(0.5f, chromaCoverage(halfOutside, target), 1e-3f)
    }

    @Test
    fun `playing one note of a chord still advances the cursor`() {
        val follower = ScoreFollower().also {
            it.load(chordTimeline())
            it.setTempo(60f)
        }

        // Only the C of the C-E-G-B chord sounds. Cosine alone scores this at
        // 1/sqrt(4) = 0.5, below the accept threshold, so the cursor used to stall —
        // the "I played the note and nothing happened" case. Coverage has to carry it.
        assertTrue(follower.onFrame(oneHot(0), isOnset = true, nowMs = base))
        assertEquals(0, follower.currentMeasure)
        // 0.5 * cosine(0.5) + 0.5 * coverage(1.0)
        assertEquals(0.75f, follower.similarity, 1e-3f)
    }

    @Test
    fun `a note outside every event does not advance the cursor`() {
        val follower = ScoreFollower().also {
            it.load(chordTimeline())
            it.setTempo(60f)
        }

        // C# belongs to neither event, so nothing in the window is credible.
        assertFalse(follower.onFrame(oneHot(1), isOnset = true, nowMs = base))
        assertEquals(0, follower.currentIndex)
    }
}
