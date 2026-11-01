package com.pianoscorefollower.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianoscorefollower.app.audio.AudioViewModel
import com.pianoscorefollower.app.midi.*
import com.pianoscorefollower.app.score.MidiToMusicXml
import kotlinx.coroutines.*

@Composable
fun RecordingScreen(audio: AudioViewModel, onOpen: (RecordingSession) -> Unit) {
    val state by audio.recording.collectAsStateWithLifecycle()
    val midi by audio.midi.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportSession by remember { mutableStateOf<RecordingSession?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<RecordingSession?>(null) }
    var name by remember { mutableStateOf("") }
    var optionsFor by remember { mutableStateOf<RecordingSession?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(state.options) }
    var bpm by remember { mutableStateOf("120") }
    var numerator by remember { mutableStateOf("4") }
    var fifths by remember { mutableStateOf("0") }
    val midiExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri ->
        val session = exportSession
        if (uri != null && session != null) scope.launch {
            message = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(MidiRecordingWriter.write(session)) } ?: error("无法写入文件") }; "MIDI 已导出" }.getOrElse { "导出失败：${it.message}" }
        }
    }
    val xmlExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.recordare.musicxml+xml")) { uri ->
        val session = exportSession
        if (uri != null && session != null) scope.launch {
            message = runCatching { withContext(Dispatchers.IO) {
                val xml = MidiToMusicXml.convert(MidiRecordingWriter.write(session), session.options.grid, true)
                context.contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) } ?: error("无法写入文件")
            }; "MusicXML 已导出" }.getOrElse { "导出失败：${it.message}" }
        }
    }
    fun beginOptions(session: RecordingSession?) {
        optionsFor = session; options = session?.options ?: state.options
        bpm = options.bpm.toString(); numerator = options.numerator.toString(); fifths = options.fifths.toString(); showOptions = true
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("记录你的演奏", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("连接 USB MIDI，保留每次触键与踏板", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text(midi.connected?.name ?: "等待 USB MIDI 设备")
            }
            midi.endpoints.forEach { endpoint -> TextButton(onClick = { if (state.recording) audio.stopRecording(); audio.midi.select(endpoint) }) { Text(endpoint.name) } }
            midi.message?.let { Text(it) }
            Spacer(Modifier.height(12.dp))
            Text(if (midi.pressed.isEmpty()) "弹奏后显示音符" else midi.pressed.joinToString("  ") { midiNoteName(it) }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (n in 48..83) Box(Modifier.weight(1f).height(54.dp).background(
                    if (n in midi.pressed) MaterialTheme.colorScheme.primary else if (n%12 in listOf(1,3,6,8,10)) Color(0xff30333a) else Color(0xffdeded9)))
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { if (state.recording) audio.stopRecording() else audio.startRecording() }, modifier = Modifier.fillMaxWidth(), enabled = state.recording || midi.connected != null) {
                Icon(if (state.recording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null)
                Spacer(Modifier.width(8.dp)); Text(if (state.recording) "停止并保存" else "开始录制")
            }
            TextButton(onClick = { beginOptions(null) }, enabled = !state.recording) { Text("记谱设置 · ${state.options.bpm} BPM · ${state.options.numerator}/${state.options.denominator}") }
        } } }
        (message ?: state.message)?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.primary) } }
        item { Text("我的录制", style = MaterialTheme.typography.titleMedium) }
        if (state.sessions.isEmpty()) item { Text("录制结束后，演奏将保存在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.sessions, key = { it.id }) { session -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(session.title, fontWeight = FontWeight.SemiBold)
                        Text("${session.durationUs/1_000_000} 秒 · ${session.options.bpm} BPM · 1/${session.options.grid} 量化", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editing = session; name = session.title }) { Icon(Icons.Default.Edit, "重命名录制") }
                    IconButton(onClick = { audio.deleteSession(session.id) }) { Icon(Icons.Default.DeleteOutline, "删除录制") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { audio.playRecording(session) }) { Icon(if (state.playingId == session.id) Icons.Default.Pause else Icons.Default.PlayArrow, "回放录制") }
                    TextButton(onClick = { audio.stopRecording(); audio.stopPlayback(); onOpen(session) }) { Text("琴谱") }
                    TextButton(onClick = { exportSession = session; midiExport.launch("${session.title}.mid") }) { Text("MIDI") }
                    TextButton(onClick = { exportSession = session; xmlExport.launch("${session.title}.musicxml") }) { Text("五线谱") }
                    IconButton(onClick = { beginOptions(session) }) { Icon(Icons.Default.Tune, "重新量化") }
                }
            }
        } }
    }
    editing?.let { session -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("录制名称") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { audio.updateSession(session.copy(title = name.trim())); editing = null }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }) }
    if (showOptions) AlertDialog(onDismissRequest = { showOptions = false }, title = { Text("基础记谱设置") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(bpm, { bpm = it }, label = { Text("速度 BPM（20—300）") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(numerator, { numerator = it }, label = { Text("每小节拍数") }, modifier = Modifier.weight(1f), singleLine = true)
                Column { Text("拍号分母"); Row { listOf(2,4,8).forEach { d -> FilterChip(options.denominator==d, { options = options.copy(denominator=d) }, { Text(d.toString()) }) } } }
            }
            OutlinedTextField(fifths, { fifths = it }, label = { Text("调号：升号数 0—7，降号用负数") }, singleLine = true)
            Text("量化网格")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(4,8,16,32).forEach { g -> FilterChip(options.grid==g, { options=options.copy(grid=g) }, { Text("1/$g") }) } }
            Text("仅调整派生琴谱，原始演奏时间与踏板仍保留。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = {
        val value = runCatching { options.copy(bpm=bpm.toInt(), numerator=numerator.toInt(), fifths=fifths.toInt()) }.getOrNull()
        if (value == null) message = "请检查速度、拍号和调号范围"
        else { optionsFor?.let { audio.updateSession(it.copy(options=value)) } ?: audio.setNotationOptions(value); showOptions = false }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = { showOptions = false }) { Text("取消") } })
}

internal fun midiNoteName(note: Int): String = arrayOf("C","C♯","D","E♭","E","F","F♯","G","A♭","A","B♭","B")[note%12] + (note/12-1)
