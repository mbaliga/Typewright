package dev.aarso.typewright.core.font.ufo

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.skipElement
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.math.roundToInt

/**
 * Reads and writes one glyph as a UFO 3 `.glif` (glyph format 2) file, using `core-geometry`'s
 * [CurveFormat.CUBIC] [Contour] as the in-memory shape (see that type's own KDoc for the "starts
 * on-curve, (on, off, off) triples" contract this codec produces and consumes).
 *
 * **Scope.** Only what a `core-geometry` [Glyph] can actually represent is read or written:
 * - a glyph's `name` and `<advance width="…"/>` map directly to [Glyph.name]/[Glyph.advanceWidth];
 * - `<contour>` elements map to [Contour]s of [CurveFormat.CUBIC];
 * - `<unicode>`, `<anchor>`, `<guideline>`, `<image>`, `<lib>` and `<note>` are skipped on read
 *   (nothing in [Glyph] has anywhere to put them) and never written;
 * - a `<point>`'s `smooth`, `name` and `identifier` attributes are dropped on read, for the same
 *   reason ([ContourPoint] carries only a location and on/off-curve flag).
 *
 * **Point types.** UFO close contours mix `type="curve"` (2 preceding off-curve controls) and
 * `type="line"` (0 preceding controls) points; `core-geometry`'s CUBIC contour has no room for a
 * zero-control segment (every on-curve point owns exactly 2 off-curve points). [parseGlif]
 * reconciles this by reading a `"line"` segment as a *degenerate* cubic whose two controls sit
 * exactly on its own start and end anchors (`control1 = start`, `control2 = end`) — this traces
 * the identical straight path a true line segment would (the point at parameter `t` is a convex
 * combination of only those two anchors for every `t`; see this file's tests), just with a
 * curve-eased rather than linear parametrization along it, which does not change the outline's
 * shape. [writeGlif] detects that exact degenerate pattern and writes it back out as `type="line"`
 * with no control points, so a file this codec wrote and re-read (or a genuinely
 * straight-segment file written by another tool) round-trips losslessly. `type="qcurve"`
 * (TrueType-style quadratic points embedded in a UFO file) and `type="move"` (an open contour)
 * have no representation in [CurveFormat.CUBIC] at all and are rejected with a clear message
 * rather than silently misread.
 */
fun parseGlif(xml: String): Glyph {
    val reader = xmlStreaming.newGenericReader(xml, expandEntities = true)
    var event = skipToStartOrEnd(reader, reader.next())
    require(event == EventType.START_ELEMENT && reader.localName == "glyph") {
        "expected a .glif document (root element <glyph>), found <${reader.localName}>"
    }
    val name =
        reader.getAttributeValue(null, "name")
            ?: throw IllegalArgumentException("<glyph> is missing its required 'name' attribute")

    var advanceWidth = 0
    var contours: List<Contour> = emptyList()

    event = skipToStartOrEnd(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT)
        when (reader.localName) {
            "advance" -> {
                advanceWidth = reader.getAttributeValue(null, "width")?.let { parseCoordinate(it) } ?: 0
                reader.skipElement()
            }

            "outline" -> {
                contours = readOutline(reader)
            }

            else -> {
                reader.skipElement()
            } // unicode, anchor, guideline, image, lib, note: not modelled, see KDoc
        }
        event = skipToStartOrEnd(reader, reader.next())
    }
    return Glyph(name, advanceWidth, contours)
}

private fun skipToStartOrEnd(
    reader: XmlReader,
    firstEvent: EventType,
): EventType {
    var event = firstEvent
    while (event != EventType.START_ELEMENT && event != EventType.END_ELEMENT) event = reader.next()
    return event
}

private fun parseCoordinate(text: String): Int =
    text.toDoubleOrNull()?.roundToInt()
        ?: throw IllegalArgumentException("expected a numeric coordinate, found '$text'")

private fun readOutline(reader: XmlReader): List<Contour> {
    val contours = mutableListOf<Contour>()
    var event = skipToStartOrEnd(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT && reader.localName == "contour") {
            "expected <contour> inside <outline>, found <${reader.localName}>"
        }
        contours += readContour(reader)
        event = skipToStartOrEnd(reader, reader.next())
    }
    return contours
}

