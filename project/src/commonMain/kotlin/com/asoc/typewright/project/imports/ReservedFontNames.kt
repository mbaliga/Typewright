// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

/**
 * The Reserved Font Names a font declares in [texts] (its copyright line and licence text), in the
 * order they appear, without repeats (compared ignoring case). The SIL Open Font License lets a
 * copyright holder reserve names; a Modified Version may not use them (OFL condition 3), which is
 * why an import with one must be renamed.
 */
fun reservedFontNames(vararg texts: String?): List<String> {
    val names = LinkedHashMap<String, String>()
    for (text in texts) {
        if (text == null) continue
        for (name in parseReservedFontNames(text)) names.getOrPut(name.lowercase()) { name }
    }
    return names.values.toList()
}

/**
 * The names [text] reserves. A declaration reads `with Reserved Font Name "X"`, `with Reserved
 * Font Names 'X' and 'Y'`, `with Reserved Font Name: X` or `with Reserved Font Name X.`, with
 * straight or curly quotes or none. A name may hold dots (`Foo.Sans`); an unquoted one stops at a
 * sentence-ending full stop, a line break, a comma, a semicolon, `and` or an opening bracket (so a
 * trailing `(http://…)` is not part of it). The licence's own boilerplate is not a declaration:
 * `"Reserved Font Name" refers to…`, `no Reserved Font Name`, and the template's unfilled
 * `<Reserved Font Name>` all yield nothing.
 */
fun parseReservedFontNames(text: String): List<String> {
    val found = LinkedHashMap<String, String>()
    for (match in MARKER.findAll(text)) {
        if (!isDeclaration(text, match)) continue
        var at = match.range.last + 1
        if (text.startsWith("(s)", at, ignoreCase = true)) at += "(s)".length
        at = skipSpaces(text, at)
        if (at < text.length && text[at] == ':') at = skipSpaces(text, at + 1)
        while (at < text.length) {
            val read = readName(text, at) ?: break
            val name =
                read.first
                    .trim()
                    .trimEnd('.', ',', ';')
                    .trim()
            if (name.isNotEmpty() && !name.startsWith("<")) found.getOrPut(name.lowercase()) { name }
            at = skipSpaces(text, read.second)
            at = skipSeparator(text, at) ?: break
        }
    }
    return found.values.toList()
}

private val MARKER = Regex("""reserved\s+font\s+names?""", RegexOption.IGNORE_CASE)

private val OPENING_QUOTES = mapOf('"' to '"', '\'' to '\'', '“' to '”', '‘' to '’', '«' to '»', '„' to '“')

/** Whether [match] declares names, as opposed to a licence sentence about them (see [parseReservedFontNames]). */
private fun isDeclaration(
    text: String,
    match: MatchResult,
): Boolean {
    val before = text.substring(0, match.range.first).trimEnd(' ', '\t')
    val lastWord = before.takeLastWhile { it.isLetter() }.lowercase()
    if (lastWord == "no" || lastWord == "without") return false
    if (before.endsWith("\"") || before.endsWith("“") || before.endsWith("'") || before.endsWith("‘")) return false
    val after = text.substring(match.range.last + 1).trimStart(' ', '\t')
    val afterMarker = after.removePrefix("(s)").trimStart(' ', '\t')
    if (afterMarker.startsWith(":") || before.endsWith("(") || WITH_BEFORE.containsMatchIn(before)) return true
    // A clause of its own (`Copyright 2012 Foo. Reserved Font Name "Bar"`, `Foo, Reserved Font Name Bar`): it counts
    // only when a name follows, which keeps `Reserved Font Names refers to…` and the like out.
    val opensClause = before.isEmpty() || before.last() in CLAUSE_ENDS
    return opensClause && startsLikeAName(afterMarker)
}

/** `with`, `with the`, `with its`… directly before the marker. */
private val WITH_BEFORE = Regex("""\bwith(?:\s+(?:the|a|its|their))?$""", RegexOption.IGNORE_CASE)

private val CLAUSE_ENDS = setOf('.', ',', ';', '\n', '\r')

private fun startsLikeAName(afterMarker: String): Boolean {
    val first = afterMarker.firstOrNull() ?: return false
    return first in OPENING_QUOTES || first.isUpperCase() || first.isDigit()
}

private fun skipSpaces(
    text: String,
    from: Int,
): Int {
    var at = from
    while (at < text.length && (text[at] == ' ' || text[at] == '\t' || text[at] == ' ')) at++
    return at
}

/** After a name: past a `,`, `;`, `and` or `&` that introduces another, or null when the list ends. */
private fun skipSeparator(
    text: String,
    from: Int,
): Int? {
    if (from >= text.length) return null
    val c = text[from]
    return when {
        c == ',' -> {
            skipSpaces(text, skipAnd(text, skipSpaces(text, from + 1)))
        }

        c == '&' -> {
            skipSpaces(text, from + 1)
        }

        text.startsWith("and", from, ignoreCase = true) && from + 3 < text.length && text[from + 3].isWhitespace() -> {
            skipSpaces(text, from + 3)
        }

        else -> {
            null
        }
    }
}

private fun skipAnd(
    text: String,
    from: Int,
): Int = if (text.startsWith("and", from, ignoreCase = true) && from + 3 < text.length && text[from + 3].isWhitespace()) from + 3 else from

/** One name starting at [from]: its text and the index just past it, or null when nothing name-like is there. */
private fun readName(
    text: String,
    from: Int,
): Pair<String, Int>? {
    if (from >= text.length) return null
    val open = text[from]
    val close = OPENING_QUOTES[open]
    if (close != null) {
        var i = from + 1
        while (i < text.length) {
            val c = text[i]
            if (c == '\n' || c == '\r') break
            // A straight single quote inside a name is an apostrophe (Foo's) unless a letter does not follow it.
            val closes = c == close && !(open == '\'' && i + 1 < text.length && text[i + 1].isLetterOrDigit())
            if (closes) return text.substring(from + 1, i) to i + 1
            i++
        }
        return text.substring(from + 1, i) to i
    }
    var i = from
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\n' || c == '\r' || c == '(' || c == ')' || c == ',' || c == ';' || c == '&' -> break

            c == '.' && (i + 1 >= text.length || text[i + 1].isWhitespace() || text[i + 1] == ')') -> break

            c.isWhitespace() &&
                text.startsWith(
                    "and",
                    i + 1,
                    ignoreCase = true,
                ) && i + 4 < text.length && text[i + 4].isWhitespace() -> break
        }
        i++
    }
    if (i == from) return null
    return text.substring(from, i) to i
}
