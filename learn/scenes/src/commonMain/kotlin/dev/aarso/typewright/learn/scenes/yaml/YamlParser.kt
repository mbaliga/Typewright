// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes.yaml

/**
 * A hand-written parser for the exact YAML subset `docs/LESSONS_SCAFFOLD.md` section 1's scene
 * schema uses: block mappings, block sequences (including sequences of mappings, as the schema's
 * own `faces:` and `callouts:` fields use), single-line flow sequences of scalars (`stress:
 * [30, 12]`, `tags: [a, b, c]` — the schema's own worked example uses flow style for every list of
 * plain values, so this is in scope even though it is a "flow collection"), single- and
 * double-quoted scalars, folded (`>`) and literal (`|`) block scalars for multi-line prose, and
 * `#` comments. Out of scope, because the schema never uses them: anchors/aliases, multi-document
 * streams, flow mappings (`{a: b}`), and multi-line flow sequences.
 *
 * **Why hand-written instead of a library.** `docs/ARCHITECTURE_REVIEW.md` section 3
 * `:learn:scenes` already flagged the risk directly: "kaml's README calls its Wasm support
 * 'highly experimental … may be removed'." That is still exactly true of kaml's current release
 * (0.104.0, checked against its own README on 24 Sept 2026): it does publish a `kaml-wasm-js`
 * artifact for every platform target kaml ships, so it is not *unavailable* on `wasmJs` the way
 * the fallback condition in the P6 prompt was written to catch — but its own maintainers state,
 * in the same breath as documenting that target, that "Kotlin/JS and Kotlin/Wasm support are
 * considered highly experimental. It is not yet fully functional, and may be removed or modified
 * at any time." That warning is about precisely the target this module needs, for a format every
 * future Learn scene across five strands will be written in — an unacceptable stability risk to
 * take a binary dependency on, versus writing the verified subset this one schema actually needs
 * (the same "build a verified substitute for what the primary source can't reliably supply" call
 * this codebase already made for `engine-construct`'s Hobby-spline tangent solve,
 * P5a-foundations, independently re-derived rather than trusted from a secondhand description).
 * No YAML library is added by this file; nothing here is registered as a dependency, so
 * `THIRD_PARTY.md` needs no new entry for it.
 *
 * **Simplifications, stated plainly rather than left as silent gaps:**
 * - Indentation must be spaces; a leading tab throws [YamlParseException] rather than guessing.
 * - A block scalar's fold/clip is intentionally normalised for in-app text rather than matching
 *   YAML's file-stream chomping rules exactly: the default (`clip`) and `-` (`strip`) both produce
 *   no trailing newline (a caption is a string used inline in the UI, not a file to re-emit byte
 *   for byte); `+` (`keep`) preserves whatever trailing blank lines were read. Folding treats every
 *   body line as equally indented (the schema's own block scalars are, in every scene this format
 *   describes, uniform paragraphs — no more-indented "literal" sub-lines within a folded block).
 * - A single-quoted scalar unescapes YAML's doubled `''` to a literal `'`; a double-quoted scalar
 *   unescapes `\"`, `\\`, `\n` and `\t` and passes any other `\x` through as `x`.
 *
 * See [YamlParserTest] for the worked example this parser is verified against, including
 * `docs/LESSONS_SCAFFOLD.md` section 1's own `lineages.transitional` block round-tripped verbatim.
 */
public fun parseYaml(text: String): YamlValue {
    val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val cursor = Cursor(lines)
    cursor.skipBlankAndCommentLines()
    if (cursor.isAtEnd) return YamlValue.Mapping(emptyList())
    val topIndent = cursor.currentIndent()
    val value = parseNode(cursor, topIndent)
    cursor.skipBlankAndCommentLines()
    if (!cursor.isAtEnd) {
        throw YamlParseException(
            "unexpected content at line ${cursor.lineNumber()}, outside the document's top-level " +
                "indent (column $topIndent): '${cursor.peekRaw().trim()}'",
        )
    }
    return value
}

