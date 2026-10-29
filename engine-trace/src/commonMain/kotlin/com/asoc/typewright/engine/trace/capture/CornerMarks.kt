// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.engine.trace.Connectivity
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.estimatePaperLevel
import com.asoc.typewright.engine.trace.labelComponents
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A corner of the photograph, named by where it is in the picture (not by where it is on the page: the sheet may be held any way up). */
enum class ImageCorner(
    val label: String,
) {
    TOP_LEFT("top left"),
    TOP_RIGHT("top right"),
    BOTTOM_RIGHT("bottom right"),
    BOTTOM_LEFT("bottom left"),
}

/** One corner mark the search found: its [centre] in the photograph's pixel coordinates and its size, as the side of the square of equal area. */
data class FoundMark(
    val centre: Pt,
    val sidePx: Double,
    val corner: ImageCorner,
)

/**
 * What a search of one photograph for the four corner marks came to: the [marks] found (their corner named by position in
 * the picture, clockwise from the top left when all four are there) and the [missing] corners, empty when all four are.
 * [candidates] is how many dark, compact, square-ish blobs the search had to choose from, for a status line that says what
 * it saw.
 */
class MarkSearch(
    val marks: List<FoundMark>,
    val missing: List<ImageCorner>,
    val candidates: Int,
) {
    val foundCount: Int get() = marks.size
    val complete: Boolean get() = marks.size == 4
}

private class Blob(
    val centre: Pt,
    val area: Double,
    val side: Double,
    val score: Double,
    /** The ratio of the smaller to the larger principal moment: 1 for a square seen straight on, less as it is seen more obliquely. */
    val isotropy: Double,
    /** The component's bounding box, in the reduced image's pixels: left, top, right, bottom (inclusive). */
    val box: IntArray,
)

/**
 * Finds the four printed corner marks (solid black squares) in a photograph of a template sheet, tolerant of perspective,
 * rotation, uneven light and a coloured sheet.
 *
 * **Method.** The photograph is reduced to about 900 pixels, the paper brightness under it is estimated
 * ([estimatePaperLevel]; a shadow or a gradient shows up in that estimate and divides out), and everything at least 45%
 * darker than its own paper is labelled into connected components. A component is a *candidate mark* when it is solid and
 * dark (mean darkness at least 0.65), does not touch the picture's edge, and has the shape of a square seen from some
 * angle: not a thin stroke (its second moments are within a factor of two of isotropic), filling a fair share of its
 * bounding box, with a compact outline (`4 pi A / P^2` of at least 0.5). Among up to twelve of the best candidates the
 * search takes the four that make the largest convex quadrilateral whose corners are of similar size (the smallest within
 * a factor of six of the largest in area) and whose sides do not differ by more than three and a half to one. If no four will
 * do, the best three that are of similar size and make roughly a right angle are returned with the fourth corner named
 * missing: that is the Camera's "3 of 4 corners".
 *
 * Each centre is then refined at full resolution as the darkness-weighted centroid of the mark's neighbourhood, which is
 * exact for a blurred square and good to a fraction of a pixel.
 */
fun findCornerMarks(
    raster: GrayscaleRaster,
    diagnostics: ((String) -> Unit)? = null,
): MarkSearch {
    val factor = max(1, max(raster.width, raster.height) / 900)
    val small = raster.downscale(factor)
    val level = estimatePaperLevel(small, radius = max(8, max(small.width, small.height) / 24), followShadows = true)
    val darkness = DoubleArray(small.pixels.size) { (1.0 - small.pixels[it] / level[it].coerceAtLeast(1.0)).coerceIn(0.0, 1.0) }
    val strict = searchMarks(raster, small, darkness, factor, MARK_DARKNESS, MARK_MEAN_DARKNESS, diagnostics)
    if (strict.complete || strict.foundCount >= 2) return strict
    // A washed-out photograph has marks that are grey, not black, so none is dark enough to be found. Look again for squares that are
    // only a fifth darker than the paper and take the result only if it finds all four, so the contrast gate can say "use more light"
    // instead of "no corners". Never done once two marks have been seen: then it is a covered corner, not a pale sheet.
    val relaxed = searchMarks(raster, small, darkness, factor, FAINT_MARK_DARKNESS, FAINT_MARK_MEAN_DARKNESS, diagnostics)
    return if (relaxed.complete) relaxed else strict
}

