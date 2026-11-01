package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.audio.AudioFrame
import com.pianoscorefollower.app.audio.ListeningEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FollowState(
    val isFollowing: Boolean = false,
    /** Tick the viewer should draw the cursor at. */
    val cursorTick: Long = 0L,
    val measure: Int = 0,
    val confidence: Float = 0f,
    /** Chroma of the most recent frame, normalised to unit length. */
    val chroma: List<Float> = List(CHROMA_BINS) { 0f },
    /** Loudest pitch classes of the most recent frame, strongest first. */
    val topPitchClasses: List<Int> = emptyList(),
    /** Pitch classes the current target event expects, for the on-screen comparison. */
    val targetPitchClasses: List<Int> = emptyList(),
    /** Match score of the most recent onset, in 0..1. */
    val bestSimilarity: Float = 0f,
    val isOnset: Boolean = false,
    val onsetPulse: Long = 0L,
    val isReady: Boolean = false,
    val position: com.pianoscorefollower.app.performance.FollowPosition = com.pianoscorefollower.app.performance.FollowPosition(),
    val midiNotes: List<Int> = emptyList(),
    val targetMidiNotes: List<Int> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FollowState) return false
        return isFollowing == other.isFollowing &&
            cursorTick == other.cursorTick &&
            measure == other.measure &&
            confidence == other.confidence &&
            chroma == other.chroma &&
            topPitchClasses == other.topPitchClasses &&
            targetPitchClasses == other.targetPitchClasses &&
            bestSimilarity == other.bestSimilarity &&
            isOnset == other.isOnset &&
            onsetPulse == other.onsetPulse &&
            isReady == other.isReady && position == other.position && midiNotes == other.midiNotes && targetMidiNotes == other.targetMidiNotes
    }

    override fun hashCode(): Int {
        var result = isFollowing.hashCode()
        result = 31 * result + cursorTick.hashCode()
        result = 31 * result + measure
        result = 31 * result + confidence.hashCode()
        result = 31 * result + bestSimilarity.hashCode()
        return result
    }

    companion object {
        const val CHROMA_BINS = 12
    }
}

/**
 * Connects exact MIDI or Magenta piano onsets to [SymbolicScoreFollower].
 *
 * Runs on the analysis worker; each attachment owns its callbacks and graph.
 */
class FollowerEngine {

    private val _state = MutableStateFlow(FollowState())
    val state: StateFlow<FollowState> = _state.asStateFlow()

    private var follower=SymbolicScoreFollower(ScoreGraph(emptyList()))
    private var neuralActive=false
    private var audioNotBeforeMs=0L
    private var graph = ScoreGraph(emptyList())
    private var listening: ListeningEngine? = null
    private var onsetPulse = 0L
    private var inputVersion = 0
    var observationListener: ((com.pianoscorefollower.app.performance.PerformanceObservation, com.pianoscorefollower.app.performance.FollowPosition) -> Unit)? = null

    @Synchronized fun attach(listeningEngine: ListeningEngine?, timeline: ScoreTimeline, bpm: Float,
        startTick: Long = 0, automatic: Boolean = false) {
        detach()
        graph = ScoreGraph.from(timeline)
        follower=SymbolicScoreFollower(graph,startTick,automatic)
        neuralActive=false
        audioNotBeforeMs=System.nanoTime()/1_000_000
        onsetPulse = 0
        _state.value = FollowState(isFollowing = true, isReady = graph.events.isNotEmpty())
        publish(follower.position)
        listening = listeningEngine
        val version=inputVersion
        listeningEngine?.frameListener = { frame -> onFrame(frame,version) }
    }

    @Synchronized fun detach() {
        inputVersion++
        listening?.frameListener = null
        listening = null
        _state.update { it.copy(isFollowing = false, confidence = 0f, isOnset = false) }
    }

