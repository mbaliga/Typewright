// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A decimal number as SVG writes it: optional sign, digits with an optional point, optional exponent. */
internal val SVG_NUMBER = Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

/** The control-point distance, as a share of the radius, at which a cubic best follows a quarter circle. */
private const val KAPPA = 0.5522847498307936

/** A 2D affine transform: `x' = a x + c y + e`, `y' = b x + d y + f`. */
internal class Affine(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val e: Double = 0.0,
    val f: Double = 0.0,
) {
    /** This applied after [inner]: points go through [inner] first. */
    fun after(inner: Affine): Affine =
        Affine(
            a * inner.a + c * inner.b,
            b * inner.a + d * inner.b,
            a * inner.c + c * inner.d,
            b * inner.c + d * inner.d,
            a * inner.e + c * inner.f + e,
            b * inner.e + d * inner.f + f,
        )

    fun apply(p: Vec2): Vec2 = Vec2(a * p.x + c * p.y + e, b * p.x + d * p.y + f)
}

internal fun transformSegment(
    segment: CurveSegment,
    t: Affine,
): CurveSegment =
    when (segment) {
        is CurveSegment.Line -> {
            CurveSegment.Line(t.apply(segment.start), t.apply(segment.end))
        }

        is CurveSegment.Quadratic -> {
            CurveSegment.Quadratic(t.apply(segment.start), t.apply(segment.control), t.apply(segment.end))
        }

        is CurveSegment.Cubic -> {
            CurveSegment.Cubic(t.apply(segment.start), t.apply(segment.control1), t.apply(segment.control2), t.apply(segment.end))
        }
    }

internal fun polygonLoop(points: String): List<List<CurveSegment>> {
    val numbers = SVG_NUMBER.findAll(points).map { it.value.toDouble() }.toList()
    val vertices = List(numbers.size / 2) { Vec2(numbers[2 * it], numbers[2 * it + 1]) }
    if (vertices.size < 3) return emptyList()
    return listOf(vertices.indices.map { CurveSegment.Line(vertices[it], vertices[(it + 1) % vertices.size]) })
}

internal fun rectLoops(
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    rxIn: Double,
    ryIn: Double,
): List<List<CurveSegment>> {
    if (w <= 0.0 || h <= 0.0) return emptyList()
    val rx = (if (rxIn > 0.0) rxIn else ryIn).coerceAtMost(w / 2.0)
    val ry = (if (ryIn > 0.0) ryIn else rxIn).coerceAtMost(h / 2.0)
    if (rx <= 0.0 || ry <= 0.0) {
        val corners = listOf(Vec2(x, y), Vec2(x + w, y), Vec2(x + w, y + h), Vec2(x, y + h))
        return listOf(corners.indices.map { CurveSegment.Line(corners[it], corners[(it + 1) % 4]) })
    }
    val k = KAPPA
    val loop =
        listOf(
            CurveSegment.Line(Vec2(x + rx, y), Vec2(x + w - rx, y)),
            CurveSegment.Cubic(Vec2(x + w - rx, y), Vec2(x + w - rx + k * rx, y), Vec2(x + w, y + ry - k * ry), Vec2(x + w, y + ry)),
            CurveSegment.Line(Vec2(x + w, y + ry), Vec2(x + w, y + h - ry)),
            CurveSegment.Cubic(
                Vec2(x + w, y + h - ry),
                Vec2(x + w, y + h - ry + k * ry),
                Vec2(x + w - rx + k * rx, y + h),
                Vec2(x + w - rx, y + h),
            ),
            CurveSegment.Line(Vec2(x + w - rx, y + h), Vec2(x + rx, y + h)),
            CurveSegment.Cubic(Vec2(x + rx, y + h), Vec2(x + rx - k * rx, y + h), Vec2(x, y + h - ry + k * ry), Vec2(x, y + h - ry)),
            CurveSegment.Line(Vec2(x, y + h - ry), Vec2(x, y + ry)),
            CurveSegment.Cubic(Vec2(x, y + ry), Vec2(x, y + ry - k * ry), Vec2(x + rx - k * rx, y), Vec2(x + rx, y)),
        )
    return listOf(loop)
}

