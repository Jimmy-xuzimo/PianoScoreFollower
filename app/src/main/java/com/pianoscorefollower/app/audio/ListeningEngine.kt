package com.pianoscorefollower.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

data class ListeningState(
    val isRunning: Boolean = false,
    val levelDb: Float = AudioConfig.MIN_LEVEL_DB,
    val peakDb: Float = AudioConfig.MIN_LEVEL_DB,
    val noiseFloorDb: Float = AudioConfig.MIN_LEVEL_DB,
    val onsetCount: Int = 0,
    val onsetPulse: Long = 0L,
    val lastOnsetFlux: Float = 0f,
    /** Rate the microphone was actually opened at, which may differ from the request. */
    val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    val errorMessage: String? = null,
) {
    val normalizedLevel: Float
        get() = AudioConfig.normalizedLevel(levelDb)
}

/**
 * Streams microphone audio through [AudioAnalyzer] on a dedicated realtime thread and
 * publishes the resulting level/onset signal. Capture is deliberately kept out of
 * coroutines so the read loop is never at the mercy of a dispatcher.
 */
class ListeningEngine {

    private val _state = MutableStateFlow(ListeningState())
    val state: StateFlow<ListeningState> = _state.asStateFlow()

    @Volatile
    private var running = false

    /**
     * Invoked on the capture thread for every analysed frame. Kept as a plain
     * callback so the score follower can consume chroma without a second analysis
     * pass or an extra thread hop.
     */
    @Volatile
    var frameListener: ((AudioFrame) -> Unit)? = null

    /**
     * Turns on the fundamental-frequency estimate for the capture that is about to
     * start. Read once when the analyzer is built, so it has to be set before [start].
     */
    @Volatile
    var pitchDetectionEnabled: Boolean = false

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    /** Returns true when capture is running afterwards. */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        worker?.let { if (it.isAlive) it.join(WORKER_JOIN_TIMEOUT_MS) }

        _state.update { it.copy(errorMessage = null) }

        val opened = openRecorder() 
        if (opened == null) {
            reportError("无法打开麦克风，请检查权限，或确认没有被其他应用占用")
            return false
        }
        val record = opened.record
        val sampleRate = opened.sampleRate

        try {
            record.startRecording()
        } catch (error: Exception) {
            record.release()
            reportError("启动录音失败：${error.message ?: "未知原因"}")
            return false
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            reportError("麦克风未进入录音状态，请重试")
            return false
        }

        recorder = record
        running = true
        _state.update { it.copy(isRunning = true, errorMessage = null, sampleRate = sampleRate) }

        worker = Thread({ captureLoop(record, sampleRate) }, "piano-listening").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        try {
            // Unblocks the pending read so the worker can unwind promptly.
            recorder?.stop()
        } catch (_: Exception) {
            // Already stopped; the worker still owns the release.
        }
    }

    fun reportError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun captureLoop(record: AudioRecord, sampleRate: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val hopSize = AudioConfig.HOP_SIZE
        val shortBuffer = ShortArray(hopSize)
        val hopSamples = FloatArray(hopSize)
        val analyzer = AudioAnalyzer(sampleRate = sampleRate).also {
            it.pitchDetectionEnabled = pitchDetectionEnabled
        }

        var filled = 0
        var onsetCount = 0
        var onsetPulse = 0L
        var lastOnsetFlux = 0f

        while (running) {
            val read = record.read(shortBuffer, filled, hopSize - filled, AudioRecord.READ_BLOCKING)
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) break
                continue
            }

            // read() fills shortBuffer starting at `filled`, so the fresh samples live
            // at [filled, filled + read) — copying from index 0 would replay stale audio
            // whenever a short read splits a hop.
            for (i in 0 until read) {
                hopSamples[filled + i] = shortBuffer[filled + i] / 32768f
            }
            filled += read
            if (filled < hopSize) continue
            filled = 0

            val result = analyzer.accept(hopSamples)
            if (result.isOnset) {
                onsetCount++
                onsetPulse++
                lastOnsetFlux = result.flux
            }

            frameListener?.invoke(result)

            _state.update {
                it.copy(
                    isRunning = true,
                    levelDb = result.levelDb,
                    peakDb = result.peakDb,
                    noiseFloorDb = result.noiseFloorDb,
                    onsetCount = onsetCount,
                    onsetPulse = onsetPulse,
                    lastOnsetFlux = lastOnsetFlux,
                )
            }
        }

        try {
            record.stop()
        } catch (_: Exception) {
        }
        record.release()
        recorder = null
        worker = null

        _state.update {
            it.copy(
                isRunning = false,
                levelDb = AudioConfig.MIN_LEVEL_DB,
                peakDb = AudioConfig.MIN_LEVEL_DB,
            )
        }
    }

    private data class OpenedRecorder(val record: AudioRecord, val sampleRate: Int)

    /**
     * Opens the microphone at the first rate the device will accept, preferring a raw
     * capture path so the OS does not apply gain control or noise suppression, which
     * distort the spectrum we later match against the score.
     *
     * The rate is read back from the record rather than assumed: some devices silently
     * run at their native 48 kHz, and every downstream pitch calculation depends on
     * knowing which one we got.
     */
    @SuppressLint("MissingPermission")
    private fun openRecorder(): OpenedRecorder? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )

        for (rate in AudioConfig.SUPPORTED_SAMPLE_RATES) {
            val minBufferBytes = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferBytes <= 0) continue
            val bufferBytes = max(minBufferBytes, AudioConfig.HOP_SIZE * 2 * 8)

            for (source in sources) {
                val record = try {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferBytes)
                        .build()
                } catch (_: Exception) {
                    null
                }

                if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                    val actual = if (record.sampleRate > 0) record.sampleRate else rate
                    return OpenedRecorder(record, actual)
                }
                record?.release()
            }
        }
        return null
    }

    private companion object {
        const val WORKER_JOIN_TIMEOUT_MS = 500L
    }
}
