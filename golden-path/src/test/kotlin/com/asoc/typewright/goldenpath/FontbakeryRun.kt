// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files

/**
 * A direct run of `fontbakery check-googlefonts` on one font file, as a person would run it, in the Python `TYPEWRIGHT_PYTHON` names: every
 * check's id, its result and what it said. The golden path uses it to see the folder Ship wrote through Fontbakery's own eyes, not through
 * the app's reading of them.
 */
object FontbakeryRun {
    /** One check of the report. [status] is Fontbakery's word: PASS, WARN, FAIL, ERROR, FATAL, SKIP or INFO. */
    class Result(
        val id: String,
        val status: String,
        val message: String,
    ) {
        val isFail: Boolean get() = status == "FAIL" || status == "ERROR" || status == "FATAL"

        override fun toString(): String = "$status $id: ${message.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()}"
    }

    /** Runs the googlefonts profile on [font], offline, and returns every check's result in the report's order. Throws if no report is written. */
    fun run(font: File): List<Result> {
        val python = System.getenv("TYPEWRIGHT_PYTHON")?.takeIf { it.isNotBlank() } ?: "python3"
        val work = Files.createTempDirectory("typewright-golden-path-fb-").toFile()
        try {
            val json = File(work, "report.json")
            val process =
                ProcessBuilder(
                    python,
                    "-m",
                    "fontbakery",
                    "check-googlefonts",
                    "-q",
                    "-n",
                    "-C",
                    "--skip-network",
                    "--json",
                    json.path,
                    font.path,
                ).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().decodeToString()
            process.waitFor()
            check(json.isFile) { "fontbakery wrote no report for ${font.path}: ${output.takeLast(600)}" }
            return parse(json.readText())
        } finally {
            work.deleteRecursively()
        }
    }

    /** The results in a `--json` report. */
    fun parse(report: String): List<Result> {
        val results = mutableListOf<Result>()
        val sections = (Json.parseToJsonElement(report) as JsonObject)["sections"] as? JsonArray ?: return emptyList()
        for (section in sections) {
            for (check in (section as JsonObject)["checks"] as? JsonArray ?: continue) {
                val entry = check as JsonObject
                val status = (entry["result"] as? JsonPrimitive)?.content ?: continue
                val id =
                    (entry["key"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.content }
                        .firstNotNullOfOrNull { Regex("<FontBakeryCheck:(.+)>").find(it)?.groupValues?.get(1) }
                        ?: (entry["description"] as? JsonPrimitive)?.content.orEmpty()
                val message =
                    (entry["logs"] as? JsonArray).orEmpty().joinToString("\n") { log ->
                        val text = (log as? JsonObject)?.get("message")
                        when (text) {
                            is JsonObject -> (text["message"] as? JsonPrimitive)?.content.orEmpty()
                            is JsonPrimitive -> text.content
                            else -> ""
                        }
                    }
                results += Result(id, status, message)
            }
        }
        return results
    }
}
