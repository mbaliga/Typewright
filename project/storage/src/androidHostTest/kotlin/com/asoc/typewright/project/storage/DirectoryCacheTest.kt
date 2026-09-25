// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DirectoryCache] holds no Android SDK call of its own, so it is exercised here, off-device, as
 * a plain host test; [SafDocumentOps] itself needs a real `ContentResolver` and is owner-verified
 * only (CLAUDE.md law 4).
 */
class DirectoryCacheTest {
    private val documents = listOf(ChildDoc("T_.glif", "doc-1", isDirectory = false), ChildDoc("regular", "doc-2", isDirectory = true))

    @Test
    fun missesUntilPopulated() {
        val cache = DirectoryCache()
        assertNull(cache.get("locks"))
        cache.put("locks", documents)
        assertEquals(documents, cache.get("locks"))
    }

    @Test
    fun invalidateForgetsOnlyThatDirectory() {
        val cache = DirectoryCache()
        cache.put("", documents)
        cache.put("locks", documents)
        cache.invalidate("locks")
        assertNull(cache.get("locks"))
        assertEquals(documents, cache.get(""))
    }

    @Test
    fun cachedPathsReflectsWhatIsStillCached() {
        val cache = DirectoryCache()
        cache.put("", documents)
        cache.put("locks", documents)
        cache.invalidate("")
        assertEquals(setOf("locks"), cache.cachedPaths())
    }
}
