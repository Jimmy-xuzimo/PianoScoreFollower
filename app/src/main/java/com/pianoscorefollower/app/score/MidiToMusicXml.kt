package com.pianoscorefollower.app.score

import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * alphaTab cannot import Standard MIDI Files (it only reads Guitar Pro, MusicXML,
 * Capella and alphaTex), so MIDI scores are translated into MusicXML here before
 * they are handed to the viewer.
 *
 * The conversion is intentionally pragmatic: the note tracks are merged into a
 * single piano part so the viewer shows one grand staff (treble + bass) instead of
 * several stacked parts, notes are clipped at bar lines and tied across them, and
 * overlapping notes inside a bar are distributed over multiple voices so sustained
 * chords do not collapse onto a moving melody.
 */
object MidiToMusicXml {

    class MidiConversionException(message: String) : Exception(message)

    private const val FALLBACK_DIVISION = 480
    private const val EPSILON = 0.02

    /** Middle C. Below it a note is treated as left-hand material when splitting. */
    private const val SPLIT_PITCH = 60

    fun convert(bytes: ByteArray): String {
        val midi = MidiReader.read(bytes)
        return MusicXmlBuilder(midi).build()
    }

    /* ------------------------------------------------------------------ */
    /* Parsed model                                                        */
    /* ------------------------------------------------------------------ */

    internal class TimedValue<T>(val tick: Long, val value: T)

    internal class TimeSignature(val numerator: Int, val denominator: Int) {
        val quarterLength: Double get() = numerator * (4.0 / denominator)
    }

    internal class MidiNote(val startTick: Long, val endTick: Long, val pitch: Int)

    internal class MidiTrack(val name: String?, val notes: List<MidiNote>)

    internal class MidiFile(
        val division: Int,
        val tracks: List<MidiTrack>,
        val timeSignatures: List<TimedValue<TimeSignature>>,
        val keySignatures: List<TimedValue<Int>>,
        val tempos: List<TimedValue<Int>>,
    )

    /* ------------------------------------------------------------------ */
    /* Parser                                                              */
    /* ------------------------------------------------------------------ */

    private object MidiReader {

        private class Cursor(val data: ByteArray) {
            var pos = 0

            fun u8(): Int {
                if (pos >= data.size) throw MidiConversionException("MIDI 文件意外结束")
                return data[pos++].toInt() and 0xFF
            }

            fun u16(): Int = (u8() shl 8) or u8()

            fun u32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

            fun ascii(length: Int): String {
                if (pos + length > data.size) throw MidiConversionException("MIDI 文件意外结束")
                val text = String(data, pos, length, StandardCharsets.US_ASCII)
                pos += length
                return text
            }

            fun peekAscii(length: Int): String {
                if (pos + length > data.size) return ""
                return String(data, pos, length, StandardCharsets.US_ASCII)
            }

            fun varLen(): Int {
                var value = 0
                var byte: Int
                var guard = 0
                do {
                    byte = u8()
                    value = (value shl 7) or (byte and 0x7F)
                    guard++
                } while (byte and 0x80 != 0 && guard < 5)
                return value
            }

            fun skip(count: Int) {
                pos += count
            }
        }

        private class RawNoteEvent(val tick: Long, val isOn: Boolean, val key: Int, val pitch: Int)

        fun read(bytes: ByteArray): MidiFile {
            if (bytes.size < 14) throw MidiConversionException("MIDI 文件过短")
            val cursor = Cursor(bytes)

            if (cursor.ascii(4) != "MThd") throw MidiConversionException("不是有效的 MIDI 文件（缺少 MThd）")
            val headerLength = cursor.u32()
            cursor.u16() // format
            val declaredTracks = cursor.u16()
            val divisionRaw = cursor.u16()
            cursor.skip((headerLength - 6).coerceAtLeast(0))

            val division = if (divisionRaw and 0x8000 != 0 || divisionRaw == 0) {
                FALLBACK_DIVISION
            } else {
                divisionRaw
            }

            val tracks = ArrayList<MidiTrack>()
            val timeSignatures = ArrayList<TimedValue<TimeSignature>>()
            val keySignatures = ArrayList<TimedValue<Int>>()
            val tempos = ArrayList<TimedValue<Int>>()

            // The track count in the header is unreliable in the wild (files written by
            // simple exporters often disagree with the chunks actually present), so the
            // chunks themselves are the source of truth.
            var parsed = 0
            val limit = maxOf(declaredTracks, 1) + 64
            while (parsed < limit && cursor.pos + 8 <= bytes.size && cursor.peekAscii(4) == "MTrk") {
                cursor.skip(4)
                val trackLength = cursor.u32()
                val trackEnd = (cursor.pos + trackLength).coerceAtMost(bytes.size)

                val track = readTrack(
                    cursor = cursor,
                    trackEnd = trackEnd,
                    timeSignatures = timeSignatures,
                    keySignatures = keySignatures,
                    tempos = tempos,
                )
                tracks.add(track)
                cursor.pos = trackEnd
                parsed++
            }

            if (tracks.all { it.notes.isEmpty() }) {
                throw MidiConversionException("MIDI 文件中没有可显示的音符")
            }

            return MidiFile(
                division = division,
                tracks = tracks,
                timeSignatures = timeSignatures.sortedBy { it.tick },
                keySignatures = keySignatures.sortedBy { it.tick },
                tempos = tempos.sortedBy { it.tick },
            )
        }

