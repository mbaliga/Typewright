// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible

/**
 * A [LayerOneChecker] that never blocks its caller: every call runs [delegate] on a background thread, and cancelling
 * the caller interrupts that thread, which stops the tool the delegate started and everything it started (the
 * delegates here wait on child processes, which a coroutine cannot otherwise cancel). A screen can therefore call
 * layer one from its own thread, show progress while it runs, and cancel it.
 */
internal class BackgroundLayerOneChecker(
    internal val delegate: LayerOneChecker,
) : LayerOneChecker {
    override val name: String get() = delegate.name

    override suspend fun availability(): LayerOneAvailability = onBackgroundThread { delegate.availability() }

    override suspend fun runtime(): LayerOneRuntime? = onBackgroundThread { delegate.runtime() }

    override suspend fun check(input: LayerOneInput): LayerOneReport = onBackgroundThread { delegate.check(input) }

    private suspend fun <T> onBackgroundThread(call: suspend () -> T): T = runInterruptible(Dispatchers.IO) { runBlocking { call() } }
}
