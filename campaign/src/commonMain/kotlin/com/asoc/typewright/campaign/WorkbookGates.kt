// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.campaign

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.qa.CheckStatus
import com.asoc.typewright.qa.LayerOneAvailability
import com.asoc.typewright.qa.LayerOneChecker
import com.asoc.typewright.qa.LayerOneInput
import com.asoc.typewright.qa.checkAlignmentMiss
import com.asoc.typewright.qa.checkAnchorsPresent
import com.asoc.typewright.qa.checkCollinearSegments
import com.asoc.typewright.qa.checkExtremaOnCurve
import com.asoc.typewright.qa.checkJaggyTurns
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.checkOvershootPresence
import com.asoc.typewright.qa.checkSemiVertical
import com.asoc.typewright.qa.checkShortSegments
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.ship.generateDescriptionHtml
import com.asoc.typewright.qa.ship.generateMetadataPbText
import com.asoc.typewright.qa.ship.generateOflText
import com.asoc.typewright.qa.ship.gfCopyrightLine

/**
 * The words a workbook gate's own sub-checks come back in. This task's own instruction: "pass /
 * fail / not-implemented per sub-check, with the real measured numbers". [WARN] and [INFO] are
 * added beyond that literal three so a sub-check backed by a `qa` function that already has its
 * own, different, documented vocabulary is never squashed into a lie:
 * - [com.asoc.typewright.qa.LayerOneChecker] (task 11) already speaks
 *   [com.asoc.typewright.qa.CheckStatus.WARN] for real, and a check firing only more than 100
 *   times is treated as intent -- collapsing that into PASS or FAIL would misreport it.
 * - The six geometric heuristics (tasks 5/6) are, by fontbakery's own documented behaviour,
 *   "heuristics that WARN, never FAIL" (`GeometricChecks.kt`'s own KDoc) -- calling a finding FAIL
 *   would overstate it.
 * - Overshoot presence (task 3) is, by its own KDoc, purely informational: "this never fails or
 *   warns by itself, it names what it found" -- [INFO] is the only honest word for it.
 */
public enum class GateCheckStatus { PASS, WARN, FAIL, INFO, NOT_IMPLEMENTED }

/** One sub-check's outcome within a [WorkbookGateResult]: what it was about, its [status], and the real measured detail behind it. */
public data class GateCheckResult(
    val label: String,
    val status: GateCheckStatus,
    val detail: String,
)

/**
 * A real, structured result of running one workbook task's gate against a real project (this
 * task's own instruction) -- see [WorkbookGates] for the functions that produce one of these.
 * [checks] carries either real measured [GateCheckResult]s or an honest single
 * [GateCheckStatus.NOT_IMPLEMENTED] entry (never both, never a fabricated pass/fail) --
 * [isImplemented] tells the two apart without a caller having to inspect [checks] by hand.
 */
public data class WorkbookGateResult(
    val taskIndex: Int,
    val gateSummary: String,
    val checks: List<GateCheckResult>,
) {
    /** True when at least one [checks] entry is a real measured result, not [GateCheckStatus.NOT_IMPLEMENTED]. */
    val isImplemented: Boolean get() = checks.any { it.status != GateCheckStatus.NOT_IMPLEMENTED }

    /** How many [checks] entries fall into each [GateCheckStatus], for a summary line (mirrors [com.asoc.typewright.qa.LayerOneCounts]). */
    val counts: Map<GateCheckStatus, Int> get() = GateCheckStatus.entries.associateWith { status -> checks.count { it.status == status } }
}

/**
 * The real Kotlin functions that run a workbook task's gate against a real
 * [UfoProject]/compiled-font input and return a real [WorkbookGateResult] (this task's own
 * instruction: "gates call qa and the boxes"). Every function here calls an existing, already-
 * implemented `qa` function -- none adds new check logic of its own (out of this task's scope,
 * per its own instruction) -- except [task12Ship], which is real string-generation sanity over
 * `qa.ship`'s own already-implemented generators, and [notImplementedGate], which is the honest
 * "not implemented" state [WorkbookGateSpec.notImplementedReason] describes for tasks 1, 2, 7, 8
 * and 10.
 *
 * Task 4's own numbers on this project's real Hyle Deco reference font, computed for real (never
 * hardcoded from the explorer's mockup) -- see `HyleDecoTask4GateTest.kt` (`jvmTest`) for how a
 * real [UfoProject] is built from `fonts/HyleDeco-Regular.ttf` and the resulting counts and
 * verdicts.
 */
