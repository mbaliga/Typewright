// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.compile.FeaCheckResult

/** One thing wrong in the `.fea` text, for the editor to show: [line] and [column] where the checker said them, and what it found. */
data class FeaFinding(
    val line: Int?,
    val column: Int?,
    val message: String,
) {
    /** The finding as one line of the report: "Line 3, column 11: ...", or just the message where no place was named. */
    fun text(): String =
        when {
            line == null -> message
            column == null -> "Line $line: $message"
            else -> "Line $line, column $column: $message"
        }
}

/**
 * What the editor can say about its text: which check it is showing the result of ([label]), what that check found
 * ([findings]), what it does not know ([limits]), and whether the parser's answer is still on its way ([pending]).
 *
 * Where a parser (fontTools' feaLib in the Python the build uses) ran, its answer is shown and labelled as its. Where there
 * is none, or it could not run, the quick shape check of [FeaCheck] is shown, labelled as that, with the reason and the plain
 * statement that the language itself was not parsed. A shape check is never presented as a parse.
 */
data class FeaReport(
    val label: String,
    val findings: List<FeaFinding>,
    val limits: String,
    val pending: Boolean,
) {
    /** The lines that have something wrong with them, so the editor can mark them in the margin. */
    val markedLines: Set<Int> get() = findings.mapNotNull { it.line }.toSet()

    companion object {
        /** The report for [text] given the parser's answer [parsed], or null while it has not answered. */
        fun of(
            text: String,
            parsed: FeaCheckResult?,
        ): FeaReport {
            val shape = FeaCheck.check(text).map { FeaFinding(it.line, null, it.message) }
            return when (parsed) {
                null -> {
                    FeaReport(
                        "Check · ${FeaCheck.COVERS}",
                        shape,
                        "Asking fontTools to read it. Until it answers, only this quick check has looked at it.",
                        pending = true,
                    )
                }

                is FeaCheckResult.Checked -> {
                    val limits =
                        "Read ${parsed.where}, and nothing leaves the computer. " +
                            if (parsed.built) {
                                "Its lookups were built too. The build can still find more."
                            } else {
                                "Its lookups were not built, so the build can still find more."
                            }
                    FeaReport(
                        label = "Check · read by ${parsed.tool}, with this font's glyph names",
                        findings = parsed.problems.map { FeaFinding(it.line, it.column, it.message) },
                        limits = limits,
                        pending = false,
                    )
                }

                is FeaCheckResult.Unavailable -> {
                    FeaReport(
                        label = "Check · ${FeaCheck.COVERS}",
                        findings = shape,
                        limits =
                            "${parsed.reason} The language itself is not parsed here: " +
                                "the full check happens when you build, so a text that passes can still fail then.",
                        pending = false,
                    )
                }
            }
        }
    }
}
