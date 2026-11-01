package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.audio.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Cached causal inference -> actual production postprocessing and follower.
 * No labels or future model frames are passed to the application pipeline. */
class NeuralPipelineReplayTest {
    @Test fun replayCachedInference() {
        val configured=System.getenv("PIANO_NEURAL_MANIFEST");assumeNotNull(configured)
        val target=File(configured!!)
        val folders=if(target.isDirectory)listOf(target)else target.readLines().filter{it.isNotBlank()}.map{File(it)}
        for(folder in folders){
            val binary=File(folder,"magenta-linear.notes.frames.bin")
            require(binary.exists()){ "Missing causal inference: $binary" }
            val data=ByteBuffer.wrap(binary.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val magic=ByteArray(4).also{data.get(it)}.decodeToString()
            require(magic=="NPF1"||magic=="NPF2")
            val frames=ArrayList<NeuralPianoFrame>()
            while(data.hasRemaining()){
                val onset=data.long-(if(magic=="NPF1")64 else 0);val available=data.long
                frames.add(NeuralPianoFrame(onset,available,FloatArray(128){data.float},FloatArray(128){data.float},FloatArray(128)))
            }
            val events=File(folder,"graph.tsv").readLines().map{line->
                val p=line.split('\t')
                fun pitches(s:String)=if(s.isBlank())intArrayOf()else s.split(',').map(String::toInt).toIntArray()
                ScoreEvent(p[0].toLong(),p[2].toInt(),FloatArray(12),pitches(p[5]).size,false,
                    pitches(p[5]),pitches(p[6]),p[1].toInt(),p[4].toLong(),p[3].toInt(),p[7].toFloat())
            }
            val engine=FollowerEngine()
            engine.attach(null,ScoreTimeline(960,events,events.last().tick+events.last().duration,events.maxOf{it.measure}+1),120f)
            val base=System.nanoTime()/1_000_000+1
            val analyzer=AudioAnalyzer(sampleRate=44100)
            var next=0;var samples=0L
            File(folder,"audio.f32").inputStream().buffered().use{input->
                File(folder,"neural-trace.tsv").bufferedWriter().use{out->
                    val raw=ByteArray(4096)
                    while(true){
                        var read=0
                        while(read<raw.size){val n=input.read(raw,read,raw.size-read);if(n<0)break;read+=n}
                        if(read<raw.size)break
                        samples+=1024;val now=samples*1000/44100
                        val ready=ArrayList<NeuralPianoFrame>()
                        while(next<frames.size&&frames[next].availableTimeMs<=now)ready.add(frames[next++])
                        val buffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                        val frame=analyzer.acceptDecodedPianoFrames(FloatArray(1024){buffer.float},ready).copy(sampleTimeMs=base+now)
                        engine.acceptAudio(frame)
                        val state=engine.state.value;val p=state.position
                        out.write("${samples/44100.0}\t${p.tick}\t${p.confidence}\t${p.status}\t${state.isOnset}\t${frame.levelDb}\n")
                    }
                }
            }
            println("NEURAL_PIPELINE ${folder.name}: $next causal frames")
        }
    }
}
