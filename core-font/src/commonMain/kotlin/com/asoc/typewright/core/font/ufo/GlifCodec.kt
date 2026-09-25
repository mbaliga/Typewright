// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.Point
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.readSimpleElement
import nl.adaptivity.xmlutil.skipElement
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Reads one glyph from a UFO 3 `.glif` (glyph format 2) document.
 *
 * **Scope.** Only what a `core-geometry` [Glyph] can represent is read:
 * - the glyph's `name` and `<advance width="…"/>` become [Glyph.name] and [Glyph.advanceWidth];
 * - `<unicode hex="…"/>` elements become [Glyph.unicodes] in document order. Hex digits are read
 *   case-insensitively, a repeated code point is dropped keeping its first occurrence (as ufoLib
 *   does), and a value that is not hex or lies outside Unicode's code space is an error;
 * - `<contour>` elements become [Contour]s, in either [CurveFormat] (below);
 * - `<anchor name="…" x="…" y="…"/>` elements become [Glyph.anchors] in document order. An anchor
 *   with no `name` is rejected, as a `<glyph>` with no `name` is: an unnamed anchor cannot pair
 *   with a mark's `_name`, which is the one thing an anchor is for;
 * - `<guideline …/>` elements become [Glyph.guidelines] in document order. A structurally invalid
 *   one (an `angle` without both `x` and `y`, or a non-numeric value) is rejected; see [Guideline];
 * - `<lib>` is read only for [LINE_CONTOUR_FORMAT_LIB_KEY]; its other keys, `<image>` and `<note>`
 *   are skipped, since [Glyph] has nowhere to keep them;
 * - a `<point>`'s `smooth`, `name` and `identifier` attributes are dropped for the same reason.
 *
 * **Points.** `type="line"`, `"curve"` and `"qcurve"` mark on-curve points; a point with no type,
 * or with `type="offcurve"`, is off-curve. `type="move"` (an open contour) has no [Contour] to
 * become and is rejected. A `line` point that follows an off-curve point (cyclically, since every
 * contour here is closed) is rejected, as the spec forbids it, and so is a contour that mixes
 * `curve` and `qcurve` points.
 *
 * **Which format each contour gets.**
 * - A contour with a `qcurve` point, or with no typed point at all (TrueType's all-implied
 *   contour), is [CurveFormat.QUADRATIC]: its points are kept in file order, start point
 *   included, and implied on-curve points stay implied.
 * - A contour with a `curve` point is [CurveFormat.CUBIC], in `core-geometry`'s normal form: the
 *   list is rotated to start at its first on-curve point, and each `line` segment becomes a
 *   degenerate cubic whose controls sit on its own two ends (`control1 = start`,
 *   `control2 = end`), which traces the same straight path. [writeGlif] writes that pattern back
 *   as a plain `line` point, so the round trip is lossless.
 * - A contour of `line` points only reads identically as either format, so it takes the glyph's
 *   format: [CurveFormat.QUADRATIC] if any contour of the glyph is quadratic by the first rule,
 *   else [CurveFormat.CUBIC] if any has a `curve` point, else the glyph's own
 *   [LINE_CONTOUR_FORMAT_LIB_KEY], else [lineContourDefault] (the project's `lib.plist` value,
 *   which [readUfoProject] passes in), which itself defaults to [CurveFormat.CUBIC].
 */