        private fun readTrack(
            cursor: Cursor,
            trackEnd: Int,
            timeSignatures: MutableList<TimedValue<TimeSignature>>,
            keySignatures: MutableList<TimedValue<Int>>,
            tempos: MutableList<TimedValue<Int>>,
        ): MidiTrack {
            val notes = ArrayList<MidiNote>()
            val pending = HashMap<Int, Long>()
            val rawNotes = ArrayList<RawNoteEvent>()
            var tick = 0L
            var runningStatus = 0
            var name: String? = null

            while (cursor.pos < trackEnd) {
                tick += cursor.varLen()
                if (cursor.pos >= trackEnd) break

                var status = cursor.data[cursor.pos].toInt() and 0xFF
                if (status < 0x80) {
                    if (runningStatus == 0) throw MidiConversionException("MIDI 事件缺少状态字节")
                    status = runningStatus
                } else {
                    cursor.pos++
                    if (status < 0xF0) runningStatus = status
                }

                when {
                    status == 0xFF -> {
                        val type = cursor.u8()
                        val length = cursor.varLen()
                        val payloadStart = cursor.pos
                        when (type) {
                            0x51 -> if (length >= 3) {
                                val micros = (cursor.data[payloadStart].toInt() and 0xFF shl 16) or
                                    (cursor.data[payloadStart + 1].toInt() and 0xFF shl 8) or
                                    (cursor.data[payloadStart + 2].toInt() and 0xFF)
                                if (micros > 0) tempos.add(TimedValue(tick, micros))
                            }

                            0x58 -> if (length >= 2) {
                                val numerator = cursor.data[payloadStart].toInt() and 0xFF
                                val denominator = 1 shl (cursor.data[payloadStart + 1].toInt() and 0xFF)
                                if (numerator > 0) {
                                    timeSignatures.add(TimedValue(tick, TimeSignature(numerator, denominator)))
                                }
                            }

                            0x59 -> if (length >= 1) {
                                val fifths = cursor.data[payloadStart].toInt()
                                keySignatures.add(TimedValue(tick, fifths))
                            }

                            0x03 -> name = String(
                                cursor.data,
                                payloadStart,
                                length.coerceAtMost(cursor.data.size - payloadStart),
                                StandardCharsets.UTF_8,
                            ).trim().takeIf { it.isNotEmpty() }

                            0x2F -> {
                                cursor.pos = trackEnd
                                break
                            }
                        }
                        cursor.pos = payloadStart + length
                    }

                    status == 0xF0 || status == 0xF7 -> {
                        val length = cursor.varLen()
                        cursor.skip(length)
                    }

                    else -> {
                        val high = status and 0xF0
                        val channel = status and 0x0F
                        when (high) {
                            0x90 -> {
                                val pitch = cursor.u8()
                                val velocity = cursor.u8()
                                rawNotes.add(RawNoteEvent(tick, velocity > 0, (channel shl 8) or pitch, pitch))
                            }

                            0x80 -> {
                                val pitch = cursor.u8()
                                cursor.u8()
                                rawNotes.add(RawNoteEvent(tick, false, (channel shl 8) or pitch, pitch))
                            }

                            0xA0, 0xB0, 0xE0 -> cursor.skip(2)
                            0xC0, 0xD0 -> cursor.skip(1)
                            else -> cursor.skip(1)
                        }
                    }
                }
            }

            // Note-offs are resolved before note-ons that share a tick. Exporters that
            // write a repeated pitch as "on, off" at the same tick would otherwise turn
            // it into a zero-length note and swallow the note that follows.
            rawNotes.sortWith(compareBy({ it.tick }, { it.isOn }))
            for (event in rawNotes) {
                closeNote(notes, pending, event.key, event.pitch, event.tick)
                if (event.isOn) pending[event.key] = event.tick
            }

            // Files that omit the final note-off would otherwise lose their last notes.
            for ((key, start) in pending) {
                if (tick > start) notes.add(MidiNote(start, tick, key and 0xFF))
            }

            return MidiTrack(name, notes)
        }

