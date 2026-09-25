// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs

/**
 * [CurveSegment.Cubic.isEffectivelyStraight]'s default tolerance, in font units: how far a
 * control point may sit off the segment's own straight chord and still count as "no meaningful
 * curvature". Matches the tolerance `core-geometry`'s own fitter test suite already uses to
 * recognise an "on-line degenerate" cubic (`CubicFittingTest.squareFitsToFourCubicSegmentsWithNoInteriorSplits`),
 * because it is measuring exactly the same thing: a straight run the P2 fitter represents as a
 * cubic with collinear control points, since [Contour]'s CUBIC format has no shorter "line" point
 * kind to fall back to (see `CubicFitting.kt`'s `buildCubicContour` KDoc).
 */
const val DEFAULT_STRAIGHTNESS_TOLERANCE_UNITS: Double = 1.5

private const val STRAIGHTNESS_LENGTH_EPSILON = 1e-9

/**
 * Whether this cubic segment has no meaningful curvature: both control points sit within
 * [toleranceUnits] of the straight chord from [CurveSegment.Cubic.start] to
 * [CurveSegment.Cubic.end] (perpendicular distance, via the standard
 * `|chord x (control - start)| / |chord|` point-to-line measure). A zero-length chord (both
 * endpoints coincide) has no direction to measure a deviation against and reads as straight —
 * there being nothing to call "curved" about it either.
 *
 * This is `core-geometry`'s one shared straightness predicate: P2b's type-constraints stage
 * (`TypeConstraints.kt`) uses it to tell a plain polygon vertex (both neighbouring segments
 * straight — CLAUDE.md's "T stem foot" case) apart from the peak of a genuinely round contour
 * (at least one curved neighbour, brief §7's overshoot-preservation rule), and P2b's construction
 * classifier (`ConstructionClassifier.kt`) uses it to tell POLYGONAL and ROUNDED_RECTANGLE (long
 * straight runs meeting short curved corners) apart from ELLIPTICAL/SUPERELLIPTICAL
 * (continuously-varying curvature, brief §8.5).
 */
fun CurveSegment.Cubic.isEffectivelyStraight(toleranceUnits: Double = DEFAULT_STRAIGHTNESS_TOLERANCE_UNITS): Boolean {
    val chord = end - start
    val chordLength = chord.length()
    if (chordLength <= STRAIGHTNESS_LENGTH_EPSILON) return true
    val control1Deviation = abs(chord.cross(control1 - start)) / chordLength
    val control2Deviation = abs(chord.cross(control2 - start)) / chordLength
    return control1Deviation <= toleranceUnits && control2Deviation <= toleranceUnits
}
