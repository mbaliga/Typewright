// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

/**
 * Layer one of the quality gate (brief §12): the googlefonts profile a real QA tool (Fontspector,
 * or Fontbakery as a desktop extra — docs/ARCHITECTURE_REVIEW.md section 3 `:qa`, decision 2)
 * runs against a **compiled** font binary. This is deliberately not a [com.asoc.typewright.core.font.ufo.UfoProject]
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

    /**
     * Which tool would run, at which version, and where (for the Check page's runtime line), or null
     * when none can run here or the checker cannot say. Never throws.
     */
    suspend fun runtime(): LayerOneRuntime? = null
}

/** The tool a [LayerOneChecker] runs: its [tool] name (`Fontbakery`), its [version] when it reports one, and [where] it runs, in words. */
data class LayerOneRuntime(
    val tool: String,
    val version: String?,
    val where: String,
) {
    /** The runtime line of the Check page: `Fontbakery 0.12.10 on this computer`. */
    val description: String get() = listOfNotNull(tool, version).joinToString(" ") + " " + where
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
 * One check's outcome, in the words brief §12's pipeline actually needs (never the box
 * check's "outlier / above / in range" — those are [com.asoc.typewright.qa.corpus.NodeEconomyVerdict]'s
 * own words, law 5, a different vocabulary for a different kind of check). [INFO] is a note the tool
 * makes about the font that is not a problem (Fontbakery's `INFO`): it is counted on its own, never
 * as a warning.
 */
enum class CheckStatus { PASS, WARN, FAIL, SKIP, INFO }

/**
 * One check's result: its tool-given [id], its [status], and a plain-language [message]. [toolStatus] is the
 * tool's own word when it has more than [CheckStatus] does (Fontbakery's `FATAL` or `ERROR`, both [CheckStatus.FAIL]), so the
 * raw view can show it; [title] is the tool's one-line description of the check, when it gives one.
 */
data class CheckOutcome(
    val id: String,
    val status: CheckStatus,
    val message: String,
    val toolStatus: String? = null,
    val title: String? = null,
)

/** How many of a [LayerOneReport]'s [LayerOneReport.results] fall into each [CheckStatus]. */
data class LayerOneCounts(
    val pass: Int,
    val warn: Int,
    val fail: Int,
    val skip: Int,
    val info: Int = 0,
) {
    companion object {
        fun of(results: List<CheckOutcome>): LayerOneCounts =
            LayerOneCounts(
                pass = results.count { it.status == CheckStatus.PASS },
                warn = results.count { it.status == CheckStatus.WARN },
                fail = results.count { it.status == CheckStatus.FAIL },
                skip = results.count { it.status == CheckStatus.SKIP },
                info = results.count { it.status == CheckStatus.INFO },
            )
    }
}

/**
 * A [LayerOneChecker]'s full output: every [results] entry, plus the [counts] a summary card shows, and the
 * [tool] that produced them when one ran. [notes] are plain sentences about how the report came to be, for
 * the page to print under the counts: that the first tool's output could not be read and another was used.
 */
data class LayerOneReport(
    val results: List<CheckOutcome>,
    val counts: LayerOneCounts = LayerOneCounts.of(results),
    val tool: LayerOneRuntime? = null,
    val notes: List<String> = emptyList(),
) {
    /** True for the one skipped outcome [unavailableReport] makes: no tool produced anything to read. */
    val isUnavailable: Boolean get() = results.size == 1 && results.single().id == LAYER_ONE_UNAVAILABLE_ID
}

/** The id of the single skipped outcome that stands for a layer one that did not run. */
const val LAYER_ONE_UNAVAILABLE_ID: String = "layer-one/unavailable"

/** One line of raw process output, for a [LayerOneReport] whose tool ran but could not be understood. */
internal fun unavailableReport(reason: String): LayerOneReport =
    LayerOneReport(listOf(CheckOutcome(id = LAYER_ONE_UNAVAILABLE_ID, status = CheckStatus.SKIP, message = reason)))

/** The [LayerOneChecker] for the platform this code runs on. */
expect fun platformLayerOneChecker(): LayerOneChecker
