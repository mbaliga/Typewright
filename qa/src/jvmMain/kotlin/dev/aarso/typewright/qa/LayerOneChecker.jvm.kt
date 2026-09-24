package dev.aarso.typewright.qa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

/**
 * Desktop/JVM actual: shells out to a `fontspector` binary on `PATH`
 * (docs/ARCHITECTURE_REVIEW.md section 3 `:qa`, section 6 decision 2 — "Fontspector everywhere").
 * `fontspector` is not on PyPI or Chaquopy's index; it is a Rust binary, installed with
 * `cargo install fontspector` (needs `protoc`) or a GitHub release download, neither of which this
 * checker attempts itself — it only *looks* for a binary that is already there (`--version`
 * probes `availability`), exactly the way `compile`'s `SystemPythonFontmakeBackend` looks for
 * `python3` rather than installing it.
 *
 * **What was actually tried in this build environment (P1-qa), for the record.** `protoc` was
 * missing and was installed (`apt-get install protobuf-compiler`); `cargo install fontspector`
 * then reached real compilation (over a hundred crates) but failed inside
 * `fontspector-checkapi`'s own build script, which fetches OpenType script tags from
 * `learn.microsoft.com` at *build* time: `"Failed to fetch OpenType script tags ...: invalid peer
 * certificate: UnknownIssuer"`. That is this sandbox's TLS-intercepting egress proxy again
 * (`/root/.ccr/README.md`) tripping up a Rust HTTP client whose trust store does not read the
 * usual `SSL_CERT_FILE`/`CARGO_HTTP_CAINFO` environment variables (both were set and made no
 * difference on a retry), not a missing dependency — so a real machine with ordinary internet
 * access should not hit this. GitHub release binaries were not tried (github.com release-asset
 * downloads are blocked here, per this session's own environment notes). This checker therefore
 * ships untested against a real `fontspector` process in this repository; [availability] returning
 * [LayerOneAvailability.Unavailable] here is the honest, expected result (CLAUDE.md law 4), and
 * the process-invocation and JSON-parsing code paths below are covered by [FontspectorCliCheckerTest]
 * against a fake `fontspector` script instead.
 *
 * **JSON shape.** Fontspector's `--json <path>` flag writes a report file; its exact schema is
 * UNVERIFIED (docs/ARCHITECTURE_REVIEW.md section 3 `:qa`, risk 3 — the binary could not be run in
 * this container to observe real output, and USING.md documents the flag but not the shape). Per
 * law 5, this reader does not pretend to know a schema it has not seen: [parseFontspectorReport]
 * walks the parsed JSON generically, treating every object that carries a recognisable status
 * field as one [CheckOutcome], rather than binding to field paths that might not exist. When
 * nothing recognisable is found, [check] falls back to [unavailableReport] with the raw text
 * attached, rather than silently reporting zero checks as if that meant "all clear".
 */
