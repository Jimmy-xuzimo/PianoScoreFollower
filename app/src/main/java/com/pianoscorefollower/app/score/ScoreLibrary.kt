package com.pianoscorefollower.app.score

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LibraryEntry(val id: String, val title: String, val source: ScoreFormat, val tick: Long, val updated: Long)

/** Each item commits metadata last. Migration leaves the legacy files untouched. */
class ScoreLibrary(context: Context) {
    private val root = File(context.filesDir, "library").apply { mkdirs() }
    private fun dir(id: String): File { require(id.matches(Regex("[a-zA-Z0-9-]+"))); return File(root, id) }
    fun list(): List<LibraryEntry> = root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { d ->
        val meta = File(d, "entry.json")
        if (!meta.exists()) null else {
            val j = JSONObject(meta.readText())
            LibraryEntry(d.name, j.getString("title"), ScoreFormat.valueOf(j.getString("source")), j.optLong("tick"), j.getLong("updated"))
        }
    }.sortedByDescending { it.updated }
    fun save(score: LoadedScore): String {
        val id = UUID.randomUUID().toString(); val d = dir(id); d.mkdirs()
        atomicWrite(File(d, "render.bin"), score.bytes)
        score.originalBytes?.let { atomicWrite(File(d, "original.bin"), it) }
        val j = JSONObject().put("title", score.displayName).put("source", score.sourceFormat.name)
            .put("render", score.renderFormat.name).put("updated", System.currentTimeMillis()).put("tick", 0)
        atomicWrite(File(d, "entry.json"), j.toString().toByteArray())
        return id
    }
    fun open(id: String): LoadedScore {
        val d = dir(id); val j = JSONObject(File(d, "entry.json").readText())
        return LoadedScore(j.getString("title"), ScoreFormat.valueOf(j.getString("source")),
            ScoreFormat.valueOf(j.getString("render")), File(d, "render.bin").readBytes(),
            originalBytes = File(d, "original.bin").takeIf { it.exists() }?.readBytes())
    }
    fun position(id: String, tick: Long) {
        val f = File(dir(id), "entry.json"); if (!f.exists()) return
        val j = JSONObject(f.readText()).put("tick", tick).put("updated", System.currentTimeMillis())
        atomicWrite(f, j.toString().toByteArray())
    }
    fun rename(id: String, title: String) {
        require(title.isNotBlank())
        val f = File(dir(id), "entry.json")
        atomicWrite(f, JSONObject(f.readText()).put("title", title.trim()).toString().toByteArray())
    }
    fun delete(id: String) {
        val d = dir(id)
        require(d.canonicalFile.parentFile == root.canonicalFile)
        check(d.deleteRecursively()) { "删除曲目失败" }
    }
    fun migrate(context: Context) {
        val marker = File(root, "legacy-v1.done")
        if (marker.exists()) return
        ScoreStore(context).load()?.let { legacy ->
            if (list().none { it.title == legacy.displayName }) save(legacy)
        }
        atomicWrite(marker, byteArrayOf(1))
    }
}
