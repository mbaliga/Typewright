package dev.aarso.typewright.ui.puck

import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.abs
import kotlin.math.max

/**
 * Every numeric constant `docs/ARCHITECTURE_REVIEW.md` §4.2's recommendation pins for the puck,
 * as the *minimum enforced by this module*, in dp/ms/degrees. [PuckGestureConfig] applies
 * [MIN_HOLD_TIMEOUT_MS]/[MIN_SLOP_DP] as a floor over a platform value, per the review's own
 * "`max(380, longPressTimeoutMillis)`" / "`max(6.dp, touchSlop)`"; the rest are fixed by UI_SPEC.
 */
object PuckGestureDefaults {
    /** UI_SPEC §3 "Gestures": "pointerdown starts a 380 ms hold timer"; review §4.2 recommendation 2 floors this against the platform's own long-press timeout (Android default 400 ms, JVM desktop 500 ms -- both already above 380, so the floor only ever binds on a platform with a *shorter* setting). */
    const val MIN_HOLD_TIMEOUT_MS: Long = 380

    /** UI_SPEC §3: "Movement > 6 dp before the timer cancels it"; floored the same way against the platform's own touch slop (Android 8 dp, JVM desktop 18 dp). */
    const val MIN_SLOP_DP: Double = 6.0

    /** UI_SPEC §3: "every 34 dp of travel cycles one tool". */
    const val SWIPE_STEP_DP: Double = 34.0

    /** UI_SPEC §3 Radial dial: "Release within r < 24 dp cancels". */
    const val RADIAL_HUB_RADIUS_DP: Double = 24.0

    /** UI_SPEC §3 Radial dial: "8 sectors ... detents every 45°". */
    const val RADIAL_DETENT_DEGREES: Double = 45.0

    /**
     * Deliberate fix, not a port: `docs/ARCHITECTURE_REVIEW.md` §4.2 measured the explorer's own
     * grip hit area at 18x12 px, against UI_SPEC §6's own stated 44 dp target floor ("Targets >=
     * 44 dp for finger"), and recommendation 2 calls for "A 44 dp grip" outright. This is the
     * enforced *hit-test* minimum; P4b's actual puck drawing keeps the smaller *visual* bar
     * UI_SPEC §3 specifies ("18x3 dp bar") -- only the tappable region grows.
     */
    const val GRIP_MIN_HIT_DP: Double = 44.0

    /** UI_SPEC §3 Puck: "68 dp circle". */
    const val PUCK_DIAMETER_DP: Double = 68.0
}

/**
 * The puck gesture machine's tunables. [platformLongPressTimeoutMs] and [platformTouchSlopDp] are
 * the platform's own defaults (Android's `ViewConfiguration.getLongPressTimeout()`/
 * `getScaledTouchSlop()`, JVM desktop's Compose defaults, or the accessibility-adjusted value if
 * the platform exposes one); [holdTimeoutMs] and [slopDp] are what the state machine actually
 * uses, each floored at the app's own minimum per `docs/ARCHITECTURE_REVIEW.md` §4.2
 * recommendation 2, so a platform whose own default happens to be *shorter* than this app's floor
 * never fires the hold before 380 ms or before 6 dp of travel.
 */
class PuckGestureConfig(
    platformLongPressTimeoutMs: Long = PuckGestureDefaults.MIN_HOLD_TIMEOUT_MS,
    platformTouchSlopDp: Double = PuckGestureDefaults.MIN_SLOP_DP,
    val swipeStepDp: Double = PuckGestureDefaults.SWIPE_STEP_DP,
    val radialHubRadiusDp: Double = PuckGestureDefaults.RADIAL_HUB_RADIUS_DP,
    val radialDetentDegrees: Double = PuckGestureDefaults.RADIAL_DETENT_DEGREES,
    val gripHitSizeDp: Double = PuckGestureDefaults.GRIP_MIN_HIT_DP,
) {
    val holdTimeoutMs: Long = max(PuckGestureDefaults.MIN_HOLD_TIMEOUT_MS, platformLongPressTimeoutMs)
    val slopDp: Double = max(PuckGestureDefaults.MIN_SLOP_DP, platformTouchSlopDp)
}

/**
 * Where the puck currently is, for the gesture machine's own geometry (the hub for the radial's
 * radius/angle math, and the grip's hit-test) -- not a rendering model; P4b's puck composable
 * owns the actual drawing. [center] is the puck's own screen-dp centre, e.g. the 18 dp-from-edge,
 * 40%-down-the-canvas default pin position UI_SPEC §3 describes, or wherever it last re-pinned to.
 */
data class PuckGeometry(
    val center: Vec2,
    val diameterDp: Double = PuckGestureDefaults.PUCK_DIAMETER_DP,
) {
    private val radiusDp: Double get() = diameterDp / 2.0

    /** UI_SPEC §3: "Grip: 18x3 dp bar at the top inside the circle" -- a few dp in from the puck's own top edge. */
    val gripVisualCenter: Vec2 get() = Vec2(center.x, center.y - radiusDp + GRIP_INSET_FROM_EDGE_DP)

    /** Whether [position] falls within a [gripHitSizeDp]-square hit region centred on [gripVisualCenter]. */
    fun isWithinGrip(
        position: Vec2,
        gripHitSizeDp: Double,
    ): Boolean {
        val half = gripHitSizeDp / 2.0
        val grip = gripVisualCenter
        return abs(position.x - grip.x) <= half && abs(position.y - grip.y) <= half
    }

    private companion object {
        const val GRIP_INSET_FROM_EDGE_DP = 9.0
    }
}
