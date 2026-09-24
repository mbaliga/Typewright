package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Contrast ratio and stress angle (brief 8.4), both read off one measurement: [oStrokeProfile]
 * casts a diameter through `o`'s ink-bounds centre at every angle from 0 to just under 180
 * degrees (a line and its 180-degrees-rotated twin are the same line, so that range already
 * covers every distinct diameter) and records the average ink-interval length the probe crosses.
 * For a ring shape that average is, to first order, the stroke's radial thickness on the two
 * sides the diameter passes through -- exact for a true ellipse, and a reasonable approximation
 * for any letterform whose stroke wall is close to perpendicular to the radius there (every `o`
 * this package has been checked against). "Angle 0" is a *vertical* probe line, which crosses the
 * ring near 12 and 6 o'clock -- matching brief 8.4's "0 = vertical stress" exactly, since vertical
 * stress means the letter is thickest at top and bottom.
 */
internal data class StrokeProbeSample(
    val angleDegreesFromVertical: Double,
    val width: Double,
)

/** [o]'s stroke-width profile around the ring, sampled every [angleStepDegrees] from 0 (a vertical probe) to just under 180. Empty if `o` has no ink bounds (a contourless glyph). */
internal fun oStrokeProfile(
    o: Glyph,
    angleStepDegrees: Double = 7.5,
): List<StrokeProbeSample> {
    val bounds = o.inkBounds() ?: return emptyList()
    val center = Vec2(bounds.centerX, bounds.centerY)
    val samples = mutableListOf<StrokeProbeSample>()
    var angle = 0.0
    while (angle < 180.0) {
        val rad = angle * PI / 180.0
        // angle=0 -> direction (0,1), a vertical probe line, through the ring's top and bottom.
        val direction = Vec2(sin(rad), cos(rad))
        val intervals = inkIntervals(o.lineCrossings(center, direction))
        if (intervals.isNotEmpty()) {
            samples += StrokeProbeSample(angle, intervals.sumOf { it.length } / intervals.size)
        }
        angle += angleStepDegrees
    }
    return samples
}

/** The thickest/thinnest ratio of [oStrokeProfile]'s widths (>= 1.0; `null` if fewer than two usable samples, or the thinnest sample is non-positive). */
internal fun contrastRatio(o: Glyph): Double? {
    val widths = oStrokeProfile(o).map { it.width }.filter { it > 0.0 }
    if (widths.size < 2) return null
    val thinnest = widths.min()
    if (thinnest <= 0.0) return null
    return widths.max() / thinnest
}

/** The angle, from vertical, of [oStrokeProfile]'s thickest diameter, folded into `(-90, 90]` (a diameter at `angle` and at `angle - 180` are the same line, so this is just a change of representative). `null` if the profile is empty. */
internal fun stressAngleDegrees(o: Glyph): Double? {
    val thickest = oStrokeProfile(o).maxByOrNull { it.width } ?: return null
    var a = thickest.angleDegreesFromVertical
    if (a > 90.0) a -= 180.0
    return a
}
