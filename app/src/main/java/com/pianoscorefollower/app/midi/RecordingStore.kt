package com.pianoscorefollower.app.midi

import android.content.Context
import com.pianoscorefollower.app.score.atomicWrite
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecordingStore(context: Context) {
    private val root = File(context.filesDir, "recordings").apply { mkdirs() }
    private fun file(id: String): File { require(id.matches(Regex("[a-zA-Z0-9-]+"))); return File(root, "$id.json") }
    fun save(s: RecordingSession) {
        val o = s.options
        val events = JSONArray()
        s.messages.forEach { m -> events.put(JSONArray().put(m.timeUs).put(m.bytes.joinToString(",") { (it.toInt() and 255).toString() })) }
        val json = JSONObject().put("id", s.id).put("title", s.title).put("duration", s.durationUs)
            .put("bpm", o.bpm).put("numerator", o.numerator).put("denominator", o.denominator)
            .put("fifths", o.fifths).put("grid", o.grid).put("events", events)
        atomicWrite(file(s.id), json.toString().toByteArray())
    }
    fun list(): List<RecordingSession> = root.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending { it.lastModified() }.map { f ->
        val j = JSONObject(f.readText()); val a = j.getJSONArray("events")
        val messages = (0 until a.length()).map { i -> val e = a.getJSONArray(i)
            MidiMessage(e.getLong(0), e.getString(1).split(',').map { it.toInt().toByte() }.toByteArray()) }
        RecordingSession(j.getString("id"), j.getString("title"), messages, j.getLong("duration"),
            NotationOptions(j.getInt("bpm"),j.getInt("numerator"),j.getInt("denominator"),j.getInt("fifths"),j.getInt("grid")))
    }
    fun delete(id: String) { check(file(id).delete()) { "删除录制失败" } }
}
