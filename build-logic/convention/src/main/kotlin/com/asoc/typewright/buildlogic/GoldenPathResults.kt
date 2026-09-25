// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.buildlogic

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** One `GoldenPathTest` testcase's outcome, boiled down to what `golden-path/README.md`'s rule needs. */
internal enum class StepStatus { PASS, NOT_BUILT, FAIL, ERROR, SKIPPED }

/** One recognised testcase from a `GoldenPathTest` JUnit XML report: its step [id], [status] and [message]. */
internal data class StepResult(
    val id: String,
    val status: StepStatus,
    val message: String,
)

/** Every step id `GoldenPathTest` counts toward "N/7"; step 1 needs both `1a` and `1b`. */
internal val GOLDEN_PATH_STEP_IDS = listOf("1a", "1b", "2", "3", "4", "5", "6", "7")

/** The chain test ids; they never count toward the N/7 total (`PROMPTS_V1.md` P10 step 6's own design). */
internal val GOLDEN_PATH_CHAIN_IDS = listOf("chain-open", "chain-scan")

/** Every id `golden-path/passing-steps.txt` may legally name. */
internal val GOLDEN_PATH_ALL_IDS = GOLDEN_PATH_STEP_IDS + GOLDEN_PATH_CHAIN_IDS

/** The golden-path step number (1-7) each id belongs to, and the label its summary row carries. */
internal val GOLDEN_PATH_STEP_LABELS =
    linkedMapOf(
        "1" to ("Open/New" to listOf("1a", "1b")),
        "2" to ("Capture/Trace" to listOf("2")),
        "3" to ("Draw/Space" to listOf("3")),
        "4" to ("Check" to listOf("4")),
        "5" to ("Compile" to listOf("5")),
        "6" to ("Ship" to listOf("6")),
        "7" to ("Close/Reopen" to listOf("7")),
    )

private val STEP_NAME_REGEX = Regex("^step(\\d)([a-z]?)[A-Z]")
private val CHAIN_NAME_REGEX = Regex("^chain(Open|Scan)$")

/**
 * The golden-path id a `GoldenPathTest` method named [testName] belongs to (`"1a"`, `"chain-open"`,
 * ...), or `null` when [testName] matches neither pattern -- a test this reader does not recognise
 * as part of the golden path at all, so it is left out of the summary and the ratchet rather than
 * guessed at.
 */
internal fun stepIdForTestName(testName: String): String? {
    CHAIN_NAME_REGEX.find(testName)?.let { return "chain-" + it.groupValues[1].lowercase() }
    STEP_NAME_REGEX.find(testName)?.let { return it.groupValues[1] + it.groupValues[2] }
    return null
}

/**
 * Parses every `TEST-*GoldenPathTest.xml` file directly inside [dir] (a `Test` task's own
 * `junitXml` output directory) into one [StepResult] per testcase [stepIdForTestName] recognises.
 * A `<testcase>` with none of `<failure>`/`<error>`/`<skipped>` is [StepStatus.PASS]; a `<failure>`
 * whose `type` ends with `StepNotBuilt` is [StepStatus.NOT_BUILT], any other `<failure>` is
 * [StepStatus.FAIL], an `<error>` is [StepStatus.ERROR], and a `<skipped>` is [StepStatus.SKIPPED].
 */
internal fun parseGoldenPathResults(dir: File): List<StepResult> {
    val files =
        dir
            .listFiles { file -> file.isFile && file.name.startsWith("TEST-") && file.name.endsWith("GoldenPathTest.xml") }
            ?.sortedBy { it.name }
            .orEmpty()
    val factory = DocumentBuilderFactory.newInstance()
    val results = mutableListOf<StepResult>()
    for (file in files) {
        val document = factory.newDocumentBuilder().parse(file)
        val testcases = document.getElementsByTagName("testcase")
        for (i in 0 until testcases.length) {
            val testcase = testcases.item(i) as Element
            val id = stepIdForTestName(testcase.getAttribute("name")) ?: continue
            results += StepResult(id, statusOf(testcase), messageOf(testcase))
        }
    }
    return results
}

private fun statusOf(testcase: Element): StepStatus {
    firstChildElement(testcase, "failure")?.let { failure ->
        return if (failure.getAttribute("type").endsWith("StepNotBuilt")) StepStatus.NOT_BUILT else StepStatus.FAIL
    }
    if (firstChildElement(testcase, "error") != null) return StepStatus.ERROR
    if (firstChildElement(testcase, "skipped") != null) return StepStatus.SKIPPED
    return StepStatus.PASS
}

