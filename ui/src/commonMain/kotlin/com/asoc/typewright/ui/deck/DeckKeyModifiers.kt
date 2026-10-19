// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Whether something that uses the arrow keys and typed characters itself (a text field, a list, a
 * radio group) has focus. The deck's global keys stand down while it does: a deck that stole the
 * arrows from a text field would be a bug.
 */
@Stable
class DeckKeyFocus {
    var owned: Boolean by mutableStateOf(false)
}

val LocalDeckKeyFocus = staticCompositionLocalOf { DeckKeyFocus() }

/** Marks the element as one that owns the arrow keys and typed characters while it has focus. */
fun Modifier.ownsDeckKeys(): Modifier =
    composed {
        val holder = LocalDeckKeyFocus.current
        onFocusChanged { holder.owned = it.isFocused }
    }

/**
 * Turns key presses that reach here, unhandled by whatever has focus, into [DeckKeyCommand]s and
 * hands them to [onCommand], which returns true when it used one.
 */
@Composable
fun Modifier.deckKeys(onCommand: (DeckKeyCommand) -> Boolean): Modifier {
    val holder = LocalDeckKeyFocus.current
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val name =
            when (event.key) {
                Key.DirectionLeft -> {
                    "ArrowLeft"
                }

                Key.DirectionRight -> {
                    "ArrowRight"
                }

                Key.Escape -> {
                    "Escape"
                }

                else -> {
                    event.utf16CodePoint
                        .takeIf { it in 0x20..0x7E }
                        ?.toChar()
                        ?.toString() ?: return@onKeyEvent false
                }
            }
        val modified = event.isCtrlPressed || event.isMetaPressed || event.isAltPressed
        DeckKeys.interpret(name, holder.owned, modified)?.let(onCommand) ?: false
    }
}
