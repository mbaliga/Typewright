// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

/**
 * What one [PaletteCommand] reads: a single contour in isolation, every contour of a glyph
 * together (direction correction is the one command that genuinely needs the whole glyph — see
 * [enforceContourDirections]'s own KDoc for why), or a bare, not-yet-closed point list (the one
 * shape [closePolylineToContour] takes, since there is no "open [Contour]" to hand it instead —
 * see that function's own KDoc). Lets a later UI task filter which commands even apply to the
 * current selection without special-casing any one command by name.
 */
enum class PaletteTarget {
    SINGLE_CONTOUR,
    GLYPH_CONTOURS,
    POINT_LIST,
}

/**
 * The eight Palette commands `TYPEWRIGHT_BUILD_BRIEF.md` line 372-373 names ("add extremes,
 * harmonise curvature, tidy/simplify with a live count, reverse contour, correct direction, round
 * coordinates, cut/knife, close/open contour"), as one small, closed registry a later UI task can
 * enumerate generically — list every command's [id]/[label] for a palette row or a command palette,
 * filter by [target] to grey out one that does not apply to the current selection — without
 * special-casing any one of them by name.
 *
 * **Why this registry carries metadata, not a uniform `invoke` function.** The eight operations
 * take genuinely different inputs — one contour alone (five commands); a whole glyph's contours
 * together ([CORRECT_DIRECTION]); a contour plus a picked join, a tolerance, or a pair of cut
 * locations ([HARMONISE_CURVATURE], [TIDY], [KNIFE]); a bare point list ([CLOSE_CONTOUR]) — so
 * forcing one shared function signature onto all eight would either be dishonest about what a
 * command actually needs or bury the real inputs (a tolerance slider's value, the two points a
 * knife gesture picked) inside an awkward wrapper type nobody else needs. Each entry's own KDoc
 * names the real function that implements it; a UI task wires [id] to that function's own call,
 * which is the "keep it simple" half of this task's own instructions (a sealed type *or* a set of
 * top-level functions with a consistent naming convention) rather than the sealed-sum-of-payloads
 * half.
 */
enum class PaletteCommand(
    val id: String,
    val label: String,
    val target: PaletteTarget,
) {
    /** [insertExtremaOnCurvePoints] (P2b; widened `public` for this command, `TypeConstraints.kt`). */
    ADD_EXTREMES("add-extremes", "Add extremes", PaletteTarget.SINGLE_CONTOUR),

    /** [harmoniseCurvatureAtJoin] (`Curvature.kt`). */
    HARMONISE_CURVATURE("harmonise-curvature", "Harmonise curvature", PaletteTarget.SINGLE_CONTOUR),

    /** [simplifyContour] (`ContourSimplify.kt`). */
    TIDY("tidy", "Tidy / simplify", PaletteTarget.SINGLE_CONTOUR),

    /** [Contour.reverse], via [reverseContour]'s own thin wrapper below. */
    REVERSE_CONTOUR("reverse-contour", "Reverse contour", PaletteTarget.SINGLE_CONTOUR),

    /** [enforceContourDirections] (P2b; widened `public` for this command, `TypeConstraints.kt`). */
    CORRECT_DIRECTION("correct-direction", "Correct direction", PaletteTarget.GLYPH_CONTOURS),

    /** [roundContourCoordinates], below. */
    ROUND_COORDINATES("round-coordinates", "Round coordinates", PaletteTarget.SINGLE_CONTOUR),

    /** [knifeContour] (`ContourKnife.kt`). */
    KNIFE("knife", "Cut / knife", PaletteTarget.SINGLE_CONTOUR),

    /** [closePolylineToContour], below. See its own KDoc for why there is no symmetrical "open" command. */
    CLOSE_CONTOUR("close-contour", "Close contour", PaletteTarget.POINT_LIST),
}

/**
 * "Reverse contour" (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373): a thin wrapper over
 * [Contour.reverse] (P1a), named and placed alongside its seven sibling Palette commands so
 * [PaletteCommand.REVERSE_CONTOUR] has one obvious top-level function to point a UI at, the same
 * way every other command here does — [Contour.reverse] itself is not renamed or duplicated, only
 * given a second, discoverable name at this module's one Palette entry point.
 */
fun reverseContour(contour: Contour): Contour = contour.reverse()

