// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

/** What a numeric field can look up: a font-wide metric, or a property of a glyph by name (`o.rsb`). */
interface ExprScope {
    /** The font-wide value called [name] (`xheight`, `capheight`, `ascender`, `descender`, `baseline`, `upm`), or null. */
    fun metric(name: String): Double?

    /** The value of [property] (`adv`, `lsb`, `rsb`) of the glyph called [glyph] (null for the glyph being drawn), or null if unknown. */
    fun glyphValue(
        glyph: String?,
        property: String,
    ): Double?
}

/** The outcome of reading what was typed into a numeric field. */
sealed interface ExprResult {
    data class Value(
        val value: Double,
    ) : ExprResult {
        /** The value as a whole font unit (integers at rest), rounding half up. */
        val units: Int get() = kotlin.math.floor(value + 0.5).toInt()
    }

    data class Error(
        val message: String,
    ) : ExprResult
}

/**
 * Reads a numeric field (INTERACTION_V1 §4.3, D6): a plain number, or `=` followed by an expression
 * over numbers, `+ - * /`, brackets and names: `=xheight`, `=o.rsb+4`, `=(capheight-xheight)/2`. A
 * name is a font metric, or `glyph.property` with the property one of `adv`, `lsb`, `rsb` (a bare
 * `lsb` is the glyph being drawn). It never throws: a mistake comes back as an [ExprResult.Error]
 * that names it, so the field can say so and keep what was there.
 */
object NumericExpr {
    private val PROPERTIES = setOf("adv", "lsb", "rsb")

    fun evaluate(
        text: String,
        scope: ExprScope,
    ): ExprResult {
        val source = text.trim()
        if (source.isEmpty()) return ExprResult.Error("Type a number, or = and an expression.")
        if (!source.startsWith("=")) {
            val plain =
                source.toDoubleOrNull()
                    ?: return ExprResult.Error("\"$source\" is not a number. Start with = to use a name such as =xheight.")
            return finite(plain)
        }
        return try {
            val parser = Parser(source.substring(1), scope)
            val value = parser.expression()
            parser.expectEnd()
            finite(value)
        } catch (failure: ExprFailure) {
            ExprResult.Error(failure.message)
        }
    }

    private fun finite(value: Double): ExprResult =
        if (value.isNaN() || value.isInfinite()) ExprResult.Error("That does not come to a number.") else ExprResult.Value(value)

    private class ExprFailure(
        override val message: String,
    ) : Exception(message)

    private class Parser(
        private val s: String,
        private val scope: ExprScope,
    ) {
        private var i = 0

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }

        fun expectEnd() {
            skip()
            if (i < s.length) throw ExprFailure("Unexpected \"${s[i]}\".")
        }

        fun expression(): Double {
            var value = term()
            while (true) {
                skip()
                value =
                    when (s.getOrNull(i)) {
                        '+' -> {
                            i++
                            value + term()
                        }

                        '-' -> {
                            i++
                            value - term()
                        }

                        else -> {
                            return value
                        }
                    }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skip()
                value =
                    when (s.getOrNull(i)) {
                        '*' -> {
                            i++
                            value * factor()
                        }

                        '/' -> {
                            i++
                            val divisor = factor()
                            if (divisor == 0.0) throw ExprFailure("Cannot divide by zero.")
                            value / divisor
                        }

                        else -> {
                            return value
                        }
                    }
            }
        }

        private fun factor(): Double {
            skip()
            val c = s.getOrNull(i) ?: throw ExprFailure("The expression ends too soon.")
            return when {
                c == '-' -> {
                    i++
                    -factor()
                }

                c == '(' -> {
                    i++
                    val inner = expression()
                    skip()
                    if (s.getOrNull(i) != ')') throw ExprFailure("A bracket is not closed.")
                    i++
                    inner
                }

                c.isDigit() || c == '.' -> {
                    number()
                }

                c.isLetter() || c == '_' -> {
                    name()
                }

                else -> {
                    throw ExprFailure("Unexpected \"$c\".")
                }
            }
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw ExprFailure("\"${s.substring(start, i)}\" is not a number.")
        }

        private fun name(): Double {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.')) i++
            val token = s.substring(start, i).trimEnd('.')
            scope.metric(token.lowercase())?.let { return it }
            if (token.lowercase() in PROPERTIES) {
                return scope.glyphValue(null, token.lowercase()) ?: throw ExprFailure("No glyph is open to read $token from.")
            }
            val dot = token.lastIndexOf('.')
            if (dot > 0) {
                val property = token.substring(dot + 1).lowercase()
                val glyph = token.substring(0, dot)
                if (property in PROPERTIES) {
                    return scope.glyphValue(glyph, property)
                        ?: throw ExprFailure("There is no glyph called \"$glyph\" to read $property from.")
                }
            }
            throw ExprFailure("\"$token\" is not a name this field knows. Try xheight, capheight, baseline, adv, lsb, rsb or glyph.rsb.")
        }
    }
}
