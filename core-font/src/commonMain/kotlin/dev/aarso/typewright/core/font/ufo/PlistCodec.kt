package dev.aarso.typewright.core.font.ufo

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.readSimpleElement
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * Parses [xml] as a plist document and returns its root value (`fontinfo.plist`,
 * `contents.plist` and `metainfo.plist` root a `<dict>`; `layercontents.plist` roots an
 * `<array>`, which is why this returns the general [PlistValue] rather than requiring a dict —
 * see [parsePlistDict] for the common "I know it's a dict" case). Uses xmlutil's [xmlStreaming]
 * pull parser (docs/ARCHITECTURE_REVIEW.md section 3 `:core-font`, section 7 item 4) rather than
 * a hand-written one, since parsing arbitrary well-formed XML correctly (entities, whitespace,
 * comments) is exactly the part not worth re-deriving; only *writing* plists is done by hand (see
 * [writePlist]), which is easy to keep byte-stable because this module controls every byte it
 * emits.
 */
fun parsePlist(xml: String): PlistValue {
    val reader = xmlStreaming.newGenericReader(xml, expandEntities = true)
    var event = skipToNextStartElement(reader, reader.next())
    require(event == EventType.START_ELEMENT && reader.localName == "plist") {
        "expected a plist document (root element <plist>), found <${reader.localName}>"
    }
    event = skipToNextStartElement(reader, reader.next())
    require(event == EventType.START_ELEMENT) { "expected <plist> to contain one value element" }
    return readPlistValue(reader)
}

/** [parsePlist], requiring (and returning) a `<dict>`-rooted plist — the common case for every UFO plist except `layercontents.plist`. */
fun parsePlistDict(xml: String): PlistValue.PDict {
    val root = parsePlist(xml)
    require(root is PlistValue.PDict) { "expected the plist's root value to be a <dict>, found <${rootElementName(root)}>" }
    return root
}

private fun rootElementName(value: PlistValue): String =
    when (value) {
        is PlistValue.PDict -> "dict"
        is PlistValue.PArray -> "array"
        is PlistValue.PString -> "string"
        is PlistValue.PInteger -> "integer"
        is PlistValue.PReal -> "real"
        is PlistValue.PBoolean -> "true/false"
    }

/** Advances past ignorable events (whitespace, comments, the document preamble) to the next [EventType.START_ELEMENT] or [EventType.END_ELEMENT]. */
private fun skipToNextStartElement(
    reader: XmlReader,
    firstEvent: EventType,
): EventType {
    var event = firstEvent
    while (event != EventType.START_ELEMENT && event != EventType.END_ELEMENT) {
        event = reader.next()
    }
    return event
}

/** Reads the plist value element the [reader] is currently positioned at (a [EventType.START_ELEMENT]), consuming through its matching end tag. */
private fun readPlistValue(reader: XmlReader): PlistValue =
    when (reader.localName) {
        "dict" -> {
            readPlistDict(reader)
        }

        "array" -> {
            readPlistArray(reader)
        }

        "string" -> {
            PlistValue.PString(reader.readSimpleElement())
        }

        "integer" -> {
            PlistValue.PInteger(reader.readSimpleElement().trim().toLong())
        }

        "real" -> {
            PlistValue.PReal(reader.readSimpleElement().trim().toDouble())
        }

        "true" -> {
            reader.readSimpleElement()
            PlistValue.PBoolean(true)
        }

        "false" -> {
            reader.readSimpleElement()
            PlistValue.PBoolean(false)
        }

        "data", "date" -> {
            throw IllegalArgumentException("plist <${reader.localName}> values are not supported by core-font")
        }

        else -> {
            throw IllegalArgumentException("unrecognised plist element <${reader.localName}>")
        }
    }

