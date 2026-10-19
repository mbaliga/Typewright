// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.tokens

/**
 * The close red: the one red in the app, only ever the fill of the deck's left circle in its Close
 * state, with a white ✕ (CLAUDE.md law 8). Themes cannot reassign it, and no theme role may sit
 * near it.
 */
object CloseToken {
    /** #D0342C on a light ground. */
    val RED_ON_LIGHT: Argb = Argb.rgb(0xD0342C)

    /** #FF5A4E on a dark ground. */
    val RED_ON_DARK: Argb = Argb.rgb(0xFF5A4E)

    /** The ✕'s colour. */
    val CROSS: Argb = Argb.rgb(0xFFFFFF)

    fun red(dark: Boolean): Argb = if (dark) RED_ON_DARK else RED_ON_LIGHT
}
