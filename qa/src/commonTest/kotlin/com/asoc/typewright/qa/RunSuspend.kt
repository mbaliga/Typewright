// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

/**
 * Runs a suspend block to its end and returns its result. On the web every actual in this module completes without
 * suspending, so the block is simply run; on the JVM the desktop checker moves its blocking work to a background thread and
 * does suspend, so the block is run to completion on the calling thread.
 */
internal expect fun <T> runSuspend(block: suspend () -> T): T
