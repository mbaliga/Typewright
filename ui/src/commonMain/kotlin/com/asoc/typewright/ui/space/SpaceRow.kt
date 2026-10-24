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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The scale, left edge and place along the string the row had when a drag began, which hold until it ends so the finger and the line it holds stay together. */
private data class FrozenRow(
    val dpPerUnit: Double,
    val leftDp: Double,
    val originUnits: Double,
)

/** A letter as the row draws it at this moment: its [slot], the [cell] shown, where it stands in the row in font units, and how solid it is. */
private class RowItem(
    val slot: Int,
    val cell: SpaceCell,
    val fromAfter: Boolean,
    val x: Double,
    val alpha: Float,
) {
    val right: Double get() = x + cell.glyph.advanceWidth
}

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
         * sidebearing). In Kerning it is the junction nearest the press. [originUnits] is how far along the string
         * the row's left edge is, when the string is longer than the row and the row has moved along it.
         */
        fun at(
            string: SpaceString,
            mode: SpaceMode,
            selected: Int,
            xDp: Double,
            dpPerUnit: Double,
            originUnits: Double = 0.0,
        ): SpaceRowTarget? {
            val units = SpaceRowLayout.unitsAt(xDp, dpPerUnit) + originUnits
            if (mode == SpaceMode.KERNING) {
                val junction = string.nearestJunction(units) ?: return null
                return SpaceRowTarget(junction, SpaceField.KERN)
            }
            val current = string.cells.getOrNull(selected)
            if (current != null && SpaceRowLayout.nearLine(xDp, (current.right - originUnits) * dpPerUnit)) {
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
 * 0 for it and 1 for [after]. [blend] puts the two side by side: a letter in both slides from one place to
 * the other as it moves, a letter in only one fades in or out, so the Before is always the string as it was and
 * never the current one. A string too long for the row at its smallest size is shown from the place along it that
 * keeps the selected letter, or pair, in view.
 */
@Composable
internal fun SpaceRow(
    after: SpaceString,
    before: SpaceString?,
    blend: SpaceBlend,
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
    val t = if (before != null) progress.coerceIn(0f, 1f) else 1f
    val showingBefore = before != null && t < HALF
    val shown = if (showingBefore) checkNotNull(before) else after
    val slots = blend.slots

    // A drag that widens a letter widens the string; the scale it was set at holds until the finger lifts, or the row would shrink under it.
    var frozen by remember { mutableStateOf<FrozenRow?>(null) }
    var scrollUnits by remember { mutableStateOf(0.0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth.value.toDouble()
        // The scale is the one the wider of the two pictures needs, so the letters do not change size as they slide between them.
        val basis = if (before != null && before.width > after.width) before else after
        val dpu = frozen?.dpPerUnit ?: SpaceRowLayout.fit(basis, font.unitsPerEm, available, maxEmDp).dpPerUnit
        val extent = max(after.cells.lastOrNull()?.right ?: 0.0, before?.cells?.lastOrNull()?.right ?: 0.0)
        val availableUnits = available / dpu
        val whole = extent * dpu <= available + EPSILON_DP
        // Centred on the picture that is showing, as it was when the two always had the same number of letters.
        val rowWidthDp = if (whole) (shown.cells.lastOrNull()?.right ?: 0.0) * dpu else available

        // The selection, as places in the row: a letter in Spacing, the two letters of the pair in Kerning.
        val junction = if (mode == SpaceMode.KERNING) after.junctionAt(selected)?.takeIf { it + 1 in after.cells.indices } else null
        val selFrom = if (mode == SpaceMode.SPACING) blend.slotOfAfter(selected) else junction?.let { blend.slotOfAfter(it) }
        val selTo = if (mode == SpaceMode.SPACING) selFrom else junction?.let { blend.slotOfAfter(it + 1) }

        val leftDp = frozen?.leftDp ?: if (centred) (available - rowWidthDp) / 2.0 else 0.0
        val originUnits =
            frozen?.originUnits ?: if (whole) {
                0.0
            } else if (selFrom != null && selTo != null) {
                SpaceRowLayout.scrollFor(slots[selFrom].reachLeft, slots[selTo].reachRight, availableUnits, scrollUnits, extent)
            } else {
                SpaceRowLayout.scrollFor(0.0, 0.0, availableUnits, scrollUnits, extent)
            }
        SideEffect { if (frozen == null && originUnits != scrollUnits) scrollUnits = originUnits }
        val latestLeft by rememberUpdatedState(leftDp)
        val latestOrigin by rememberUpdatedState(originUnits)

        val boxHeightDp = (font.ascent + font.descent) * dpu
        val labelDp = LABEL_HEIGHT_DP
        val rowWidth = rowWidthDp.dp
        val epsilonUnits = EPSILON_DP / dpu

        // Only the letters that are whole inside the row are drawn; the others are off its edges, and the selection is never one of them.
        val items =
            slots.mapIndexedNotNull { i, slot ->
                val solid = slot.alpha(t)
                if (solid <= 0f) return@mapIndexedNotNull null
                val cell = slot.cell(t)
                val x = slot.x(t)
                if (x < originUnits - epsilonUnits || x + cell.glyph.advanceWidth > originUnits + availableUnits + epsilonUnits) {
                    return@mapIndexedNotNull null
                }
                RowItem(i, cell, fromAfter = cell === slot.after, x = x - originUnits, alpha = solid)
            }
        val itemBySlot = items.associateBy { it.slot }
        val selectedShown = if (showingBefore) blend.beforeIndexOfAfter(selected) ?: -1 else selected
        val shownCells = items.filter { it.fromAfter != showingBefore }.map { it.cell }

        val latestShown by rememberUpdatedState(shown)
        val latestSelected by rememberUpdatedState(selected)
        val latestSelectedShown by rememberUpdatedState(selectedShown)
        val latestToAfter by rememberUpdatedState<(Int) -> Int?>(if (showingBefore) blend::afterIndexOfBefore else { index -> index })
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
                        contentDescription =
                            (if (showingBefore) "Before. " else "") + describeRow(shown.cells, shown, mode, selectedShown, shownCells)
                    }.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val dpu0 = latestDpu
                            val pressDp = down.position.x / density.density.toDouble()
                            val pressed =
                                SpaceRowTarget.at(latestShown, latestMode, latestSelectedShown, pressDp, dpu0, latestOrigin)
                                    ?: return@awaitEachGesture
                            // The Before's letters are numbered as the Before had them; the page selects by the string as it is now.
                            val target = pressed.copy(index = latestToAfter(pressed.index) ?: return@awaitEachGesture)
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
                                        frozen = FrozenRow(latestDpu, latestLeft, latestOrigin)
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
                val blockFrom = selFrom?.let { itemBySlot[it] }
                val blockTo = selTo?.let { itemBySlot[it] }
                val blockSlots = if (blockFrom != null && blockTo != null) blockFrom.slot..blockTo.slot else null
                if (blockFrom != null && blockTo != null) {
                    val left = blockFrom.x.toFloat() * scale
                    val right = blockTo.right.toFloat() * scale
                    drawRect(
                        ink,
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, heightPx),
                        alpha = min(blockFrom.alpha, blockTo.alpha),
                    )
                }
                val hairline = 1.dp.toPx()
                for (item in items) {
                    val x = item.x.toFloat() * scale
                    val inBlock = blockSlots != null && item.slot in blockSlots
                    val lineColor = if (inBlock) blockLines else lines
                    drawLine(lineColor, Offset(x, 0f), Offset(x, heightPx), strokeWidth = hairline, alpha = item.alpha)
                    val rightX = x + item.cell.glyph.advanceWidth * scale
                    drawLine(lineColor, Offset(rightX, 0f), Offset(rightX, heightPx), strokeWidth = hairline, alpha = item.alpha)
                    withTransform({
                        translate(x, ascentPx)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        drawPath(paths.pathOf(item.cell.glyph), if (inBlock) canvas else ink, alpha = item.alpha)
                    }
                }
                // The grab handle: a small square on the line a drag changes, so the drag is findable without being told.
                if (editable) {
                    val handleX =
                        if (mode == SpaceMode.SPACING) {
                            blockFrom?.right?.toFloat()?.times(scale)
                        } else {
                            blockTo?.x?.toFloat()?.times(scale)
                        }
                    if (handleX != null) {
                        val half = HANDLE_DP.dp.toPx() / 2f
                        drawRect(ink, topLeft = Offset(handleX - half, heightPx - half), size = Size(half * 2f, half * 2f))
                    }
                }
            }
            // The numbers under the letters.
            for (item in items) {
                val cell = item.cell
                if (mode == SpaceMode.SPACING) {
                    val isSelected = item.slot == selFrom
                    val cellDp = cell.glyph.advanceWidth * dpu
                    Box(
                        Modifier
                            .width(cellDp.dp)
                            .alpha(item.alpha)
                            .offset {
                                IntOffset(
                                    (item.x * dpu * density.density).roundToInt(),
                                    (boxHeightDp * density.density).roundToInt(),
                                )
                            },
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
                    // The junction sits between this letter and the one before it in its own picture.
                    val ownIsAfter = item.fromAfter
                    val own = if (ownIsAfter) after else checkNotNull(before)
                    val previous = own.cells[cell.index - 1]
                    val previousSlot = if (ownIsAfter) blend.slotOfAfter(previous.index) else blend.slotOfBefore(previous.index)
                    val previousItem = previousSlot?.let { itemBySlot[it] } ?: continue
                    val junctionX = (previousItem.x + previous.glyph.advanceWidth + item.x) / 2.0
                    val selectedJunction = item.slot == selTo
                    val kern = cell.kern
                    Box(
                        Modifier
                            .width(JUNCTION_LABEL_DP.dp)
                            .alpha(min(item.alpha, previousItem.alpha))
                            .offset {
                                IntOffset(
                                    ((junctionX * dpu - JUNCTION_LABEL_DP / 2.0) * density.density).roundToInt(),
                                    (boxHeightDp * density.density).roundToInt(),
                                )
                            },
                    ) {
                        BasicText(
                            text = formatUnits(kern.value) + "\n" + kern.sourceWord(),
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

/**
 * What a screen reader says of the row: the string, what is under its letters, and the selected letter's or pair's numbers.
 * [cells] and [shown] are the whole string, [selected] a place in it, and [visible] the letters the row has room for.
 */
internal fun describeRow(
    cells: List<SpaceCell>,
    shown: SpaceString,
    mode: SpaceMode,
    selected: Int,
    visible: List<SpaceCell> = cells,
): String {
    val letters = visible.joinToString("") { it.char.toString() }
    if (mode == SpaceMode.SPACING) {
        val cell = cells.getOrNull(selected)
        val selection =
            cell?.let {
                ". Selected ${it.glyph.name}: left ${it.lsb ?: "none"}, right ${it.rsb ?: "none"}, advance ${it.glyph.advanceWidth}"
            }
        return "Control string $letters, sidebearings under each letter" + selection.orEmpty()
    }
    val junction = selected.takeIf { it in cells.indices }?.let(shown::junctionAt)?.takeIf { it + 1 in cells.indices }
    val pair =
        junction?.let {
            val second = cells[it + 1]
            val kern = second.kern?.value?.let(::formatUnits) ?: "0"
            ". Selected pair ${cells[it].glyph.name} ${second.glyph.name}: kern $kern, ${second.kern?.sourceWord()}"
        }
    return "Control string $letters, pair values between letters" + pair.orEmpty()
}

private const val ADVANCE_LINE_ALPHA = 0.35f
private const val HALF = 0.5f
private const val LABEL_SP = 9.5
private const val LABEL_HEIGHT_DP = 34.0
private const val HANDLE_DP = 8f
private const val JUNCTION_LABEL_DP = 56.0
private const val EPSILON_DP = 0.5
