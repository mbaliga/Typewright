package dev.aarso.typewright.core.geometry

/**
 * A point on a [CurveFormat.CUBIC] contour's own boundary, addressed the way a picked point on an
 * outline naturally is: which of [Contour.cubicSegments] it falls on ([segmentIndex], 0-based) and
 * where along that segment ([t] — exactly [CurveSegment.pointAt]'s own parameter: `0.0` is the
 * segment's own start, `1.0` its own end). `t = 0.0`/`t = 1.0` addresses an *existing* on-curve
 * anchor directly (no new point needs inserting there); anything strictly between addresses a point
 * [knifeContour] creates by de Casteljau subdivision ([splitCubicAt] — see [knifeContour]'s own
 * KDoc for why this module's own existing splitter, not `engine-construct`'s, is reused here).
 */
data class ContourLocation(
    val segmentIndex: Int,
    val t: Double,
) {
    init {
        require(t in 0.0..1.0) { "t must be in [0, 1], was $t" }
    }
}

/** [knifeContour]'s own per-piece bookkeeping while it rebuilds [Contour.cubicSegments] with the two cut locations inserted as real anchors — which of the two cuts, if either, each resulting sub-segment's own start point is. */
private data class TaggedSegment(
    val segment: CurveSegment.Cubic,
    val startsAtFirst: Boolean,
    val startsAtSecond: Boolean,
)

/**
 * "Cut/knife" (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373): splits [contour] into two closed contours
 * at two picked locations on its own boundary, [first] and [second] — exactly what a knife or
 * scissors tool in a font editor does: pick two points on an outline, and get two shapes back,
 * split along a straight line between them.
 *
 * **Why de Casteljau subdivision, and why this module's own copy rather than `engine-construct`'s.**
 * The natural primitive for this is a de Casteljau split at a parameter `t` — `engine-construct`'s
 * `Booleans.kt` already has one (`CurveSegment.Cubic.subdivide`, used there for its own
 * curve-restoring splits). But `core-geometry` cannot depend on `engine-construct`
 * (`settings.gradle.kts` plus `engine-construct/build.gradle.kts`'s own `api(project(":core-geometry"))`:
 * the dependency runs the one way, `engine-construct` on `core-geometry`, never the reverse), so
 * reusing that copy directly is not an option, and this Palette command belongs in `core-geometry`
 * with its seven siblings, not moved to `engine-construct` for the sake of one shared primitive.
 * Rather than add a second, near-identical de Casteljau implementation to reach it, this reuses
 * `TypeConstraints.kt`'s own [splitCubicAt] — the same standard construction, already written,
 * tested and in production use inside this very module (P2b's extrema-insertion stage) — so there
 * ends up being exactly one de Casteljau split in each of the two modules that independently need
 * one, never a third copy invented here.
 *
 * **Method.**
 * 1. Canonicalise [first] and [second] ([canonicalizeLocation]: a location with `t >= 1.0` is
 *    renamed to `t = 0.0` on the next segment — its own equivalent point) and require they resolve
 *    to two genuinely different locations; a knife needs two distinct picks.
 * 2. Walk [contour]'s segments in order, subdividing (via [splitCubicAt]) wherever [first] or
 *    [second] falls strictly inside a segment, so both picked locations become real on-curve
 *    anchors in a rebuilt, gap-free segment list that still traces exactly [contour]'s own original
 *    shape (this is the general case [splitCubicAtParameters] already handles for a single cut
 *    point per segment; here two independent locations can, between them, land on the same
 *    original segment, so this walks and tags each resulting piece directly rather than reusing
 *    that helper's own single-caller contract unchanged).
 * 3. Split that rebuilt list into its two cyclic arcs between the two new anchors — one running
 *    [first] to [second], the other [second] to [first] — and close each arc into its own contour
 *    with one straight segment ([straightLineCubic]) directly between the two cut points. The two
 *    closing segments are the same straight cut, walked in opposite directions by the two results;
 *    their signed-area contributions exactly cancel when summed, so `loopA.signedArea() +
 *    loopB.signedArea()` reproduces [contour]'s own [Contour.signedArea] up to ordinary
 *    floating-point rounding — verified directly in `ContourKnifeTest`.
 *
 * [contour] must already be [CurveFormat.CUBIC].
 */