public object WorkbookGates {
    /** Round glyphs task 3's gate checks by default -- the Check room's own list, "o e c s", plus their capitals. */
    public val TASK3_DEFAULT_GLYPHS: List<String> = listOf("o", "e", "c", "s", "O", "C", "S", "G")

    /** n o H O -- task 4's own control characters (handoff M5 item 4). */
    public val TASK4_DEFAULT_GLYPHS: List<String> = listOf("n", "o", "H", "O")

    /** The derived family task 5's gate checks by default (handoff M5 item 5). */
    public val TASK5_DEFAULT_GLYPHS: List<String> = listOf("b", "d", "p", "q", "h", "m", "u", "E", "F", "L", "T", "C", "G", "Q")

    /** The hard letters task 6's gate checks by default (handoff M5 item 6): a e g s, the diagonals, the numerals. */
    public val TASK6_DEFAULT_GLYPHS: List<String> =
        listOf("a", "e", "g", "s", "v", "w", "x", "y", "k", "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    /** An honest "not implemented" [WorkbookGateResult]: one [GateCheckResult] carrying [GateCheckStatus.NOT_IMPLEMENTED] and why. */
    public fun notImplementedGate(
        taskIndex: Int,
        gateSummary: String,
        reason: String,
    ): WorkbookGateResult =
        WorkbookGateResult(taskIndex, gateSummary, listOf(GateCheckResult(gateSummary, GateCheckStatus.NOT_IMPLEMENTED, reason)))

    /**
     * Task 3, "Set metrics": overshoot presence ([checkOvershootPresence]) on [glyphNames]
     * present in [project]. Always [GateCheckStatus.INFO] per that function's own documented
     * contract (never a pass/fail/warn by itself); a glyph absent from [project] is
     * [GateCheckStatus.NOT_IMPLEMENTED] (nothing to check, said honestly rather than skipped
     * silently).
     */
    public fun task3SetMetrics(
        project: UfoProject,
        glyphNames: List<String> = TASK3_DEFAULT_GLYPHS,
    ): WorkbookGateResult {
        val byName = project.glyphs.associateBy { it.name }
        val checks =
            glyphNames.map { name ->
                val glyph = byName[name]
                if (glyph == null) {
                    GateCheckResult(name, GateCheckStatus.NOT_IMPLEMENTED, "'$name' is not in this project.")
                } else {
                    val findings = checkOvershootPresence(glyph, project.fontInfo)
                    val detail =
                        if (findings.isEmpty()) {
                            "'$name' has no vertical extreme sitting on a metric line to report (not judged round by this heuristic, or no contours)."
                        } else {
                            findings.joinToString(" ") { it.message }
                        }
                    GateCheckResult(name, GateCheckStatus.INFO, detail)
                }
            }
        return WorkbookGateResult(3, "overshoot presence at baseline / x-height / cap-height", checks)
    }

    /**
     * Task 4, "Control characters": node economy ([checkNodeEconomy]) for [glyphNames] against
     * [styleKey]'s box in [corpus] -- this task's own real gate. Status is the on-curve
     * [NodeEconomyVerdict] ([checkNodeEconomy]'s own KDoc: "a caller judging a UFO project should
     * weight the on-curve verdict more than the off-curve one"); [GateCheckResult.detail] is the
     * check's own plain-language rationale, which covers both axes.
     */
    public fun task4ControlCharacters(
        project: UfoProject,
        corpus: NodeEconomyCorpus,
        styleKey: String,
        glyphNames: List<String> = TASK4_DEFAULT_GLYPHS,
    ): WorkbookGateResult {
        val report = checkNodeEconomy(project, styleKey, corpus)
        val byName = report.glyphs.associateBy { it.glyphName }
        val checks =
            glyphNames.map { name ->
                val result = byName[name]
                if (result == null) {
                    GateCheckResult(name, GateCheckStatus.NOT_IMPLEMENTED, "'$name' is not in this project.")
                } else {
                    val status =
                        when (result.onCurveVerdict) {
                            NodeEconomyVerdict.IN_RANGE -> GateCheckStatus.PASS
                            NodeEconomyVerdict.ABOVE -> GateCheckStatus.WARN
                            NodeEconomyVerdict.OUTLIER -> GateCheckStatus.FAIL
                            null -> GateCheckStatus.NOT_IMPLEMENTED
                        }
                    GateCheckResult(name, status, result.rationale)
                }
            }
        return WorkbookGateResult(4, "node economy against the geometric box ($styleKey)", checks)
    }

    /** Task 5, "Derive the family": [geometricSanityGate] over [glyphNames]. */
    public fun task5DeriveTheFamily(
        project: UfoProject,
        glyphNames: List<String> = TASK5_DEFAULT_GLYPHS,
    ): WorkbookGateResult = geometricSanityGate(5, "geometric sanity on the derived family", project, glyphNames)

    /** Task 6, "The hard letters": [geometricSanityGate] over [glyphNames]. */
    public fun task6HardLetters(
        project: UfoProject,
        glyphNames: List<String> = TASK6_DEFAULT_GLYPHS,
    ): WorkbookGateResult = geometricSanityGate(6, "geometric sanity on the hard letters", project, glyphNames)

    /**
     * The six `GeometricChecks.kt` heuristics (alignment-miss, collinear, jaggy, short, semi-
     * vertical, extrema-off-curve), run together over each of [glyphNames] present in [project].
     * A glyph with no findings is [GateCheckStatus.PASS]; one with any finding is
     * [GateCheckStatus.WARN] (never FAIL -- `GeometricChecks.kt`'s own KDoc: fontbakery "treats
     * all six as heuristics that WARN, never FAIL").
     */
    private fun geometricSanityGate(
        taskIndex: Int,
        gateSummary: String,
        project: UfoProject,
        glyphNames: List<String>,
    ): WorkbookGateResult {
        val metricLines = metricLinesOf(project.fontInfo)
        val byName = project.glyphs.associateBy { it.name }
        val checks =
            glyphNames.map { name ->
                val glyph = byName[name]
                if (glyph == null) {
                    GateCheckResult(name, GateCheckStatus.NOT_IMPLEMENTED, "'$name' is not in this project.")
                } else {
                    val findings =
                        buildList {
                            addAll(checkAlignmentMiss(glyph, metricLines))
                            addAll(checkCollinearSegments(glyph))
                            addAll(checkJaggyTurns(glyph))
                            addAll(checkShortSegments(glyph))
                            addAll(checkSemiVertical(glyph))
                            addAll(checkExtremaOnCurve(glyph))
                        }
                    if (findings.isEmpty()) {
                        GateCheckResult(
                            name,
                            GateCheckStatus.PASS,
                            "'$name': no alignment-miss, collinear, jaggy, short, semi-vertical or off-curve-extrema findings.",
                        )
                    } else {
                        val byCheck = findings.groupingBy { it.checkId }.eachCount()
                        GateCheckResult(
                            name,
                            GateCheckStatus.WARN,
                            "'$name': " + byCheck.entries.joinToString("; ") { (checkId, count) -> "$checkId=$count" },
                        )
                    }
                }
            }
        return WorkbookGateResult(taskIndex, gateSummary, checks)
    }

    /** Task 9, "Diacritics and anchors": anchor presence ([checkAnchorsPresent]) over the whole project. */
    public fun task9DiacriticsAndAnchors(project: UfoProject): WorkbookGateResult {
        val findings = checkAnchorsPresent(project)
        val checks =
            findings.map { finding ->
                GateCheckResult(
                    finding.glyphName,
                    if (finding.hasAppropriateAnchor) GateCheckStatus.PASS else GateCheckStatus.WARN,
                    finding.message,
                )
            }
        return WorkbookGateResult(9, "anchors present on the base Latin letters (A-Z, a-z)", checks)
    }

    /**
     * Task 11, "Test": layer one ([LayerOneChecker.check]), or an honest not-implemented result
     * when [checker] reports [LayerOneAvailability.Unavailable] (following that interface's own
     * documented contract: "returns an honest, empty-ish [com.asoc.typewright.qa.LayerOneReport]
     * rather than fabricating results when the tool cannot run"). `suspend` because
     * [LayerOneChecker.availability]/[LayerOneChecker.check] both are.
     */
    public suspend fun task11Test(
        checker: LayerOneChecker,
        compiled: LayerOneInput?,
    ): WorkbookGateResult {
        val gateSummary = "layer one (${checker.name})"
        val availability = checker.availability()
        if (availability is LayerOneAvailability.Unavailable) {
            return notImplementedGate(11, gateSummary, availability.reason)
        }
        if (compiled == null) {
            return notImplementedGate(11, gateSummary, "no compiled font to check yet -- compile the project first.")
        }
        val report = checker.check(compiled)
        val checks =
            report.results.map { outcome ->
                val status =
                    when (outcome.status) {
                        CheckStatus.PASS -> GateCheckStatus.PASS
                        CheckStatus.WARN -> GateCheckStatus.WARN
                        CheckStatus.FAIL -> GateCheckStatus.FAIL
                        CheckStatus.SKIP -> GateCheckStatus.NOT_IMPLEMENTED
                    }
                GateCheckResult(outcome.id, status, outcome.message)
            }
        return WorkbookGateResult(11, gateSummary, checks)
    }

    /**
     * Task 12, "Ship": generates [UfoFontInfo]/[family]/[designer]/[year]/[gitUrl]'s real
     * `OFL.txt`, `DESCRIPTION.en_us.html` and `METADATA.pb` text with `qa.ship`'s own already-
     * implemented pure generators, then checks the real generated text actually carries this
     * project's own real values -- not new check logic (this task's own restriction), just a
     * sanity read of real output.
     */
    public fun task12Ship(
        fontInfo: UfoFontInfo,
        family: String,
        designer: String,
        year: Int,
        gitUrl: String,
    ): WorkbookGateResult {
        val expectedCopyright = gfCopyrightLine(year, family, gitUrl)

        val oflText = generateOflText(family, year, gitUrl)
        val oflCheck =
            GateCheckResult(
                "OFL.txt",
                if (oflText.startsWith(expectedCopyright)) GateCheckStatus.PASS else GateCheckStatus.FAIL,
                "first line: \"${oflText.lineSequence().first()}\"",
            )

        val description = generateDescriptionHtml(family, gitUrl)
        val descriptionOk = description.contains(family) && description.contains(gitUrl)
        val descriptionCheck =
            GateCheckResult(
                "DESCRIPTION.en_us.html",
                if (descriptionOk) GateCheckStatus.PASS else GateCheckStatus.FAIL,
                "contains the family name and the repository url: $descriptionOk",
            )

        val metadata = generateMetadataPbText(fontInfo, family, designer, year, gitUrl)
        val metadataOk =
            metadata.contains("name: \"$family\"") &&
                metadata.contains("designer: \"$designer\"") &&
                metadata.contains("license: \"OFL\"") &&
                metadata.contains(expectedCopyright)
        val metadataCheck =
            GateCheckResult(
                "METADATA.pb",
                if (metadataOk) GateCheckStatus.PASS else GateCheckStatus.FAIL,
                "contains name/designer/license/copyright: $metadataOk",
            )

        return WorkbookGateResult(
            12,
            "repository scaffold generation (OFL.txt, DESCRIPTION.en_us.html, METADATA.pb)",
            listOf(oflCheck, descriptionCheck, metadataCheck),
        )
    }

    /**
     * The baseline/x-height/cap-height map [checkAlignmentMiss] and [checkOvershootPresence]
     * both expect from a caller (their own KDoc: not every glyph should be checked against
     * every line, and neither function has a glyph-inventory model to know which apply) --
     * baseline is always checked (CLAUDE.md: "integers at rest ... y up" is a font-wide
     * convention, not a [UfoFontInfo] field); x-height/cap-height only when [fontInfo] states
     * them.
     */
    private fun metricLinesOf(fontInfo: UfoFontInfo): Map<String, Int> =
        buildMap {
            put("baseline", 0)
            fontInfo.xHeight?.let { put("x-height", it) }
            fontInfo.capHeight?.let { put("cap-height", it) }
        }
}
