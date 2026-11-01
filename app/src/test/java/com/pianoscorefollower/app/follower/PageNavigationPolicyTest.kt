package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.*
import org.junit.Assert.*
import org.junit.Test

class PageNavigationPolicyTest {
    private val certain = FollowPosition(confidence=0.9f,status=FollowStatus.FOLLOWING)
    @Test fun manualBrowsingAndDisabledNavigationAlwaysWin() {
        assertFalse(PageNavigationPolicy.canFollow(certain,true,true))
        assertFalse(PageNavigationPolicy.canFollow(certain,false,false))
        assertTrue(PageNavigationPolicy.canFollow(certain,true,false))
    }
    @Test fun silenceLossAndAmbiguousLocationsCannotTurnPages() {
        for (s in listOf(FollowStatus.WAITING,FollowStatus.LOST,FollowStatus.LOCATING))
            assertFalse(PageNavigationPolicy.canFollow(certain.copy(status=s),true,false))
        assertFalse(PageNavigationPolicy.canFollow(certain.copy(confidence=0.3f),true,false))
    }
}
