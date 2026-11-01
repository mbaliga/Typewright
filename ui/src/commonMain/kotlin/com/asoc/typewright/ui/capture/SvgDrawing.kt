// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.enforceContourDirections
import com.asoc.typewright.core.geometry.polygonContour
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** What reading an SVG drawing made: [contours] in font units, y up, ready for Fit; or a [failure] saying in plain words why not. */
sealed interface SvgReading {
    class Drawing(
        val contours: List<Contour>,
        /** Things the person should know that did not stop the read: strokes that were left out, a size that did not match. */
        val notes: List<String>,
    ) : SvgReading

    data class Failure(
        val sentence: String,
    ) : SvgReading
}

/** The box a template SVG is drawn in: its `viewBox` origin and size, in the template's own units (font units, y down). */
data class SvgFrame(
    val minX: Double,
    val minY: Double,
    val width: Double,
    val height: Double,
)

/**
 * Reads the filled shapes of an SVG drawing made on one of the template SVGs, as the contours a glyph is.
 *
 * The template draws font units directly, with the baseline on the line `y = 0` and the vertical flipped (SVG's y runs down),
 * so a point `(x, y)` in the file is the font point `(x, -y)`. A vector tool that re-saves the file with its own origin moves
 * the picture; when the file's `viewBox` has the template's size ([template]) but a different corner, the difference is taken
 * out, so the baseline lands where it was. A file whose size differs is read as it stands and says so.
 *
 * Read: `path` (every command, including arcs), `rect`, `circle`, `ellipse`, `polygon`, with `transform` on any element or
 * group. Left out, because they are not part of a glyph: the `guides` and `reference_delete_me` groups, text, `defs`, and a
 * shape with no fill (a stroke, which a font cannot hold; the note says to run Outline Stroke).
 */
object SvgDrawingReader {
    /** An arc is cut into cubic pieces of at most a quarter turn, where a cubic follows a circle to about one part in ten thousand. */
    private const val ARC_SEGMENT_QUARTER = PI / 2.0

    fun read(
        text: String,
        template: SvgFrame? = null,
    ): SvgReading {
        val tokens =
            try {
                XmlScan.tokens(text)
            } catch (e: IllegalArgumentException) {
                return SvgReading.Failure("This is not a readable SVG file: ${e.message}.")
            }
        val svg =
            tokens.firstOrNull { it is XmlToken.Open && it.name == "svg" } as? XmlToken.Open
                ?: return SvgReading.Failure("This file has no <svg> element, so it is not an SVG drawing.")
        val notes = mutableListOf<String>()
        val viewBox = svg.attributes["viewBox"]?.let { numbers(it) }?.takeIf { it.size == 4 }
        var shiftX = 0.0
        var shiftY = 0.0
        if (viewBox != null && template != null) {
            val sameSize = abs(viewBox[2] - template.width) < 1.0 && abs(viewBox[3] - template.height) < 1.0
            if (sameSize) {
                shiftX = template.minX - viewBox[0]
                shiftY = template.minY - viewBox[1]
            } else {
                notes += "The file is ${fmt(viewBox[2])} by ${fmt(viewBox[3])} and the template is ${fmt(template.width)} by " +
                    "${fmt(template.height)}, so it was read as it stands, with the baseline on y = 0."
            }
        }

        val contours = mutableListOf<List<Seg>>()
        var strokeOnly = 0

        // One frame per open element: the matrix that applies inside it and whether it, or an element above it, is left out.
        class Frame(
            val name: String,
            val matrix: Matrix,
            val skipped: Boolean,
        )
        val frames = ArrayDeque<Frame>()
        for (token in tokens) {
            when (token) {
                is XmlToken.Open -> {
                    val parent = frames.lastOrNull()
                    val matrix =
                        (parent?.matrix ?: Matrix.IDENTITY) * (token.attributes["transform"]?.let { parseTransform(it) } ?: Matrix.IDENTITY)
                    val skipped = (parent?.skipped ?: false) || token.name in SKIPPED || token.attributes["id"] in SKIPPED_IDS
                    if (!token.selfClosing) frames.addLast(Frame(token.name, matrix, skipped))
                    if (skipped) continue
                    val shape = shapeOf(token, matrix) ?: continue
                    if (token.attributes["fill"]?.trim()?.lowercase() == "none") {
                        strokeOnly++
                    } else {
                        contours += shape
                    }
                }

                is XmlToken.Close -> {
                    if (frames.isNotEmpty() && frames.last().name == token.name) frames.removeLast()
                }

                is XmlToken.Text -> {}
            }
        }
        if (strokeOnly > 0) {
            notes += "${plural(strokeOnly, "shape")} had no fill and was left out. A font holds filled shapes only: run Outline Stroke " +
                "in your vector tool before exporting."
        }
        if (contours.isEmpty()) {
            return SvgReading.Failure(
                "There are no filled shapes in this drawing. Draw filled shapes inside the guides, delete the reference, and export again.",
            )
        }
        val built = contours.mapNotNull { toContour(it, shiftX, shiftY) }
        if (built.isEmpty()) return SvgReading.Failure("The shapes in this drawing have no area, so there is nothing to make a glyph of.")
        return SvgReading.Drawing(enforceContourDirections(built), notes)
    }

