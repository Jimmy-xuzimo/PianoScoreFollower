/* Kotlin adaptation of Matchmaker OnlineTimeWarpingArztFrame / oltw_arzt_loop.
 * Apache-2.0; attribution and exact upstream revision in third_party/matchmaker/NOTICE.
 * Keep recurrence, normalisation, window and step clamp in parity with upstream.
 */
package com.pianoscorefollower.app.follower

import kotlin.math.abs

class ArztOnlineTimeWarping(
    private val reference: Array<FloatArray>,
    private val frameRate: Double = 44100.0 / 1024,
    private val stepSize: Int = 3,
) {
    private val previous = FloatArray(reference.size + 1) { Float.POSITIVE_INFINITY }
    private val next = FloatArray(reference.size + 1) { Float.POSITIVE_INFINITY }
    private val windowSize = Math.rint(10 * frameRate).toInt()
    private val startWindow = Math.rint(.1 * frameRate).toInt().coerceAtLeast(1)
    var frame = 0; private set
    var inputIndex = 0; private set
    var cost = Float.POSITIVE_INFINITY; private set

    init {
        require(reference.isNotEmpty())
        require(reference[0].isNotEmpty() && reference.all { it.size == reference[0].size })
    }

    fun accept(feature: FloatArray): Int {
        require(feature.size == reference[0].size)
        val w = if (frame < startWindow) startWindow else windowSize
        val start = (frame - w).coerceAtLeast(0)
        val end = (frame + w).coerceAtMost(reference.size)
        val distances = FloatArray(end - start)
        for (i in start until end) {
            var distance = 0f
            for (j in feature.indices) distance += abs(reference[i][j] - feature[j])
            distances[i - start] = distance
        }
        var best = Float.POSITIVE_INFINITY
        var index = (frame - stepSize).coerceAtLeast(0)
        if (start == 0 && inputIndex == 0) {
            // Deliberately retain upstream's first-cell initialisation (window sum).
            next[1] = distances.sum(); best = next[1]; index = 0
        }
        for (i in start until end) {
            if (i == 0 && inputIndex == 0) continue
            val d = distances[i - start]
            val total = minOf(next[i] + d, previous[i + 1] + d, previous[i] + d)
            next[i + 1] = total
            val normalised = total / (inputIndex + i + 1f)
            if (normalised < best) { best = normalised; index = i }
        }
        next.copyInto(previous)
        next.fill(Float.POSITIVE_INFINITY)
        frame = if (inputIndex == 0) index else index.coerceIn(frame, minOf(reference.lastIndex, frame + stepSize))
        cost = best
        inputIndex++
        return frame
    }
}
