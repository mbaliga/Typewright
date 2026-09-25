// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

/**
 * Pure Kotlin geometry for Typewright, in font units (1000 UPM default), y up, integers at rest
 * (CLAUDE.md conventions). This is the module's one explanation of its vocabulary; later modules
 * (`core-font`, `qa:corpus`, the P2 fitter) link back here instead of re-explaining it.
 *
 * ### On-curve, off-curve, implied on-curve
 *
 * A [Contour] is a closed, cyclic list of [ContourPoint]s, each either **on-curve** (the curve
 * actually passes through it) or **off-curve** (a control point that pulls the curve toward it
 * without the curve touching it). What a run of off-curve points means depends on
 * [CurveFormat]:
 *
 * - **[CurveFormat.QUADRATIC]** (TrueType `glyf`): a single off-curve point between two on-curve
 *   points is an ordinary quadratic control point.
 *   Two or more *consecutive* off-curve points are TrueType's shorthand for a chain of quadratic
 *   segments that share **implied on-curve points**: the OpenType spec has a rasterizer treat the
 *   midpoint of every cyclically-adjacent off-curve pair as if it were a real on-curve point. A
 *   contour that is entirely off-curve (some compact circle encodings) is just the case where the
 *   whole contour is one such run. This module's node-economy counting functions
 *   ([onCurveEquivalentCount]) implement exactly this rule, and exactly matches
 *   `data/scripts/build_node_economy_corpus.py`'s `_count_simple_contours` (P0c, 2026-09-24) —
 *   see that function's own KDoc for the hand-traced Poppins-Regular `o` example this was
 *   verified against, reproduced as a unit test here.
 * - **[CurveFormat.CUBIC]** (PostScript/UFO `glif`): every on-curve point is followed by exactly
 *   two off-curve control points before the next on-curve point. Those two control points are
 *   never adjacent to another segment's control points in a way that implies anything: a cubic
 *   segment's off-curve count is just its two explicit control points, full stop. There is no
 *   cubic equivalent of "implied on-curve".
 *
 * Because of that difference, "on-curve count" always means **on-curve equivalents**: explicit
 * on-curve points, plus TrueType's implied ones where the format has them. "Off-curve count" is
 * always the raw, explicit off-curve point count — never adjusted — in both formats.
 *
 * ### Why one [Contour] type for both formats
 *
 * `core-geometry` is the one pure module both the quadratic TrueType world (`core-font`'s sfnt
 * reader, this app's own node-economy measurements) and the cubic UFO world (the P2 curve fitter,
 * every glyph this app draws or edits) have to share (docs/ARCHITECTURE_REVIEW.md section 7,
 * "What P1 and P2 need first"). A contour is, underneath either convention, nothing more than a
 * cyclic list of points each tagged on- or off-curve — a cubic contour is simply the special case
 * where that list always reads as repeating (on, off, off) triples, with no run ever longer than
 * two off-curve points and no implied points to compute. Rather than modelling quadratic and
 * cubic contours as two unrelated types (which would force every later algorithm that only cares
 * about "the points and their tags" — counting, direction, reversal — to be written and tested
 * twice), [Contour] carries one flat, tagged, cyclic point list plus a [CurveFormat] tag that
 * only the few operations that truly differ by format (counting's implied-point rule; how a flat
 * point list decodes into [CurveSegment]s; how reversal keeps a cubic contour's "starts on-curve"
 * shape) need to branch on. Segment-level work ([Contour.segments], [Contour.signedArea],
 * extrema) is expressed over the decoded [CurveSegment]s, which *are* format-specific
 * (`Line`/`Quadratic`/`Cubic`), so nothing downstream has to keep re-deriving curve shape from
 * raw point tags.
 *
 * ### Integers at rest, floats inside algorithms
 *
 * [Point] is the integer, font-unit coordinate that lives in a [Contour] — what is actually
 * stored on disk in a `glyf` table or a `.glif` file. [Vec2] is a double-precision 2D vector used
 * only where geometry demands it: a TrueType implied on-curve point is a true midpoint and is not
 * generally an integer; a Bezier extremum or an area integral is real-valued by nature. No
 * function in this module returns a [Vec2] as something meant to be written back as a glyph's
 * coordinate — that rounding decision belongs to whichever later module (the P2 fitter, a curve
 * cleanup pass) actually commits a new point to a [Contour].
 *
 * [NAME] just names the module (kept for parity with every other module's placeholder object); the
 * documentation above is this file's real job.
 */
object CoreGeometryModule {
    const val NAME: String = "core-geometry"
}
