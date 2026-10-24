// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory

/** The environment variable that names the Python to use first, shared with the build backend. */
internal const val PYTHON_ENV_VARIABLE = "TYPEWRIGHT_PYTHON"

private const val DEFAULT_PYTHON = "python3"

/** The command that installs Fontbakery with everything its Google Fonts profile imports. */
internal const val FONTBAKERY_INSTALL_COMMAND = "pip install \"fontbakery[googlefonts]\""

/** A Python that has Fontbakery and its Google Fonts profile: the interpreter as it was named, and Fontbakery's [version]. */
internal class FontbakeryPython(
    val python: String,
    val version: String?,
)

/** The outcome of looking for Fontbakery: a [Found] one, or [Missing] with the sentence that says why and what to install. */
internal sealed interface FontbakeryLookup {
    class Found(
        val python: FontbakeryPython,
    ) : FontbakeryLookup

    class Missing(
        val reason: String,
    ) : FontbakeryLookup
}

/**
 * Layer one through Fontbakery's `check-googlefonts` profile, run by the computer's own Python:
 * `TYPEWRIGHT_PYTHON` first, then `python3` on PATH, the first of them that runs. It looks for
 * Fontbakery in that Python without installing anything: [availability] says plainly when there is no
 * Python, when Fontbakery is not installed, and when it is but its Google Fonts extras (the packages the
 * profile imports) are not, each with the pip command that fixes it.
 *
 * [check] writes the compiled font to a fresh temporary directory, runs
 * `python -m fontbakery check-googlefonts --skip-network --json …` on it (`--skip-network`: a check the
 * person started must not quietly use the network), reads the JSON report into [CheckOutcome]s with
 * Fontbakery's own check ids and messages, and deletes the directory. Fontbakery exits with 1 when any
 * check fails, so the exit code is not read as a failure to run; a missing or unreadable report is. The
 * call blocks the thread it is on, so a caller runs it on a background thread; interrupting that thread
 * stops Fontbakery and everything it started.
 */
class FontbakeryCliChecker internal constructor(
    private val env: (String) -> String?,
    private val runner: CommandRunner,
    private val timeoutMillis: Long,
) : LayerOneChecker {
    constructor() : this(System::getenv, RealCommandRunner, CHECK_TIMEOUT_MILLIS)

    override val name: String = "Fontbakery (system Python)"

    override suspend fun availability(): LayerOneAvailability =
        when (val found = locate()) {
            is FontbakeryLookup.Found -> LayerOneAvailability.Available
            is FontbakeryLookup.Missing -> LayerOneAvailability.Unavailable(found.reason)
        }

    override suspend fun runtime(): LayerOneRuntime? =
        (locate() as? FontbakeryLookup.Found)?.let { LayerOneRuntime("Fontbakery", it.python.version, "on this computer") }

    override suspend fun check(input: LayerOneInput): LayerOneReport {
        val python =
            when (val found = locate()) {
                is FontbakeryLookup.Missing -> return unavailableReport(found.reason)
                is FontbakeryLookup.Found -> found.python
            }
        val tool = LayerOneRuntime("Fontbakery", python.version, "on this computer")
        val work = createTempDirectory("fontbakery-").toFile()
        try {
            val fontFile = File(work, sanitizeFileName(input.fileName))
            fontFile.writeBytes(input.bytes)
            val jsonFile = File(work, "report.json")
            val command =
                listOf(
                    python.python,
                    "-m",
                    "fontbakery",
                    "check-googlefonts",
                    "--quiet",
                    "--no-progress",
                    "--no-colors",
                    "--skip-network",
                    "--json",
                    jsonFile.absolutePath,
                    fontFile.absolutePath,
                )
            val ran = runner.run(command, timeoutMillis)
            if (ran.problem != null) return unavailableReport("Fontbakery ${ran.problem}.")
            if (!jsonFile.isFile) {
                return unavailableReport(
                    "Fontbakery ran (exit code ${ran.exitCode}) but wrote no report. " + "What it printed: ${tail(ran.output)}",
                )
            }
            val results = runCatching { parseFontbakeryReport(Json.parseToJsonElement(jsonFile.readText())) }.getOrElse { emptyList() }
            if (results.isEmpty()) {
                return unavailableReport("Fontbakery's report held no check results. What it printed: ${tail(ran.output)}")
            }
            return LayerOneReport(results, tool = tool)
        } finally {
            work.deleteRecursively()
        }
    }

    /** Where Fontbakery was last found, and under which `TYPEWRIGHT_PYTHON`, so the probes (about two seconds) are not repeated; a miss is never kept. */
    @Volatile
    private var lastFound: Pair<String?, FontbakeryLookup.Found>? = null

    /** Looks for a Python that runs and has Fontbakery's Google Fonts profile; [FontbakeryLookup.Missing] says why not. */
    private fun locate(): FontbakeryLookup {
        val named = env(PYTHON_ENV_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
        lastFound?.let { (key, found) -> if (key == named) return found }
        return locateAnew(named).also { if (it is FontbakeryLookup.Found) lastFound = named to it }
    }

    private fun locateAnew(named: String?): FontbakeryLookup {
        val notes = mutableListOf<String>()
        for (candidate in listOfNotNull(named, DEFAULT_PYTHON).distinct()) {
            val label = if (candidate == named) "$PYTHON_ENV_VARIABLE ($candidate)" else candidate
            val python = runner.run(listOf(candidate, "-c", "import sys; print('python ok', sys.version.split()[0])"), PROBE_TIMEOUT_MILLIS)
            val pythonVersion =
                python.output
                    .lineSequence()
                    .firstOrNull { it.startsWith("python ok ") }
                    ?.removePrefix("python ok ")
                    ?.trim()
            if (!python.succeeded || pythonVersion == null) {
                notes += if (python.problem != null) "$label ${python.problem}." else "$label ran but did not answer like Python 3."
                continue
            }
            return fontbakeryIn(candidate, pythonVersion)
        }
        return FontbakeryLookup.Missing(
            (
                listOf("Fontbakery was not found: Python 3 was not found on this computer.") + notes +
                    "Install Python 3, then run: $FONTBAKERY_INSTALL_COMMAND"
            ).joinToString(" "),
        )
    }

    private fun fontbakeryIn(
        python: String,
        pythonVersion: String,
    ): FontbakeryLookup {
        val whichPython = "Python $pythonVersion ($python)"
        val version = runner.run(listOf(python, "-m", "fontbakery", "--version"), PROBE_TIMEOUT_MILLIS)
        val versionText =
            version.output
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { VERSION.matches(it) }
        if (!version.succeeded || versionText == null) {
            return FontbakeryLookup.Missing("Fontbakery is not installed in $whichPython. Install it with: ${installCommand(python)}")
        }
        // The Google Fonts profile imports packages Fontbakery does not require; list its checks to prove it loads.
        val profile = runner.run(listOf(python, "-m", "fontbakery", "check-googlefonts", "--list-checks"), PROBE_TIMEOUT_MILLIS)
        if (!profile.succeeded) {
            val module = MISSING_MODULE.find(profile.output)?.groupValues?.get(1)
            val cause = if (module != null) "the package \"$module\" is missing" else "its checks could not be loaded"
            return FontbakeryLookup.Missing(
                "Fontbakery $versionText is installed in $whichPython, but the Google Fonts profile cannot run: $cause. " +
                    "Install what it needs with: ${installCommand(python)}",
            )
        }
        return FontbakeryLookup.Found(FontbakeryPython(python, versionText))
    }

    private fun installCommand(python: String): String =
        if (python == DEFAULT_PYTHON) FONTBAKERY_INSTALL_COMMAND else "$python -m $FONTBAKERY_INSTALL_COMMAND"

    private fun tail(output: String): String = output.trim().takeLast(RAW_OUTPUT_LIMIT).ifBlank { "(nothing)" }

    private companion object {
        const val PROBE_TIMEOUT_MILLIS = 60_000L
        const val CHECK_TIMEOUT_MILLIS = 15L * 60L * 1000L
        const val RAW_OUTPUT_LIMIT = 1_500
        val VERSION = Regex("""\d+(\.\d+)+\S*""")
        val MISSING_MODULE = Regex("""No module named '([^']+)'""")
    }
}

/** A safe-enough basename for a temporary file: strips any path separators out of a caller-given name. */
private fun sanitizeFileName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
    return base.ifBlank { "font.ttf" }
}

