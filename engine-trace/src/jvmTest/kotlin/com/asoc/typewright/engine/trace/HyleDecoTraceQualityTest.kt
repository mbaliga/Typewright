// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.core.geometry.signedArea
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The trace chain held to CLAUDE.md's fixtures, from a raster and not from the font's outline: Hyle Deco's `T`, `o`,
 * `n` and `H` are drawn into anti-aliased rasters at four resolutions (a pixel is 1, 2, 2.5 and 4 font units) and
 * run through every stage. The fitted column is T 8 on - 0 off, o 16 - 16, n 14 - 8, H 12 - 0 (the fixture counts are
 * stated on - off, with a straight side having no off-curve point, exactly as `GlifCodec` writes it).
 *
 * The source outlines are the font's own 1,763, 80, 44 and 1,252 points; nothing here depends on those numbers, only
 * the rendered pixels reach the chain.
 */
class HyleDecoTraceQualityTest {
    private data class Target(
        val char: Char,
        val on: Int,
        val off: Int,
        val contours: Int,
    )

    private val targets = listOf(Target('T', 8, 0, 1), Target('o', 16, 16, 2), Target('n', 14, 8, 1), Target('H', 12, 0, 1))
    private val resolutions = listOf(1.0, 0.5, 0.4, 0.25)
    private val metricLines = listOf(0, 500, 700)

    private fun render(
        glyph: Glyph,
        pxPerUnit: Double,
    ): Pair<GrayscaleRaster, RasterFrame> {
        val bounds = glyph.outlineBounds()!!
        val pad = 20.0
        val frame = RasterFrame(pxPerUnit, bounds.xMin - pad, bounds.yMax + pad)
        val w = ((bounds.xMax - bounds.xMin + 2 * pad) * pxPerUnit).toInt() + 1
        val h = ((bounds.yMax - bounds.yMin + 2 * pad) * pxPerUnit).toInt() + 1
        return rasterizeContours(glyph.contours, frame, w, h) to frame
    }

