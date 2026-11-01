package com.pianoscorefollower.app.midi

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

data class NotationOptions(val bpm: Int = 120, val numerator: Int = 4, val denominator: Int = 4,
    val fifths: Int = 0, val grid: Int = 16) {
    init { require(bpm in 20..300 && numerator in 1..12 && denominator in listOf(2,4,8,16) && fifths in -7..7 && grid in listOf(4,8,16,32)) }
}

data class RecordingSession(val id: String, val title: String, val messages: List<MidiMessage>,
    val durationUs: Long, val options: NotationOptions = NotationOptions())

object MidiRecordingWriter {
    const val PPQ = 960
    fun write(session: RecordingSession): ByteArray {
        val track = ByteArrayOutputStream()
        fun variable(value: Long) {
            var n = value.coerceAtLeast(0); val bytes = ArrayList<Int>(); bytes += (n and 127).toInt(); n = n shr 7
            while (n > 0) { bytes += ((n and 127) or 128).toInt(); n = n shr 7 }
            bytes.asReversed().forEach { track.write(it) }
        }
        val options = session.options
        val tempo = 60_000_000 / options.bpm
        track.write(byteArrayOf(0,0xff.toByte(),0x51,3,(tempo shr 16).toByte(),(tempo shr 8).toByte(),tempo.toByte()))
        track.write(byteArrayOf(0,0xff.toByte(),0x58,4,options.numerator.toByte(),Integer.numberOfTrailingZeros(options.denominator).toByte(),24,8))
        track.write(byteArrayOf(0,0xff.toByte(),0x59,2,options.fifths.toByte(),0))
        var lastTick = 0L
        val keyboard = MidiKeyboard()
        session.messages.sortedBy { it.timeUs }.forEach { m ->
            val tick = (m.timeUs.coerceAtLeast(0) * PPQ / tempo.toDouble()).toLong()
            variable(tick-lastTick); track.write(m.bytes); lastTick = tick; keyboard.accept(m.bytes)
        }
        val end = maxOf(lastTick, (session.durationUs * PPQ / tempo.toDouble()).toLong())
        var first = true
        // All channels are closed at stop, including a recording stopped with a pedal held.
        for (c in 0..15) {
            variable(if (first) end-lastTick else 0); first = false
            track.write(byteArrayOf((0xb0 or c).toByte(),64,0))
            variable(0); track.write(byteArrayOf((0xb0 or c).toByte(),123,0))
        }
        variable(0); track.write(byteArrayOf(0xff.toByte(),0x2f,0))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeBytes("MThd"); out.writeInt(6); out.writeShort(0); out.writeShort(1); out.writeShort(PPQ)
            out.writeBytes("MTrk"); out.writeInt(track.size()); out.write(track.toByteArray())
        }
        return bytes.toByteArray()
    }
}
