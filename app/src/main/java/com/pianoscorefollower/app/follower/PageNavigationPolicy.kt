package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.FollowPosition
import com.pianoscorefollower.app.performance.FollowStatus

/** Only confirmed musical positions may move the visible score. */
object PageNavigationPolicy {
    fun canFollow(position: FollowPosition, automatic: Boolean, browsing: Boolean): Boolean =
        automatic && !browsing && position.status == FollowStatus.FOLLOWING && position.confidence >= 0.5f
}