    @Test
    fun theFourFixturesReachTheirFittedNodeCountsFromARasterAtEveryResolution() {
        println("glyph  px/unit  contour-pts  corners  fit(on/off)  final(on/off)  bbox-diff  mismatch  deviation")
        for (target in targets) {
            for (resolution in resolutions) {
                val glyph = HyleDecoFixture.glyph(target.char)
                val (raster, frame) = render(glyph, resolution)
                val trace = traceCell(raster, frame, TraceParameters(metricLines = metricLines))
                val review = trace.review!!
                println(
                    "${target.char}      $resolution      ${trace.stage(TraceStage.CONTOUR).nodes!!.total}      " +
                        "${trace.stage(
                            TraceStage.CORNERS,
                        ).nodes!!.total}      ${trace.stage(TraceStage.FIT).nodes!!.let { "${it.onCurve}/${it.offCurve}" }}      " +
                        "${trace.finalNodes!!.let { "${it.onCurve}/${it.offCurve}" }}      ${review.boundsDifference}      " +
                        "${(review.mismatchFraction * 1000).toInt() / 10.0}%      ${review.maxDeviation}",
                )
                assertEquals(target.contours, trace.contours.size, "${target.char} at $resolution px/unit: contours")
                assertEquals(
                    NodeCount(target.on, target.off),
                    trace.finalNodes,
                    "${target.char} at $resolution px/unit: on/off points (${trace.stage(TraceStage.FIT).sentence})",
                )
            }
        }
    }

    @Test
    fun theTracedInkStaysWithinTwoPercentOfTheEmOfTheSourcesBox() {
        for (target in targets) {
            val glyph = HyleDecoFixture.glyph(target.char)
            val source = glyph.outlineBounds()!!
            val (raster, frame) = render(glyph, 0.5)
            val trace = traceCell(raster, frame, TraceParameters(metricLines = metricLines))
            val traced = Glyph("t", 0, trace.contours).outlineBounds()!!
            for ((name, a, b) in listOf(
                Triple("left", traced.xMin, source.xMin),
                Triple("right", traced.xMax, source.xMax),
                Triple("bottom", traced.yMin, source.yMin),
                Triple("top", traced.yMax, source.yMax),
            )) {
                assertTrue(abs(a - b) <= 20, "${target.char} $name: traced $a against the source's $b (limit 2% of 1000)")
            }
        }
    }

    @Test
    fun theStemFootSnapsFromOneToZeroAndTheTStemStaysOffCentre() {
        val glyph = HyleDecoFixture.glyph('T')
        val (raster, frame) = render(glyph, 0.5)
        val trace = traceCell(raster, frame, TraceParameters(metricLines = metricLines))
        val nodes =
            trace.contours
                .single()
                .points
                .filter { it.onCurve }
                .map { it.point }
        assertEquals(2, nodes.count { it.y == 0 }, "the two foot corners sit on the baseline: $nodes")
        val foot = nodes.filter { it.y == 0 }.map { it.x }
        val barLeft = nodes.minOf { it.x }
        val barRight = nodes.maxOf { it.x }
        val stemCentre = (foot.min() + foot.max()) / 2.0
        assertTrue(abs(stemCentre - (barLeft + barRight) / 2.0) > 10.0, "the stem is off-centre by design, not symmetrised")
        assertTrue(foot.max() - foot.min() in 40..48, "the stem is about 43 units wide: $foot")
    }

    @Test
    fun everyStageKeepsItsOutputAndNodeCountsNeverRiseFromContourToFit() {
        for (target in targets) {
            val (raster, frame) = render(HyleDecoFixture.glyph(target.char), 0.5)
            val trace = traceCell(raster, frame, TraceParameters(metricLines = metricLines))
            assertEquals(TraceStage.entries, trace.stages.map { it.stage })
            for (stage in trace.stages) assertTrue(stage.sentence.isNotBlank(), "${stage.stage} has a sentence")
            val contour = trace.stage(TraceStage.CONTOUR)
            assertTrue(
                contour.polylines.isNotEmpty() && contour.polylines.all { it.size > 20 },
                "the contour stage keeps the dense polygon",
            )
            assertTrue(trace.stage(TraceStage.FIT).contours.isNotEmpty(), "the fit stage keeps its contours")
            assertTrue(
                trace.stage(TraceStage.FIT).nodes!!.total <= contour.nodes!!.total,
                "${target.char}: fit ${trace.stage(TraceStage.FIT).nodes} against ${contour.nodes}",
            )
            assertTrue(trace.stage(TraceStage.CLEAN).parameters.isNotEmpty() && trace.stage(TraceStage.FIT).parameters.isNotEmpty())
        }
    }

    @Test
    fun theTSentenceSaysWhatHappenedInPlainWords() {
        val (raster, frame) = render(HyleDecoFixture.glyph('T'), 0.5)
        val trace = traceCell(raster, frame, TraceParameters(metricLines = metricLines))
        val sentence = trace.stage(TraceStage.FIT).sentence
        assertTrue("become 8 corners joined by straight lines" in sentence && "No curves, so no off-curve points." in sentence, sentence)
        assertTrue(
            trace.stage(TraceStage.CLEAN).sentence.startsWith("Found ink with a stroke about 4"),
            trace.stage(TraceStage.CLEAN).sentence,
        )
    }

    @Test
    fun theRoundGlyphSaysOvershootDoesNotApplyOnItsFlatTopAndBottom() {
        val (raster, frame) = render(HyleDecoFixture.glyph('o'), 0.5)
        val review = traceCell(raster, frame, TraceParameters(metricLines = metricLines)).review!!
        assertTrue(review.flatTop && review.flatBottom && review.hasCurves)
        val sentence = traceCell(raster, frame, TraceParameters(metricLines = metricLines)).stage(TraceStage.REVIEW).sentence
        assertTrue("Flat top and bottom: overshoot does not apply." in sentence, sentence)
    }

    @Test
    fun aBroaderSetOfHyleDecoLettersTracesWithoutLosingTheirContoursOrBoxes() {
        // Not asserted against fixture counts (none are stated): the point is that the chain neither loses nor invents shapes.
        val expectedContours =
            mapOf(
                'L' to 1,
                'I' to 1,
                'E' to 1,
                'O' to 2,
                'B' to 3,
                'l' to 1,
                'e' to 2,
                'a' to 2,
                'p' to 2,
                'd' to 2,
                'i' to 2,
                'u' to 1,
            )
        println("glyph  contours  nodes  fit-sentence")
        for ((char, contours) in expectedContours) {
            val glyph = HyleDecoFixture.glyph(char)
            val (raster, frame) = render(glyph, 0.5)
            val trace = traceCell(raster, frame, TraceParameters(metricLines = metricLines))
            println("$char  ${trace.contours.size}  ${trace.finalNodes}  ${trace.stage(TraceStage.FIT).sentence}")
            assertEquals(contours, trace.contours.size, "$char: contour count")
            val source = glyph.outlineBounds()!!
            val traced = Glyph("t", 0, trace.contours).outlineBounds()!!
            assertTrue(abs(source.xMin - traced.xMin) <= 20 && abs(source.yMax - traced.yMax) <= 20, "$char box")
            assertTrue(
                trace.review!!.mismatchFraction < 0.03,
                "$char: the outline redrawn agrees with the ink: ${trace.review!!.mismatchFraction}",
            )
        }
    }

    @Test
    fun contourDirectionsFollowTheConvention() {
        val (raster, frame) = render(HyleDecoFixture.glyph('o'), 0.5)
        val contours: List<Contour> = traceCell(raster, frame, TraceParameters(metricLines = metricLines)).contours
        assertEquals(2, contours.size)
        val signed = contours.map { it.signedArea() }
        assertTrue(signed.count { it > 0 } == 1 && signed.count { it < 0 } == 1, "outer counter-clockwise, inner clockwise: $signed")
        assertTrue(signed.maxBy { abs(it) } > 0, "the larger contour is the outer one")
    }
}
