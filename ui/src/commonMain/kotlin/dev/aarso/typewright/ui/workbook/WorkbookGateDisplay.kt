// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.workbook

import dev.aarso.typewright.campaign.GateCheckStatus
import dev.aarso.typewright.campaign.WorkbookGateResult
import dev.aarso.typewright.qa.NodeEconomyReport
import dev.aarso.typewright.ui.tokens.toThousandsString
import kotlin.math.round

/** Task indices whose gate checks are keyed by a real single glyph name (`n`, `o`, ...) -- the shape `#s-workbook`'s own `.gate` grid draws its hero cell for. Every other task's checks are keyed by a longer label (`"OFL.txt"`, or the gate's own summary for a single not-implemented row), which reads better as a plain list than a grid of oversized cells. */
val GLYPH_KEYED_WORKBOOK_TASK_INDICES: Set<Int> = setOf(3, 4, 5, 6, 9)

/**
 * One [WorkbookGateResult] check, ready to render: [heroText] is the check's own [dev.aarso.
 * typewright.campaign.GateCheckResult.label] either way, but [isGlyphHero] says whether a caller
 * should draw it large, in the user's own font (`#s-workbook`'s `.gate div .g`) or as a plain mono
 * label; [valueText] is task 4's own real on-curve count (`#s-workbook`'s `.gate div b`, e.g.
 * `"1,252"`) when [boxText] is non-null, or [status]'s own word ([gateCheckStatusWord]) for every
 * other task -- the explorer has no worked example for those (only task 4's own gate is ever
 * shown), so this is this screen's own honest, disclosed extension of that one real shape to the
 * rest of this task's real data, not a second invented look.
 */
data class WorkbookGateRow(
    val heroText: String,
    val isGlyphHero: Boolean,
    val status: GateCheckStatus,
    val valueText: String,
    val boxText: String?,
    val detail: String,
)

/** [gate]'s own checks turned into [WorkbookGateRow]s for [taskIndex] -- see that type's own KDoc for the task-4-vs-everything-else split. */
fun workbookGateRows(
    taskIndex: Int,
    gate: WorkbookGateResult,
    task4Report: NodeEconomyReport?,
): List<WorkbookGateRow> {
    val isGlyphHero = taskIndex in GLYPH_KEYED_WORKBOOK_TASK_INDICES
    val task4ByGlyph = task4Report?.glyphs?.associateBy { it.glyphName }
    return gate.checks.map { check ->
        val task4Glyph = if (taskIndex == 4) task4ByGlyph?.get(check.label) else null
        val boxText = task4Glyph?.onCurveBox?.let { box -> "box ${box.q1.roundToDisplayInt()} – ${box.q3.roundToDisplayInt()}" }
        val valueText = if (task4Glyph != null) task4Glyph.onCurve.toThousandsString() else gateCheckStatusWord(check.status)
        WorkbookGateRow(
            heroText = check.label,
            isGlyphHero = isGlyphHero,
            status = check.status,
            valueText = valueText,
            boxText = boxText,
            detail = check.detail,
        )
    }
}

/** A [Double] quartile value rounded to the nearest whole number for display (`#s-workbook`'s own `"box 12 - 14"` is whole numbers; the real corpus quartiles are not always integers, e.g. `22.25`). */
fun Double.roundToDisplayInt(): Int = round(this).toInt()

/**
 * Whether a [dev.aarso.typewright.campaign.Demonstration.StatDemonstration.label] reads as a
 * short "hero" sample -- `#s-workbook`'s own literal `.demo .dl` content, `"noHO"` -- rather than
 * a longer descriptive caption. A real, screenshot-caught bug found while building this screen:
 * rendering every real label at `.dl`'s own 40sp size in the user's own font is only sensible for
 * task 4's own short sample; every other real label in `workbook-latin.yaml` is a phrase (task 6's
 * own real `"on-curve range across 13 open fonts (docs/RESEARCH_font_quality.md)"`, 68 characters),
 * and forcing one of those into a giant unconstrained hero text starved its own sibling stats
 * column of width in a `Row`, wrapping every character onto its own line (the same failure class
 * `docs/OPEN_QUESTIONS.md` items 74/81 already name for an unweighted `Row` sibling). This is a
 * length/shape heuristic over the real data, not a task-index special case, so it keeps working if
 * `workbook-latin.yaml`'s own content changes.
 */
fun isHeroStatLabel(label: String): Boolean = label.length <= 10 && ' ' !in label