    // ---- shapes -----------------------------------------------------------------------------------

    private val SKIPPED = setOf("defs", "text", "title", "desc", "style", "metadata", "clipPath", "mask", "pattern", "symbol", "marker")
    private val SKIPPED_IDS = setOf("guides", "reference_delete_me")

    /** A shape element as subpaths in the file's coordinates after [matrix], each a list of segments; null for an element that is no shape. */
    private fun shapeOf(
        element: XmlToken.Open,
        matrix: Matrix,
    ): List<List<Seg>>? {
        fun number(name: String): Double = element.attributes[name]?.let { parseNumber(it) } ?: 0.0
        val subpaths: List<List<Seg>> =
            when (element.name) {
                "path" -> {
                    PathData.parse(element.attributes["d"] ?: return null)
                }

                "rect" -> {
                    val x = number("x")
                    val y = number("y")
                    val w = number("width")
                    val h = number("height")
                    if (w <= 0.0 || h <= 0.0) return null
                    listOf(polygon(listOf(P(x, y), P(x + w, y), P(x + w, y + h), P(x, y + h))))
                }

                "circle" -> {
                    val r = number("r")
                    if (r <= 0.0) return null
                    listOf(ellipse(number("cx"), number("cy"), r, r))
                }

                "ellipse" -> {
                    val rx = number("rx")
                    val ry = number("ry")
                    if (rx <= 0.0 || ry <= 0.0) return null
                    listOf(ellipse(number("cx"), number("cy"), rx, ry))
                }

                "polygon" -> {
                    val n = numbers(element.attributes["points"] ?: return null)
                    if (n.size < 6) return null
                    listOf(polygon(List(n.size / 2) { P(n[2 * it], n[2 * it + 1]) }))
                }

                else -> {
                    return null
                }
            }
        return subpaths.map { path -> path.map { it.transformed(matrix) } }
    }

    private fun polygon(points: List<P>): List<Seg> = points.indices.map { Seg.Line(points[it], points[(it + 1) % points.size]) }