/** The most elongated a mark's box may look: a square seen at 45 degrees is 1.4 to 1, and perspective adds to that (our heuristic). */
private const val MAX_BOX_ASPECT = 3.0

/** A blob this round is surely a square seen from not too far to the side; a corner reported missing is judged from such blobs only (our heuristic). */
private const val CONFIDENT_ISOTROPY = 0.5

/** The least round a mark may look, as the ratio of the smaller to the larger principal moment: a square seen from 45 degrees off its normal at the sheet's far edge is about 0.25 (our heuristic). */
private const val MIN_ISOTROPY = 0.22

/** A pixel at least this much darker than its paper can be part of a mark (our heuristic). */
private const val MARK_DARKNESS = 0.45

/** A mark's pixels are on average at least this dark against their paper (our heuristic). */
private const val MARK_MEAN_DARKNESS = 0.65

/** [MARK_DARKNESS] for the second look at a washed-out photograph (our heuristic). */
private const val FAINT_MARK_DARKNESS = 0.2

/** [MARK_MEAN_DARKNESS] for the second look at a washed-out photograph (our heuristic). */
private const val FAINT_MARK_MEAN_DARKNESS = 0.28

private fun searchMarks(
    raster: GrayscaleRaster,
    small: GrayscaleRaster,
    darkness: DoubleArray,
    factor: Int,
    minDarkness: Double,
    minMeanDarkness: Double,
    diagnostics: ((String) -> Unit)?,
): MarkSearch {
    val labeling =
        labelComponents(small.width, small.height, Connectivity.EIGHT) { x, y -> darkness[y * small.width + x] >= minDarkness }
    val n = labeling.componentCount
    val imageArea = small.width.toDouble() * small.height
    val minArea = max(12.0, 0.00003 * imageArea)
    val maxArea = 0.02 * imageArea

    // Per-component moments in one pass.
    val area = IntArray(n)
    val sx = DoubleArray(n)
    val sy = DoubleArray(n)
    val sxx = DoubleArray(n)
    val syy = DoubleArray(n)
    val sxy = DoubleArray(n)
    val minX = IntArray(n) { Int.MAX_VALUE }
    val minY = IntArray(n) { Int.MAX_VALUE }
    val maxX = IntArray(n) { -1 }
    val maxY = IntArray(n) { -1 }
    val boundary = IntArray(n)
    val darkSum = DoubleArray(n)
    for (y in 0 until small.height) {
        for (x in 0 until small.width) {
            val c = labeling.labels[y * small.width + x]
            if (c < 0) continue
            area[c]++
            sx[c] += x
            sy[c] += y
            sxx[c] += x.toDouble() * x
            syy[c] += y.toDouble() * y
            sxy[c] += x.toDouble() * y
            darkSum[c] += darkness[y * small.width + x]
            if (x < minX[c]) minX[c] = x
            if (x > maxX[c]) maxX[c] = x
            if (y < minY[c]) minY[c] = y
            if (y > maxY[c]) maxY[c] = y
            val edge =
                x == 0 || y == 0 || x == small.width - 1 || y == small.height - 1 ||
                    labeling.labels[y * small.width + x - 1] != c || labeling.labels[y * small.width + x + 1] != c ||
                    labeling.labels[(y - 1) * small.width + x] != c || labeling.labels[(y + 1) * small.width + x] != c
            if (edge) boundary[c]++
        }
    }
    val blobs = mutableListOf<Blob>()
    for (c in 0 until n) {
        val a = area[c].toDouble()
        if (a < minArea || a > maxArea || labeling.componentTouchesBorder[c]) continue
        val boxW = (maxX[c] - minX[c] + 1).toDouble()
        val boxH = (maxY[c] - minY[c] + 1).toDouble()
        val where = "blob at (${minX[c] * factor},${minY[c] * factor}) area=${a.toInt()} box=${boxW.toInt()}x${boxH.toInt()}"
        if (a / (boxW * boxH) < 0.4 || max(boxW, boxH) / min(boxW, boxH) > MAX_BOX_ASPECT) {
            diagnostics?.invoke("$where rejected: box fill/aspect")
            continue
        }
        if (darkSum[c] / a < minMeanDarkness) {
            diagnostics?.invoke("$where rejected: mean darkness ${darkSum[c] / a}")
            continue
        }
        val mx = sx[c] / a
        val my = sy[c] / a
        val cxx = sxx[c] / a - mx * mx
        val cyy = syy[c] / a - my * my
        val cxy = sxy[c] / a - mx * my
        val trace = cxx + cyy
        val det = cxx * cyy - cxy * cxy
        val spread = sqrt(max(0.0, trace * trace / 4.0 - det))
        val big = trace / 2.0 + spread
        val little = trace / 2.0 - spread
        if (big <= 0.0 || little / big < MIN_ISOTROPY) {
            diagnostics?.invoke("$where rejected: isotropy ${little / big}")
            continue
        }
        val compact = 4.0 * PI * a / (boundary[c].toDouble() * boundary[c])
        if (compact < 0.5) {
            diagnostics?.invoke("$where rejected: compactness $compact")
            continue
        }
        diagnostics?.invoke("$where accepted: compact=$compact isotropy=${little / big}")
        blobs += Blob(Pt(mx, my), a, sqrt(a), compact * (little / big), little / big, intArrayOf(minX[c], minY[c], maxX[c], maxY[c]))
    }
    val best = blobs.sortedByDescending { it.score }.take(12)
    // Four may include marks seen very obliquely, because their sizes can be checked against each other; three cannot be checked,
    // so only blobs that are clearly square are trusted to stand for a corner when one is missing.
    val chosen = chooseFour(best) ?: chooseThree(best.filter { it.isotropy >= CONFIDENT_ISOTROPY })
    return when {
        chosen == null -> {
            // Fewer than three marks make a shape: report the strongest one or two, named by the quadrant of the picture they are in.
            val some =
                best.sortedByDescending { it.area }.take(2).map { b ->
                    val left = b.centre.x * factor < raster.width / 2.0
                    val top = b.centre.y * factor < raster.height / 2.0
                    val corner =
                        when {
                            top && left -> ImageCorner.TOP_LEFT
                            top -> ImageCorner.TOP_RIGHT
                            left -> ImageCorner.BOTTOM_LEFT
                            else -> ImageCorner.BOTTOM_RIGHT
                        }
                    refined(raster, factor, b, corner)
                }
            MarkSearch(some, emptyList(), blobs.size)
        }

        chosen.size == 4 -> {
            val corners = namedCorners(chosen.map { it.centre })
            MarkSearch(
                chosen
                    .mapIndexed {
                        i,
                        b,
                        ->
                        refined(raster, factor, b, corners[i])
                    }.sortedBy { it.corner.ordinal },
                emptyList(),
                blobs.size,
            )
        }

        else -> {
            val (missingCorner, names) = missingCorner(chosen.map { it.centre })
            MarkSearch(
                chosen
                    .mapIndexed {
                        i,
                        b,
                        ->
                        refined(raster, factor, b, names[i])
                    }.sortedBy { it.corner.ordinal },
                listOf(missingCorner),
                blobs.size,
            )
        }
    }
}

