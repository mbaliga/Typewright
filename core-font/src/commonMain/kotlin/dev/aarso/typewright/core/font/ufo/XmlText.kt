package dev.aarso.typewright.core.font.ufo

/**
 * Escapes [text] for use as XML element text content: `&`, `<` and `>` become entities (`>` is
 * escaped defensively — it is only strictly required inside `]]>` — everything else, including
 * literal newlines, passes through unchanged). Shared by the plist and `.glif` writers so both
 * produce the same, deterministic escaping (byte-stability: the same [PlistValue]/[Contour]
 * always serializes to the same bytes).
 */
internal fun escapeXmlText(text: String): String =
    buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }

/** Escapes [text] for use inside a double-quoted XML attribute value. */
internal fun escapeXmlAttribute(text: String): String =
    buildString(text.length) {
        for (c in text) {
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