fun parseGlif(
    xml: String,
    lineContourDefault: CurveFormat = CurveFormat.CUBIC,
): Glyph {
    val reader = xmlStreaming.newGenericReader(xml, expandEntities = true)
    var event = skipToStartOrEnd(reader, reader.next())
    require(event == EventType.START_ELEMENT && reader.localName == "glyph") {
        "expected a .glif document (root element <glyph>), found <${reader.localName}>"
    }
    val name =
        reader.getAttributeValue(null, "name")
            ?: throw IllegalArgumentException("<glyph> is missing its required 'name' attribute")

    var advanceWidth = 0
    val unicodes = mutableListOf<Int>()
    var rawContours: List<List<RawPoint>> = emptyList()
    val anchors = mutableListOf<Anchor>()
    val guidelines = mutableListOf<Guideline>()
    var glyphLineFormat: CurveFormat? = null

    event = skipToStartOrEnd(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT)
        when (reader.localName) {
            "advance" -> {
                advanceWidth = reader.getAttributeValue(null, "width")?.let { parseCoordinate(it) } ?: 0
                reader.skipElement()
            }

            "unicode" -> {
                val hex =
                    reader.getAttributeValue(null, "hex")
                        ?: throw IllegalArgumentException("<unicode> is missing its required 'hex' attribute")
                val codePoint = parseUnicodeHex(hex)
                if (codePoint !in unicodes) unicodes += codePoint
                reader.skipElement()
            }

            "outline" -> {
                rawContours = readOutline(reader)
            }

            "anchor" -> {
                anchors += readAnchor(reader)
            }

            "guideline" -> {
                guidelines += readGuideline(reader)
            }

            "lib" -> {
                glyphLineFormat = readGlyphLib(reader)
            }

            else -> {
                reader.skipElement()
            } // image, note: not modelled, see KDoc
        }
        event = skipToStartOrEnd(reader, reader.next())
    }

    val ownFormats = rawContours.map { ownFormat(it) }
    val glyphFormat =
        when {
            CurveFormat.QUADRATIC in ownFormats -> CurveFormat.QUADRATIC
            CurveFormat.CUBIC in ownFormats -> CurveFormat.CUBIC
            else -> glyphLineFormat ?: lineContourDefault
        }
    val contours =
        rawContours.mapIndexed { i, raw ->
            when (ownFormats[i] ?: glyphFormat) {
                CurveFormat.QUADRATIC -> buildQuadraticContour(raw)
                CurveFormat.CUBIC -> buildCubicContour(raw)
            }
        }
    return Glyph(name, advanceWidth, contours, anchors, guidelines, unicodes)
}

/**
 * Parses a `<unicode>` element's `hex` value: one or more hex digits in either case (surrounding
 * whitespace allowed), naming a code point in Unicode's code space.
 */
private fun parseUnicodeHex(text: String): Int {
    val digits = text.trim()
    require(digits.isNotEmpty() && digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "<unicode hex=\"$text\"> is not a hexadecimal code point"
    }
    val significant = digits.trimStart('0')
    require(significant.length <= 6 && (significant.isEmpty() || significant.toInt(16) <= 0x10FFFF)) {
        "<unicode hex=\"$text\"> is outside Unicode's code space (0 to 10FFFF)"
    }
    return if (significant.isEmpty()) 0 else significant.toInt(16)
}

/**
 * Reads a glif `<lib>` element for [LINE_CONTOUR_FORMAT_LIB_KEY] only, skipping every other key's
 * value unparsed (other tools may store any plist value there, `<data>` included).
 */
private fun readGlyphLib(reader: XmlReader): CurveFormat? {
    var event = skipToStartOrEnd(reader, reader.next())
    if (event == EventType.END_ELEMENT) return null // an empty <lib/>
    require(reader.localName == "dict") { "expected <dict> inside a glyph <lib>, found <${reader.localName}>" }
    var format: CurveFormat? = null
    event = skipToStartOrEnd(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(reader.localName == "key") { "expected <key> inside a glyph <lib> <dict>, found <${reader.localName}>" }
        val key = reader.readSimpleElement()
        event = skipToStartOrEnd(reader, reader.next())
        require(event == EventType.START_ELEMENT) { "expected a value after <key>$key</key> in a glyph <lib>" }
        if (key == LINE_CONTOUR_FORMAT_LIB_KEY) {
            require(reader.localName == "string") {
                "glyph <lib> '$LINE_CONTOUR_FORMAT_LIB_KEY' must be a <string>, found <${reader.localName}>"
            }
            format = parseLineContourFormat(reader.readSimpleElement().trim(), "glyph <lib>")
        } else {
            reader.skipElement()
        }
        event = skipToStartOrEnd(reader, reader.next())
    }
    event = skipToStartOrEnd(reader, reader.next())
    require(event == EventType.END_ELEMENT) { "a glyph <lib> must hold exactly one <dict>, found <${reader.localName}> after it" }
    return format
}

