package com.pianoscorefollower.app.audio

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianoscorefollower.app.follower.*
import com.pianoscorefollower.app.assessment.*
import com.pianoscorefollower.app.midi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

// A single owner arbitrates microphone, MIDI, tuner and recording lifetimes.
enum class InputSource { MICROPHONE, MIDI }
data class RecordingState(val recording: Boolean = false, val sessions: List<RecordingSession> = emptyList(),
    val options: NotationOptions = NotationOptions(), val playingId: String? = null, val message: String? = null)
data class ReferenceState(val preparing:Boolean=false,val ready:Boolean=false,val seconds:Int=0,val error:String?=null)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val listening = ListeningEngine(application.getSystemService(android.media.AudioManager::class.java)
        ?.getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)=="true")
    private val follower = FollowerEngine()
    private val _referenceState=MutableStateFlow(ReferenceState())
    val referenceState=_referenceState.asStateFlow()
    @Volatile private var pianoModel:MagentaPianoModel?=null
    private val referenceWorker=Dispatchers.Default.limitedParallelism(1)
    fun prepareFollowing() {
        if(pianoModel!=null){_referenceState.value=ReferenceState(ready=true);return}
        if(_referenceState.value.preparing)return
        _referenceState.value=ReferenceState(preparing=true)
        viewModelScope.launch(referenceWorker) {
            runCatching {
                val model=MagentaPianoModel(getApplication<Application>().assets)
                try { currentCoroutineContext().ensureActive() } catch(error:CancellationException) { model.close();throw error }
                pianoModel=model;listening.pianoModel=model
                _referenceState.value=ReferenceState(ready=true)
            }.onFailure {_referenceState.value=ReferenceState(error="跟谱模型加载失败：${it.message}")}
        }
    }
    private val calibration = CalibrationEngine()
    val midi = MidiInput(application)
    private val store = RecordingStore(application)
    private var playback: android.media.MediaPlayer? = null
    private var playbackVersion = 0
    private val recordingLock = Any()
    private val messages = ArrayList<MidiMessage>()
    private var recordingStart = -1L
    private var recordingId = ""
    private var recordingTitle = ""
    private var checkpoint: Job? = null
    private val recordingStorage = Dispatchers.IO.limitedParallelism(1)
    private var resumeTick = 0L
    private var activeTimeline = ScoreTimeline.EMPTY
    @Volatile private var replayVersion = 0
    private val performancePrefs = application.getSharedPreferences("performance",0)
    private val _source = MutableStateFlow(runCatching { InputSource.valueOf(performancePrefs.getString("source",null) ?: "MICROPHONE") }.getOrDefault(InputSource.MICROPHONE))
    val source = _source.asStateFlow()
    private val assessor = AssessmentEngine()
    private var assessmentPublishedAt=0L
    private val _assessment = MutableStateFlow(AssessmentState())
    val assessment = _assessment.asStateFlow()
    private val _assessmentMode = MutableStateFlow(false)
    val assessmentMode = _assessmentMode.asStateFlow()
    private val _assessmentOptions = MutableStateFlow(AssessmentOptions(
        performancePrefs.getFloat("pitchWeight", .7f).coerceIn(.1f,.9f),
        performancePrefs.getFloat("practiceSpeed",1f).coerceIn(.25f,2f)))
    val assessmentOptions = _assessmentOptions.asStateFlow()
    fun setAssessmentMode(value: Boolean) { stopListening(); _assessmentMode.value=value; resetAssessment() }
    fun setAssessmentOptions(value: AssessmentOptions) {
        stopListening(); _assessmentOptions.value=value; resetAssessment()
        performancePrefs.edit().putFloat("pitchWeight",value.pitchWeight).putFloat("practiceSpeed",value.speed).apply()
    }
    fun bindAssessmentScore(timeline: ScoreTimeline) { if(activeTimeline!=timeline) { stopListening();resetAssessment();activeTimeline=timeline } }
    fun resetAssessment() { assessor.clear(); _assessment.value=assessor.snapshot() }
    fun finishAssessment() { stopListening(); assessor.finish(); _assessment.value=assessor.snapshot() }
    private val _recording = MutableStateFlow(RecordingState())
    val recording = _recording.asStateFlow()
    val listeningState = listening.state
    val followState = follower.state
    val calibrationState = calibration.state
    init {
        follower.observationListener = { observation, position ->
            if (_assessmentMode.value) {
                assessor.accept(observation,position)
                val now=System.nanoTime()/1_000_000
                if(now-assessmentPublishedAt>=250) { assessmentPublishedAt=now;_assessment.value=assessor.snapshot() }
            }
        }
        val tuningPrefs=application.getSharedPreferences("piano_audio",0)
        val storedTuning=tuningPrefs.getFloat("tuning",0f)
        if(tuningPrefs.contains("tuning") && calibration.restore(storedTuning)) listening.tuningCents=storedTuning
        midi.listener = { observation -> if (source.value == InputSource.MIDI) follower.accept(observation) }
        midi.messageListener = { event -> synchronized(recordingLock) {
            if (recordingStart >= 0) {
                if (messages.size < 500_000) messages += event.copy(timeUs = (event.timeUs - recordingStart).coerceAtLeast(0))
                if (event.timeUs - recordingStart >= 3_600_000_000L || messages.size >= 500_000) viewModelScope.launch { stopRecording() }
            }
        } }
        midi.disconnected = { viewModelScope.launch {
            val wasRecording=_recording.value.recording
            if (source.value == InputSource.MIDI) stopListening()
            stopRecording()
            _recording.update { it.copy(message = if(wasRecording) "MIDI 已断开，正在保存录制…" else "MIDI 设备已断开") }
        } }
        viewModelScope.launch {
            listening.state.map { it.isRunning to it.errorMessage }.distinctUntilChanged().collect { (running,error) ->
                if (!running && error != null) {
                    if (source.value == InputSource.MICROPHONE) stopListening()
                    if (calibration.state.value.isRunning) stopCalibration()
                }
            }
        }
        refreshRecordings()
        if (com.pianoscorefollower.app.BuildConfig.DEBUG) com.pianoscorefollower.app.DebugHooks.command = { action, argument ->
            when (action) {
                "midi" -> midi.inject(argument.split(',').map { it.trim().toInt().toByte() }.toByteArray())
                "disconnectMidi" -> midi.simulateDisconnect()
                "record" -> startRecording()
                "stopRecord" -> stopRecording()
                "replayAudio" -> debugReplay(argument)
                "dump" -> {
                    val j=org.json.JSONObject().put("source",source.value.name).put("following",followState.value.isFollowing)
                        .put("tick",followState.value.cursorTick).put("measure",followState.value.measure)
                        .put("confidence",followState.value.confidence).put("recording",recording.value.recording)
                        .put("sessions",recording.value.sessions.size).put("connected",midi.state.value.connected?.name)
                        .put("keys",midi.state.value.pressed.toString()).put("audioRunning",listeningState.value.isRunning)
                        .put("levelDb",listeningState.value.levelDb).put("silentInput",listeningState.value.silentInput)
                        .put("audioSource",listeningState.value.audioSource).put("referenceReady",referenceState.value.ready)
                        .put("referenceError",referenceState.value.error)
                        .put("status",followState.value.position.status.name)
                        .put("modelInvocations",pianoModel?.invocations?:0)
                        .put("modelMeanMs",pianoModel?.let{it.totalInferenceMs/maxOf(1,it.invocations)}?:0.0)
                        .put("modelMaxMs",pianoModel?.maxInferenceMs?:0.0)
                    java.io.File(application.getExternalFilesDir(null),"debug-audio.json").writeText(j.toString())
                }
                "audioSource" -> {
                    listening.sourceOverride=argument.toIntOrNull()?.takeIf{it in listOf(1,6,9)}
                    stopListening()
                    startFollowing(activeTimeline,120f,0)
                }
            }
        }
    }
    fun setSource(value: InputSource) {
        if (_source.value == value) return
        stopListening(); resetAssessment(); _source.value = value; performancePrefs.edit().putString("source",value.name).apply()
    }
    fun hasRecordPermission() = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun startFollowing(timeline: ScoreTimeline, bpm: Float, tick: Long = resumeTick, automatic: Boolean = false) {
        activeTimeline = timeline
        stopPlayback(); stopCalibration()
        if (_recording.value.recording) { listening.reportError("请先停止录制再开始跟谱"); return }
        if (source.value == InputSource.MIDI) {
            if (midi.state.value.connected == null) { listening.reportError("请先连接 USB MIDI 键盘"); return }
            prepareAssessment(timeline,tick); follower.attach(null, timeline, bpm, tick, automatic && !_assessmentMode.value)
        } else {
            if(pianoModel==null) { listening.reportError(_referenceState.value.error ?: "正在加载钢琴识别模型，请稍候");return }
            prepareAssessment(timeline,tick)
            listening.pitchDetectionEnabled = false
            listening.neuralEnabled=true
            follower.attach(listening, timeline, bpm, tick, automatic && !_assessmentMode.value)
            if (!listening.start()) follower.detach()
        }
    }
    private fun prepareAssessment(timeline: ScoreTimeline, tick: Long) {
        if (!_assessmentMode.value) return
        val previous=_assessment.value
        if (!previous.started || previous.finished) assessor.begin(ScoreGraph.from(timeline),tick,_assessmentOptions.value,source.value==InputSource.MICROPHONE)
        else assessor.resume(System.nanoTime()/1_000_000)
        _assessment.value=assessor.snapshot()
    }
    fun startListening() { if (source.value == InputSource.MICROPHONE) listening.start() }
    fun startCalibration() {
        stopPlayback(); stopListening()
        if (_recording.value.recording) stopRecording()
        listening.pitchDetectionEnabled = true
        listening.neuralEnabled=false
        calibration.start(); listening.frameListener = calibration::onFrame
        listening.start()
    }
    fun stopCalibration() {
        if (!calibration.state.value.isRunning) return
        calibration.state.value.takeIf { it.hasCalibration }?.let {
            listening.tuningCents = it.offsetCents
            getApplication<Application>().getSharedPreferences("piano_audio", 0).edit().putFloat("tuning", it.offsetCents).apply()
        }
        calibration.stop(); listening.frameListener = null
        listening.pitchDetectionEnabled = false; listening.stop()
    }
    fun resetCalibration() {
        calibration.reset(); listening.tuningCents = 0f
        getApplication<Application>().getSharedPreferences("piano_audio",0).edit().remove("tuning").apply()
    }
    fun stopListening() { replayVersion++;assessor.pause(System.nanoTime()/1_000_000); _assessment.value=assessor.snapshot(); resumeTick = followState.value.cursorTick; follower.detach(); listening.stop() }
    fun seekToStart() = seekTo(0)
    fun seekTo(tick: Long) { resumeTick = tick; follower.seekTo(tick) }
    fun onPermissionDenied() = listening.reportError("需要麦克风权限才能听音跟谱")
    fun clearError() = listening.clearError()
    fun setNotationOptions(options: NotationOptions) { _recording.update { it.copy(options = options) } }
    fun startRecording() {
        if (midi.state.value.connected == null) { _recording.update { it.copy(message = "请连接 USB MIDI 键盘后录制") }; return }
        stopPlayback(); stopCalibration(); stopListening()
        synchronized(recordingLock) {
            messages.clear(); recordingStart = System.nanoTime() / 1000
            recordingId = UUID.randomUUID().toString()
            recordingTitle = "演奏 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date())}"
        }
        checkpoint?.cancel()
        checkpoint = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val snapshot = synchronized(recordingLock) {
                    if (recordingStart < 0 || messages.isEmpty()) null else RecordingSession(recordingId,recordingTitle,
                        messages.toList(),System.nanoTime()/1000-recordingStart,_recording.value.options)
                }
                if (snapshot != null) runCatching { withContext(recordingStorage) { store.save(snapshot) } }
                    .onFailure { error -> _recording.update { it.copy(message="自动保存失败：${error.message}") } }
            }
        }
        _recording.update { it.copy(recording = true, message = null) }
    }
    fun stopRecording() {
        checkpoint?.cancel(); checkpoint = null
        val session = synchronized(recordingLock) {
            if (recordingStart < 0) return
            val duration = System.nanoTime() / 1000 - recordingStart
            recordingStart = -1
            if (messages.isEmpty()) null else RecordingSession(recordingId, recordingTitle,
                messages.toList(), duration, _recording.value.options)
        }
        _recording.update { it.copy(recording = false) }
        if (session == null) { _recording.update { it.copy(message = "没有收到琴键事件，未保存空录制") }; return }
        viewModelScope.launch {
            runCatching { withContext(recordingStorage) { store.save(session) } }
                .onSuccess { refreshRecordings(); _recording.update { it.copy(message = "录制已保存") } }
                .onFailure { error -> _recording.update { it.copy(sessions = listOf(session) + it.sessions, message = "保存失败，录制仍在内存中：${error.message}") } }
        }
    }
    fun updateSession(session: RecordingSession) { viewModelScope.launch {
        runCatching { withContext(recordingStorage) { store.save(session) } }.onSuccess { refreshRecordings() }
            .onFailure { e -> _recording.update { it.copy(message = e.message) } }
    } }
    fun deleteSession(id: String) { viewModelScope.launch {
        runCatching { withContext(recordingStorage) { store.delete(id) } }.onSuccess { refreshRecordings() }
            .onFailure { e -> _recording.update { it.copy(message = e.message) } }
    } }
    private fun refreshRecordings() { viewModelScope.launch {
        runCatching { withContext(recordingStorage) { store.list() } }
            .onSuccess { sessions -> _recording.update { it.copy(sessions = sessions) } }
            .onFailure { e -> _recording.update { it.copy(message = "读取录制失败：${e.message}") } }
    } }
    fun stopPlayback() { playbackVersion++; playback?.release(); playback = null; _recording.update { it.copy(playingId = null) } }
    fun playRecording(session: RecordingSession) {
        if (_recording.value.playingId == session.id) { stopPlayback(); return }
        stopPlayback(); stopRecording(); stopListening(); stopCalibration()
        val version = playbackVersion
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                java.io.File(getApplication<Application>().cacheDir, "recording-playback.mid").apply { writeBytes(MidiRecordingWriter.write(session)) }
            } }.onSuccess { file ->
                if (version != playbackVersion) return@onSuccess
                try {
                    playback = android.media.MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnPreparedListener { if (version == playbackVersion) { start(); _recording.update { it.copy(playingId=session.id) } } }
                        setOnCompletionListener { stopPlayback() }
                        setOnErrorListener { _,_,_ -> stopPlayback(); _recording.update { it.copy(message="录制回放失败") }; true }
                        prepareAsync()
                    }
                } catch (e: Exception) { stopPlayback(); _recording.update { it.copy(message="回放失败：${e.message}") } }
            }.onFailure { e -> _recording.update { it.copy(message=e.message) } }
        }
    }
    private fun debugReplay(path: String) {
        if (!com.pianoscorefollower.app.BuildConfig.DEBUG) return
        val root = getApplication<Application>().getExternalFilesDir(null)?.canonicalFile ?: return
        val file = java.io.File(path).canonicalFile
        if (!file.toPath().startsWith(root.toPath())) return
        val version = ++replayVersion
        listening.stop(); listening.frameListener = null
        follower.attach(null, activeTimeline, 120f, resumeTick)
        Thread({
            val analyzer = AudioAnalyzer(model=pianoModel)
            val trace=java.io.File(root,"debug-neural-trace.tsv").bufferedWriter()
            val predictions=java.io.File(root,"debug-model-frames.bin").outputStream().buffered()
            predictions.write(byteArrayOf(78,80,70,50)) // NPF2: uncentred frame labels
            java.io.DataInputStream(java.io.BufferedInputStream(file.inputStream())).use { input ->
                val raw = ByteArray(AudioConfig.HOP_SIZE * 4)
                val start = System.nanoTime()
                var samples = 0L
                try { while (followState.value.isFollowing && version == replayVersion) {
                    input.readFully(raw)
                    val buffer=java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val frame=analyzer.accept(FloatArray(AudioConfig.HOP_SIZE) { buffer.float })
                    samples += AudioConfig.HOP_SIZE
                    val ms=samples*1000/AudioConfig.SAMPLE_RATE
                    follower.acceptAudio(frame.copy(sampleTimeMs=start/1_000_000+ms))
                    val position=followState.value.position
                    trace.write("${samples.toDouble()/AudioConfig.SAMPLE_RATE}\t${position.tick}\t${position.confidence}\t${position.status}\t${frame.isOnset}\t${frame.levelDb}\t${(System.nanoTime()-start)/1_000_000}\n")
                    for(n in frame.neural){
                        val b=java.nio.ByteBuffer.allocate(16+128*8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        b.putLong(n.onsetTimeMs);b.putLong(n.availableTimeMs)
                        n.pitches.forEach{b.putFloat(it)};n.attacks.forEach{b.putFloat(it)}
                        predictions.write(b.array())
                    }
                    val remaining=ms-(System.nanoTime()-start)/1_000_000
                    if(remaining>0) Thread.sleep(remaining)
                } } catch (_: java.io.EOFException) { }
                finally { trace.close();predictions.close();if(version==replayVersion)follower.detach() }
            }
        }, "debug-audio-replay").start()
    }
    override fun onCleared() { stopPlayback(); stopRecording(); stopCalibration(); stopListening(); listening.releaseModel();midi.close()
        if (com.pianoscorefollower.app.BuildConfig.DEBUG) com.pianoscorefollower.app.DebugHooks.command=null }
}