class FontspectorCliChecker(
    private val binaryName: String = "fontspector",
    private val profile: String = "googlefonts",
    private val timeoutSeconds: Long = 120,
) : LayerOneChecker {
    override val name: String = "Fontspector (CLI)"

    override suspend fun availability(): LayerOneAvailability =
        runCatching {
            val process = ProcessBuilder(binaryName, "--version").redirectErrorStream(true).start()
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            process.destroyForcibly()
        }.fold(
            onSuccess = { LayerOneAvailability.Available },
            onFailure = { LayerOneAvailability.Unavailable(NOT_FOUND_REASON) },
        )

    override suspend fun check(input: LayerOneInput): LayerOneReport {
        val workDir = createTempDirectory("fontspector-").toFile()
        try {
            val fontFile = File(workDir, sanitizeFileName(input.fileName))
            fontFile.writeBytes(input.bytes)
            val jsonFile = File(workDir, "report.json")

            // --skip-network: CLAUDE.md law 3 -- a QA run the user starts must not silently make
            // network calls Fontspector's own shaping/glyph-coverage checks would otherwise try.
            val args =
                listOf(
                    binaryName,
                    "--profile",
                    profile,
                    "--skip-network",
                    "--json",
                    jsonFile.absolutePath,
                    fontFile.absolutePath,
                )
            val process =
                runCatching {
                    ProcessBuilder(args).redirectErrorStream(false).start()
                }.getOrElse { return unavailableReport(NOT_FOUND_REASON) }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            if (!finished) {
                process.destroyForcibly()
                return unavailableReport("Unavailable: fontspector did not finish within ${timeoutSeconds}s.")
            }

            if (!jsonFile.exists()) {
                return unavailableReport(
                    "Fontspector ran (exit ${process.exitValue()}) but wrote no JSON report. " +
                        "stdout: ${stdout.take(RAW_OUTPUT_LIMIT)} stderr: ${stderr.take(RAW_OUTPUT_LIMIT)}",
                )
            }
            val reportText = jsonFile.readText()
            val results =
                runCatching { parseFontspectorReport(Json.parseToJsonElement(reportText)) }
                    .getOrElse { emptyList() }
            return if (results.isNotEmpty()) {
                LayerOneReport(results)
            } else {
                unavailableReport(
                    "Fontspector's JSON report did not match any recognised shape (its exact schema " +
                        "is unverified -- see this class's KDoc). Raw report: ${reportText.take(RAW_OUTPUT_LIMIT)}",
                )
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private companion object {
        const val NOT_FOUND_REASON = "Unavailable: fontspector not found on PATH."
        const val RAW_OUTPUT_LIMIT = 2000
    }
}

/** A safe-enough basename for a temporary file: strips any path separators out of a caller-given name. */
private fun sanitizeFileName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
    return base.ifBlank { "font.ttf" }
}

/**
 * Walks [element] looking for every JSON object that carries a status-like field (`status` or
 * `result`) whose value is one of Fontspector's known severities (`pass`, `warn`, `fail`, `skip`,
 * `info`, `error` -- USING.md's `-e/--error-code-on` option lists exactly these). Each one becomes
 * one [CheckOutcome]: an id from a nearby `check_id`/`id`/`code` field, or the JSON key the object
 * was found under, and a message from a nearby `message`/`reason` field. This is deliberately
 * shape-agnostic (see this file's top KDoc on why); it does not require the report to nest objects
 * any particular number of levels deep.
 */
internal fun parseFontspectorReport(element: JsonElement): List<CheckOutcome> {
    val outcomes = mutableListOf<CheckOutcome>()
    walkForOutcomes(element, idHint = null, outcomes)
    return outcomes
}

private fun walkForOutcomes(
    element: JsonElement,
    idHint: String?,
    into: MutableList<CheckOutcome>,
) {
    when (element) {
        is JsonObject -> {
            val localId = element.stringField("check_id") ?: element.stringField("id") ?: element.stringField("code")
            val effectiveId = localId ?: idHint
            val status = (element.stringField("status") ?: element.stringField("result"))?.let(::parseFontspectorStatus)
            if (status != null) {
                val message = element.stringField("message") ?: element.stringField("reason") ?: "(no message)"
                into += CheckOutcome(effectiveId ?: "fontspector/unknown-check", status, message)
            }
            for ((key, value) in element) walkForOutcomes(value, effectiveId ?: key, into)
        }

        is JsonArray -> {
            for (item in element) walkForOutcomes(item, idHint, into)
        }

        else -> {}
    }
}

private fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * Maps one of Fontspector's severities onto [CheckStatus]'s four words. `info` becomes [CheckStatus.WARN]
 * (a note worth the user's attention, folded in since this pipeline has no separate INFO level) and
 * `error` (a check that itself crashed) becomes [CheckStatus.FAIL] (something is wrong, conservatively);
 * both are simplifications this KDoc states rather than hides. An unrecognised word is not a status
 * at all -- returns `null`, so [walkForOutcomes] keeps looking rather than inventing one.
 */
private fun parseFontspectorStatus(word: String): CheckStatus? =
    when (word.lowercase()) {
        "pass" -> CheckStatus.PASS
        "warn", "info" -> CheckStatus.WARN
        "fail", "error" -> CheckStatus.FAIL
        "skip" -> CheckStatus.SKIP
        else -> null
    }

actual fun platformLayerOneChecker(): LayerOneChecker = FontspectorCliChecker()