    private fun ellipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
    ): List<Seg> {
        val k = 0.5522847498307936
        val right = P(cx + rx, cy)
        val bottom = P(cx, cy + ry)
        val left = P(cx - rx, cy)
        val top = P(cx, cy - ry)
        return listOf(
            Seg.Cubic(right, P(cx + rx, cy + k * ry), P(cx + k * rx, cy + ry), bottom),
            Seg.Cubic(bottom, P(cx - k * rx, cy + ry), P(cx - rx, cy + k * ry), left),
            Seg.Cubic(left, P(cx - rx, cy - k * ry), P(cx - k * rx, cy - ry), top),
            Seg.Cubic(top, P(cx + k * rx, cy - ry), P(cx + rx, cy - k * ry), right),
        )
    }

    // ---- to contours ------------------------------------------------------------------------------

    /** One subpath as a cubic contour in font units, or null when it has fewer than three corners' worth of segments or no area. */
    private fun toContour(
        segments: List<Seg>,
        shiftX: Double,
        shiftY: Double,
    ): Contour? {
        if (segments.isEmpty()) return null

        fun font(p: P) = Point((p.x + shiftX).roundToInt(), (-(p.y + shiftY)).roundToInt())
        val points = ArrayList<ContourPoint>()
        for (seg in segments) {
            when (seg) {
                is Seg.Line -> {
                    val a = font(seg.from)
                    val b = font(seg.to)
                    points += ContourPoint(a, true)
                    points += ContourPoint(a, false)
                    points += ContourPoint(b, false)
                }

                is Seg.Cubic -> {
                    points += ContourPoint(font(seg.from), true)
                    points += ContourPoint(font(seg.c1), false)
                    points += ContourPoint(font(seg.c2), false)
                }
            }
        }
        // A path that does not come back to where it began is closed by a straight side, as a fill closes it.
        val last = segments.last()
        val first = segments.first()
        if (font(last.end) != font(first.start)) {
            val a = font(last.end)
            val b = font(first.start)
            points += ContourPoint(a, true)
            points += ContourPoint(a, false)
            points += ContourPoint(b, false)
        }
        val contour = Contour(dropDuplicateLines(points), CurveFormat.CUBIC)
        return if (area(contour) == 0.0) null else contour
    }

    /** Removes a zero-length straight side (two on-curve points at one place with its handles also there), which a rounded coordinate can make. */
    private fun dropDuplicateLines(points: List<ContourPoint>): List<ContourPoint> {
        val out = ArrayList<ContourPoint>()
        val n = points.size / 3
        for (i in 0 until n) {
            val start = points[3 * i]
            val next = points[(3 * i + 3) % points.size]
            val c1 = points[3 * i + 1]
            val c2 = points[3 * i + 2]
            if (start.point == next.point && c1.point == start.point && c2.point == start.point && n > 3) continue
            out += listOf(start, c1, c2)
        }
        return if (out.size >= 9) out else points
    }

    private fun area(contour: Contour): Double {
        val ring = polygonOf(contour)
        var sum = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            sum += a.first * b.second - b.first * a.second
        }
        return abs(sum / 2.0)
    }

    private fun polygonOf(contour: Contour): List<Pair<Double, Double>> {
        val n = contour.points.size / 3
        val out = ArrayList<Pair<Double, Double>>()
        for (i in 0 until n) {
            val p0 = contour.points[3 * i].point
            val p1 = contour.points[3 * i + 1].point
            val p2 = contour.points[3 * i + 2].point
            val p3 = contour.points[(3 * i + 3) % contour.points.size].point
            for (step in 0 until 8) {
                val t = step / 8.0
                val u = 1 - t
                out += (u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x) to
                    (u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y)
            }
        }
        return out
    }

    // ---- geometry ---------------------------------------------------------------------------------

    internal data class P(
        val x: Double,
        val y: Double,
    )

    internal sealed interface Seg {
        val start: P
        val end: P

        fun transformed(m: Matrix): Seg

        data class Line(
            val from: P,
            val to: P,
        ) : Seg {
            override val start: P get() = from
            override val end: P get() = to

            override fun transformed(m: Matrix): Seg = Line(m.apply(from), m.apply(to))
        }

        data class Cubic(
            val from: P,
            val c1: P,
            val c2: P,
            val to: P,
        ) : Seg {
            override val start: P get() = from
            override val end: P get() = to

            override fun transformed(m: Matrix): Seg = Cubic(m.apply(from), m.apply(c1), m.apply(c2), m.apply(to))
        }
    }

    /** An affine map: `x' = a x + c y + e`, `y' = b x + d y + f`. */
    internal class Matrix(
        val a: Double,
        val b: Double,
        val c: Double,
        val d: Double,
        val e: Double,
        val f: Double,
    ) {
        operator fun times(o: Matrix) =
            Matrix(
                a * o.a + c * o.b,
                b * o.a + d * o.b,
                a * o.c + c * o.d,
                b * o.c + d * o.d,
                a * o.e + c * o.f + e,
                b * o.e + d * o.f + f,
            )

        fun apply(p: P) = P(a * p.x + c * p.y + e, b * p.x + d * p.y + f)

        companion object {
            val IDENTITY = Matrix(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        }
    }

    internal fun parseTransform(text: String): Matrix {
        var result = Matrix.IDENTITY
        for (m in TRANSFORM.findAll(text)) {
            val args = numbers(m.groupValues[2])
            val step =
                when (m.groupValues[1]) {
                    "matrix" -> if (args.size == 6) Matrix(args[0], args[1], args[2], args[3], args[4], args[5]) else null
                    "translate" -> if (args.isNotEmpty()) Matrix(1.0, 0.0, 0.0, 1.0, args[0], args.getOrElse(1) { 0.0 }) else null
                    "scale" -> if (args.isNotEmpty()) Matrix(args[0], 0.0, 0.0, args.getOrElse(1) { args[0] }, 0.0, 0.0) else null
                    "rotate" -> if (args.isNotEmpty()) rotation(args[0], args.getOrElse(1) { 0.0 }, args.getOrElse(2) { 0.0 }) else null
                    "skewX" -> if (args.isNotEmpty()) Matrix(1.0, 0.0, tan(args[0] * PI / 180.0), 1.0, 0.0, 0.0) else null
                    "skewY" -> if (args.isNotEmpty()) Matrix(1.0, tan(args[0] * PI / 180.0), 0.0, 1.0, 0.0, 0.0) else null
                    else -> null
                }
            if (step != null) result *= step
        }
        return result
    }

    private fun rotation(
        degrees: Double,
        cx: Double,
        cy: Double,
    ): Matrix {
        val r = degrees * PI / 180.0
        val about = Matrix(cos(r), sin(r), -sin(r), cos(r), 0.0, 0.0)
        return Matrix(1.0, 0.0, 0.0, 1.0, cx, cy) * about * Matrix(1.0, 0.0, 0.0, 1.0, -cx, -cy)
    }

    private val TRANSFORM = Regex("([a-zA-Z]+)\\s*\\(([^)]*)\\)")

    // ---- path data --------------------------------------------------------------------------------

    internal object PathData {
        /** The subpaths of [d]: each a list of segments, in the path's own units, absolute. */
        fun parse(d: String): List<List<Seg>> {
            val tokens = tokenize(d)
            val result = mutableListOf<List<Seg>>()
            var current = mutableListOf<Seg>()
            var at = P(0.0, 0.0)
            var start = at
            var lastControl: P? = null
            var lastQuadControl: P? = null
            var i = 0
            var command = ' '

            fun flush() {
                if (current.isNotEmpty()) result += current
                current = mutableListOf()
            }

            fun hasNumbers(count: Int) = i + count <= tokens.size && (0 until count).all { tokens[i + it] is PathToken.Num }

            fun num() = (tokens[i++] as PathToken.Num).value
            while (i < tokens.size) {
                val token = tokens[i]
                if (token is PathToken.Cmd) {
                    command = token.letter
                    i++
                    if (command == 'Z' || command == 'z') {
                        if (at != start) current += Seg.Line(at, start)
                        at = start
                        flush()
                        lastControl = null
                        lastQuadControl = null
                        continue
                    }
                } else if (command == ' ') {
                    break
                }
                val relative = command.isLowerCase()
                val ox = if (relative) at.x else 0.0
                val oy = if (relative) at.y else 0.0
                when (command.uppercaseChar()) {
                    'M' -> {
                        if (!hasNumbers(2)) break
                        flush()
                        at = P(ox + num(), oy + num())
                        start = at
                        // Further pairs after a moveto are lineto.
                        command = if (relative) 'l' else 'L'
                        lastControl = null
                        lastQuadControl = null
                    }

                    'L' -> {
                        if (!hasNumbers(2)) break
                        val to = P(ox + num(), oy + num())
                        current += Seg.Line(at, to)
                        at = to
                        lastControl = null
                        lastQuadControl = null
                    }

                    'H' -> {
                        if (!hasNumbers(1)) break
                        val to = P(ox + num(), at.y)
                        current += Seg.Line(at, to)
                        at = to
                        lastControl = null
                        lastQuadControl = null
                    }

                    'V' -> {
                        if (!hasNumbers(1)) break
                        val to = P(at.x, oy + num())
                        current += Seg.Line(at, to)
                        at = to
                        lastControl = null
                        lastQuadControl = null
                    }

                    'C' -> {
                        if (!hasNumbers(6)) break
                        val c1 = P(ox + num(), oy + num())
                        val c2 = P(ox + num(), oy + num())
                        val to = P(ox + num(), oy + num())
                        current += Seg.Cubic(at, c1, c2, to)
                        at = to
                        lastControl = c2
                        lastQuadControl = null
                    }

                    'S' -> {
                        if (!hasNumbers(4)) break
                        val c1 = lastControl?.let { P(2 * at.x - it.x, 2 * at.y - it.y) } ?: at
                        val c2 = P(ox + num(), oy + num())
                        val to = P(ox + num(), oy + num())
                        current += Seg.Cubic(at, c1, c2, to)
                        at = to
                        lastControl = c2
                        lastQuadControl = null
                    }

                    'Q' -> {
                        if (!hasNumbers(4)) break
                        val q = P(ox + num(), oy + num())
                        val to = P(ox + num(), oy + num())
                        current += quadratic(at, q, to)
                        at = to
                        lastQuadControl = q
                        lastControl = null
                    }

                    'T' -> {
                        if (!hasNumbers(2)) break
                        val q = lastQuadControl?.let { P(2 * at.x - it.x, 2 * at.y - it.y) } ?: at
                        val to = P(ox + num(), oy + num())
                        current += quadratic(at, q, to)
                        at = to
                        lastQuadControl = q
                        lastControl = null
                    }

                    'A' -> {
                        if (!hasNumbers(7)) break
                        val rx = num()
                        val ry = num()
                        val rotation = num()
                        val large = num() != 0.0
                        val sweep = num() != 0.0
                        val to = P(ox + num(), oy + num())
                        current += arc(at, rx, ry, rotation, large, sweep, to)
                        at = to
                        lastControl = null
                        lastQuadControl = null
                    }

                    else -> {
                        break
                    }
                }
            }
            flush()
            return result
        }

        private fun quadratic(
            a: P,
            q: P,
            b: P,
        ): Seg =
            Seg.Cubic(
                a,
                P(a.x + 2.0 / 3.0 * (q.x - a.x), a.y + 2.0 / 3.0 * (q.y - a.y)),
                P(b.x + 2.0 / 3.0 * (q.x - b.x), b.y + 2.0 / 3.0 * (q.y - b.y)),
                b,
            )

        /** The SVG arc from [from] to [to] as cubic pieces of at most a quarter turn (SVG 1.1 appendix F.6). */
        private fun arc(
            from: P,
            rxIn: Double,
            ryIn: Double,
            rotationDegrees: Double,
            large: Boolean,
            sweep: Boolean,
            to: P,
        ): List<Seg> {
            if (from == to) return emptyList()
            var rx = abs(rxIn)
            var ry = abs(ryIn)
            if (rx == 0.0 || ry == 0.0) return listOf(Seg.Line(from, to))
            val phi = rotationDegrees * PI / 180.0
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            val dx = (from.x - to.x) / 2.0
            val dy = (from.y - to.y) / 2.0
            val x1 = cosPhi * dx + sinPhi * dy
            val y1 = -sinPhi * dx + cosPhi * dy
            val lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
            if (lambda > 1.0) {
                rx *= sqrt(lambda)
                ry *= sqrt(lambda)
            }
            val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            val denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
            val factor = (if (large == sweep) -1.0 else 1.0) * sqrt(max(0.0, numerator / denominator))
            val cxp = factor * rx * y1 / ry
            val cyp = -factor * ry * x1 / rx
            val cx = cosPhi * cxp - sinPhi * cyp + (from.x + to.x) / 2.0
            val cy = sinPhi * cxp + cosPhi * cyp + (from.y + to.y) / 2.0
            val theta1 = atan2((y1 - cyp) / ry, (x1 - cxp) / rx)
            var delta = atan2((-y1 - cyp) / ry, (-x1 - cxp) / rx) - theta1
            if (!sweep && delta > 0) delta -= 2 * PI
            if (sweep && delta < 0) delta += 2 * PI
            val pieces = max(1, ceil(abs(delta) / ARC_SEGMENT_QUARTER - 1e-9).toInt())
            val step = delta / pieces
            val k = 4.0 / 3.0 * tan(step / 4.0)

            fun pointAt(theta: Double) =
                P(
                    cx + rx * cos(theta) * cosPhi - ry * sin(theta) * sinPhi,
                    cy + rx * cos(theta) * sinPhi + ry * sin(theta) * cosPhi,
                )

            fun derivative(theta: Double) =
                P(
                    -rx * sin(theta) * cosPhi - ry * cos(theta) * sinPhi,
                    -rx * sin(theta) * sinPhi + ry * cos(theta) * cosPhi,
                )
            val out = ArrayList<Seg>()
            var a = from
            for (n in 0 until pieces) {
                val t0 = theta1 + n * step
                val t1 = t0 + step
                val end = if (n == pieces - 1) to else pointAt(t1)
                val d0 = derivative(t0)
                val d1 = derivative(t1)
                out += Seg.Cubic(a, P(a.x + k * d0.x, a.y + k * d0.y), P(end.x - k * d1.x, end.y - k * d1.y), end)
                a = end
            }
            return out
        }

        private sealed interface PathToken {
            class Cmd(
                val letter: Char,
            ) : PathToken

            class Num(
                val value: Double,
            ) : PathToken
        }

        private fun tokenize(d: String): List<PathToken> {
            val out = ArrayList<PathToken>()
            var i = 0
            while (i < d.length) {
                val c = d[i]
                when {
                    c.isLetter() && c != 'e' && c != 'E' -> {
                        out += PathToken.Cmd(c)
                        i++
                    }

                    c == '-' || c == '+' || c == '.' || c.isDigit() -> {
                        val begin = i
                        i++
                        var seenDot = c == '.'
                        while (i < d.length) {
                            val ch = d[i]
                            if (ch.isDigit()) {
                                i++
                            } else if (ch == '.' && !seenDot) {
                                seenDot = true
                                i++
                            } else if ((ch == 'e' || ch == 'E') && i + 1 < d.length &&
                                (d[i + 1].isDigit() || d[i + 1] == '-' || d[i + 1] == '+')
                            ) {
                                i += 2
                            } else {
                                break
                            }
                        }
                        d.substring(begin, i).toDoubleOrNull()?.let { out += PathToken.Num(it) }
                    }

                    else -> {
                        i++
                    }
                }
            }
            return out
        }
    }

    // ---- small helpers ----------------------------------------------------------------------------

    private val NUMBER = Regex("[-+]?(?:[0-9]*\\.[0-9]+|[0-9]+\\.?)(?:[eE][-+]?[0-9]+)?")

    internal fun numbers(text: String): List<Double> = NUMBER.findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()

    private fun parseNumber(text: String): Double? = NUMBER.find(text)?.value?.toDoubleOrNull()

    private fun fmt(x: Double): String = x.roundToInt().toString()

    private fun plural(
        n: Int,
        word: String,
    ): String = "$n $word" + if (n == 1) "" else "s"
}

