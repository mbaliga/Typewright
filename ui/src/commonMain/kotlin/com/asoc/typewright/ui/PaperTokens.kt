// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.ui.graphics.Color
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor

/** The paper texture's canvas and ink (UI_SPEC.md §2), read from the texture rather than written out again. */
object PaperTokens {
    val Canvas: Color get() = CanvasTextures.PAPER.canvas.toColor()
    val Ink: Color get() = CanvasTextures.PAPER.ink.toColor()
}
