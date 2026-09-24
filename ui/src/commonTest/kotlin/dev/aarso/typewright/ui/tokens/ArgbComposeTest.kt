package dev.aarso.typewright.ui.tokens

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [Argb.toColor]'s own correctness check: every one of UI_SPEC's canvas/ink/meaning colours round-
 * trips to the exact same 0-255 components a [androidx.compose.ui.graphics.Color] reports back,
 * confirming the component-wise constructor is the right one (see that function's own KDoc for
 * why the alternative, more "obvious" `Color(value.toULong())` conversion is wrong).
 */
class ArgbComposeTest {
    @Test
    fun toColorRoundTripsEveryComponentExactly() {
        for (argb in listOf(
            Argb.rgb(0xEFE9DC),
            Argb.rgb(0x17150F),
            Argb.rgb(0x000000),
            Argb.rgb(0xFFFFFF),
            Argb.rgb(0x5F4BE0).withAlpha(0.42),
        )) {
            val color = argb.toColor()
            assertEquals(argb.red, (color.red * 255f).roundToIntExact(), "red for $argb")
            assertEquals(argb.green, (color.green * 255f).roundToIntExact(), "green for $argb")
            assertEquals(argb.blue, (color.blue * 255f).roundToIntExact(), "blue for $argb")
            assertEquals(argb.alpha, (color.alpha * 255f).roundToIntExact(), "alpha for $argb")
        }
    }

    private fun Float.roundToIntExact(): Int = kotlin.math.round(this).toInt()
}
