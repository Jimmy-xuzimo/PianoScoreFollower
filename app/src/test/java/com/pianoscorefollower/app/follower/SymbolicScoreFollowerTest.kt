package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.*
import org.junit.Assert.*
import org.junit.Test

class SymbolicScoreFollowerTest {
    private fun graph(vararg notes:Int)=ScoreGraph(notes.mapIndexed{i,p->GraphEvent(i*960L,960,i/4,0,i*960L,intArrayOf(p))},endTick=notes.size*960L)
    private fun note(ms:Long,pitch:Int)=PerformanceObservation(ms,FloatArray(128).also{it[pitch]=1f},true,true,true,0)
    @Test fun silenceDoesNotAdvance(){
        val f=SymbolicScoreFollower(graph(60,62,64));f.accept(note(1000,60))
        val tick=f.position.tick
        repeat(100){assertEquals(tick,f.advance(1100+it*100L,false).tick)}
    }
    @Test fun pauseDoesNotBecomeAPermanentTempoDrop(){
        val f=SymbolicScoreFollower(graph(60,62,64,65))
        f.accept(note(1000,60));f.advance(1300,false)
        f.accept(note(6000,62));f.accept(note(6500,64))
        assertEquals(2,f.position.eventIndex)
        assertTrue("resume at the previous tempo",f.position.tempo>110f)
    }
    @Test fun oneWrongNoteDoesNotSendTheCursorBack(){
        val f=SymbolicScoreFollower(graph(60,62,64,65,67))
        f.accept(note(1000,60));f.accept(note(1500,62))
        val previous=f.position.tick
        f.accept(note(1750,61));assertTrue(f.position.tick>=previous)
        f.accept(note(2000,64));assertEquals(2,f.position.eventIndex)
    }
    @Test fun identicalRepeatedPhrasesRemainUnlocated(){
        val g=graph(*(List(12){listOf(60,62,64,65)}.flatten().toIntArray()))
        val f=SymbolicScoreFollower(g,automatic=true)
        listOf(60,62,64,65,60,62,64,65).forEachIndexed{i,p->f.accept(note(1000+i*500L,p))}
        assertEquals(FollowStatus.LOCATING,f.position.status)
        assertEquals(0L,f.position.tick)
    }
    @Test fun automaticStartNeedsSeveralNotesAndFindsDistinctiveContext(){
        val g=graph(60,62,64,65,67,69,71,72,48,50,52,53,55,57,59,60,61,63,66,68,70,73)
        val f=SymbolicScoreFollower(g,automatic=true)
        listOf(48,50,52,53,55,57,59).forEachIndexed{i,p->f.accept(note(1000+i*500L,p))}
        assertTrue(f.position.eventIndex>=13)
        assertEquals(FollowStatus.FOLLOWING,f.position.status)
    }
}
