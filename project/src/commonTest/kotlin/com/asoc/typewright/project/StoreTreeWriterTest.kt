// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreTreeWriterTest {
    private fun bytes(text: String) = text.encodeToByteArray()

    private val tree =
        mapOf(
            "OFL.txt" to bytes("licence"),
            "fonts/ttf/F-Regular.ttf" to byteArrayOf(0, 1, 2, -1),
            "sources/F-Regular.ufo/fontinfo.plist" to bytes("info"),
            "sources/F-Regular.ufo/glyphs/A_.glif" to bytes("A"),
        )

    /** A store that fails its [failAt]th write (counting from 1), as a full disk or a revoked grant would. */
    private class FailingStore(
        private val inner: ProjectStore,
        private val failAt: Int,
    ) : ProjectStore by inner {
        var writes = 0

        override suspend fun writeAtomic(
            path: String,
            bytes: ByteArray,
        ) {
            writes++
            if (writes == failAt) throw IllegalStateException("the disk is full")
            inner.writeAtomic(path, bytes)
        }
    }

    private suspend fun snapshot(store: ProjectStore): Map<String, List<Byte>> = store.list().associateWith { store.read(it)!!.toList() }

    @Test
    fun writesEveryFileOfTheTree() =
        runTest {
            val store = InMemoryProjectStore("repo")
            val report = StoreTreeWriter(store).write(tree, listOf("sources/F-Regular.ufo"))
            assertEquals(tree.keys.sorted(), store.list())
            for ((path, bytes) in tree) assertContentEquals(bytes, store.read(path), path)
            assertEquals(TreeWriteReport(files = 4, replaced = 0, bytes = tree.values.sumOf { it.size.toLong() }), report)
        }

    @Test
    fun filesTheTreeDoesNotNameAreLeftAlone() =
        runTest {
            val store = InMemoryProjectStore("repo", mapOf("README.md" to bytes("mine"), "CONTRIBUTORS.txt" to bytes("them")))
            StoreTreeWriter(store).write(tree)
            assertEquals("mine", store.read("README.md")?.decodeToString())
            assertEquals("them", store.read("CONTRIBUTORS.txt")?.decodeToString())
        }

    @Test
    fun anOwnedDirectoryIsReplacedAsAWholeSoNoStaleFileLingers() =
        runTest {
            val store =
                InMemoryProjectStore(
                    "repo",
                    mapOf(
                        "sources/F-Regular.ufo/glyphs/Deleted.glif" to bytes("an old glyph"),
                        "sources/F-Regular.ufo/fontinfo.plist" to bytes("old info"),
                        "sources/Other.ufo/fontinfo.plist" to bytes("another font"),
                    ),
                )
            val report = StoreTreeWriter(store).write(tree, listOf("sources/F-Regular.ufo"))
            assertNull(store.read("sources/F-Regular.ufo/glyphs/Deleted.glif"), "the glyph the new tree no longer has must go")
            assertEquals("info", store.read("sources/F-Regular.ufo/fontinfo.plist")?.decodeToString())
            assertEquals("another font", store.read("sources/Other.ufo/fontinfo.plist")?.decodeToString(), "a sibling UFO is not ours")
            assertEquals(1, report.replaced)
        }

    @Test
    fun aFailurePartWayLeavesTheStoreAsItWas() =
        runTest {
            val inner =
                InMemoryProjectStore(
                    "repo",
                    mapOf(
                        "OFL.txt" to bytes("the old licence"),
                        "README.md" to bytes("mine"),
                        "sources/F-Regular.ufo/old.glif" to bytes("old"),
                    ),
                )
            val before = snapshot(inner)
            val failing = FailingStore(inner, failAt = 3)
            val error = assertFailsWith<TreeWriteException> { StoreTreeWriter(failing).write(tree, listOf("sources/F-Regular.ufo")) }
            assertTrue("Nothing was changed" in error.message.orEmpty(), error.message)
            assertTrue("the disk is full" in error.message.orEmpty(), error.message)
            assertEquals(before, snapshot(inner), "the files written before the failure must be put back or removed")
        }

    @Test
    fun theFailureAfterTheLastWriteStillLeavesTheStoreAsItWas() =
        runTest {
            val inner = InMemoryProjectStore("repo", mapOf("sources/F-Regular.ufo/old.glif" to bytes("old")))
            val before = snapshot(inner)
            assertFailsWith<TreeWriteException> {
                StoreTreeWriter(
                    FailingStore(inner, failAt = 4),
                ).write(tree, listOf("sources/F-Regular.ufo"))
            }
            assertEquals(before, snapshot(inner))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellingPartWayLeavesTheStoreAsItWas() =
        runTest {
            val inner = InMemoryProjectStore("repo", mapOf("OFL.txt" to bytes("the old licence")))
            val before = snapshot(inner)
            val reachedSecondWrite = CompletableDeferred<Unit>()
            val proceed = CompletableDeferred<Unit>()
            var writes = 0
            val pausing =
                object : ProjectStore by inner {
                    override suspend fun writeAtomic(
                        path: String,
                        bytes: ByteArray,
                    ) {
                        inner.writeAtomic(path, bytes)
                        if (++writes == 2) {
                            reachedSecondWrite.complete(Unit)
                            proceed.await()
                        }
                    }
                }
            var cancelled = false
            val job =
                launch {
                    try {
                        StoreTreeWriter(pausing).write(tree)
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    }
                }
            reachedSecondWrite.await()
            job.cancel()
            job.join()
            assertTrue(cancelled, "the writer must report the cancellation, not swallow it")
            assertEquals(before, snapshot(inner))
        }

    @Test
    fun aPathOutsideTheStoreIsRefusedBeforeAnythingIsWritten() =
        runTest {
            val store = InMemoryProjectStore("repo")
            for (bad in listOf("../escape.txt", "/etc/passwd", "a/../../b", "x\\y", "a//b", "tmp.tmp")) {
                assertFailsWith<TreeWriteException>(bad) { StoreTreeWriter(store).write(mapOf("ok.txt" to bytes("x"), bad to bytes("y"))) }
            }
            assertEquals(emptyList(), store.list(), "nothing may be written when any path is refused")
        }

    @Test
    fun aFileThatIsAlsoAFolderIsRefused() =
        runTest {
            val store = InMemoryProjectStore("repo")
            assertFailsWith<TreeWriteException> { StoreTreeWriter(store).write(mapOf("a" to bytes("x"), "a/b" to bytes("y"))) }
            assertFailsWith<TreeWriteException> { StoreTreeWriter(store).write(mapOf("a" to bytes("x")), listOf("a")) }
            assertEquals(emptyList(), store.list())
        }

    @Test
    fun theDescriptionIsTheStoresName() {
        assertEquals("repo", StoreTreeWriter(InMemoryProjectStore("repo")).description)
        assertEquals("~/Fonts/x", StoreTreeWriter(InMemoryProjectStore("repo"), "~/Fonts/x").description)
    }

    @Test
    fun ancestorsListTheDirectoriesAboveAPath() {
        assertEquals(listOf("a", "a/b"), TreeLayout.ancestorsOf("a/b/c.txt"))
        assertEquals(emptyList(), TreeLayout.ancestorsOf("c.txt"))
    }
}
