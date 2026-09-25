// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [GlyphShape]: the "shape modulo one horizontal translation" rule law 1's enforcement depends on. Runs on the JVM and Wasm. */
class GlyphShapeTest {
    @Test
    fun identicalGlyphsGiveZero() {
        val glyph = Fixtures.glyph("A")
        assertEquals(0, GlyphShape.translationDx(glyph, glyph))
    }

    @Test
    fun aPureHorizontalShiftGivesItsDx() {
        val before = Fixtures.glyph("A")
        val after = Fixtures.glyph("A", dx = 12)
        assertEquals(12, GlyphShape.translationDx(before, after))
    }

    @Test
    fun aVerticalMoveIsNotATranslationMatch() {
        val before = Fixtures.glyph("A")
        val after =
            before.copy(
                contours =
                    before.contours.map { c ->
                        c.copy(
                            points =
                                c.points.map {
                                    it.copy(
                                        point =
                                            Point(
                                                it.point.x,
                                                it.point.y + 1,
                                            ),
                                    )
                                },
                        )
                    },
            )
        assertNull(GlyphShape.translationDx(before, after))
    }

    @Test
    fun aShapeChangeIsNotATranslationMatch() {
        val before = Fixtures.glyph("A")
        val after = Fixtures.squareContour(50, 0, 460, 700).let { before.copy(contours = listOf(it)) }
        assertNull(GlyphShape.translationDx(before, after))
    }

    @Test
    fun anchorsMustShiftBySameDxToo() {
        val before = Fixtures.glyph("A").copy(anchors = listOf(Anchor("top", Point(250, 700))))
        val consistent = before.copy(contours = Fixtures.glyph("A", dx = 5).contours, anchors = listOf(Anchor("top", Point(255, 700))))
        val inconsistent = before.copy(contours = Fixtures.glyph("A", dx = 5).contours, anchors = listOf(Anchor("top", Point(260, 700))))
        assertEquals(5, GlyphShape.translationDx(before, consistent))
        assertNull(GlyphShape.translationDx(before, inconsistent))
    }

    @Test
    fun differingAnchorCountIsNotAMatch() {
        val before = Fixtures.glyph("A")
        val after = before.copy(anchors = listOf(Anchor("top", Point(250, 700))))
        assertNull(GlyphShape.translationDx(before, after))
    }

    @Test
    fun differingContourCountIsNotAMatch() {
        val before = Fixtures.glyph("A")
        val after = before.copy(contours = before.contours + before.contours)
        assertNull(GlyphShape.translationDx(before, after))
    }

    @Test
    fun advanceWidthUnicodesGuidelinesAndNameAreNeverPartOfShape() {
        val before = Fixtures.glyph("A")
        val after = before.copy(advanceWidth = 999, unicodes = listOf(0x41), name = "Zed")
        assertEquals(0, GlyphShape.translationDx(before, after))
    }

    @Test
    fun shiftXMovesEveryContourPointAndAnchorBySameDx() {
        val glyph = Fixtures.glyph("A").copy(anchors = listOf(Anchor("top", Point(250, 700))))
        val shifted = GlyphShape.shiftX(glyph, 7)
        assertEquals(7, GlyphShape.translationDx(glyph, shifted))
        assertEquals(
            7,
            shifted.anchors
                .single()
                .point.x -
                glyph.anchors
                    .single()
                    .point.x,
        )
    }

    @Test
    fun shiftXByZeroReturnsTheSameInstance() {
        val glyph = Fixtures.glyph("A")
        val shifted = GlyphShape.shiftX(glyph, 0)
        assertEquals(glyph, shifted)
    }
}