/** A read-only cursor over a document's raw lines, tracking only a current line index. */
private class Cursor(
    private val lines: List<String>,
) {
    var pos: Int = 0
        private set

    val isAtEnd: Boolean get() = pos >= lines.size
    val totalLines: Int get() = lines.size

    fun lineAt(index: Int): String = lines[index]

    fun peekRaw(): String = lines[pos]

    fun advance() {
        pos++
    }

    /** 1-based line number of the current position, for error messages. */
    fun lineNumber(): Int = pos + 1

    fun currentIndent(): Int = indentOf(peekRaw())

    fun skipBlankAndCommentLines() {
        while (!isAtEnd && isBlankOrCommentLine(lines[pos])) pos++
    }

    private fun isBlankOrCommentLine(line: String): Boolean = line.isBlank() || line.trimStart(' ').startsWith("#")
}

/** The number of leading spaces on [line]; a leading tab is rejected rather than guessed at. */
private fun indentOf(line: String): Int {
    var i = 0
    while (i < line.length && line[i] == ' ') i++
    if (i < line.length && line[i] == '\t') {
        throw YamlParseException("indentation must use spaces, not tabs: '${line.trim()}'")
    }
    return i
}

/** [Cursor.peekRaw] sliced to the content past [indent] columns, with a trailing comment removed. */
private fun contentAt(
    cursor: Cursor,
    indent: Int,
): String = stripTrailingComment(cursor.peekRaw().substring(indent))

/**
 * One node at exactly [indent] columns: a block sequence if the line at [indent] starts with
 * `- `, a block mapping otherwise. Returns [YamlValue.NULL] if there is nothing at or past
 * [indent] (an explicitly empty value).
 */
private fun parseNode(
    cursor: Cursor,
    indent: Int,
): YamlValue {
    cursor.skipBlankAndCommentLines()
    if (cursor.isAtEnd) return YamlValue.NULL
    val lineIndent = cursor.currentIndent()
    if (lineIndent < indent) return YamlValue.NULL
    if (lineIndent > indent) {
        throw YamlParseException(
            "unexpected indentation at line ${cursor.lineNumber()}: expected column $indent, found $lineIndent",
        )
    }
    val content = contentAt(cursor, indent)
    return if (content == "-" || content.startsWith("- ")) {
        YamlValue.Sequence(parseSequenceItems(cursor, indent))
    } else {
        YamlValue.Mapping(parseMappingEntries(cursor, indent))
    }
}

/** `key: value` entries at exactly [indent] columns, stopping at dedent, a `- ` item, or EOF. */
private fun parseMappingEntries(
    cursor: Cursor,
    indent: Int,
): List<Pair<String, YamlValue>> {
    val entries = mutableListOf<Pair<String, YamlValue>>()
    while (true) {
        cursor.skipBlankAndCommentLines()
        if (cursor.isAtEnd) break
        if (cursor.currentIndent() != indent) break
        val content = contentAt(cursor, indent)
        if (content == "-" || content.startsWith("- ")) break
        entries.add(parseOneMappingEntry(cursor, indent, content))
    }
    return entries
}

/**
 * Parses one `key: value` entry from [content] (the current line's text past [keyColumn], comment
 * already stripped), consuming exactly the current line plus however many further lines the
 * value itself spans (a nested block, or a folded/literal block scalar).
 */
private fun parseOneMappingEntry(
    cursor: Cursor,
    keyColumn: Int,
    content: String,
): Pair<String, YamlValue> {
    val split =
        splitKeyValue(content)
            ?: throw YamlParseException("expected 'key: value' at line ${cursor.lineNumber()}: '${cursor.peekRaw().trim()}'")
    val (key, rest) = split
    cursor.advance()
    val trimmedRest = rest.trim()
    val value: YamlValue =
        when {
            trimmedRest.isEmpty() -> {
                cursor.skipBlankAndCommentLines()
                if (cursor.isAtEnd || cursor.currentIndent() <= keyColumn) {
                    YamlValue.NULL
                } else {
                    parseNode(cursor, cursor.currentIndent())
                }
            }

            trimmedRest[0] == '>' || trimmedRest[0] == '|' -> {
                parseBlockScalar(cursor, keyColumn, trimmedRest)
            }

            trimmedRest[0] == '[' -> {
                parseFlowSequence(trimmedRest)
            }

            else -> {
                YamlValue.Scalar(parseScalarTextOrNull(trimmedRest))
            }
        }
    return key to value
}

