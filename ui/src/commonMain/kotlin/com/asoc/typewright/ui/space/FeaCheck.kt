// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

/** One thing the quick check found wrong in a `.fea` text, on [line] (from 1). */
data class FeaIssue(
    val line: Int,
    val message: String,
)

/**
 * A quick look at the shape of a `.fea` text, not a parser: whether strings end, braces and square
 * brackets match, and whether a named block (`feature kern { … } kern;`, a `lookup`, a `table`)
 * closes with its own name. It does not know the grammar, so a text it passes can still fail when the
 * font is built, and the screen says so. It is what the editor shows while fontTools' feaLib has not answered
 * and where there is no feaLib to ask (see [FeaReport]); where feaLib runs, its parse is the check and this is not shown.
 */
object FeaCheck {
    /** What this check looks at, as the screen states it beside the result. */
    const val COVERS = "braces, brackets, strings and block names"

    private val NAMED_BLOCKS = setOf("feature", "lookup", "table", "anon", "anonymous")

    private class Block(
        val kind: String,
        val name: String?,
        val line: Int,
    )

    /** The issues in [text], in order of appearance; empty when nothing this check looks at is wrong. */
    fun check(text: String): List<FeaIssue> = Scan(text).run()

    private class Scan(
        private val text: String,
    ) {
        private val issues = ArrayList<FeaIssue>()
        private val open = ArrayList<Block>()
        private val statement = ArrayList<String>()
        private var brackets = 0
        private var bracketLine = 0
        private var line = 1
        private var i = 0

        // After a `}` the block's name (if it has one) and then `;` are expected.
        private var closing: Block? = null
        private var closingLine = 0
        private var nameSeen = false

        fun run(): List<FeaIssue> {
            while (i < text.length) step()
            closing?.let { issues += FeaIssue(closingLine, closingProblem(it)) }
            for (block in open) {
                issues += FeaIssue(block.line, "${block.kind}${block.name?.let { " $it" } ?: ""} is opened here and never closed")
            }
            if (brackets > 0) issues += FeaIssue(bracketLine, "A square bracket is opened here and never closed")
            return issues.sortedBy { it.line }
        }

        private fun closingProblem(block: Block): String =
            if (block.name != null && !nameSeen) {
                "${block.kind} ${block.name} (opened on line ${block.line}) should end with } ${block.name};"
            } else {
                "Expected ; after the closing brace"
            }

        /** Something other than the block's name or `;` came after its `}`. */
        private fun abandonClosing() {
            closing?.let { issues += FeaIssue(closingLine, closingProblem(it)) }
            closing = null
            nameSeen = false
        }

        private fun step() {
            val c = text[i]
            when {
                c == '\n' -> {
                    line++
                    i++
                }

                c == '#' -> {
                    while (i < text.length && text[i] != '\n') i++
                }

                c == '"' -> {
                    string()
                }

                c == '{' -> {
                    abandonClosing()
                    val first = statement.firstOrNull()
                    val named = first != null && first in NAMED_BLOCKS
                    open += Block(first ?: "block", if (named) statement.getOrNull(1) else null, line)
                    statement.clear()
                    i++
                }

                c == '}' -> {
                    abandonClosing()
                    val block = open.removeLastOrNull()
                    if (block == null) {
                        issues += FeaIssue(line, "A closing brace with no block open")
                    } else {
                        closing = block
                        closingLine = line
                    }
                    statement.clear()
                    i++
                }

                c == ';' -> {
                    val block = closing
                    if (block != null && block.name != null && !nameSeen) issues += FeaIssue(closingLine, closingProblem(block))
                    closing = null
                    nameSeen = false
                    statement.clear()
                    i++
                }

                c == '[' -> {
                    if (brackets == 0) bracketLine = line
                    brackets++
                    i++
                }

                c == ']' -> {
                    if (brackets == 0) issues += FeaIssue(line, "A closing bracket with no opening one") else brackets--
                    i++
                }

                c.isWhitespace() -> {
                    i++
                }

                else -> {
                    val start = i
                    while (i < text.length && !text[i].isWhitespace() && text[i] !in DELIMITERS) i++
                    token(text.substring(start, i))
                }
            }
        }

        private fun string() {
            val startLine = line
            i++
            while (i < text.length && text[i] != '"') {
                if (text[i] == '\n') line++
                i++
            }
            if (i >= text.length) {
                issues += FeaIssue(startLine, "A string starts here and never ends")
                return
            }
            i++
            token(STRING)
        }

        private fun token(word: String) {
            val block = closing
            if (block != null) {
                if (block.name != null && !nameSeen && word == block.name) {
                    nameSeen = true
                    return
                }
                abandonClosing()
            }
            statement += word
        }
    }

    private const val DELIMITERS = "{};[]#\""
    private const val STRING = "\"\""
}
