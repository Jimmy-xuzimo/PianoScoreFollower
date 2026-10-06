package com.pianofollower.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ln

enum class CalibrationPhase {
    /** Capture is not running. */
    Idle,

    /** Waiting for the player to sound middle C so the reference offset can be learned. */
    AwaitingReference,

    /** A reference is held; every sounding note is named. */
    Ready,
}

data class CalibrationState(
    val isRunning: Boolean = false,
    val phase: CalibrationPhase = CalibrationPhase.Idle,
    /** Cents the instrument sits above (positive) or below (negative) concert pitch. */
    val offsetCents: Float = 0f,
    val hasCalibration: Boolean = false,
    val noteName: String = "",
    val pitchClass: Int = -1,
    val octave: Int = -1,
    /** Raw measured fundamental, before the calibration offset is removed. */
    val frequencyHz: Float = 0f,
    val correctedHz: Float = 0f,
    /** Distance from equal temperament once the instrument's own offset is removed. */
    val centsDeviation: Float = 0f,
    val midiNote: Int = -1,
    val confidence: Float = 0f,
    val collected: Int = 0,
    val referenceSamples: Int = CalibrationEngine.REFERENCE_SAMPLES,
    val isSounding: Boolean = false,
    val message: String? = null,
)

/**
 * Learns the instrument's pitch offset from a single middle C and then names whatever
 * is played.
 *
 * The offset is measured in cents rather than hertz, which makes it octave
 * independent: if the detector reports C5 instead of C4 the ratio to the nearest
 * equal-tempered C is unchanged, so the learned offset is still right. That matters
 * because an octave slip is the one mistake a harmonic-product detector is most likely
 * to make on a piano's bright harmonics.
 */
class CalibrationEngine {

    private val _state = MutableStateFlow(CalibrationState())
    val state: StateFlow<CalibrationState> = _state.asStateFlow()

    private val readings = ArrayList<Float>(REFERENCE_SAMPLES)
    private var offsetCents = 0f
    private var hasCalibration = false

    @Synchronized
    fun start() {
        readings.clear()
        _state.value = _state.value.copy(
            isRunning = true,
            phase = if (hasCalibration) CalibrationPhase.Ready else CalibrationPhase.AwaitingReference,
            offsetCents = offsetCents,
            hasCalibration = hasCalibration,
            collected = 0,
            isSounding = false,
            message = if (hasCalibration) null else "请先弹奏中央 C，校准你的钢琴",
        )
    }

    @Synchronized
    fun stop() {
        readings.clear()
        _state.value = _state.value.copy(isRunning = false, isSounding = false, collected = 0)
    }

    /** Drops the learned offset and starts over from the middle C prompt. */
    @Synchronized
    fun reset() {
        readings.clear()
        offsetCents = 0f
        hasCalibration = false
        _state.value = _state.value.copy(
            phase = if (_state.value.isRunning) CalibrationPhase.AwaitingReference else CalibrationPhase.Idle,
            offsetCents = 0f,
            hasCalibration = false,
            noteName = "",
            pitchClass = -1,
            octave = -1,
            frequencyHz = 0f,
            correctedHz = 0f,
            centsDeviation = 0f,
            midiNote = -1,
            confidence = 0f,
            collected = 0,
            isSounding = false,
            message = if (_state.value.isRunning) "请先弹奏中央 C，校准你的钢琴" else null,
        )
    }

