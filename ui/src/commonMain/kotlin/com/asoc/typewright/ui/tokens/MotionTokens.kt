// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.tokens

import com.asoc.typewright.ui.sheet.CubicBezierEasing

/**
 * UI_SPEC.md §5 "Motion" and brief §6, transcribed as named durations (ms) plus the one shared
 * easing curve ([com.asoc.typewright.ui.sheet.CubicBezierEasing.ROOM_PAN]). [RoomFlight][com.
 * asoc.typewright.ui.sheet.RoomFlight] already models the room pan's own interpolation; this
 * object is the flat list of every other named motion value for P4b to drive its `tween`/
 * `Animatable` calls from, so no duration is hand-copied twice. "prefers-reduced-motion: all of
 * the above become instant" (UI_SPEC §5) is P4b's job (reading the platform's reduced-motion
 * setting is not pure, Compose-free logic), not modelled here.
 */
object MotionTokens {
    /** "Room pan: 600 ms cubic-bezier(.2,.8,.2,1)"; also "Map in/out: 600 ms, same curve". */
    const val ROOM_PAN_MS: Int = 600

    /** "Puck pin: 350 ms". */
    const val PUCK_PIN_MS: Int = 350

    /** "icon roll 110 ms" (UI_SPEC §3 Puck: "rolling ... for 110 ms on tool change"). */
    const val PUCK_ICON_ROLL_MS: Int = 110

    /** "radial fade 150 ms" (UI_SPEC §5); brief §6 names the same value "radial open 150 ms". */
    const val RADIAL_FADE_MS: Int = 150

    /** "Count collapse: 1.4 s, cubic ease-out on a log scale". */
    const val COUNT_COLLAPSE_MS: Int = 1400

    /** "Screen change: 350 ms fade". */
    const val SCREEN_CHANGE_FADE_MS: Int = 350

    /** "stress needle 300 ms ease" (Lineages, UI_SPEC §5). "Lineages crossfade: linear with scrubber position" has no fixed duration -- it tracks the scrubber directly, not a timer. */
    const val LINEAGES_STRESS_NEEDLE_MS: Int = 300

    /** The one eased curve UI_SPEC §5 names for every duration above except the two explicitly "linear"/scrubber-driven ones. */
    val EASING = CubicBezierEasing.ROOM_PAN
}
