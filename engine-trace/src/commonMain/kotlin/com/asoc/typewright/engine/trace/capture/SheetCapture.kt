// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.estimatePaperLevel
import com.asoc.typewright.scripts.sheets.MmPoint
import com.asoc.typewright.scripts.sheets.MmRect
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import com.asoc.typewright.scripts.sheets.SheetIdentity
import com.asoc.typewright.scripts.sheets.SheetLayout
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Reads a photograph or scan of one of the printed template sheets: the pure part of Capture (handoff M1 step 1), with no
 * platform code and no OpenCV, so desktop and web read photographs exactly as the phone does.
 *
 * 1. **Corners.** [findCornerMarks] finds the four black squares. Fewer than four is [CaptureFailure.CornersMissing],
 *    naming the missing one when exactly three were seen.
 * 2. **Which sheet, which way up.** The four marks are given every rotation and each paper size in turn; for each the
 *    homography from page millimetres to photograph pixels is solved ([Homography.fromCorrespondences]) and the
 *    identity code printed between the upper marks is sampled through it and decoded ([SheetIdentity.decode]). The
 *    hypothesis whose code passes its start pattern and CRC, and whose paper bit agrees with the paper tried, is the sheet;
 *    none is [CaptureFailure.IdentityUnreadable].
 * 3. **Quality.** Edge softness of the marks, the angle the sheet is seen at, glare, shadow and contrast are measured
 *    ([CaptureQuality]) and, unless [CaptureOptions.enforceQualityGates] is off, a value past its limit is a named failure.
 * 4. **Cells.** Each of the sheet's cells is cut out flat and square at [CaptureOptions.cellPxPerMm] by sampling the
 *    photograph through the homography, the paper's brightness is divided out, the printed guides (pale grey) are dropped
 *    because they are far lighter than pen, and the drawing is cropped, measured and named for its template glyph.
 *
 * Nothing in the whole chain looks at what the drawing is: the glyph a cell holds is the one the sheet's template puts there,
 * so a misread comes only from geometry, and a cell whose ink touches its edge or is too faint is put to the person.
 */
object SheetCapture {
    /** The 10-to-90% width of a step edge blurred by a Gaussian of standard deviation sigma is this many sigmas. */
    private const val EDGE_WIDTH_PER_SIGMA = 2.563

    /** A pixel this bright (of 255) is taken as blown out (our heuristic). */
    private const val BLOWN_OUT = 252

    /** If the darkest quarter of the sheet's pixels is brighter than this, the photograph is bright all over and not glared (our heuristic). */
    private const val GENERALLY_BRIGHT = 235

    /** Reads [photo]. */
    fun read(
        photo: GrayscaleRaster,
        options: CaptureOptions = CaptureOptions(),
    ): CaptureResult {
        val search = findCornerMarks(photo)
        if (!search.complete) {
            return CaptureResult.Failure(
                CaptureFailure.CornersMissing(search.marks.map { it.corner }, search.missing, search.foundCount),
            )
        }
        // Seen from far to the side the code's small squares cannot be read, and "the code could not be read" would be the wrong
        // thing to say: the angle is the cause, so it is judged before the code is.
        val perspective = Measurement(perspectiveOf(search.marks), options.maxPerspective, "×")
        if (options.enforceQualityGates && perspective.value > perspective.limit) {
            return CaptureResult.Failure(CaptureFailure.PerspectiveTooExtreme(perspective))
        }
        val hypothesis = identify(photo, search.marks) ?: return CaptureResult.Failure(CaptureFailure.IdentityUnreadable)
        val identity = hypothesis.identity
        val layout = SheetLayout.of(identity.paper, identity.script)
        val toPhoto = hypothesis.toPhoto

        val quality = measureQuality(photo, toPhoto, layout, search.marks, options)
        if (options.enforceQualityGates) {
            gate(quality, options)?.let { return CaptureResult.Failure(it, quality) }
        }

        val set = PrintSheets.forScript(identity.script)
        if (set.sheets.size != identity.count) return CaptureResult.Failure(CaptureFailure.IdentityUnreadable, quality)
        val spec = set.sheets[identity.index]
        val cells = CellReader.readAll(photo, toPhoto, layout, spec, options.cellPxPerMm, quality.edgeWidth.value / EDGE_WIDTH_PER_SIGMA)
        return CaptureResult.Success(CapturedSheet(identity, layout, spec, toPhoto, search.marks, quality, cells))
    }

