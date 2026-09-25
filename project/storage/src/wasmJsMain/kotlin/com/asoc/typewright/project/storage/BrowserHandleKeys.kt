// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlin.random.Random

// A fresh, opaque key for IndexedDbHandleRegistry: unique enough for one browser's own store,
// never parsed back into anything meaningful.
private var browserHandleKeyCounter = 0

internal fun newBrowserHandleKey(): String = "wh-${Random.nextInt(Int.MAX_VALUE).toString(16)}-${browserHandleKeyCounter++}"