private fun readAnchor(reader: XmlReader): Anchor {
    val name =
        reader.getAttributeValue(null, "name")
            ?: throw IllegalArgumentException("<anchor> is missing its required 'name' attribute")
    val x = parseCoordinate(reader.getAttributeValue(null, "x") ?: throw IllegalArgumentException("<anchor> is missing 'x'"))
    val y = parseCoordinate(reader.getAttributeValue(null, "y") ?: throw IllegalArgumentException("<anchor> is missing 'y'"))
    reader.skipElement()
    return Anchor(name, Point(x, y))
}

private fun readGuideline(reader: XmlReader): Guideline {
    val x = reader.getAttributeValue(null, "x")?.let { parseGuidelineNumber(it, "x") }
    val y = reader.getAttributeValue(null, "y")?.let { parseGuidelineNumber(it, "y") }
    val angle = reader.getAttributeValue(null, "angle")?.let { parseGuidelineNumber(it, "angle") }
    val name = reader.getAttributeValue(null, "name")
    val color = reader.getAttributeValue(null, "color")
    val identifier = reader.getAttributeValue(null, "identifier")
    reader.skipElement()
    return Guideline(x, y, angle, name, color, identifier)
}

private fun parseGuidelineNumber(
    text: String,
    attribute: String,
): Double =
    parseRealOrNull(text)
        ?: throw IllegalArgumentException("<guideline> attribute '$attribute' must be numeric, found '$text'")

private fun skipToStartOrEnd(
    reader: XmlReader,
    firstEvent: EventType,
): EventType {
    var event = firstEvent
    while (event != EventType.START_ELEMENT && event != EventType.END_ELEMENT) event = reader.next()
    return event
}

private fun parseCoordinate(text: String): Int =
    parseRealOrNull(text)?.roundToInt()
        ?: throw IllegalArgumentException("expected a numeric coordinate, found '$text'")

private fun readOutline(reader: XmlReader): List<List<RawPoint>> {
    val contours = mutableListOf<List<RawPoint>>()
    var event = skipToStartOrEnd(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT && reader.localName == "contour") {
            "expected <contour> inside <outline>, found <${reader.localName}>"
        }
        contours += readContourPoints(reader)
        event = skipToStartOrEnd(reader, reader.next())
    }
    return contours
}

/** One `<point>` as written: its location, and its segment type, `null` for an off-curve point. */
private class RawPoint(
    val point: Point,
    val type: String?,
)

private val ON_CURVE_TYPES = setOf("move", "line", "curve", "qcurve")

/** Reads one `<contour>`'s points and checks the rules every contour shares, whichever format it becomes. */
private fun readContourPoints(reader: XmlReader): List<RawPoint> {
    val raw = mutableListOf<RawPoint>()
    var event = skipToStartOrEnd(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT && reader.localName == "point") {
            "expected <point> inside <contour>, found <${reader.localName}>"
        }
        val x = parseCoordinate(reader.getAttributeValue(null, "x") ?: throw IllegalArgumentException("<point> is missing 'x'"))
        val y = parseCoordinate(reader.getAttributeValue(null, "y") ?: throw IllegalArgumentException("<point> is missing 'y'"))
        val type =
            when (val written = reader.getAttributeValue(null, "type")) {
                null, "offcurve" -> null
                in ON_CURVE_TYPES -> written
                else -> throw IllegalArgumentException("<point type=\"$written\"> is not a UFO point type")
            }
        raw += RawPoint(Point(x, y), type)
        reader.skipElement()
        event = skipToStartOrEnd(reader, reader.next())
    }
    require(raw.isNotEmpty()) { "a <contour> must have at least one <point>" }
    require(raw.none { it.type == "move" }) {
        "open contours (a <point type=\"move\"/>) are not supported; core-geometry's Contour models only closed contours"
    }
    for (i in raw.indices) {
        require(raw[i].type != "line" || raw[(i - 1 + raw.size) % raw.size].type != null) {
            "<point type=\"line\"> at index $i follows an off-curve point; a line segment has no control points"
        }
    }
    require(raw.none { it.type == "curve" } || raw.none { it.type == "qcurve" }) {
        "a <contour> mixes type=\"curve\" and type=\"qcurve\" points; one contour holds one curve format"
    }
    return raw
}

