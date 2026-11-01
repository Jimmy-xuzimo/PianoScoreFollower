package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.*
import org.junit.Assert.*
import org.junit.Test

class OnlineAlignerTest {
    @Test fun isolatedWrongNotesDoNotReverseContinuousForwardPlaying() {
        val notes=IntArray(70) { 40+it%45 }
        val a=OnlineAligner();a.load(graph(*notes))
        var last=0L
        notes.forEachIndexed { i,note ->
            val p=a.accept(observation(1000L+i*500,note))
            assertTrue("forward passage reversed at $i: ${p.tick} < $last",p.tick>=last);last=p.tick
            if(i>4 && i%5==0) {
                val wrong=a.accept(observation(1200L+i*500,notes[i-4]))
                assertTrue("one wrong old note caused a backwards jump",wrong.tick>=last);last=wrong.tick
            }
        }
        assertEquals(notes.lastIndex,a.position.eventIndex)
    }

    @Test fun widelyArpeggiatedChordUnderPedalDoesNotConsumeTheNextEvent() {
        val chord=intArrayOf(60,64,67,72,76,79,84,88)
        val a=OnlineAligner();a.load(ScoreGraph(listOf(GraphEvent(0,960,0,0,0,chord),
            GraphEvent(960,960,1,0,960,intArrayOf(65)))))
        fun play(ms:Long,note:Int)=a.accept(PerformanceObservation(ms,
            FloatArray(128) { if(it in 21..108) 1f else 0f },true,true,true,
            attacks=FloatArray(128).also { it[note]=1f }))
        chord.forEachIndexed { i,note ->
            val p=play(1000L+i*100,note)
            assertEquals(0,p.eventIndex);assertEquals(FollowStatus.FOLLOWING,p.status)
        }
        assertEquals(1,play(2000,65).eventIndex)
    }

    @Test fun exactNewKeysRemainMatchableUnderAFullSustainPedal() {
        val a=OnlineAligner();a.load(graph(60,62,64))
        fun play(ms:Long,note:Int):FollowPosition {
            val sounding=FloatArray(128).also { for(n in 21..108) it[n]=1f }
            val attack=FloatArray(128).also { it[note]=1f }
            return a.accept(PerformanceObservation(ms,sounding,true,true,true,attacks=attack))
        }
        assertEquals(FollowStatus.FOLLOWING,play(1000,60).status)
        assertEquals(1,play(1500,62).eventIndex)
        assertEquals(2,play(2000,64).eventIndex)
    }

