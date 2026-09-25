// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Runs a suspend block that completes without suspending, as every actual in this module does. */
internal fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "The block suspended; use kotlinx-coroutines-test for real suspension." }
        .getOrThrow()
}
