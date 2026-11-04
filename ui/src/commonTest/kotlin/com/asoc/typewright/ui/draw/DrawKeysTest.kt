// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.ui.puck.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrawKeysTest {
    private fun action(
        key: DrawKey,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        selected: Boolean = true,
    ) = DrawKeyMap.actionFor(KeyChord(key, ctrl, shift, alt), hasSelection = selected)

    @Test
    fun toolKeysAreTheBriefsLetters() {
        assertEquals(DrawKeyAction.PickTool(Tool.SELECT), action(DrawKey.V))
        assertEquals(DrawKeyAction.PickTool(Tool.PEN), action(DrawKey.P))
        assertEquals(DrawKeyAction.PickTool(Tool.PRIMITIVES), action(DrawKey.S))
        assertEquals(DrawKeyAction.PickTool(Tool.BOOLEAN), action(DrawKey.B))
        assertEquals(DrawKeyAction.PickTool(Tool.STROKE), action(DrawKey.K))
        assertEquals(DrawKeyAction.PickTool(Tool.MEASURE), action(DrawKey.M))
        assertEquals(DrawKeyAction.PickTool(Tool.ANCHORS), action(DrawKey.A))
        assertEquals(DrawKeyAction.PickTool(Tool.METRICS), action(DrawKey.T))
    }

    @Test
    fun aToolLetterWithCtrlIsNotTheTool() {
        assertNull(action(DrawKey.V, ctrl = true))
        assertEquals(DrawKeyAction.SelectAll, action(DrawKey.A, ctrl = true))
        assertEquals(DrawKeyAction.OpenMap, action(DrawKey.M, ctrl = true))
    }

    @Test
    fun undoAndRedoFollowTheChordsAndCtrlShiftYIsTidyNotRedo() {
        assertEquals(DrawKeyAction.Undo, action(DrawKey.Z, ctrl = true))
        assertEquals(DrawKeyAction.Redo, action(DrawKey.Z, ctrl = true, shift = true))
        assertEquals(DrawKeyAction.Redo, action(DrawKey.Y, ctrl = true))
        assertEquals(DrawKeyAction.Tidy, action(DrawKey.Y, ctrl = true, shift = true))
    }

    @Test
    fun theModifiedCommandsFollowTheKeyMap() {
        assertEquals(DrawKeyAction.AddExtremes, action(DrawKey.E, ctrl = true, shift = true))
        assertEquals(DrawKeyAction.CorrectDirection, action(DrawKey.R, ctrl = true, shift = true))
        assertEquals(DrawKeyAction.ReverseContour, action(DrawKey.R, ctrl = true, alt = true))
        assertEquals(DrawKeyAction.BreakHandles, action(DrawKey.B, ctrl = true, shift = true))
        assertEquals(DrawKeyAction.AlignToMetric, action(DrawKey.A, ctrl = true, shift = true))
        assertEquals(DrawKeyAction.LockGlyph, action(DrawKey.L, ctrl = true))
        assertEquals(DrawKeyAction.FitGlyph, action(DrawKey.DIGIT_0, ctrl = true))
        assertEquals(DrawKeyAction.ActualSize, action(DrawKey.DIGIT_1, ctrl = true))
        assertEquals(DrawKeyAction.ToggleSmooth, action(DrawKey.ENTER))
        assertEquals(DrawKeyAction.DeleteSelection, action(DrawKey.BACKSPACE))
        assertEquals(DrawKeyAction.DeleteSelection, action(DrawKey.DELETE))
    }

    @Test
    fun theDigitsOneToEightArePlainSectorKeysAndZeroAndNineAreNot() {
        val digits =
            listOf(
                DrawKey.DIGIT_1,
                DrawKey.DIGIT_2,
                DrawKey.DIGIT_3,
                DrawKey.DIGIT_4,
                DrawKey.DIGIT_5,
                DrawKey.DIGIT_6,
                DrawKey.DIGIT_7,
                DrawKey.DIGIT_8,
            )
        digits.forEachIndexed {
            i,
            key,
            ->
            assertEquals(DrawKeyAction.RadialSector(i), action(key), "digit ${i + 1} is sector $i clockwise from the top")
        }
        assertNull(action(DrawKey.DIGIT_0), "a bare zero does nothing")
        assertEquals(DrawKeyAction.ActualSize, action(DrawKey.DIGIT_1, ctrl = true), "with Ctrl a one is still actual size")
    }

    @Test
    fun arrowsNudgeByOneTenOrAHundredOnlyWhileSomethingIsSelected() {
        assertEquals(DrawKeyAction.Nudge(-1, 0), action(DrawKey.LEFT))
        assertEquals(DrawKeyAction.Nudge(0, 10), action(DrawKey.UP, shift = true))
        assertEquals(DrawKeyAction.Nudge(100, 0), action(DrawKey.RIGHT, ctrl = true))
        assertEquals(DrawKeyAction.Nudge(0, -1), action(DrawKey.DOWN))
        assertNull(action(DrawKey.LEFT, selected = false), "with nothing selected the arrow belongs to room navigation")
    }

    @Test
    fun tabStepsNodesWhileSomethingIsSelectedAndLeavesTheCanvasOtherwise() {
        assertEquals(DrawKeyAction.StepNode(1), action(DrawKey.TAB))
        assertEquals(DrawKeyAction.StepNode(-1), action(DrawKey.TAB, shift = true))
        assertNull(action(DrawKey.TAB, selected = false), "returning null lets the system move focus on")
    }

    @Test
    fun glyphKeysAreOffTheLetterKeys() {
        assertEquals(DrawKeyAction.PreviousGlyph, action(DrawKey.PAGE_UP))
        assertEquals(DrawKeyAction.NextGlyph, action(DrawKey.PAGE_DOWN))
        assertEquals(DrawKeyAction.GoToGlyph, action(DrawKey.G, ctrl = true))
        assertEquals(DrawKeyAction.Escape, action(DrawKey.ESCAPE))
        assertEquals(DrawKeyAction.ShortcutSheet, action(DrawKey.QUESTION))
    }

    @Test
    fun anUnknownKeyDoesNothing() {
        assertNull(action(DrawKey.OTHER))
        assertNull(action(DrawKey.OTHER, ctrl = true))
    }
}
