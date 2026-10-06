package com.pianofollower.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Converts the shipped two-hand MIDI so the viewer's import path is checked against a
 * real piece rather than a synthetic one. Gradle runs unit tests from the module
 * directory, so the asset can be read straight from `src/main/assets`.
 */
class MidiToMusicXmlTest {

    private fun loadSample(name: String): ByteArray {
        val file = File("src/main/assets/samples/$name")
        assertTrue("missing test asset: ${file.absolutePath}", file.exists())
        return file.readBytes()
    }

    @Test
    fun `the truth that you leave converts to a two-staff piano score`() {
        val xml = MidiToMusicXml.convert(loadSample("the_truth_that_you_leave.mid"))

        assertTrue("expected a grand staff", xml.contains("<staves>2</staves>"))
        assertTrue("expected a treble clef", xml.contains("<clef number=\"1\"><sign>G</sign>"))
        assertTrue("expected a bass clef", xml.contains("<clef number=\"2\"><sign>F</sign>"))
        assertTrue("expected the track title", xml.contains("The truth that you leave"))

        // The file is in Bb major (two flats), not the C major default.
        assertTrue("expected a Bb key signature", xml.contains("<fifths>-2</fifths>"))
        assertTrue("expected tempo 130", xml.contains("<sound tempo=\"130\"/>"))

        val measures = Regex("<measure number=").findAll(xml).count()
        assertEquals(116, measures)
    }

    @Test
    fun `both hands survive the conversion`() {
        val xml = MidiToMusicXml.convert(loadSample("the_truth_that_you_leave.mid"))

        // The MIDI holds 792 right-hand and 796 left-hand notes.
        val notes = Regex("<note>").findAll(xml).count()
        assertTrue("expected roughly 1588 notes but found $notes", notes > 1500)

        assertTrue("expected notes on staff 1", xml.contains("<staff>1</staff>"))
        assertTrue("expected notes on staff 2", xml.contains("<staff>2</staff>"))
    }

    @Test
    fun `every voice fills its bar`() {
        val xml = MidiToMusicXml.convert(loadSample("the_truth_that_you_leave.mid"))

        val barTicks = 480 * 4
        val bars = xml.split("<measure number=").drop(1)
        assertEquals(116, bars.size)

        bars.forEachIndexed { index, bar ->
            // A <backup> rewinds the stream, so it starts the next voice. Within one
            // voice the durations must add up to exactly the bar, otherwise the score
            // is missing time. Notes flagged <chord/> stack on the previous onset and
            // must not be counted again.
            val sections = bar.split("<backup>").drop(1).toMutableList().also {
                it.add(0, bar.substringBefore("<backup>"))
            }
            sections.forEachIndexed { sectionIndex, section ->
                val voiceTicks = Regex("<note>(.*?)</note>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(section)
                    .filter { !it.groupValues[1].contains("<chord/>") }
                    .sumOf { note ->
                        Regex("<duration>(\\d+)</duration>").find(note.groupValues[1])
                            ?.groupValues?.get(1)?.toInt() ?: 0
                    }
                assertEquals(
                    "measure ${index + 1} voice ${sectionIndex + 1} is $voiceTicks ticks, expected $barTicks",
                    barTicks,
                    voiceTicks,
                )
            }
        }
    }
}