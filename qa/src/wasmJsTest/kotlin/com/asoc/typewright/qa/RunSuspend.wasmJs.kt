// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** The web's checker answers without suspending, so the block is started and its result read straight back. */
internal actual fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "The block suspended; the web checker is expected to answer without suspending." }
        .getOrThrow()
}
