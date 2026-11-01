/* Google Magenta's released realtime unidirectional Onsets and Frames model.
 * Model provenance, hash and Apache-2.0 attribution: third_party/magenta/. */
package com.pianoscorefollower.app.audio

import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

data class NeuralPianoFrame(val onsetTimeMs:Long,val availableTimeMs:Long,
    val pitches:FloatArray,val attacks:FloatArray,val velocities:FloatArray)

/** Loaded on a background worker. Inference has one analysis-thread owner. */
class MagentaPianoModel(assets:AssetManager):AutoCloseable {
    @Volatile var lastInferenceMs=0.0;private set
    @Volatile var maxInferenceMs=0.0;private set
    @Volatile var invocations=0L;private set
    @Volatile var totalInferenceMs=0.0;private set
    private val interpreter:Interpreter
    private val input=ByteBuffer.allocateDirect(17920*4).order(ByteOrder.nativeOrder())
    private val outputs:Map<Int,Any>
    private val buffers=HashMap<String,ByteBuffer>()
    init {
        val mapped=assets.openFd("models/onsets_frames_wavinput_uni.tflite").use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { it.map(FileChannel.MapMode.READ_ONLY,fd.startOffset,fd.declaredLength) }
        }
        interpreter=Interpreter(mapped,Interpreter.Options().setNumThreads(2).setUseXNNPACK(true))
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(17920))){"钢琴模型输入格式不匹配"}
        outputs=(0 until interpreter.outputTensorCount).associateWith { i ->
            val tensor=interpreter.getOutputTensor(i)
            require(tensor.shape().contentEquals(intArrayOf(1,32,88))){"钢琴模型输出格式不匹配"}
            ByteBuffer.allocateDirect(32*88*4).order(ByteOrder.nativeOrder()).also{buffers[tensor.name()]=it}
        }
        require(listOf("frame_logits","onset_logits","velocity_values").all{it in buffers})
    }
    @Synchronized internal fun infer(samples:FloatArray):Map<String,FloatArray> {
        input.clear();input.asFloatBuffer().put(samples)
        buffers.values.forEach{it.clear()}
        val started=System.nanoTime()
        interpreter.runForMultipleInputsOutputs(arrayOf(input),outputs)
        lastInferenceMs=(System.nanoTime()-started)/1e6
        maxInferenceMs=maxOf(maxInferenceMs,lastInferenceMs);totalInferenceMs+=lastInferenceMs;invocations++
        return buffers.mapValues { (_,buffer) -> buffer.rewind();FloatArray(32*88){buffer.float} }
    }
    override fun close(){interpreter.close()}

    /** Same rolling window and emitted frame range as tools/probe-magenta.py. */
    inner class Stream(sampleRate:Int,tuningCents:Float=0f) {
        private val resampler=StreamingResampler(sampleRate,16000,tuningCents)
        private val timestampRate=16000*Math.pow(2.0,tuningCents/1200.0)
        private val window=FloatArray(17920)
        private val block=FloatArray(2048)
        private var filled=0
        private var samples=0L
        private var previous=FloatArray(88)
        fun accept(pcm:FloatArray):List<NeuralPianoFrame> {
            val frames=ArrayList<NeuralPianoFrame>()
            for(x in resampler.accept(pcm)){
                block[filled++]=x
                if(filled<block.size)continue
                filled=0;samples+=block.size
                System.arraycopy(window,block.size,window,0,window.size-block.size)
                System.arraycopy(block,0,window,window.size-block.size,block.size)
                val out=infer(window)
                val onset=out.getValue("onset_logits");val sounding=out.getValue("frame_logits");val velocity=out.getValue("velocity_values")
                for(frame in 26 until 30){
                    // Upstream labels use frame_index / frames_per_second.
                    // The uncentred STFT starts at frame*512; adding half the
                    // 2048-sample window would shift every label 64ms late.
                    val at=samples-window.size+frame*512
                    if(at<0)continue
                    val pitches=FloatArray(128);val attacks=FloatArray(128);val velocities=FloatArray(128)
                    for(n in 0 until 88){
                        val i=frame*88+n
                        pitches[n+21]=sigmoid(sounding[i])
                        if(onset[i]>0f&&previous[n]<=0f)attacks[n+21]=sigmoid(onset[i])
                        velocities[n+21]=velocity[i].coerceIn(0f,1f)
                        previous[n]=onset[i]
                    }
                    frames.add(NeuralPianoFrame((at*1000/timestampRate).toLong(),(samples*1000/timestampRate).toLong(),pitches,attacks,velocities))
                }
            }
            return frames
        }
    }
    private fun sigmoid(x:Float)=(1.0/(1.0+exp(-x.toDouble()))).toFloat()
}
