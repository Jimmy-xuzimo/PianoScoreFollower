package com.pianoscorefollower.app.follower

enum class PageTurnReason { NONE, ADVANCE, REWIND }

data class PageTurnDecision(
    val reason: PageTurnReason,
    /** Page the viewer should display; -1 when no action is required. */
    val targetPage: Int,
) {
    companion object {
        val NONE = PageTurnDecision(PageTurnReason.NONE, -1)
    }
}

/**
 * Decides when the viewer should move to another page.
 *
 * Turning exactly on the first beat of the last measure would hide that measure
 * while it is still being played, so the advance waits until the measure is mostly
 * finished. Confidence gates the decision because a wrong turn is far more
 * disruptive than a late one.
 *
 * Backwards moves are judged on the cursor's *measure*, not its page: a manual
 * scroll changes the displayed page but not the measure, and must not be undone.
 * The controller is stateful and driven once per analysis frame.
 */
class PageTurnController(
    private val config: PageTurnConfig = PageTurnConfig(),
) {

    private var displayedPage = 0
    private var lastCursorMeasure = -1

    /** Page whose trailing measure has already been consumed, so we turn only once. */
    private var advancedFromPage = -1

    fun reset() {
        displayedPage = 0
        lastCursorMeasure = -1
        advancedFromPage = -1
    }

    /** Keeps the machine in sync when the user pages by hand. */
    fun onManualPage(page: Int) {
        if (page < 0) return
        displayedPage = page
        advancedFromPage = -1
    }

    /**
     * @param cursorPage page holding the cursor's current measure
     * @param cursorMeasure zero-based measure the cursor is on
     * @param pageCount total pages reported by the viewer
     * @param isLastMeasureOfPage whether that measure ends its page
     * @param measureProgress how far the player is through that measure, 0..1
     * @param confidence follower confidence, 0..1
     */
    fun onCursor(
        cursorPage: Int,
        cursorMeasure: Int,
        pageCount: Int,
        isLastMeasureOfPage: Boolean,
        measureProgress: Float,
        confidence: Float,
    ): PageTurnDecision {
        if (pageCount <= 1 || cursorPage < 0 || cursorPage >= pageCount) {
            return PageTurnDecision.NONE
        }

        val previousMeasure = lastCursorMeasure
        lastCursorMeasure = cursorMeasure

        // The player jumped backwards through the score (restart, repeat, or a
        // correction): follow them instead of leaving them on the wrong page.
        if (previousMeasure >= 0 && cursorMeasure < previousMeasure && cursorPage < displayedPage) {
            displayedPage = cursorPage
            advancedFromPage = -1
            return PageTurnDecision(PageTurnReason.REWIND, cursorPage)
        }

        if (!isLastMeasureOfPage) return PageTurnDecision.NONE
        if (cursorPage == advancedFromPage) return PageTurnDecision.NONE
        if (cursorPage >= pageCount - 1) return PageTurnDecision.NONE
        if (confidence < config.minConfidence) return PageTurnDecision.NONE
        if (measureProgress < config.advanceAtProgress) return PageTurnDecision.NONE

        advancedFromPage = cursorPage
        displayedPage = cursorPage + 1
        return PageTurnDecision(PageTurnReason.ADVANCE, cursorPage + 1)
    }
}

data class PageTurnConfig(
    /** Confidence below which a page turn is considered too risky. */
    val minConfidence: Float = 0.45f,
    /** Fraction of the trailing measure that must be played before turning. */
    val advanceAtProgress: Float = 0.55f,
)
