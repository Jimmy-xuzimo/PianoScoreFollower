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
    val silentInput:Boolean=false,
    val audioSource:Int=MediaRecorder.AudioSource.MIC,
) {
    val normalizedLevel: Float
        get() = AudioConfig.normalizedLevel(levelDb)
}

/**
 * Streams microphone audio through [AudioAnalyzer] on a dedicated realtime thread and
 * publishes the resulting level/onset signal. Capture is deliberately kept out of
 * coroutines so the read loop is never at the mercy of a dispatcher.
 */
class ListeningEngine(private val supportsUnprocessed:Boolean=false) {
    @Volatile var pianoModel:MagentaPianoModel?=null
    @Volatile var neuralEnabled=false
    /** Debug source comparison; no external entry point exists in release builds. */
    @Volatile var sourceOverride:Int?=null

    private val _state = MutableStateFlow(ListeningState())
    val state: StateFlow<ListeningState> = _state.asStateFlow()

    @Volatile
    private var running = false

    /**
     * Invoked on the analysis worker for each frame. A capture session owns its
     * callback; switching consumers requires stopping and starting capture.
     */
    @Volatile
    var frameListener: ((AudioFrame) -> Unit)? = null

    /**
     * Turns on the fundamental-frequency estimate for the capture that is about to
     * start. Read once when the analyzer is built, so it has to be set before [start].
     */
    @Volatile
    var pitchDetectionEnabled: Boolean = false
    @Volatile var tuningCents: Float = 0f

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    /** Returns true when capture is running afterwards. */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        worker?.let {
            if (it.isAlive) it.join(WORKER_JOIN_TIMEOUT_MS)
            if (it.isAlive) { reportError("正在释放音频设备，请稍后重试"); return false }
        }

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
        _state.update { it.copy(isRunning = true, errorMessage = null, sampleRate = sampleRate,silentInput=false,audioSource=record.audioSource) }

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
    fun releaseModel() {
        stop()
        val owner=worker;val model=pianoModel;pianoModel=null
        Thread({owner?.join();model?.close()},"piano-model-release").start()
    }

    private data class CapturedHop(val samples: FloatArray, val timeMs: Long)

    private fun captureLoop(record: AudioRecord, sampleRate: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val hopSize=AudioConfig.HOP_SIZE
        val receiver=frameListener
        val queue=java.util.concurrent.ArrayBlockingQueue<CapturedHop>(8)
        val analysis=Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val analyzer=AudioAnalyzer(sampleRate=sampleRate,model=pianoModel.takeIf{neuralEnabled},initialTuningCents=tuningCents).also { it.pitchDetectionEnabled=pitchDetectionEnabled }
            var onsetCount=0;var onsetPulse=0L;var lastOnsetFlux=0f
            try {
                while(running) {
                    val hop=queue.take()
                    if(!running) break
                    analyzer.tuningCents=tuningCents
                    val result=analyzer.accept(hop.samples).copy(sampleTimeMs=hop.timeMs)
                    if(!running) break
                    if(result.isOnset) { onsetCount++;onsetPulse++;lastOnsetFlux=result.flux }
                    receiver?.invoke(result)
                    _state.update { it.copy(isRunning=true,levelDb=result.levelDb,peakDb=result.peakDb,
                        noiseFloorDb=result.noiseFloorDb,onsetCount=onsetCount,onsetPulse=onsetPulse,lastOnsetFlux=lastOnsetFlux) }
                }
            } catch (_: InterruptedException) {
                // The capture owner stops and joins this worker before another session starts.
            } catch (error: Exception) {
                reportError("音频分析已停止：${error.message}");running=false
                try { record.stop() } catch (_: Exception) { }
            }
        },"piano-analysis")
        val shortBuffer=ShortArray(hopSize)
        val timestamp=android.media.AudioTimestamp()
        val fallbackBase=System.nanoTime()
        var capturedFrames=0L;var lastTimeMs=0L;var filled=0;var zeroFrames=0L
        analysis.start()
        try {
            while(running) {
                val read=record.read(shortBuffer,filled,hopSize-filled,AudioRecord.READ_BLOCKING)
                if(read<0) {
                    if(running) reportError("麦克风输入中断（$read），请重新开始")
                    break
                }
                if(read==0) continue
                filled+=read
                if(filled<hopSize) continue
                filled=0;capturedFrames+=hopSize
                // Some emulator HALs return +/-1 dither instead of exact zeros.
                if(shortBuffer.all{kotlin.math.abs(it.toInt())<=2})zeroFrames+=hopSize else zeroFrames=0
                val silent=zeroFrames>=sampleRate*3L
                if(silent!=_state.value.silentInput)_state.update{it.copy(silentInput=silent)}
                val timestampStatus=record.getTimestamp(timestamp,android.media.AudioTimestamp.TIMEBASE_MONOTONIC)
                val sampleNs=if(timestampStatus==0 && timestamp.nanoTime>0)
                    timestamp.nanoTime+(capturedFrames-timestamp.framePosition)*1_000_000_000L/sampleRate
                    else fallbackBase+capturedFrames*1_000_000_000L/sampleRate
                val timeMs=maxOf(lastTimeMs,sampleNs/1_000_000L);lastTimeMs=timeMs
                val owned=FloatArray(hopSize) { shortBuffer[it]/32768f }
                if(!queue.offer(CapturedHop(owned,timeMs))) {
                    reportError("设备音频处理跟不上输入，已暂停跟谱，请重试")
                    break
                }
            }
        } catch(error: Exception) {
            if(running) reportError("音频采集已停止：${error.message}")
        } finally {
            running=false
            try { record.stop() } catch (_: Exception) { }
            record.release();recorder=null
            analysis.interrupt()
            var interrupted=false
            while(analysis.isAlive) try { analysis.join() } catch (_: InterruptedException) { interrupted=true }
            _state.update { it.copy(isRunning=false,levelDb=AudioConfig.MIN_LEVEL_DB,peakDb=AudioConfig.MIN_LEVEL_DB) }
            worker=null
            if(interrupted) Thread.currentThread().interrupt()
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
        val sources = (listOfNotNull(sourceOverride)+
            (if(supportsUnprocessed)listOf(MediaRecorder.AudioSource.UNPROCESSED)else emptyList())+
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION,MediaRecorder.AudioSource.MIC)).distinct()

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
