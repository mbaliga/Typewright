// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.ui.puck.Tool

/** The keys Draw answers to, named by physical key, never by the character they type, so a tool key works on any layout. */
enum class DrawKey {
    A,
    B,
    D,
    E,
    G,
    K,
    L,
    M,
    P,
    R,
    S,
    T,
    V,
    Y,
    Z,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    TAB,
    ENTER,
    BACKSPACE,
    DELETE,
    ESCAPE,
    SPACE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    PAGE_UP,
    PAGE_DOWN,
    QUESTION,
    OTHER,
}

/** A key with the modifiers held. Ctrl stands for ⌘ on a Mac later (INTERACTION_V1 §6.1). */
data class KeyChord(
    val key: DrawKey,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

/** What a key does in Draw. */
sealed interface DrawKeyAction {
    data class PickTool(
        val tool: Tool,
    ) : DrawKeyAction

    /** Move the selection by [dx], [dy] font units (an arrow key: 1, with Shift 10, with Ctrl 100). */
    data class Nudge(
        val dx: Int,
        val dy: Int,
    ) : DrawKeyAction

    data object Undo : DrawKeyAction

    data object Redo : DrawKeyAction

    data object ToggleSmooth : DrawKeyAction

    data object DeleteSelection : DrawKeyAction

    data object AddExtremes : DrawKeyAction

    data object Tidy : DrawKeyAction

    data object CorrectDirection : DrawKeyAction

    data object ReverseContour : DrawKeyAction

    data object BreakHandles : DrawKeyAction

    data object AlignToMetric : DrawKeyAction

    data object LockGlyph : DrawKeyAction

    data object SelectAll : DrawKeyAction

    /** Tab and Shift Tab: the next or previous node, only while something is selected; otherwise the key is left to the system to move focus. */
    data class StepNode(
        val delta: Int,
    ) : DrawKeyAction

    data object FitGlyph : DrawKeyAction

    data object ActualSize : DrawKeyAction

    data object PreviousGlyph : DrawKeyAction

    data object NextGlyph : DrawKeyAction

    data object GoToGlyph : DrawKeyAction

    data object OpenMap : DrawKeyAction

    data object Escape : DrawKeyAction

    /** With the radial open, a digit picks the sector that many places clockwise from the top (1 is the top): [index] 0 to 7. */
    data class RadialSector(
        val index: Int,
    ) : DrawKeyAction

    data object ShortcutSheet : DrawKeyAction
}

/**
 * The V1 key map (INTERACTION_V1 §6.1). Tool keys are the plain letters; every command with a
 * modifier follows the table. Ctrl is the temporary Select tool while dragging (D5), not an
 * additive-select modifier, so it never appears here as one. Ctrl Shift Y is Tidy, and so is not
 * Redo: Redo is Ctrl Shift Z or Ctrl Y.
 */
object DrawKeyMap {
    fun actionFor(
        chord: KeyChord,
        hasSelection: Boolean,
    ): DrawKeyAction? {
        val (key, ctrl, shift, alt) = chord
        if (ctrl) return withCtrl(key, shift, alt, hasSelection)
        if (alt) return null
        return when (key) {
            DrawKey.V -> DrawKeyAction.PickTool(Tool.SELECT)

            DrawKey.P -> DrawKeyAction.PickTool(Tool.PEN)

            DrawKey.S -> DrawKeyAction.PickTool(Tool.PRIMITIVES)

            DrawKey.B -> DrawKeyAction.PickTool(Tool.BOOLEAN)

            DrawKey.K -> DrawKeyAction.PickTool(Tool.STROKE)

            DrawKey.M -> DrawKeyAction.PickTool(Tool.MEASURE)

            DrawKey.A -> DrawKeyAction.PickTool(Tool.ANCHORS)

            DrawKey.T -> DrawKeyAction.PickTool(Tool.METRICS)

            DrawKey.ENTER -> DrawKeyAction.ToggleSmooth

            DrawKey.BACKSPACE, DrawKey.DELETE -> DrawKeyAction.DeleteSelection

            DrawKey.TAB -> if (hasSelection) DrawKeyAction.StepNode(if (shift) -1 else 1) else null

            DrawKey.ESCAPE -> DrawKeyAction.Escape

            DrawKey.DIGIT_1, DrawKey.DIGIT_2, DrawKey.DIGIT_3, DrawKey.DIGIT_4,
            DrawKey.DIGIT_5, DrawKey.DIGIT_6, DrawKey.DIGIT_7, DrawKey.DIGIT_8,
            -> DrawKeyAction.RadialSector(key.ordinal - DrawKey.DIGIT_1.ordinal)

            DrawKey.PAGE_UP -> DrawKeyAction.PreviousGlyph

            DrawKey.PAGE_DOWN -> DrawKeyAction.NextGlyph

            DrawKey.QUESTION -> DrawKeyAction.ShortcutSheet

            DrawKey.LEFT, DrawKey.RIGHT, DrawKey.UP, DrawKey.DOWN -> if (hasSelection) nudge(key, if (shift) 10 else 1) else null

            else -> null
        }
    }

    private fun nudge(
        key: DrawKey,
        step: Int,
    ): DrawKeyAction =
        when (key) {
            DrawKey.LEFT -> DrawKeyAction.Nudge(-step, 0)
            DrawKey.RIGHT -> DrawKeyAction.Nudge(step, 0)
            DrawKey.UP -> DrawKeyAction.Nudge(0, step)
            else -> DrawKeyAction.Nudge(0, -step)
        }

    private fun withCtrl(
        key: DrawKey,
        shift: Boolean,
        alt: Boolean,
        hasSelection: Boolean,
    ): DrawKeyAction? =
        when {
            alt && !shift -> {
                if (key == DrawKey.R) DrawKeyAction.ReverseContour else null
            }

            alt -> {
                null
            }

            shift -> {
                when (key) {
                    DrawKey.Z -> DrawKeyAction.Redo
                    DrawKey.E -> DrawKeyAction.AddExtremes
                    DrawKey.Y -> DrawKeyAction.Tidy
                    DrawKey.R -> DrawKeyAction.CorrectDirection
                    DrawKey.B -> DrawKeyAction.BreakHandles
                    DrawKey.A -> DrawKeyAction.AlignToMetric
                    DrawKey.LEFT, DrawKey.RIGHT, DrawKey.UP, DrawKey.DOWN -> if (hasSelection) nudge(key, NUDGE_LARGE) else null
                    else -> null
                }
            }

            else -> {
                when (key) {
                    DrawKey.Z -> DrawKeyAction.Undo
                    DrawKey.Y -> DrawKeyAction.Redo
                    DrawKey.L -> DrawKeyAction.LockGlyph
                    DrawKey.A -> DrawKeyAction.SelectAll
                    DrawKey.G -> DrawKeyAction.GoToGlyph
                    DrawKey.M -> DrawKeyAction.OpenMap
                    DrawKey.DIGIT_0 -> DrawKeyAction.FitGlyph
                    DrawKey.DIGIT_1 -> DrawKeyAction.ActualSize
                    DrawKey.LEFT, DrawKey.RIGHT, DrawKey.UP, DrawKey.DOWN -> if (hasSelection) nudge(key, NUDGE_LARGE) else null
                    else -> null
                }
            }
        }

    private const val NUDGE_LARGE = 100
}
