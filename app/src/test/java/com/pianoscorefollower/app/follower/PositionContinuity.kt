package com.pianoscorefollower.app.follower

import kotlin.math.sign

internal data class MovementEvidence(val index: Int, val time: Long, val count: Int, val began: Long)

/** Observe hypotheses before they become strong enough to commit. This lets
 * sequence alignment and jump confirmation share the same causal evidence. */
internal class MovementSequence {
    private var pending=-1
    private var count=0
    private var began=0L
    private var observed=Long.MIN_VALUE
    fun reset() { pending=-1;count=0;began=0;observed=Long.MIN_VALUE }
    fun observe(to: Int,time: Long,fit: Float, minimumFit: Float = .5f): MovementEvidence {
        if(fit<minimumFit || to<0) { reset();return MovementEvidence(to,time,0,time) }
        if(pending<0 || to<pending || to>pending+4 || time-observed>8000) {
            pending=to;count=1;began=time
        } else if(to>pending && time>observed) { count++;pending=to }
        observed=time
        return MovementEvidence(to,time,count,began)
    }
}

/** Hysteresis for discontinuous hypotheses. Repeated evidence for the same held
 * note is not a sequence and cannot authorize a jump. Explicit seeks bypass it. */
internal class PositionContinuity {
    private val sequence=MovementSequence()
    private var lastJumpAt = Long.MIN_VALUE
    private var lastDirection = 0

    fun reset() { sequence.reset();lastJumpAt=Long.MIN_VALUE;lastDirection=0 }
    fun clearPending() { sequence.reset() }

    fun allow(from: Int, to: Int, time: Long, discontinuous: Boolean, fit: Float, prior: MovementEvidence? = null,
        minimumFit: Float = .5f, requiredEvidence: Int = 3): Boolean {
        if(!discontinuous) { clearPending();return true }
        if(fit<minimumFit) { clearPending();return false }
        val local=sequence.observe(to,time,fit,minimumFit)
        val evidence=prior?.takeIf { it.index==to && it.time==time && it.count>local.count } ?: local
        val direction=(to-from).sign
        val reversing=lastJumpAt!=Long.MIN_VALUE && time-lastJumpAt<1500 && direction!=lastDirection
        val needed=if(reversing) maxOf(5,requiredEvidence) else requiredEvidence
        if(evidence.count<needed || time-evidence.began<180) return false
        lastJumpAt=time;lastDirection=direction;clearPending();return true
    }
}
