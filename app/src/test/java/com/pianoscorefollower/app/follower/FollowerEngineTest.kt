package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.audio.AudioFrame
import com.pianoscorefollower.app.audio.ListeningEngine
import com.pianoscorefollower.app.performance.FollowStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowerEngineTest {
    private fun timeline()=ScoreTimeline(960,listOf(60,62,64).mapIndexed { i,p ->
        ScoreEvent(i*960L,i,FloatArray(12),1,true,intArrayOf(p))
    },2880,3)

    @Test fun selectedStartIsPublishedBeforeTheFirstSound() {
        val engine=FollowerEngine();engine.attach(null,timeline(),120f,2200)
        assertEquals(2200L,engine.state.value.cursorTick)
        assertEquals(2,engine.state.value.measure)
        assertEquals(FollowStatus.WAITING,engine.state.value.position.status)
    }

    @Test fun delayedFrameFromOldMicrophoneSessionCannotMoveNewSession() {
        val input=ListeningEngine();val engine=FollowerEngine()
        engine.attach(input,timeline(),120f)
        val oldReceiver=input.frameListener!!
        engine.detach();engine.attach(null,timeline(),120f,960)
        oldReceiver(AudioFrame(-20f,-20f,-80f,true,1f,1f,FloatArray(12),
            pitches=FloatArray(128).also { it[62]=1f },sampleTimeMs=1000))
        assertEquals(960L,engine.state.value.cursorTick)
        assertEquals(FollowStatus.WAITING,engine.state.value.position.status)
    }
    @Test fun onsetBeforeAnExplicitSeekCannotMoveItsNewPosition(){
        val engine=FollowerEngine();engine.attach(null,timeline(),120f)
        val before=System.nanoTime()/1_000_000-1000
        engine.seekTo(960)
        val keys=FloatArray(128).also{it[62]=1f}
        engine.acceptAudio(AudioFrame(-20f,-20f,-80f,true,1f,1f,FloatArray(12),
            sampleTimeMs=before+2000,audioTimeMs=2000,
            neural=listOf(com.pianoscorefollower.app.audio.NeuralPianoFrame(0,2000,keys,keys,FloatArray(128)))))
        assertEquals(960L,engine.state.value.cursorTick)
        assertEquals(FollowStatus.WAITING,engine.state.value.position.status)
    }
}