    @Test fun globalRecoveryCannotChooseBetweenIdenticalPhrasePrefixes() {
        val notes=IntArray(100) { 90+it%10 }
        intArrayOf(60,62,64,65,67).copyInto(notes,0)
        intArrayOf(60,62,64,65,69).copyInto(notes,30)
        val a=OnlineAligner();a.load(graph(*notes),80*960L)
        a.accept(observation(1000,notes[80]))
        a.accept(observation(1500,60));a.accept(observation(2000,62))
        val p=a.accept(observation(2500,64))
        assertTrue("Indistinguishable restart prefixes must not commit an arbitrary visit",p.status!=FollowStatus.FOLLOWING)
        assertTrue(p.tick>=80*960L)
    }
    private fun graph(vararg notes: Int) = ScoreGraph(notes.mapIndexed { i, p -> GraphEvent(i*960L,960,i/4,0,i*960L,intArrayOf(p)) })
    private fun observation(time: Long, vararg notes: Int, attack: Boolean = true, active: Boolean = true) =
        PerformanceObservation(time, FloatArray(128).also { a -> notes.forEach { a[it]=1f } }, attack, active, true)
    @Test fun repeatedNotesConsumeDistinctAttacks() {
        val a = OnlineAligner(); a.load(graph(60,60,60,62))
        for (i in 0..2) assertEquals(i, a.accept(observation(1000L+i*500,60)).eventIndex)
        assertEquals(3,a.accept(observation(2500,62)).eventIndex)
    }
    @Test fun decayingSamePitchFluxDoesNotConsumeAnotherAttack() {
        val a=OnlineAligner();a.load(graph(60,60,60,62))
        val values=FloatArray(128).also { it[60]=1f }
        a.accept(PerformanceObservation(1000,values,true,true,rearticulation=true))
        for(time in 1100L..1400L step 100)
            assertEquals(0,a.accept(PerformanceObservation(time,values,true,true,rearticulation=false)).eventIndex)
        assertEquals(1,a.accept(PerformanceObservation(1500,values,true,true,rearticulation=true)).eventIndex)
    }
    @Test fun heldNotesAndSilenceNeverAdvance() {
        val a = OnlineAligner(); a.load(graph(60,60,62)); a.accept(observation(1000,60))
        for (i in 1..100) assertEquals(0,a.accept(observation(1000L+i*20,60,attack=false)).eventIndex)
        for (i in 1..100) assertEquals(0,a.accept(observation(4000L+i*20,active=false)).eventIndex)
    }
    @Test fun tiedSustainCrossesPrintedBarWithoutConsumingAnAttack() {
        val g=ScoreGraph(listOf(GraphEvent(0,960,0,0,0,intArrayOf(60)),
            GraphEvent(960,960,1,0,960,intArrayOf(),intArrayOf(60)),
            GraphEvent(1920,960,2,0,1920,intArrayOf(62))))
        val a=OnlineAligner();a.load(g);a.accept(observation(1000,60))
        val p=a.accept(observation(1700,60,attack=false))
        assertEquals(0,p.eventIndex);assertEquals(1,p.measure);assertTrue(p.tick in 960..1919)
    }
    @Test fun octavesAreNotInterchangeable() {
        val a=OnlineAligner(); a.load(graph(60,72,64))
        a.accept(observation(1000,60)); assertEquals(1,a.accept(observation(1500,72)).eventIndex)
    }
    @Test fun wrongNotesDoNotCommitAndCanRecover() {
        val a=OnlineAligner(); a.load(graph(60,62,64,65))
        a.accept(observation(1000,60)); assertEquals(0,a.accept(observation(1500,90)).eventIndex)
        assertEquals(1,a.accept(observation(2000,62)).eventIndex)
    }
    @Test fun seekResetsCandidatesAtTheRequestedPosition() {
        val a=OnlineAligner(); a.load(graph(60,62,64,65)); a.seek(1920)
        assertEquals(2,a.accept(observation(1000,64)).eventIndex)
    }
    @Test fun ambiguousAutoLocateWaitsForContext() {
        val a=OnlineAligner(); a.load(graph(60,62,64,60,62,65),autoLocate=true)
        assertEquals(FollowStatus.LOCATING,a.accept(observation(1000,60)).status)
        a.accept(observation(1500,62)); val position=a.accept(observation(2000,65))
        assertEquals(5,position.eventIndex); assertEquals(FollowStatus.FOLLOWING,position.status)
    }
    @Test fun repeatOccurrenceAndPrintedMeasureRemainIndependent() {
        val g=ScoreGraph(listOf(GraphEvent(0,960,0,0,0,intArrayOf(60)), GraphEvent(960,960,1,0,960,intArrayOf(62)),
            GraphEvent(1920,960,0,1,0,intArrayOf(60)),GraphEvent(2880,960,1,1,960,intArrayOf(62))))
        val a=OnlineAligner(); a.load(g)
        a.accept(observation(1000,60)); a.accept(observation(1500,62))
        val p=a.accept(observation(2000,60)); assertEquals(0,p.measure); assertEquals(1,p.occurrence); assertEquals(1920L,p.tick)
    }
    @Test fun arbitraryBackwardRestartRecoversWithinThreeSeconds() {
        val random=java.util.Random(7721)
        val notes=IntArray(100) { 40+random.nextInt(45) }
        val a=OnlineAligner();a.load(graph(*notes))
        var time=1000L
        for(i in 0..24) { a.accept(observation(time,notes[i]));time+=400 }
        var recovered=false
        for(i in 3..9) {
            val p=a.accept(observation(time,notes[i]));time+=400
            if(p.eventIndex==i) recovered=true
        }
        assertTrue("A unique restart should recover within seven attacks",recovered)
    }
    @Test fun longMidiRecordingCanBeWrittenWithoutOverflow() {
        val session=com.pianoscorefollower.app.midi.RecordingSession("long","long",listOf(
            com.pianoscorefollower.app.midi.MidiMessage(0,byteArrayOf(0x90.toByte(),60,100)),
            com.pianoscorefollower.app.midi.MidiMessage(3_599_000_000L,byteArrayOf(0x80.toByte(),60,0))),3_600_000_000L)
        assertTrue(com.pianoscorefollower.app.midi.MidiRecordingWriter.write(session).size>60)
    }
}