        private fun closeNote(
            notes: MutableList<MidiNote>,
            pending: HashMap<Int, Long>,
            key: Int,
            pitch: Int,
            tick: Long,
        ) {
            val start = pending.remove(key) ?: return
            if (tick > start) notes.add(MidiNote(start, tick, pitch))
        }
    }

    /* ------------------------------------------------------------------ */
    /* MusicXML writer                                                     */
    /* ------------------------------------------------------------------ */

    private class Bar(val index: Int, val start: Long, val end: Long, val timeSignature: TimeSignature)

    private class Chord(val start: Long, val end: Long, val pitches: List<Int>)

    private class ChordSegment(
        val start: Long,
        val end: Long,
        val pitches: List<Int>,
        val tieStart: Boolean,
        val tieStop: Boolean,
    )

    private class StaffedNote(val note: MidiNote, val staff: Int)

    private class MusicXmlBuilder(private val midi: MidiFile) {

        private val division = midi.division.toLong()

        /**
         * Recorded MIDI is rarely rhythmically exact (notes are shortened to sound
         * detached), which would otherwise produce non-notatable durations. Onsets and
         * offsets are snapped to a sixteenth-note grid so the result reads as real
         * notation instead of arbitrary tuplets.
         */
        private val gridTicks = maxOf(1L, division / 4)

        private val bars: List<Bar> = buildBars()

        private val noteTracks: List<MidiTrack> = midi.tracks.filter { it.notes.isNotEmpty() }

        /** Right hand / left hand published as two tracks: keep them on their own staff. */
        private val splitByTrack = noteTracks.size == 2

        /**
         * A single track is only spread over a grand staff when it actually contains
         * chords spanning both sides of middle C — a monophonic melody stays on one
         * staff no matter how wide its range is.
         */
        private val staffCount: Int = when {
            splitByTrack -> 2
            hasChordsAcrossMiddleC() -> 2
            else -> 1
        }

        private val trackStaff: Map<Int, Int> = if (splitByTrack) {
            // The track with the lower average pitch becomes the lower (bass) staff.
            val byPitch = noteTracks
                .mapIndexed { index, track -> index to track.notes.sumOf { it.pitch }.toDouble() / track.notes.size }
                .sortedBy { it.second }
            val result = HashMap<Int, Int>()
            byPitch.forEachIndexed { rank, entry -> result[entry.first] = if (rank == 0) 1 else 0 }
            result
        } else {
            emptyMap()
        }

        private val staffedNotes: List<StaffedNote> = buildStaffedNotes()

        private val title: String? = midi.tracks.firstNotNullOfOrNull { it.name }

        fun build(): String {
            val out = StringBuilder(64 * 1024)
            out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            out.append("<score-partwise version=\"3.1\">\n")
            out.append("  <identification>\n")
            out.append("    <encoding><software>PianoScoreFollower</software></encoding>\n")
            out.append("  </identification>\n")
            title?.let {
                out.append("  <movement-title>").append(escape(it)).append("</movement-title>\n")
            }
            out.append("  <part-list>\n")
            out.append("    <score-part id=\"P1\"><part-name>Piano</part-name></score-part>\n")
            out.append("  </part-list>\n")

            writePart(out)

            out.append("</score-partwise>\n")
            return out.toString()
        }

