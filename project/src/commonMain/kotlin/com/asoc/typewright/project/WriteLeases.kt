// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Write leases held inside this process, by key. Stores whose platform has no cross-process lock
 * (Android runs one app instance) use it so two sessions in the same process can't both write
 * one project.
 */
internal object ProcessWriteLeases {
    private val mutex = Mutex()
    private val held = mutableSetOf<Any>()

    /** A lease on [key], or null while another holder has it. */
    suspend fun tryAcquire(key: Any): WriteLease? =
        mutex.withLock {
            if (held.add(key)) OnceLease { mutex.withLock { held.remove(key) } } else null
        }
}

/** A [WriteLease] whose release action runs at most once, however often [release] is called. */
internal class OnceLease(
    private val action: suspend () -> Unit,
) : WriteLease {
    private val mutex = Mutex()
    private var released = false

    override suspend fun release() {
        mutex.withLock {
            if (released) return
            released = true
            action()
        }
    }
}
