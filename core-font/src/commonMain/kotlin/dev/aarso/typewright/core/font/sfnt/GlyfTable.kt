// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.sfnt

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Point
import kotlin.math.roundToInt

// Simple-glyph point flags (OpenType spec, "Glyf Table", "Simple Glyph Description").
private const val ON_CURVE_POINT = 0x01
private const val X_SHORT_VECTOR = 0x02
private const val Y_SHORT_VECTOR = 0x04
private const val REPEAT_FLAG = 0x08
private const val X_IS_SAME_OR_POSITIVE_X_SHORT_VECTOR = 0x10
private const val Y_IS_SAME_OR_POSITIVE_Y_SHORT_VECTOR = 0x20

// Composite-glyph component flags (OpenType spec, "Glyf Table", "Composite Glyph Description").
private const val ARG_1_AND_2_ARE_WORDS = 0x0001
private const val ARGS_ARE_XY_VALUES = 0x0002
private const val WE_HAVE_A_SCALE = 0x0008
private const val MORE_COMPONENTS = 0x0020
private const val WE_HAVE_AN_X_AND_Y_SCALE = 0x0040
private const val WE_HAVE_A_TWO_BY_TWO = 0x0080
private const val WE_HAVE_INSTRUCTIONS = 0x0100
private const val SCALED_COMPONENT_OFFSET = 0x0800

/**
 * One component of a composite glyph: which glyph it places ([glyphIndex]) and the affine
 * transform that places it, already resolved from whichever of the `glyf` spec's four transform
 * encodings (none, uniform scale, X/Y scale, full 2x2) and two offset encodings (words or bytes)
 * this component used. [glyf]'s own point-transform convention (OpenType spec, and matched
 * against `fontTools.misc.transform.Transform.transformPoint`, whose six-number tuple this mirrors
 * field-for-field) is `x' = xScale*x + scale10*y + dx`, `y' = scale01*x + yScale*y + dy`.
 *
 * Only [ARGS_ARE_XY_VALUES] components are supported: the alternative, point-matching two anchor
 * points between the component and the compound instead of giving an explicit offset, needs the
 * compound's own already-placed points to resolve and is rare in practice (unused anywhere in
 * `fonts/HyleDeco-Regular.ttf`'s 161 composites); [readGlyfRecord] throws a clear
 * [UnsupportedOperationException] if it is encountered rather than silently mis-placing a glyph.
 */
data class CompositeComponent(
    val glyphIndex: Int,
    val xScale: Double,
    val scale01: Double,
    val scale10: Double,
    val yScale: Double,
    val dx: Double,
    val dy: Double,
) {
    /** This component's transform applied to one [Point], rounded to the nearest font unit. */
    fun transform(p: Point): Point {
        val x = p.x.toDouble()
        val y = p.y.toDouble()
        val nx = xScale * x + scale10 * y + dx
        val ny = scale01 * x + yScale * y + dy
        return Point(nx.roundToInt(), ny.roundToInt())
    }
}

/** One glyph's raw, undecomposed `glyf`-table record. */
sealed class RawGlyfRecord {
    /** A glyph with no outline data at all (equal `loca` offsets): `.notdef` in some fonts, `space`, marks, ... */
    data object Empty : RawGlyfRecord()

    /** An ordinary outline, already decoded into [core-geometry][Contour]s (format [CurveFormat.QUADRATIC]). */
    data class Simple(
        val contours: List<Contour>,
    ) : RawGlyfRecord()

    /** A glyph built by placing other glyphs; [components] reference other glyph ids, resolved by [SfntFont]. */
    data class Composite(
        val components: List<CompositeComponent>,
    ) : RawGlyfRecord()
}

