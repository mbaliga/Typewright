// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Writes JSON the way every project file is written (docs/PROJECT_MODEL.md §3): 2-space indent,
 * keys in the order the element holds them (schema order), `\n` line endings, a trailing
 * newline, nulls written out. Encoding is a pure function of the element and gives the same
 * bytes on every platform, so saving unchanged state rewrites nothing and git sees no churn.
 *
 * - Numbers with a fraction or an exponent are read with [parseJsonDouble] and rewritten with
 *   [formatDouble], both exact in common code, because each platform's own conversions spell
 *   and read some values differently. Build double elements with [jsonDouble] (or
 *   [ProjectDoubleSerializer]), never `JsonPrimitive(Double)`, which spells them with the
 *   platform's `toString`. Integers, and numbers too large or too small for a double, keep
 *   their spelling.
 * - Strings are UTF-8 with only what JSON requires escaped, except an unpaired UTF-16
 *   surrogate, which UTF-8 can't hold: it is written as a `\uXXXX` escape, so text cut in the
 *   middle of an emoji reads back exactly instead of becoming `?` or U+FFFD.
 */
internal object CanonicalJson {
    private const val INDENT = "  "

    /** [element] as project-file JSON text, ending in a newline. */
    fun encode(element: JsonElement): String {
        val out = StringBuilder()
        write(element, 0, out)
        out.append('\n')
        return out.toString()
    }

    /** [encode] as UTF-8 bytes, without a byte-order mark. */
    fun encodeToBytes(element: JsonElement): ByteArray = encode(element).encodeToByteArray()

    private fun write(
        element: JsonElement,
        depth: Int,
        out: StringBuilder,
    ) {
        when (element) {
            is JsonObject -> writeObject(element, depth, out)
            is JsonArray -> writeArray(element, depth, out)
            is JsonNull -> out.append("null")
            is JsonPrimitive -> if (element.isString) writeString(element.content, out) else out.append(canonicalLiteral(element.content))
        }
    }

    private fun writeObject(
        element: JsonObject,
        depth: Int,
        out: StringBuilder,
    ) {
        if (element.isEmpty()) {
            out.append("{}")
            return
        }
        out.append("{\n")
        var first = true
        for ((key, value) in element) {
            if (!first) out.append(",\n")
            first = false
            indent(depth + 1, out)
            writeString(key, out)
            out.append(": ")
            write(value, depth + 1, out)
        }
        out.append('\n')
        indent(depth, out)
        out.append('}')
    }

    private fun writeArray(
        element: JsonArray,
        depth: Int,
        out: StringBuilder,
    ) {
        if (element.isEmpty()) {
            out.append("[]")
            return
        }
        out.append("[\n")
        element.forEachIndexed { index, value ->
            if (index > 0) out.append(",\n")
            indent(depth + 1, out)
            write(value, depth + 1, out)
        }
        out.append('\n')
        indent(depth, out)
        out.append(']')
    }

    private fun indent(
        depth: Int,
        out: StringBuilder,
    ) {
        repeat(depth) { out.append(INDENT) }
    }

    private fun writeString(
        value: String,
        out: StringBuilder,
    ) {
        out.append('"')
        for ((index, char) in value.withIndex()) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char == '\b' -> out.append("\\b")
                char == '\u000C' -> out.append("\\f")
                char < ' ' -> appendEscape(char, out)
                char.isSurrogate() && !isPairedSurrogate(value, index) -> appendEscape(char, out)
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    private fun appendEscape(
        char: Char,
        out: StringBuilder,
    ) {
        out.append("\\u")
        for (shift in intArrayOf(12, 8, 4, 0)) out.append(HEX[(char.code shr shift) and 0xF])
    }

    // Whether the surrogate at [index] is half of a high-then-low pair.
    private fun isPairedSurrogate(
        value: String,
        index: Int,
    ): Boolean {
        val char = value[index]
        return if (char.isHighSurrogate()) {
            index + 1 < value.length && value[index + 1].isLowSurrogate()
        } else {
            index > 0 && value[index - 1].isHighSurrogate()
        }
    }

    private fun canonicalLiteral(content: String): String {
        if (content == "true" || content == "false" || content == "null") return content
        if (content.none { it == '.' || it == 'e' || it == 'E' }) return content
        val value = parseJsonDouble(content)
        return if (value == null || !value.isFinite()) content else formatDouble(value)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

/**
 * Timestamps in project files: UTC, whole seconds, ISO-8601 with `Z`
 * (`2026-09-25T14:03:11Z`). They come from state, set by the command that caused them, so a
 * value with a fraction of a second is a bug and is refused rather than silently truncated.
 */
object ProjectTimestamps {
    private val PATTERN = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")

    /** [instant] as `yyyy-MM-ddTHH:mm:ssZ`. */
    fun format(instant: Instant): String {
        require(instant.nanosecondsOfSecond == 0) { "Project timestamps are whole seconds; got $instant" }
        val text = instant.toString()
        check(PATTERN.matches(text)) { "Unexpected instant spelling $text" }
        return text
    }

    /** [instant] in the compact form diff file names use: `yyyyMMddTHHmmssZ`. */
    fun formatCompact(instant: Instant): String = format(instant).replace("-", "").replace(":", "")

    /** Parses exactly the [format] spelling; anything else throws [IllegalArgumentException]. */
    fun parse(text: String): Instant {
        require(PATTERN.matches(text)) { "Not a project timestamp (yyyy-MM-ddTHH:mm:ssZ): \"$text\"" }
        return Instant.parse(text)
    }
}