    @Synchronized
    fun onFrame(frame: AudioFrame) {
        val current = _state.value
        if (!current.isRunning) return

        val sounding = frame.levelDb >= frame.noiseFloorDb + SOUND_MARGIN_DB &&
            frame.levelDb >= MIN_SOUND_LEVEL_DB
        val voiced = sounding &&
            frame.pitchHz > 0f &&
            frame.pitchConfidence >= MIN_CONFIDENCE_DISPLAY

        if (!voiced) {
            // A partial reference reading must not survive into the next attempt: the
            // player may have let go halfway through and struck a different key.
            if (readings.isNotEmpty()) readings.clear()
            _state.value = current.copy(isSounding = false, collected = 0)
            return
        }

        var message = current.message
        var phase = current.phase

        if (phase == CalibrationPhase.AwaitingReference) {
            val semitonesFromC4 = 12f * (ln((frame.pitchHz / C4_HZ).toDouble()) / LN2).toFloat()
            val nearestSemitone = Math.round(semitonesFromC4)
            // One semitone is 100 cents, so the residual is scaled by 100 rather
            // than by the 1200 that converts octaves into cents.
            val deviation = 100f * (semitonesFromC4 - nearestSemitone)
            val pitchClass = ((nearestSemitone % 12) + 12) % 12
            val settled = frame.pitchConfidence >= MIN_CONFIDENCE_REFERENCE

            if (pitchClass == 0 && abs(deviation) <= REFERENCE_TOLERANCE_CENTS && settled) {
                readings.add(deviation)
                if (readings.size >= REFERENCE_SAMPLES) {
                    val spread = readings.max() - readings.min()
                    if (spread <= MAX_REFERENCE_SPREAD_CENTS) {
                        offsetCents = medianOf(readings)
                        hasCalibration = true
                        phase = CalibrationPhase.Ready
                        message = describeOffset(offsetCents)
                        readings.clear()
                    } else {
                        readings.clear()
                        message = "音高不太稳定，请按住中央 C 再弹一次"
                    }
                } else {
                    message = null
                }
            } else {
                readings.clear()
                message = "检测到 ${noteNameOf(frame.pitchHz)}，请弹奏中央 C（键盘正中间的那个 C）"
            }
        }

        val correctedHz = frame.pitchHz / centsToRatio(offsetCents)
        val midi = midiOf(correctedHz)
        if (midi < MIN_MIDI || midi > MAX_MIDI) {
            _state.value = current.copy(
                isSounding = true,
                collected = readings.size,
                phase = phase,
                message = "这个音超出了识别范围，请弹奏钢琴键盘上的音",
            )
            return
        }

        val deviationCents = 1200f *
            (ln((correctedHz / frequencyOf(midi)).toDouble()) / LN2).toFloat()

        _state.value = current.copy(
            phase = phase,
            offsetCents = offsetCents,
            hasCalibration = hasCalibration,
            isSounding = true,
            noteName = noteName(midi),
            pitchClass = ((midi % 12) + 12) % 12,
            octave = midi / 12 - 1,
            frequencyHz = frame.pitchHz,
            correctedHz = correctedHz,
            centsDeviation = deviationCents,
            midiNote = midi,
            confidence = frame.pitchConfidence,
            collected = readings.size,
            message = message,
        )
    }

    private fun describeOffset(cents: Float): String = when {
        abs(cents) < 5f -> "校准完成：音准很好，与标准音高几乎一致"
        cents > 0f -> "校准完成：你的钢琴整体偏高 %.0f 音分，已自动补偿".format(cents)
        else -> "校准完成：你的钢琴整体偏低 %.0f 音分，已自动补偿".format(-cents)
    }

    private fun medianOf(values: List<Float>): Float {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            0.5f * (sorted[middle - 1] + sorted[middle])
        }
    }

    companion object {
        const val REFERENCE_SAMPLES = 8

        private const val C4_HZ = 261.6256f
        private const val SOUND_MARGIN_DB = 6f
        private const val MIN_SOUND_LEVEL_DB = -60f
        /*
         * The gate already requires the input to be clearly above the room's noise
         * floor, so the confidence bar here only has to reject unpitched transients.
         * Keeping it low means quiet high notes and short bass notes still name
         * themselves instead of leaving the readout blank.
         */
        private const val MIN_CONFIDENCE_DISPLAY = 0.22f
        private const val MIN_CONFIDENCE_REFERENCE = 0.45f
        private const val REFERENCE_TOLERANCE_CENTS = 60f
        private const val MAX_REFERENCE_SPREAD_CENTS = 45f
        private const val MIN_MIDI = 21
        private const val MAX_MIDI = 108

        private val LN2 = ln(2.0)
        private val NOTE_NAMES = arrayOf(
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
        )

        fun frequencyOf(midi: Int): Float = (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()

        fun midiOf(frequencyHz: Float): Int =
            Math.round(69.0 + 12.0 * ln(frequencyHz / 440.0) / ln(2.0)).toInt()

        fun noteName(midi: Int): String =
            NOTE_NAMES[((midi % 12) + 12) % 12] + (midi / 12 - 1)

        /** Name of the equal-tempered note nearest to [frequencyHz]. */
        fun noteNameOf(frequencyHz: Float): String = noteName(midiOf(frequencyHz))

        /** Frequency multiplier for an interval of [cents]. */
        fun centsToRatio(cents: Float): Float =
            Math.pow(2.0, cents / 1200.0).toFloat()
    }
}
