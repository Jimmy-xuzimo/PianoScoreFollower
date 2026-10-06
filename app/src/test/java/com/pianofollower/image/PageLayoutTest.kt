package com.pianofollower.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scroll player derives its speed from [PageLayout] rather than from measured
 * positions, so these numbers decide how long the score actually takes to pass.
 */
class PageLayoutTest {

    @Test
    fun `page heights follow the viewport width and aspect ratio`() {
        val layout = PageLayout(
            aspects = listOf(1f, 0.5f),
            viewportWidth = 1000,
            viewportHeight = 800,
            gapPx = 20,
        )

        assertArrayEquals(intArrayOf(1000, 2000), layout.itemHeights)
        assertArrayEquals(intArrayOf(0, 1020), layout.starts)
        assertEquals(3020, layout.contentHeight)
        assertEquals(2220, layout.maxScroll)
    }

    @Test
    fun `an offset resolves to the page that owns it`() {
        val layout = PageLayout(listOf(1f, 0.5f), viewportWidth = 1000, viewportHeight = 800, gapPx = 20)

        assertEquals(0 to 0, layout.itemAt(0))
        assertEquals(0 to 500, layout.itemAt(500))
        // The first pixel of page two is the gap boundary, not the tail of page one.
        assertEquals(1 to 0, layout.itemAt(1020))
        assertEquals(1 to 100, layout.itemAt(1120))
        // Past the end the offset saturates at the last page.
        assertEquals(1 to 1200, layout.itemAt(5000))
    }

    @Test
    fun `a stack that fits on one screen cannot scroll`() {
        val layout = PageLayout(listOf(2f), viewportWidth = 1000, viewportHeight = 800, gapPx = 20)

        assertEquals(0, layout.maxScroll)
        assertFalse(layout.maxScroll > 0)
        assertEquals(0 to 0, layout.itemAt(400))
    }

    @Test
    fun `layout waits for the viewport to be measured`() {
        val layout = PageLayout(listOf(1f), viewportWidth = 0, viewportHeight = 0, gapPx = 0)

        assertFalse(layout.isReady)
        assertEquals(0, layout.maxScroll)
    }

    @Test
    fun `start of page is clamped into the scrollable range`() {
        val layout = PageLayout(listOf(1f, 1f), viewportWidth = 1000, viewportHeight = 3000, gapPx = 0)

        assertEquals(0, layout.maxScroll)
        assertEquals(0, layout.startOf(1))
        assertTrue(layout.isReady)
    }

    @Test
    fun `an empty stack is inert`() {
        val layout = PageLayout(emptyList(), viewportWidth = 1000, viewportHeight = 800, gapPx = 20)

        assertEquals(0, layout.pageCount)
        assertEquals(0, layout.maxScroll)
        assertEquals(0 to 0, layout.itemAt(0))
    }
}