/** `- item` entries at exactly [dashIndent] columns; an item may be a scalar, flow list, or mapping. */
private fun parseSequenceItems(
    cursor: Cursor,
    dashIndent: Int,
): List<YamlValue> {
    val items = mutableListOf<YamlValue>()
    while (true) {
        cursor.skipBlankAndCommentLines()
        if (cursor.isAtEnd) break
        if (cursor.currentIndent() != dashIndent) break
        val content = contentAt(cursor, dashIndent)
        if (!(content == "-" || content.startsWith("- "))) break
        val afterDash = if (content == "-") "" else content.substring(2)
        val extraIndent = afterDash.length - afterDash.trimStart(' ').length
        val contentColumn = dashIndent + 2 + extraIndent
        val inline = afterDash.trim()
        val item =
            when {
                inline.isEmpty() -> {
                    cursor.advance()
                    cursor.skipBlankAndCommentLines()
                    if (cursor.isAtEnd || cursor.currentIndent() <= dashIndent) {
                        YamlValue.NULL
                    } else {
                        parseNode(cursor, cursor.currentIndent())
                    }
                }

                looksLikeMappingEntry(inline) -> {
                    val first = parseOneMappingEntry(cursor, contentColumn, inline)
                    val more = parseMappingEntries(cursor, contentColumn)
                    YamlValue.Mapping(listOf(first) + more)
                }

                inline.startsWith("[") -> {
                    cursor.advance()
                    parseFlowSequence(inline)
                }

                else -> {
                    cursor.advance()
                    YamlValue.Scalar(parseScalarTextOrNull(inline))
                }
            }
        items.add(item)
    }
    return items
}

/**
 * A folded (`>`) or literal (`|`) block scalar, per this file's own KDoc on how chomping and
 * folding are normalised for in-app text. [indicatorRaw] is the already comment-stripped text
 * starting with `>` or `|`, optionally followed by a chomping indicator (`-` or `+`).
 */
private fun parseBlockScalar(
    cursor: Cursor,
    keyColumn: Int,
    indicatorRaw: String,
): YamlValue.Scalar {
    val indicator = indicatorRaw.trim()
    val style = indicator[0]
    val chomp = indicator.getOrNull(1)?.takeIf { it == '-' || it == '+' }

    var lookahead = cursor.pos
    var contentIndent: Int? = null
    while (lookahead < cursor.totalLines) {
        val raw = cursor.lineAt(lookahead)
        if (raw.isNotBlank()) {
            val ind = indentOf(raw)
            if (ind > keyColumn) contentIndent = ind
            break
        }
        lookahead++
    }

    val bodyLines = mutableListOf<String>()
    if (contentIndent != null) {
        while (!cursor.isAtEnd) {
            val raw = cursor.peekRaw()
            if (raw.isBlank()) {
                bodyLines.add("")
                cursor.advance()
                continue
            }
            val ind = indentOf(raw)
            if (ind < contentIndent) break
            bodyLines.add(raw.substring(contentIndent).trimEnd())
            cursor.advance()
        }
    }

    val body =
        if (style == '|') {
            bodyLines.joinToString("\n")
        } else {
            val sb = StringBuilder()
            for (i in bodyLines.indices) {
                val line = bodyLines[i]
                if (line.isEmpty()) {
                    sb.append('\n')
                } else {
                    if (i > 0 && bodyLines[i - 1].isNotEmpty()) sb.append(' ')
                    sb.append(line)
                }
            }
            sb.toString()
        }
    val chomped = if (chomp == '+') body else body.trimEnd('\n')
    return YamlValue.Scalar(chomped)
}