/**
 * "Round coordinates" (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373; CLAUDE.md "integers at rest").
 *
 * **Honestly, this is an identity on [contour]'s own values.** [Point] stores `x`/`y` as Kotlin
 * `Int`, never a float (`Point.kt`'s own KDoc: "integers at rest ... floats only inside
 * algorithms") — so every [ContourPoint] a [Contour] can hold is already rounded to the nearest
 * font unit by construction; there is no sub-integer coordinate left anywhere in this type for a
 * rounding pass to find. This function still exists, and still rebuilds a genuinely new [Contour]
 * from scratch rather than short-circuiting to `return contour`, for two honest reasons rather than
 * a fake one: [PaletteCommand.ROUND_COORDINATES] needs a real, callable, testable entry point so a
 * later UI's command registry never has to special-case "this one command is secretly a no-op";
 * and rebuilding through the constructor re-checks [Contour]'s own CUBIC (on, off, off) triple and
 * closedness invariants explicitly, which is exactly what `PaletteTest`'s own round-trip test
 * confirms survives.
 */
fun roundContourCoordinates(contour: Contour): Contour =
    Contour(contour.points.map { it.copy(point = Point(it.point.x, it.point.y)) }, contour.format)

/**
 * "Close contour" — half of `TYPEWRIGHT_BUILD_BRIEF.md` line 372-373's "close/open contour", the
 * half that actually applies to this module's [Contour] type.
 *
 * **Honestly, "open" does not apply here, and this is not a gap silently papered over.**
 * [Contour]'s own KDoc is explicit: it is *always* a closed, cyclic point sequence — there is no
 * "open `Contour`" representation in this data model at all (`HobbySpline.kt`'s own
 * [hobbySplineOpen] hits exactly this: "`core-geometry` has no `open Contour` type, so the result
 * is the segment list directly"). An "open path" is a Pen tool's own *authoring-time* idea — a
 * drawing in progress that has not yet looped back to its own start — not a property a finished,
 * already-valid [Contour] can carry or toggle. Inventing a fake `isOpen` flag on [Contour] just to
 * make "close" and "open" look symmetric here would mean either silently breaking [Contour]'s own
 * documented invariant or building a whole second, parallel meaning of "open" nothing else in this
 * module recognises — CLAUDE.md's "when unsure, stop and report" rather than guess. See this
 * task's own `docs/OPEN_QUESTIONS.md` entry for the plain statement of this scope cut.
 *
 * What *is* meaningful, and what this function builds: turning an **open polyline** — a plain,
 * not-yet-closed list of anchor points, exactly what a pen tool has placed so far before its last
 * point loops back to its first — into a valid, closed [CurveFormat.CUBIC] [Contour], by connecting
 * [points]' last entry back to its first with one more straight segment. Every edge, including that
 * closing one, is built the same way `CubicFitting.kt`'s own `buildCubicContour` KDoc documents as
 * this format's only way to express a straight run: a cubic with two on-line, degenerate control
 * points ([straightLineCubic]), never a curve fit — fitting a smooth shape through these points is
 * a different, heavier operation ([fitClosedContourToCubics]) this command deliberately does not
 * reach for, so a plain click-to-place polygon closes as exactly the polygon drawn, no more and no
 * less.
 *
 * [points] must be non-empty (`Contour`'s own invariant); a single point closes to a fully
 * degenerate one-point contour (the same "no ink, but structurally valid" answer `Offset.kt`'s own
 * `buildDegenerateContour` already established as this app's convention for a collapsed shape).
 */
fun closePolylineToContour(points: List<Point>): Contour {
    require(points.isNotEmpty()) { "need at least one point to close into a contour" }
    val segments =
        points.indices.map { i ->
            straightLineCubic(points[i].toVec2(), points[(i + 1) % points.size].toVec2())
        }
    return buildCubicContour(segments)
}

/**
 * A straight line from [from] to [to], expressed the only way [CurveFormat.CUBIC] can hold a
 * straight run — a cubic with two on-line, degenerate control points collinear with its own
 * endpoints, at the conventional one-third and two-thirds points along the chord — exactly what
 * `CubicFitting.kt`'s own `buildCubicContour` KDoc documents this format falling back to for a
 * straight side, and what its own least-squares solve already naturally produces for collinear
 * input, never special-cased there either. Shared by [knifeContour]'s closing cut and
 * [closePolylineToContour]'s own closing (and every other) edge, rather than written twice.
 */
internal fun straightLineCubic(
    from: Vec2,
    to: Vec2,
): CurveSegment.Cubic {
    val delta = to - from
    return CurveSegment.Cubic(from, from + delta * (1.0 / 3.0), from + delta * (2.0 / 3.0), to)
}