// Gradle's own JUnit XML writer sets a <failure>/<error>'s `message` attribute from the
// Throwable's own `toString()` ("com.asoc.typewright.goldenpath.StepNotBuilt: Step 1a ... not
// built: ..."), not its bare `.message` -- stripping a single leading fully-qualified-class-name
// prefix here, once, is what keeps notBuilt()'s and ChainFailure's own carefully-worded messages
// (and CHAIN_FAILURE_REGEX below, which parses a [ChainFailure]'s message) readable rather than
// duplicating the class name into every summary row and README example.
private val EXCEPTION_TO_STRING_PREFIX_REGEX = Regex("""^[A-Za-z_][\w$]*(?:\.[A-Za-z_][\w$]*)+:\s""")

private fun messageOf(testcase: Element): String {
    val child = firstChildElement(testcase, "failure") ?: firstChildElement(testcase, "error") ?: firstChildElement(testcase, "skipped")
    val fromAttribute = child?.getAttribute("message")
    val raw = if (!fromAttribute.isNullOrBlank()) fromAttribute else child?.textContent?.trim().orEmpty()
    return raw.replaceFirst(EXCEPTION_TO_STRING_PREFIX_REGEX, "")
}

private fun firstChildElement(
    parent: Element,
    tagName: String,
): Element? {
    val children = parent.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node is Element && node.tagName == tagName) return node
    }
    return null
}

/** `golden-path/passing-steps.txt`'s ids: one per line, `#` starts a comment, blank lines ignored. */
internal fun readPassingSteps(file: File): List<String> =
    file
        .readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }

private val CHAIN_FAILURE_REGEX = Regex("""^chain \((open|scan)\): reached step (\d+); stopped at step (\S+): (.*)$""")

/** One summary row's status word and how urgently it should win a tie over the id's siblings (lower wins). */
private fun statusWord(result: StepResult?): Pair<Int, String> =
    when (result?.status) {
        null -> 2 to "MISSING"
        StepStatus.FAIL -> 0 to "FAIL"
        StepStatus.ERROR -> 1 to "ERROR"
        StepStatus.NOT_BUILT -> 3 to "NOT BUILT"
        StepStatus.SKIPPED -> 4 to "SKIPPED"
        StepStatus.PASS -> 5 to "PASS"
    }

/**
 * The golden-path summary: `"golden path: N/7 steps pass"`, one row per step naming every sub-id's
 * own message (`"1a: <message>; 1b: <message>"` for step 1, which needs both to pass; a bare
 * `<message>` for every other step, which has one id), then one line per chain test. A step's row
 * status is its worst sub-id's ([statusWord]'s ordering: FAIL, then ERROR, then MISSING, then
 * NOT BUILT, then SKIPPED, then PASS); N counts only steps whose every sub-id is [StepStatus.PASS].
 */
internal fun buildGoldenPathSummary(results: List<StepResult>): String {
    val byId = results.associateBy { it.id }
    var passCount = 0
    val stepLines = mutableListOf<String>()
    for ((stepNumber, labelAndIds) in GOLDEN_PATH_STEP_LABELS) {
        val (label, ids) = labelAndIds
        val subResults = ids.map { id -> id to byId[id] }
        if (subResults.all { (_, result) -> result?.status == StepStatus.PASS }) passCount++
        val status = subResults.map { (_, result) -> statusWord(result) }.minByOrNull { it.first }!!.second
        val message =
            subResults.joinToString("; ") { (id, result) ->
                val text =
                    when {
                        result == null -> "no testcase"
                        result.status == StepStatus.PASS -> "passes"
                        else -> result.message
                    }
                if (ids.size > 1) "$id: $text" else text
            }
        stepLines += "  %-2s %-14s %-10s %s".format(stepNumber, label, status, message).trimEnd()
    }

    val chainLines =
        GOLDEN_PATH_CHAIN_IDS.map { chainId ->
            val word = chainId.removePrefix("chain-")
            val prefix = "chain ($word)"
            val result = byId[chainId]
            when {
                result == null -> {
                    "  $prefix: no testcase"
                }

                result.status == StepStatus.PASS -> {
                    "  $prefix: PASS"
                }

                else -> {
                    val match = CHAIN_FAILURE_REGEX.find(result.message)
                    if (match != null) {
                        "  $prefix: reached step ${match.groupValues[2]} — ${match.groupValues[4]}"
                    } else {
                        "  $prefix: ${result.message}"
                    }
                }
            }
        }

    return (listOf("golden path: $passCount/7 steps pass") + stepLines + chainLines).joinToString("\n") + "\n"
}