/**
 * Reads glyph [gid]'s raw `glyf`-table record: [RawGlyfRecord.Empty] if `loca`'s offsets for it
 * are equal, [RawGlyfRecord.Simple] with its contours already decoded if `numberOfContours >= 0`,
 * or [RawGlyfRecord.Composite] with its components (not yet resolved against other glyphs) if
 * `numberOfContours < 0` (the spec reserves exactly `-1` for "composite"; any other negative value
 * is treated the same way, since no other value is defined and real fonts do not emit one).
 */
fun readGlyfRecord(
    fontBytes: ByteArray,
    glyfTableOffset: Int,
    loca: LocaTable,
    gid: Int,
): RawGlyfRecord {
    val start = loca.offsets[gid]
    val end = loca.offsets[gid + 1]
    if (start == end) return RawGlyfRecord.Empty
    val cursor = ByteCursor(fontBytes, glyfTableOffset + start)
    val numberOfContours = cursor.i16()
    cursor.skip(2 * 4) // xMin, yMin, xMax, yMax (bounding box; recomputable from the points)
    return if (numberOfContours >= 0) {
        RawGlyfRecord.Simple(readSimpleGlyphContours(cursor, numberOfContours))
    } else {
        RawGlyfRecord.Composite(readCompositeComponents(cursor))
    }
}

private fun readSimpleGlyphContours(
    cursor: ByteCursor,
    numberOfContours: Int,
): List<Contour> {
    if (numberOfContours == 0) return emptyList()
    val endPtsOfContours = IntArray(numberOfContours) { cursor.u16() }
    val numPoints = endPtsOfContours.last() + 1
    val instructionLength = cursor.u16()
    cursor.skip(instructionLength)

    val flags = IntArray(numPoints)
    var i = 0
    while (i < numPoints) {
        val flag = cursor.u8()
        flags[i] = flag
        i += 1
        if (flag and REPEAT_FLAG != 0) {
            var repeatCount = cursor.u8()
            while (repeatCount > 0 && i < numPoints) {
                flags[i] = flag
                i += 1
                repeatCount -= 1
            }
        }
    }

    val xs = IntArray(numPoints)
    var x = 0
    for (p in 0 until numPoints) {
        val flag = flags[p]
        x +=
            when {
                flag and X_SHORT_VECTOR != 0 -> {
                    val delta = cursor.u8()
                    if (flag and X_IS_SAME_OR_POSITIVE_X_SHORT_VECTOR != 0) delta else -delta
                }

                flag and X_IS_SAME_OR_POSITIVE_X_SHORT_VECTOR != 0 -> {
                    0
                }

                else -> {
                    cursor.i16()
                }
            }
        xs[p] = x
    }

    val ys = IntArray(numPoints)
    var y = 0
    for (p in 0 until numPoints) {
        val flag = flags[p]
        y +=
            when {
                flag and Y_SHORT_VECTOR != 0 -> {
                    val delta = cursor.u8()
                    if (flag and Y_IS_SAME_OR_POSITIVE_Y_SHORT_VECTOR != 0) delta else -delta
                }

                flag and Y_IS_SAME_OR_POSITIVE_Y_SHORT_VECTOR != 0 -> {
                    0
                }

                else -> {
                    cursor.i16()
                }
            }
        ys[p] = y
    }

    val contours = ArrayList<Contour>(numberOfContours)
    var pointStart = 0
    for (c in 0 until numberOfContours) {
        val pointEnd = endPtsOfContours[c] // inclusive
        val points =
            (pointStart..pointEnd).map { p ->
                ContourPoint(Point(xs[p], ys[p]), onCurve = flags[p] and ON_CURVE_POINT != 0)
            }
        contours += Contour(points, CurveFormat.QUADRATIC)
        pointStart = pointEnd + 1
    }
    return contours
}