/**
 * Fontbakery's `--json` report as [CheckOutcome]s, one per check in the report's own order. A check's id
 * is Fontbakery's (`com.google.fonts/check/gpos_kerning_info`), its [CheckOutcome.title] is Fontbakery's
 * description of it, its message is what the check logged, and its status is Fontbakery's: `PASS`, `WARN`,
 * `FAIL` and `SKIP` map to themselves, `INFO` (a note, not a problem) to a warning and `ERROR` or `FATAL`
 * (the check broke, or found the font unusable) to a failure, with [CheckOutcome.toolStatus] keeping the
 * word the tool used. A check with a word this reader does not know is a skip, never a pass.
 */
internal fun parseFontbakeryReport(report: JsonElement): List<CheckOutcome> {
    val sections = (report as? JsonObject)?.get("sections") as? JsonArray ?: return emptyList()
    val outcomes = mutableListOf<CheckOutcome>()
    for (section in sections) {
        val checks = (section as? JsonObject)?.get("checks") as? JsonArray ?: continue
        for (check in checks) {
            val entry = check as? JsonObject ?: continue
            val word = entry.text("result") ?: continue
            val title = entry.text("description")
            val id = checkId(entry) ?: title ?: "fontbakery/unknown-check"
            val message = (entry["logs"] as? JsonArray).orEmpty().mapNotNull { logText(it) }.joinToString("\n\n")
            val status = statusOf(word)
            outcomes +=
                CheckOutcome(
                    id = id,
                    status = status,
                    message = message.ifBlank { title ?: "(no message)" },
                    toolStatus = word.takeIf { it != status.name },
                    title = title,
                )
        }
    }
    return outcomes
}

private fun statusOf(word: String): CheckStatus =
    when (word.uppercase()) {
        "PASS" -> CheckStatus.PASS
        "WARN", "INFO" -> CheckStatus.WARN
        "FAIL", "ERROR", "FATAL" -> CheckStatus.FAIL
        else -> CheckStatus.SKIP
    }

private val CHECK_KEY = Regex("""<FontBakeryCheck:(.+)>""")

private fun checkId(entry: JsonObject): String? {
    val key = entry["key"] as? JsonArray ?: return null
    for (part in key) {
        val text = (part as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        CHECK_KEY.find(text)?.let { return it.groupValues[1] }
    }
    return null
}

private fun logText(log: JsonElement): String? {
    val entry = log as? JsonObject ?: return null
    val message = entry["message"]
    return when (message) {
        is JsonPrimitive -> message.takeIf { it.isString }?.content
        is JsonObject -> message.text("message") ?: message.text("code")
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
