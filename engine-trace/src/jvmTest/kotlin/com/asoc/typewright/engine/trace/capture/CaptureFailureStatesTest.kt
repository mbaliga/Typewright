// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The typed failure states of the capture, each provoked on a SYNTHETIC photograph (see [PhotoSpec]) and checked for the failure it
 * should be, the plain sentence it carries, and that the same photograph is read when the quality gates are switched off. The
 * limits themselves are "our heuristic" (CLAUDE.md law 5): they are set by looking at the synthetic set, not measured from a corpus
 * of real photographs, and every sentence says so.
 */
class CaptureFailureStatesTest {
    private val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets[0]

    private fun photo(spec: PhotoSpec) = SyntheticPhotos.make(sheet, PaperSize.A4, GuideStyle.LIGHT, spec)

    private fun failureOf(spec: PhotoSpec): CaptureFailure {
        val result = SheetCapture.read(photo(spec).gray)
        assertIs<CaptureResult.Failure>(result, "expected a failure for $spec")
        return result.failure
    }

    private fun assertPlainSentence(sentence: String) {
        assertTrue(sentence.length in 40..400, "a sentence a person can read at a glance: $sentence")
        assertTrue(sentence.trim().endsWith(".") || sentence.trim().endsWith("?"), "ends like a sentence: $sentence")
        assertTrue(sentence.first().isUpperCase() || sentence.first().isDigit(), "starts like a sentence: $sentence")
    }

    @Test
    fun aCoveredCornerIsNamedByItsPlaceInThePicture() {
        // Marks are numbered top left, top right, bottom right, bottom left. The sheet is upright and the camera is straight on, so
        // the corner of the picture the mark is nearest is the corner of the page.
        val expected = listOf(ImageCorner.TOP_LEFT, ImageCorner.TOP_RIGHT, ImageCorner.BOTTOM_RIGHT, ImageCorner.BOTTOM_LEFT)
        for ((covered, corner) in expected.withIndex()) {
            val failure = failureOf(PhotoSpec(seed = 40 + covered, coveredMarks = setOf(covered)))
            assertIs<CaptureFailure.CornersMissing>(failure)
            assertEquals(3, failure.foundCount)
            assertEquals(listOf(corner), failure.missing, "mark $covered covered")
            assertTrue(corner !in failure.found)
            assertTrue(failure.sentence.contains(corner.label), failure.sentence)
            assertTrue(failure.sentence.startsWith("3 of 4 corners"), failure.sentence)
            assertPlainSentence(failure.sentence)
        }
    }

    @Test
    fun twoCoveredCornersAreCountedButNoneIsNamed() {
        val failure = failureOf(PhotoSpec(seed = 45, coveredMarks = setOf(0, 2)))
        assertIs<CaptureFailure.CornersMissing>(failure)
        assertEquals(2, failure.foundCount)
        assertTrue(failure.missing.isEmpty(), "with two missing the reader does not guess which: ${failure.missing}")
        assertTrue(failure.sentence.startsWith("2 of 4 corners"), failure.sentence)
        assertPlainSentence(failure.sentence)
    }

    @Test
    fun aHiddenOrDamagedIdentityCodeIsUnreadableNotGuessed() {
        for (fault in listOf(CodeFault.HIDDEN, CodeFault.ONE_MODULE_FLIPPED)) {
            val failure = failureOf(PhotoSpec(seed = 46, codeFault = fault))
            assertEquals(CaptureFailure.IdentityUnreadable, failure, "$fault")
            assertPlainSentence(failure.sentence)
            assertTrue(failure.sentence.contains("four corners are in view"), failure.sentence)
        }
    }

