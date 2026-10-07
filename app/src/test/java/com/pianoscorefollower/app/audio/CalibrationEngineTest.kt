package com.pianoscorefollower.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tuner learns one number — how far the instrument sits from concert pitch —
 * from middle C, and every later reading is corrected by it. The tolerance checks
 * below are what decides whether a real piano can be calibrated at all.
 */
class CalibrationEngineTest {

    private val c4 = 261.6256f
    private val e4 = 329.6276f

    private fun frame(frequencyHz: Float, confidence: Float = 0.9f) = AudioFrame(
        levelDb = -20f,
        peakDb = -12f,
        noiseFloorDb = -70f,
        isOnset = true,
        flux = 0.5f,
        peakMagnitude = 0.4f,
        chroma = FloatArray(12),
        pitchHz = frequencyHz,
        pitchConfidence = confidence,
    )

    /** A note [cents] away from equal temperament. */
    private fun detuned(frequencyHz: Float, cents: Float): Float =
        frequencyHz * Math.pow(2.0, cents / 1200.0).toFloat()

    @Test
    fun `middle c teaches the engine how far the piano is off`() {
        val engine = CalibrationEngine()
        engine.start()

        repeat(CalibrationEngine.REFERENCE_SAMPLES + 2) {
            engine.onFrame(frame(detuned(c4, 25f)))
        }

        val state = engine.state.value
        assertTrue(state.hasCalibration)
        assertEquals(CalibrationPhase.Ready, state.phase)
        // A quarter-semitone sharp must land inside the tolerance, not 12x outside it.
        assertEquals(25f, state.offsetCents, 2f)
    }

    @Test
    fun `a note other than middle c never calibrates`() {
        val engine = CalibrationEngine()
        engine.start()

        repeat(CalibrationEngine.REFERENCE_SAMPLES + 6) {
            engine.onFrame(frame(e4))
        }

        assertFalse(engine.state.value.hasCalibration)
    }

    @Test
    fun `a wandering reference is rejected instead of averaged`() {
        val engine = CalibrationEngine()
        engine.start()

        repeat(CalibrationEngine.REFERENCE_SAMPLES) { index ->
            // Alternating by 80 cents: far more spread than a held key should show.
            engine.onFrame(frame(detuned(c4, if (index % 2 == 0) -40f else 40f)))
        }

        assertFalse(engine.state.value.hasCalibration)
    }

    @Test
    fun `once calibrated the played note is named and corrected`() {
        val engine = CalibrationEngine()
        engine.start()
        repeat(CalibrationEngine.REFERENCE_SAMPLES + 2) {
            engine.onFrame(frame(detuned(c4, 25f)))
        }

        // The piano's E is sharp by the same 25 cents, so the reading must still
        // come out as a clean E4 rather than as a quarter-tone between notes.
        engine.onFrame(frame(detuned(e4, 25f)))

        val state = engine.state.value
        assertTrue(state.isSounding)
        assertEquals("E4", state.noteName)
        assertEquals(4, state.pitchClass)
        assertEquals(0f, state.centsDeviation, 3f)
    }

    @Test
    fun `silence is not reported as a note`() {
        val engine = CalibrationEngine()
        engine.start()

        engine.onFrame(frame(0f, confidence = 0f).copy(levelDb = -80f))

        assertFalse(engine.state.value.isSounding)
        assertEquals("", engine.state.value.noteName)
    }

    @Test
    fun `resetting drops the learned offset`() {
        val engine = CalibrationEngine()
        engine.start()
        repeat(CalibrationEngine.REFERENCE_SAMPLES + 2) {
            engine.onFrame(frame(detuned(c4, 25f)))
        }
        assertTrue(engine.state.value.hasCalibration)

        engine.reset()

        assertFalse(engine.state.value.hasCalibration)
        assertEquals(0f, engine.state.value.offsetCents, 0.01f)
    }
}
