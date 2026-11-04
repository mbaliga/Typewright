// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * One plain sentence for why a fontmake run failed, from the lines it printed. It looks for the
 * line fontmake itself ends on (its `fontmake: Error:` line), else the last line of a Python
 * traceback, else the last `ERROR` line, and names the part of the build that stopped when the
 * message says which: the cubic-to-quadratic conversion (cu2qu), ufo2ft, the UFO itself, or a
 * missing package. The raw lines stay in the build log; this is only the headline.
 */
internal fun explainFailure(
    lines: List<String>,
    exitCode: Int?,
): String {
    val detail = headline(lines)
    if (detail == null) {
        val code = if (exitCode != null) "exit code $exitCode" else "no exit code"
        return "fontmake stopped ($code) and did not say why; the build log has everything it printed."
    }
    val lower = detail.lowercase()
    val sentence =
        when {
            "unsupported segment type" in lower -> {
                "fontmake could not remove overlaps because an outline uses a curve form the overlap remover does not support: $detail"
            }

            "cu2qu" in lower || "cannot convert" in lower -> {
                "A curve could not be converted from cubic to quadratic (cu2qu), so the TrueType outlines could not be made: $detail"
            }

            "modulenotfounderror" in lower || "importerror" in lower || "no module named" in lower -> {
                "A Python package fontmake needs is missing: $detail"
            }

            "filenotfounderror" in lower || "no such file" in lower -> {
                "fontmake could not find a file it needs: $detail"
            }

            "invalidfontdata" in lower || "ufolib" in lower || "invalid ufo" in lower || "not a ufo" in lower -> {
                "The UFO is not valid, so fontmake could not read it: $detail"
            }

            "ufo2ft" in lower -> {
                "ufo2ft, the part of fontmake that builds the font, stopped: $detail"
            }

            else -> {
                "fontmake stopped with an error: $detail"
            }
        }
    return sentence.trimEnd().let { if (it.endsWith('.')) it else "$it." }
}

private val FONTMAKE_ERROR = Regex("""^fontmake: Error: (.*)$""")
private val EXCEPTION_LINE = Regex("""^[A-Za-z_][\w.]*(Error|Exception|Warning|Exit): ?.*$""")
private val EXCEPTION_NAME_ONLY = Regex("""^[A-Za-z_][\w.]*(Error|Exception)$""")
private val LOGGED_ERROR = Regex("""^(ERROR|CRITICAL):[\w.]*:(.*)$""")
private const val MAX_DETAIL = 240

private fun headline(lines: List<String>): String? {
    val trimmed = lines.map { it.trimEnd() }.filter { it.isNotBlank() }
    val found =
        trimmed.lastNotNullOf { line -> FONTMAKE_ERROR.find(line)?.groupValues?.get(1) }
            ?: trimmed.lastNotNullOf { line -> line.takeIf { EXCEPTION_LINE.matches(it) || EXCEPTION_NAME_ONLY.matches(it) } }
            ?: trimmed.lastNotNullOf { line ->
                LOGGED_ERROR
                    .find(line)
                    ?.groupValues
                    ?.get(2)
                    ?.trim()
            }
    return found?.replace(Regex("""\s+"""), " ")?.trim()?.take(MAX_DETAIL)
}

private inline fun <T, R : Any> List<T>.lastNotNullOf(transform: (T) -> R?): R? {
    for (i in indices.reversed()) transform(this[i])?.let { return it }
    return null
}
