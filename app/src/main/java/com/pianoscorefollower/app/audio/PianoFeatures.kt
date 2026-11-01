package com.pianoscorefollower.app.audio

import kotlin.math.*

/** Two resolutions keep fast treble attacks separate while resolving bass harmonics. */
class PianoFeatures(private val rate: Int) {
    private val short = HarmonicSpectrum(rate,8192)
    private val bass = HarmonicSpectrum(rate,16384)
    private val result = FloatArray(128)
    var tuningCents: Float = 0f
    fun accept(samples: FloatArray): FloatArray {
        val quick=short.accept(samples,tuningCents)
        val low=bass.accept(samples,tuningCents)
        var norm=0f
        for(n in 21..108) {
            val bassWeight=if(n<48) 0.65f else if(n<60) 0.2f else 0f
            result[n]=quick[n]*(1-bassWeight)+low[n]*bassWeight
            norm+=result[n]*result[n]
        }
        norm=sqrt(norm)
        if(norm>1e-9f) for(n in result.indices) result[n]/=norm
        return result
    }
    private class HarmonicSpectrum(val rate: Int,val size: Int) {
        val fft=Fft(size);val history=FloatArray(size);val re=FloatArray(size);val im=FloatArray(size)
        val window=FloatArray(size) { (0.5-0.5*cos(2*PI*it/(size-1))).toFloat() }
        val result=FloatArray(128)
        fun accept(samples:FloatArray,tuning:Float):FloatArray {
            System.arraycopy(history,samples.size,history,0,size-samples.size)
            System.arraycopy(samples,0,history,size-samples.size,samples.size)
            for(i in 0 until size) { re[i]=history[i]*window[i];im[i]=0f }
            fft.forward(re,im);val spectrum=fft.magnitude
            fun energy(bin: Float): Float {
                val center=bin.roundToInt()
                if(center<2 || center>=spectrum.lastIndex-2) return 0f
                val peak=maxOf(spectrum[center-1],spectrum[center],spectrum[center+1])
                var surrounding=0f
                for(d in -12..12) surrounding+=spectrum[(center+d).coerceIn(1,spectrum.lastIndex)]
                return (peak/(surrounding/25+0.002f)).coerceAtMost(25f)*sqrt(peak.coerceAtLeast(0f))
            }
            result.fill(0f)
            for(note in 21..108) {
                val bin=(440.0*2.0.pow((note-69+tuning/100)/12.0)*size/rate).toFloat()
                var sum=0f;var weight=0f
                for(h in 1..5) { val w=1f/h;sum+=energy(bin*h)*w;weight+=w }
                result[note]=(sum/weight-energy(bin/2)*0.12f).coerceAtLeast(0f)
            }
            val raw=result.copyOf();var norm=0f
            for(n in 21..108) { result[n]=(raw[n]-.4f*max(raw[n-1],raw[n+1])).coerceAtLeast(0f);norm+=result[n]*result[n] }
            norm=sqrt(norm)
            if(norm>1e-9f) for(n in result.indices) result[n]/=norm
            return result
        }
    }
}