private fun refined(
    raster: GrayscaleRaster,
    factor: Int,
    blob: Blob,
    corner: ImageCorner,
): FoundMark {
    // Back to full-resolution pixel coordinates: small pixel i covers full pixels i*f .. i*f+f-1.
    val cx = (blob.centre.x + 0.5) * factor - 0.5
    val cy = (blob.centre.y + 0.5) * factor - 0.5
    val side = blob.side * factor
    // Only the mark's own neighbourhood: its box plus a few pixels, so printed text near a mark cannot pull the centre.
    val x0 = (blob.box[0] * factor - 3).coerceAtLeast(0)
    val x1 = ((blob.box[2] + 1) * factor + 2).coerceAtMost(raster.width - 1)
    val y0 = (blob.box[1] * factor - 3).coerceAtLeast(0)
    val y1 = ((blob.box[3] + 1) * factor + 2).coerceAtMost(raster.height - 1)
    // The paper level here: the brightest tenth of a wider window, which holds more paper than the mark's own box.
    val pad = max(6, (side * 0.6).toInt())
    val values = ArrayList<Int>()
    for (y in max(0, y0 - pad)..min(raster.height - 1, y1 + pad)) {
        for (x in max(0, x0 - pad)..min(raster.width - 1, x1 + pad)) values += raster.pixels[y * raster.width + x]
    }
    values.sort()
    val paper = values[(values.size * 9) / 10].toDouble().coerceAtLeast(1.0)
    var sw = 0.0
    var swx = 0.0
    var swy = 0.0
    for (y in y0..y1) {
        for (x in x0..x1) {
            val d = (1.0 - raster.pixels[y * raster.width + x] / paper).coerceIn(0.0, 1.0)
            val w = ((d - 0.1) / 0.8).coerceIn(0.0, 1.0)
            sw += w
            swx += w * x
            swy += w * y
        }
    }
    val centre = if (sw > 0.0) Pt(swx / sw, swy / sw) else Pt(cx, cy)
    return FoundMark(centre, side, corner)
}

