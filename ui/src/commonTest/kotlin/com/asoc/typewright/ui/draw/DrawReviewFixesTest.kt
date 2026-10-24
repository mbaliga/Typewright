// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.glass.NumericStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

/** A square of four straight sides in cubic form: four segments, so segment indices 0 to 3 exist and 4 does not. */
private fun square(): Glyph {
    val corners = listOf(Point(0, 0), Point(300, 0), Point(300, 400), Point(0, 400))
    val points =
        corners.flatMapIndexed { i, p ->
            listOf(ContourPoint(p, true), ContourPoint(p, false), ContourPoint(corners[(i + 1) % 4], false))
        }
    return Glyph("s", 400, listOf(Contour(points, CurveFormat.CUBIC)))
}

/** What a selection may name once an undo, a redo or an edit in another room has changed the outline under it. */
class DrawSelectionClippingTest {
    @Test
    fun aSegmentPastTheEndOfItsContourIsDropped() {
        val stale = DrawSelection.EMPTY.onlySegment(SegmentKey(0, 4), 0.5)
        assertNull(stale.clippedTo(square()).segment, "the contour has segments 0 to 3 only")
        val last = DrawSelection.EMPTY.onlySegment(SegmentKey(0, 3), 0.5)
        assertEquals(SegmentKey(0, 3), last.clippedTo(square()).segment, "the last segment that exists is kept")
    }

    @Test
    fun aSegmentOfAContourThatIsGoneIsDropped() {
        assertNull(
            DrawSelection.EMPTY
                .onlySegment(SegmentKey(2, 0), 0.5)
                .clippedTo(square())
                .segment,
        )
        assertNull(
            DrawSelection.EMPTY
                .onlySegment(SegmentKey(-1, 0), 0.5)
                .clippedTo(square())
                .segment,
        )
        assertNull(
            DrawSelection.EMPTY
                .onlySegment(SegmentKey(0, -1), 0.5)
                .clippedTo(square())
                .segment,
        )
    }

    @Test
    fun pointsPastTheEndOfTheirContourAreDroppedAndTheRestKept() {
        val selection = DrawSelection(setOf(PointRef(0, 3), PointRef(0, 12), PointRef(1, 0), PointRef(0, -1)))
        assertEquals(setOf(PointRef(0, 3)), selection.clippedTo(square()).points)
    }

    @Test
    fun aSelectionThatNeedsNoClippingComesBackAsItself() {
        val selection = DrawSelection(setOf(PointRef(0, 3))).copy(segment = SegmentKey(0, 2))
        assertSame(selection, selection.clippedTo(square()))
    }

    @Test
    fun theSubjectLineOfAStaleSelectionIsTheGlyphsNotACrash() {
        val stale = DrawSelection.EMPTY.onlySegment(SegmentKey(5, 0), 0.5)
        assertEquals(DrawSubject.glyphLine(square()), DrawSubject.describe(square(), stale))
        assertEquals(DrawSubject.glyphLine(square()), DrawSubject.headerLine(square(), stale))
    }
}

/** The note after taking control points out of a quadratic contour says what really happened. */
class QuadraticHandleRemovalTest {
    private fun quad(vararg points: ContourPoint) = Contour(points.toList(), CurveFormat.QUADRATIC)

    @Test
    fun aLoneControlBetweenTwoNodesLeavesAStraightSide() {
        val c = quad(on(0, 0), off(50, 60), on(100, 0), on(100, -100), on(0, -100))
        val removal = QuadraticHandleRemoval.of(c, setOf(1))
        assertEquals(QuadraticHandleRemoval(removed = 1, straightSides = 1, impliedGone = 0), removal)
        assertEquals("Removed 1 control point; the side is now straight.", removal.sentence())
    }

    @Test
    fun oneControlOutOfARunOfTwoKeepsTheSideCurvedAndLosesTheImpliedPoint() {
        // on, off, off, on: the point implied between the two controls goes with the one that is removed.
        val c = quad(on(0, 0), off(0, 100), off(100, 100), on(100, 0), on(50, -60))
        val removal = QuadraticHandleRemoval.of(c, setOf(1))
        assertEquals(QuadraticHandleRemoval(removed = 1, straightSides = 0, impliedGone = 1), removal)
        val sentence = removal.sentence().orEmpty()
        assertTrue("implied" in sentence && "gone" in sentence && "reshaped" in sentence, sentence)
        assertTrue("straight" !in sentence, "the side is not straight: $sentence")
    }

    @Test
    fun bothControlsOfARunOfTwoLeaveAStraightSide() {
        val c = quad(on(0, 0), off(0, 100), off(100, 100), on(100, 0), on(50, -60))
        assertEquals(QuadraticHandleRemoval(removed = 2, straightSides = 1, impliedGone = 0), QuadraticHandleRemoval.of(c, setOf(1, 2)))
    }