    @Test
    fun aBlurryPhotographIsRefusedWithTheMeasuredSoftnessAndTheLimit() {
        val failure = failureOf(PhotoSpec(seed = 47, blurSigmaPx = 3.4))
        assertIs<CaptureFailure.TooBlurry>(failure)
        assertTrue(failure.measure.value > failure.measure.limit, "${failure.measure}")
        assertEquals("our heuristic", failure.measure.basis)
        assertTrue(failure.sentence.contains("our heuristic"), failure.sentence)
        assertPlainSentence(failure.sentence)
    }

    @Test
    fun glareWashingOutTheSheetIsNamedAsGlare() {
        val failure = failureOf(PhotoSpec(seed = 48, gradient = 0.3, glare = 1.0))
        assertIs<CaptureFailure.LightingTooStrong>(failure)
        assertEquals(LightingProblem.GLARE, failure.problem)
        assertTrue(failure.measure.value > failure.measure.limit)
        assertTrue(failure.sentence.startsWith("Glare"), failure.sentence)
        assertPlainSentence(failure.sentence)
    }

    @Test
    fun aHeavyShadowIsNamedAsAShadow() {
        val failure = failureOf(PhotoSpec(seed = 49, shadowDepth = 0.55, tint = Triple(236, 236, 236)))
        assertIs<CaptureFailure.LightingTooStrong>(failure)
        assertEquals(LightingProblem.SHADOW, failure.problem)
        assertTrue(failure.measure.value > failure.measure.limit, "${failure.measure}")
        assertTrue(failure.sentence.contains("shadow"), failure.sentence)
        assertPlainSentence(failure.sentence)
    }

    @Test
    fun aWashedOutPhotographWhoseBlackSquaresAreGreyIsLowContrast() {
        val failure = failureOf(PhotoSpec(seed = 50, veil = 0.6))
        assertIs<CaptureFailure.LightingTooStrong>(failure)
        assertEquals(LightingProblem.LOW_CONTRAST, failure.problem)
        assertTrue(failure.measure.value < failure.measure.limit, "${failure.measure}")
        assertPlainSentence(failure.sentence)
    }

    @Test
    fun aSheetSeenAlmostEdgeOnIsTooExtremeAPerspective() {
        val failure = failureOf(PhotoSpec(seed = 51, tiltDegrees = 48.0, tiltAxisDegrees = 0.0, fill = 0.72))
        assertIs<CaptureFailure.PerspectiveTooExtreme>(failure)
        assertTrue(failure.measure.value > failure.measure.limit, "${failure.measure}")
        assertPlainSentence(failure.sentence)
    }

    @Test
    fun withTheGatesOffTheSamePhotographIsReadAndTheMeasurementsAreStillReported() {
        val blurry = photo(PhotoSpec(seed = 47, blurSigmaPx = 3.4))
        val result = SheetCapture.read(blurry.gray, CaptureOptions(enforceQualityGates = false))
        assertIs<CaptureResult.Success>(result)
        val quality = result.sheet.quality
        assertTrue(quality.edgeWidth.value > quality.edgeWidth.limit, "the blur is still measured: ${quality.edgeWidth}")
        assertEquals(24, result.sheet.cells.size)
    }

    @Test
    fun aGoodPhotographPassesEveryGateAndReportsEveryMeasurementWithItsBasis() {
        val result = SheetCapture.read(photo(PhotoSpec(seed = 52, tiltDegrees = 10.0, rollDegrees = 3.0, fill = 0.8)).gray)
        assertIs<CaptureResult.Success>(result)
        val q = result.sheet.quality
        for (measurement in listOf(q.edgeWidth, q.perspective, q.glare, q.shadow, q.markContrast)) {
            assertEquals("our heuristic", measurement.basis)
            assertNotNull(measurement.unit)
        }
        assertTrue(q.edgeWidth.value <= q.edgeWidth.limit)
        assertTrue(q.perspective.value <= q.perspective.limit)
        assertTrue(q.glare.value <= q.glare.limit)
        assertTrue(q.shadow.value <= q.shadow.limit)
        assertTrue(q.markContrast.value >= q.markContrast.limit)
    }
}