/** The format [raw]'s own points decide (see [parseGlif]), or `null` for a contour of `line` points only. */
private fun ownFormat(raw: List<RawPoint>): CurveFormat? =
    when {
        raw.any { it.type == "qcurve" } -> CurveFormat.QUADRATIC
        raw.any { it.type == "curve" } -> CurveFormat.CUBIC
        raw.all { it.type == null } -> CurveFormat.QUADRATIC
        else -> null
    }

/** [raw] as a quadratic contour: file order kept, typed points on-curve, the rest off-curve. */
private fun buildQuadraticContour(raw: List<RawPoint>): Contour =
    Contour(raw.map { ContourPoint(it.point, onCurve = it.type != null) }, CurveFormat.QUADRATIC)

private class Vertex(
    val point: Point,
    val type: String,
    val incomingOffs: List<Point>,
)

/** [raw] as a cubic contour in `core-geometry`'s normal form; see [parseGlif]'s KDoc for the convention this reconstructs. */
private fun buildCubicContour(raw: List<RawPoint>): Contour {
    val startIndex = raw.indexOfFirst { it.type != null }
    val rotated = raw.drop(startIndex) + raw.take(startIndex)

    val vertices = mutableListOf<Vertex>()
    var pending = mutableListOf<Point>()
    for (p in rotated) {
        val type = p.type
        if (type == null) {
            pending.add(p.point)
        } else {
            vertices += Vertex(p.point, type, pending)
            pending = mutableListOf()
        }
    }
    // The off-curve points collected after the last on-curve point wrap around cyclically to
    // become the *first* vertex's incoming controls, replacing the empty placeholder it was given
    // when the main loop visited it (before the wrap-around points were known).
    val wrapIncoming = pending
    val m = vertices.size

    val points = mutableListOf<ContourPoint>()
    for (i in 0 until m) {
        val current = vertices[i]
        val next = vertices[(i + 1) % m]
        val nextIncoming = if (i + 1 == m) wrapIncoming else next.incomingOffs
        val (c1, c2) =
            when {
                next.type == "curve" && nextIncoming.size == 2 -> {
                    nextIncoming[0] to nextIncoming[1]
                }

                next.type == "line" && nextIncoming.isEmpty() -> {
                    current.point to next.point
                }

                else -> {
                    throw IllegalArgumentException(
                        "<point type=\"${next.type}\"> with ${nextIncoming.size} preceding off-curve points " +
                            "is not a supported cubic segment (expected 0 for \"line\" or 2 for \"curve\")",
                    )
                }
            }
        points += ContourPoint(current.point, onCurve = true)
        points += ContourPoint(c1, onCurve = false)
        points += ContourPoint(c2, onCurve = false)
    }
    return Contour(points, CurveFormat.CUBIC)
}

