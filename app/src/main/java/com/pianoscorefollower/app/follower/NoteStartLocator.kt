package com.pianoscorefollower.app.follower

/** Parallel instances of the same upstream tracker locate an unknown start.
 * Equal repeated phrases remain ambiguous; no cursor is published until the
 * following notes distinguish them. References are bounded to 128 events each. */
class NoteStartLocator(private val graph:ScoreGraph,private val initialPeriod:Double?=null) {
    data class Result(val eventIndex:Int,val startedAt:Long,val tracker:TempoPitchTracker)
    private data class Candidate(val start:Int,val at:Long,val tracker:TempoPitchTracker)
    private val groups=graph.events.indices.filter{graph.events[it].pitches.isNotEmpty()}
    private val pitchSets=groups.map{graph.events[it].pitches.toSet()}
    private val candidates=ArrayList<Candidate>()
    private val spawned=HashSet<Int>()
    private var first=-1L
    private var lastGroup=-1L
    private var onsetGroups=0
    fun accept(time:Long,pitch:Int):Result? {
        if(first<0)first=time
        if(lastGroup<0||time-lastGroup>=70){onsetGroups++;lastGroup=time}
        if(time-first<=128){
            val matches=groups.indices.filter{it !in spawned && pitch in pitchSets[it]}
            // A massively repeated note supplies no useful starting evidence.
            // Wait for a more distinctive note instead of exhausting memory/CPU.
            for(start in if(matches.size<=512)matches else emptyList()){
                val ids=groups.subList(start,minOf(start+128,groups.size))
                val tracker=TempoPitchTracker(ids.map{graph.events[it].tick.toDouble()/graph.quarter}.toDoubleArray(),
                    pitchSets.subList(start,start+ids.size),initialPeriod?: (60.0/graph.events[ids.first()].tempo))
                candidates.add(Candidate(start,time,tracker));spawned.add(start)
            }
        }
        candidates.forEach { c -> c.tracker.accept((time-c.at)/1000.0,pitch) }
        val ranked=candidates.filter{it.tracker.count>= (if(initialPeriod==null)6 else 8)}.sortedBy{it.tracker.distance}
        if(onsetGroups>=4&&ranked.isNotEmpty()){
            val best=ranked.first()
            // Different starting hypotheses can converge on the same endpoint.
            // They are corroboration, rather than ambiguous distant repetitions.
            val endpoint=best.start+best.tracker.index
            val second=ranked.firstOrNull{kotlin.math.abs(it.start+it.tracker.index-endpoint)>1}
            if(best.tracker.distance<.6f&&(second==null||second.tracker.distance-best.tracker.distance>.08f))
                return Result(groups[best.start],best.at,best.tracker)
        }
        if(time-first>5000){candidates.clear();spawned.clear();first=-1;onsetGroups=0;lastGroup=-1}
        return null
    }
}
