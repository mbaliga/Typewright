// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

/**
 * [text] without the characters XML 1.0 cannot carry: control characters other than tab, line
 * feed and carriage return (a font's `name` table and `OS/2` vendor id sometimes hold a NUL as
 * padding), the two non-characters U+FFFE and U+FFFF, and a surrogate that is not half of a pair.
 * Every `.plist` and `.glif` this module writes is XML, so a string carrying one of these would
 * make the file unreadable to FontForge, RoboFont, Glyphs and `plistlib` alike. Everything else,
 * including characters outside the Basic Multilingual Plane, is kept as it is.
 *
 * Callers that take strings from outside (a font being imported) clean them with this before they
 * reach a [UfoFontInfo], so the project in memory is already what the file will hold. The writers
 * apply it again as a last line of defence, which means a string with such a character in it reads
 * back without it.
 */
fun withoutXmlIllegalCharacters(text: String): String {
    var firstBad = -1
    for (i in text.indices) {
        if (!isXmlCharacterAt(text, i)) {
            firstBad = i
            break
        }
    }
    if (firstBad < 0) return text
    return buildString(text.length) {
        append(text, 0, firstBad)
        for (i in firstBad until text.length) {
            if (isXmlCharacterAt(text, i)) append(text[i])
        }
    }
}

/** Whether the UTF-16 unit at [index] of [text] belongs to a character XML 1.0 allows (the `Char` production). */
private fun isXmlCharacterAt(
    text: String,
    index: Int,
): Boolean {
    val c = text[index]
    return when {
        c == '\t' || c == '\n' || c == '\r' -> true
        c < ' ' -> false
        c in '\uD800'..'\uDBFF' -> index + 1 < text.length && text[index + 1] in '\uDC00'..'\uDFFF'
        c in '\uDC00'..'\uDFFF' -> index > 0 && text[index - 1] in '\uD800'..'\uDBFF'
        c == '￾' || c == '￿' -> false
        else -> true
    }
}

/**
 * Escapes [text] for use as XML element text content: `&`, `<` and `>` become entities (`>` is
 * escaped defensively — it is only strictly required inside `]]>`), and a carriage return becomes
 * `&#13;` so a parser's end-of-line normalisation cannot turn it into a line feed. Characters XML
 * cannot carry are dropped ([withoutXmlIllegalCharacters]); everything else, including literal
 * newlines, passes through unchanged. Shared by the plist and `.glif` writers so both produce the
 * same, deterministic escaping (byte-stability: the same [PlistValue]/[Contour] always serializes
 * to the same bytes).
 */
internal fun escapeXmlText(text: String): String {
    val safe = withoutXmlIllegalCharacters(text)
    return buildString(safe.length) {
        for (c in safe) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\r' -> append("&#13;")
                else -> append(c)
            }
        }
    }
}

/** Escapes [text] for use inside a double-quoted XML attribute value; characters XML cannot carry are dropped ([withoutXmlIllegalCharacters]). */
internal fun escapeXmlAttribute(text: String): String {
    val safe = withoutXmlIllegalCharacters(text)
    return buildString(safe.length) {
        for (c in safe) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '"' -> append("&quot;")
                '\n' -> append("&#10;")
                '\r' -> append("&#13;")
                '\t' -> append("&#9;")
                else -> append(c)
            }
        }
    }
}
