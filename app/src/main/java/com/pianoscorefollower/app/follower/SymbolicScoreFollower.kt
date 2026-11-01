package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.*
import kotlin.math.abs

/** Shared MIDI/neural-note follower. Temporal alignment is Parangonar's tracker;
 * this adapter maps score events and confirms disruptive navigation changes. */
class SymbolicScoreFollower(private val graph:ScoreGraph,startTick:Long=0,automatic:Boolean=false) {
    private var locator:NoteStartLocator?=null
    private var recoverySearch=false
    private val locatingHistory=ArrayDeque<PerformanceObservation>()
    private var groups=emptyList<Int>()
    private var tracker:TempoPitchTracker?=null
    private var prediction=ScoreTempoPrediction(.5)
    private var anchor=0
    private var anchorTime=-1L
    private var lastTime=-1L
    private var originTime=-1L
    private var quietSince=-1L
    private var excludedSilence=0L
    private var pending=-1
    private var pendingAt=-1L
    private var pendingCount=0
    private val evidence=ArrayDeque<Float>()
    var position=FollowPosition();private set
    init{seek(startTick);if(automatic){locator=NoteStartLocator(graph);position=position.copy(status=FollowStatus.LOCATING)}}
    fun seek(tick:Long,initialPeriod:Double?=null){
        locator=null;recoverySearch=false;locatingHistory.clear()
        val start=graph.events.indexOfFirst{it.tick>=tick}.let{if(it<0)graph.events.lastIndex else it}.coerceAtLeast(0)
        groups=graph.events.indices.filter{it>=start&&graph.events[it].pitches.isNotEmpty()}
        tracker=groups.takeIf{it.isNotEmpty()}?.let{TempoPitchTracker(it.map{n->graph.events[n].tick.toDouble()/graph.quarter}.toDoubleArray(),it.map{n->graph.events[n].pitches.toSet()},initialPeriod?: (60.0/graph.events[it.first()].tempo))}
        prediction=ScoreTempoPrediction(tracker?.period?:.5)
        anchor=0;anchorTime=-1;lastTime=-1;originTime=-1;quietSince=-1;excludedSilence=0;pending=-1;pendingCount=0;evidence.clear()
        val e=graph.events.getOrNull(start)
        position=FollowPosition(eventIndex=start,tick=tick,measure=e?.measure?:0,occurrence=e?.occurrence?:0,status=FollowStatus.WAITING)
    }
    fun accept(o:PerformanceObservation):FollowPosition{
        val search=locator
        if(search!=null){
            if(o.isAttack){
                locatingHistory.addLast(o.copy(pitches=o.pitches.copyOf(),attacks=o.attacks?.copyOf()))
                if(locatingHistory.size>128)locatingHistory.removeFirst()
                val time=o.timeMs-(if(o.exact)0 else o.analysisLatencyMs)
                val attacks=o.attacks?:o.pitches
                for(n in attacks.indices.filter{attacks[it]>0&&(o.exact||attacks[it]>=.5f)}){
                    val found=search.accept(time,n)?:continue
                    groups=graph.events.indices.filter{it>=found.eventIndex&&graph.events[it].pitches.isNotEmpty()}
                    tracker=found.tracker.extendReference(groups.map{graph.events[it].tick.toDouble()/graph.quarter}.toDoubleArray(),groups.map{graph.events[it].pitches.toSet()})
                    prediction=ScoreTempoPrediction(found.tracker.period)
                    anchor=found.tracker.index;anchorTime=time;originTime=found.startedAt;lastTime=time
                    excludedSilence=0;quietSince=-1;pending=-1;pendingCount=0
                    locator=null;recoverySearch=false;locatingHistory.clear()
                    evidence.clear();repeat(8){evidence.addLast(1f)}
                    val e=graph.events[groups[anchor]]
                    position=FollowPosition(groups[anchor],e.tick,e.measure,e.occurrence,(60/found.tracker.period).toFloat(),confidence(),FollowStatus.FOLLOWING,confidence())
                    return advance(o.timeMs,o.active)
                }
            }
            if(!recoverySearch)return position.copy(status=FollowStatus.LOCATING,confidence=0f).also{position=it}
        }
        val t=tracker?:return position
        val time=o.timeMs-(if(o.exact)0 else o.analysisLatencyMs)
        if(lastTime>=0&&time<lastTime)return position
        if(locator==null && o.isAttack && quietSince>=0 && time-quietSince>=400 && anchorTime>=0){
            val attacks=o.attacks?:o.pitches
            val next=groups.getOrNull(anchor+1)?.let{graph.events[it]}
            // A resumed continuation keeps its tempo and local path. A different
            // phrase needs a fresh multi-note search, without old DTW costs.
            val expected=next?.let{(it.tick-graph.events[groups[anchor]].tick)*1000*t.period/graph.quarter}?:Double.POSITIVE_INFINITY
            if(next!=null && time-anchorTime-expected>750){
                locator=NoteStartLocator(graph,t.period)
                recoverySearch=true
                locatingHistory.clear()
                return accept(o)
            }
        }
        lastTime=time
        if(o.isAttack){
            val attacks=o.attacks?:o.pitches
            val peak=attacks.maxOrNull()?:0f
            if(quietSince>=0 && time-quietSince>=800 && anchorTime>=0){
                val next=groups.getOrNull(anchor+1)?.let{graph.events[it]}
                if(next!=null&&next.pitches.any{attacks.getOrElse(it){0f}>0}){
                    val expected=(next.tick-graph.events[groups[anchor]].tick)*1000*t.period/graph.quarter
                    val extra=time-anchorTime-expected
                    if(extra>750)excludedSilence+=extra.toLong()
                }
            }
            quietSince=-1
            if(peak>0)for(n in attacks.indices.filter{attacks[it]>0 && (o.exact||attacks[it]>=.5f)}){
                if(originTime<0)originTime=time
                val index=t.accept((time-originTime-excludedSilence)/1000.0,n)
                val fit=n in t.pitches[index]
                evidence.addLast(if(fit)1f else 0f);if(evidence.size>8)evidence.removeFirst()
                if(!fit)continue
                val jump=abs(graph.events[groups[index]].tick-graph.events[groups[anchor]].tick)
                val disruptive=anchorTime>=0 && (index<anchor || jump>graph.quarter*2)
                var commit=!disruptive
                if(disruptive){
                    if(pending>=0 && abs(index-pending)<=10 && time-pendingAt>=70){pendingCount++;pendingAt=time}
                    else if(pending<0 || abs(index-pending)>10){pendingCount=1;pendingAt=time}
                    pending=index;commit=pendingCount>=2
                }
                if(commit){
                    val backwards=index<anchor
                    if(index!=anchor || anchorTime<0){anchor=index;anchorTime=time}
                    pending=-1;pendingCount=0
                    val e=graph.events[groups[anchor]]
                    prediction.confirm(e.tick,time,graph.quarter,t.period)
                    val confidence=confidence()
                    position=FollowPosition(groups[anchor],if(backwards)e.tick else maxOf(e.tick,position.tick),e.measure,e.occurrence,(60/t.period).toFloat(),confidence,if(confidence>=.5f)FollowStatus.FOLLOWING else FollowStatus.LOST,confidence)
                }
            }
        }
        return advance(o.timeMs,o.active)
    }
    fun advance(now:Long,active:Boolean):FollowPosition {
        if(locator!=null && !recoverySearch){position=position.copy(status=FollowStatus.LOCATING,confidence=0f);return position}
        val t=tracker?:return position
        if(anchorTime<0)return position
        val confidence=confidence()
        if(!active){if(quietSince<0)quietSince=now;position=position.copy(status=FollowStatus.WAITING);return position}
        if(pending>=0||confidence<.5f){position=position.copy(status=if(pending>=0)FollowStatus.LOCATING else FollowStatus.LOST,confidence=confidence.coerceAtMost(.49f));return position}
        val e=graph.events[groups[anchor]]
        // Recognition arrives after the struck note. Keep a bounded tempo
        // projection through that delay; the confirmed event/measure stays put
        // until pitch evidence arrives, so this cannot confirm a page turn.
        val cap=groups.getOrNull(anchor+1)?.let{graph.events[it].tick+graph.quarter/2}?:graph.endTick
        val predicted=(e.tick+(now-anchorTime).coerceAtLeast(0)*graph.quarter/(1000*prediction.period)).toLong().coerceIn(e.tick,cap.coerceAtLeast(e.tick))
        position=position.copy(tick=maxOf(position.tick,predicted),confidence=confidence,status=FollowStatus.FOLLOWING,tempo=(60/prediction.period).toFloat(),anticipatedNotes=groups.drop(anchor+1).take(2).flatMap{graph.events[it].pitches.toList()}.distinct())
        return position
    }
    private fun confidence():Float {
        val average=if(evidence.isEmpty())0f else evidence.sum()/evidence.size
        val distance=tracker?.distance?:Float.POSITIVE_INFINITY
        return (.7f*average+.3f/(1f+distance)).coerceIn(0f,1f)
    }
}
