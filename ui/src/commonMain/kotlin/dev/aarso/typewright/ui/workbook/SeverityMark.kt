// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.workbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.campaign.GateCheckStatus

/**
 * CLAUDE.md law 8: "severity is a shape and a word" -- never colour (this draws every status in
 * [color], one caller-chosen ink tone, never a per-status colour of its own; the accompanying word
 * is the caller's job, e.g. [gateCheckStatusWord]). Traced faithfully from
 * `ui/typewright-explorer.html`'s own `<symbol id="mk-*">` defs (grep `id="mk-fail"`, around
 * lines 547-551, each on a 12x12 `viewBox`) and its `.mk{width:11px;height:11px;...;color:var(--fg)}`
 * rule -- every mark is chrome ink, not a meaning colour, matching every other severity mark
 * already in this codebase's own `.gate`/`.it`/`.margin` rows.
 *
 * [GateCheckStatus] maps onto the explorer's five marks in the one place their meanings actually
 * line up: [GateCheckStatus.PASS] -> `mk-pass` (check), [GateCheckStatus.WARN] -> `mk-warn`
 * (half-filled ring), [GateCheckStatus.FAIL] -> `mk-fail` (filled circle),
 * [GateCheckStatus.INFO] -> `mk-info` (hollow ring), [GateCheckStatus.NOT_IMPLEMENTED] ->
 * `mk-todo` (dashed ring, UI_SPEC section 5.4's own "dotted circle = not started" -- the one status
 * word this codebase already had a named shape for before this file existed).
 */
@Composable
fun SeverityMark(
    status: GateCheckStatus,
    color: Color,
    modifier: Modifier = Modifier,
    sizeDp: Double = 11.0,
) {
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        drawSeverityMark(status, color)
    }
}

/** The plain [DrawScope] draw, reused by [SeverityMark] and by anything else that wants to paint a mark mid-`Canvas` (e.g. a legend row) without a nested `Canvas`. */
fun DrawScope.drawSeverityMark(
    status: GateCheckStatus,
    color: Color,
) {
    // The explorer's own symbols are defined on a 12x12 viewBox; scale that design grid to
    // whatever box this DrawScope was actually given (the same "scale the design grid" approach
    // ToolIcons.kt's own drawToolIcon already uses).
    val scale = size.minDimension / 12f
    val center = Offset(6f * scale, 6f * scale)

    when (status) {
        GateCheckStatus.FAIL -> {
            // mk-fail: circle cx6 cy6 r5, filled.
            drawCircle(color = color, radius = 5f * scale, center = center)
        }

        GateCheckStatus.WARN -> {
            // mk-warn: a stroked ring (r4.6, stroke 1.5) plus its own right half filled solid --
            // "M6 1.4 a4.6 4.6 0 0 1 0 9.2 z", top-to-bottom clockwise around r4.6, then straight
            // back up the vertical diameter, which traces exactly the right half-disc.
            val r = 4.6f * scale
            drawCircle(color = color, radius = r, center = center, style = Stroke(width = 1.5f * scale))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f),
            )
        }

        GateCheckStatus.PASS -> {
            // mk-pass: "M2.3 6.4 l2.6 2.6 4.8-5.6" -- a two-segment checkmark stroke, no fill.
            val p1 = Offset(2.3f * scale, 6.4f * scale)
            val p2 = Offset((2.3f + 2.6f) * scale, (6.4f + 2.6f) * scale)
            val p3 = Offset((2.3f + 2.6f + 4.8f) * scale, (6.4f + 2.6f - 5.6f) * scale)
            val strokeWidth = 1.7f * scale
            drawLine(color = color, start = p1, end = p2, strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color = color, start = p2, end = p3, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }

        GateCheckStatus.INFO -> {
            // mk-info: the same hollow ring as mk-warn's own outline, with no half-fill.
            drawCircle(color = color, radius = 4.6f * scale, center = center, style = Stroke(width = 1.5f * scale))
        }

        GateCheckStatus.NOT_IMPLEMENTED -> {
            // mk-todo: r4.4, stroke 1.2, dashed 2.2/2.2 -- UI_SPEC's own "not started" mark.
            drawCircle(
                color = color,
                radius = 4.4f * scale,
                center = center,
                style =
                    Stroke(
                        width = 1.2f * scale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.2f * scale, 2.2f * scale)),
                    ),
            )
        }
    }
}

/**
 * The word half of "severity is a shape and a word" (CLAUDE.md law 8) for a [GateCheckStatus] --
 * this app's own real gate vocabulary (`campaign`'s [GateCheckStatus], not brief section 8.2's
 * "outlier / above / in range", which is the *Economy* screen's own different wording for a
 * different check; `#s-workbook`'s own `.gate` rows use `mk-fail` for a node-economy miss, so a
 * pass/fail word is what the workbook's own gate says, not the box-plot legend).
 */
fun gateCheckStatusWord(status: GateCheckStatus): String =
    when (status) {
        GateCheckStatus.PASS -> "PASS"
        GateCheckStatus.WARN -> "WARN"
        GateCheckStatus.FAIL -> "FAIL"
        GateCheckStatus.INFO -> "INFO"
        GateCheckStatus.NOT_IMPLEMENTED -> "NOT IMPLEMENTED"
    }
