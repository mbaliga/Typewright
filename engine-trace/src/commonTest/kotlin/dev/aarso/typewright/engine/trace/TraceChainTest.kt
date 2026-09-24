package dev.aarso.typewright.engine.trace

import dev.aarso.typewright.core.geometry.CurveFormat
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * P3-core's item 7 integration test: the full pure chain (raster -> adaptive threshold ->
 * despeckle -> hole fill -> marching-squares contour extraction -> `core-geometry`'s
 * `fitClosedContourToCubics`) against a known shape (a circle) with synthetic noise (a few speckle
 * pixels, a small hole), confirming the final fitted contour is close to the true shape *and*
 * running the same shape a second time with despeckle/hole-fill effectively disabled (an honest
 * ablation -- both passes call the exact same [traceGrayscaleToContours], only the thresholds
 * differ), to measure what those two stages actually bought rather than assert it.
 */
class TraceChainTest {
    companion object {
        private const val WIDTH = 60
        private const val HEIGHT = 60
        private const val CENTER_X = 30.0
        private const val CENTER_Y = 30.0

        // Comfortably under adaptiveThreshold's default windowRadius (15): AdaptiveThreshold.kt's
        // own KDoc documents, honestly, that a solid filled shape wider than about 2*windowRadius
        // defeats plain local-mean thresholding in its own interior (every pixel's window is then
        // entirely ink, so nothing is ever "darker than its own local mean"). A real hand-drawn
        // letter stroke is thin, not a large filled disc, so this is the representative case.
        private const val RADIUS = 12.0

        // Three isolated single-pixel specks, far from the disc and from each other.
        private val SPECKLES = listOf(3 to 3, 56 to 56, 56 to 3)

        // A small 2x2 hole punched well inside the disc (near, but not on, its centre).
        private val HOLE = listOf(28 to 30, 29 to 30, 28 to 31, 29 to 31)

        private fun noisyDiscRaster(): GrayscaleRaster =
            rasterizeDisc(WIDTH, HEIGHT, CENTER_X, CENTER_Y, RADIUS)
                .withInkAt(SPECKLES)
                .withHoleAt(HOLE)
                .toGrayscale()
    }

    @Test
    fun theCleanedChainFindsExactlyOneContourCloseToTheTrueCircle() {
        val raster = noisyDiscRaster()
        val result = traceGrayscaleToContours(raster)

        assertEquals(1, result.contours.size, "despeckle and hole-fill should leave exactly the disc's own boundary")
        assertEquals(1, result.fittedContours.size)

        for ((x, y) in SPECKLES) assertTrue(!result.despeckled[x, y], "a speckle pixel should not survive into the despeckled raster")
        for ((x, y) in HOLE) {
            assertTrue(
                result.holeFilled[x, y],
                "a small enclosed hole pixel should be filled by the time hole-fill has run",
            )
        }

        val onCurvePoints =
            result.fittedContours
                .single()
                .points
                .count { it.onCurve }
        val deviations =
            result.densePolylines
                .single()
                .map { kotlin.math.abs(hypot(it.x - CENTER_X, it.y - CENTER_Y) - RADIUS) }
        val maxDeviation = deviations.max()

        println(
            "TraceChain (cleaned): rawContourPoints=${result.densePolylines.single().size} fittedOnCurvePoints=$onCurvePoints " +
                "maxDeviationFromTrueCircle=$maxDeviation",
        )
        assertTrue(maxDeviation < 1.5, "the recovered outline should stay close to the true circle")
    }

    @Test
    fun skippingDespeckleAndHoleFillLeavesSpuriousContoursTheCleanedChainDoesNotHave() {
        val raster = noisyDiscRaster()
        val cleaned = traceGrayscaleToContours(raster)
        // The exact same function, only with despeckle/hole-fill made into no-ops (every
        // component clears an area threshold of 0) -- a real ablation, not a separate code path.
        val uncleaned = traceGrayscaleToContours(raster, despeckleMinArea = 0, holeFillMaxArea = 0)

        println(
            "TraceChain ablation: contours with cleanup=${cleaned.contours.size}, without cleanup=${uncleaned.contours.size} " +
                "(expected specks=${SPECKLES.size}, holes=1, disc=1)",
        )

        assertEquals(1, cleaned.contours.size)
        // The 3 speckle dots and the 1 unfilled hole each survive as their own spurious loop
        // (and spurious fitted contour) when cleanup is skipped, on top of the disc's own.
        assertEquals(1 + SPECKLES.size + 1, uncleaned.contours.size)
        assertEquals(cleaned.contours.size, cleaned.fittedContours.size)
        assertEquals(uncleaned.contours.size, uncleaned.fittedContours.size)

        // The disc's own outline (the largest loop by far) is recovered equally well either way --
        // despeckle/hole-fill's benefit here is entirely in not inventing extra shapes that were
        // never part of the drawn letter, not in reshaping the real boundary.
        val cleanedDiscDeviation =
            cleaned.densePolylines
                .single()
                .map { kotlin.math.abs(hypot(it.x - CENTER_X, it.y - CENTER_Y) - RADIUS) }
                .max()
        val uncleanedDiscDeviation =
            uncleaned.densePolylines
                .maxBy { it.size }
                .map { kotlin.math.abs(hypot(it.x - CENTER_X, it.y - CENTER_Y) - RADIUS) }
                .max()
        println("TraceChain ablation: disc-loop max deviation with cleanup=$cleanedDiscDeviation, without cleanup=$uncleanedDiscDeviation")
        assertTrue(uncleanedDiscDeviation < 1.5)
    }

    @Test
    fun traceBinaryToContoursIsTheEntryPointTraceGrayscaleToContoursBuildsOn() {
        val raster = rasterizeRectangle(30, 30, x0 = 4, y0 = 4, rectWidth = 10, rectHeight = 10)
        val result = traceBinaryToContours(raster)

        assertEquals(1, result.fittedContours.size)
        val contour = result.fittedContours.single()
        assertEquals(CurveFormat.CUBIC, contour.format)
        assertTrue(contour.points.isNotEmpty())
    }

    @Test
    fun traceBinaryToContoursRejectsANonPositiveSubpixelScale() {
        val raster = rasterizeRectangle(10, 10, x0 = 2, y0 = 2, rectWidth = 4, rectHeight = 4)
        assertFailsWith<IllegalArgumentException> { traceBinaryToContours(raster, subpixelScale = 0) }
    }

    @Test
    fun defaultTraceFitParametersScalesPixelToleranceByTheSubpixelScale() {
        val params = defaultTraceFitParameters(subpixelScale = 10)
        assertEquals(DEFAULT_TRACE_FIT_ERROR_TOLERANCE_PIXELS * 10, params.errorTolerance)
    }
}
