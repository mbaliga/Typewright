// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.coroutines.runBlocking

internal actual fun <T> runSuspend(block: suspend () -> T): T = runBlocking { block() }
