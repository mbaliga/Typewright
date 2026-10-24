// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/** Where Typewright looks first for the Python that builds fonts: the environment variable a person or a test harness sets. */
internal const val PYTHON_ENV_VARIABLE = "TYPEWRIGHT_PYTHON"

/** The Python on PATH that is tried when the environment variable is not set (or cannot be run). */
internal const val DEFAULT_PYTHON = "python3"

/**
 * A Python that runs and the build packages it can import: which interpreter ([python], as it was
 * named), its [pythonVersion], fontmake's version and whether skia-pathops, which removes overlaps
 * exactly, imports ([pathops]).
 */
internal class FontmakePython(
    val python: String,
    val pythonVersion: String?,
    val fontmakeVersion: String?,
    val pathops: Boolean,
)

/** The outcome of looking for a Python that can build fonts: one that [Ready], or [NotReady] with the sentence to show. */
internal sealed interface FontmakeEnvironment {
    class Ready(
        val python: FontmakePython,
    ) : FontmakeEnvironment

    class NotReady(
        val reason: String,
    ) : FontmakeEnvironment
}

/**
 * Finds a Python 3 that can run fontmake, and says plainly what is missing when there is none.
 *
 * It tries `TYPEWRIGHT_PYTHON` first (the environment variable a person or the test harness sets),
 * then `python3` on PATH, and takes the first that runs. It asks that Python which of fontmake,
 * ufo2ft and fontTools it can import, and whether skia-pathops imports (optional: without it
 * overlaps are not removed, which the build log says). It installs nothing and never throws.
 */
internal class PythonLocator(
    private val env: (String) -> String?,
    private val runner: ProcessRunner,
    private val probeTimeoutMillis: Long = PROBE_TIMEOUT_MILLIS,
) {
    fun resolve(): FontmakeEnvironment {
        val named = env(PYTHON_ENV_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
        val candidates = listOfNotNull(named, DEFAULT_PYTHON).distinct()
        val notes = mutableListOf<String>()
        for (candidate in candidates) {
            val outcome = runner.run(listOf(candidate, "-c", PROBE_SCRIPT), probeTimeoutMillis)
            val facts = if (outcome.succeeded) parseProbe(outcome.output) else null
            if (facts == null || facts["python"] == null) {
                notes += describeFailure(candidate, outcome, candidate == named)
                continue
            }
            return interpret(candidate, facts)
        }
        return FontmakeEnvironment.NotReady(notFound(notes, named != null))
    }

    private fun interpret(
        python: String,
        facts: Map<String, Pair<Boolean, String>>,
    ): FontmakeEnvironment {
        val missing = REQUIRED_PACKAGES.filter { facts[it.module]?.first != true }
        val version = facts["python"]?.second
        if (missing.isNotEmpty()) {
            val names = joinWords(missing.map { it.module })
            val command = pipCommand(python, missing)
            val label = if (version != null) "Python $version ($python)" else python
            return FontmakeEnvironment.NotReady(
                "$label is missing $names. Install ${if (missing.size == 1) "it" else "them"} with: $command",
            )
        }
        return FontmakeEnvironment.Ready(
            FontmakePython(
                python = python,
                pythonVersion = version,
                fontmakeVersion = facts["fontmake"]?.second?.takeIf { it.isNotBlank() },
                pathops = facts["pathops"]?.first == true,
            ),
        )
    }

    private fun notFound(
        notes: List<String>,
        triedVariable: Boolean,
    ): String {
        val looked =
            if (triedVariable) {
                "$PYTHON_ENV_VARIABLE, then $DEFAULT_PYTHON on PATH"
            } else {
                "$DEFAULT_PYTHON on PATH; $PYTHON_ENV_VARIABLE is not set"
            }
        return (
            listOf("Python 3 was not found on this computer (looked at $looked).") + notes +
                "Install Python 3, then run: pip install fontmake"
        ).joinToString(" ")
    }

    private fun describeFailure(
        candidate: String,
        outcome: ProcessOutcome,
        fromVariable: Boolean,
    ): String {
        val source = if (fromVariable) "$PYTHON_ENV_VARIABLE ($candidate)" else candidate
        return when {
            outcome.problem != null -> "$source ${outcome.problem}."
            else -> "$source ran but did not answer like Python 3 (exit code ${outcome.exitCode})."
        }
    }

    companion object {
        private const val PROBE_TIMEOUT_MILLIS = 60_000L

        /** The packages a build cannot do without, and the pip name of each. */
        private val REQUIRED_PACKAGES =
            listOf(
                Package("fontmake", "fontmake"),
                Package("ufo2ft", "ufo2ft"),
                Package("fontTools", "fonttools"),
            )

        private class Package(
            val module: String,
            val pip: String,
        )

        private fun pipCommand(
            python: String,
            missing: List<Package>,
        ): String {
            // fontmake depends on ufo2ft and fontTools, so installing it brings both.
            val packages = if (missing.any { it.module == "fontmake" }) "fontmake" else missing.joinToString(" ") { it.pip }
            return if (python == DEFAULT_PYTHON) "pip install $packages" else "$python -m pip install $packages"
        }

        private fun joinWords(words: List<String>): String =
            when (words.size) {
                1 -> words[0]
                2 -> "${words[0]} and ${words[1]}"
                else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
            }

        /**
         * Run by `python -c`: one line per package, `name ok <version>` or `name missing <reason>`, then
         * `python ok <version>`. It prints nothing else, so a Python that cannot run it is not Python 3.
         */
        internal val PROBE_SCRIPT =
            """
            import importlib, sys
            def probe(module, dist):
                try:
                    found = importlib.import_module(module)
                except Exception as e:
                    print(module, "missing", type(e).__name__)
                    return
                try:
                    from importlib.metadata import version
                    v = version(dist)
                except Exception:
                    v = getattr(found, "__version__", "")
                print(module, "ok", v)
            probe("fontmake", "fontmake")
            probe("ufo2ft", "ufo2ft")
            probe("fontTools", "fonttools")
            probe("pathops", "skia-pathops")
            print("python", "ok", "%d.%d.%d" % sys.version_info[:3])
            """.trimIndent()

        /** `package -> (imports, detail)` from [PROBE_SCRIPT]'s output, or null when it is not that output. */
        internal fun parseProbe(output: String): Map<String, Pair<Boolean, String>>? {
            val facts = LinkedHashMap<String, Pair<Boolean, String>>()
            for (line in output.lineSequence()) {
                val parts = line.trim().split(' ', limit = 3)
                if (parts.size < 2 || (parts[1] != "ok" && parts[1] != "missing")) continue
                facts[parts[0]] = (parts[1] == "ok") to parts.getOrElse(2) { "" }
            }
            return facts.takeIf { it.isNotEmpty() }
        }
    }
}