private fun readPlistDict(reader: XmlReader): PlistValue.PDict {
    val entries = mutableListOf<Pair<String, PlistValue>>()
    var event = skipToNextStartElement(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT && reader.localName == "key") {
            "expected <key> inside <dict>, found <${reader.localName}>"
        }
        val key = reader.readSimpleElement()
        event = skipToNextStartElement(reader, reader.next())
        require(event == EventType.START_ELEMENT) { "expected a value element after <key>$key</key>" }
        entries += key to readPlistValue(reader)
        event = skipToNextStartElement(reader, reader.next())
    }
    return PlistValue.PDict(entries)
}

private fun readPlistArray(reader: XmlReader): PlistValue.PArray {
    val items = mutableListOf<PlistValue>()
    var event = skipToNextStartElement(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT) { "expected a value element inside <array>, found something else" }
        items += readPlistValue(reader)
        event = skipToNextStartElement(reader, reader.next())
    }
    return PlistValue.PArray(items)
}

/**
 * Writes [root] as a complete plist document, byte-stably: the same [PlistValue] tree always
 * produces the same bytes (no timestamps, no map/set iteration order — [PlistValue.PDict]'s
 * entries are an ordered list precisely so this holds). Two-space indentation per nesting level;
 * the UFO 3 spec treats a plist as XML, not as a byte-for-byte format, so this whitespace choice is
 * ours to make as long as the result parses back to the same value (round-tripped by this file's
 * tests) and opens in a real plist reader (`plistlib`/ufoLib; verified manually, see
 * `docs/ARCHITECTURE_REVIEW.md` section 3 `:core-font` risk 1). [root] may be any [PlistValue]:
 * most UFO plists root a `<dict>`, but `layercontents.plist` roots an `<array>`.
 */
fun writePlist(root: PlistValue): String =
    buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n")
        append("<plist version=\"1.0\">\n")
        writePlistValue(this, root, 0)
        append('\n')
        append("</plist>\n")
    }

private fun writePlistValue(
    out: StringBuilder,
    value: PlistValue,
    depth: Int,
) {
    val indent = "  ".repeat(depth)
    when (value) {
        is PlistValue.PDict -> {
            out.append(indent).append("<dict>")
            for ((key, child) in value.entries) {
                out
                    .append('\n')
                    .append(indent)
                    .append("  <key>")
                    .append(escapeXmlText(key))
                    .append("</key>\n")
                writePlistValue(out, child, depth + 1)
            }
            if (value.entries.isNotEmpty()) out.append('\n').append(indent)
            out.append("</dict>")
        }

        is PlistValue.PArray -> {
            out.append(indent).append("<array>")
            for (item in value.items) {
                out.append('\n')
                writePlistValue(out, item, depth + 1)
            }
            if (value.items.isNotEmpty()) out.append('\n').append(indent)
            out.append("</array>")
        }

        is PlistValue.PString -> {
            out
                .append(indent)
                .append("<string>")
                .append(escapeXmlText(value.value))
                .append("</string>")
        }

        is PlistValue.PInteger -> {
            out
                .append(indent)
                .append("<integer>")
                .append(value.value)
                .append("</integer>")
        }

        is PlistValue.PReal -> {
            out
                .append(indent)
                .append("<real>")
                .append(formatPlistReal(value.value))
                .append("</real>")
        }

        is PlistValue.PBoolean -> {
            out.append(indent).append(if (value.value) "<true/>" else "<false/>")
        }
    }
}

/**
 * Formats a real number as Kotlin's own [Double.toString] would (`-12.5`, `0.0`, or, for a
 * magnitude no font metric ever reaches, exponent notation like `1.0E10`). Both this reader's
 * `.toDouble()` and `plistlib`'s `float()` parse exponent notation without trouble, so there is no
 * need to expand it by hand here.
 */
private fun formatPlistReal(value: Double): String {
    if (value.isNaN() || value.isInfinite()) {
        throw IllegalArgumentException("plist <real> cannot represent NaN or an infinite value")
    }
    return value.toString()
}
