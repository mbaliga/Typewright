package dev.aarso.typewright.qa

import dev.aarso.typewright.core.font.ufo.UfoFontInfo
import dev.aarso.typewright.core.font.ufo.UfoProject
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.qa.corpus.FamilyEntry
import dev.aarso.typewright.qa.corpus.GlyphDist
import dev.aarso.typewright.qa.corpus.NodeEconomyCorpus
import dev.aarso.typewright.qa.corpus.NodeEconomyPack
import dev.aarso.typewright.qa.corpus.NodeEconomyVerdict
import dev.aarso.typewright.qa.corpus.Quartiles
import dev.aarso.typewright.qa.corpus.StyleClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun fixtureCorpus(): NodeEconomyCorpus =
    NodeEconomyCorpus(
        NodeEconomyPack(
            source = "test",
            glyphs = "on",
            styles =
                mapOf(
                    "test-style" to
                        StyleClass(
                            tags = listOf("/Test"),
                            families =
                                listOf(FamilyEntry("Alpha", "alpha.ttf", "quadratic", 90.0, mapOf("o" to listOf(4, 8, 1)))),
                            dist =
                                mapOf(
                                    "o" to
                                        GlyphDist(
                                            on = Quartiles(min = 2.0, q1 = 3.0, med = 4.0, q3 = 4.0, max = 6.0, n = 1),
                                            off = Quartiles(min = 4.0, q1 = 6.0, med = 8.0, q3 = 8.0, max = 12.0, n = 1),
                                        ),
                                ),
                        ),
                ),
        ),
    )

/** A cubic on/off/off triple contour with [onCurvePoints] anchors, each carrying two off-curve controls. */
private fun cubicContour(onCurvePoints: Int): Contour {
    val points = mutableListOf<ContourPoint>()
    for (i in 0 until onCurvePoints) {
        points += ContourPoint(Point(i, 0), true)
        points += ContourPoint(Point(i, 1), false)
        points += ContourPoint(Point(i, 2), false)
    }
    return Contour(points, CurveFormat.CUBIC)
}

private fun project(glyphs: List<Glyph>) = UfoProject(UfoFontInfo(familyName = "Test"), glyphs)

class NodeEconomyCheckTest {
    @Test
    fun aGlyphAtTheBoxMedianIsInRange() {
        // 4 on-curve anchors -> 4 on, 8 off (cubic's fixed 2-per-anchor). Box: on med=4 q3=4, off med=8 q3=8.
        val glyph = Glyph("o", 500, listOf(cubicContour(4)))
        val report = checkNodeEconomy(project(listOf(glyph)), "test-style", fixtureCorpus())

        val result = report.glyphs.single()
        assertEquals("o", result.glyphName)
        assertEquals(4, result.onCurve)
        assertEquals(8, result.offCurve)
        assertEquals(NodeEconomyVerdict.IN_RANGE, result.onCurveVerdict)
        assertEquals(NodeEconomyVerdict.IN_RANGE, result.offCurveVerdict)
        assertTrue(result.rationale.contains("'o'"))
        assertTrue(result.rationale.contains("in range"))
    }

    @Test
    fun aGlyphWellPastTheFenceIsAnOutlier() {
        // 40 on-curve anchors is far past q3=4 and the fence (q3 + max(1.5*IQR, 0.25*q3)).
        val glyph = Glyph("o", 500, listOf(cubicContour(40)))
        val report = checkNodeEconomy(project(listOf(glyph)), "test-style", fixtureCorpus())

        val result = report.glyphs.single()
        assertEquals(NodeEconomyVerdict.OUTLIER, result.onCurveVerdict)
        assertTrue(result.rationale.contains("an outlier"))
    }

    @Test
    fun aGlyphWithNoBoxInTheStyleClassReportsNullVerdictsAndSaysSo() {
        val glyph = Glyph("z", 500, listOf(cubicContour(4)))
        val report = checkNodeEconomy(project(listOf(glyph)), "test-style", fixtureCorpus())

        val result = report.glyphs.single()
        assertNull(result.onCurveBox)
        assertNull(result.onCurveVerdict)
        assertNull(result.offCurveVerdict)
        assertTrue(result.rationale.contains("no measured box"))
    }

    @Test
    fun reportCoversEveryGlyphInTheProjectInOrder() {
        val glyphs = listOf(Glyph("o", 500, listOf(cubicContour(4))), Glyph("z", 500, listOf(cubicContour(2))))
        val report = checkNodeEconomy(project(glyphs), "test-style", fixtureCorpus())
        assertEquals(listOf("o", "z"), report.glyphs.map { it.glyphName })
        assertEquals("test-style", report.styleKey)
    }
}
