// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.asoc.typewright.ui.project.GlyphPathCache
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** The scale and left edge the row had when a drag began, which hold until it ends so the finger and the line it holds stay together. */
private data class FrozenRow(
    val dpPerUnit: Double,
    val leftDp: Double,
)

/** What a press on the row took hold of, found by [SpaceRowTarget.at]. */
internal data class SpaceRowTarget(
    /** The cell to select (Spacing) or the left cell of the junction to select (Kerning). */
    val index: Int,
    /** The number a drag that starts here changes. */
    val field: SpaceField,
) {
    companion object {
        /**
         * What a press [xDp] in from the row's left edge takes hold of. In Spacing it is the cell under the
         * press, except that a press near the right advance line of the cell already [selected] takes that line
         * (its right sidebearing); on the body of a cell a drag moves the drawing within its space (its left
         * sidebearing). In Kerning it is the junction nearest the press.
         */
        fun at(
            string: SpaceString,
            mode: SpaceMode,
            selected: Int,
            xDp: Double,
            dpPerUnit: Double,
        ): SpaceRowTarget? {
            val units = SpaceRowLayout.unitsAt(xDp, dpPerUnit)
            if (mode == SpaceMode.KERNING) {
                val junction = string.nearestJunction(units) ?: return null
                return SpaceRowTarget(junction, SpaceField.KERN)
            }
            val current = string.cells.getOrNull(selected)
            if (current != null && SpaceRowLayout.nearLine(xDp, current.right * dpPerUnit)) {
                return SpaceRowTarget(current.index, SpaceField.RIGHT)
            }
            val cell = string.cellAt(units) ?: return null
            return SpaceRowTarget(cell, SpaceField.LEFT)
        }
    }
}

/**
 * The control string, set in the project's outlines at the largest size the width allows (the board's
 * 72 dp), each letter between its two advance lines with its numbers under it: in Spacing the left and
 * right sidebearing, in Kerning the pair value between two letters and where it comes from. The selected
 * letter (or the selected pair) is an ink block with paper-coloured letters. A press selects; a sideways
 * drag from there changes a number, one undo step, through [onScrubStart], [onScrub] and [onScrubEnd] -
 * and [onScrubEnd] is called however the drag ends, including when the row leaves the screen mid-drag.
 *
 * [before] is the string as it stood at the Before, or null where there is none to show; [progress] is
 * 0 for it and 1 for [after], and the letters slide between the two as it moves.
 */
