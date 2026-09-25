// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.tokens

import com.asoc.typewright.ui.sheet.CubicBezierEasing
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every duration transcribed from UI_SPEC.md §5 / brief §6; see those for the source of truth. */
class MotionTokensTest {
    @Test
    fun durationsMatchUiSpec() {
        assertEquals(600, MotionTokens.ROOM_PAN_MS)
        assertEquals(350, MotionTokens.PUCK_PIN_MS)
        assertEquals(110, MotionTokens.PUCK_ICON_ROLL_MS)
        assertEquals(150, MotionTokens.RADIAL_FADE_MS)
        assertEquals(1400, MotionTokens.COUNT_COLLAPSE_MS)
        assertEquals(350, MotionTokens.SCREEN_CHANGE_FADE_MS)
        assertEquals(300, MotionTokens.LINEAGES_STRESS_NEEDLE_MS)
    }

    @Test
    fun sharedEasingIsTheRoomPanCurve() {
        assertEquals(CubicBezierEasing.ROOM_PAN, MotionTokens.EASING)
    }
}