/**
 * Writes [glyph] as a `.glif` (glyph format 2) document, byte-stably, in the element order
 * fontTools writes: `<advance>`, `<unicode>`s, `<guideline>`s, `<anchor>`s, `<outline>`, `<lib>`.
 * Unicodes are written `%04X`, in list order, so the primary one stays first.
 *
 * **Cubic contours** are written as `curve` and `line` points, a degenerate triple (see
 * [parseGlif]) becoming a plain `line` point with no controls.
 *
 * **Quadratic contours** keep TrueType's point order verbatim, including a start point that is
 * off-curve, and implied on-curve points stay implied, since UFO's `qcurve` keeps TrueType's
 * implied-midpoint rule:
 * - an off-curve point is written with no type;
 * - an on-curve point whose cyclic predecessor is off-curve is `type="qcurve"`;
 * - an on-curve point after an on-curve point is `type="line"`;
 * - so a contour with no on-curve point at all is written with every point untyped.
 *
 * **Straight glyphs.** A contour made only of straight segments is written as `line` points in
 * either format, so the file cannot say which format it was. [parseGlif] resolves it from the
 * glyph's other contours when they decide, and otherwise from the glyph's
 * [LINE_CONTOUR_FORMAT_LIB_KEY] or the project's [lineContourDefault]. This writer adds a `<lib>`
 * holding that key exactly when the glyph's contours are all straight and their format differs
 * from [lineContourDefault]. A glyph whose straight contours cannot all be read back in their own
 * format (a straight contour of one format beside curved contours of the other, or straight
 * contours of both formats) is refused rather than written in a way that reads back different.
 */
fun writeGlif(
    glyph: Glyph,
    lineContourDefault: CurveFormat = CurveFormat.CUBIC,
): String {
    val glyphLibFormat = glyphLibLineContourFormat(glyph, lineContourDefault)
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<glyph name=\"").append(escapeXmlAttribute(glyph.name)).append("\" format=\"2\">\n")
        append("  <advance width=\"").append(glyph.advanceWidth).append("\"/>\n")
        for (codePoint in glyph.unicodes) {
            append("  <unicode hex=\"").append(codePoint.toString(16).uppercase().padStart(4, '0')).append("\"/>\n")
        }
        for (guideline in glyph.guidelines) writeGuideline(this, guideline)
        for (anchor in glyph.anchors) {
            append("  <anchor x=\"")
                .append(anchor.point.x)
                .append("\" y=\"")
                .append(anchor.point.y)
                .append("\" name=\"")
                .append(escapeXmlAttribute(anchor.name))
                .append("\"/>\n")
        }
        if (glyph.contours.isNotEmpty()) {
            append("  <outline>\n")
            for (contour in glyph.contours) {
                when (contour.format) {
                    CurveFormat.CUBIC -> writeCubicContour(this, contour)
                    CurveFormat.QUADRATIC -> writeQuadraticContour(this, contour)
                }
            }
            append("  </outline>\n")
        }
        if (glyphLibFormat != null) {
            append("  <lib>\n")
            append("    <dict>\n")
            append("      <key>").append(LINE_CONTOUR_FORMAT_LIB_KEY).append("</key>\n")
            append("      <string>").append(glyphLibFormat.libValue()).append("</string>\n")
            append("    </dict>\n")
            append("  </lib>\n")
        }
        append("</glyph>\n")
    }
}

/**
 * The [LINE_CONTOUR_FORMAT_LIB_KEY] value [glyph]'s `.glif` must carry so [parseGlif] reads every
 * contour back in its own format, or `null` when none is needed; refuses a glyph no `.glif` can
 * say that for (see [writeGlif]).
 */
private fun glyphLibLineContourFormat(
    glyph: Glyph,
    lineContourDefault: CurveFormat,
): CurveFormat? {
    val decided = glyph.contours.mapNotNull { it.writtenOwnFormat() }
    val decidedFormat =
        when {
            CurveFormat.QUADRATIC in decided -> CurveFormat.QUADRATIC
            CurveFormat.CUBIC in decided -> CurveFormat.CUBIC
            else -> null
        }
    val straightFormats =
        glyph.contours
            .filter { it.writtenOwnFormat() == null }
            .map { it.format }
            .distinct()
    if (straightFormats.isEmpty()) return null
    val readBackFormat = decidedFormat ?: straightFormats.first()
    require(straightFormats == listOf(readBackFormat)) {
        "glyph '${glyph.name}' mixes curve formats in a way a .glif cannot keep: its straight contours are " +
            "${straightFormats.joinToString(" and ")} and would read back as $readBackFormat; one glyph holds one curve format"
    }
    return if (decidedFormat == null && readBackFormat != lineContourDefault) readBackFormat else null
}

