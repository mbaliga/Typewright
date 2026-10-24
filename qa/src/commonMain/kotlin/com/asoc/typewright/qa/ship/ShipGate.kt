// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.Severity

/** Which of the two layers of Check a [ShipBlocker] comes from. */
enum class BlockerSource(
    val label: String,
) {
    /** Typewright's own fourteen checks over the project's UFO. */
    LAYER_TWO("Check"),

    /** Google Fonts' own profile, run by Fontbakery or Fontspector on a compiled binary. */
    LAYER_ONE("Layer one"),
}

/** One fail that stops the build: where it comes from, its title, and one sentence of what it found. */
data class ShipBlocker(
    val source: BlockerSource,
    val id: String,
    val title: String,
    val sentence: String,
)

/**
 * The block rule (brief §12: "the Ship room is a checklist that produces a repository, blocked while Check has fails"): Ship stops while
 * Check has any fail, and Check's fails are layer two's, and layer one's when it has run on a compiled binary. A warn, a note or a check
 * that could not measure never stops it; a layer one that did not run (a phone, a browser, no Python) contributes nothing, because there is
 * nothing to count, and the screen says so rather than calling it a pass.
 */
class ShipGate(
    val blockers: List<ShipBlocker>,
) {
    /** Whether the build is stopped. */
    val blocked: Boolean get() = blockers.isNotEmpty()

    /** How many fails there are. */
    val failCount: Int get() = blockers.size

    /** The checklist's first line: `Check has 4 fails`, or `Check has no fails`. */
    fun title(): String =
        when (failCount) {
            0 -> "Check has no fails"
            1 -> "Check has 1 fail"
            else -> "Check has $failCount fails"
        }

    /** The ink block under the checklist while the build is stopped: `Build is blocked while Check has fails. 4 to go.` */
    fun blockedSentence(): String = "Build is blocked while Check has fails. $failCount to go."

    /** What a tap on the dimmed build circle says. */
    fun explanation(): String =
        if (blocked) {
            "Ship is blocked while Check has fails: ${blockers.joinToString(", ") { it.title }}. Fix them in Check."
        } else {
            "Nothing blocks the build."
        }
}

/** The gate for [report] (layer two, with [layerOne]'s fails when a layer-one report is given or attached to the report). */
fun shipGate(
    report: CheckReport?,
    layerOne: LayerOneReport? = report?.layerOne,
): ShipGate {
    val blockers = mutableListOf<ShipBlocker>()
    report?.items?.filter { it.severity == Severity.FAIL }?.forEach {
        blockers += ShipBlocker(BlockerSource.LAYER_TWO, it.id, it.title, firstSentence(it.summary))
    }
    if (layerOne != null && !layerOne.isUnavailable) {
        layerOne.results.filter { it.status == CheckStatus.FAIL }.forEach {
            blockers += ShipBlocker(BlockerSource.LAYER_ONE, it.id, it.title ?: it.id, firstSentence(it.message))
        }
    }
    return ShipGate(blockers)
}

/**
 * The first sentence of a finding's words (the first line, up to its first full stop followed by a space), cut at [LIMIT] characters at a
 * word, so that a list of fails stays a list and each fail is one line a person can take in.
 */
internal fun firstSentence(message: String): String {
    val line =
        message
            .trim()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
    val stop = SENTENCE_END.find(line)
    val sentence = if (stop != null) line.substring(0, stop.range.first + 1) else line
    if (sentence.length <= LIMIT) return sentence
    val cut = sentence.take(LIMIT).substringBeforeLast(' ', sentence.take(LIMIT))
    return cut.trimEnd('.', ',', ';', ':') + "…"
}

private const val LIMIT = 160
private val SENTENCE_END = Regex("""[.!?](?=\s)""")
