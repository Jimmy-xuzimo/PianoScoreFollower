package com.pianoscorefollower.app.audio

import kotlin.math.sqrt

/**
 * Turns a stream of hop-sized PCM windows into the level, onset and chroma signals
 * that the UI and the score follower consume. Owns the sliding FFT window plus the
 * envelope followers, so it must be driven from a single thread.
 */
internal class AudioAnalyzer(
    private val hopSize: Int = AudioConfig.HOP_SIZE,
    private val fftSize: Int = AudioConfig.FFT_SIZE,
    private val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    model:MagentaPianoModel?=null,
    initialTuningCents:Float=0f,
) {

    private val onsetDetector by lazy { OnsetDetector(hopSize = hopSize, fftSize = fftSize, sampleRate = sampleRate) }
    private val piano by lazy { PianoFeatures(sampleRate) }
    private val neural=model?.let{it.Stream(sampleRate,initialTuningCents)}
    private var inputSamples=0L
    private var lastNeuralPitches=FloatArray(128)
    var tuningCents: Float=initialTuningCents
        set(value) { field=value;if(neural==null)piano.tuningCents = value }
    private val chromaExtractor by lazy { ChromaExtractor(sampleRate = sampleRate) }
    private val pitchDetector by lazy { PitchDetector(sampleRate = sampleRate, fftSize = AudioConfig.CHROMA_FFT_SIZE) }
    private val windowBuffer = FloatArray(fftSize)

    /**
     * Switches on the extra fundamental-frequency estimate. The score follower uses polyphonic pitch features;
     * the calibration screen is the only consumer of this extra fundamental estimate and the
     * cost is not paid on the following path.
     */
    var pitchDetectionEnabled: Boolean = false

    /** Sliding window for the chroma stage; longer than the onset window on purpose. */
    private val chromaBuffer = FloatArray(AudioConfig.CHROMA_FFT_SIZE)

    /** Allow the fast harmonic window to settle before reporting each attack.
     * Bass evidence keeps its longer window; the exact delay accompanies every
     * frame so 44.1 and 48 kHz inputs use the correct time compensation. */
    private val onsetDelayHops = (AudioConfig.CHROMA_FFT_SIZE * 5 / 8 / hopSize).coerceAtLeast(0)
    private val analysisLatencyMs = onsetDelayHops * hopSize * 1000L / sampleRate
    private val pendingRearticulations = ArrayDeque<Boolean>()
    private val pendingOnsets = ArrayDeque<Boolean>(onsetDelayHops + 1)

    private val minSignalBin = (MIN_SIGNAL_HZ * fftSize / sampleRate).toInt().coerceAtLeast(1)
    private val maxSignalBin = (MAX_SIGNAL_HZ * fftSize / sampleRate).toInt()
        .coerceIn(minSignalBin + 1, fftSize / 2)

    private var smoothedDb = AudioConfig.MIN_LEVEL_DB
    private var peakDb = AudioConfig.MIN_LEVEL_DB
    private var noiseFloorDb = AudioConfig.MIN_LEVEL_DB
    private var hasNoiseEstimate = false

    /** [hop] must hold exactly [hopSize] samples, and the caller must not mutate it. */
    fun accept(hop: FloatArray): AudioFrame {
        inputSamples+=hop.size
        val neuralFrames=neural?.accept(hop).orEmpty()
        if(neural!=null){
            return decodedPianoFrame(hop,neuralFrames)
        }
        return legacyFrame(hop,neuralFrames)
    }

    /** Replay seam after inference, also used by the live neural branch. Cached
     * frames must contain only results available by the end of this PCM hop. */
    internal fun acceptDecodedPianoFrames(hop:FloatArray,frames:List<NeuralPianoFrame>):AudioFrame {
        inputSamples+=hop.size
        return decodedPianoFrame(hop,frames)
    }

    private fun decodedPianoFrame(hop:FloatArray,neuralFrames:List<NeuralPianoFrame>):AudioFrame {
            neuralFrames.lastOrNull()?.let{lastNeuralPitches=it.pitches}
            var sum=0.0;for(x in hop)sum+=x.toDouble()*x
            val db=AudioConfig.toDb(sqrt(sum/hop.size).toFloat())
            smoothedDb+= (db-smoothedDb)*(if(db>smoothedDb)ATTACK else RELEASE)
            peakDb=maxOf(db,peakDb-PEAK_DECAY_DB)
            if(!hasNoiseEstimate){noiseFloorDb=db;hasNoiseEstimate=true}else noiseFloorDb+= (db-noiseFloorDb)*(if(db<noiseFloorDb)NOISE_TRACK_DOWN else NOISE_TRACK_UP)
            val classes=FloatArray(12)
            lastNeuralPitches.forEachIndexed { n,p -> if(p>=.5f)classes[n%12]+=p }
            return AudioFrame(smoothedDb,peakDb,noiseFloorDb,neuralFrames.any{f->f.attacks.any{it>=.5f}},0f,0f,classes,
                pitches=lastNeuralPitches,analysisLatencyMs=128,neural=neuralFrames,audioTimeMs=inputSamples*1000/sampleRate)
    }
    private fun legacyFrame(hop:FloatArray,neuralFrames:List<NeuralPianoFrame>):AudioFrame {
        System.arraycopy(windowBuffer, hopSize, windowBuffer, 0, fftSize - hopSize)
        System.arraycopy(hop, 0, windowBuffer, fftSize - hopSize, hopSize)

        val chromaSize = chromaBuffer.size
        System.arraycopy(chromaBuffer, hopSize, chromaBuffer, 0, chromaSize - hopSize)
        System.arraycopy(hop, 0, chromaBuffer, chromaSize - hopSize, hopSize)

        var sumSquares = 0.0
        for (sample in hop) sumSquares += (sample * sample).toDouble()
        val db = AudioConfig.toDb(sqrt(sumSquares / hop.size).toFloat())

        smoothedDb = if (db > smoothedDb) {
            smoothedDb + (db - smoothedDb) * ATTACK
        } else {
            smoothedDb + (db - smoothedDb) * RELEASE
        }
        peakDb = maxOf(db, peakDb - PEAK_DECAY_DB)

        if (!hasNoiseEstimate) {
            // Seeding from the first frame avoids a long warm-up during which a
            // noisy room would look like playing.
            noiseFloorDb = db
            hasNoiseEstimate = true
        } else if (db < noiseFloorDb) {
            noiseFloorDb += (db - noiseFloorDb) * NOISE_TRACK_DOWN
        } else {
            noiseFloorDb += (db - noiseFloorDb) * NOISE_TRACK_UP
        }

        // Broadband noise alone produces plenty of spectral flux, so onsets are only
        // considered once the input clearly rises above both the floor and an absolute
        // minimum. Without this the detector fires continuously on an idle microphone.
        val gateOpen = db > noiseFloorDb + ONSET_GATE_MARGIN_DB && db > MIN_ONSET_LEVEL_DB
        val onset = onsetDetector.process(windowBuffer, gateOpen)

        var peakMagnitude = 0f
        for (bin in minSignalBin until maxSignalBin) {
            val value = onsetDetector.spectrum[bin]
            if (value > peakMagnitude) peakMagnitude = value
        }

        pendingRearticulations.addLast(onset.rearticulation)
        val repeatAttack = if (pendingRearticulations.size > onsetDelayHops) pendingRearticulations.removeFirst() else false
        val chroma = chromaExtractor.extract(chromaBuffer)
        val pitches = piano.accept(hop)
        val pitch = if (pitchDetectionEnabled) {
            pitchDetector.estimate(chromaExtractor.spectrum)
        } else {
            PitchReading.SILENT
        }

        return AudioFrame(
            levelDb = smoothedDb,
            peakDb = peakDb,
            noiseFloorDb = noiseFloorDb,
            isOnset = delayedOnset(onset.isOnset),
            flux = onset.flux,
            peakMagnitude = peakMagnitude,
            chroma = chroma,
            pitchHz = pitch.frequencyHz,
            pitchConfidence = pitch.confidence,
            pitches = pitches,
            rearticulation = repeatAttack,
            analysisLatencyMs = analysisLatencyMs,
            neural=neuralFrames,
            audioTimeMs=inputSamples*1000/sampleRate,
        )
    }

    /** Reports the onset flag [onsetDelayHops] frames after it was raised, pairing it with
     * the chroma window that actually covers the struck note. */
    private fun delayedOnset(isOnset: Boolean): Boolean {
        if (onsetDelayHops == 0) return isOnset
        pendingOnsets.addLast(isOnset)
        return if (pendingOnsets.size > onsetDelayHops) pendingOnsets.removeFirst() else false
    }

    private companion object {
        const val ATTACK = 0.55f
        const val RELEASE = 0.12f
        const val PEAK_DECAY_DB = 0.55f
        const val NOISE_TRACK_DOWN = 0.25f
        const val NOISE_TRACK_UP = 0.00005f
        const val ONSET_GATE_MARGIN_DB = 6f
        const val MIN_ONSET_LEVEL_DB = -60f

        /** ~55 Hz .. ~2.1 kHz, the span that carries the notated pitches. */
        const val MIN_SIGNAL_HZ = 55.0
        const val MAX_SIGNAL_HZ = 2093.0
    }
}