@Composable
internal fun SpaceRow(
    after: SpaceString,
    before: SpaceString?,
    progress: Float,
    font: ProjectFont,
    mode: SpaceMode,
    selected: Int,
    editable: Boolean,
    texture: CanvasTexture,
    paths: GlyphPathCache,
    maxEmDp: Double,
    centred: Boolean,
    onSelect: (Int) -> Unit,
    onScrubStart: (SpaceRowTarget) -> Boolean,
    onScrub: (Int) -> Unit,
    onScrubEnd: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val lines = ink.copy(alpha = ADVANCE_LINE_ALPHA)
    val blockLines = canvas.copy(alpha = ADVANCE_LINE_ALPHA)
    val showBefore = before != null && before.cells.size == after.cells.size
    val t = if (showBefore) progress.coerceIn(0f, 1f) else 1f
    val shown = if (t >= HALF || !showBefore) after else checkNotNull(before)

    // A drag that widens a letter widens the string; the scale it was set at holds until the finger lifts, or the row would shrink under it.
    var frozen by remember { mutableStateOf<FrozenRow?>(null) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth.value.toDouble()
        val fit =
            frozen?.let { SpaceRowLayout.at(shown, it.dpPerUnit, available) }
                ?: SpaceRowLayout.fit(shown, font.unitsPerEm, available, maxEmDp)
        val dpu = fit.dpPerUnit
        val leftDp = frozen?.leftDp ?: if (centred) (available - fit.widthDp) / 2.0 else 0.0
        val latestLeft by rememberUpdatedState(leftDp)
        val boxHeightDp = (font.ascent + font.descent) * dpu
        val labelDp = LABEL_HEIGHT_DP
        val rowWidth = fit.widthDp.dp
        val cells = shown.cells.take(fit.shown)

        fun originX(i: Int): Double =
            if (showBefore) {
                val a = checkNotNull(before).cells[i].x
                val b = after.cells[i].x
                a + (b - a) * t
            } else {
                after.cells[i].x
            }

        val latestShown by rememberUpdatedState(shown)
        val latestSelected by rememberUpdatedState(selected)
        val latestMode by rememberUpdatedState(mode)
        val latestEditable by rememberUpdatedState(editable)
        val latestDpu by rememberUpdatedState(dpu)
        val latestSelect by rememberUpdatedState(onSelect)
        val latestStart by rememberUpdatedState(onScrubStart)
        val latestScrub by rememberUpdatedState(onScrub)
        val latestEnd by rememberUpdatedState(onScrubEnd)

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = leftDp.dp)
                    .width(rowWidth)
                    .height((boxHeightDp + labelDp).dp)
                    .semantics {
                        contentDescription = describeRow(cells, shown, mode, selected)
                    }.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val dpu0 = latestDpu
                            val pressDp = down.position.x / density.density.toDouble()
                            val target =
                                SpaceRowTarget.at(latestShown, latestMode, latestSelected, pressDp, dpu0) ?: return@awaitEachGesture
                            if (target.index != latestSelected && !(latestMode == SpaceMode.SPACING && target.field == SpaceField.RIGHT)) {
                                latestSelect(target.index)
                            }
                            if (!latestEditable) return@awaitEachGesture
                            var scrubbing = false
                            var finished = false
                            try {
                                var travelled = 0f
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUp()) {
                                        finished = true
                                        break
                                    }
                                    travelled += change.positionChange().x
                                    if (!scrubbing && abs(travelled) > viewConfiguration.touchSlop) {
                                        frozen = FrozenRow(latestDpu, latestLeft)
                                        scrubbing = latestStart(target)
                                        if (!scrubbing) {
                                            frozen = null
                                            break
                                        }
                                    }
                                    if (scrubbing) {
                                        change.consume()
                                        latestScrub((travelled / density.density / latestDpu).roundToInt())
                                    }
                                }
                            } finally {
                                if (scrubbing) latestEnd(finished)
                                frozen = null
                            }
                        }
                    },
        ) {
            Canvas(Modifier.width(rowWidth).height(boxHeightDp.dp)) {
                val scale = (dpu * density.density).toFloat()
                val ascentPx = font.ascent * scale
                val heightPx = size.height
                // The ink block behind the selection: the letter (Spacing) or both letters of the pair (Kerning).
                val blockFrom: Int?
                val blockTo: Int?
                if (mode == SpaceMode.SPACING) {
                    blockFrom = selected.takeIf { it in cells.indices }
                    blockTo = blockFrom
                } else {
                    val j = shown.junctionAt(selected)?.takeIf { it + 1 in cells.indices }
                    blockFrom = j
                    blockTo = j?.plus(1)
                }
                if (blockFrom != null && blockTo != null) {
                    val left = originX(blockFrom).toFloat() * scale
                    val right = (originX(blockTo) + cells[blockTo].glyph.advanceWidth).toFloat() * scale
                    drawRect(ink, topLeft = Offset(left, 0f), size = Size(right - left, heightPx))
                }
                val hairline = 1.dp.toPx()
                for (cell in cells) {
                    val x = originX(cell.index).toFloat() * scale
                    val inBlock = blockFrom != null && blockTo != null && cell.index in blockFrom..blockTo
                    val lineColor = if (inBlock) blockLines else lines
                    drawLine(lineColor, Offset(x, 0f), Offset(x, heightPx), strokeWidth = hairline)
                    val rightX = x + cell.glyph.advanceWidth * scale
                    drawLine(lineColor, Offset(rightX, 0f), Offset(rightX, heightPx), strokeWidth = hairline)
                    withTransform({
                        translate(x, ascentPx)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        drawPath(paths.pathOf(cell.glyph), if (inBlock) canvas else ink)
                    }
                }
                // The grab handle: a small square on the line a drag changes, so the drag is findable without being told.
                if (editable) {
                    val handleX =
                        if (mode == SpaceMode.SPACING) {
                            cells.getOrNull(selected)?.let { (originX(it.index) + it.glyph.advanceWidth).toFloat() * scale }
                        } else {
                            shown.junctionAt(selected)?.takeIf { it + 1 in cells.indices }?.let { originX(it + 1).toFloat() * scale }
                        }
                    if (handleX != null) {
                        val half = HANDLE_DP.dp.toPx() / 2f
                        drawRect(ink, topLeft = Offset(handleX - half, heightPx - half), size = Size(half * 2f, half * 2f))
                    }
                }
            }
            // The numbers under the letters.
            for (cell in cells) {
                val x = originX(cell.index)
                val isSelected =
                    if (mode == SpaceMode.SPACING) {
                        cell.index == selected
                    } else {
                        false
                    }
                if (mode == SpaceMode.SPACING) {
                    val cellDp = cell.glyph.advanceWidth * dpu
                    Box(
                        Modifier
                            .width(cellDp.dp)
                            .offset { IntOffset((x * dpu * density.density).roundToInt(), (boxHeightDp * density.density).roundToInt()) },
                    ) {
                        BasicText(
                            text = SpaceRowLayout.sidebearingLabel(cell.lsb, cell.rsb, cellDp),
                            style =
                                Typography
                                    .mono(sizeSp = LABEL_SP, weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                                    .copy(color = if (isSelected) fg else muted, letterSpacing = 0.04.em, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (cell.index > 0 && cell.kern != null) {
                    val previous = cells[cell.index - 1]
                    val junctionX = (originX(previous.index) + previous.glyph.advanceWidth + x) / 2.0
                    val selectedJunction = shown.junctionAt(selected) == previous.index
                    val kern = cell.kern
                    Box(
                        Modifier
                            .width(JUNCTION_LABEL_DP.dp)
                            .offset {
                                IntOffset(
                                    ((junctionX * dpu - JUNCTION_LABEL_DP / 2.0) * density.density).roundToInt(),
                                    (boxHeightDp * density.density).roundToInt(),
                                )
                            },
                    ) {
                        BasicText(
                            text = kern.value.roundToInt().toString() + "\n" + kern.sourceWord(),
                            style =
                                Typography
                                    .mono(sizeSp = LABEL_SP, weight = if (selectedJunction) FontWeight.SemiBold else FontWeight.Normal)
                                    .copy(
                                        color = if (selectedJunction) fg else muted,
                                        letterSpacing = 0.04.em,
                                        textAlign = TextAlign.Center,
                                    ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** What a screen reader says of the row: the string, what is under its letters, and the selected letter's or pair's numbers. */
internal fun describeRow(
    cells: List<SpaceCell>,
    shown: SpaceString,
    mode: SpaceMode,
    selected: Int,
): String {
    val letters = cells.joinToString("") { it.char.toString() }
    if (mode == SpaceMode.SPACING) {
        val cell = cells.getOrNull(selected)
        val selection =
            cell?.let {
                ". Selected ${it.glyph.name}: left ${it.lsb ?: "none"}, right ${it.rsb ?: "none"}, advance ${it.glyph.advanceWidth}"
            }
        return "Control string $letters, sidebearings under each letter" + selection.orEmpty()
    }
    val junction = shown.junctionAt(selected)?.takeIf { it + 1 in cells.indices }
    val pair =
        junction?.let {
            val second = cells[it + 1]
            ". Selected pair ${cells[it].glyph.name} ${second.glyph.name}: kern ${second.kern?.value?.roundToInt() ?: 0}, ${second.kern?.sourceWord()}"
        }
    return "Control string $letters, pair values between letters" + pair.orEmpty()
}

private const val ADVANCE_LINE_ALPHA = 0.35f
private const val HALF = 0.5f
private const val LABEL_SP = 9.5
private const val LABEL_HEIGHT_DP = 34.0
private const val HANDLE_DP = 8f
private const val JUNCTION_LABEL_DP = 56.0