/**
 * The format [parseGlif] will see in this contour's own written points: [CurveFormat.QUADRATIC]
 * when it has an off-curve point (it is written with a `qcurve`, or with no typed point),
 * [CurveFormat.CUBIC] when it has a non-degenerate cubic segment (written as `curve`), and `null`
 * when every segment is straight and it is written as `line` points only.
 */
private fun Contour.writtenOwnFormat(): CurveFormat? =
    when (format) {
        CurveFormat.QUADRATIC -> if (points.any { !it.onCurve }) CurveFormat.QUADRATIC else null
        CurveFormat.CUBIC -> if ((0 until points.size / 3).all { isLineTriple(it) }) null else CurveFormat.CUBIC
    }

/** Whether this cubic contour's triple [i] is the degenerate straight segment [parseGlif] builds from a `line` point. */
private fun Contour.isLineTriple(i: Int): Boolean {
    val m = points.size / 3
    return points[3 * i + 1].point == points[3 * i].point && points[3 * i + 2].point == points[3 * ((i + 1) % m)].point
}

/** Writes one `<guideline .../>` element (self-closing; the spec gives it no child elements), in the attribute order [Guideline] declares them. */
private fun writeGuideline(
    out: StringBuilder,
    guideline: Guideline,
) {
    out.append("  <guideline")
    guideline.x?.let { out.append(" x=\"").append(formatGuidelineNumber(it)).append('"') }
    guideline.y?.let { out.append(" y=\"").append(formatGuidelineNumber(it)).append('"') }
    guideline.angle?.let { out.append(" angle=\"").append(formatGuidelineNumber(it)).append('"') }
    guideline.name?.let { out.append(" name=\"").append(escapeXmlAttribute(it)).append('"') }
    guideline.color?.let { out.append(" color=\"").append(escapeXmlAttribute(it)).append('"') }
    guideline.identifier?.let { out.append(" identifier=\"").append(escapeXmlAttribute(it)).append('"') }
    out.append("/>\n")
}

/**
 * Formats a guideline `x`/`y`/`angle` value as a `.glif` XML attribute: a whole number is written
 * without a trailing `.0` (matching how real UFO tools write, e.g., `x="500"` rather than
 * `x="500.0"`); any other value is written by [formatReal], as fontTools writes it and the same on
 * every platform. Mirrors [numericPlistValue]'s integer-vs-real choice for the same values in
 * `fontinfo.plist`'s `guidelines` list, just as XML attribute text rather than a [PlistValue].
 */
private fun formatGuidelineNumber(value: Double): String =
    if (value.isFinite() && value == floor(value) && abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        formatReal(value)
    }

/** Writes one `<point x="…" y="…"[ type="…"]/>` line; [type] `null` writes an off-curve point. */
private fun writePoint(
    out: StringBuilder,
    point: Point,
    type: String?,
) {
    out
        .append("      <point x=\"")
        .append(point.x)
        .append("\" y=\"")
        .append(point.y)
        .append('"')
    if (type != null) out.append(" type=\"").append(type).append('"')
    out.append("/>\n")
}

private fun writeCubicContour(
    out: StringBuilder,
    contour: Contour,
) {
    val points = contour.points
    val m = points.size / 3
    out.append("    <contour>\n")
    for (i in 0 until m) {
        val incomingIsLine = contour.isLineTriple((i - 1 + m) % m)
        writePoint(out, points[3 * i].point, if (incomingIsLine) "line" else "curve")
        if (!contour.isLineTriple(i)) {
            writePoint(out, points[3 * i + 1].point, null)
            writePoint(out, points[3 * i + 2].point, null)
        }
    }
    out.append("    </contour>\n")
}

private fun writeQuadraticContour(
    out: StringBuilder,
    contour: Contour,
) {
    val points = contour.points
    val n = points.size
    out.append("    <contour>\n")
    for (i in 0 until n) {
        val current = points[i]
        val type =
            when {
                !current.onCurve -> null
                !points[(i - 1 + n) % n].onCurve -> "qcurve"
                else -> "line"
            }
        writePoint(out, current.point, type)
    }
    out.append("    </contour>\n")
}
