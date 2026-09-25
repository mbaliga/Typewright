// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.campaign

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Runs a suspend block that completes without suspending -- every [WorkbookGates.task11Test]
 * call in this module's own tests does, since the test doubles/platform checkers involved never
 * actually suspend. Mirrors `qa`'s own internal `RunSuspend.kt` (same pattern, needed again here
 * since that one is `internal` to a different module).
 */
internal fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "The block suspended; use kotlinx-coroutines-test for real suspension." }
        .getOrThrow()
}
