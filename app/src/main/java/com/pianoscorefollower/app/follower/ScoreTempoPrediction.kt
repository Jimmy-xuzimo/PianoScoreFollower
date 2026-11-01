package com.pianoscorefollower.app.follower

/** Causal tempo prediction from confirmed score attacks. Chord notes and isolated
 * timing outliers cannot each reset the display clock. Never emits played notes. */
class ScoreTempoPrediction(initialPeriod:Double) {
    private var tick:Long?=null
    private var time=0L
    private val intervals=ArrayDeque<Double>()
    var period=initialPeriod;private set
    fun confirm(atTick:Long,atTime:Long,quarter:Int,alignmentPeriod:Double){
        val previous=tick
        if(previous!=null && atTick>previous){
            val beats=(atTick-previous).toDouble()/quarter
            val seconds=(atTime-time)/1000.0
            val candidate=seconds/beats
            if(beats<=2 && seconds>=.08 && candidate in period*.5..period*2){
                intervals.addLast(candidate);if(intervals.size>7)intervals.removeFirst()
            }
        }
        if(previous==null || atTick!=previous){tick=atTick;time=atTime}
        val sorted=intervals.sorted()
        val measured=if(sorted.size>=3)sorted[sorted.size/2] else alignmentPeriod
        period=(.75*alignmentPeriod+.25*measured).coerceIn(.05,10.0)
    }
}