    fun seekToStart() = seekTo(0)
    @Synchronized fun seekTo(tick: Long) {
        audioNotBeforeMs=System.nanoTime()/1_000_000;neuralActive=false
        follower.seek(tick)
        publish(follower.position)
    }
    @Synchronized fun accept(observation: com.pianoscorefollower.app.performance.PerformanceObservation) {
        if (!_state.value.isFollowing) return
        val position = follower.accept(observation)
        publish(position)
        observationListener?.invoke(observation, position)
        val peak=observation.pitches.maxOrNull() ?: 0f
        _state.update { it.copy(midiNotes = if(observation.active && peak>0f)
            observation.pitches.indices.filter { n -> observation.pitches[n]>=maxOf(if(observation.exact)0.001f else .5f,peak*0.5f) }
                .sortedByDescending { n -> observation.pitches[n] }.take(6) else emptyList()) }
    }
    private fun publish(p: com.pianoscorefollower.app.performance.FollowPosition) {
        _state.update { it.copy(cursorTick = p.tick, measure = p.measure, confidence = p.confidence,
            bestSimilarity = p.similarity, position = p,
            targetMidiNotes = graph.events.getOrNull(p.eventIndex)?.pitches?.toList().orEmpty(),
            targetPitchClasses = graph.events.getOrNull(p.eventIndex)?.pitches?.map { n -> n % 12 }?.distinct().orEmpty()) }
    }
    @Synchronized fun acceptAudio(frame:AudioFrame) { onFrame(frame,inputVersion) }
    @Synchronized private fun onFrame(frame: AudioFrame, version: Int) {
        if(version!=inputVersion) return
        if(!_state.value.isFollowing)return
        val now=frame.sampleTimeMs.takeIf{it>0}?:System.nanoTime()/1_000_000
        if(now<audioNotBeforeMs)return
        val base=now-frame.audioTimeMs
        var attack=false
        for(n in frame.neural){
            if(base+n.onsetTimeMs<audioNotBeforeMs)continue
            val onset=n.attacks.any{it>=.5f};attack=attack||onset
            neuralActive=onset||n.pitches.any{it>=.5f}
            accept(com.pianoscorefollower.app.performance.PerformanceObservation(
                base+n.availableTimeMs,n.pitches,onset,neuralActive,
                analysisLatencyMs=n.availableTimeMs-n.onsetTimeMs,attacks=n.attacks))
        }
        val sounding=frame.levelDb>=-65f&&neuralActive
        publish(follower.advance(now,sounding))
        _state.update { it.copy(chroma = frame.chroma.toList(),
            topPitchClasses = if (sounding) topPitchClasses(frame.chroma) else emptyList(),
            isOnset = attack, onsetPulse = if (attack) ++onsetPulse else onsetPulse) }
    }

    /**
     * Loudest pitch classes, strongest first.
     *
     * The cutoff is relative to the strongest class, not absolute: striking one piano
     * key sounds faintly of its own harmonics (a C carries some G and E), so a fixed
     * floor would report three notes where the player played one and make a correct
     * reading look like a wrong one.
     */
    private fun topPitchClasses(chroma: FloatArray): List<Int> {
        var peak = 0f
        for (value in chroma) if (value > peak) peak = value
        if (peak <= 0f) return emptyList()

        val cutoff = peak * TOP_PITCH_CLASS_RATIO
        return chroma.indices
            .filter { chroma[it] >= cutoff }
            .sortedByDescending { chroma[it] }
            .take(TOP_PITCH_CLASSES)
    }

    private companion object {
        /** Same margin the onset detector uses to decide the input is above the room. */
        const val DOMINANT_GATE_MARGIN_DB = 6f
        const val MIN_DOMINANT_LEVEL_DB = -60f
        const val TOP_PITCH_CLASSES = 3

        /** A class is only named if it carries at least this share of the loudest one. */
        const val TOP_PITCH_CLASS_RATIO = 0.4f
    }
}
