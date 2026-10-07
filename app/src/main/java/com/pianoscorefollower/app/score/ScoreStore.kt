package com.pianoscorefollower.app.score

import android.content.Context
import java.io.File

/**
 * Keeps the most recently opened score on disk.
 *
 * The workspace used to forget everything the moment the process died, so a player
 * who imported a MIDI or MusicXML file had to find it again on every launch. The
 * renderable bytes and a three-line descriptor are written to the app's private
 * storage and read back at startup.
 *
 * What is stored is the *rendered* payload: a MIDI source has already been converted
 * to MusicXML by [LoadedScore.prepare], so restoring it must not run the converter a
 * second time — hence the render format travels alongside the source format.
 */
internal class ScoreStore(context: Context) {

    private val directory = File(context.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    private val dataFile: File
        get() = File(directory, DATA_FILE)

    private val metaFile: File
        get() = File(directory, META_FILE)

    fun save(score: LoadedScore) {
        runCatching {
            dataFile.writeBytes(score.bytes)
            metaFile.writeText(
                buildString {
                    append(escape(score.displayName)).append('\n')
                    append(score.sourceFormat.name).append('\n')
                    append(score.renderFormat.name).append('\n')
                }
            )
        }
    }

    fun load(): LoadedScore? {
        val data = dataFile
        val meta = metaFile
        if (!data.exists() || data.length() == 0L || !meta.exists()) return null

        return runCatching {
            val lines = meta.readLines()
            if (lines.size < 3) return null
            val displayName = unescape(lines[0]).ifBlank { "上次打开的乐谱" }
            val sourceFormat = ScoreFormat.valueOf(lines[1])
            val renderFormat = ScoreFormat.valueOf(lines[2])
            LoadedScore(
                displayName = displayName,
                sourceFormat = sourceFormat,
                renderFormat = renderFormat,
                bytes = data.readBytes(),
            )
        }.getOrNull()
    }

    fun clear() {
        runCatching {
            dataFile.delete()
            metaFile.delete()
        }
    }

    private fun escape(value: String): String {
        val builder = StringBuilder(value.length)
        value.forEach { c ->
            when (c) {
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun unescape(value: String): String {
        val builder = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> builder.append('\\')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    else -> builder.append(value[i + 1])
                }
                i += 2
            } else {
                builder.append(c)
                i++
            }
        }
        return builder.toString()
    }

    private companion object {
        const val DIRECTORY = "score"
        const val DATA_FILE = "current.bin"
        const val META_FILE = "current.meta"
    }
}