    @Test
    fun theMiddleOfARunOfThreeLeavesTwoControlsAndOneImpliedPointLess() {
        val c = quad(on(0, 0), off(0, 100), off(50, 140), off(100, 100), on(100, 0), on(50, -60))
        val removal = QuadraticHandleRemoval.of(c, setOf(2))
        assertEquals(QuadraticHandleRemoval(removed = 1, straightSides = 0, impliedGone = 1), removal)
    }

    @Test
    fun runsAreFoundAcrossTheEndOfThePointList() {
        // The run of two wraps round the end of the list: indices 4 and 0.
        val c = quad(off(0, 100), on(50, 100), on(100, 0), on(50, -60), off(100, 60))
        val removal = QuadraticHandleRemoval.of(c, setOf(0))
        assertEquals(1, removal.impliedGone, "removing one of two controls in the wrapped run reshapes it")
    }

    @Test
    fun separateSidesAreCountedSeparatelyAndPluralsAgree() {
        val c = quad(on(0, 0), off(50, 60), on(100, 0), off(150, 60), on(200, 0), on(100, -100))
        val removal = QuadraticHandleRemoval.of(c, setOf(1, 3))
        assertEquals(QuadraticHandleRemoval(removed = 2, straightSides = 2, impliedGone = 0), removal)
        assertEquals("Removed 2 control points; the sides are now straight.", removal.sentence())
    }

    @Test
    fun nothingRemovedSaysNothing() {
        val c = quad(on(0, 0), off(50, 60), on(100, 0), on(100, -100))
        assertNull(QuadraticHandleRemoval.of(c, setOf(0, 2, 99)).sentence(), "on-curve points and missing indices are not controls")
    }
}

/** Typing a number into a field and stepping it with the arrow keys (INTERACTION_V1 D6). */
class NumericStepTest {
    @Test
    fun upAndDownStepByOneAndShiftStepsByTen() {
        assertEquals("101", NumericStep.stepped("100", +1, big = false))
        assertEquals("99", NumericStep.stepped("100", -1, big = false))
        assertEquals("110", NumericStep.stepped("100", +1, big = true))
        assertEquals("90", NumericStep.stepped("100", -1, big = true))
    }

    @Test
    fun steppingCrossesZeroAndKeepsTheSign() {
        assertEquals("0", NumericStep.stepped("-1", +1, big = false))
        assertEquals("-5", NumericStep.stepped("5", -1, big = true))
        assertEquals("-9", NumericStep.stepped(" -10 ", +1, big = false), "surrounding spaces are ignored")
    }

    @Test
    fun anExpressionOrAnythingThatIsNotAWholeNumberIsLeftAlone() {
        assertNull(NumericStep.stepped("=xheight+10", +1, big = false))
        assertNull(NumericStep.stepped("", +1, big = false))
        assertNull(NumericStep.stepped("12.5", -1, big = false))
        assertNull(NumericStep.stepped("abc", +1, big = true))
    }

    @Test
    fun aDirectionThatIsNeitherUpNorDownIsRefused() {
        assertFailsWith<IllegalArgumentException> { NumericStep.stepped("5", 0, big = false) }
        assertFailsWith<IllegalArgumentException> { NumericStep.stepped("5", 2, big = false) }
    }
}

/** The ⋯ menu offers a Layers row only where there is a layers panel to show. */
class DrawPageMenuLayersTest {
    private fun roomWithoutAGlyph() =
        DrawRoom(
            session = null,
            glyphName = null,
            controller = null,
            directory = GlyphDirectory.of(emptyList()),
            fitNow = {},
            stepTo = {},
            spaceNow = { DrawSpace(Vec2(0.0, 0.0), 1.0) },
        )

    @Test
    fun withAPanelToShowTheMenuOffersLayersAndHideLayers() {
        val shown = DrawPageMenuItems.build(roomWithoutAGlyph(), layersShown = false, onCommand = {}, onGoTo = {}, onLayers = {})
        assertEquals(listOf("Command…", "Layers"), shown.map { it.title })
        val hide = DrawPageMenuItems.build(roomWithoutAGlyph(), layersShown = true, onCommand = {}, onGoTo = {}, onLayers = {})
        assertEquals(listOf("Command…", "Hide layers"), hide.map { it.title })
    }

    @Test
    fun withNoPanelToShowTheMenuHasNoLayersRowAtAll() {
        val items = DrawPageMenuItems.build(roomWithoutAGlyph(), layersShown = false, onCommand = {}, onGoTo = {}, onLayers = null)
        assertEquals(listOf("Command…"), items.map { it.title }, "a row that does nothing would be a lie")
    }

    @Test
    fun theLayersRowRunsWhatItWasGiven() {
        var toggled = 0
        val items = DrawPageMenuItems.build(roomWithoutAGlyph(), layersShown = false, onCommand = {}, onGoTo = {}, onLayers = { toggled++ })
        items.single { it.title == "Layers" }.run()
        assertEquals(1, toggled)
    }
}
