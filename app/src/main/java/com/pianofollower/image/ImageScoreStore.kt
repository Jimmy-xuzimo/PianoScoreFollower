package com.pianofollower.image

import android.content.Context
import java.io.File

/**
 * Remembers the imported page stack across app restarts.
 *
 * The page images themselves already live in the app's private storage; what is
 * missing without this is the *list* — which files exist, in which order, and how
 * long the whole stack should take to scroll. That list is written to a tiny text
 * index next to the images, so reopening the app shows the same score instead of an
 * empty screen.
 *
 * The format is a handful of `key=value` header lines followed by one tab-separated
 * record per page. It is deliberately not JSON: it keeps the reader allocation-light
 * and needs no parsing dependency.
 */
internal class ImageScoreStore(context: Context) {

    private val directory = File(context.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    private val indexFile: File
        get() = File(directory, INDEX_FILE)

    data class Snapshot(
        val pages: List<ImageScorePage>,
        val durationSeconds: Int,
        val scrollOffset: Int,
    )

    fun load(): Snapshot {
        val file = indexFile
        if (!file.exists()) return emptySnapshot()

        return runCatching {
            var duration = DEFAULT_SCROLL_DURATION_SECONDS
            var scroll = 0
            val pages = ArrayList<ImageScorePage>()

            file.forEachLine { line ->
                when {
                    line == HEADER || line.isBlank() -> Unit
                    line.startsWith(DURATION_KEY) ->
                        duration = line.substringAfter('=').toIntOrNull() ?: duration
                    line.startsWith(SCROLL_KEY) ->
                        scroll = line.substringAfter('=').toIntOrNull() ?: scroll
                    else -> parsePage(line)?.let { pages += it }
                }
            }
            Snapshot(pages, duration, scroll)
        }.getOrElse { emptySnapshot() }
    }

    fun save(pages: List<ImageScorePage>, durationSeconds: Int, scrollOffset: Int) {
        runCatching {
            val builder = StringBuilder()
            builder.append(HEADER).append('\n')
            builder.append(DURATION_KEY).append('=').append(durationSeconds).append('\n')
            builder.append(SCROLL_KEY).append('=').append(scrollOffset).append('\n')
            pages.forEach { page ->
                builder.append(page.id).append('\t')
                    .append(page.file.name).append('\t')
                    .append(page.width).append('\t')
                    .append(page.height).append('\t')
                    .append(escape(page.sourceName)).append('\n')
            }
            indexFile.writeText(builder.toString())
        }
    }

    /** Drops the index; used when the whole stack is cleared. */
    fun clear() {
        runCatching { indexFile.delete() }
    }

    private fun parsePage(line: String): ImageScorePage? {
        // limit = 5 keeps a tab inside the source name from being read as a field
        // boundary; the name is escaped on write, so the tail is one field.
        val parts = line.split('\t', limit = 5)
        if (parts.size < 5) return null
        val id = parts[0].toLongOrNull() ?: return null
        val file = File(directory, parts[1])
        // A file removed behind the app's back would otherwise show as a blank page.
        if (!file.exists() || file.length() == 0L) return null
        val width = parts[2].toIntOrNull() ?: return null
        val height = parts[3].toIntOrNull() ?: return null
        return ImageScorePage(id, file, width, height, unescape(parts[4]))
    }

    private fun emptySnapshot() = Snapshot(emptyList(), DEFAULT_SCROLL_DURATION_SECONDS, 0)

    private fun escape(value: String): String {
        val builder = StringBuilder(value.length)
        value.forEach { c ->
            when (c) {
                '\\' -> builder.append("\\\\")
                '\t' -> builder.append("\\t")
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
                    't' -> builder.append('\t')
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
        const val DIRECTORY = "image-score"
        const val INDEX_FILE = "index.txt"
        const val HEADER = "pianofollower-image-score-v1"
        const val DURATION_KEY = "duration"
        const val SCROLL_KEY = "scroll"
    }
}