private fun readCompositeComponents(cursor: ByteCursor): List<CompositeComponent> {
    val components = mutableListOf<CompositeComponent>()
    var more = true
    var lastHadInstructions = false
    while (more) {
        val flags = cursor.u16()
        val glyphIndex = cursor.u16()

        val dxRaw: Int
        val dyRaw: Int
        if (flags and ARG_1_AND_2_ARE_WORDS != 0) {
            require(flags and ARGS_ARE_XY_VALUES != 0) {
                "composite glyph component references a point-matched anchor (ARGS_ARE_XY_VALUES unset); " +
                    "point-matched composites are not supported"
            }
            dxRaw = cursor.i16()
            dyRaw = cursor.i16()
        } else {
            require(flags and ARGS_ARE_XY_VALUES != 0) {
                "composite glyph component references a point-matched anchor (ARGS_ARE_XY_VALUES unset); " +
                    "point-matched composites are not supported"
            }
            dxRaw = cursor.i8()
            dyRaw = cursor.i8()
        }

        var xScale = 1.0
        var scale01 = 0.0
        var scale10 = 0.0
        var yScale = 1.0
        when {
            flags and WE_HAVE_A_SCALE != 0 -> {
                val s = cursor.f2Dot14()
                xScale = s
                yScale = s
            }

            flags and WE_HAVE_AN_X_AND_Y_SCALE != 0 -> {
                xScale = cursor.f2Dot14()
                yScale = cursor.f2Dot14()
            }

            flags and WE_HAVE_A_TWO_BY_TWO != 0 -> {
                xScale = cursor.f2Dot14()
                scale01 = cursor.f2Dot14()
                scale10 = cursor.f2Dot14()
                yScale = cursor.f2Dot14()
            }
        }

        // OpenType spec: component offsets are scaled by the component's own transform only when
        // SCALED_COMPONENT_OFFSET is set (Microsoft's default reading, which this follows); the
        // alternative (UNSCALED_COMPONENT_OFFSET, or neither flag set) leaves dx/dy untransformed.
        val dx: Double
        val dy: Double
        if (flags and SCALED_COMPONENT_OFFSET != 0) {
            dx = xScale * dxRaw + scale10 * dyRaw
            dy = scale01 * dxRaw + yScale * dyRaw
        } else {
            dx = dxRaw.toDouble()
            dy = dyRaw.toDouble()
        }

        components +=
            CompositeComponent(
                glyphIndex = glyphIndex,
                xScale = xScale,
                scale01 = scale01,
                scale10 = scale10,
                yScale = yScale,
                dx = dx,
                dy = dy,
            )

        more = flags and MORE_COMPONENTS != 0
        lastHadInstructions = flags and WE_HAVE_INSTRUCTIONS != 0
    }
    if (lastHadInstructions) {
        val numInstr = cursor.u16()
        cursor.skip(numInstr)
    }
    return components
}

/**
 * Recursively decomposes glyph [gid] into plain (non-composite) [Contour]s by looking up each
 * [RawGlyfRecord] in [records] (indexed by glyph id) and, for [RawGlyfRecord.Composite], applying
 * every component's [CompositeComponent.transform] to its base glyph's own decomposed contours
 * (which may themselves be composite, handled by recursing). [maxDepth] guards against a
 * malformed font whose components reference each other in a cycle; the OpenType spec does not
 * allow this, and no real font needs recursion anywhere near this deep.
 */
fun decomposeGlyf(
    records: List<RawGlyfRecord>,
    gid: Int,
    maxDepth: Int = 16,
): List<Contour> {
    require(maxDepth > 0) { "composite glyph nesting exceeded the recursion guard (a cyclic composite reference?)" }
    return when (val record = records[gid]) {
        is RawGlyfRecord.Empty -> {
            emptyList()
        }

        is RawGlyfRecord.Simple -> {
            record.contours
        }

        is RawGlyfRecord.Composite -> {
            record.components.flatMap { component ->
                val baseContours = decomposeGlyf(records, component.glyphIndex, maxDepth - 1)
                baseContours.map { contour ->
                    Contour(contour.points.map { ContourPoint(component.transform(it.point), it.onCurve) }, contour.format)
                }
            }
        }
    }
}