/** A piece of an XML document, as much of one as an SVG drawing needs. */
internal sealed interface XmlToken {
    class Open(
        val name: String,
        val attributes: Map<String, String>,
        val selfClosing: Boolean,
    ) : XmlToken

    class Close(
        val name: String,
    ) : XmlToken

    class Text(
        val text: String,
    ) : XmlToken
}

internal object XmlScan {
    private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

    /** The tags of [text] in order. Comments, the XML declaration, a doctype and CDATA are skipped; an unterminated tag is an error. */
    fun tokens(text: String): List<XmlToken> {
        val out = ArrayList<XmlToken>()
        var i = 0
        while (i < text.length) {
            val lt = text.indexOf('<', i)
            if (lt < 0) break
            when {
                text.startsWith("<!--", lt) -> {
                    val end = text.indexOf("-->", lt + 4)
                    require(end >= 0) { "a comment is never closed" }
                    i = end + 3
                }

                text.startsWith("<![CDATA[", lt) -> {
                    val end = text.indexOf("]]>", lt + 9)
                    require(end >= 0) { "a CDATA section is never closed" }
                    i = end + 3
                }

                text.startsWith("<?", lt) || text.startsWith("<!", lt) -> {
                    val end = text.indexOf('>', lt + 2)
                    require(end >= 0) { "a declaration is never closed" }
                    i = end + 1
                }

                text.startsWith("</", lt) -> {
                    val end = text.indexOf('>', lt + 2)
                    require(end >= 0) { "a closing tag is never closed" }
                    out += XmlToken.Close(text.substring(lt + 2, end).trim().substringAfter(':'))
                    i = end + 1
                }

                else -> {
                    val end = tagEnd(text, lt + 1)
                    require(end >= 0) { "a tag is never closed" }
                    val body = text.substring(lt + 1, end)
                    val selfClosing = body.endsWith("/")
                    val inner = if (selfClosing) body.dropLast(1) else body
                    val name = inner.takeWhile { !it.isWhitespace() }
                    val attributes = LinkedHashMap<String, String>()
                    for (m in ATTRIBUTE.findAll(inner.substring(name.length))) {
                        attributes[m.groupValues[1]] = m.groupValues[2].ifEmpty { m.groupValues[3] }
                    }
                    out += XmlToken.Open(name.substringAfter(':'), attributes, selfClosing)
                    i = end + 1
                }
            }
        }
        return out
    }

    /** The index of the `>` that ends the tag whose text begins at [from], not counting a `>` inside a quoted value. */
    private fun tagEnd(
        text: String,
        from: Int,
    ): Int {
        var quote = ' '
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (quote != ' ') {
                if (c == quote) quote = ' '
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        return -1
    }
}
