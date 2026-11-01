package com.pianoscorefollower.app.follower

import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

class TempoPitchTrackerTest {
    @Test fun extendingSearchKeepsItsExactOnlineState() {
        val beats=DoubleArray(30){it.toDouble()}
        val pitches=List(30){setOf(48+it%24)}
        val full=TempoPitchTracker(beats,pitches)
        val bounded=TempoPitchTracker(beats.copyOf(12),pitches.take(12))
        repeat(8){i->full.accept(i*.5,48+i);bounded.accept(i*.5,48+i)}
        val extended=bounded.extendReference(beats,pitches)
        for(i in 8..20){
            assertEquals(full.accept(i*.5,48+i%24),extended.accept(i*.5,48+i%24))
            assertEquals(full.period,extended.period,0.0)
            assertEquals(full.distance,extended.distance,0f)
        }
    }
    @Test fun matchesUpstreamNoteByNote() {
        val path=System.getenv("PIANO_SYMBOLIC_CASE");assumeNotNull(path)
        val folder=File(path!!)
        val events=File(folder,"graph.tsv").readLines().map{it.split('\t')}.filter{it[5].isNotBlank()}
        val tracker=TempoPitchTracker(events.map{it[0].toDouble()/960}.toDoubleArray(),
            events.map{it[5].split(',').map(String::toInt).toSet()},60.0/130.0)
        val expected=File(folder,"magenta-symbolic-full.tsv").readLines().drop(1)
        File(folder,"magenta-full.notes.tsv").readLines().drop(1).forEachIndexed { i,line ->
            val row=line.split('\t');val actual=tracker.accept(row[1].toDouble(),row[2].toInt())
            assertEquals("note $i",expected[i].split('\t')[3].toInt(),actual)
        }
    }
    @Test fun rolledChordAndTempoChange() {
        val tracker=TempoPitchTracker(doubleArrayOf(0.0,1.0,2.0,3.0,4.0),
            listOf(setOf(48,60,64),setOf(50,62,65),setOf(52,64,67),setOf(53,65,69),setOf(55,67,71)))
        listOf(48,60,64).forEachIndexed { i,n -> assertEquals(0,tracker.accept(i*.02,n)) }
        listOf(50,62,65).forEachIndexed { i,n -> assertEquals(1,tracker.accept(.5+i*.02,n)) }
        listOf(52,64,67).forEachIndexed { i,n -> assertEquals(2,tracker.accept(1.1+i*.02,n)) }
    }
}