    private class Hypothesis(
        val identity: SheetIdentity,
        val toPhoto: Homography,
        val margin: Double,
    )

    /** Tries every rotation of the four marks against both papers and keeps the best reading of the identity code that checks out. */
    private fun identify(
        photo: GrayscaleRaster,
        marks: List<FoundMark>,
    ): Hypothesis? {
        // The marks arrive clockwise from the picture's top left; the page's own corners go the same way.
        val ordered = marks.sortedBy { it.corner.ordinal }
        var best: Hypothesis? = null
        for (paper in PaperSize.entries) {
            val model = SheetLayout.of(paper, PrintScript.LATIN)
            for (rotation in 0 until 4) {
                val pageCorners = List(4) { i -> model.markCentres[(i + rotation) % 4] }
                // image mark i is page corner (i + rotation) % 4
                val h =
                    Homography.fromCorrespondences(
                        pageCorners.map { Pt(it.x, it.y) },
                        ordered.map { it.centre },
                    ) ?: continue
                val reading = sampleCode(photo, h, model, ordered) ?: continue
                val identity = SheetIdentity.decode(reading.bits) ?: continue
                if (identity.paper != paper) continue
                if (best == null || reading.margin > best.margin) best = Hypothesis(identity, h, reading.margin)
            }
        }
        return best
    }

    private class CodeReading(
        val bits: List<Boolean>,
        val margin: Double,
    )

