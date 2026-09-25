// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgbTest {
    @Test
    fun rgbIsOpaque() {
        val color = Argb.rgb(0xEFE9DC)
        assertEquals(0xFF, color.alpha)
        assertEquals(0xEF, color.red)
        assertEquals(0xE9, color.green)
        assertEquals(0xDC, color.blue)
    }

    @Test
    fun rgbIgnoresBitsAboveTheLow24() {
        val color = Argb.rgb(0x12EFE9DC)
        assertEquals(0xFF, color.alpha)
        assertEquals(0xEF, color.red)
    }

    @Test
    fun withAlphaKeepsRgbAndReplacesAlpha() {
        val ink = Argb.rgb(0x17150F)
        val line = ink.withAlpha(0.20)
        assertEquals(0xFF, ink.alpha)
        assertEquals(51, line.alpha) // round(0.20 * 255) = 51
        assertEquals(ink.red, line.red)
        assertEquals(ink.green, line.green)
        assertEquals(ink.blue, line.blue)
    }

    @Test
    fun withAlphaAtTheExtremes() {
        val color = Argb.rgb(0xABCDEF)
        assertEquals(0, color.withAlpha(0.0).alpha)
        assertEquals(255, color.withAlpha(1.0).alpha)
    }

    @Test
    fun withAlphaRejectsOutOfRangeFractions() {
        val color = Argb.rgb(0x000000)
        assertFailsWith<IllegalArgumentException> { color.withAlpha(-0.1) }
        assertFailsWith<IllegalArgumentException> { color.withAlpha(1.1) }
    }

    @Test
    fun transparentIsAllZeroBits() {
        assertEquals(0, Argb.TRANSPARENT.alpha)
        assertEquals(0, Argb.TRANSPARENT.red)
        assertEquals(0, Argb.TRANSPARENT.green)
        assertEquals(0, Argb.TRANSPARENT.blue)
    }
}