/** The corner of the picture each of four points sits in, by position: the pair of lowest x+y and, among the rest, by angle about their centroid. */
private fun namedCorners(points: List<Pt>): List<ImageCorner> {
    val cx = points.sumOf { it.x } / points.size
    val cy = points.sumOf { it.y } / points.size
    // Clockwise (y down) from the top-left-most point.
    val order = points.indices.sortedBy { atan2(points[it].y - cy, points[it].x - cx) }
    val start = order.minBy { points[it].x + points[it].y }
    val ring = order.indices.map { order[(order.indexOf(start) + it) % order.size] }
    val names = Array(points.size) { ImageCorner.TOP_LEFT }
    for ((position, index) in ring.withIndex()) names[index] = ImageCorner.entries[position]
    return names.toList()
}

private fun missingCorner(three: List<Pt>): Pair<ImageCorner, List<ImageCorner>> {
    // The corner opposite the missing one is the one whose two edges to the others are nearly perpendicular.
    var apex = 0
    var bestCos = 2.0
    for (i in 0 until 3) {
        val a = three[(i + 1) % 3] - three[i]
        val b = three[(i + 2) % 3] - three[i]
        val c = abs((a.x * b.x + a.y * b.y) / (hypot(a) * hypot(b)))
        if (c < bestCos) {
            bestCos = c
            apex = i
        }
    }
    val p = three[apex]
    val a = three[(apex + 1) % 3]
    val b = three[(apex + 2) % 3]
    val fourth = Pt(a.x + b.x - p.x, a.y + b.y - p.y)
    val all = three + fourth
    val names = namedCorners(all)
    return names[3] to names.take(3)
}

private operator fun Pt.minus(o: Pt): Pt = Pt(x - o.x, y - o.y)

private fun hypot(p: Pt): Double = sqrt(p.x * p.x + p.y * p.y)

/**
 * The most the areas of the four marks as seen may differ: a sheet held at 25 degrees from a phone's distance shows its near
 * corners two to three times the area of its far ones, and the code's 3 mm modules, a ninth the area of a mark, must stay out.
 */
private const val MAX_AREA_RATIO = 6.0

private fun chooseFour(candidates: List<Blob>): List<Blob>? {
    var best: List<Blob>? = null
    var bestArea = 0.0
    val n = candidates.size
    for (a in 0 until n) {
        for (b in a + 1 until n) {
            for (c in b + 1 until n) {
                for (d in c + 1 until n) {
                    val four = listOf(candidates[a], candidates[b], candidates[c], candidates[d])
                    val areas = four.map { it.area }
                    if (areas.min() < areas.max() / MAX_AREA_RATIO) continue
                    if (!sizesAreConsistent(four)) continue
                    val quadArea = convexQuadArea(four.map { it.centre }) ?: continue
                    if (quadArea > bestArea) {
                        bestArea = quadArea
                        best = four
                    }
                }
            }
        }
    }
    return best
}

