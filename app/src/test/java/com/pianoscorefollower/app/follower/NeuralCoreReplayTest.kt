package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.performance.PerformanceObservation
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Alignment-only replay of the released model's causal outputs. This does NOT
 * replace Android decoder/microphone tests and is labelled separately in reports. */
class NeuralCoreReplayTest {
    private data class Note(val available:Long,val onset:Long,val pitch:Int,val probability:Float)
    @Test fun replayModelNotes() {
        val configured=System.getenv("PIANO_NEURAL_MANIFEST");assumeNotNull(configured)
        val target=File(configured!!)
        val folders=if(target.isDirectory)listOf(target)else target.readLines().filter{it.isNotBlank()}.map{File(it)}
        val notesName=System.getenv("PIANO_NEURAL_NOTES")?:"magenta.notes.tsv"
        for(folder in folders){
            val events=File(folder,"graph.tsv").readLines().map{line->
                val p=line.split('\t')
                fun pitches(s:String)=if(s.isBlank())intArrayOf()else s.split(',').map(String::toInt).toIntArray()
                GraphEvent(p[0].toLong(),p[1].toLong(),p[2].toInt(),p[3].toInt(),p[4].toLong(),pitches(p[5]),pitches(p[6]),p[7].toFloat())
            }
            val follower=SymbolicScoreFollower(ScoreGraph(events,endTick=events.last().tick+events.last().duration))
            val notes=File(folder,notesName).readLines().drop(1).map{line->val p=line.split('\t');Note(kotlin.math.round(p[0].toDouble()*1000).toLong(),kotlin.math.round(p[1].toDouble()*1000).toLong(),p[2].toInt(),p[3].toFloat())}
            var next=0;var samples=0L
            File(folder,"audio.f32").inputStream().buffered().use { input ->
                File(folder,"symbolic-trace.tsv").bufferedWriter().use { out ->
                    val raw=ByteArray(1024*4)
                    while(true){
                        var read=0;while(read<raw.size){val n=input.read(raw,read,raw.size-read);if(n<0)break;read+=n}
                        if(read<raw.size)break
                        samples+=1024;val now=samples*1000/44100
                        var attack=false
                        while(next<notes.size&&notes[next].available<=now){
                            val n=notes[next++];val pitches=FloatArray(128).also{it[n.pitch]=n.probability}
                            follower.accept(PerformanceObservation(n.available,pitches,true,true,analysisLatencyMs=n.available-n.onset,attacks=pitches))
                            attack=true
                        }
                        val buffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                        var sum=0.0;repeat(1024){val x=buffer.float;sum+=x.toDouble()*x}
                        val db=10*kotlin.math.log10(maxOf(1e-18,sum/1024))
                        val p=follower.advance(now,db>=-55)
                        out.write("${samples/44100.0}\t${p.tick}\t${p.confidence}\t${p.status}\t$attack\t$db\n")
                    }
                }
            }
            println("NEURAL_CORE ${folder.name}: ${notes.size} model notes")
        }
    }
}
