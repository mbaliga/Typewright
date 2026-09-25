// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa

/**
 * Layer one of the quality gate (brief §12): the googlefonts profile a real QA tool (Fontspector,
 * or Fontbakery as a desktop extra — docs/ARCHITECTURE_REVIEW.md section 3 `:qa`, decision 2)
 * runs against a **compiled** font binary. This is deliberately not a [dev.aarso.typewright.core.font.ufo.UfoProject]
 * input: every check either tool runs (name-table entries, OS/2 fields, GDEF/GPOS coverage,
 * glyf/loca consistency, ...) inspects binary font tables that only exist after compilation, and
 * the architecture review is explicit that only layer *two* — Typewright's own checks, in this
 * same package — must measure the UFO and never the binary (section 3 `:qa`, risk 2; section 6
 * decision 3). A caller compiles the project first (`compile`'s `CompileBackend`, out of this
 * module's scope) and passes the resulting bytes here.
 *
 * The bridge to a real tool needs a runtime `qa` cannot have as a pure module
 * (docs/OPEN_QUESTIONS.md item 7): this interface lives in `commonMain`, with `expect`/`actual`
 * platform implementations exactly like `compile`'s `CompileBackend` — [availability] is checked
 * first and never throws, and [check] returns an honest, empty-ish [LayerOneReport] rather than
 * fabricating results when the tool cannot run (CLAUDE.md law 4: a feature that needs something
 * this environment cannot have is stubbed, not faked).
 */
interface LayerOneChecker {
    /** Short name for the UI and logs, for example "Fontspector (CLI)". */
    val name: String

    /** Whether this checker can run here, now. Never throws and never installs anything. */
    suspend fun availability(): LayerOneAvailability

    /**
     * Runs layer one against [input]. When [availability] is [LayerOneAvailability.Unavailable],
     * this still returns a [LayerOneReport] rather than throwing: a single [CheckOutcome] whose
     * [CheckOutcome.status] is [CheckStatus.SKIP] and whose message names the reason, so a caller
     * never has to special-case "no report at all" versus "an empty one".
     */
    suspend fun check(input: LayerOneInput): LayerOneReport
}

/** The compiled font layer one checks: raw bytes plus the file name a checker's CLI reports against. */
class LayerOneInput(
    val fileName: String,
    val bytes: ByteArray,
)

/** Whether a [LayerOneChecker] can run here. Mirrors `compile`'s `BackendAvailability`. */
sealed interface LayerOneAvailability {
    data object Available : LayerOneAvailability

    data class Unavailable(
        val reason: String,
    ) : LayerOneAvailability
}

/**
 * One check's outcome, in the four words brief §12's pipeline actually needs (never the box
 * check's "outlier / above / in range" — those are [dev.aarso.typewright.qa.corpus.NodeEconomyVerdict]'s
 * own words, law 5, a different vocabulary for a different kind of check).
 */
enum class CheckStatus { PASS, WARN, FAIL, SKIP }

/** One check's result: its tool-given [id], its [status], and a plain-language [message]. */
data class CheckOutcome(
    val id: String,
    val status: CheckStatus,
    val message: String,
)

/** How many of a [LayerOneReport]'s [LayerOneReport.results] fall into each [CheckStatus]. */
data class LayerOneCounts(
    val pass: Int,
    val warn: Int,
    val fail: Int,
    val skip: Int,
) {
    companion object {
        fun of(results: List<CheckOutcome>): LayerOneCounts =
            LayerOneCounts(
                pass = results.count { it.status == CheckStatus.PASS },
                warn = results.count { it.status == CheckStatus.WARN },
                fail = results.count { it.status == CheckStatus.FAIL },
                skip = results.count { it.status == CheckStatus.SKIP },
            )
    }
}

/** A [LayerOneChecker]'s full output: every [results] entry, plus the [counts] a summary card shows. */
data class LayerOneReport(
    val results: List<CheckOutcome>,
    val counts: LayerOneCounts = LayerOneCounts.of(results),
)

/** One line of raw process output, for a [LayerOneReport] whose tool ran but could not be understood. */
internal fun unavailableReport(reason: String): LayerOneReport =
    LayerOneReport(listOf(CheckOutcome(id = "layer-one/unavailable", status = CheckStatus.SKIP, message = reason)))

/** The [LayerOneChecker] for the platform this code runs on. */
expect fun platformLayerOneChecker(): LayerOneChecker
