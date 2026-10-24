// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The headline sentence for a failed fontmake run, from the lines it printed. */
class FailureExplanationTest {
    @Test
    fun theLineFontmakeEndsOnIsTheHeadline() {
        val lines =
            listOf(
                "INFO:fontmake.font_project:Building TTF for X",
                "Traceback (most recent call last):",
                "ValueError: something deep inside",
                "fontmake: Error: In 'x.ufo': Compiling UFO failed: the glyph A has a bad point",
            )
        assertEquals(
            "fontmake stopped with an error: In 'x.ufo': Compiling UFO failed: the glyph A has a bad point.",
            explainFailure(lines, 2),
        )
    }

    @Test
    fun aTracebackWithoutAFontmakeLineUsesItsLastExceptionLine() {
        val lines = listOf("Traceback (most recent call last):", "  File \"a.py\", line 3, in f", "KeyError: 'public.glyphOrder'")
        assertEquals("fontmake stopped with an error: KeyError: 'public.glyphOrder'.", explainFailure(lines, 1))
    }

    @Test
    fun cu2quFailuresNameTheConversion() {
        val lines = listOf("fontmake: Error: In 'x.ufo': Compiling UFO failed: Cu2QuError: curve could not be converted")
        val sentence = explainFailure(lines, 1)
        assertTrue(sentence.startsWith("A curve could not be converted from cubic to quadratic (cu2qu)"), sentence)
    }

    @Test
    fun anOverlapRemoverThatCannotReadAQcurveIsNamedAsSuch() {
        val lines =
            listOf(
                "fontmake: Error: In 'p.ufo': Compiling UFO failed: ('Trying to perform operation on unsupported segment type.', 'qcurve')",
            )
        val sentence = explainFailure(lines, 1)
        assertTrue(sentence.startsWith("fontmake could not remove overlaps because an outline uses a curve form"), sentence)
        assertTrue("qcurve" in sentence)
    }

    @Test
    fun aMissingPackageIsSaidToBeMissing() {
        val sentence = explainFailure(listOf("ModuleNotFoundError: No module named 'pathops'"), 1)
        assertEquals("A Python package fontmake needs is missing: ModuleNotFoundError: No module named 'pathops'.", sentence)
    }

    @Test
    fun anInvalidUfoIsNamed() {
        val sentence = explainFailure(listOf("ufoLib.errors.UFOLibError: The file glyphs/A_.glif is not valid"), 1)
        assertTrue(sentence.startsWith("The UFO is not valid, so fontmake could not read it:"), sentence)
    }

    @Test
    fun ufo2ftIsNamedWhenTheMessageNamesIt() {
        val sentence = explainFailure(listOf("ERROR:x:ufo2ft could not build the kern table"), 1)
        assertEquals("ufo2ft, the part of fontmake that builds the font, stopped: ufo2ft could not build the kern table.", sentence)
    }

    @Test
    fun aLoggedErrorIsUsedWhenThereIsNothingBetter() {
        val sentence = explainFailure(listOf("INFO:a:fine", "ERROR:ufo2ft.filters.removeOverlaps:Failed to remove overlaps for Aogonek"), 1)
        assertEquals("fontmake stopped with an error: Failed to remove overlaps for Aogonek.", sentence)
    }

    @Test
    fun withNothingToGoOnItSaysSoAndNamesTheExitCode() {
        assertEquals(
            "fontmake stopped (exit code 3) and did not say why; the build log has everything it printed.",
            explainFailure(listOf("INFO:a:fine"), 3),
        )
    }

    @Test
    fun theSentenceIsOneLineAndNeverLong() {
        val long = "fontmake: Error: " + "word ".repeat(200) + "\twith\ttabs"
        val sentence = explainFailure(listOf(long), 1)
        assertFalse(sentence.contains('\n') || sentence.contains('\t'))
        assertTrue(sentence.length < 400, "${sentence.length} characters")
        assertTrue(sentence.endsWith("."))
    }
}
