package com.pianoscorefollower.app.midi

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.pianoscorefollower.app.score.MidiToMusicXml

class MidiRoundTripTest {
    @Test fun writeIndependentParserFixture() {
        val messages=listOf(MidiMessage(123456,byteArrayOf(0x93.toByte(),60,95)),
            MidiMessage(234567,byteArrayOf(0xb3.toByte(),64,127)),
            MidiMessage(456789,byteArrayOf(0x83.toByte(),60,12)),
            MidiMessage(876543,byteArrayOf(0xb3.toByte(),64,0)))
        val bytes=MidiRecordingWriter.write(RecordingSession("test","test",messages,1_000_000))
        val folder=File("build/roundtrip").apply { mkdirs() }
        File(folder,"recording.mid").writeBytes(bytes)
        assertTrue(bytes.size > 60)
    }
    @Test fun midiTempoChangesSurviveNotationConversion() {
        val xml=MidiToMusicXml.convert(File("src/test/resources/midi/tempo-changes.mid").readBytes())
        assertTrue(xml.contains("tempo=\"120.0\""));assertTrue(xml.contains("tempo=\"80.0\""))
    }
    @Test fun severalPartsRemainSelectableAndNamesAreEscaped() {
        val xml=MidiToMusicXml.convert(File("src/test/resources/midi/three-parts.mid").readBytes())
        assertEquals(3,Regex("<score-part id=").findAll(xml).count());assertTrue(xml.contains("&amp;"))
    }
}
