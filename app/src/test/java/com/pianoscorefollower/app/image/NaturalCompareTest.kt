package com.pianoscorefollower.app.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Photographed pages are ordered by name, so `scan2` must beat `scan10`. */
class NaturalCompareTest {

    @Test
    fun `embedded numbers compare numerically`() {
        assertTrue(naturalCompare("page2.jpg", "page10.jpg") < 0)
        assertTrue(naturalCompare("page10.jpg", "page2.jpg") > 0)
        assertTrue(naturalCompare("scan1", "scan002") < 0)
    }

    @Test
    fun `case and prefix differences do not affect ordering`() {
        assertEquals(0, naturalCompare("Page3.png", "page3.png"))
        assertTrue(naturalCompare("a", "a1") < 0)
    }

    @Test
    fun `identical names compare equal`() {
        assertEquals(0, naturalCompare("score.musicxml", "score.musicxml"))
    }
}
