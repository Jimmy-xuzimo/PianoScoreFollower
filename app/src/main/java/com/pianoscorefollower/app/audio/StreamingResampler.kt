package com.pianoscorefollower.app.audio

/** Linear streaming rate conversion; state is kept across capture hops. */
internal class StreamingResampler(private val sourceRate:Int,private val targetRate:Int=44100,private val tuningCents:Float=0f) {
    private val step=sourceRate.toDouble()/targetRate*Math.pow(2.0,-tuningCents/1200.0)
    private var index=-1L
    private var next=0.0
    private var previous=0f
    fun accept(input:FloatArray):FloatArray {
        if(sourceRate==targetRate&&tuningCents==0f)return input
        val out=ArrayList<Float>((input.size.toLong()*targetRate/sourceRate+2).toInt())
        for(x in input){
            index++
            while(next<=index){
                val f=(next-(index-1)).coerceIn(0.0,1.0).toFloat()
                out.add(if(index==0L)x else previous+(x-previous)*f)
                next+=step
            }
            previous=x
        }
        return out.toFloatArray()
    }
}
