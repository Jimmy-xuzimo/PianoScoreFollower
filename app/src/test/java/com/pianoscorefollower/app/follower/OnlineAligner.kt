package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.*
import kotlin.math.*

/** Bounded online Viterbi search. Only observations already received are inspected.
 * Held notes update acoustic evidence but never consume a repeated attack. */
class OnlineAligner {
    private data class Candidate(val index: Int, val score: Float, val at: Long, val tempo: Float, val evidence: Int)
    private var graph = ScoreGraph(emptyList())
    private var beam = emptyList<Candidate>()
    private var anchor = 0
    private var started = false
    private var lastAttack = -1L
    private var lastEvidence = -1L
    private var failures = 0
    private var automatic = false
    private var confirmedAt = 0L
    private var confirmedIndex = 0
    private var confirmedExact = false
    private var previousPitches = FloatArray(128)
    private var attackView: FloatArray? = null
    private var exactAttackMode = false
    private var acousticCandidate = -1
    private var acousticFrames = 0
    private var lastInferredAt = -1000L
    private var globalScores = FloatArray(0)
    private var globalSteps = 0
    private var globalEvidence = IntArray(0)
    private var globalUnique = false
    private var globalSupport = 0
    private var quietSince = -1L
    private var silenceReset = false
    private var nextAttackTicks = LongArray(0)
    private val continuity = PositionContinuity()
    private val globalMovement = MovementSequence()
    private var globalMovementEvidence: MovementEvidence? = null
    private var restartAfterPauseUntil = 0L
    var position = FollowPosition(); private set

    @Synchronized fun load(value: ScoreGraph, startTick: Long = 0, autoLocate: Boolean = false) {
        graph = value
        var followingAttack: Long? = null
        nextAttackTicks=LongArray(value.events.size)
        for(i in value.events.indices.reversed()) {
            val event=value.events[i]
            nextAttackTicks[i]=followingAttack ?: (event.tick+event.duration)
            if(event.pitches.isNotEmpty()) followingAttack=event.tick
        }
        globalScores = FloatArray(value.events.size)
        globalSteps = 0; globalEvidence=IntArray(value.events.size); globalUnique=false; globalSupport = 0
        automatic = autoLocate
        seek(startTick)
    }

    @Synchronized fun seek(tick: Long) {
        continuity.reset()
        restartAfterPauseUntil=0
        globalMovement.reset();globalMovementEvidence=null
        globalScores=FloatArray(graph.events.size); globalSteps=0; globalEvidence=IntArray(graph.events.size);globalUnique=false; globalSupport=0
        quietSince=-1;silenceReset=false
        previousPitches=FloatArray(128);attackView=null;exactAttackMode=false;acousticCandidate=-1;acousticFrames=0;lastInferredAt=-1000
        anchor = graph.events.indexOfLast { it.tick <= tick }.coerceAtLeast(0)
        beam = emptyList(); confirmedAt = 0; confirmedIndex = anchor; started = false; lastAttack = -1; lastEvidence = -1; failures = 0
        val e = graph.events.getOrNull(anchor)
        val end=graph.endTick.takeIf { it>0 } ?: graph.events.lastOrNull()?.let { it.tick+it.duration } ?: 0
        val initialTick=if(e==null) 0 else tick.coerceIn(e.tick,(end-1).coerceAtLeast(e.tick))
        position = FollowPosition(anchor, initialTick, e?.measure ?: 0, e?.occurrence ?: 0,
            e?.tempo ?: 120f, status = if (automatic) FollowStatus.LOCATING else FollowStatus.WAITING)
    }

