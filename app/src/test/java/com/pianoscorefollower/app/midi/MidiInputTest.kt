package com.pianoscorefollower.app.midi

import org.junit.Assert.*
import org.junit.Test
import com.pianoscorefollower.app.score.MidiToMusicXml

class MidiInputTest {
    @Test fun fragmentedRunningStatusAndRealtimeAreDecoded() {
        val result=ArrayList<ByteArray>(); val d=MidiStreamDecoder { b, _ -> result+=b }
        d.accept(byteArrayOf(0x90.toByte(),60)); d.accept(byteArrayOf(100,0xf8.toByte(),62,90,60,0))
        assertEquals(3,result.size); assertArrayEquals(byteArrayOf(0x80.toByte(),60,0),result.last())
    }
    @Test fun pedalSustainsSoundButNotPhysicalKey() {
        val k=MidiKeyboard(); k.accept(byteArrayOf(0x90.toByte(),60,100)); k.accept(byteArrayOf(0xb0.toByte(),64,127))
        k.accept(byteArrayOf(0x80.toByte(),60,0)); assertTrue(k.pressed().isEmpty()); assertTrue(k.pitches()[60]>0)
        k.accept(byteArrayOf(0xb0.toByte(),64,0)); assertEquals(0f,k.pitches()[60],0f)
    }
    @Test fun sysexAndSystemCommonDoNotCorruptNextMessage() {
        val result=ArrayList<ByteArray>(); val d=MidiStreamDecoder { b,_->result+=b }
        d.accept(byteArrayOf(0xf0.toByte(),1,2,0xf8.toByte(),0xf7.toByte(),0xf2.toByte(),1,2,0x91.toByte(),64,80))
        assertEquals(1,result.size); assertEquals(0x91,result[0][0].toInt() and 255)
    }
    @Test fun writerCreatesImportableGrandStaffAndPreservesEvents() {
        val s=RecordingSession("test","test",listOf(MidiMessage(0,byteArrayOf(0x90.toByte(),60,100)),
            MidiMessage(0,byteArrayOf(0x90.toByte(),48,90)),MidiMessage(500000,byteArrayOf(0x80.toByte(),60,0)),
            MidiMessage(600000,byteArrayOf(0x80.toByte(),48,0))),700000)
        val raw=MidiRecordingWriter.write(s)
        assertEquals("MThd",String(raw.copyOfRange(0,4)))
        val xml=MidiToMusicXml.convert(raw,16,true)
        assertTrue(xml.contains("<staves>2</staves>")); assertTrue(xml.contains("<step>C</step>"))
    }
}
