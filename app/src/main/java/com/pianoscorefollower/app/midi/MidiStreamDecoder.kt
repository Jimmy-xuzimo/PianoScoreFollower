package com.pianoscorefollower.app.midi

/** Stateful MIDI 1 byte-stream decoder, including fragmented and running-status data. */
class MidiStreamDecoder(private val emit: (ByteArray, Long) -> Unit) {
    private var status = 0
    private var needed = 0
    private val data = ByteArray(2)
    private var count = 0
    private var sysex = false
    fun reset() { status = 0; count = 0; needed = 0; sysex = false }
    fun accept(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size, timeUs: Long = 0) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
        for (i in offset until offset + length) {
            val value = bytes[i].toInt() and 255
            if (value >= 0xf8) continue // realtime may occur in the middle of any message
            if (value == 0xf0) { sysex = true; status = 0; count = 0; continue }
            if (value == 0xf7) { sysex = false; continue }
            if (sysex) continue
            if (value and 0x80 != 0) {
                count = 0
                status = if (value < 0xf0) value else 0
                needed = when (value and 0xf0) { 0xc0, 0xd0 -> 1; else -> 2 }
            } else if (status != 0) {
                data[count++] = value.toByte()
                if (count == needed) {
                    val message = ByteArray(needed + 1)
                    message[0] = status.toByte()
                    System.arraycopy(data, 0, message, 1, needed)
                    if (status and 0xf0 == 0x90 && needed == 2 && data[1].toInt() == 0)
                        message[0] = ((status and 15) or 0x80).toByte()
                    emit(message, timeUs)
                    count = 0
                }
            }
        }
    }
}

data class MidiMessage(val timeUs: Long, val bytes: ByteArray)

/** Physical keys and sustain are separate: pedal never changes the key release time. */
class MidiKeyboard {
    private val keys = Array(16) { IntArray(128) }
    private val sustained = Array(16) { BooleanArray(128) }
    private val pedal = BooleanArray(16)
    fun clear() { keys.forEach { it.fill(0) }; sustained.forEach { it.fill(false) }; pedal.fill(false) }
    fun accept(message: ByteArray): Boolean {
        val s = message[0].toInt() and 255; val channel = s and 15
        val p = message.getOrNull(1)?.toInt()?.and(127) ?: 0
        val v = message.getOrNull(2)?.toInt()?.and(127) ?: 0
        when (s and 0xf0) {
            0x90 -> if (v > 0) { keys[channel][p] = v; sustained[channel][p] = false; return true }
                else release(channel, p)
            0x80 -> release(channel, p)
            0xb0 -> when (p) {
                64 -> { pedal[channel] = v >= 64; if (!pedal[channel]) sustained[channel].fill(false) }
                120, 123 -> { keys[channel].fill(0); sustained[channel].fill(false) }
                121 -> { pedal[channel] = false; sustained[channel].fill(false) }
            }
        }
        return false
    }
    private fun release(c: Int, p: Int) { if (keys[c][p] > 0) sustained[c][p] = pedal[c]; keys[c][p] = 0 }
    fun pitches(includePedal: Boolean = true) = FloatArray(128) { p ->
        if ((0..15).any { keys[it][p] > 0 }) 1f
        else if (includePedal && (0..15).any { sustained[it][p] }) 0.25f else 0f
    }
    fun pressed() = (0..127).filter { p -> (0..15).any { keys[it][p] > 0 } }
}
