package com.pianoscorefollower.app.audio

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.pianoscorefollower.app.follower.FollowState
import com.pianoscorefollower.app.follower.FollowerEngine
import com.pianoscorefollower.app.follower.ScoreTimeline
import kotlinx.coroutines.flow.StateFlow

class AudioViewModel(application: Application) : AndroidViewModel(application) {

    private val listening = ListeningEngine()
    private val follower = FollowerEngine()
    private val calibration = CalibrationEngine()

    val listeningState: StateFlow<ListeningState> = listening.state
    val followState: StateFlow<FollowState> = follower.state
    val calibrationState: StateFlow<CalibrationState> = calibration.state

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Capture starts first: attaching the follower only makes sense once the
     * microphone is actually delivering frames.
     */
    fun startFollowing(timeline: ScoreTimeline, bpm: Float) {
        if (!listening.start()) return
        follower.attach(listening, timeline, bpm)
    }

    /** Microphone only, used before a score is available to align against. */
    fun startListening() {
        listening.start()
    }

    /**
     * Capture for the tuner. The fundamental estimate is only switched on here, and the
     * calibration engine takes over the frame callback from the follower.
     */
    fun startCalibration() {
        follower.detach()
        listening.pitchDetectionEnabled = true
        if (!listening.start()) return
        calibration.start()
        listening.frameListener = calibration::onFrame
    }

    fun stopCalibration() {
        calibration.stop()
        listening.frameListener = null
        listening.pitchDetectionEnabled = false
        listening.stop()
    }

    fun resetCalibration() {
        calibration.reset()
    }

    fun stopListening() {
        follower.detach()
        listening.stop()
    }

    fun seekToStart() {
        follower.seekToStart()
    }

    fun onPermissionDenied() {
        listening.reportError("需要麦克风权限才能跟随你的演奏")
    }

    fun clearError() {
        listening.clearError()
    }

    override fun onCleared() {
        stopListening()
    }
}
