package com.pianoscorefollower.app.score

import java.nio.charset.StandardCharsets

enum class ScoreFormat(val mimeType: String) {
    MIDI("audio/midi"),
    MUSIC_XML("application/vnd.recordare.musicxml+xml"),
    MUSIC_XML_COMPRESSED("application/vnd.recordare.musicxml"),
    UNKNOWN("application/octet-stream");

    val displayName: String
        get() = when (this) {
            MIDI -> "MIDI"
            MUSIC_XML -> "MusicXML"
            MUSIC_XML_COMPRESSED -> "MusicXML (MXL)"
            UNKNOWN -> "未知格式"
        }

    companion object {
        private val MIDI_HEADER = byteArrayOf(0x4D, 0x54, 0x68, 0x64) // "MThd"
        private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        fun detect(bytes: ByteArray, fileName: String?): ScoreFormat {
            if (bytes.startsWith(MIDI_HEADER)) return MIDI
            if (bytes.startsWith(ZIP_HEADER)) {
                return if (fileName?.endsWith(".mxl", true) == true) MUSIC_XML_COMPRESSED else UNKNOWN
            }
            if (looksLikeMusicXml(bytes)) {
                return if (fileName?.endsWith(".mxl", true) == true) MUSIC_XML_COMPRESSED else MUSIC_XML
            }
            return when {
                fileName?.endsWith(".mid", true) == true -> MIDI
                fileName?.endsWith(".midi", true) == true -> MIDI
                fileName?.endsWith(".musicxml", true) == true -> MUSIC_XML
                fileName?.endsWith(".xml", true) == true -> MUSIC_XML
                fileName?.endsWith(".mxl", true) == true -> MUSIC_XML_COMPRESSED
                else -> UNKNOWN
            }
        }

        private fun looksLikeMusicXml(bytes: ByteArray): Boolean {
            val head = String(
                bytes,
                0,
                minOf(bytes.size, 512),
                StandardCharsets.UTF_8
            ).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (!head.startsWith("<")) return false
            return head.contains("score-partwise", true) ||
                head.contains("score-timewise", true) ||
                head.contains("<?xml", true)
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            for (i in prefix.indices) {
                if (this[i] != prefix[i]) return false
            }
            return true
        }
    }
}

data class LoadedScore(
    val displayName: String,
    val sourceFormat: ScoreFormat,
    val renderFormat: ScoreFormat,
    val bytes: ByteArray,
    val sizeBytes: Long = bytes.size.toLong(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoadedScore) return false
        return displayName == other.displayName &&
            sourceFormat == other.sourceFormat &&
            renderFormat == other.renderFormat &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + sourceFormat.hashCode()
        result = 31 * result + renderFormat.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    companion object {
        /**
         * Normalises an imported score into something the viewer can render.
         * alphaTab has no MIDI importer, so MIDI sources are translated into
         * MusicXML; every other format is passed through untouched.
         */
        fun prepare(displayName: String, sourceFormat: ScoreFormat, bytes: ByteArray): LoadedScore {
            return if (sourceFormat == ScoreFormat.MIDI) {
                val xml = MidiToMusicXml.convert(bytes).toByteArray(StandardCharsets.UTF_8)
                LoadedScore(
                    displayName = displayName,
                    sourceFormat = sourceFormat,
                    renderFormat = ScoreFormat.MUSIC_XML,
                    bytes = xml,
                )
            } else {
                LoadedScore(
                    displayName = displayName,
                    sourceFormat = sourceFormat,
                    renderFormat = sourceFormat,
                    bytes = bytes,
                )
            }
        }
    }
}