internal fun ellipseLoop(
    cx: Double,
    cy: Double,
    rx: Double,
    ry: Double,
): List<List<CurveSegment>> {
    if (rx <= 0.0 || ry <= 0.0) return emptyList()
    val k = KAPPA
    return listOf(
        listOf(
            CurveSegment.Cubic(Vec2(cx + rx, cy), Vec2(cx + rx, cy + k * ry), Vec2(cx + k * rx, cy + ry), Vec2(cx, cy + ry)),
            CurveSegment.Cubic(Vec2(cx, cy + ry), Vec2(cx - k * rx, cy + ry), Vec2(cx - rx, cy + k * ry), Vec2(cx - rx, cy)),
            CurveSegment.Cubic(Vec2(cx - rx, cy), Vec2(cx - rx, cy - k * ry), Vec2(cx - k * rx, cy - ry), Vec2(cx, cy - ry)),
            CurveSegment.Cubic(Vec2(cx, cy - ry), Vec2(cx + k * rx, cy - ry), Vec2(cx + rx, cy - k * ry), Vec2(cx + rx, cy)),
        ),
    )
}

private val TRANSFORM_FUNCTION = Regex("([a-zA-Z]+)\\s*\\(([^)]*)\\)")

/** The `transform` attribute's list (`translate`, `scale`, `rotate`, `matrix`, `skewX`, `skewY`) as one transform. */
internal fun parseTransform(text: String?): Affine {
    if (text.isNullOrBlank()) return Affine()
    var result = Affine()
    for (match in TRANSFORM_FUNCTION.findAll(text)) {
        val args = SVG_NUMBER.findAll(match.groupValues[2]).map { it.value.toDouble() }.toList()
        val one =
            when (match.groupValues[1].lowercase()) {
                "translate" -> {
                    Affine(e = args.getOrElse(0) { 0.0 }, f = args.getOrElse(1) { 0.0 })
                }

                "scale" -> {
                    Affine(a = args.getOrElse(0) { 1.0 }, d = args.getOrElse(1) { args.getOrElse(0) { 1.0 } })
                }

                "matrix" -> {
                    if (args.size >= 6) Affine(args[0], args[1], args[2], args[3], args[4], args[5]) else Affine()
                }

                "rotate" -> {
                    val angle = args.getOrElse(0) { 0.0 } * PI / 180.0
                    val rotation = Affine(cos(angle), sin(angle), -sin(angle), cos(angle))
                    if (args.size >= 3) {
                        Affine(e = args[1], f = args[2]).after(rotation).after(Affine(e = -args[1], f = -args[2]))
                    } else {
                        rotation
                    }
                }

                "skewx" -> {
                    Affine(c = tan(args.getOrElse(0) { 0.0 } * PI / 180.0))
                }

                "skewy" -> {
                    Affine(b = tan(args.getOrElse(0) { 0.0 } * PI / 180.0))
                }

                else -> {
                    Affine()
                }
            }
        // A list is applied right to left: `translate(..) scale(..)` scales first.
        result = result.after(one)
    }
    return result
}

/**
 * The `d` attribute of a path as closed loops: M L H V C S Q T A Z and their relative forms. An open sub-path is closed with a
 * straight line, as a fill closes it. An arc becomes cubics (by the endpoint-to-centre conversion in the SVG specification's
 * implementation notes).
 */