        private fun writePart(out: StringBuilder) {
            val segments = distributeToBars()
            var previousKey: Int? = null
            var previousTime: TimeSignature? = null
            val singleStaffIsBass = staffCount == 1 &&
                staffedNotes.sumOf { it.note.pitch }.toDouble() / staffedNotes.size.coerceAtLeast(1) < SPLIT_PITCH

            out.append("  <part id=\"P1\">\n")

            bars.forEachIndexed { barIndex, bar ->
                out.append("    <measure number=\"").append(barIndex + 1).append("\">\n")

                val activeTime = timeSignatureAt(bar.start)
                val activeKey = keySignatureAt(bar.start)
                val needsAttributes = barIndex == 0 ||
                    activeTime != previousTime ||
                    activeKey != previousKey

                if (needsAttributes) {
                    out.append("      <attributes>\n")
                    if (barIndex == 0) {
                        out.append("        <divisions>").append(division).append("</divisions>\n")
                    }
                    if (activeKey != previousKey || barIndex == 0) {
                        out.append("        <key><fifths>").append(activeKey).append("</fifths></key>\n")
                    }
                    if (activeTime != previousTime || barIndex == 0) {
                        out.append("        <time><beats>").append(activeTime.numerator)
                            .append("</beats><beat-type>").append(activeTime.denominator)
                            .append("</beat-type></time>\n")
                    }
                    if (barIndex == 0) {
                        when {
                            staffCount > 1 -> {
                                out.append("        <staves>2</staves>\n")
                                out.append("        <clef number=\"1\"><sign>G</sign><line>2</line></clef>\n")
                                out.append("        <clef number=\"2\"><sign>F</sign><line>4</line></clef>\n")
                            }

                            singleStaffIsBass ->
                                out.append("        <clef><sign>F</sign><line>4</line></clef>\n")

                            else ->
                                out.append("        <clef><sign>G</sign><line>2</line></clef>\n")
                        }
                    }
                    out.append("      </attributes>\n")
                }
                previousTime = activeTime
                previousKey = activeKey

                if (barIndex == 0) writeTempo(out)

                writeBarContent(out, bar, segments, barIndex)
                out.append("    </measure>\n")
            }

            out.append("  </part>\n")
        }

        private fun writeTempo(out: StringBuilder) {
            val micros = midi.tempos.firstOrNull { it.tick <= 0 }?.value
                ?: midi.tempos.firstOrNull()?.value
                ?: return
            val bpm = (60_000_000.0 / micros).toInt().coerceIn(20, 400)
            out.append("      <direction placement=\"above\">\n")
            out.append("        <direction-type>\n")
            out.append("          <metronome><beat-unit>quarter</beat-unit><per-minute>")
                .append(bpm).append("</per-minute></metronome>\n")
            out.append("        </direction-type>\n")
            out.append("        <sound tempo=\"").append(bpm).append("\"/>\n")
            out.append("      </direction>\n")
        }

        private fun writeBarContent(
            out: StringBuilder,
            bar: Bar,
            segments: List<List<MutableList<ChordSegment>>>,
            barIndex: Int,
        ) {
            val barLength = bar.end - bar.start

            for (staff in 0 until staffCount) {
                // MusicXML streams are linear: every staff starts back at the bar line.
                if (staff > 0) {
                    out.append("      <backup><duration>").append(barLength).append("</duration></backup>\n")
                }

                val staffSegments = segments[staff][barIndex]
                if (staffSegments.isEmpty()) {
                    out.append("      <note><rest measure=\"yes\"/>")
                    out.append("<duration>").append(barLength).append("</duration>")
                    out.append("<voice>1</voice>")
                    writeStaff(out, staff)
                    out.append("</note>\n")
                    continue
                }

                val voices = assignVoices(staffSegments)
                voices.forEachIndexed { voiceIndex, voice ->
                    if (voiceIndex > 0) {
                        out.append("      <backup><duration>").append(barLength).append("</duration></backup>\n")
                    }
                    writeVoice(out, bar, voice, voiceIndex + 1, staff)
                }
            }
        }

        private fun writeVoice(
            out: StringBuilder,
            bar: Bar,
            voice: List<ChordSegment>,
            voiceNumber: Int,
            staff: Int,
        ) {
            var cursor = bar.start
            for (segment in voice) {
                if (segment.start > cursor) {
                    writeRest(out, cursor, segment.start, voiceNumber, staff)
                }
                writeChord(out, segment, voiceNumber, staff)
                cursor = segment.end
            }
            if (cursor < bar.end) {
                writeRest(out, cursor, bar.end, voiceNumber, staff)
            }
        }

        private fun writeRest(out: StringBuilder, start: Long, end: Long, voiceNumber: Int, staff: Int) {
            val duration = end - start
            if (duration <= 0) return
            out.append("      <note><rest/>")
            out.append("<duration>").append(duration).append("</duration>")
            out.append("<voice>").append(voiceNumber).append("</voice>")
            writeType(out, duration)
            writeStaff(out, staff)
            out.append("</note>\n")
        }

