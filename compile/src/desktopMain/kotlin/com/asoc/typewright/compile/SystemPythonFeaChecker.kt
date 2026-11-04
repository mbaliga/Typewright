// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files

/**
 * The desktop `.fea` checker: fontTools' feaLib, run by the same Python the build uses ([PythonLocator]: `TYPEWRIGHT_PYTHON`,
 * then `python3`). The text and the font's glyph names go to a Python process in temporary files; feaLib parses the text with
 * those names as the glyph set, then builds the lookups into an empty font, and the first error it stops at comes back with
 * its line and column. Nothing is installed and nothing leaves the computer. Where there is no Python with fontTools, the
 * answer is [FeaCheckResult.Unavailable] with the sentence that says what is missing.
 *
 * The Python that was found is remembered, so typing does not search for it again; one that was not found is looked for again
 * after a while, in case it has been installed since.
 */
class SystemPythonFeaChecker internal constructor(
    private val env: (String) -> String?,
    private val runner: ProcessRunner,
    private val clock: () -> Long,
    private val timeoutMillis: Long,
) : FeaChecker {
    constructor() : this(System::getenv, RealProcessRunner, System::nanoTime, DEFAULT_TIMEOUT_MILLIS)

    private var remembered: Pair<Long, FontmakeEnvironment>? = null

    override suspend fun check(
        text: String,
        glyphNames: List<String>,
    ): FeaCheckResult = withContext(Dispatchers.IO) { checkBlocking(text, glyphNames) }

    private fun checkBlocking(
        text: String,
        glyphNames: List<String>,
    ): FeaCheckResult {
        val python =
            when (val found = environment()) {
                is FontmakeEnvironment.NotReady -> return FeaCheckResult.Unavailable(found.reason)
                is FontmakeEnvironment.Ready -> found.python
            }
        val work = Files.createTempDirectory("typewright-fea-").toFile()
        try {
            val features = File(work, "features.fea").apply { writeBytes(text.encodeToByteArray()) }
            val glyphs = File(work, "glyphs.txt").apply { writeText(glyphNames.filter { '\n' !in it }.joinToString("\n")) }
            val outcome =
                runner.run(listOf(python.python, "-c", SCRIPT, features.absolutePath, glyphs.absolutePath), timeoutMillis)
            if (outcome.problem != null) {
                forgetEnvironment()
                return FeaCheckResult.Unavailable("Python ${outcome.problem}, so the text was not checked.")
            }
            return interpret(outcome, python)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun interpret(
        outcome: ProcessOutcome,
        python: FontmakePython,
    ): FeaCheckResult {
        val line = outcome.output.lineSequence().lastOrNull { it.startsWith(RESULT_PREFIX) }
        val answer = line?.removePrefix(RESULT_PREFIX)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        if (!outcome.succeeded || answer == null) {
            val last =
                outcome.output
                    .lines()
                    .lastOrNull { it.isNotBlank() }
                    ?.trim()
            return FeaCheckResult.Unavailable(
                "Python could not read the text with fontTools" + (last?.let { " ($it)" } ?: "") + ", so it was not checked.",
            )
        }
        return FeaCheckResult.Checked(
            tool = answer.string("tool") ?: "fontTools feaLib",
            where = "in this computer's Python ${python.pythonVersion ?: "3"}",
            built = answer["built"]?.jsonPrimitive?.booleanOrNull ?: false,
            problems =
                answer["problems"]?.jsonArray.orEmpty().map { item ->
                    val problem = item.jsonObject
                    FeaProblem(problem.number("line"), problem.number("column"), problem.string("message").orEmpty())
                },
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun environment(): FontmakeEnvironment =
        synchronized(this) {
            val now = clock()
            remembered?.let { (at, found) -> if (found is FontmakeEnvironment.Ready || now - at < RETRY_NANOS) return found }
            PythonLocator(env, runner).resolve().also { remembered = now to it }
        }

    private fun forgetEnvironment() = synchronized(this) { remembered = null }

    internal companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        private const val RETRY_NANOS = 30_000_000_000L
        const val RESULT_PREFIX = "TYPEWRIGHT-FEA "

        /**
         * Run by `python -c` with the paths of the text and of the glyph names (one per line). It prints one line,
         * [RESULT_PREFIX] and a JSON object: the tool and version, whether the lookups were built, and the problem
         * feaLib stopped at (none when the text is good). A build step that fails for a reason that is not the text's
         * (an empty font lacking a table) is not reported as a problem; the answer says the lookups were not built.
         */
        val SCRIPT =
            """
            import io, json, re, sys
            from fontTools import version
            from fontTools.feaLib.builder import Builder
            from fontTools.feaLib.error import FeatureLibError
            from fontTools.feaLib.parser import Parser
            from fontTools.ttLib import TTFont

            def where(error):
                location = getattr(error, "location", None)
                line = getattr(location, "line", None)
                column = getattr(location, "column", None)
                message = str(error.args[0]) if error.args else str(error)
                found = re.search(r"<features>:(\d+):(\d+)", message)
                if line is None and found:
                    line, column = int(found.group(1)), int(found.group(2))
                message = re.sub(r"<features>:(\d+):(\d+)", r"line \1, column \2", message)
                message = " ".join(message.split())
                # feaLib says "got None None" where it ran out of text while still expecting more.
                message = re.sub(r": got None None$", ", but the text ended", message)
                return {"line": line, "column": column, "message": message}

            text = open(sys.argv[1], encoding="utf-8", newline="").read()
            names = [n for n in open(sys.argv[2], encoding="utf-8").read().split("\n") if n]
            answer = {"tool": "fontTools " + version + " (feaLib)", "built": False, "problems": []}
            try:
                tree = Parser(io.StringIO(text), glyphNames=names, followIncludes=False).parse()
                try:
                    font = TTFont()
                    font.setGlyphOrder(names)
                    Builder(font, tree).build()
                    answer["built"] = True
                except FeatureLibError as error:
                    answer["built"] = True
                    answer["problems"].append(where(error))
                except Exception:
                    pass
            except FeatureLibError as error:
                answer["problems"].append(where(error))
            print("TYPEWRIGHT-FEA " + json.dumps(answer))
            """.trimIndent()
    }
}
