package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.*

/** Reference seconds use the actual per-event tempo map, including unfolded repeats. */
class ScoreTimeMap(private val graph: ScoreGraph) {
    private val seconds = DoubleArray(graph.events.size)
    init {
        if (seconds.isNotEmpty()) seconds[0]=graph.events[0].tick*60.0/(graph.quarter*graph.events[0].tempo)
        for(i in 1 until seconds.size) seconds[i]=seconds[i-1]+(graph.events[i].tick-graph.events[i-1].tick)*60.0/(graph.quarter*graph.events[i-1].tempo)
    }
    fun timeAt(tick:Long):Double {
        if(seconds.isEmpty())return 0.0
        val i=eventAt(tick)
        return seconds[i]+(tick-graph.events[i].tick)*60.0/(graph.quarter*graph.events[i].tempo)
    }
    fun tickAt(time:Double):Long {
        if(seconds.isEmpty())return 0
        val i=(seconds.binarySearch(time).let { if(it>=0)it else -it-2 }).coerceIn(0,seconds.lastIndex)
        return (graph.events[i].tick+(time-seconds[i])*graph.quarter*graph.events[i].tempo/60).toLong().coerceIn(0,graph.endTick.coerceAtLeast(graph.events.last().tick))
    }
    fun eventAt(tick:Long):Int {
        var lo=0;var hi=graph.events.size
        while(lo<hi){val m=(lo+hi) ushr 1;if(graph.events[m].tick<=tick)lo=m+1 else hi=m}
        return (lo-1).coerceAtLeast(0)
    }
}

/** Microphone follows spectral sequences. Exact MIDI retains a separate symbolic path. */
class SpectralScoreFollower(private val graph: ScoreGraph, private val reference:Array<FloatArray>, startTick:Long=0) {
    private val map=ScoreTimeMap(graph)
    private var base=0
    private lateinit var tracker:ArztOnlineTimeWarping
    var position=FollowPosition();private set
    init { seek(startTick) }
    fun seek(tick:Long) {
        base=(map.timeAt(tick)*44100/1024).toInt().coerceIn(0,reference.lastIndex)
        tracker=ArztOnlineTimeWarping(reference.copyOfRange(base,reference.size))
        val i=map.eventAt(tick)
        position=FollowPosition(eventIndex=i,tick=tick,measure=graph.events[i].measure,occurrence=graph.events[i].occurrence,status=FollowStatus.WAITING)
    }
    fun accept(feature:FloatArray, levelDb:Float):FollowPosition {
        if(levelDb < -55f) { position=position.copy(status=FollowStatus.WAITING);return position }
        val frame=tracker.accept(feature)
        val tick=map.tickAt((base+frame)*1024.0/44100)
        val i=map.eventAt(tick);val event=graph.events[i]
        // OLTW cost is an alignment distance, not a calibrated probability.
        // Expose it conservatively as a diagnostic; navigation requires sustained evidence.
        val similarity=(1f-tracker.cost/10f).coerceIn(0f,1f)
        val confirmed=tracker.inputIndex>=5 && similarity>=.5f
        position=if(confirmed) FollowPosition(i,tick,event.measure,event.occurrence,event.tempo,similarity,FollowStatus.FOLLOWING,similarity)
            else position.copy(confidence=similarity,status=if(tracker.inputIndex<5)FollowStatus.LOCATING else FollowStatus.LOST,similarity=similarity)
        return position
    }
}
