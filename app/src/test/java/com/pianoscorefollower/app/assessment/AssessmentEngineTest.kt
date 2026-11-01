package com.pianoscorefollower.app.assessment

import com.pianoscorefollower.app.follower.*
import com.pianoscorefollower.app.performance.*
import org.junit.Assert.*
import org.junit.Test

class AssessmentEngineTest {
    private fun graph(vararg pitches: IntArray)=ScoreGraph(pitches.mapIndexed { i,p -> GraphEvent(i*960L,960,i,0,i*960L,p) },960,pitches.size*960L)
    private fun play(e: AssessmentEngine,index: Int,time: Long,vararg pitches: Int,exact: Boolean=true,confidence: Float=1f) {
        val a=FloatArray(128);pitches.forEach { a[it]=1f }
        e.accept(PerformanceObservation(time,a,true,true,exact,0,attacks=if(exact)a else null),
            FollowPosition(index,index*960L,index,confidence=confidence,status=FollowStatus.FOLLOWING))
    }
    @Test fun perfectNotesAndRhythmScore100() {
        val e=AssessmentEngine();e.begin(graph(intArrayOf(60),intArrayOf(62),intArrayOf(64)),0,AssessmentOptions(),false)
        play(e,0,1000,60);play(e,1,1500,62);play(e,2,2000,64);e.finish()
        assertEquals(100.0,e.snapshot().score!!,.001);assertEquals(3,e.snapshot().correct)
    }
    @Test fun wrongAndMissingConsumeSeparateExpectedSlotsWithoutDoublePenalty() {
        val e=AssessmentEngine();e.begin(graph(intArrayOf(60),intArrayOf(62),intArrayOf(64),intArrayOf(65)),0,AssessmentOptions(),false)
        play(e,0,1000,60);play(e,0,1500,63);play(e,3,2500,65);e.finish()
        val s=e.snapshot();assertEquals(2,s.correct);assertEquals(1,s.wrong);assertEquals(1,s.missed);assertEquals(0,s.extra)
        assertEquals(50.0,s.pitchScore,.001);assertEquals(75.0,s.rhythmScore,.001)
    }
    @Test fun extraAttackDeductsOneNoteShare() {
        val e=AssessmentEngine();e.begin(graph(intArrayOf(60),intArrayOf(62)),0,AssessmentOptions(),false)
        play(e,0,1000,60,61);play(e,1,1500,62);e.finish()
        assertEquals(1,e.snapshot().extra);assertEquals(50.0,e.snapshot().score!!,.001)
    }
    @Test fun chordAttacksCanArriveInAnyOrderWithPedal() {
        val e=AssessmentEngine();e.begin(graph(intArrayOf(60,64,67),intArrayOf(62)),0,AssessmentOptions(),false)
        play(e,0,1000,67);play(e,0,1020,60);play(e,0,1040,64);play(e,1,1500,62);e.finish()
        assertEquals(4,e.snapshot().correct);assertEquals(100.0,e.snapshot().score!!,.001)
    }
    @Test fun halfBeatLateLosesRhythmShareOnly() {
        val e=AssessmentEngine();e.begin(graph(intArrayOf(60),intArrayOf(62)),0,AssessmentOptions(),false)
        play(e,0,1000,60);play(e,1,1750,62);e.finish()
        assertEquals(100.0,e.snapshot().pitchScore,.001);assertEquals(50.0,e.snapshot().rhythmScore,.001)
        assertEquals(85.0,e.snapshot().score!!,.001)
    }
    @Test fun tempoChangesPracticeSpeedAndPauseUseTheirOwnClock() {
        val g=graph(intArrayOf(60),intArrayOf(62),intArrayOf(64))
        val e=AssessmentEngine();e.begin(g.copy(events=g.events.mapIndexed { i,n->n.copy(tempo=if(i==0)120f else 60f) }),0,AssessmentOptions(speed=.5f),false)
        play(e,0,1000,60);e.pause(1200);e.resume(6200);play(e,1,7000,62);play(e,2,9000,64);e.finish()
        assertEquals(100.0,e.snapshot().score!!,.001)
    }
    @Test fun repeatsCountAgainTiesAndRestsDoNotCount() {
        val g=graph(intArrayOf(60),intArrayOf(),intArrayOf(60),intArrayOf(62))
        val e=AssessmentEngine();e.begin(g.copy(events=g.events.mapIndexed { i,n->n.copy(occurrence=if(i>=2)1 else 0,measure=if(i>=2)i-2 else i) }),0,AssessmentOptions(),false)
        assertEquals(3,e.snapshot().total);assertEquals(0,e.snapshot().missed)
        e.finish();assertEquals(3,e.snapshot().missed)
    }
    @Test fun replayingAlreadyJudgedPassagesDoesNotFarmPoints() {
        val e=AssessmentEngine();e.begin(graph(intArrayOf(60),intArrayOf(62),intArrayOf(64)),0,AssessmentOptions(),false)
        play(e,0,1000,60);play(e,1,1500,62);play(e,0,2000,60);assertTrue(e.snapshot().replaying)
        play(e,2,2000,64);e.finish();assertEquals(3,e.snapshot().correct);assertEquals(100.0,e.snapshot().score!!,.001)
    }
    @Test fun lowConfidenceAudioProducesAnUncertainRange() {
        val e=AssessmentEngine();e.begin(graph(intArrayOf(60),intArrayOf(62)),0,AssessmentOptions(),true)
        play(e,0,1000,60,exact=false);play(e,0,1400,63,exact=false,confidence=.3f);e.finish()
        assertEquals(1,e.snapshot().uncertain);assertNull(e.snapshot().score);assertTrue(e.snapshot().upperScore>e.snapshot().lowerScore)
    }
}
