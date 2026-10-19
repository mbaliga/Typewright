// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The platform's back (Android's back button or gesture, the browser's back) as one handler the
 * screen on top can claim. A screen that has a deck claims it and does exactly what the deck's left
 * circle does; the host asks [dispatch] first and only goes on to its own default when nothing
 * claimed it.
 */
class SystemBack {
    private var handler: (() -> Unit)? = null

    internal fun claim(onBack: () -> Unit): () -> Unit {
        handler = onBack
        return { if (handler === onBack) handler = null }
    }

    /** Runs the claimed handler and returns true, or returns false when nothing claimed back. */
    fun dispatch(): Boolean {
        val claimed = handler ?: return false
        claimed()
        return true
    }
}

val LocalSystemBack = staticCompositionLocalOf<SystemBack?> { null }

/** Claims the system back for as long as this is in the composition and [enabled]. */
@Composable
fun SystemBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    val registry = LocalSystemBack.current
    val latest = rememberUpdatedState(onBack)
    DisposableEffect(registry, enabled) {
        val release = if (enabled) registry?.claim { latest.value() } else null
        onDispose { release?.invoke() }
    }
}
