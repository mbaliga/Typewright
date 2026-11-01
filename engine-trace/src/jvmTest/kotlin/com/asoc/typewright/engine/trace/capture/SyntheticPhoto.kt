// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.OutlineBounds
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.HyleDecoFixture
import com.asoc.typewright.engine.trace.RasterFrame
import com.asoc.typewright.engine.trace.coverageOf
import com.asoc.typewright.scripts.sheets.DrawOp
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PdfWriter
import com.asoc.typewright.scripts.sheets.PrintSheetSpec
import com.asoc.typewright.scripts.sheets.SheetFont
import com.asoc.typewright.scripts.sheets.SheetIdentity
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.scripts.sheets.SheetRenderer
import com.asoc.typewright.scripts.sheets.TextAlign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What a synthetic photograph of a printed sheet is made of. Every field is a knob of one thing a real photograph does: the
 * sheet is seen from a tilted, rolled camera ([tiltDegrees], [tiltAxisDegrees], [rollDegrees]), lit unevenly ([gradient],
 * [vignette]), a little out of focus ([blurSigmaPx]), noisy ([noiseSigma]), compressed ([jpegQuality]), and printed on tinted
 * paper ([tint]) with a pen of a given darkness ([inkGray]). Seeds make every photograph reproducible.
 *
 * THESE ARE SYNTHETIC. They are drawn from the repository's own Hyle Deco outlines by a simple renderer and distorted by a
 * pinhole camera model; they are not photographs of a printed page, and real ones (a printer's blacks and greys, paper
 * texture, lens distortion, a phone's own sharpening) are an owner-supplied fixture still owed.
 */
data class PhotoSpec(
    val seed: Int,
    val widthPx: Int = 1500,
    val heightPx: Int = 2000,
    val tiltDegrees: Double = 0.0,
    val tiltAxisDegrees: Double = 0.0,
    val rollDegrees: Double = 0.0,
    val fill: Double = 0.84,
    val shiftX: Double = 0.0,
    val shiftY: Double = 0.0,
    val gradient: Double = 0.0,
    val gradientAngleDegrees: Double = 30.0,
    val vignette: Double = 0.0,
    val blurSigmaPx: Double = 0.6,
    val noiseSigma: Double = 0.0,
    val jpegQuality: Int? = null,
    val tint: Triple<Int, Int, Int> = Triple(255, 255, 255),
    val inkGray: Int = 20,
    val deskGray: Int = 70,
    /** Which marks to hide, by corner index (0 top left, 1 top right, 2 bottom right, 3 bottom left): a thumb over a corner. */
    val coveredMarks: Set<Int> = emptySet(),
    val flatPxPerMm: Double = 8.0,
    /** What is wrong with the printed identity code, if anything. */
    val codeFault: CodeFault = CodeFault.NONE,
    /** A highlight of this strength (1 is enough to wash out the paper under it) centred on the sheet's middle: a lamp reflected in the page. */
    val glare: Double = 0.0,
    /** A soft-edged shadow of this depth (0 to 1, the share of the light it takes) across the left third of the picture. */
    val shadowDepth: Double = 0.0,
    /** A veil of stray light of this share (0 to 1) that lifts the blacks toward grey: a washed-out, low-contrast photograph. */
    val veil: Double = 0.0,
)

/** How a synthetic sheet's identity code is damaged. */
enum class CodeFault { NONE, HIDDEN, ONE_MODULE_FLIPPED }

/** A cell's ground truth: the glyph the template puts there and the box of the ink drawn in it, null when nothing was drawn. */
class TruthCell(
    val index: Int,
    val glyphName: String,
    val bounds: OutlineBounds?,
)

class SyntheticPhoto(
    val gray: GrayscaleRaster,
    val rgb: RgbRaster,
    /** Page millimetres to photograph pixels. */
    val pageToPhoto: Homography,
    val truth: List<TruthCell>,
    val spec: PhotoSpec,
    val layout: SheetLayout,
)

/** What to draw in a cell: a Hyle Deco glyph by code point, or a filled disc standing in for a glyph the font has none for. */
internal fun interface CellArt {
    fun contours(
        glyph: com.asoc.typewright.scripts.sheets.PrintGlyph,
        index: Int,
    ): List<Contour>
}

internal object HyleArt : CellArt {
    override fun contours(
        glyph: com.asoc.typewright.scripts.sheets.PrintGlyph,
        index: Int,
    ): List<Contour> = glyph.codePoint?.let { HyleDecoFixture.glyphForCodePoint(it)?.contours } ?: emptyList()
}

/** A ring of a size that depends on the cell, centred in the advance: stands in for glyphs of scripts Hyle Deco does not cover. */
internal object ProxyArt : CellArt {
    override fun contours(
        glyph: com.asoc.typewright.scripts.sheets.PrintGlyph,
        index: Int,
    ): List<Contour> {
        val radius = 150.0 + 12.0 * (index % 6)
        val centreX = 325.0
        val centreY = 250.0

        fun ring(
            r: Double,
            clockwise: Boolean,
        ): Contour {
            val n = 48
            val pts =
                (0 until n).flatMap { i ->
                    val a = 2.0 * PI * i / n * (if (clockwise) -1.0 else 1.0)
                    listOf(ContourPoint(Point((centreX + r * cos(a)).roundToInt(), (centreY + r * sin(a)).roundToInt()), true))
                }
            return Contour(pts, CurveFormat.QUADRATIC)
        }
        return listOf(ring(radius, clockwise = false), ring(radius - 50.0, clockwise = true))
    }
}

internal object SyntheticPhotos {
    /** The module flipped by [CodeFault.ONE_MODULE_FLIPPED]: one of the data bits, past the start pattern. */
    private const val FLIPPED_MODULE = 9

    /** The photograph [spec] describes of [sheet] on [paper], with [art] drawn in its cells. */
    fun make(
        sheet: PrintSheetSpec,
        paper: PaperSize,
        guides: GuideStyle,
        spec: PhotoSpec,
        art: CellArt = HyleArt,
    ): SyntheticPhoto {
        val layout = SheetLayout.of(paper, sheet.script)
        val s = spec.flatPxPerMm
        val fw = (layout.pageWidthMm * s).toInt()
        val fh = (layout.pageHeightMm * s).toInt()
        val flat = DoubleArray(fw * fh)
        val page = SheetRenderer.render(sheet, layout, guides)
        for ((i, op) in page.ops.withIndex()) {
            // The marks are the first four filled squares.
            if (op is DrawOp.Rect && op.fillGray == 0.0 && op.width == SheetLayout.MARK_SIDE_MM) {
                val cornerIndex =
                    layout.markCentres.indexOfFirst {
                        abs(it.x - (op.x + op.width / 2.0)) < 1e-6 &&
                            abs(it.y - (op.y + op.height / 2.0)) < 1e-6
                    }
                if (cornerIndex in spec.coveredMarks) continue
            }
            if (op is DrawOp.Rect && op.fillGray == 0.0 && op.width == SheetLayout.CODE_MODULE_MM &&
                spec.codeFault == CodeFault.HIDDEN
            ) {
                continue
            }
            drawOp(flat, fw, fh, s, op, i)
        }
        if (spec.codeFault == CodeFault.ONE_MODULE_FLIPPED) flipCodeModule(flat, fw, fh, s, layout, sheet, FLIPPED_MODULE)
        // The drawings.
        val ink = (255 - spec.inkGray) / 255.0
        val truth = mutableListOf<TruthCell>()
        val pageContours = mutableListOf<Contour>()
        for ((index, glyph) in sheet.glyphs.withIndex()) {
            val contours = art.contours(glyph, index)
            val box = layout.cellBoxes[index]
            val frame = layout.frame
            var bounds: OutlineBounds? = null
            if (contours.isNotEmpty()) {
                bounds = Glyph("g", 0, contours).outlineBounds()
                // Font units to page millimetres, y up (the rasteriser's convention): page y-up = pageHeight - page y-down.
                pageContours +=
                    contours.map { c ->
                        Contour(
                            c.points.map { p ->
                                val mm = frame.boxMm(p.point.x.toDouble(), p.point.y.toDouble())
                                ContourPoint(
                                    Point(
                                        ((box.left + mm.x) * 1000.0).roundToInt(),
                                        ((layout.pageHeightMm - (box.top + mm.y)) * 1000.0).roundToInt(),
                                    ),
                                    p.onCurve,
                                )
                            },
                            c.format,
                        )
                    }
            }
            truth += TruthCell(index, glyph.glyphName, bounds)
        }
        // Rasterise all drawings at once: contour coordinates are in thousandths of a millimetre, so one unit = 0.001 mm.
        val frame = RasterFrame(pxPerUnit = s / 1000.0, leftUnits = 0.0, topUnits = layout.pageHeightMm * 1000.0)
        val coverage = coverageOf(pageContours, frame, fw, fh)
        for (i in flat.indices) flat[i] = min(1.0, flat[i] + coverage[i] * ink - flat[i] * coverage[i] * ink)

        return photograph(flat, fw, fh, layout, spec, truth)
    }

    /** The module [module] of the code printed the other way: black where it was paper, paper where it was black. */
    private fun flipCodeModule(
        flat: DoubleArray,
        fw: Int,
        fh: Int,
        s: Double,
        layout: SheetLayout,
        sheet: PrintSheetSpec,
        module: Int,
    ) {
        val identity = SheetIdentity(sheet.script, sheet.index, sheet.count, layout.paper)
        val centre = layout.codeCentres[module]
        val side = SheetLayout.CODE_MODULE_MM
        if (identity.encode()[module]) {
            // Erase: paper over the module.
            val x0 = ((centre.x - side / 2.0) * s).roundToInt()
            val x1 = ((centre.x + side / 2.0) * s).roundToInt()
            val y0 = ((centre.y - side / 2.0) * s).roundToInt()
            val y1 = ((centre.y + side / 2.0) * s).roundToInt()
            for (y in y0 until y1) for (x in x0 until x1) flat[y * fw + x] = 0.0
        } else {
            fillRect(flat, fw, fh, s, centre.x - side / 2.0, centre.y - side / 2.0, side, side, 1.0)
        }
    }

    private fun drawOp(
        flat: DoubleArray,
        fw: Int,
        fh: Int,
        s: Double,
        op: DrawOp,
        seq: Int,
    ) {
        when (op) {
            is DrawOp.Rect -> {
                val fillGray = op.fillGray
                val strokeGray = op.strokeGray
                if (fillGray != null) fillRect(flat, fw, fh, s, op.x, op.y, op.width, op.height, 1.0 - fillGray)
                if (strokeGray != null) {
                    val t = max(op.strokeMm, 1.0 / s)
                    val d = 1.0 - strokeGray
                    fillRect(flat, fw, fh, s, op.x - t / 2, op.y - t / 2, op.width + t, t, d)
                    fillRect(flat, fw, fh, s, op.x - t / 2, op.y + op.height - t / 2, op.width + t, t, d)
                    fillRect(flat, fw, fh, s, op.x - t / 2, op.y - t / 2, t, op.height + t, d)
                    fillRect(flat, fw, fh, s, op.x + op.width - t / 2, op.y - t / 2, t, op.height + t, d)
                }
            }

            is DrawOp.Line -> {
                val t = max(op.widthMm, 1.0 / s)
                val d = 1.0 - op.gray
                val dash = op.dashMm
                if (op.y1 == op.y2) {
                    var x = min(op.x1, op.x2)
                    val end = max(op.x1, op.x2)
                    while (x < end) {
                        val len = if (dash == null) end - x else min(dash, end - x)
                        fillRect(flat, fw, fh, s, x, op.y1 - t / 2, len, t, d)
                        x += if (dash == null) len else 2 * dash
                    }
                } else {
                    var y = min(op.y1, op.y2)
                    val end = max(op.y1, op.y2)
                    while (y < end) {
                        val len = if (dash == null) end - y else min(dash, end - y)
                        fillRect(flat, fw, fh, s, op.x1 - t / 2, y, t, len, d)
                        y += if (dash == null) len else 2 * dash
                    }
                }
            }

            is DrawOp.Text -> {
                // Text is drawn as a block of the grey it would average to: the reader only needs to see that something is there.
                val width =
                    com.asoc.typewright.scripts.sheets.PdfWriter
                        .textWidthMm(op.text, op.font, op.sizePt)
                val left =
                    when (op.align) {
                        TextAlign.START -> op.x
                        TextAlign.MIDDLE -> op.x - width / 2.0
                        TextAlign.END -> op.x - width
                    }
                val height = op.sizePt * 25.4 / 72.0 * 0.55
                val strength = (1.0 - op.gray) * 0.6
                fillRect(flat, fw, fh, s, left, op.y - height, width, height, strength)
            }
        }
    }

    /** Adds [darkness] over the rectangle (millimetres), with exact fractional coverage at its edges; darkness composes as 1 - (1 - a)(1 - b). */
    private fun fillRect(
        flat: DoubleArray,
        fw: Int,
        fh: Int,
        s: Double,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        darkness: Double,
    ) {
        val x0 = x * s
        val x1 = (x + w) * s
        val y0 = y * s
        val y1 = (y + h) * s
        for (j in max(0, y0.toInt())..min(fh - 1, y1.toInt())) {
            val cy = min(j + 1.0, y1) - max(j.toDouble(), y0)
            if (cy <= 0) continue
            for (i in max(0, x0.toInt())..min(fw - 1, x1.toInt())) {
                val cx = min(i + 1.0, x1) - max(i.toDouble(), x0)
                if (cx <= 0) continue
                val d = darkness * cx * cy
                val k = j * fw + i
                flat[k] = 1.0 - (1.0 - flat[k]) * (1.0 - d)
            }
        }
    }

    private fun photograph(
        flat: DoubleArray,
        fw: Int,
        fh: Int,
        layout: SheetLayout,
        spec: PhotoSpec,
        truth: List<TruthCell>,
    ): SyntheticPhoto {
        val rnd = Random(spec.seed)
        val w = spec.widthPx
        val h = spec.heightPx
        val f = 1.1 * w
        val distance = f * layout.pageHeightMm / (spec.fill * h)
        val tilt = spec.tiltDegrees * PI / 180.0
        val axis = spec.tiltAxisDegrees * PI / 180.0
        val roll = spec.rollDegrees * PI / 180.0
        // Rotation = roll about z, then tilt about the in-plane axis at angle `axis`.
        val rz = matrix(cos(roll), -sin(roll), 0.0, sin(roll), cos(roll), 0.0, 0.0, 0.0, 1.0)
        val ax = cos(axis)
        val ay = sin(axis)
        val c = cos(tilt)
        val sn = sin(tilt)
        val rt =
            matrix(
                c + ax * ax * (1 - c),
                ax * ay * (1 - c),
                ay * sn,
                ax * ay * (1 - c),
                c + ay * ay * (1 - c),
                -ax * sn,
                -ay * sn,
                ax * sn,
                c,
            )
        val r = multiply(rt, rz)
        val cx = w / 2.0 + spec.shiftX * w
        val cy = h / 2.0 + spec.shiftY * h
        // Page point (x, y) in mm from the top-left -> centred -> rotated -> projected.
        val toPhoto =
            Homography(
                multiply(
                    matrix(f, 0.0, cx, 0.0, f, cy, 0.0, 0.0, 1.0),
                    multiply(
                        matrixCols(r, distance),
                        matrix(1.0, 0.0, -layout.pageWidthMm / 2.0, 0.0, 1.0, -layout.pageHeightMm / 2.0, 0.0, 0.0, 1.0),
                    ),
                ),
            )
        val back = toPhoto.inverse()!!
        val gradX = cos(spec.gradientAngleDegrees * PI / 180.0)
        val gradY = sin(spec.gradientAngleDegrees * PI / 180.0)
        val tint = spec.tint
        val rgb = IntArray(w * h)
        val gray = IntArray(w * h)
        val sat = summedAreaTable(flat, fw, fh)
        for (v in 0 until h) {
            for (u in 0 until w) {
                // A sensor pixel integrates the light that falls on its area, which is what keeps a slanted edge smooth: the
                // photograph is the mean of the sheet over the pixel's footprint, read from a summed-area table. The footprint is
                // the bounding box of the pixel's four corners mapped onto the sheet.
                var minX = Double.MAX_VALUE
                var maxX = -Double.MAX_VALUE
                var minY = Double.MAX_VALUE
                var maxY = -Double.MAX_VALUE
                for ((du, dv) in CORNERS) {
                    val page = back.apply(u + du, v + dv)
                    minX = min(minX, page.x * spec.flatPxPerMm)
                    maxX = max(maxX, page.x * spec.flatPxPerMm)
                    minY = min(minY, page.y * spec.flatPxPerMm)
                    maxY = max(maxY, page.y * spec.flatPxPerMm)
                }
                val boxArea = max(1e-9, (maxX - minX) * (maxY - minY))
                val cx0 = minX.coerceIn(0.0, fw.toDouble())
                val cx1 = maxX.coerceIn(0.0, fw.toDouble())
                val cy0 = minY.coerceIn(0.0, fh.toDouble())
                val cy1 = maxY.coerceIn(0.0, fh.toDouble())
                val clippedArea = max(0.0, cx1 - cx0) * max(0.0, cy1 - cy0)
                val onPage = clippedArea > 1e-9
                val d = if (onPage) satBox(sat, fw, cx0, cy0, cx1, cy1) / clippedArea else 0.0
                val edgeMix = (clippedArea / boxArea).coerceIn(0.0, 1.0)
                val nx = (u - w / 2.0) / (w / 2.0)
                val ny = (v - h / 2.0) / (h / 2.0)
                val shadow = ((-0.35 - nx) / 0.25).coerceIn(0.0, 1.0)
                val highlight = spec.glare * 1.6 * exp(-(nx * nx + ny * ny) / (2.0 * 0.2 * 0.2))
                val light =
                    (1.0 - spec.gradient * (0.5 + 0.5 * (nx * gradX + ny * gradY)) - spec.vignette * (nx * nx + ny * ny) / 2.0) *
                        (1.0 - spec.shadowDepth * shadow) + highlight
                val r8: Double
                val g8: Double
                val b8: Double
                if (onPage) {
                    // Where only part of the pixel is paper the rest is desk.
                    val desk = spec.deskGray * (1.0 - edgeMix)
                    r8 = tint.first * (1.0 - d) * edgeMix + desk
                    g8 = tint.second * (1.0 - d) * edgeMix + desk * 0.92
                    b8 = tint.third * (1.0 - d) * edgeMix + desk * 0.8
                } else {
                    val grain = spec.deskGray * (0.9 + 0.2 * ((u * 7 + v * 13) % 17) / 17.0)
                    r8 = grain
                    g8 = grain * 0.92
                    b8 = grain * 0.8
                }
                rgb[v * w + u] = doubleArrayOf(r8 * light, g8 * light, b8 * light).let { packed(it) }
                gray[v * w + u] = ((299.0 * r8 + 587.0 * g8 + 114.0 * b8) / 1000.0 * light).roundToInt().coerceIn(0, 255)
            }
        }
        var luma = gray
        if (spec.blurSigmaPx > 0.0) luma = blur(luma, w, h, spec.blurSigmaPx)
        if (spec.noiseSigma > 0.0) {
            luma = IntArray(luma.size) { (luma[it] + gaussian(rnd) * spec.noiseSigma).roundToInt().coerceIn(0, 255) }
        }
        spec.jpegQuality?.let { luma = jpegLike(luma, w, h, it) }
        if (spec.veil > 0.0) luma = IntArray(luma.size) { (255.0 * spec.veil + luma[it] * (1.0 - spec.veil)).roundToInt().coerceIn(0, 255) }
        // The RGB version carries the same blur/noise/compression as a gain on each channel relative to the luma it came from.
        val outRgb =
            IntArray(w * h) {
                val l0 = gray[it].coerceAtLeast(1)
                val ratio = luma[it].toDouble() / l0
                val p = rgb[it]
                val rr = (((p shr 16) and 0xFF) * ratio).roundToInt().coerceIn(0, 255)
                val gg = (((p shr 8) and 0xFF) * ratio).roundToInt().coerceIn(0, 255)
                val bb = ((p and 0xFF) * ratio).roundToInt().coerceIn(0, 255)
                (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        return SyntheticPhoto(GrayscaleRaster(w, h, luma), RgbRaster(w, h, outRgb), toPhoto, truth, spec, layout)
    }

    /** A pixel's four corners, as offsets from its centre. */
    private val CORNERS = listOf(-0.5 to -0.5, 0.5 to -0.5, 0.5 to 0.5, -0.5 to 0.5)

    /** `table[(y) * (w + 1) + x]` is the sum of `flat` over columns `0 until x` and rows `0 until y`. */
    private fun summedAreaTable(
        flat: DoubleArray,
        w: Int,
        h: Int,
    ): DoubleArray {
        val sat = DoubleArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0.0
            for (x in 0 until w) {
                row += flat[y * w + x]
                sat[(y + 1) * (w + 1) + x + 1] = sat[y * (w + 1) + x + 1] + row
            }
        }
        return sat
    }

    /** The integral of the flat image over the box [x0, x1] x [y0, y1] (flat pixels, fractional), the table read with bilinear interpolation. */
    private fun satBox(
        sat: DoubleArray,
        w: Int,
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
    ): Double = satAt(sat, w, x1, y1) - satAt(sat, w, x0, y1) - satAt(sat, w, x1, y0) + satAt(sat, w, x0, y0)

    private fun satAt(
        sat: DoubleArray,
        w: Int,
        x: Double,
        y: Double,
    ): Double {
        val ix = x.toInt()
        val iy = y.toInt()
        val fx = x - ix
        val fy = y - iy
        val stride = w + 1
        val rows = sat.size / stride
        val x1 = min(ix + 1, w)
        val y1 = min(iy + 1, rows - 1)
        val a = sat[iy * stride + ix]
        val b = sat[iy * stride + x1]
        val c = sat[y1 * stride + ix]
        val d = sat[y1 * stride + x1]
        return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy
    }

    private fun packed(c: DoubleArray): Int {
        val r = c[0].roundToInt().coerceIn(0, 255)
        val g = c[1].roundToInt().coerceIn(0, 255)
        val b = c[2].roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun gaussian(rnd: Random): Double {
        var s: Double
        var u: Double
        var v: Double
        do {
            u = rnd.nextDouble() * 2 - 1
            v = rnd.nextDouble() * 2 - 1
            s = u * u + v * v
        } while (s >= 1.0 || s == 0.0)
        return u * sqrt(-2.0 * kotlin.math.ln(s) / s)
    }

    /** A separable Gaussian blur of standard deviation [sigma] pixels. */
    fun blur(
        pixels: IntArray,
        w: Int,
        h: Int,
        sigma: Double,
    ): IntArray {
        val radius = max(1, (3.0 * sigma).toInt())
        val kernel = DoubleArray(2 * radius + 1) { exp(-((it - radius) * (it - radius)) / (2.0 * sigma * sigma)) }
        val total = kernel.sum()
        for (i in kernel.indices) kernel[i] /= total
        val tmp = DoubleArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0.0
                for (k in -radius..radius) sum += kernel[k + radius] * pixels[y * w + (x + k).coerceIn(0, w - 1)]
                tmp[y * w + x] = sum
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0.0
                for (k in -radius..radius) sum += kernel[k + radius] * tmp[(y + k).coerceIn(0, h - 1) * w + x]
                out[y * w + x] = sum.roundToInt().coerceIn(0, 255)
            }
        }
        return out
    }

    /** JPEG's own loss, applied to the luma: 8 by 8 DCT, the standard luminance quantisation table scaled for [quality], inverse DCT. */
    fun jpegLike(
        pixels: IntArray,
        w: Int,
        h: Int,
        quality: Int,
    ): IntArray {
        val table =
            intArrayOf(
                16,
                11,
                10,
                16,
                24,
                40,
                51,
                61,
                12,
                12,
                14,
                19,
                26,
                58,
                60,
                55,
                14,
                13,
                16,
                24,
                40,
                57,
                69,
                56,
                14,
                17,
                22,
                29,
                51,
                87,
                80,
                62,
                18,
                22,
                37,
                56,
                68,
                109,
                103,
                77,
                24,
                35,
                55,
                64,
                81,
                104,
                113,
                92,
                49,
                64,
                78,
                87,
                103,
                121,
                120,
                101,
                72,
                92,
                95,
                98,
                112,
                100,
                103,
                99,
            )
        val scale = if (quality < 50) 5000.0 / quality else 200.0 - 2.0 * quality
        val q = DoubleArray(64) { max(1.0, ((table[it] * scale + 50.0) / 100.0).toInt().toDouble()) }
        val cosines = Array(8) { u -> DoubleArray(8) { x -> cos((2 * x + 1) * u * PI / 16.0) } }
        val out = IntArray(w * h)
        val block = DoubleArray(64)
        val coeff = DoubleArray(64)
        for (by in 0 until h step 8) {
            for (bx in 0 until w step 8) {
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        block[y * 8 + x] = pixels[min(h - 1, by + y) * w + min(w - 1, bx + x)] - 128.0
                    }
                }
                for (v in 0 until 8) {
                    for (u in 0 until 8) {
                        var sum = 0.0
                        for (y in 0 until 8) for (x in 0 until 8) sum += block[y * 8 + x] * cosines[u][x] * cosines[v][y]
                        val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                        val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                        val value = 0.25 * cu * cv * sum
                        coeff[v * 8 + u] = (value / q[v * 8 + u]).roundToInt() * q[v * 8 + u]
                    }
                }
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        var sum = 0.0
                        for (v in 0 until 8) {
                            for (u in 0 until 8) {
                                val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                                val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                                sum += cu * cv * coeff[v * 8 + u] * cosines[u][x] * cosines[v][y]
                            }
                        }
                        val px = bx + x
                        val py = by + y
                        if (px < w && py < h) out[py * w + px] = (0.25 * sum + 128.0).roundToInt().coerceIn(0, 255)
                    }
                }
            }
        }
        return out
    }

    private fun matrix(vararg v: Double): DoubleArray = v

    /** The 3 by 3 whose columns are [r]'s first two columns and the translation (0, 0, [distance]). */
    private fun matrixCols(
        r: DoubleArray,
        distance: Double,
    ): DoubleArray = doubleArrayOf(r[0], r[1], 0.0, r[3], r[4], 0.0, r[6], r[7], distance)

    private fun multiply(
        a: DoubleArray,
        b: DoubleArray,
    ): DoubleArray {
        val out = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) out[i * 3 + j] = (0 until 3).sumOf { k -> a[i * 3 + k] * b[k * 3 + j] }
        return out
    }
}
