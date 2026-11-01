/* Adapted from Parangonar SLT_OLTW and tempo_and_pitch_metric, Apache-2.0.
 * Pinned sources and modifications: third_party/parangonar/NOTICE.
 */
package com.pianoscorefollower.app.follower

import kotlin.math.abs

/** Existing online tempo/pitch DTW recurrence, one performed Note On per update.
 * Each score event is a set: rolled chords do not require simultaneous key-downs.
 * No global best-pitch search or independent position teleport is used. */
class TempoPitchTracker(
    val beats:DoubleArray,
    val pitches:List<Set<Int>>,
    initialPeriod:Double=.5,
    private val window:Int=40,
    private val maxRun:Int=10,
) {
    private val initial=initialPeriod.coerceIn(.05,10.0)
    private val cost=Array(2){FloatArray(beats.size+1){Float.POSITIVE_INFINITY}}
    private val length=Array(2){FloatArray(beats.size+1)}
    private val tempo=Array(2){FloatArray(beats.size+1){initial.toFloat()}}
    var index=0;private set
    var count=0;private set
    var period=initial;private set
    var distance=Float.POSITIVE_INFINITY;private set
    private var previousTime=0.0
    init { require(beats.isNotEmpty()&&beats.size==pitches.size) }

    /** Keep the winning online state when a bounded search becomes the main
     * tracker. Replaying a chord from its first pitch changes that DP history. */
    fun extendReference(newBeats:DoubleArray,newPitches:List<Set<Int>>):TempoPitchTracker {
        require(newBeats.size>=beats.size && beats.indices.all{beats[it]==newBeats[it]})
        return TempoPitchTracker(newBeats,newPitches,initial,window,maxRun).also { next ->
            for(c in 0..1){cost[c].copyInto(next.cost[c]);length[c].copyInto(next.length[c]);tempo[c].copyInto(next.tempo[c])}
            next.index=index;next.count=count;next.period=period;next.distance=distance;next.previousTime=previousTime
        }
    }

    fun accept(time:Double,pitch:Int):Int {
        require(time.isFinite())
        val start=(index-window).coerceAtLeast(0)
        val end=(index+window).coerceAtMost(beats.size)
        var best=Double.POSITIVE_INFINITY;var candidate=start
        if(start==0&&count==0){cost[1][1]=0f;length[1][1]=0f;best=0.0;candidate=0}
        for(i in start until end){
            if(i==0&&count==0)continue
            var localBest=Double.POSITIVE_INFINITY
            var localTempo=initial.toFloat();var localLength=1f
            // Same direction order/weights as upstream: REF=2, BOTH=1, INPUT=1.
            for(d in 0..2){
                val istep=if(d==2)0 else 1
                val jstep=if(d==0)0 else 1
                val prev=i-istep
                val col=1-jstep
                val oldCost=cost[col][prev+1]
                if(!oldCost.isFinite())continue
                val oldLength=length[col][prev+1]
                val oldTempo=tempo[col][prev+1]
                val prevTime=if(jstep==0||count==0)time else previousTime
                val dt=time-prevTime
                val db=beats[i]-beats[if(prev<0)beats.lastIndex else prev]
                val pitchDistance=if(pitch in pitches[i])0.0 else 1.0
                // Match NumPy 2 scalar promotion: Python scalars combined with
                // float32 tempo/cost values remain float32 (NEP 50).
                val onsetDistance=abs(time.toFloat()-(prevTime.toFloat()+db.toFloat()*oldTempo))/oldTempo
                val currentPeriod=if(db>0)dt/db else (oldTempo+dt.toFloat()).toDouble()
                val newPeriod=((.1*currentPeriod).toFloat()+.9f*oldTempo).coerceIn(.05f,10f)
                val metric=pitchDistance.toFloat()+2f*onsetDistance
                val value=(oldCost*oldLength+metric*(if(d==0)2.0 else 1.0))/(oldLength+1)
                if(value<localBest){localBest=value;localTempo=newPeriod.toFloat();localLength=oldLength+1}
            }
            cost[1][i+1]=localBest.toFloat();tempo[1][i+1]=localTempo;length[1][i+1]=localLength
            if(localBest<best){best=localBest;candidate=i}
        }
        cost[1].copyInto(cost[0]);cost[1].fill(Float.POSITIVE_INFINITY)
        length[1].copyInto(length[0]);length[1].fill(0f)
        tempo[1].copyInto(tempo[0]);tempo[1].fill(initial.toFloat())
        if(count>0)index=candidate.coerceIn((index-maxRun).coerceAtLeast(0),(index+maxRun).coerceAtMost(beats.lastIndex))
        period=tempo[0][index+1].toDouble();distance=cost[0][index+1]
        count++;previousTime=time
        return index
    }
}
