package dev.aarso.typewright.qa

import dev.aarso.typewright.core.font.ufo.UfoFontInfo
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

private fun quadContour(points: List<ContourPoint>) = Contour(points, CurveFormat.QUADRATIC)

class OvershootCheckTest {
    @Test
    fun aTwoContourGlyphWithAFlatTopAndAPointedBottomReportsBoth() {
        // contour 0: bottom is a single point at baseline (not flat); top is two points at
        // x-height (flat). contour 1: an unrelated dummy contour, present only to make this a
        // "round" (2+ contour) glyph without depending on any curve.
        val shape = quadContour(listOf(on(5, 0), on(0, 500), on(10, 500)))
        val dummy = quadContour(listOf(on(3, 250), on(4, 251), on(5, 250)))
        val glyph = Glyph("o", 500, listOf(shape, dummy))

        val findings = checkOvershootPresence(glyph, UfoFontInfo(xHeight = 500))
        assertEquals(2, findings.size)

        val top = findings.single { it.metricLine == "x-height" }
        assertTrue(top.isFlat)
        assertTrue(top.message.contains("flat"))

        val bottom = findings.single { it.metricLine == "baseline" }
        assertTrue(!bottom.isFlat)
        assertTrue(bottom.message.contains("no overshoot"))
    }

    @Test
    fun aSingleCurvedContourCountsAsRoundEvenAlone() {
        // Same fixture as ExtremaTest/CurveSegmentTest: a curve whose extremum is at y=5,
        // interior, with both on-curve anchors sitting on the baseline.
        val glyph = Glyph("c", 500, listOf(quadContour(listOf(on(0, 0), off(10, 10), on(20, 0)))))
        val findings = checkOvershootPresence(glyph, UfoFontInfo())
        val bottom = findings.single { it.metricLine == "baseline" }
        assertTrue(bottom.isFlat) // both anchors sit at y=0
    }

    @Test
    fun aSingleStraightContourIsNotRoundAndProducesNoFindingsEvenOnAMetricLine() {
        val glyph = Glyph("l", 500, listOf(quadContour(listOf(on(0, 0), on(10, 0), on(10, 20), on(0, 20)))))
        assertEquals(emptyList(), checkOvershootPresence(glyph, UfoFontInfo()))
    }

    @Test
    fun realOvershootBeyondTheLineIsNotFlagged() {
        val shape = quadContour(listOf(on(0, -5), on(0, 505), on(10, 505)))
        val dummy = quadContour(listOf(on(3, 250), on(4, 251), on(5, 250)))
        val glyph = Glyph("o", 500, listOf(shape, dummy))
        assertEquals(emptyList(), checkOvershootPresence(glyph, UfoFontInfo(xHeight = 500)))
    }

    @Test
    fun aContourlessGlyphProducesNoFindings() {
        assertEquals(emptyList(), checkOvershootPresence(Glyph("space", 300, emptyList()), UfoFontInfo()))
    }
}