private class RawPoint(
    val point: Point,
    val type: String?,
)

private fun readContour(reader: XmlReader): Contour {
    val raw = mutableListOf<RawPoint>()
    var event = skipToStartOrEnd(reader, reader.next())
    while (event != EventType.END_ELEMENT) {
        require(event == EventType.START_ELEMENT && reader.localName == "point") {
            "expected <point> inside <contour>, found <${reader.localName}>"
        }
        val x = parseCoordinate(reader.getAttributeValue(null, "x") ?: throw IllegalArgumentException("<point> is missing 'x'"))
        val y = parseCoordinate(reader.getAttributeValue(null, "y") ?: throw IllegalArgumentException("<point> is missing 'y'"))
        val type = reader.getAttributeValue(null, "type")
        raw += RawPoint(Point(x, y), type)
        reader.skipElement()
        event = skipToStartOrEnd(reader, reader.next())
    }
    return buildCubicContour(raw)
}

private class Vertex(
    val point: Point,
    val type: String,
    val incomingOffs: List<Point>,
)

/** See [parseGlif]'s KDoc for the point-list convention this reconstructs. */
private fun buildCubicContour(raw: List<RawPoint>): Contour {
    require(raw.isNotEmpty()) { "a <contour> must have at least one <point>" }
    for (p in raw) {
        require(p.type != "move") {
            "open contours (a <point type=\"move\"/>) are not supported; core-geometry's Contour models only closed contours"
        }
        require(p.type != "qcurve") {
            "quadratic points in a UFO <contour> (<point type=\"qcurve\"/>) are not supported by this cubic-only reader"
        }
    }

    val startIndex = raw.indexOfFirst { it.type != null }
    require(startIndex >= 0) { "a fully off-curve <contour> has no on-curve point to start a cubic contour at" }
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

/** Writes [glyph] as a `.glif` (glyph format 2) document; see the file's top KDoc for scope and round-trip behaviour. */
fun writeGlif(glyph: Glyph): String {
    require(glyph.contours.all { it.format == CurveFormat.CUBIC }) {
        "writeGlif only writes CUBIC contours; glyph '${glyph.name}' has a QUADRATIC one " +
            "(convert with a fitter before writing to UFO — out of core-font's scope, see docs/ARCHITECTURE_REVIEW.md)"
    }
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<glyph name=\"").append(escapeXmlAttribute(glyph.name)).append("\" format=\"2\">\n")
        append("  <advance width=\"").append(glyph.advanceWidth).append("\"/>\n")
        if (glyph.contours.isNotEmpty()) {
            append("  <outline>\n")
            for (contour in glyph.contours) writeContour(this, contour)
            append("  </outline>\n")
        }
        append("</glyph>\n")
    }
}

private fun writeContour(
    out: StringBuilder,
    contour: Contour,
) {
    val points = contour.points
    val m = points.size / 3

    fun onPoint(i: Int) = points[3 * i].point

    fun isLineTriple(i: Int): Boolean {
        val c1 = points[3 * i + 1].point
        val c2 = points[3 * i + 2].point
        return c1 == onPoint(i) && c2 == onPoint((i + 1) % m)
    }

    out.append("    <contour>\n")
    for (i in 0 until m) {
        val incomingIsLine = isLineTriple((i - 1 + m) % m)
        val on = onPoint(i)
        out
            .append("      <point x=\"")
            .append(on.x)
            .append("\" y=\"")
            .append(on.y)
            .append("\" type=\"")
            .append(if (incomingIsLine) "line" else "curve")
            .append("\"/>\n")
        if (!isLineTriple(i)) {
            val c1 = points[3 * i + 1].point
            val c2 = points[3 * i + 2].point
            out
                .append("      <point x=\"")
                .append(c1.x)
                .append("\" y=\"")
                .append(c1.y)
                .append("\"/>\n")
            out
                .append("      <point x=\"")
                .append(c2.x)
                .append("\" y=\"")
                .append(c2.y)
                .append("\"/>\n")
        }
    }
    out.append("    </contour>\n")
}
