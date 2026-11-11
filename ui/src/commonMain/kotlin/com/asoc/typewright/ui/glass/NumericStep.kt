// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

/**
 * Stepping a number typed into a numeric field with the arrow keys (INTERACTION_V1 D6): up adds one,
 * down takes one off, and Shift makes the step ten. Only a plain whole number steps; an `=` expression
 * has no obvious "next" and is left as it was typed, so a stray arrow key never rewrites a sum.
 */
object NumericStep {
    /** The step Shift makes. */
    const val BIG_STEP: Int = 10

    /**
     * [text] stepped by [direction] (+1 for up, -1 for down), by [BIG_STEP] when [big], or null when [text] is not a whole number
     * (an expression, an empty field, a fraction), in which case the field keeps what it has.
     */
    fun stepped(
        text: String,
        direction: Int,
        big: Boolean,
    ): String? {
        require(direction == 1 || direction == -1) { "a step goes up (1) or down (-1), was $direction" }
        val value = text.trim().toIntOrNull() ?: return null
        return (value + direction * (if (big) BIG_STEP else 1)).toString()
    }
}