        private fun writeChord(out: StringBuilder, segment: ChordSegment, voiceNumber: Int, staff: Int) {
            val duration = segment.end - segment.start
            if (duration <= 0) return
            segment.pitches.sorted().forEachIndexed { noteIndex, pitch ->
                out.append("      <note>")
                if (noteIndex > 0) out.append("<chord/>")
                out.append("<pitch>")
                out.append("<step>").append(stepOf(pitch)).append("</step>")
                val alter = alterOf(pitch)
                if (alter != 0) out.append("<alter>").append(alter).append("</alter>")
                out.append("<octave>").append(octaveOf(pitch)).append("</octave>")
                out.append("</pitch>")
                out.append("<duration>").append(duration).append("</duration>")
                if (segment.tieStop) out.append("<tie type=\"stop\"/>")
                if (segment.tieStart) out.append("<tie type=\"start\"/>")
                out.append("<voice>").append(voiceNumber).append("</voice>")
                writeType(out, duration)
                writeStaff(out, staff)
                if (segment.tieStop || segment.tieStart) {
                    out.append("<notations>")
                    if (segment.tieStop) out.append("<tied type=\"stop\"/>")
                    if (segment.tieStart) out.append("<tied type=\"start\"/>")
                    out.append("</notations>")
                }
                out.append("</note>\n")
            }
        }

        private fun writeStaff(out: StringBuilder, staff: Int) {
            if (staffCount > 1) out.append("<staff>").append(staff + 1).append("</staff>")
        }

        private fun writeType(out: StringBuilder, duration: Long) {
            val type = noteTypeFor(duration.toDouble() / division) ?: return
            out.append("<type>").append(type.name).append("</type>")
            repeat(type.dots) { out.append("<dot/>") }
            if (type.triplet) {
                out.append("<time-modification><actual-notes>3</actual-notes>")
                    .append("<normal-notes>2</normal-notes></time-modification>")
            }
        }

        /* -------------------------------------------------------------- */

        private fun buildStaffedNotes(): List<StaffedNote> {
            val result = ArrayList<StaffedNote>()
            noteTracks.forEachIndexed { index, track ->
                val fixedStaff = when {
                    staffCount == 1 -> 0
                    splitByTrack -> trackStaff[index] ?: 0
                    else -> -1
                }
                for (note in track.notes) {
                    val staff = if (fixedStaff >= 0) {
                        fixedStaff
                    } else if (note.pitch >= SPLIT_PITCH) {
                        0
                    } else {
                        1
                    }
                    result.add(StaffedNote(note, staff))
                }
            }
            return result
        }

        private fun hasChordsAcrossMiddleC(): Boolean {
            val all = noteTracks.flatMap { it.notes }
            if (all.isEmpty()) return false
            val low = all.minOf { it.pitch }
            val high = all.maxOf { it.pitch }
            if (low >= SPLIT_PITCH || high < SPLIT_PITCH) return false
            return noteTracks.any { track -> groupChords(track.notes).any { it.pitches.size > 1 } }
        }

        private fun distributeToBars(): List<List<MutableList<ChordSegment>>> {
            val result: List<List<MutableList<ChordSegment>>> =
                List(staffCount) { List(bars.size) { mutableListOf<ChordSegment>() } }

            for (staff in 0 until staffCount) {
                val staffNotes = staffedNotes.filter { it.staff == staff }.map { it.note }
                for (chord in groupChords(staffNotes)) {
                    var start = chord.start
                    while (start < chord.end) {
                        val barIndex = barIndexAt(start)
                        if (barIndex < 0) break
                        val barEnd = bars[barIndex].end
                        val end = minOf(chord.end, barEnd)
                        result[staff][barIndex].add(
                            ChordSegment(
                                start = start,
                                end = end,
                                pitches = chord.pitches,
                                tieStart = start > chord.start,
                                tieStop = end < chord.end,
                            )
                        )
                        start = end
                    }
                }
            }
            return result
        }

        private fun groupChords(notes: List<MidiNote>): List<Chord> {
            val grouped = LinkedHashMap<Pair<Long, Long>, MutableList<Int>>()
            for (note in notes) {
                if (note.endTick <= note.startTick) continue
                val start = quantize(note.startTick)
                val end = quantize(note.endTick).coerceAtLeast(start + gridTicks)
                grouped.getOrPut(start to end) { mutableListOf() }.add(note.pitch)
            }
            return grouped.map { (key, pitches) ->
                Chord(key.first, key.second, pitches.distinct())
            }.sortedBy { it.start }
        }

