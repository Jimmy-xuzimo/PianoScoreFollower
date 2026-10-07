package com.pianoscorefollower.app.image

import kotlin.math.roundToInt

/**
 * Analytic geometry of the page stack.
 *
 * The scroll bar has to know the total travel distance before the list is laid
 * out — the playback speed is derived from it, and the progress slider maps onto
 * it — so the page heights are computed from the viewport width and each page's
 * aspect ratio instead of being measured. Kept free of Android types so the maths
 * can be unit tested.
 */
class PageLayout(
    /** Width divided by height for each page, in display order. */
    private val aspects: List<Float>,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val gapPx: Int,
) {

    val itemHeights: IntArray = IntArray(aspects.size) { index ->
        val aspect = aspects[index]
        if (viewportWidth <= 0 || aspect <= 0f) 0
        else (viewportWidth / aspect).roundToInt().coerceAtLeast(1)
    }

    /** Pixel offset of the top of each page within the content. */
    val starts: IntArray = IntArray(aspects.size).also { starts ->
        var cursor = 0
        for (index in aspects.indices) {
            starts[index] = cursor
            cursor += itemHeights[index] + gapPx
        }
    }

    val contentHeight: Int =
        if (aspects.isEmpty()) 0 else starts.last() + itemHeights.last()

    /** Distance the content can travel; zero when everything already fits. */
    val maxScroll: Int = (contentHeight - viewportHeight).coerceAtLeast(0)

    /** True once the viewport has been measured and there is something to lay out. */
    val isReady: Boolean get() = viewportWidth > 0 && viewportHeight > 0 && aspects.isNotEmpty()

    val pageCount: Int get() = aspects.size

    /** Converts an absolute pixel offset into a `(pageIndex, offsetWithinPage)` pair. */
    fun itemAt(offset: Int): Pair<Int, Int> {
        if (aspects.isEmpty()) return 0 to 0
        val clamped = offset.coerceIn(0, maxScroll)
        for (index in aspects.indices) {
            val pageEnd = starts[index] + itemHeights[index]
            if (clamped < pageEnd + gapPx || index == aspects.lastIndex) {
                val within = (clamped - starts[index]).coerceIn(0, maxOf(0, itemHeights[index] - 1))
                return index to within
            }
        }
        return aspects.lastIndex to 0
    }

    /** Scroll offset that puts page [index] at the top of the viewport. */
    fun startOf(index: Int): Int =
        if (index in starts.indices) starts[index].coerceIn(0, maxScroll) else 0
}
