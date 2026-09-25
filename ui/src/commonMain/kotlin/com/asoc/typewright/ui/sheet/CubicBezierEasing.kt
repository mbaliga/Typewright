// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

/**
 * The CSS-standard `cubic-bezier(x1, y1, x2, y2)` timing function, matching the explorer's own
 * `.world`/`.bloom` CSS transition and UI_SPEC §5 / brief §6's "600 ms cubic-bezier(.2,.8,.2,1)"
 * room pan. `docs/ARCHITECTURE_REVIEW.md` §4.1 translates that to Compose as `tween(600, easing =
 * CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))`; this class is the plain-math curve P4b's own
 * `androidx.compose.animation.core.CubicBezierEasing`/`Animatable` wiring evaluates against, kept
 * here as ordinary arithmetic so the curve itself is unit-testable with no Compose runtime.
 *
 * The bezier's x-axis is elapsed-time fraction (`0..1`) and its y-axis is output progress
 * (`0..1`), exactly like a CSS `transition-timing-function`: [transform] solves `x(u) = t` for the
 * bezier parameter `u` by bisection -- the curve is monotonic in `x` for every control point this
 * app uses, since [x1] and [x2] both lie in `[0, 1]` -- and returns `y(u)`.
 */
data class CubicBezierEasing(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
) {
    /** The eased progress (`0..1`) at elapsed-time fraction [t] (clamped to `0..1`). */
    fun transform(t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        if (clamped == 0.0 || clamped == 1.0) return clamped
        val u = solveForU(clamped)
        return bezierComponent(u, y1, y2)
    }

    private fun solveForU(x: Double): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(BISECTION_ITERATIONS) {
            val mid = (lo + hi) / 2.0
            if (bezierComponent(mid, x1, x2) < x) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    /** One cubic-bezier axis at parameter [u], for control points [c1]/[c2] (endpoints 0 and 1). */
    private fun bezierComponent(
        u: Double,
        c1: Double,
        c2: Double,
    ): Double {
        val v = 1.0 - u
        return 3 * v * v * u * c1 + 3 * v * u * u * c2 + u * u * u
    }

    companion object {
        private const val BISECTION_ITERATIONS = 30

        /** UI_SPEC §5 / brief §6: the room pan and the map in/out transition. */
        val ROOM_PAN = CubicBezierEasing(0.2, 0.8, 0.2, 1.0)
    }
}
