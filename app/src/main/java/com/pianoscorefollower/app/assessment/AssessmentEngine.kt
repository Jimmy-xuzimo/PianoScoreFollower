package com.pianoscorefollower.app.assessment

import com.pianoscorefollower.app.follower.ScoreGraph
import com.pianoscorefollower.app.performance.FollowPosition
import com.pianoscorefollower.app.performance.FollowStatus
import com.pianoscorefollower.app.performance.PerformanceObservation
import kotlin.math.abs
import kotlin.math.max

data class AssessmentOptions(val pitchWeight: Float = .7f, val speed: Float = 1f) {
    init { require(pitchWeight in 0.1f..0.9f); require(speed in .25f..2f) }
}
enum class NoteGrade { PENDING, CORRECT, WRONG, MISSED, UNCERTAIN }
data class AssessedNote(val id: String, val eventIndex: Int, val tick: Long, val notationTick: Long,
    val measure: Int, val occurrence: Int, val expectedPitch: Int, val grade: NoteGrade,
    val playedPitch: Int? = null, val timingErrorMs: Double? = null, val rhythmCredit: Double = 0.0)
data class ExtraNote(val pitch: Int, val tick: Long, val measure: Int)
data class AssessmentState(
    val started: Boolean = false, val paused: Boolean = false, val finished: Boolean = false,
    val estimated: Boolean = false, val startTick: Long = 0, val total: Int = 0,
    val correct: Int = 0, val wrong: Int = 0, val missed: Int = 0, val extra: Int = 0,
    val uncertain: Int = 0, val pending: Int = 0, val rhythmErrors: Int = 0,
    val pitchScore: Double = 0.0, val rhythmScore: Double = 0.0,
    val score: Double? = null, val lowerScore: Double = 0.0, val upperScore: Double = 0.0,
    val options: AssessmentOptions = AssessmentOptions(), val notes: List<AssessedNote> = emptyList(),
    val extras: List<ExtraNote> = emptyList(), val replaying: Boolean = false,
) { val judged: Int get() = total-pending-uncertain }

/** Note-count assessment. A wrong replacement consumes one expected slot, not a
 * wrong slot plus a missing slot. Chords are unordered sets of physical attacks. */
class AssessmentEngine {
    private data class Played(val pitch: Int, val time: Double)
    private data class Slot(val id: String, val event: Int, val pitch: Int, val expectedMs: Double,
        val quarterMs: Double, var grade: NoteGrade = NoteGrade.PENDING, var played: Played? = null)
    private var graph=ScoreGraph(emptyList())
    private var slots=mutableListOf<Slot>()
    private var groups=emptyList<Int>()
    private val unmatched=mutableListOf<Played>()
    private val extraNotes=mutableListOf<ExtraNote>()
    private var options=AssessmentOptions()
    private var origin: Double?=null
    private var frontier=0
    private var startTick=0L
    private var lastTime=0L
    private var excludedPause=0L
    private var pausedAt=0L
    private var started=false
    private var paused=false
    private var finished=false
    private var estimated=false
    private var uncertainWindow=false
    private var lastSeenEvent=-1
    private var replaying=false

    @Synchronized fun begin(value: ScoreGraph, tick: Long, settings: AssessmentOptions, audio: Boolean) {
        graph=value;options=settings;startTick=tick;estimated=audio
        val anchor=value.events.indexOfLast { it.tick<=tick }.coerceAtLeast(0)
        groups=value.events.indices.filter { it>=anchor && value.events[it].pitches.isNotEmpty() }
        val times=DoubleArray(value.events.size)
        for(i in 1 until times.size) times[i]=times[i-1]+(value.events[i].tick-value.events[i-1].tick)*60000.0/
            (value.quarter*value.events[i-1].tempo.coerceAtLeast(1f)*settings.speed)
        val base=groups.firstOrNull()?.let { times[it] } ?: 0.0
        slots=groups.flatMap { i -> value.events[i].pitches.distinct().map { pitch ->
            Slot("$i:$pitch",i,pitch,times[i]-base,60000.0/(value.events[i].tempo*settings.speed))
        } }.toMutableList()
        unmatched.clear();extraNotes.clear();origin=null;frontier=0;lastTime=0;excludedPause=0;pausedAt=0
        started=slots.isNotEmpty();paused=false;finished=false;uncertainWindow=false;lastSeenEvent=-1;replaying=false
    }

    @Synchronized fun pause(nowMs: Long) {
        if(started && !finished && !paused) { paused=true;pausedAt=nowMs }
    }
    @Synchronized fun resume(nowMs: Long) {
        if(paused && !finished) { excludedPause+=(nowMs-pausedAt).coerceAtLeast(0);paused=false }
    }
    @Synchronized fun clear() { started=false;slots.clear();unmatched.clear();extraNotes.clear();finished=false;paused=false }

