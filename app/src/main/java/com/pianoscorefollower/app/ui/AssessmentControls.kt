package com.pianoscorefollower.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pianoscorefollower.app.assessment.*
import kotlin.math.roundToInt

@Composable
fun AssessmentSettings(options: AssessmentOptions, enabled: Boolean, onChange: (AssessmentOptions)->Unit) {
    var pitch by remember(options) { mutableStateOf(options.pitchWeight) }
    var speed by remember(options) { mutableStateOf(options.speed) }
    Text("音高 ${(pitch*100).roundToInt()}% · 节奏 ${100-(pitch*100).roundToInt()}%", style=MaterialTheme.typography.bodySmall)
    Slider(pitch,{pitch=it},enabled=enabled,valueRange=.1f.. .9f,steps=7,
        onValueChangeFinished={onChange(options.copy(pitchWeight=pitch))})
    Text("练习速度 ${(speed*100).roundToInt()}% · 首个命中音作为计时起点",style=MaterialTheme.typography.bodySmall)
    Slider(speed,{speed=it},enabled=enabled,valueRange=.25f..2f,steps=6,
        onValueChangeFinished={onChange(options.copy(speed=speed))})
    Text("每个应弹音等权。节奏误差不超过 ¼ 拍得满分，达到 ½ 拍不得节奏分；多弹一个音扣 100÷总音符数 分。点按暂停会停止计时。",style=MaterialTheme.typography.bodySmall)
}

@Composable
fun AssessmentSummary(state: AssessmentState, onFinish:()->Unit, onRestart:()->Unit, onSeek:(Long)->Unit) {
    var details by remember { mutableStateOf(false) }
    Surface(tonalElevation=1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
            val score=state.score?.let { "%.1f 分".format(it) }
                ?: if(state.finished) "%.1f–%.1f 分（含待确认）".format(state.lowerScore,state.upperScore) else "已完成 ${state.judged}/${state.total} 音"
            Text((if(state.estimated) "听音估算 · " else "MIDI 评分 · ")+score,style=MaterialTheme.typography.titleSmall)
            Text("正确 ${state.correct} · 错音 ${state.wrong} · 漏音 ${state.missed} · 多弹 ${state.extra} · 节奏偏差 ${state.rhythmErrors}",style=MaterialTheme.typography.labelSmall)
            if(state.replaying) Text("当前为已评分段落，重弹不重复加分",style=MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(!state.finished) TextButton(onClick=onFinish) { Text("结束并评分") }
                TextButton(onClick={details=true}) { Text("查看明细") }
                if(state.finished) TextButton(onClick=onRestart) { Text("重新评分") }
            }
        }
    }
    if(details) AlertDialog(onDismissRequest={details=false}, title={Text("音符与节奏明细")},
        text={ Column {
            Text("应弹 ${state.total} 音；待弹 ${state.pending}，待确认 ${state.uncertain}。\n音高 %.1f · 节奏 %.1f".format(state.pitchScore,state.rhythmScore))
            if(state.estimated) Text("麦克风结果为估算，泛音与踏板可能影响逐音判定。待确认部分显示分数范围。",style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max=340.dp)) {
                items(state.notes.filter { it.grade!=NoteGrade.PENDING && (it.grade!=NoteGrade.CORRECT || it.rhythmCredit<1) },key={it.id}) { n ->
                    val grade=when(n.grade){NoteGrade.WRONG->"错音 → ${n.playedPitch?.let(::midiNoteName)}";NoteGrade.MISSED->"漏音";NoteGrade.UNCERTAIN->"待确认";else->"节奏偏差"}
                    TextButton(onClick={details=false;onSeek(n.tick)}) { Text("第 ${n.measure+1} 小节 · 第 ${n.occurrence+1} 遍 · ${midiNoteName(n.expectedPitch)} $grade"+
                        (n.timingErrorMs?.let { " · ${it.roundToInt()}ms" }?:"")) }
                }
                items(state.extras) { n -> TextButton(onClick={details=false;onSeek(n.tick)}) { Text("第 ${n.measure+1} 小节 · 多弹 ${midiNoteName(n.pitch)}") } }
            }
        } },confirmButton={TextButton(onClick={details=false}){Text("关闭")}})
}