fun knifeContour(
    contour: Contour,
    first: ContourLocation,
    second: ContourLocation,
): Pair<Contour, Contour> {
    require(contour.format == CurveFormat.CUBIC) { "knifeContour operates on a CUBIC contour, was ${contour.format}" }
    val segments = contour.cubicSegments()
    val segmentCount = segments.size
    require(first.segmentIndex in 0 until segmentCount) {
        "first.segmentIndex ${first.segmentIndex} is out of range for $segmentCount segment(s)"
    }
    require(second.segmentIndex in 0 until segmentCount) {
        "second.segmentIndex ${second.segmentIndex} is out of range for $segmentCount segment(s)"
    }
    val a = canonicalizeLocation(first, segmentCount)
    val b = canonicalizeLocation(second, segmentCount)
    require(a != b) {
        "knifeContour needs two distinct cut locations; both first and second resolved to segment ${a.segmentIndex} at t=${a.t}"
    }

    val rebuilt = mutableListOf<TaggedSegment>()
    for (i in 0 until segmentCount) {
        val cutsHere =
            listOfNotNull(
                if (a.segmentIndex == i && a.t > 0.0) Triple(a.t, true, false) else null,
                if (b.segmentIndex == i && b.t > 0.0) Triple(b.t, false, true) else null,
            ).sortedBy { it.first }

        var remaining = segments[i]
        var consumedT = 0.0
        var startsAtFirst = a.segmentIndex == i && a.t == 0.0
        var startsAtSecond = b.segmentIndex == i && b.t == 0.0
        for ((t, isFirst, isSecond) in cutsHere) {
            val localT = (t - consumedT) / (1.0 - consumedT)
            val (left, right) = splitCubicAt(remaining, localT)
            rebuilt += TaggedSegment(left, startsAtFirst, startsAtSecond)
            remaining = right
            consumedT = t
            startsAtFirst = isFirst
            startsAtSecond = isSecond
        }
        rebuilt += TaggedSegment(remaining, startsAtFirst, startsAtSecond)
    }

    val aIndex = rebuilt.indexOfFirst { it.startsAtFirst }
    val bIndex = rebuilt.indexOfFirst { it.startsAtSecond }
    check(aIndex >= 0 && bIndex >= 0) { "internal error: cut anchors were not found after subdivision" }

    val arcFirstToSecond = cyclicSlice(rebuilt, aIndex, bIndex).map { it.segment }
    val arcSecondToFirst = cyclicSlice(rebuilt, bIndex, aIndex).map { it.segment }
    val pointFirst = rebuilt[aIndex].segment.start
    val pointSecond = rebuilt[bIndex].segment.start

    val loopFirstToSecond = buildCubicContour(arcFirstToSecond + straightLineCubic(pointSecond, pointFirst))
    val loopSecondToFirst = buildCubicContour(arcSecondToFirst + straightLineCubic(pointFirst, pointSecond))
    return loopFirstToSecond to loopSecondToFirst
}

/** [location], with `t >= 1.0` renamed to the equivalent `t = 0.0` on the next segment (cyclically) — its own canonical form, so a location supplied as an existing anchor's *end* and one supplied as the next segment's *start* compare equal. */
private fun canonicalizeLocation(
    location: ContourLocation,
    segmentCount: Int,
): ContourLocation = if (location.t >= 1.0) ContourLocation((location.segmentIndex + 1) % segmentCount, 0.0) else location

/** [list], read cyclically from [fromIndexInclusive] forward up to but not including [toIndexExclusive] — cyclic list slicing for [knifeContour]'s own two arcs. */
private fun <T> cyclicSlice(
    list: List<T>,
    fromIndexInclusive: Int,
    toIndexExclusive: Int,
): List<T> {
    val n = list.size
    val count = ((toIndexExclusive - fromIndexInclusive) % n + n) % n
    return (0 until count).map { list[(fromIndexInclusive + it) % n] }
}