    @Synchronized fun accept(o: PerformanceObservation, p: FollowPosition) {
        if(!started || paused || finished || !o.active) return
        if(lastTime>0 && o.timeMs<lastTime) { uncertainWindow=true;return }
        lastTime=o.timeMs
        estimated=estimated || !o.exact
        val inferred=!o.exact && p.eventIndex>lastSeenEvent
        lastSeenEvent=p.eventIndex
        if(!o.isAttack && (o.attacks!=null || !inferred)) return
        val strong=p.status==FollowStatus.FOLLOWING && (o.exact || p.confidence>=.75f)
        if(!o.exact && !strong) { uncertainWindow=true;return }
        val source=o.attacks ?: o.pitches
        val peak=source.maxOrNull() ?: 0f
        if(peak<=0f) return
        val pitches=source.indices.filter { source[it]>0 && (o.exact ||
            (source[it]>=peak*.6f && source[it]>=source.getOrElse(it-1) { 0f } && source[it]>=source.getOrElse(it+1) { 0f })) }
        val time=(o.timeMs-(if(o.exact) 0 else o.analysisLatencyMs)-excludedPause).toDouble()
        val located=groups.indexOf(p.eventIndex)
        if(strong && located>=0 && located<frontier) { replaying=true;return }
        replaying=false
        val target=if(strong && located>=frontier) located else frontier
        val endEvent=groups.getOrNull(target) ?: return
        val window=slots.filter { it.event>=groups[frontier] && it.event<=endEvent }
        pitches.forEach { pitch ->
            val played=Played(pitch,time)
            if(!match(played,window)) {
                if(o.exact) unmatched+=played
                else if(pitches.size==1) uncertainWindow=true
            }
        }
        if(strong && target>frontier) {
            // Previously unlocated MIDI notes can be credited once later context
            // confirms which part of the score was played.
            val matched=unmatched.filter { match(it,window) }
            unmatched.removeAll(matched.toSet())
            finalizeBefore(endEvent,time)
            frontier=target;uncertainWindow=false
        }
    }

    private fun match(played: Played, window: List<Slot>): Boolean {
        val available=window.filter { it.grade==NoteGrade.PENDING && it.pitch==played.pitch }
        val slot=if(origin==null) available.firstOrNull() else available.minByOrNull { abs(it.expectedMs-(played.time-origin!!)) }
        if(slot==null) return false
        if(origin==null) origin=played.time-slot.expectedMs
        slot.grade=NoteGrade.CORRECT;slot.played=played
        return true
    }

    private fun finalizeBefore(eventExclusive: Int, boundary: Double) {
        val pending=slots.filter { it.event<eventExclusive && it.grade==NoteGrade.PENDING }.toMutableList()
        val old=unmatched.filter { it.time<boundary }
        if(origin==null && old.isNotEmpty()) origin=old.first().time-(pending.firstOrNull()?.expectedMs ?: 0.0)
        old.forEach { played ->
            val slot=pending.minWithOrNull(compareBy<Slot> { abs(it.expectedMs-(played.time-(origin ?: played.time))) }
                .thenBy { abs(it.pitch-played.pitch) })
            if(slot!=null) { slot.grade=if(uncertainWindow && estimated) NoteGrade.UNCERTAIN else NoteGrade.WRONG
                slot.played=played;pending.remove(slot) }
            else {
                val event=graph.events[groups.getOrElse(frontier) { groups.last() }]
                extraNotes+=ExtraNote(played.pitch,event.tick,event.measure)
            }
        }
        unmatched.removeAll(old.toSet())
        pending.forEach { it.grade=if(uncertainWindow && estimated) NoteGrade.UNCERTAIN else NoteGrade.MISSED }
    }

    @Synchronized fun finish() {
        if(!started || finished) return
        // Only the current confirmed group is reconciled on stopping. Random early
        // notes cannot receive credit for arbitrary unplayed future passages.
        val current=groups.getOrNull(frontier)
        if(current!=null) {
            val matched=unmatched.filter { match(it,slots.filter { s -> s.event==current }) }
            unmatched.removeAll(matched.toSet())
        }
        finalizeBefore(Int.MAX_VALUE,Double.POSITIVE_INFINITY)
        finished=true;paused=false
    }

    @Synchronized fun snapshot(): AssessmentState {
        if(!started) return AssessmentState()
        val notes=slots.map { s ->
            val event=graph.events[s.event]
            val error=s.played?.let { it.time-(origin ?: it.time)-s.expectedMs }
            val good=s.quarterMs/4;val bad=s.quarterMs/2
            val credit=if(error==null) 0.0 else if(abs(error)<=good) 1.0 else ((bad-abs(error))/(bad-good)).coerceIn(0.0,1.0)
            AssessedNote(s.id,s.event,event.tick,event.notationTick,event.measure,event.occurrence,s.pitch,s.grade,
                s.played?.pitch,error,credit)
        }
        val total=notes.size
        val correct=notes.count { it.grade==NoteGrade.CORRECT }
        val uncertain=notes.count { it.grade==NoteGrade.UNCERTAIN }
        val rhythm=notes.filter { it.grade==NoteGrade.CORRECT || it.grade==NoteGrade.WRONG }.sumOf { it.rhythmCredit }
        val pitchScore=correct*100.0/total;val rhythmScore=rhythm*100.0/total
        val earned=correct*options.pitchWeight+rhythm*(1-options.pitchWeight)-extraNotes.size
        val low=(earned*100/total).coerceIn(0.0,100.0)
        val high=((earned+uncertain)*100/total).coerceIn(0.0,100.0)
        return AssessmentState(true,paused,finished,estimated,startTick,total,correct,
            notes.count { it.grade==NoteGrade.WRONG },notes.count { it.grade==NoteGrade.MISSED },extraNotes.size,
            uncertain,notes.count { it.grade==NoteGrade.PENDING },
            notes.count { (it.grade==NoteGrade.CORRECT || it.grade==NoteGrade.WRONG) && it.rhythmCredit<1 },
            pitchScore,rhythmScore,if(finished && uncertain==0) low else null,low,high,options,notes,extraNotes.toList(),replaying)
    }
}
