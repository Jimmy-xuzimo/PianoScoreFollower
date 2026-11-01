package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.PerformanceObservation
import org.junit.Assert.*
import org.junit.Test

class RecoveryStressTest {
    @Test fun nineteenOfTwentyDistinctMidiRestartsRecoverWithinThreeSeconds() {
        val latencies=ArrayList<Long>()
        repeat(20) { trial ->
            val random=java.util.Random(911L+trial)
            val notes=IntArray(100) { 36+random.nextInt(55) }
            val graph=ScoreGraph(notes.mapIndexed { i,n -> GraphEvent(i*960L,960,i/4,0,i*960L,intArrayOf(n)) })
            val a=OnlineAligner();a.load(graph)
            fun play(index:Int,ms:Long)=a.accept(PerformanceObservation(ms,FloatArray(128).also { it[notes[index]]=1f },true,true,true))
            for(i in 0..29) play(i,1000L+i*400)
            var recovered:Long?=null
            for(i in 4..11) {
                val ms=14000L+(i-4)*400
                if(play(i,ms).eventIndex==i && recovered==null) recovered=ms-14000
            }
            latencies += recovered ?: 10000L
        }
        assertTrue("Recovery latencies: $latencies",latencies.count { it<=3000 }>=19)
        assertTrue(latencies.sorted()[18]<=3000)
        println("MIDI restart p95=${latencies.sorted()[18]} ms; ${latencies.count { it<=3000 }}/20 within 3s")
    }
}
