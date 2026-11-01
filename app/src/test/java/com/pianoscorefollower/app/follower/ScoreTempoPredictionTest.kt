package com.pianoscorefollower.app.follower

import org.junit.Assert.*
import org.junit.Test

class ScoreTempoPredictionTest {
    @Test fun rolledChordsDoNotBecomeVeryFastTempo() {
        val p=ScoreTempoPrediction(.5)
        repeat(5){beat->
            repeat(3){note->p.confirm(beat*960L,1000+beat*500L+note*15,960,.5)}
        }
        assertEquals(.5,p.period,.02)
    }
    @Test fun aLongPracticePauseDoesNotSlowThePrediction() {
        val p=ScoreTempoPrediction(.5)
        repeat(5){p.confirm(it*960L,it*500L,960,.5)}
        p.confirm(5*960L,10000,960,.5)
        assertEquals(.5,p.period,.01)
    }
    @Test fun predictedNotesAreSeparateFromConfirmedPosition() {
        val events=listOf(60,64,67,72).mapIndexed{i,n->GraphEvent(i*960L,960,i,0,i*960L,intArrayOf(n))}
        val f=SymbolicScoreFollower(ScoreGraph(events,endTick=3840))
        f.accept(com.pianoscorefollower.app.performance.PerformanceObservation(1000,FloatArray(128).also{it[60]=1f},true,true,true))
        val p=f.advance(1100,true)
        assertEquals(listOf(64,67),p.anticipatedNotes)
        assertEquals(0,p.eventIndex)
        assertEquals(0,p.measure)
    }
}
