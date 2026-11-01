package com.pianoscorefollower.app.follower

import org.junit.Assert.*
import org.junit.Test

class PositionContinuityTest {
    @Test fun longJumpsNeedLongerContextThanNearbyCorrections() {
        val gate=PositionContinuity()
        repeat(5) { assertFalse(gate.allow(100,200+it,1000+it*200L,true,.8f,requiredEvidence=6)) }
        assertTrue(gate.allow(100,205,2000,true,.8f,requiredEvidence=6))
    }
    @Test fun acousticEvidenceUsesTheSameAcceptanceThresholdAsTheAudioMatcher() {
        val gate=PositionContinuity()
        assertFalse(gate.allow(100,20,1000,true,.42f,minimumFit=.38f))
        assertFalse(gate.allow(100,21,1200,true,.46f,minimumFit=.38f))
        assertTrue(gate.allow(100,22,1400,true,.43f,minimumFit=.38f))
        gate.reset();repeat(6){assertFalse(gate.allow(100,20+it,1000+it*200L,true,.42f))}
    }
    @Test fun recoveryCanReuseAlreadyObservedSequenceButCannotReuseStaleEvidence() {
        val sequence=MovementSequence();val gate=PositionContinuity()
        sequence.observe(20,1000,.9f);sequence.observe(21,1200,.9f)
        val evidence=sequence.observe(22,1400,.9f)
        assertTrue(gate.allow(100,22,1400,true,.9f,evidence))
        gate.reset()
        assertFalse(gate.allow(100,22,1500,true,.9f,evidence))
    }
    @Test fun isolatedWrongLocationsDoNotMoveTheConfirmedAnchor() {
        val p=PositionContinuity()
        assertFalse(p.allow(100,20,1000,true,.9f))
        assertFalse(p.allow(100,80,1200,true,.9f))
        assertTrue(p.allow(100,101,1300,false,.9f))
        assertFalse(p.allow(101,21,1500,true,.9f))
    }
    @Test fun threeProgressingNotesConfirmARealRestart() {
        val p=PositionContinuity()
        assertFalse(p.allow(100,20,1000,true,.9f))
        assertFalse(p.allow(100,21,1250,true,.9f))
        assertTrue(p.allow(100,22,1500,true,.9f))
    }
    @Test fun RepeatedEvidenceForOneNoteCannotConfirmAJump() {
        val p=PositionContinuity()
        repeat(20) { assertFalse(p.allow(100,20,1000+it*100L,true,.9f)) }
    }
    @Test fun quickOppositeJumpRequiresMoreEvidenceAndSeekResetsIt() {
        val p=PositionContinuity()
        p.allow(100,20,1000,true,.9f);p.allow(100,21,1100,true,.9f);assertTrue(p.allow(100,22,1200,true,.9f))
        repeat(4) { assertFalse(p.allow(22,100+it,1300+it*100L,true,.9f)) }
        assertTrue(p.allow(22,104,1800,true,.9f))
        p.reset();assertTrue(p.allow(104,5,1900,false,.9f))
    }
}
