package com.pianoscorefollower.app.follower

import com.pianoscorefollower.app.audio.*
import com.pianoscorefollower.app.performance.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Explicit opt-in corpus runner. Feeds the production analyzer and aligner in order. */
class AudioBenchmarkTest {
    @Test fun replayCorpus() {
        val configured = System.getenv("PIANO_BENCHMARK_MANIFEST")
        org.junit.Assume.assumeNotNull(configured)
        val manifest = configured!!
        for (path in File(manifest).readLines().filter { it.isNotBlank() }) {
            val folder=File(path)
            val events=File(folder,"graph.tsv").readLines().map { line ->
                val p=line.split('\t')
                fun pitches(v:String)=if(v.isBlank()) intArrayOf() else v.split(',').map { it.toInt() }.toIntArray()
                GraphEvent(p[0].toLong(),p[1].toLong(),p[2].toInt(),p[3].toInt(),p[4].toLong(),pitches(p[5]),pitches(p[6]),p[7].toFloat())
            }
            val graph=ScoreGraph(events)
            val aligner=OnlineAligner();aligner.load(graph)
            val analyzer=AudioAnalyzer()
            val stream=BufferedInputStream(FileInputStream(File(folder,"audio.f32")))
            val bytes=ByteArray(AudioConfig.HOP_SIZE*4);var index=0L
            val trace=File(folder,"trace.tsv").bufferedWriter()
            val start=System.nanoTime()
            stream.use { input -> trace.use { out ->
                while (true) {
                    var read=0
                    while (read<bytes.size) { val n=input.read(bytes,read,bytes.size-read);if(n<0) break;read+=n }
                    if(read<bytes.size) break
                    val buf=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val frame=analyzer.accept(FloatArray(AudioConfig.HOP_SIZE) { buf.float })
                    index+=AudioConfig.HOP_SIZE
                    val ms=index*1000/AudioConfig.SAMPLE_RATE
                    val active=frame.levelDb>=maxOf(-60f,frame.noiseFloorDb+5f)
                    val position=aligner.accept(PerformanceObservation(ms,frame.pitches,frame.isOnset,active,analysisLatencyMs=frame.analysisLatencyMs,rearticulation=frame.rearticulation))
                    out.write("${index.toDouble()/AudioConfig.SAMPLE_RATE}\t${position.tick}\t${position.confidence}\t${position.status}\t${frame.isOnset}\t${frame.levelDb}\n")
                }
            } }
            println("BENCHMARK ${folder.name}: ${(System.nanoTime()-start)/1e9}s compute")
        }
    }
}
