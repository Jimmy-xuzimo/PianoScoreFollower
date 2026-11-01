package com.pianoscorefollower.app.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class StreamingResamplerTest {
    @Test fun arbitraryCaptureChunksKeepTheSameSamples(){
        val samples=FloatArray(48000){sin(2*PI*997*it/48000).toFloat()}
        val whole=StreamingResampler(48000,16000).accept(samples)
        val streaming=StreamingResampler(48000,16000)
        val parts=samples.toList().chunked(1024).flatMap{streaming.accept(it.toFloatArray()).toList()}.toFloatArray()
        assertArrayEquals(whole,parts,0f)
        assertEquals(16000,parts.size)
    }
    @Test fun tuningActuallyCorrectsTheModelInputPitch(){
        val cents=40f
        val raised=440*2.0.pow(cents/1200.0)
        val input=FloatArray(44100){sin(2*PI*raised*it/44100).toFloat()}
        val output=StreamingResampler(44100,16000,cents).accept(input)
        val crossings=(1 until output.size).count{output[it-1]<=0&&output[it]>0}
        val modelFrequency=crossings*16000.0/output.size
        assertEquals(440.0,modelFrequency,1.0)
        assertTrue("pitch correction must preserve its changed sample-clock ratio",output.size>16000)
    }
}
