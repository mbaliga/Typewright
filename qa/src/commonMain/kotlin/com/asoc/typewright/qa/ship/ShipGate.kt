// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.CheckRoute
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

/**
 * One fail that stops the build: where it comes from, its title, one sentence of what it found, and where a person goes to put it right
 * ([route]: the Font info value it is about, or the Check page).
 */
data class ShipBlocker(
    val source: BlockerSource,
    val id: String,
    val title: String,
    val sentence: String,
    val route: ShipRoute = ShipRoute.Check,
)

/**
 * The block rule (brief §12: "the Ship room is a checklist that produces a repository, blocked while Check has fails"): Ship stops while
 * Check has any fail, and Check's fails are layer two's, and layer one's when it has run on a compiled binary. A warn, a note or a check
 * that could not measure never stops it; a layer one that did not run (a phone, a browser, no Python) contributes nothing, because there is
 * nothing to count, and the screen says so rather than calling it a pass. **Ship also stops while Check has not run at all** ([checkRun]):
 * a report that does not exist is not a clean one.
 */
class ShipGate(
    val blockers: List<ShipBlocker>,
    /** False when there was no report to read: Check has not looked at the project yet. */
    val checkRun: Boolean = true,
) {
    /** Whether the build is stopped. */
    val blocked: Boolean get() = !checkRun || blockers.isNotEmpty()

    /** How many fails there are. */
    val failCount: Int get() = blockers.size

    /** The checklist's first line: `Check has 4 fails`, `Check has no fails`, or `Check has not run yet`. */
    fun title(): String =
        when {
            !checkRun -> "Check has not run yet"
            failCount == 0 -> "Check has no fails"
            failCount == 1 -> "Check has 1 fail"
            else -> "Check has $failCount fails"
        }

    /** The ink block under the checklist while the build is stopped: `Build is blocked while Check has fails. 4 to go.` */
    fun blockedSentence(): String =
        if (checkRun) "Build is blocked while Check has fails. $failCount to go." else "Build is blocked until Check has run."

    /** What a tap on the dimmed build circle says. */
    fun explanation(): String =
        when {
            !checkRun -> "Ship is blocked until Check has run: it has not looked at this project yet."
            blocked -> "Ship is blocked while Check has fails: ${blockers.joinToString(", ") { it.title }}. Fix them in Check."
            else -> "Nothing blocks the build."
        }
}

/**
 * The gate for [report] (layer two, with [layerOne]'s fails when a layer-one report is given or attached to the report). A null [report]
 * blocks: Check has not run. Layer one's fails are counted only when [countLayerOne] is true; a repository under the person's own licence is
 * not going to Google Fonts, so it is not stopped by that profile.
 */
fun shipGate(
    report: CheckReport?,
    layerOne: LayerOneReport? = report?.layerOne,
    countLayerOne: Boolean = true,
    embeddingStated: Boolean = false,
): ShipGate {
    if (report == null) return ShipGate(emptyList(), checkRun = false)
    val blockers = mutableListOf<ShipBlocker>()
    report.items.filter { it.severity == Severity.FAIL }.forEach {
        val route = (it.action?.route as? CheckRoute.OpenFontInfo)?.let { open -> ShipRoute.FontInfoAt(open.target) } ?: ShipRoute.Check
        blockers += ShipBlocker(BlockerSource.LAYER_TWO, it.id, it.title, firstSentence(it.summary), route)
    }
    if (countLayerOne) blockers += layerOneBlockers(layerOne, embeddingStated)
    return ShipGate(blockers)
}

/**
 * The gate for a compiled binary alone: the fails of Google Fonts' own profile on it, and nothing of layer two. [embeddingStated] is whether
 * the project states the embedding setting, which changes what the `fsType` fail says (see [shipGate]).
 */
fun binaryGate(
    layerOne: LayerOneReport,
    embeddingStated: Boolean = false,
): ShipGate = ShipGate(layerOneBlockers(layerOne, embeddingStated))

private const val FSTYPE_CHECK = "com.google.fonts/check/fstype"

/** What the `fsType` fail says when the project states the embedding setting: whose it is, and that the build left it as stated. */
const val EMBEDDING_STATED_SENTENCE: String =
    "Your project states an embedding restriction (openTypeOS2Type), which Google Fonts does not take; Typewright left it as you stated it."

private fun layerOneBlockers(
    layerOne: LayerOneReport?,
    embeddingStated: Boolean,
): List<ShipBlocker> {
    if (layerOne == null || layerOne.isUnavailable) return emptyList()
    return layerOne.results
        .filter { it.status == CheckStatus.FAIL }
        .map {
            val sentence = if (it.id == FSTYPE_CHECK && embeddingStated) EMBEDDING_STATED_SENTENCE else firstSentence(it.message)
            ShipBlocker(BlockerSource.LAYER_ONE, it.id, it.title ?: it.id, sentence, layerOneRoute(it.id))
        }
}

/**
 * Where a fail of Google Fonts' profile on the binary is put right: the Font info value that the check reads, for the checks whose ids name
 * one (the embedding setting is part of the licence; the file name is made from the family and style), and the Check page for the rest.
 * The ids are the ones Fontbakery 0.12 gives these checks.
 */
internal fun layerOneRoute(id: String): ShipRoute =
    when (id) {
        FSTYPE_CHECK, "com.google.fonts/check/name/license", "com.google.fonts/check/name/license_url" -> {
            ShipRoute.FontInfo(ShipFontInfoField.LICENCE_TEXT)
        }

        "com.google.fonts/check/font_copyright", "com.google.fonts/check/name/rfn" -> {
            ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT)
        }

        "com.google.fonts/check/canonical_filename",
        "com.google.fonts/check/name/family_and_style_max_length",
        "com.google.fonts/check/name/family_name_compliance",
        "com.google.fonts/check/name/familyname_first_char",
        "com.google.fonts/check/name/italic_names",
        "com.google.fonts/check/name/match_familyname_fullfont",
        -> {
            ShipRoute.FontInfo(ShipFontInfoField.FAMILY)
        }

        "com.google.fonts/check/vendor_id" -> {
            ShipRoute.FontInfo(ShipFontInfoField.VENDOR_ID)
        }

        else -> {
            ShipRoute.Check
        }
    }

/**
 * The first sentence of a finding's words (the first line, up to its first full stop followed by a space), cut at [LIMIT] characters at a
 * word, so that a list of fails stays a list and each fail is one line a person can take in.
 */
fun firstSentence(message: String): String {
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