    fun similarity(pitches: FloatArray, event: GraphEvent): Float {
        if (event.pitches.isEmpty()) return 0f
        var dot = 0f; var norm = 0f; var total = 0f; var inside = 0f
        for (p in pitches.indices) {
            val v = pitches[p].coerceAtLeast(0f)
            norm += v * v; total += v
            if (p in event.sounding) { inside += v; dot += v }
        }
        if (norm <= 1e-10f) return 0f
        val cosine = dot / sqrt(norm * event.sounding.size.coerceAtLeast(1))
        var attackDot = 0f
        event.pitches.forEach { if (it in pitches.indices) attackDot += pitches[it] }
        var attackNorm = norm
        attackView?.let { novelty ->
            attackDot = 0f; attackNorm = 0f
            for (v in novelty) attackNorm += v*v
            event.pitches.forEach { if (it in novelty.indices) attackDot += novelty[it] }
        }
        val attackCosine = attackDot / sqrt(attackNorm.coerceAtLeast(1e-10f) * event.pitches.size.coerceAtLeast(1))
        if(exactAttackMode) {
            val attackPrecision=attackDot/(attackView?.sum() ?: 0f).coerceAtLeast(1e-6f)
            // An arpeggiated or incomplete chord still identifies its score event.
            val keyMatch=.8f*attackPrecision+.2f*attackCosine
            return (.15f*cosine+.8f*keyMatch+.05f*inside/total.coerceAtLeast(1e-6f)).coerceIn(0f,1f)
        }
        return (0.40f * cosine + 0.45f * attackCosine + 0.15f * inside / total.coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
    }

    @Synchronized fun accept(o: PerformanceObservation): FollowPosition {
        if (graph.events.isEmpty()) return position
        if (!o.active) {
            if(quietSince<0) quietSince=o.timeMs
            if(!o.exact && !silenceReset && o.timeMs-quietSince>=400) {
                // A real pause breaks acoustic continuity. Keep the anchored local
                // path, but restart the independent search and discard old residuals.
                globalScores=FloatArray(graph.events.size);globalSteps=0;globalEvidence=IntArray(graph.events.size);globalUnique=false;globalSupport=0
                previousPitches=FloatArray(128);silenceReset=true
                globalMovement.reset();globalMovementEvidence=null
            }
            // Silence freezes the confirmed cursor. A pause can lower certainty, not advance time.
            if (lastEvidence >= 0 && o.timeMs - lastEvidence > 1800)
                position = position.copy(confidence = position.confidence.coerceAtMost(0.35f), status = FollowStatus.WAITING)
            return position
        }
        if(quietSince>=0 && o.timeMs-quietSince>=400) restartAfterPauseUntil=o.timeMs+4000
        quietSince=-1;silenceReset=false
        if (!o.isAttack) return acousticFrame(o)
        if (!o.exact && o.timeMs - lastInferredAt < 110) return interpolate(o)
        if ((lastAttack >= 0 && o.timeMs - lastAttack < if (o.exact) 20 else 65)) return interpolate(o)
        val gap = if (lastAttack >= 0) o.timeMs - lastAttack else 0
        lastAttack = o.timeMs
        exactAttackMode=o.exact && o.attacks!=null
        attackView = if (o.exact) o.attacks else pitchNovelty(o)
        previousPitches = o.pitches.copyOf()
        val recovery = updateGlobal(o)
        val candidates = ArrayList<Candidate>()
        val observationTime = o.timeMs + if (o.exact) 0 else 185 - o.analysisLatencyMs
        fun add(index: Int, prior: Float, previous: Candidate?, jump: Boolean = false) {
            val e = graph.events.getOrNull(index) ?: return
            if (e.pitches.isEmpty()) return
            val match = similarity(o.pitches, e)
            var tempo = previous?.tempo ?: e.tempo
            var timing = 0f
            if(started && (graph.events[confirmedIndex].tick-e.tick>graph.quarter*2L || index>confirmedIndex+4))
                timing+=locationPrior(e.tick,observationTime)
            if (previous != null && index > previous.index && !jump) {
                val beats = (e.tick - graph.events[previous.index].tick).toFloat() / graph.quarter
                val elapsed = (observationTime - previous.at).coerceAtLeast(1)
                val expected = beats * 60000f / tempo
                timing = min(2.5f, abs(ln((elapsed / expected.coerceAtLeast(30f)).toDouble())).toFloat()) * 0.8f
                val repeated = e.pitches.contentEquals(graph.events[previous.index].pitches)
                if (repeated && expected > 200 && !o.exact && !o.rearticulation) timing += 2.5f
                if (elapsed < expected * if (repeated) 0.65f else 0.35f) timing += 2.5f
                val measured = 60000f * beats / elapsed
                if (measured in 25f..300f) tempo = tempo * 0.75f + measured * 0.25f
            }
            val score = prior + ln(match.coerceAtLeast(0.005f)) * 2.7f - timing
            candidates += Candidate(index, score, if (previous != null && index == previous.index) previous.at else observationTime, tempo, (previous?.evidence ?: 0) + if (match >= 0.5f) 1 else 0)
        }
        if (beam.isEmpty()) {
            if (automatic) graph.events.indices.forEach { add(it, 0f, null) }
            else graph.events.indices.drop(anchor).filter { graph.events[it].pitches.isNotEmpty() }.take(4)
                .forEachIndexed { rank, i -> add(i, -rank * 0.7f, null) }
        } else {
            beam.forEach { c ->
                // Staying represents an extra/wrong attack. Advancing is preferred on a new repeated note.
                add(c.index, c.score - 0.9f, c)
                for (step in 1..4) add(c.index + step, c.score - (step - 1) * 0.65f, c)
                // Local restart candidates need a sequence of supporting notes before being committed.
                if (failures > 1 || gap > 1500) {
                    for (i in maxOf(0, c.index - 32)..minOf(graph.events.lastIndex, c.index + 32))
                        if (abs(i - c.index) > 4) add(i, c.score - 3.2f, c, true)
                }
            }
            if(started && position.status!=FollowStatus.FOLLOWING) {
                // Keep a trusted local continuation alive while uncommitted
                // restart hypotheses compete. A wrong note must not evict the
                // original score path from the entire beam.
                val anchored=Candidate(confirmedIndex,0f,confirmedAt,position.tempo,2)
                graph.events.indices.drop(confirmedIndex).filter { graph.events[it].pitches.isNotEmpty() }.take(5)
                    .forEachIndexed { rank,i -> add(i,-rank*.45f,anchored) }
            }
            if (failures >= 3 || gap > 2500) {
                val prior = beam.maxOf { it.score } - 4.0f
                graph.events.indices.forEach { add(it, prior, null, true) }
            }
        }
        if (candidates.isEmpty()) return position
        val ranked = candidates.groupBy { it.index }.values.map { cs -> cs.maxBy { it.score } }
            .sortedByDescending { it.score }.take(24)
        val best = ranked.first()
        val maximum = best.score
        beam = ranked.map { it.copy(score = it.score - maximum) }
        val e = graph.events[best.index]
        val match = similarity(o.pitches, e)
        val anchoredContinuation=started && match>=.5f && best.index in confirmedIndex..confirmedIndex+4 &&
            lastEvidence>=0 && o.timeMs-lastEvidence<120000f/position.tempo.coerceAtLeast(25f)
        if (recovery >= 0 && abs(recovery - best.index) > 4 && !anchoredContinuation) {
            val recovered = graph.events[recovery]
            val fit = similarity(o.pitches,recovered)
            if (globalSupport >= 3 && globalScores[recovery] - globalScores[best.index] > 4f && fit >= if(o.exact) 0.65f else 0.5f) {
                if(!globalUnique) {
                    // An ambiguous unanchored phrase must not veto a good nearby
                    // continuation of the user's already established position.
                    if(match<.5f || abs(best.index-confirmedIndex)>4) {
                        position=position.copy(status=FollowStatus.LOCATING,confidence=.25f,similarity=fit)
                        return position
                    }
                } else {
                    if(!confirmMovement(recovery,observationTime,fit,globalMovementEvidence,o.exact)) return position
                    confirmedAt=observationTime; confirmedIndex=recovery; confirmedExact=o.exact
                    lastEvidence=o.timeMs; failures=0; started=true; automatic=false
                    beam=listOf(Candidate(recovery,0f,observationTime,best.tempo,3))
                    position=FollowPosition(recovery,recovered.tick,recovered.measure,recovered.occurrence,best.tempo,
                        0.75f,FollowStatus.FOLLOWING,fit)
                    return interpolate(o)
                }
            }
        }
        val margin = if (ranked.size > 1) maximum - ranked[1].score else 3f
        val distant = started && abs(best.index - position.eventIndex) > 4
        val ambiguous = (automatic || distant) && margin < 0.55f
        if (match < if (o.exact) 0.50f else 0.38f) {
            failures++
            position = position.copy(confidence = (position.confidence - 0.18f).coerceAtLeast(0f), status = FollowStatus.LOST, similarity = match)
        } else if (ambiguous || ((automatic || distant) && best.evidence < 3)) {
            position = position.copy(status = FollowStatus.LOCATING, confidence = 0.25f, similarity = match)
        } else {
            if(!confirmMovement(best.index,observationTime,match,globalMovementEvidence,o.exact)) return position
            failures = 0; started = true; automatic = false; lastEvidence = o.timeMs
            confirmedAt = best.at; confirmedIndex = best.index; confirmedExact = o.exact
            position = FollowPosition(best.index, e.tick, e.measure, e.occurrence, best.tempo,
                (0.35f + min(best.evidence, 4) * 0.1f + min(margin, 1f) * 0.15f).coerceAtMost(1f),
                FollowStatus.FOLLOWING, match)
        }
        return interpolate(o)
    }
    private fun confirmMovement(index: Int, time: Long, fit: Float, prior: MovementEvidence?, exact: Boolean): Boolean {
        val current=graph.events[confirmedIndex]
        val target=graph.events[index]
        val elapsed=(time-confirmedAt).coerceAtLeast(0)
        val plausibleTicks=maxOf(graph.quarter*2L,
            (elapsed*graph.quarter*position.tempo/60000f*2+graph.quarter).toLong())
        val discontinuous=started && (current.tick-target.tick>graph.quarter*2L || index-confirmedIndex>4 ||
            (index-confirmedIndex>1 && target.tick-current.tick>plausibleTicks))
        val required=if(time>restartAfterPauseUntil && abs(target.tick-current.tick)>graph.quarter*16L) 6 else 3
        if(!continuity.allow(confirmedIndex,index,time,discontinuous,fit,prior,if(exact) .5f else .38f,required)) {
            position=position.copy(status=FollowStatus.LOCATING,confidence=minOf(position.confidence,.4f),similarity=fit)
            return false
        }
        return true
    }
    private fun locationPrior(tick: Long,time: Long): Float {
        if(!started || automatic || time<=restartAfterPauseUntil) return 0f
        val current=graph.events[confirmedIndex]
        val expected=current.tick+(time-confirmedAt).coerceAtLeast(0)*graph.quarter*position.tempo/60000f
        val distance=abs(tick-expected)/graph.quarter
        val age=(time-lastEvidence).coerceAtLeast(0)
        val reliability=exp(-((age-1500).coerceAtLeast(0)/1500f))
        return reliability*minOf(8f,1.2f*ln(1f+distance)+if(tick<current.tick) 1f else 0f)
    }
    /** Independent unanchored sequence hypotheses allow recovery even when a wrong
     * local phrase shares a few notes with the actual phrase. No future input is used. */
    private fun updateGlobal(o: PerformanceObservation): Int {
        if (graph.events.isEmpty()) return -1
        val norm=sqrt(o.pitches.sumOf { (it*it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        val total=o.pitches.sum().coerceAtLeast(1e-6f)
        val novelty=attackView ?: o.pitches
        val noveltyMass=if(exactAttackMode) novelty.sum().coerceAtLeast(1e-6f) else 1f
        val noveltyNorm=sqrt(novelty.sumOf { (it*it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        val next=FloatArray(globalScores.size) { -10000f }
        val evidence=IntArray(globalScores.size)
        var best=-1; var maximum=-10000f;var bestFit=0f
        graph.events.forEachIndexed { i,e ->
            if (e.pitches.isNotEmpty()) {
                var inside=0f; for(p in e.sounding) if(p in o.pitches.indices) inside+=o.pitches[p]
                var attack=0f; for(p in e.pitches) if(p in novelty.indices) attack+=novelty[p]
                val fit=if(exactAttackMode) (.15f*inside/(norm*sqrt(e.sounding.size.coerceAtLeast(1).toFloat()))+
                    .8f*(.8f*attack/noveltyMass+.2f*attack/(noveltyNorm*sqrt(e.pitches.size.toFloat())))+.05f*inside/total).coerceIn(.005f,1f)
                    else (.4f*inside/(norm*sqrt(e.sounding.size.coerceAtLeast(1).toFloat()))+
                    .45f*attack/(noveltyNorm*sqrt(e.pitches.size.toFloat()))+.15f*inside/total).coerceIn(.005f,1f)
                var prior=if(globalSteps==0) 0f else globalScores[i]-.7f
                var support=if(globalSteps==0) 0 else globalEvidence[i]
                if(globalSteps>0) for(step in 1..4) if(i>=step) {
                    val candidate=globalScores[i-step]-(step-1)*.6f -
                        if(!o.exact && !o.rearticulation &&
                            (e.tick-graph.events[i-step].tick)*60000f/(graph.quarter*position.tempo)>200 &&
                            e.pitches.contentEquals(graph.events[i-step].pitches)) 2.5f else 0f
                    if(candidate>prior) { prior=candidate;support=globalEvidence[i-step] }
                }
                if(prior < -5f) { prior=-5f;support=0 }
                next[i]=prior+ln(fit)*2.7f
                evidence[i]=support+if(fit>=.5f) 1 else 0
                if(next[i]>maximum) { maximum=next[i];best=i;bestFit=fit }
            }
        }
        // A trusted starting position supplies a spatial prior without advancing
        // the cursor by time alone. The unanchored sequence scores stay intact.
        val time=o.timeMs+if(o.exact) 0 else 185-o.analysisLatencyMs
        if(started && !automatic) {
            val preferred=next.indices.filter { graph.events[it].pitches.isNotEmpty() }
                .maxByOrNull { next[it]-locationPrior(graph.events[it].tick,time) }
            if(preferred!=null) { best=preferred;bestFit=similarity(o.pitches,graph.events[best]) }
        }
        // Equal distant paths represent genuinely indistinguishable phrases or
        // repeat visits. Keep all their evidence until later notes separate them.
        globalUnique=best>=0 && next.indices.none { abs(it-best)>4 && next[it]>=next[best]-.55f }
        for(i in next.indices) next[i]-=maximum
        globalScores=next;globalEvidence=evidence;globalSteps++
        globalSupport=evidence.getOrElse(best) { 0 }
        globalMovementEvidence=globalMovement.observe(best,time,bestFit,if(o.exact) .5f else .38f)
        return if(globalSteps>=3) best else -1
    }
    private fun pitchNovelty(o: PerformanceObservation): FloatArray {
        val event=graph.events[position.eventIndex]
        val interval=(nextAttackTicks[position.eventIndex]-event.tick)*60000f/(graph.quarter*position.tempo)
        val fast=interval<200f
        val retained=if(fast) .05f else .2f
        val previous=if(fast) .95f else .75f
        return FloatArray(o.pitches.size) { i -> maxOf(o.pitches[i]*retained,
            o.pitches[i]-previousPitches.getOrElse(i) { 0f }*previous) }
    }
    private fun acousticFrame(o: PerformanceObservation): FollowPosition {
        if (o.exact || !started || o.timeMs - lastAttack < 90) return interpolate(o)
        val current = graph.events.getOrNull(position.eventIndex) ?: return position
        val previousView = attackView
        attackView = pitchNovelty(o)
        val currentScore = similarity(o.pitches,current)
        val possible = ((position.eventIndex+1)..minOf(position.eventIndex+4,graph.events.lastIndex))
            .filter { graph.events[it].pitches.isNotEmpty() && !graph.events[it].pitches.contentEquals(current.pitches) }
            .map { it to similarity(o.pitches,graph.events[it]) }
        val best = possible.maxByOrNull { it.second }
        attackView = previousView
        if (best != null && best.second > 0.45f && best.second > currentScore + 0.03f) {
            if (best.first == acousticCandidate) acousticFrames++ else { acousticCandidate=best.first; acousticFrames=1 }
            if (acousticFrames>=2) {
                acousticFrames=0
                val result=accept(o.copy(isAttack=true,analysisLatencyMs=100))
                lastInferredAt=o.timeMs
                return result
            }
        } else { acousticCandidate=-1; acousticFrames=0 }
        return interpolate(o)
    }
    private fun interpolate(o: PerformanceObservation): FollowPosition {
        if (confirmedAt <= 0 || position.status != FollowStatus.FOLLOWING || !o.active) return position
        val e = graph.events[confirmedIndex]
        val next = nextAttackTicks[confirmedIndex]
        val elapsed = (o.timeMs - confirmedAt + if (confirmedExact) 0 else 185).coerceAtLeast(0)
        val ticks = elapsed * graph.quarter * position.tempo / 60000f
        val tick = e.tick + ticks.toLong().coerceIn(0, (next-e.tick-1).coerceAtLeast(0))
        val found = graph.events.binarySearchBy(tick) { it.tick }
        val displayed = graph.events[if(found>=0) found else (-found-2).coerceAtLeast(0)]
        position = position.copy(tick = tick, measure = displayed.measure, occurrence = displayed.occurrence)
        return position
    }
}
