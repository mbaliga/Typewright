package dev.aarso.typewright.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {
    @Test
    fun formatsPositiveValues() {
        assertEquals("1.50", 1.5.toFixedString(2))
        assertEquals("0.00", 0.0.toFixedString(2))
        assertEquals("42.00", 42.0.toFixedString(2))
        assertEquals("3.14", 3.14159.toFixedString(2))
    }

    @Test
    fun formatsNegativeValues() {
        assertEquals("-1.50", (-1.5).toFixedString(2))
        assertEquals("-0.10", (-0.1).toFixedString(2))
    }

    @Test
    fun negativeThatRoundsToZeroDropsTheSign() {
        assertEquals("0.00", (-0.001).toFixedString(2))
    }

    @Test
    fun zeroDecimalsDropsThePoint() {
        assertEquals("308", 308.4.toFixedString(0))
        assertEquals("309", 308.6.toFixedString(0))
    }

    @Test
    fun exactTiesRoundToEven() {
        // kotlin.math.round ties towards the even integer, not away from zero -- documented here
        // via a concrete example rather than only in prose, since it is easy to assume otherwise.
        assertEquals("308", 308.5.toFixedString(0))
        assertEquals("310", 309.5.toFixedString(0))
    }

    @Test
    fun rejectsNegativeDecimals() {
        try {
            1.0.toFixedString(-1)
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
