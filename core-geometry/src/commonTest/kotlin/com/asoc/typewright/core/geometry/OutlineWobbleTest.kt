// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The parts of the outline fitter that cope with a boundary traced from a photograph: how rough an edge is, how it is smoothed without
 * losing its corners, how far the finished outline is from the boundary, and the allowances (wobble, softness) the fit makes. Every
 * case has an answer worked out from the shape, not read back from the code.
 */
class OutlineWobbleTest {
    private fun rectangle(
        width: Double,
        height: Double,
        step: Double = 1.0,
    ): List<Vec2> {
        val corners = listOf(Vec2(0.0, 0.0), Vec2(width, 0.0), Vec2(width, height), Vec2(0.0, height))
        val out = mutableListOf<Vec2>()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            val n = max(1, ((b - a).length() / step).toInt())
            for (k in 0 until n) out += a + (b - a) * (k.toDouble() / n)
        }
        return out
    }

    /** A deterministic generator of roughly normal numbers (sum of twelve uniforms), so noisy cases do not change between runs. */
    private class Noise(
        seed: Long,
    ) {
        private var state = seed

        private fun uniform(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return ((state ushr 11) and 0x1FFFFFFFFFFFFFL) / 9007199254740992.0
        }

        fun normal(): Double = (0 until 12).sumOf { uniform() } - 6.0
    }

    /**
     * [boundary] with the edge grained the way a photograph's is: a normal random offset of standard deviation [amplitude] at knots
     * [knot] points apart (units, for a boundary one unit a point), joined linearly, applied along the outward direction. White noise at the
     * point spacing would lengthen the path more than a real edge's grain does.
     */
    private fun roughened(
        boundary: List<Vec2>,
        amplitude: Double,
        seed: Long,
        knot: Int = 6,
    ): List<Vec2> {
        val noise = Noise(seed)
        val n = boundary.size
        val knots = DoubleArray(n / knot + 2) { amplitude * noise.normal() }
        return List(n) { i ->
            val tangent = boundary[(i + 1) % n] - boundary[(i - 1 + n) % n]
            val normal = Vec2(tangent.y, -tangent.x) * (1.0 / tangent.length())
            val u = i.toDouble() / knot
            val k = u.toInt()
            val offset = knots[k] * (1.0 - (u - k)) + knots[k + 1] * (u - k)
            boundary[i] + normal * offset
        }
    }

    // ---- measureEdgeRoughness --------------------------------------------------------------

    @Test
    fun aPerfectRectangleHasNoRoughness() {
        val samples = resampleClosed(rectangle(600.0, 200.0), 2.0)
        assertTrue(measureEdgeRoughness(samples) < 0.2, "a clean render reads a fraction of a unit: ${measureEdgeRoughness(samples)}")
    }

    @Test
    fun aSideWavingByAKnownAmountReadsThatAmountOfWobble() {
        // A sine of amplitude a and a 40-unit period on the long sides: its root mean square is a / sqrt(2).
        for (amplitude in listOf(1.0, 2.0, 3.0)) {
            val base = rectangle(800.0, 300.0)
            val waved =
                base.map { p ->
                    val onLongSide = p.y == 0.0 || p.y == 300.0
                    if (onLongSide) Vec2(p.x, p.y + amplitude * sin(2.0 * PI * p.x / 40.0)) else p
                }
            val reading = measureEdgeRoughness(resampleClosed(waved, 2.0))
            val expected = amplitude / sqrt(2.0)
            assertEquals(expected, reading, 0.25 * expected + 0.05, "amplitude $amplitude")
        }
    }

    @Test
    fun roughnessGrowsWithTheWobbleAndAnEdgelessCircleReadsZero() {
        val base = rectangle(800.0, 300.0)
        val small = measureEdgeRoughness(resampleClosed(roughened(base, 0.4, 1), 2.0))
        val large = measureEdgeRoughness(resampleClosed(roughened(base, 1.2, 1), 2.0))
        assertTrue(large > 2.0 * small, "$small then $large")
        val circle = List(720) { Vec2(300.0 * cos(2.0 * PI * it / 720.0), 300.0 * sin(2.0 * PI * it / 720.0)) }
        assertEquals(0.0, measureEdgeRoughness(resampleClosed(circle, 2.0)), 1e-9)
    }

    @Test
    fun tooFewSamplesReadZeroRoughness() {
        assertEquals(0.0, measureEdgeRoughness(List(15) { Vec2(it.toDouble(), 0.0) }))
        assertEquals(0.0, measureEdgeRoughness(emptyList()))
    }

    // ---- estimateBoundaryNoise -------------------------------------------------------------

    @Test
    fun theNoiseOfACleanBoundaryIsNearZeroAndOfARoughOneIsItsStandardDeviation() {
        val clean = resampleClosed(rectangle(600.0, 300.0), 2.0)
        assertTrue(estimateBoundaryNoise(clean) < 0.15, "${estimateBoundaryNoise(clean)}")
        // Grain at knots six units apart: the part of it finer than the 10-unit scale is what the estimate sees. That is a fixed share of the
        // grain, so the reading is proportional to its amplitude, and a share well under all of it.
        val one = estimateBoundaryNoise(roughened(clean, 1.0, 7))
        val three = estimateBoundaryNoise(roughened(clean, 3.0, 7))
        assertTrue(one in 0.2..1.0, "$one")
        assertEquals(3.0, three / one, 0.5)
        assertTrue(three in 0.6..3.0, "$three")
    }

    @Test
    fun theNoiseOfATinyBoundaryIsZero() {
        assertEquals(0.0, estimateBoundaryNoise(List(15) { Vec2(it.toDouble(), (it % 2).toDouble()) }))
    }

    // ---- detectOutlineCornersDenoised ------------------------------------------------------

    @Test
    fun aCleanBoundaryIsLeftExactlyAsPlainCornerDetectionLeavesIt() {
        val boundary = rectangle(500.0, 250.0)
        val plain = detectOutlineCorners(boundary)
        val denoised = detectOutlineCornersDenoised(boundary)
        assertEquals(0.0, denoised.smoothingUnits)
        assertEquals(plain.cornerIndices, denoised.corners.cornerIndices)
        assertEquals(plain.samples, denoised.corners.samples)
        assertEquals(4, denoised.corners.cornerIndices.size)
    }

    @Test
    fun aRoughRectangleKeepsItsFourCornersWhereAPlainReadingFindsSpuriousOnes() {
        val boundary = roughened(rectangle(700.0, 300.0), 3.5, 3)
        val plain = detectOutlineCorners(boundary)
        val denoised = detectOutlineCornersDenoised(boundary)
        assertTrue(plain.cornerIndices.size > 4, "plain detection is fooled by the grain: ${plain.cornerIndices.size} corners")
        assertEquals(4, denoised.corners.cornerIndices.size)
        assertTrue(denoised.smoothingUnits > 0.0 && denoised.smoothingUnits <= MAX_SMOOTHING_UNITS)
        assertTrue(denoised.noise > NOISE_FLOOR_UNITS)
        // The corners are where the rectangle's are.
        val expected = listOf(Vec2(0.0, 0.0), Vec2(700.0, 0.0), Vec2(700.0, 300.0), Vec2(0.0, 300.0))
        for (c in denoised.corners.cornerIndices) {
            val at = denoised.corners.samples[c]
            assertTrue(expected.any { (it - at).length() < 25.0 }, "a corner at $at")
        }
        // And the sides between them are straighter than the grain they were traced from.
        val roughSide = roughened(rectangle(700.0, 300.0), 3.5, 3).filter { it.y < 5.0 && it.x in 100.0..600.0 }
        val smoothSide = denoised.corners.samples.filter { it.y < 5.0 && it.x in 100.0..600.0 }

        fun spread(points: List<Vec2>) = points.map { it.y }.let { ys -> ys.max() - ys.min() }
        assertTrue(spread(smoothSide) < 0.5 * spread(roughSide), "${spread(smoothSide)} against ${spread(roughSide)}")
    }

    @Test
    fun aRoughRectangleFitsToFourStraightSidesOnceTheEdgeIsDenoisedAndTheWobbleAllowedFor() {
        val boundary = roughened(rectangle(700.0, 300.0), 3.5, 3)
        val denoised = detectOutlineCornersDenoised(boundary)
        val fit = fitOutline(denoised.corners, OutlineFitParameters(fitTolerance = 2.0, wobble = denoised.noise))
        assertEquals(4, fit.contour.points.size / 3)
        assertEquals(4, fit.lineCount)
        assertEquals(0, fit.curveCount)
    }

    // ---- outlineDeviation ------------------------------------------------------------------

    private fun squareContour(side: Int): Contour =
        polygonContour(listOf(Point(0, 0), Point(side, 0), Point(side, side), Point(0, side)), CurveFormat.CUBIC)

    @Test
    fun anOutlineSittingOnItsBoundaryDeviatesByNothing() {
        val boundary = listOf(rectangle(100.0, 100.0))
        assertEquals(0.0, outlineDeviation(boundary, listOf(squareContour(100))), 1e-9)
    }

    @Test
    fun anOutlineShiftedBy3UnitsDeviatesBy3() {
        val boundary = listOf(rectangle(100.0, 100.0).map { it + Vec2(3.0, 0.0) })
        assertEquals(3.0, outlineDeviation(boundary, listOf(squareContour(100))), 1e-6)
    }

    @Test
    fun deviationIsMeasuredBothWaysSoAMissingOrAnAddedStrokeBothShowUp() {
        // The boundary has a 10-unit tab the outline lacks, and the outline (a square of 120) has a side the boundary (100) lacks.
        val tab =
            listOf(
                Vec2(0.0, 0.0),
                Vec2(100.0, 0.0),
                Vec2(100.0, 40.0),
                Vec2(110.0, 40.0),
                Vec2(110.0, 60.0),
                Vec2(100.0, 60.0),
                Vec2(100.0, 100.0),
                Vec2(0.0, 100.0),
            )
        val dense = tab.indices.flatMap { i -> rectangleSegment(tab[i], tab[(i + 1) % tab.size]) }
        assertEquals(10.0, outlineDeviation(listOf(dense), listOf(squareContour(100))), 1e-6)
        // The farthest point of the larger square from the smaller is its far corner, 20 units out along both axes.
        assertEquals(20.0 * sqrt(2.0), outlineDeviation(listOf(rectangle(100.0, 100.0)), listOf(squareContour(120))), 1e-6)
    }

    private fun rectangleSegment(
        a: Vec2,
        b: Vec2,
    ): List<Vec2> = (0 until 20).map { a + (b - a) * (it / 20.0) }

    @Test
    fun deviationWithNothingToCompareIsZero() {
        assertEquals(0.0, outlineDeviation(emptyList(), listOf(squareContour(10))))
        assertEquals(0.0, outlineDeviation(listOf(rectangle(10.0, 10.0)), emptyList()))
    }

    // ---- smoothing -------------------------------------------------------------------------

    @Test
    fun smoothingAClosedBoundaryLeavesAStraightRunAloneAndReducesNoise() {
        val line = List(200) { Vec2(it * 2.0, 5.0) }
        val smoothedLine = gaussianClosed(line, 6.0, 2.0)
        // Away from the wrap-around the straight run does not move.
        for (i in 20 until 180) assertEquals(5.0, smoothedLine[i].y, 1e-9)
        val noise = Noise(11)
        val rough = List(200) { Vec2(it * 2.0, 5.0 + noise.normal()) }

        fun variance(points: List<Vec2>) =
            points.subList(20, 180).map { it.y }.let { ys ->
                ys
                    .map {
                        (it - ys.average()) *
                            (it - ys.average())
                    }.average()
            }
        assertTrue(variance(gaussianClosed(rough, 6.0, 2.0)) < 0.2 * variance(rough))
    }

    @Test
    fun smoothingAnOpenRunHoldsBothEndsAndDoesNotCurlAStraightRunIn() {
        val line = List(60) { Vec2(it * 2.0, it * 1.0) }
        val smoothed = gaussianOpen(line, 6.0, 2.0)
        assertEquals(line.first(), smoothed.first())
        assertEquals(line.last(), smoothed.last())
        for (i in line.indices) assertTrue((smoothed[i] - line[i]).length() < 1e-9, "point $i moved to ${smoothed[i]}")
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(1.0, 1.0)), gaussianOpen(listOf(Vec2(0.0, 0.0), Vec2(1.0, 1.0)), 6.0, 2.0))
    }

    // ---- the allowances the fit makes ------------------------------------------------------

    /** A rectangle whose corners are rounded with radius [radius] (a quarter circle each), as a dense polyline. */
    private fun roundedRectangle(
        width: Double,
        height: Double,
        radius: Double,
    ): List<Vec2> {
        val out = mutableListOf<Vec2>()
        val centres =
            listOf(Vec2(width - radius, radius), Vec2(width - radius, height - radius), Vec2(radius, height - radius), Vec2(radius, radius))
        val sides = 40
        for (c in centres.indices) {
            for (k in 0..sides) {
                val angle = (-90.0 + 90.0 * c + 90.0 * k / sides) * PI / 180.0
                out += centres[c] + Vec2(cos(angle), sin(angle)) * radius
            }
        }
        return out
    }

    @Test
    fun aRoundingNoBiggerThanBlurMakesIsSquaredUpAndADrawnOneIsKept() {
        // A rounding of radius 38 turns only 30 degrees over the corner finder's look distance, so it is not found as a corner; with an edge
        // 14 units soft it is what a sharp corner looks like (a blurred corner strays 0.4 of its radius from the sharp one), and radius 70 is not.
        val blurred = roundedRectangle(500.0, 300.0, 38.0)
        val kept = roundedRectangle(500.0, 300.0, 70.0)
        val detectedBlurred = detectOutlineCorners(blurred)
        val detectedKept = detectOutlineCorners(kept)
        assertEquals(emptyList(), detectedBlurred.cornerIndices, "the roundings are not corners to the corner finder")
        val squared = fitOutline(detectedBlurred, OutlineFitParameters(softness = 14.0))
        val asTraced = fitOutline(detectedBlurred, OutlineFitParameters())
        val drawn = fitOutline(detectedKept, OutlineFitParameters(softness = 14.0))
        assertEquals(4, squared.squaredCorners)
        assertEquals(0, squared.curveCount, "four sharp corners joined by four lines")
        assertEquals(4, squared.contour.points.size / 3)
        assertEquals(0, asTraced.squaredCorners)
        assertEquals(4, asTraced.curveCount, "without the allowance the roundings stay curves")
        assertEquals(0, drawn.squaredCorners, "a rounding of radius 70 is a designed one, however soft the edge")
        assertEquals(4, drawn.curveCount)
        // Squared up, the corners are where the sides meet, not on the blurred arcs: the box is the sharp rectangle's.
        val bounds = Glyph("g", 0, listOf(squared.contour)).outlineBounds()!!
        assertEquals(listOf(0, 0, 500, 300), listOf(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax))
    }

    @Test
    fun aSideTheCornerFinderCutInTwoIsOneSide() {
        val samples = resampleClosed(rectangle(600.0, 200.0), 2.0)
        val real = detectOutlineCorners(samples).cornerIndices
        // A false corner in the middle of each long side, as a wobble can leave.
        val longSides =
            samples.indices.filter { i ->
                (samples[i].y == 0.0 && abs(samples[i].x - 300.0) < 1.0) ||
                    (samples[i].y == 200.0 && abs(samples[i].x - 300.0) < 1.0)
            }
        assertEquals(2, longSides.size)
        val fit = fitOutline(OutlineCorners(samples, (real + longSides).sorted()), OutlineFitParameters())
        assertEquals(4, fit.contour.points.size / 3, "the two halves of each long side are joined")
        assertEquals(4, fit.lineCount)
    }

    @Test
    fun aLongSideMayDriftByAFractionOfItsLengthAndStillBeOneLine() {
        // A 600-unit side that leans 4 units over its length (0.7%): straight to the eye, but over the 1.2-unit line tolerance.
        val lean = rectangle(600.0, 200.0).map { p -> if (p.y == 0.0) Vec2(p.x, 4.0 * p.x / 600.0) else p }
        val fit = fitClosedOutline(lean, OutlineFitParameters())
        assertEquals(4, fit.lineCount)
        assertEquals(0, fit.curveCount)
    }

    @Test
    fun theWobbleWidensOnlyTheTestsForAStraightLineAndSoLeavesTheOtherParametersAlone() {
        val plain = OutlineFitParameters(fitTolerance = 2.0)
        val wobbly = OutlineFitParameters(fitTolerance = 2.0, wobble = 1.0)
        assertEquals(1.2, plain.lineTolerance, 1e-9)
        assertEquals(LINE_TOLERANCE_WOBBLES, wobbly.lineTolerance, 1e-9)
        assertTrue(wobbly.bowTolerance > plain.bowTolerance)
        assertEquals(2.0, plain.curveTolerance, 1e-9)
        assertEquals(2.0 + CURVE_TOLERANCE_WOBBLES, wobbly.curveTolerance, 1e-9)
        assertEquals(plain.fitTolerance, wobbly.fitTolerance)
    }

    @Test
    fun negativeWobbleOrSoftnessIsRefused() {
        val refused = runCatching { OutlineFitParameters(wobble = -1.0) }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException)
        assertTrue(runCatching { OutlineFitParameters(softness = -0.1) }.exceptionOrNull() is IllegalArgumentException)
    }
}
