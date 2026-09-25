// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.InMemoryProjectFiles
import com.asoc.typewright.project.ProjectZip
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FsaProjectStore] against OPFS (docs/PROJECT_MODEL.md §5's note: "FSA's browser test runs
 * against OPFS ... in headless Chrome through Karma"), a real, writable directory with no picker
 * and no permission prompt. Each test opens its own randomly named subdirectory of OPFS's root
 * ([FsaProjectStore.openOpfs]) so runs never see each other's leftovers.
 */
class FsaProjectStoreBrowserTest {
    private suspend fun freshStore(): FsaProjectStore = FsaProjectStore.openOpfs("test-${Random.nextInt(Int.MAX_VALUE)}")

    @Test
    fun writesReadsListsAndDeletes() =
        runTest {
            val store = freshStore()
            assertEquals(emptyList(), store.list())

            store.writeAtomic("typewright.json", "{}\n".encodeToByteArray())
            store.writeAtomic("HyleDeco-Regular.ufo/glyphs/T_.glif", "<glyph/>\n".encodeToByteArray())

            assertEquals(listOf("HyleDeco-Regular.ufo/glyphs/T_.glif", "typewright.json"), store.list())
            assertEquals("{}\n", store.read("typewright.json")!!.decodeToString())
            assertNull(store.read("does-not-exist.json"))

            store.delete("typewright.json")
            assertEquals(listOf("HyleDeco-Regular.ufo/glyphs/T_.glif"), store.list())
            assertNull(store.read("typewright.json"))
        }

    @Test
    fun deleteInsideLocksPrunesEmptyAncestorsOnly() =
        runTest {
            val store = freshStore()
            store.writeAtomic("locks/regular/T_.glif", "<glyph/>\n".encodeToByteArray())
            store.writeAtomic("locks/regular/o.glif", "<glyph/>\n".encodeToByteArray())

            store.delete("locks/regular/T_.glif")
            // A sibling file remains, so locks/regular is not pruned.
            assertEquals(listOf("locks/regular/o.glif"), store.list())

            store.delete("locks/regular/o.glif")
            // Now empty: both locks/regular and locks are pruned away.
            assertEquals(emptyList(), store.list())
        }

    @Test
    fun ensureDirectoryThenListStaysEmpty() =
        runTest {
            val store = freshStore()
            store.ensureDirectory("scrapbook")
            assertEquals(emptyList(), store.list())
        }

    @Test
    fun recoverSweepsAStaleCrswapAndLeavesRealFilesAlone() =
        runTest {
            val store = freshStore()
            store.writeAtomic("typewright.json", "{}\n".encodeToByteArray())
            // A real write through FsaProjectStore never leaves one of these; this stands in for
            // Chromium's own internal swap file surviving a crash mid-write, bypassing
            // writeAtomic (which refuses a *.crswap path outright) to plant it directly.
            writeStrayFile(store, "typewright.json.crswap", "partial")

            val report = store.recover()

            assertEquals(listOf("typewright.json.crswap"), report.discarded)
            assertTrue(report.rolledForward.isEmpty())
            assertEquals(listOf("typewright.json"), store.list())
            assertEquals("{}\n", store.read("typewright.json")!!.decodeToString())
        }

    @Test
    fun zipRoundTripsThroughTheRealCompressionStreams() =
        runTest {
            val store = freshStore()
            store.writeAtomic("typewright.json", "{\n  \"format\": \"typewright-project\"\n}\n".encodeToByteArray())
            store.writeAtomic(
                "HyleDeco-Regular.ufo/glyphs/T_.glif",
                "<point x=\"42\" y=\"0\" type=\"line\"/>\n".repeat(50).encodeToByteArray(),
            )
            val snapshot = InMemoryProjectFiles(store.displayName, store.list().associateWith { store.read(it)!! })

            val zipped = ProjectZip.write(snapshot, root = "Hyle Deco", deflate = FsaCompression::deflateRaw)
            val unzipped = ProjectZip.read(zipped, inflate = FsaCompression::inflateRaw)

            for (path in snapshot.listFiles()) {
                assertEquals(snapshot.readBytes(path).decodeToString(), unzipped.getValue(path).decodeToString())
            }
        }

    // Bypasses FsaProjectStore's own writeAtomic (which validates project paths and never writes
    // a .crswap of its own) to plant a raw file directly through the FSA primitives, in the same
    // OPFS subdirectory the store itself opened, the way a crashed browser -- not this store --
    // would have left one.
    private suspend fun writeStrayFile(
        store: FsaProjectStore,
        path: String,
        content: String,
    ) {
        val root = fsaOpfsRoot().await<JsAny>()
        val projectHandle = fsaGetOrCreateChildDirectory(root, store.displayName.toJsString()).await<JsAny>()
        fsaWriteFile(projectHandle, path.toJsString(), Base64.encode(content.encodeToByteArray()).toJsString()).await<JsAny?>()
    }
}