/**
 * Whether four blobs are the right sizes to be the four corners of one flat sheet. Seen in perspective, the side of a square
 * printed on a plane is a linear function of where it is in the picture (its distance from the camera is), so each blob's side
 * must be what the other three predict, to within [SIZE_CONSISTENCY]. Two adjacent modules of the identity code look like a
 * small mark and would otherwise stand in for a covered corner; they are far too small for where they are.
 */
private fun sizesAreConsistent(four: List<Blob>): Boolean {
    for (left in 0 until 4) {
        val rest = (0 until 4).filter { it != left }.map { four[it] }
        val predicted = affinePrediction(rest, four[left].centre) ?: return false
        if (kotlin.math.abs(predicted - four[left].side) > SIZE_CONSISTENCY * predicted.coerceAtLeast(1.0)) return false
    }
    return true
}

/** How far a mark's side may be, as a share of what the other three marks predict (our heuristic). */
private const val SIZE_CONSISTENCY = 0.35

/** The value at [at] of the plane `side = a x + b y + c` through three blobs, or null when they are in a line. */
private fun affinePrediction(
    three: List<Blob>,
    at: Pt,
): Double? {
    val (p, q, r) = three
    val ux = q.centre.x - p.centre.x
    val uy = q.centre.y - p.centre.y
    val vx = r.centre.x - p.centre.x
    val vy = r.centre.y - p.centre.y
    val det = ux * vy - uy * vx
    if (kotlin.math.abs(det) < 1e-9) return null
    val dx = at.x - p.centre.x
    val dy = at.y - p.centre.y
    val s = (dx * vy - dy * vx) / det
    val t = (ux * dy - uy * dx) / det
    return p.side + s * (q.side - p.side) + t * (r.side - p.side)
}

/** The area of the quadrilateral through [points] if they form a convex one with sides within 3.5 to 1 of each other, else null. */
private fun convexQuadArea(points: List<Pt>): Double? {
    val cx = points.sumOf { it.x } / 4.0
    val cy = points.sumOf { it.y } / 4.0
    val ring = points.sortedBy { atan2(it.y - cy, it.x - cx) }
    var sign = 0
    for (i in 0 until 4) {
        val a = ring[i]
        val b = ring[(i + 1) % 4]
        val c = ring[(i + 2) % 4]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        val s = if (cross > 0) 1 else -1
        if (sign == 0) {
            sign = s
        } else if (s != sign) {
            return null
        }
    }
    val sides = (0 until 4).map { hypot(ring[(it + 1) % 4] - ring[it]) }
    if (sides.min() < 1.0 || sides.max() / sides.min() > 3.5) return null
    var twice = 0.0
    for (i in 0 until 4) {
        val a = ring[i]
        val b = ring[(i + 1) % 4]
        twice += a.x * b.y - b.x * a.y
    }
    return abs(twice) / 2.0
}

private fun chooseThree(candidates: List<Blob>): List<Blob>? {
    var best: List<Blob>? = null
    var bestScore = 0.0
    val n = candidates.size
    for (a in 0 until n) {
        for (b in a + 1 until n) {
            for (c in b + 1 until n) {
                val three = listOf(candidates[a], candidates[b], candidates[c])
                val areas = three.map { it.area }
                if (areas.min() < areas.max() / MAX_AREA_RATIO) continue
                // One of the three must be the apex of a roughly right angle.
                var cosMin = 2.0
                var arm = 0.0
                for (i in 0 until 3) {
                    val u = three[(i + 1) % 3].centre - three[i].centre
                    val v = three[(i + 2) % 3].centre - three[i].centre
                    val cos = abs((u.x * v.x + u.y * v.y) / (hypot(u) * hypot(v)))
                    if (cos < cosMin) {
                        cosMin = cos
                        arm = min(hypot(u), hypot(v))
                    }
                }
                if (cosMin > 0.5) continue
                if (arm > bestScore) {
                    bestScore = arm
                    best = three
                }
            }
        }
    }
    return best
}
