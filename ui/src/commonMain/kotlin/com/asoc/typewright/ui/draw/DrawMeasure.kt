// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The distance and angle between two places on the sheet, in font units, y up. It is a reading and nothing else:
 * measuring never edits the glyph and so never makes an undo step. The angle is counter-clockwise from the
 * positive x axis, in degrees, from -180 to 180 (0 is along the baseline, 90 straight up).
 */
data class Measurement(
    val a: Vec2,
    val b: Vec2,
) {
    val dx: Double get() = b.x - a.x
    val dy: Double get() = b.y - a.y
    val distance: Double get() = sqrt(dx * dx + dy * dy)

    /** The angle of the line from [a] to [b]; 0 for two places on one spot, which have no direction. */
    val angleDegrees: Double get() = if (distance == 0.0) 0.0 else atan2(dy, dx) * DEGREES_PER_RADIAN

    /** "Δx 120 · Δy −40 · 126.5 units · −18.4°", the line shown beside the measured segment. */
    val text: String
        get() = "Δx ${signed(dx)} · Δy ${signed(dy)} · ${tenths(distance)} units · ${signedTenths(angleDegrees)}°"

    companion object {
        private const val DEGREES_PER_RADIAN = 180.0 / PI

        /** [value] to one decimal place with no trailing ".0" ("126.5", "120"), without a locale or `String.format`. */
        fun tenths(value: Double): String {
            val scaled = floor(abs(value) * 10.0 + 0.5).toLong()
            val whole = scaled / 10
            val fraction = scaled % 10
            val sign = if (value < 0.0 && scaled != 0L) "−" else ""
            return if (fraction == 0L) "$sign$whole" else "$sign$whole.$fraction"
        }

        private fun signedTenths(value: Double): String = tenths(value)

        private fun signed(value: Double): String = tenths(value)
    }
}

/**
 * Where the measure tool is: nothing placed, one end placed, or both. A third tap starts again from that tap.
 * Pure, so what a tap does is a number a test can check.
 */
data class MeasureState(
    val first: Vec2? = null,
    val second: Vec2? = null,
) {
    /** The finished reading, or null until both ends are down. */
    val measurement: Measurement? get() = if (first != null && second != null) Measurement(first, second) else null

    /** The state after a tap at [place]: the first end, then the second, then a new first. */
    fun tapped(place: Vec2): MeasureState = if (first == null || second != null) MeasureState(first = place) else copy(second = place)

    companion object {
        /** A free tap lands on a whole font unit, as every other point the app places does. */
        fun wholeUnit(place: Vec2): Vec2 {
            val p = Point(floor(place.x + 0.5).toInt(), floor(place.y + 0.5).toInt())
            return Vec2(p.x.toDouble(), p.y.toDouble())
        }
    }
}