/** `[a, b, c]` on a single line; each item is a plain or quoted scalar. */
private fun parseFlowSequence(raw: String): YamlValue.Sequence {
    val trimmed = raw.trim()
    if (!trimmed.endsWith("]")) {
        throw YamlParseException("flow sequence is missing its closing ']' (multi-line flow sequences are not supported): $raw")
    }
    val inner = trimmed.substring(1, trimmed.length - 1).trim()
    if (inner.isEmpty()) return YamlValue.Sequence(emptyList())
    return YamlValue.Sequence(splitTopLevelCommas(inner).map { YamlValue.Scalar(parseScalarTextOrNull(it)) })
}

private fun looksLikeMappingEntry(s: String): Boolean = splitKeyValue(s) != null

/** Splits `"key: rest"` at the first unquoted, unescaped top-level colon, or null if there is none. */
private fun splitKeyValue(s: String): Pair<String, String>? {
    var inDouble = false
    var inSingle = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            inDouble -> {
                if (c == '\\' && i + 1 < s.length) {
                    i++
                } else if (c == '"') {
                    inDouble = false
                }
            }

            inSingle -> {
                if (c == '\'') {
                    if (i + 1 < s.length && s[i + 1] == '\'') {
                        i++
                    } else {
                        inSingle = false
                    }
                }
            }

            c == '"' -> {
                inDouble = true
            }

            c == '\'' -> {
                inSingle = true
            }

            c == ':' && (i == s.length - 1 || s[i + 1] == ' ') -> {
                val key = unquoteKey(s.substring(0, i).trim())
                val rest = s.substring(i + 1)
                return key to rest
            }
        }
        i++
    }
    return null
}

private fun unquoteKey(raw: String): String {
    if (raw.length >= 2 && ((raw.first() == '"' && raw.last() == '"') || (raw.first() == '\'' && raw.last() == '\''))) {
        return raw.substring(1, raw.length - 1)
    }
    return raw
}

/** A trailing `#` comment (unquoted, preceded by whitespace or at the start of the line) removed. */
private fun stripTrailingComment(s: String): String {
    var inDouble = false
    var inSingle = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            inDouble -> {
                if (c == '\\' && i + 1 < s.length) {
                    i++
                } else if (c == '"') {
                    inDouble = false
                }
            }

            inSingle -> {
                if (c == '\'') {
                    if (i + 1 < s.length && s[i + 1] == '\'') {
                        i++
                    } else {
                        inSingle = false
                    }
                }
            }

            c == '"' -> {
                inDouble = true
            }

            c == '\'' -> {
                inSingle = true
            }

            c == '#' && (i == 0 || s[i - 1] == ' ' || s[i - 1] == '\t') -> {
                return s.substring(0, i).trimEnd()
            }
        }
        i++
    }
    return s.trimEnd()
}

/** A single scalar's text, with quotes removed and escapes resolved; null for `~`/`null`/empty. */
private fun parseScalarTextOrNull(raw: String): String? {
    val s = raw.trim()
    if (s.isEmpty() || s == "~" || s.equals("null", ignoreCase = true)) return null
    if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
        return unescapeDoubleQuoted(s.substring(1, s.length - 1))
    }
    if (s.length >= 2 && s.first() == '\'' && s.last() == '\'') {
        return s.substring(1, s.length - 1).replace("''", "'")
    }
    return s
}

private fun unescapeDoubleQuoted(s: String): String {
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (val next = s[i + 1]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                'n' -> out.append('\n')
                't' -> out.append('\t')
                else -> out.append(next)
            }
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/** Splits [s] on commas outside quotes, trimming each part. */
private fun splitTopLevelCommas(s: String): List<String> {
    val parts = mutableListOf<String>()
    var inDouble = false
    var inSingle = false
    var start = 0
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            inDouble -> {
                if (c == '\\' && i + 1 < s.length) {
                    i++
                } else if (c == '"') {
                    inDouble = false
                }
            }

            inSingle -> {
                if (c == '\'') {
                    if (i + 1 < s.length && s[i + 1] == '\'') {
                        i++
                    } else {
                        inSingle = false
                    }
                }
            }

            c == '"' -> {
                inDouble = true
            }

            c == '\'' -> {
                inSingle = true
            }

            c == ',' -> {
                parts.add(s.substring(start, i).trim())
                start = i + 1
            }
        }
        i++
    }
    parts.add(s.substring(start).trim())
    return parts
}
