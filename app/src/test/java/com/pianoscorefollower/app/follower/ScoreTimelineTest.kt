package com.pianoscorefollower.app.follower

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTimelineTest {

    private fun event(tick: Long, measure: Int, vararg pitchClasses: Int): ScoreEvent {
        val chroma = FloatArray(12)
        pitchClasses.forEach { chroma[it] = 1f }
        return ScoreEvent(
            tick = tick,
            measure = measure,
            chroma = chroma,
            noteCount = pitchClasses.size,
            isMeasureStart = false,
        )
    }

    private val timeline = ScoreTimeline(
        ticksPerQuarter = 960,
        events = listOf(
            event(0, 0, 0),
            event(960, 0, 2),
            event(1920, 1, 4),
            event(2880, 1, 5),
            event(3840, 2, 7),
            event(4800, 2, 9),
        ),
        endTick = 5760,
        measureCount = 3,
    )

    @Test
    fun `progress is zero at the measure start`() {
        assertEquals(0f, timeline.progressInMeasure(1, 1920), 0.001f)
    }

    @Test
    fun `progress is one at the next measure start`() {
        assertEquals(1f, timeline.progressInMeasure(1, 3840), 0.001f)
    }

    @Test
    fun `progress interpolates inside a measure`() {
        assertEquals(0.5f, timeline.progressInMeasure(1, 2880), 0.001f)
    }

    @Test
    fun `last measure ends at the timeline end`() {
        assertEquals(0f, timeline.progressInMeasure(2, 3840), 0.001f)
        assertEquals(1f, timeline.progressInMeasure(2, 5760), 0.001f)
    }

    @Test
    fun `unknown measures report completion`() {
        assertEquals(1f, timeline.progressInMeasure(-1, 0), 0.001f)
        assertEquals(1f, timeline.progressInMeasure(9, 0), 0.001f)
    }

    @Test
    fun `progress clamps beyond the measure`() {
        assertTrue(timeline.progressInMeasure(0, 10_000) == 1f)
        assertEquals(0f, timeline.progressInMeasure(0, -500), 0.001f)
    }

    @Test
    fun `empty timeline reports completion`() {
        assertEquals(1f, ScoreTimeline.EMPTY.progressInMeasure(0, 0), 0.001f)
    }
}
