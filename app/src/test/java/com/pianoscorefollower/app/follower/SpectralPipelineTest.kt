package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.audio.*
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LegacySpectralBaselineTest {
    private fun pcm(file:File):FloatArray {
        val b=ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(b.remaining()/4){b.float}
    }
    @Test fun replayActualPipeline() {
        val path=System.getenv("PIANO_SPECTRAL_CASE")
        assumeNotNull(path)
        val target=File(path!!)
        val folders=if(target.isFile)target.readLines().filter{it.isNotBlank()}.map{File(it)} else listOf(target)
        folders.forEach { replay(it) }
    }
    private fun replay(folder:File) {
        val events=File(folder,"graph.tsv").readLines().map { line ->
            val p=line.split('\t')
            fun pitches(s:String)=if(s.isBlank())intArrayOf() else s.split(',').map{it.toInt()}.toIntArray()
            ScoreEvent(p[0].toLong(),p[2].toInt(),FloatArray(12),pitches(p[5]).size,false,
                pitches(p[5]),pitches(p[6]),p[1].toInt(),p[4].toLong(),p[3].toInt(),p[7].toFloat())
        }
        val timeline=ScoreTimeline(960,events,events.last().tick+events.last().duration,events.maxOf{it.measure}+1)
        val reference=ArrayList<FloatArray>()
        LogSpectralEnergy().accept(pcm(File(folder,"reference.f32"))){f,_,_->reference.add(f)}
        val follower=SpectralScoreFollower(ScoreGraph.from(timeline),reference.toTypedArray())
        val analyzer=AudioAnalyzer()
        val processor=LogSpectralEnergy()
        val input=pcm(File(folder,"audio.f32"))
        var maxBack=0L;var last=0L
        val started=System.nanoTime()
        File(folder,"spectral-trace.tsv").bufferedWriter().use { out ->
            for(i in 0 until input.size/1024){
                val samples=input.copyOfRange(i*1024,(i+1)*1024)
                val frame=analyzer.accept(samples).copy(sampleTimeMs=(i+1)*1024L*1000/44100)
                processor.accept(samples){f,_,db->follower.accept(f,db)}
                val p=follower.position
                maxBack=maxOf(maxBack,last-p.tick);last=p.tick
                out.write("${(i+1)*1024.0/44100}\t${p.tick}\t${p.confidence}\t${p.status}\t${frame.isOnset}\t${frame.levelDb}\n")
            }
        }
        assertEquals("continuous performance must not move backwards",0L,maxBack)
        assertTrue("must actually advance",last>timeline.endTick/2)
        println("SPECTRAL_PIPELINE ${folder.name} ${(System.nanoTime()-started)/1e9}s compute; last=$last")
    }
    @Test fun silenceDoesNotAdvance() {
        val graph=ScoreGraph(listOf(GraphEvent(0,960,0,0,0,intArrayOf(60))),endTick=960)
        val reference=Array(200){FloatArray(84).also{it[40]=1f}}
        val follower=SpectralScoreFollower(graph,reference)
        repeat(1000){ assertEquals(0,follower.accept(FloatArray(84),-72f).tick) }
    }
}
