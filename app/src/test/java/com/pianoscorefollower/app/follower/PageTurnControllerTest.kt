package com.pianoscorefollower.app.follower

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnControllerTest {

    private val config = PageTurnConfig(minConfidence = 0.5f, advanceAtProgress = 0.5f)

    private fun controller() = PageTurnController(config)

    @Test
    fun `does not turn while the page still has measures left`() {
        val controller = controller()
        val decision = controller.onCursor(
            cursorPage = 0,
            cursorMeasure = 3,
            pageCount = 3,
            isLastMeasureOfPage = false,
            measureProgress = 1f,
            confidence = 1f,
        )
        assertEquals(PageTurnReason.NONE, decision.reason)
    }

    @Test
    fun `does not turn before the trailing measure is mostly played`() {
        val controller = controller()
        val decision = controller.onCursor(
            cursorPage = 0,
            cursorMeasure = 7,
            pageCount = 3,
            isLastMeasureOfPage = true,
            measureProgress = 0.2f,
            confidence = 1f,
        )
        assertEquals(PageTurnReason.NONE, decision.reason)
    }

    @Test
    fun `does not turn on low confidence`() {
        val controller = controller()
        val decision = controller.onCursor(
            cursorPage = 0,
            cursorMeasure = 7,
            pageCount = 3,
            isLastMeasureOfPage = true,
            measureProgress = 1f,
            confidence = 0.2f,
        )
        assertEquals(PageTurnReason.NONE, decision.reason)
    }

    @Test
    fun `advances once the trailing measure is nearly done`() {
        val controller = controller()
        val decision = controller.onCursor(
            cursorPage = 1,
            cursorMeasure = 12,
            pageCount = 4,
            isLastMeasureOfPage = true,
            measureProgress = 0.9f,
            confidence = 0.8f,
        )
        assertEquals(PageTurnReason.ADVANCE, decision.reason)
        assertEquals(2, decision.targetPage)
    }

    @Test
    fun `advances only once per page`() {
        val controller = controller()
        repeat(5) {
            controller.onCursor(1, 12, 4, isLastMeasureOfPage = true, measureProgress = 0.9f, confidence = 0.9f)
        }
        val again = controller.onCursor(
            cursorPage = 1,
            cursorMeasure = 12,
            pageCount = 4,
            isLastMeasureOfPage = true,
            measureProgress = 1f,
            confidence = 1f,
        )
        assertEquals(PageTurnReason.NONE, again.reason)
    }

    @Test
    fun `rewinds when the player jumps back to an earlier page`() {
        val controller = controller()
        controller.onCursor(1, 12, 4, isLastMeasureOfPage = true, measureProgress = 0.9f, confidence = 0.9f)

        // Still on the trailing measure of page 1 after the turn: no second turn.
        assertEquals(
            PageTurnReason.NONE,
            controller.onCursor(1, 12, 4, isLastMeasureOfPage = true, measureProgress = 1f, confidence = 1f).reason,
        )

        // Player restarts: the cursor lands back on measure 0, page 0.
        val rewind = controller.onCursor(0, 0, 4, isLastMeasureOfPage = false, measureProgress = 0f, confidence = 0.9f)
        assertEquals(PageTurnReason.REWIND, rewind.reason)
        assertEquals(0, rewind.targetPage)
    }

    @Test
    fun `turns again after a rewind`() {
        val controller = controller()
        controller.onCursor(1, 12, 4, isLastMeasureOfPage = true, measureProgress = 0.9f, confidence = 0.9f)
        controller.onCursor(0, 0, 4, isLastMeasureOfPage = false, measureProgress = 0f, confidence = 0.9f)

        val again = controller.onCursor(
            cursorPage = 1,
            cursorMeasure = 12,
            pageCount = 4,
            isLastMeasureOfPage = true,
            measureProgress = 0.9f,
            confidence = 0.9f,
        )
        assertEquals(PageTurnReason.ADVANCE, again.reason)
    }

    @Test
    fun `manual scroll is not undone by the cursor`() {
        val controller = controller()
        controller.onManualPage(3)

        val decision = controller.onCursor(
            cursorPage = 1,
            cursorMeasure = 12,
            pageCount = 5,
            isLastMeasureOfPage = false,
            measureProgress = 0.1f,
            confidence = 0.9f,
        )
        assertEquals(PageTurnReason.NONE, decision.reason)
    }

    @Test
    fun `manual navigation re-arms the trailing measure`() {
        val controller = controller()
        controller.onCursor(1, 12, 4, isLastMeasureOfPage = true, measureProgress = 0.9f, confidence = 0.9f)
        controller.onManualPage(1)

        val decision = controller.onCursor(
            cursorPage = 1,
            cursorMeasure = 12,
            pageCount = 4,
            isLastMeasureOfPage = true,
            measureProgress = 0.9f,
            confidence = 0.9f,
        )
        assertEquals(PageTurnReason.ADVANCE, decision.reason)
    }

    @Test
    fun `never turns past the final page`() {
        val controller = controller()
        val decision = controller.onCursor(
            cursorPage = 3,
            cursorMeasure = 40,
            pageCount = 4,
            isLastMeasureOfPage = true,
            measureProgress = 1f,
            confidence = 1f,
        )
        assertEquals(PageTurnReason.NONE, decision.reason)
    }

    @Test
    fun `single page scores never turn`() {
        val controller = controller()
        val decision = controller.onCursor(
            cursorPage = 0,
            cursorMeasure = 0,
            pageCount = 1,
            isLastMeasureOfPage = true,
            measureProgress = 1f,
            confidence = 1f,
        )
        assertEquals(PageTurnReason.NONE, decision.reason)
    }
}