    /** The code's modules read through [toPhoto], 1 where a module is dark; [margin] is how clearly (the smallest gap from the decision level). */
    private fun sampleCode(
        photo: GrayscaleRaster,
        toPhoto: Homography,
        model: SheetLayout,
        marks: List<FoundMark>,
    ): CodeReading? {
        // What black and paper read as here: the marks' centres, and the paper beside the code strip.
        val black = marks.map { average(photo, it.centre, max(1.0, it.sidePx * 0.15)) }.average()
        val paperSamples =
            model.codeCentres.flatMap { c ->
                listOf(MmPoint(c.x, c.y - 4.0), MmPoint(c.x, c.y + 4.0)).map { toPhoto.apply(it.x, it.y) }
            }
        if (paperSamples.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val paper = paperSamples.map { average(photo, it, 2.0) }.sorted().let { it[it.size / 2] }
        if (paper - black < 40.0) return null
        // Black is a share of the paper's brightness, and the paper's brightness changes across a sheet that is shaded or lit from
        // one side, so each module is judged against the paper beside it, not against one level for the whole strip.
        val blackShare = (black / paper).coerceIn(0.0, 0.9)
        // A module is about 3 mm; read the middle of it, a third of its width either way.
        val sideMm = 1.0
        var margin = Double.MAX_VALUE
        val bits =
            model.codeCentres.map { c ->
                val samples =
                    listOf(-sideMm, 0.0, sideMm).flatMap { dx ->
                        listOf(-sideMm, 0.0, sideMm).map { dy -> toPhoto.apply(c.x + dx, c.y + dy) }
                    }
                if (samples.any { !it.x.isFinite() || !it.y.isFinite() }) return null
                val value = samples.sumOf { photo.sample(it.x, it.y) } / samples.size
                val near =
                    listOf(
                        MmPoint(c.x, c.y - 4.0),
                        MmPoint(c.x, c.y + 4.0),
                    ).map { toPhoto.apply(it.x, it.y) }.map { average(photo, it, 2.0) }.average()
                val level = near * (1.0 + blackShare) / 2.0
                margin = min(margin, abs(value - level) / (near * (1.0 - blackShare)))
                value < level
            }
        return CodeReading(bits, margin)
    }

    private fun average(
        photo: GrayscaleRaster,
        centre: Pt,
        radius: Double,
    ): Double {
        var sum = 0.0
        var count = 0
        val r = max(1, radius.roundToInt())
        for (dy in -r..r) {
            for (dx in -r..r) {
                sum += photo.sample(centre.x + dx, centre.y + dy)
                count++
            }
        }
        return sum / count
    }

    // ---- quality ----------------------------------------------------------------------------

    private fun measureQuality(
        photo: GrayscaleRaster,
        toPhoto: Homography,
        layout: SheetLayout,
        marks: List<FoundMark>,
        options: CaptureOptions,
    ): CaptureQuality {
        val edge = edgeWidthMm(photo, toPhoto, layout)
        val perspective = perspectiveOf(marks)

        val pxPerMm = 2.0
        val flat = SheetWarp(photo, toPhoto).warp(MmRect(0.0, 0.0, layout.pageWidthMm, layout.pageHeightMm), pxPerMm)
        // The paper's brightness with glare left out (a highlight over a tenth of the sheet must not lift the estimate beside it).
        val level = estimatePaperLevel(flat, radius = 30, saturation = BLOWN_OUT, followShadows = true)
        val inside = ArrayList<Int>()
        val raw = ArrayList<Int>()
        val margin = (14.0 * pxPerMm).toInt()
        for (y in margin until flat.height - margin) {
            for (x in margin until flat.width - margin) {
                inside += level[y * flat.width + x].toInt()
                raw += flat.pixels[y * flat.width + x]
            }
        }
        inside.sort()
        raw.sort()
        val median = inside[inside.size / 2].toDouble()
        val low = inside[inside.size / 20].toDouble()
        // Glare is the share of the sheet that is blown out to white, but only when most of the sheet is not: a photograph that is
        // simply bright all over has white paper, which is not glare.
        val blown = raw.count { it >= BLOWN_OUT }.toDouble() / raw.size
        val glare = if (raw[raw.size / 4] < GENERALLY_BRIGHT) blown else 0.0
        val shadow = if (median > 0.0) 1.0 - low / median else 0.0
        val contrast = markContrast(photo, marks, median)
        return CaptureQuality(
            edgeWidth = Measurement(edge, options.maxEdgeWidthMm, "mm"),
            perspective = Measurement(perspective, options.maxPerspective, "×"),
            glare = Measurement(glare, options.maxGlareFraction, "of the sheet"),
            shadow = Measurement(shadow, options.maxShadowDepth, "darker"),
            markContrast = Measurement(contrast, options.minMarkContrast, "darker than paper"),
        )
    }

    /** How much longer the sheet's nearer edge looks than its farther one, for the more skewed of the two pairs of opposite edges: 1 straight on. */
    private fun perspectiveOf(marks: List<FoundMark>): Double {
        val ordered = marks.sortedBy { it.corner.ordinal }.map { it.centre }
        val top = dist(ordered[0], ordered[1])
        val bottom = dist(ordered[3], ordered[2])
        val left = dist(ordered[0], ordered[3])
        val right = dist(ordered[1], ordered[2])
        return max(max(top, bottom) / min(top, bottom), max(left, right) / min(left, right))
    }

    private fun markContrast(
        photo: GrayscaleRaster,
        marks: List<FoundMark>,
        paperMedian: Double,
    ): Double {
        val black = marks.map { average(photo, it.centre, max(1.0, it.sidePx * 0.15)) }.average()
        return if (paperMedian <= 0.0) 0.0 else ((paperMedian - black) / paperMedian).coerceIn(0.0, 1.0)
    }

    /**
     * How wide the edge of a printed mark is, in millimetres of paper: along short horizontal profiles through the left and
     * right edges of the four marks, the distance over which the brightness goes from 90% to 10% of the way down from paper to
     * black. A perfectly sharp edge reads about the size of one pixel; a Gaussian blur of standard deviation s reads about
     * 2.56 s. The median of the profiles is returned.
     */
    private fun edgeWidthMm(
        photo: GrayscaleRaster,
        toPhoto: Homography,
        layout: SheetLayout,
    ): Double {
        val half = SheetLayout.MARK_SIDE_MM / 2.0
        val widths = mutableListOf<Double>()
        val step = 0.04
        for (centre in layout.markCentres) {
            for (direction in listOf(-1.0, 1.0)) {
                for (dy in listOf(-2.0, -1.0, 0.0, 1.0, 2.0)) {
                    // x runs from outside the mark (paper) to inside it (black), along the page's x axis.
                    val outside = half + 2.5
                    val inside = half - 2.5
                    val count = ((outside - inside) / step).toInt()
                    val values =
                        DoubleArray(count + 1) { i ->
                            val offset = outside - i * step
                            val p = toPhoto.apply(centre.x + direction * offset, centre.y + dy)
                            photo.sample(p.x, p.y)
                        }
                    val paper = values.take(count / 5).average()
                    val black = values.takeLast(count / 5).average()
                    if (paper - black < 30.0) continue

                    fun first(level: Double): Double? {
                        for (i in values.indices) if (values[i] <= level) return i * step
                        return null
                    }
                    val x90 = first(paper - 0.1 * (paper - black))
                    val x10 = first(paper - 0.9 * (paper - black))
                    if (x90 != null && x10 != null && x10 >= x90) widths += x10 - x90
                }
            }
        }
        if (widths.isEmpty()) return Double.MAX_VALUE
        widths.sort()
        return widths[widths.size / 2]
    }

    private fun dist(
        a: Pt,
        b: Pt,
    ): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    private fun gate(
        quality: CaptureQuality,
        options: CaptureOptions,
    ): CaptureFailure? =
        when {
            quality.markContrast.value < options.minMarkContrast -> {
                CaptureFailure.LightingTooStrong(LightingProblem.LOW_CONTRAST, quality.markContrast)
            }

            quality.perspective.value > options.maxPerspective -> {
                CaptureFailure.PerspectiveTooExtreme(quality.perspective)
            }

            quality.edgeWidth.value > options.maxEdgeWidthMm -> {
                CaptureFailure.TooBlurry(quality.edgeWidth)
            }

            quality.glare.value > options.maxGlareFraction -> {
                CaptureFailure.LightingTooStrong(LightingProblem.GLARE, quality.glare)
            }

            quality.shadow.value > options.maxShadowDepth -> {
                CaptureFailure.LightingTooStrong(LightingProblem.SHADOW, quality.shadow)
            }

            else -> {
                null
            }
        }
}

/**
 * Cuts flat, square pieces of a sheet out of its photograph: the region [warp] is asked for, in page millimetres, sampled
 * through [toPhoto] (page millimetres to photograph pixels) at the requested resolution. Output pixel `(i, j)` is the page
 * point `(area.left + (i + 0.5) / pxPerMm, area.top + (j + 0.5) / pxPerMm)`. Where the photograph has more detail than the
 * output asks for, several samples per output pixel are averaged so a fine photograph is not aliased.
 */
class SheetWarp(
    private val photo: GrayscaleRaster,
    private val toPhoto: Homography,
) {
    /** Photograph pixels per millimetre of paper near ([x], [y]) on the page. */
    fun photoPxPerMm(
        x: Double,
        y: Double,
    ): Double {
        val a = toPhoto.apply(x, y)
        val b = toPhoto.apply(x + 1.0, y)
        val c = toPhoto.apply(x, y + 1.0)
        return (sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)) + sqrt((c.x - a.x) * (c.x - a.x) + (c.y - a.y) * (c.y - a.y))) /
            2.0
    }

    fun warp(
        area: MmRect,
        pxPerMm: Double,
    ): GrayscaleRaster {
        val w = ceil(area.width * pxPerMm).toInt().coerceAtLeast(1)
        val h = ceil(area.height * pxPerMm).toInt().coerceAtLeast(1)
        val centre = area.centre
        val factor = (photoPxPerMm(centre.x, centre.y) / pxPerMm).let { if (it > 1.5) min(4, ceil(it).toInt()) else 1 }
        val out = IntArray(w * h)
        val m = toPhoto.m
        for (j in 0 until h) {
            for (i in 0 until w) {
                var sum = 0.0
                for (sj in 0 until factor) {
                    for (si in 0 until factor) {
                        val px = area.left + (i + (si + 0.5) / factor) / pxPerMm
                        val py = area.top + (j + (sj + 0.5) / factor) / pxPerMm
                        val wgt = m[6] * px + m[7] * py + m[8]
                        sum += photo.sampleCubic((m[0] * px + m[1] * py + m[2]) / wgt, (m[3] * px + m[4] * py + m[5]) / wgt)
                    }
                }
                out[j * w + i] = (sum / (factor * factor)).roundToInt().coerceIn(0, 255)
            }
        }
        return GrayscaleRaster(w, h, out)
    }
}
