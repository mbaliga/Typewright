// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.NodeEconomyReport

/**
 * How serious a layer-two finding is, as a shape and a word (never a colour alone): ● fail, ◐ warn,
 * ✓ pass, and ○ not measured for a check that could not run on this project and says why. Not measured
 * is a fourth state rather than a quiet pass: a check that had nothing to judge by must not read as
 * though it had judged.
 */
enum class Severity(
    val shape: String,
    val word: String,
) {
    FAIL("●", "fail"),
    WARN("◐", "warn"),
    PASS("✓", "pass"),
    NOT_MEASURED("○", "not measured"),
}

/** The five groups of the Check report, in the order a reviewer meets them (see [CheckReport]). */
enum class CheckGroup(
    val title: String,
) {
    OUTLINES("Outlines"),
    METRICS("Metrics"),
    PROVENANCE("Provenance"),
    NAMES("Names"),
    LICENCE("Licence"),
}

/**
 * What stands behind the line between one severity and the next (CLAUDE.md law 5): [MEASURED] against the
 * reference faces in `qa/corpus`, [PUBLISHED] when it is a rule someone else published (Fontbakery's
 * outline thresholds, Google Fonts' vertical-metrics rules), or [HEURISTIC] when it is our own judgement,
 * which the screen says in those words.
 */
enum class Basis(
    val label: String,
) {
    MEASURED("measured"),
    PUBLISHED("published rule"),
    HEURISTIC("our heuristic"),
}

/** Where a finding's action goes. A route is data; the screen decides how to get there. */
sealed interface CheckRoute {
    /** The Economy view of Check. */
    data object OpenEconomy : CheckRoute

    /** Draw, on [glyph]. */
    data class OpenDraw(
        val glyph: String,
    ) : CheckRoute

    /** Trace, for [glyphs] to refit. */
    data class OpenTrace(
        val glyphs: List<String>,
    ) : CheckRoute

    /** Capture, to start the source again from the person's drawings. */
    data object OpenCapture : CheckRoute

    /** Ship, where a release is built. */
    data object OpenShip : CheckRoute

    /** Font info, where names, metrics and the licence lines are set. */
    data object OpenFontInfo : CheckRoute
}

/** An action offered under a finding: the words on the link (without the trailing ›) and where it goes. */
data class CheckAction(
    val label: String,
    val route: CheckRoute,
)

/**
 * One check's result.
 *
 * [title] is the mono title, [detail] the small line beside it, [summary] the one plain sentence that
 * quotes this project's own numbers, [reviewerQuote] what a Google Fonts reviewer actually wrote when
 * this went wrong (only where one is on record), [action] the link under it, and [raw] the same finding
 * as plain lines, for the Raw view. [basis] and [basisNote] say what the thresholds rest on.
 */
data class CheckItem(
    val id: String,
    val group: CheckGroup,
    val title: String,
    val detail: String?,
    val severity: Severity,
    val summary: String,
    val reviewerQuote: String?,
    val action: CheckAction?,
    val basis: Basis,
    val basisNote: String,
    val raw: String,
)

/** How many items fall in each [Severity]. */
data class CheckCounts(
    val fail: Int,
    val warn: Int,
    val pass: Int,
    val notMeasured: Int,
) {
    val total: Int get() = fail + warn + pass + notMeasured

    /** The report's one-line tally: `4 fail · 4 warn · 6 pass`, with `· 1 not measured` only when there is one. */
    fun line(): String =
        listOfNotNull(
            "$fail fail",
            "$warn warn",
            "$pass pass",
            "$notMeasured not measured".takeIf { notMeasured > 0 },
        ).joinToString(" · ")

    /** The severity bar as fractions of its width: fail solid, warn hatched, the rest plain. */
    fun bar(): SeverityBar =
        if (total == 0) {
            SeverityBar(0.0, 0.0, 0.0)
        } else {
            SeverityBar(fail.toDouble() / total, warn.toDouble() / total, (pass + notMeasured).toDouble() / total)
        }
}

/** The widths of the severity bar's three runs as fractions of the whole (they sum to 1, or all 0 for an empty report). */
data class SeverityBar(
    val solid: Double,
    val hatched: Double,
    val plain: Double,
)

/** One group of the report with its items in report order. */
data class CheckGroupItems(
    val group: CheckGroup,
    val items: List<CheckItem>,
)

/**
 * The Check report: Typewright's fourteen layer-two checks over a project's UFO, in the order a reviewer
 * attacks them (see [ReviewOrder]), the node-economy judgment they share ([economy], null when the
 * project has no class to be judged by), and, on a computer where layer one has run, that tool's report
 * ([layerOne]).
 *
 * The counts and the bar are worked out from [items] every time they are asked for, never stored, so
 * they cannot disagree with the items.
 */
data class CheckReport(
    val items: List<CheckItem>,
    val economy: NodeEconomyReport?,
    val style: CheckStyle?,
    val layerOne: LayerOneReport? = null,
) {
    val counts: CheckCounts
        get() =
            CheckCounts(
                fail = items.count { it.severity == Severity.FAIL },
                warn = items.count { it.severity == Severity.WARN },
                pass = items.count { it.severity == Severity.PASS },
                notMeasured = items.count { it.severity == Severity.NOT_MEASURED },
            )

    /** The items by group, groups in report order, empty groups left out. */
    val groups: List<CheckGroupItems>
        get() =
            CheckGroup.entries.mapNotNull { group ->
                items.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { CheckGroupItems(group, it) }
            }

    /** This report with layer one's results attached (or replaced). */
    fun withLayerOne(report: LayerOneReport?): CheckReport = copy(layerOne = report)

    /** The item with [id], or null. */
    fun item(id: String): CheckItem? = items.firstOrNull { it.id == id }
}

/**
 * The style class a project's letters are judged against, as the report needs it: its atlas [key], its
 * plain [name] as it reads inside a sentence (`geometric sans`), and whether the app [heuristic]ally read
 * it off the letters rather than the person declaring or confirming it.
 */
data class CheckStyle(
    val key: String,
    val name: String,
    val heuristic: Boolean,
)

/**
 * What the project says about where its outlines came from, when it says anything: it began as an
 * imported compiled font ([fromCompiledFont]), how many outline glyphs are still as imported
 * ([untouchedGlyphs]) of the [outlineGlyphs] it has, and the font file's name when known.
 */
data class ImportProvenance(
    val fromCompiledFont: Boolean,
    val untouchedGlyphs: Int,
    val outlineGlyphs: Int,
    val sourceFileName: String? = null,
)

/** The names of another master in the project, for the uniqueness check. */
data class MasterNames(
    val familyName: String?,
    val styleName: String?,
)
