// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [LensRenderPlan.kt]'s pixel-space math: the font-units-to-pixels framing
 * (ascender/descender span, advance-width centring), the baseline/x-height guide-line positions,
 * and every simultaneous label's dot/anchor placement -- on synthetic glyphs, no real font needed.
 * desktopTest, not commonTest: [buildLensRenderPlan] calls [Glyph.toComposePath] internally (to
 * size the diagram to the glyph's own outline), which constructs a real
 * `androidx.compose.ui.graphics.Path` -- fine on desktop's Skia-backed `Path`, but Android's host
 * (non-instrumented) unit tests wrap the real `android.graphics.Path`, whose native methods throw
 * ("Method moveTo in android.graphics.Path not mocked") with no Robolectric shadow set up, which
 * this module does not have (the same reasoning as [com.asoc.typewright.ui.GeometryInteropPathTest],
 * itself moved here for the same reason).
 */
class LensRenderPlanTest {
    private val width = 400f
    private val height = 500f

    @Test
    fun missingHeroGlyphYieldsAnEmptyPlan() {
        val glyphSet =
            AnatomyLensGlyphSet(
                unitsPerEm = 1000,
                glyphs = emptyMap(),
                ascender = 800,
                descender = -200,
                osXHeight = null,
                osCapHeight = null,
            )
        val plan = buildLensRenderPlan(glyphSet, AnatomyTerm.STEM, width, height)
        assertNull(plan.heroChar)
        assertNull(plan.glyphPath)
        assertNull(plan.baselineYPx)
        assertTrue(plan.labels.isEmpty())
    }

    @Test
    fun baselineAndXHeightLinesSitWhereTheRealAscenderDescenderAndXHeightSay() {
        val glyphSet = glyphSetFor('n' to rectGlyph("n", 400, 700), 'x' to rectGlyph("x", 420, 500))
        val marginFraction = 0.10f
        val plan = buildLensRenderPlan(glyphSet, AnatomyTerm.STEM, width, height, marginFraction = marginFraction)

        val ascender = 800.0
        val descender = -200.0
        val spanUnits = ascender - descender
        val usableHeightPx = height * (1f - 2f * marginFraction)
        val scale = usableHeightPx / spanUnits
        val marginPx = height * marginFraction

        val expectedBaselineY = (marginPx + ascender * scale).toFloat()
        assertEquals(expectedBaselineY, assertNotNull(plan.baselineYPx), 0.01f)

        // x's own real ink height (500, its own rectangle) drives the x-height guide line, via
        // the same AnatomyTerm.X_HEIGHT entry the definitions list itself would show.
        val expectedXHeightY = (marginPx + (ascender - 500.0) * scale).toFloat()
        assertEquals(expectedXHeightY, assertNotNull(plan.xHeightYPx), 0.01f)
    }

    @Test
    fun stemAloneProducesExactlyOneLabelAtItsOwnDocumentedFraction() {
        val glyphSet = glyphSetFor('n' to rectGlyph("n", 400, 700))
        val plan = buildLensRenderPlan(glyphSet, AnatomyTerm.STEM, width, height, marginFraction = 0.10f)

        assertEquals('n', plan.heroChar)
        assertNotNull(plan.glyphPath)
        assertEquals(1, plan.labels.size)
        val label = plan.labels.single()
        assertEquals(AnatomyTerm.STEM, label.term)
        assertTrue(label.selected)

        // Cross-check the dot's pixel position against the same transform math, independently
        // derived here (not by calling buildLensRenderPlan's own private toPx again).
        val ascender = 800.0
        val spanUnits = 800.0 - (-200.0)
        val usableHeightPx = height * 0.80f
        val scale = usableHeightPx / spanUnits
        val marginPx = height * 0.10f
        val centerX = 400.0 / 2.0
        val targetFontX = 0.14 * 400.0
        val targetFontY = 0.32 * 700.0
        val expectedX = (width / 2.0 + (targetFontX - centerX) * scale).toFloat()
        val expectedY = (marginPx + (ascender - targetFontY) * scale).toFloat()
        assertEquals(expectedX, label.dotPx.x, 0.05f)
        assertEquals(expectedY, label.dotPx.y, 0.05f)
    }

    @Test
    fun theOGroupProducesFourDistinctSimultaneousLabelsAllSelectedFlagCorrect() {
        val glyphSet = glyphSetFor('o' to rectGlyph("o", 480, 500))
        val plan = buildLensRenderPlan(glyphSet, AnatomyTerm.BOWL, width, height)

        assertEquals('o', plan.heroChar)
        assertEquals(
            setOf(AnatomyTerm.BOWL, AnatomyTerm.COUNTER, AnatomyTerm.CONTRAST, AnatomyTerm.ROUNDNESS),
            plan.labels.map { it.term }.toSet(),
        )
        val selected = plan.labels.filter { it.selected }
        assertEquals(listOf(AnatomyTerm.BOWL), selected.map { it.term })

        // No two labels should land on the exact same anchor point (they would visually collide).
        val anchors = plan.labels.map { it.labelAnchorPx }
        assertEquals(anchors.size, anchors.toSet().size, "expected 4 distinct label anchor points, got $anchors")
    }

    @Test
    fun labelOffsetScalesWithTheDiagramsOwnSizeNotAFixedPixelAmount() {
        // A doubled canvas (same aspect ratio, e.g. a wider container at the same density) should
        // place the glyph, and each label's own offset from its dot, at exactly double the pixel
        // distance -- proof the offset is a *fraction* of widthPx/heightPx, not a magic constant
        // that would leave a label too close to (or too far from) the glyph on a differently sized
        // real container (a phone's narrow column versus a wider desktop pane).
        val glyphSet = glyphSetFor('n' to rectGlyph("n", 400, 700))
        val plan1 = buildLensRenderPlan(glyphSet, AnatomyTerm.STEM, width, height)
        val plan2 = buildLensRenderPlan(glyphSet, AnatomyTerm.STEM, width * 2f, height * 2f)

        val dot1 = plan1.labels.single().dotPx
        val dot2 = plan2.labels.single().dotPx
        assertEquals(dot1.x * 2f, dot2.x, 0.05f, "the dot's own position should scale with the canvas too")
        assertEquals(dot1.y * 2f, dot2.y, 0.05f)

        val offset1 = plan1.labels.single().labelAnchorPx - dot1
        val offset2 = plan2.labels.single().labelAnchorPx - dot2
        assertEquals(offset1.x * 2f, offset2.x, 0.05f, "the label's own offset from the dot should scale with the canvas size")
        assertEquals(offset1.y * 2f, offset2.y, 0.05f)
    }

    @Test
    fun labelAnchorsStayOnCanvasEvenForAnUnusuallyNarrowDiagram() {
        // The 'o' group's own left/right offset fractions (bowl -0.30, roundness +0.22) would push
        // an anchor off a narrow canvas without clampToCanvas's own safety net -- a real, if
        // extreme, shape this tab could be laid out at (e.g. a very narrow phone column).
        val glyphSet = glyphSetFor('o' to rectGlyph("o", 480, 500))
        val plan = buildLensRenderPlan(glyphSet, AnatomyTerm.BOWL, widthPx = 90f, heightPx = 500f)

        assertTrue(plan.labels.isNotEmpty())
        for (label in plan.labels) {
            assertTrue(
                label.labelAnchorPx.x in 0f..90f,
                "${label.term}'s label anchor x ${label.labelAnchorPx.x} should stay on a 90px-wide canvas",
            )
            assertTrue(label.labelAnchorPx.y in 0f..500f, "${label.term}'s label anchor y ${label.labelAnchorPx.y} should stay on-canvas")
        }
    }

    // ---- synthetic glyphs ----

    private fun rectangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Contour {
        val pts = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
        return Contour(pts.map { (x, y) -> ContourPoint(Point(x, y), onCurve = true) }, CurveFormat.QUADRATIC)
    }

    private fun rectGlyph(
        name: String,
        width: Int,
        height: Int,
    ): Glyph = Glyph(name, width, listOf(rectangle(0, 0, width, height)))

    private fun glyphSetFor(vararg glyphs: Pair<Char, Glyph>): AnatomyLensGlyphSet =
        AnatomyLensGlyphSet(
            unitsPerEm = 1000,
            glyphs = glyphs.toMap(),
            ascender = 800,
            descender = -200,
            osXHeight = null,
            osCapHeight = null,
        )
}