internal class PathParser(
    private val d: String,
) {
    private var pos = 0
    private val loops = mutableListOf<List<CurveSegment>>()
    private var current = mutableListOf<CurveSegment>()
    private var at = Vec2(0.0, 0.0)
    private var start = Vec2(0.0, 0.0)
    private var lastCubicControl: Vec2? = null
    private var lastQuadControl: Vec2? = null

    fun loops(): List<List<CurveSegment>> {
        var command = ' '
        while (true) {
            skipSeparators()
            if (pos >= d.length) break
            val c = d[pos]
            if (c.isLetter()) {
                command = c
                pos++
                if (command == 'z' || command == 'Z') {
                    close()
                    continue
                }
            } else if (command == ' ') {
                throw IllegalArgumentException("path data does not start with a command")
            } else if (command == 'M') {
                command = 'L'
            } else if (command == 'm') {
                command = 'l'
            }
            run(command)
        }
        finish()
        return loops
    }

    private fun run(command: Char) {
        val relative = command.isLowerCase()
        val origin = if (relative) at else Vec2(0.0, 0.0)
        when (command.uppercaseChar()) {
            'M' -> {
                finish()
                at = origin + Vec2(number(), number())
                start = at
                lastCubicControl = null
                lastQuadControl = null
            }

            'L' -> {
                lineTo(origin + Vec2(number(), number()))
            }

            'H' -> {
                lineTo(Vec2((if (relative) at.x else 0.0) + number(), at.y))
            }

            'V' -> {
                lineTo(Vec2(at.x, (if (relative) at.y else 0.0) + number()))
            }

            'C' -> {
                val c1 = origin + Vec2(number(), number())
                val c2 = origin + Vec2(number(), number())
                cubicTo(c1, c2, origin + Vec2(number(), number()))
            }

            'S' -> {
                val reflected = lastCubicControl?.let { at * 2.0 - it } ?: at
                val c2 = origin + Vec2(number(), number())
                cubicTo(reflected, c2, origin + Vec2(number(), number()))
            }

            'Q' -> {
                val control = origin + Vec2(number(), number())
                quadTo(control, origin + Vec2(number(), number()))
            }

            'T' -> {
                val reflected = lastQuadControl?.let { at * 2.0 - it } ?: at
                quadTo(reflected, origin + Vec2(number(), number()))
            }

            'A' -> {
                val rx = number()
                val ry = number()
                val rotation = number()
                val large = flag()
                val sweep = flag()
                arcTo(rx, ry, rotation, large, sweep, origin + Vec2(number(), number()))
            }

            else -> {
                throw IllegalArgumentException("unknown path command '$command'")
            }
        }
    }

    private fun lineTo(to: Vec2) {
        current += CurveSegment.Line(at, to)
        at = to
        lastCubicControl = null
        lastQuadControl = null
    }

    private fun cubicTo(
        c1: Vec2,
        c2: Vec2,
        to: Vec2,
    ) {
        current += CurveSegment.Cubic(at, c1, c2, to)
        at = to
        lastCubicControl = c2
        lastQuadControl = null
    }

    private fun quadTo(
        control: Vec2,
        to: Vec2,
    ) {
        current += CurveSegment.Quadratic(at, control, to)
        at = to
        lastQuadControl = control
        lastCubicControl = null
    }

    private fun arcTo(
        rxIn: Double,
        ryIn: Double,
        rotationDegrees: Double,
        large: Boolean,
        sweep: Boolean,
        to: Vec2,
    ) {
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if (rx < 1e-9 || ry < 1e-9 || (to - at).length() < 1e-9) {
            if ((to - at).length() >= 1e-9) lineTo(to)
            return
        }
        val phi = rotationDegrees * PI / 180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (at.x - to.x) / 2.0
        val dy = (at.y - to.y) / 2.0
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
        val cx = cosPhi * cxp - sinPhi * cyp + (at.x + to.x) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (at.y + to.y) / 2.0
        val theta1 = atan2((y1 - cyp) / ry, (x1 - cxp) / rx)
        var delta = atan2((-y1 - cyp) / ry, (-x1 - cxp) / rx) - theta1
        if (!sweep && delta > 0.0) delta -= 2.0 * PI
        if (sweep && delta < 0.0) delta += 2.0 * PI
        val pieces = ceil(abs(delta) / (PI / 2.0) - 1e-9).toInt().coerceAtLeast(1)
        val step = delta / pieces
        val t = 4.0 / 3.0 * tan(step / 4.0)

        fun point(a: Double) = Vec2(cx + rx * cos(a) * cosPhi - ry * sin(a) * sinPhi, cy + rx * cos(a) * sinPhi + ry * sin(a) * cosPhi)

        fun slope(a: Double) = Vec2(-rx * sin(a) * cosPhi - ry * cos(a) * sinPhi, -rx * sin(a) * sinPhi + ry * cos(a) * cosPhi)
        var angle = theta1
        var from = at
        for (i in 0 until pieces) {
            val a2 = angle + step
            val end = if (i == pieces - 1) to else point(a2)
            current += CurveSegment.Cubic(from, from + slope(angle) * t, end - slope(a2) * t, end)
            from = end
            angle = a2
        }
        at = to
        lastCubicControl = null
        lastQuadControl = null
    }

    /** Z: close the sub-path with a straight line back to its start (if it is not already there) and begin the next at the start. */
    private fun close() {
        if ((at - start).length() > 1e-9) current += CurveSegment.Line(at, start)
        if (current.isNotEmpty()) loops += current
        current = mutableListOf()
        at = start
        lastCubicControl = null
        lastQuadControl = null
    }

    /** The end of the path or a new `M`: whatever is open is closed as a fill closes it. */
    private fun finish() {
        if (current.isNotEmpty()) {
            if ((at - start).length() > 1e-9) current += CurveSegment.Line(at, start)
            loops += current
        }
        current = mutableListOf()
    }

    private fun skipSeparators() {
        while (pos < d.length && (d[pos].isWhitespace() || d[pos] == ',')) pos++
    }

    private fun number(): Double {
        skipSeparators()
        val match = SVG_NUMBER.matchAt(d, pos) ?: throw IllegalArgumentException("a number was expected at character $pos")
        pos += match.value.length
        return match.value.toDouble()
    }

    /** An arc flag is one character, 0 or 1, and may be written without a separator after it. */
    private fun flag(): Boolean {
        skipSeparators()
        val c = d.getOrNull(pos)
        if (c != '0' && c != '1') throw IllegalArgumentException("an arc flag was expected at character $pos")
        pos++
        return c == '1'
    }
}