        private fun quantize(tick: Long): Long =
            (tick.toDouble() / gridTicks).roundToLong() * gridTicks

        private fun assignVoices(segments: List<ChordSegment>): List<List<ChordSegment>> {
            val sorted = segments.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))
            val voices = ArrayList<MutableList<ChordSegment>>()
            for (segment in sorted) {
                var placed = false
                for (voice in voices) {
                    if (voice.none { it.start < segment.end && it.end > segment.start }) {
                        voice.add(segment)
                        placed = true
                        break
                    }
                }
                if (!placed) voices.add(mutableListOf(segment))
            }
            return voices.map { voice -> voice.sortedBy { it.start } }
        }

        private fun buildBars(): List<Bar> {
            val totalTicks = maxOf(
                midi.tracks.maxOfOrNull { track -> track.notes.maxOfOrNull { it.endTick } ?: 0L } ?: 0L,
                division * 4,
            )

            val signatures = midi.timeSignatures.ifEmpty {
                listOf(TimedValue(0L, TimeSignature(4, 4)))
            }

            val result = ArrayList<Bar>()
            var start = 0L
            var guard = 0
            while (start < totalTicks && guard < 10_000) {
                val signature = timeSignatureAt(start, signatures)
                val length = (signature.quarterLength * division).toLong().coerceAtLeast(1)
                result.add(Bar(result.size, start, start + length, signature))
                start += length
                guard++
            }
            if (result.isEmpty()) {
                result.add(Bar(0, 0, division * 4, TimeSignature(4, 4)))
            }
            return result
        }

        private fun timeSignatureAt(tick: Long): TimeSignature =
            timeSignatureAt(tick, midi.timeSignatures.ifEmpty { listOf(TimedValue(0L, TimeSignature(4, 4))) })

        private fun timeSignatureAt(tick: Long, signatures: List<TimedValue<TimeSignature>>): TimeSignature {
            var active = signatures.first().value
            for (entry in signatures) {
                if (entry.tick <= tick) active = entry.value else break
            }
            return active
        }

        private fun keySignatureAt(tick: Long): Int {
            if (midi.keySignatures.isEmpty()) return 0
            var active = midi.keySignatures.first().value
            for (entry in midi.keySignatures) {
                if (entry.tick <= tick) active = entry.value else break
            }
            return active
        }

        private fun barIndexAt(tick: Long): Int {
            if (bars.isEmpty()) return -1
            var low = 0
            var high = bars.size - 1
            var found = -1
            while (low <= high) {
                val mid = (low + high) / 2
                val bar = bars[mid]
                when {
                    tick < bar.start -> high = mid - 1
                    tick >= bar.end -> low = mid + 1
                    else -> {
                        found = mid
                        break
                    }
                }
            }
            return found
        }

        /* -------------------------------------------------------------- */

        private class NoteType(val name: String, val dots: Int, val triplet: Boolean)

        private val bases = listOf(
            "whole" to 4.0,
            "half" to 2.0,
            "quarter" to 1.0,
            "eighth" to 0.5,
            "16th" to 0.25,
            "32nd" to 0.125,
            "64th" to 0.0625,
        )

        private fun noteTypeFor(quarters: Double): NoteType? {
            if (quarters <= 0) return null
            for ((name, base) in bases) {
                if (abs(quarters - base * 2.0 / 3.0) < EPSILON) return NoteType(name, 0, true)
                for (dots in 0..2) {
                    val value = base * (2.0 - 1.0 / (1 shl dots))
                    if (abs(quarters - value) < EPSILON) return NoteType(name, dots, false)
                }
            }
            // Not a plain notatable value (e.g. a quarter plus a sixteenth): fall back to
            // the closest plain value so alphaTab never has to guess a tuplet.
            val fallback = bases.firstOrNull { it.second <= quarters } ?: bases.last()
            return NoteType(fallback.first, 0, false)
        }

        private fun stepOf(pitch: Int): String = STEPS[pitch % 12]

        private fun alterOf(pitch: Int): Int = ALTERS[pitch % 12]

        private fun octaveOf(pitch: Int): Int = pitch / 12 - 1

        private fun escape(value: String): String = buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }

    private val STEPS = arrayOf("C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B")
    private val ALTERS = intArrayOf(0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0)
}
