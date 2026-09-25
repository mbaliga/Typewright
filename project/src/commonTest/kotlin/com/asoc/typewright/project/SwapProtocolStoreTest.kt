// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwapProtocolStoreTest {
    private var locations = 0

    private fun store(ops: DocumentOps): SwapProtocolStore =
        SwapProtocolStore(ops, ProjectLocation.SafTree("content://test/tree", "doc-${locations++}-${this.hashCode()}"), "Hyle Deco")

    private fun CrashingDocumentOps.text(): Map<String, String> = files.mapValues { it.value.decodeToString() }

    @Test
    fun writesReadsAndListsSortedFiles() =
        runTest {
            val ops = CrashingDocumentOps()
            val store = store(ops)
            store.writeAtomic("typewright.json", "{}".encodeToByteArray())
            store.writeAtomic("HyleDeco-Regular.ufo/glyphs/a.glif", "a".encodeToByteArray())
            store.writeAtomic("HyleDeco-Regular.ufo/glyphs/a.glif", "a2".encodeToByteArray())

            assertEquals(listOf("HyleDeco-Regular.ufo/glyphs/a.glif", "typewright.json"), store.list())
            assertEquals("a2", store.read("HyleDeco-Regular.ufo/glyphs/a.glif")!!.decodeToString())
            assertNull(store.read("missing.json"))
            assertTrue("HyleDeco-Regular.ufo/glyphs" in ops.directories, "parents are created")
            assertEquals(Durability.PERSISTENT, store.durability)
        }

    @Test
    fun aWriteUsesTheSwapProtocolAndLeavesNoTempFiles() =
        runTest {
            val ops = CrashingDocumentOps(mapOf("x.json" to "old".encodeToByteArray()))
            store(ops).writeAtomic("x.json", "new".encodeToByteArray())
            assertEquals(mapOf("x.json" to "new"), ops.text())
            // ensureDirectory is skipped at the root: writeTruncate, rename, delete, rename.
            assertEquals(4, ops.operations)
        }

    @Test
    fun deleteIsANoOpWhenAbsentAndPrunesOnlyEmptyLockDirectories() =
        runTest {
            val ops = CrashingDocumentOps()
            val store = store(ops)
            store.writeAtomic("locks/regular/T_.glif", "t".encodeToByteArray())
            store.writeAtomic("scrapbook/pin-1.png", "p".encodeToByteArray())
            store.delete("nothing/here.txt")

            store.delete("scrapbook/pin-1.png")
            assertTrue("scrapbook" in ops.directories, "scrapbook/ stays even when empty")

            store.delete("locks/regular/T_.glif")
            assertFalse("locks/regular" in ops.directories)
            assertFalse("locks" in ops.directories)
            assertEquals(emptyList(), store.list())
        }

    @Test
    fun deleteKeepsALockDirectoryThatStillHasFiles() =
        runTest {
            val ops = CrashingDocumentOps()
            val store = store(ops)
            store.writeAtomic("locks/regular/T_.glif", "t".encodeToByteArray())
            store.writeAtomic("locks/regular/H_.glif", "h".encodeToByteArray())
            store.delete("locks/regular/T_.glif")
            assertTrue("locks/regular" in ops.directories)
            assertEquals(listOf("locks/regular/H_.glif"), store.list())
        }

    @Test
    fun refusesPathsThatLeaveTheProjectOrLookLikeTempFiles() =
        runTest {
            val store = store(CrashingDocumentOps())
            for (bad in listOf("../x", "/etc/passwd", "a\\b", "a\u0000b", "", "a//b", "./a", "a/")) {
                assertFailsWith<IllegalArgumentException>(bad) { store.writeAtomic(bad, ByteArray(0)) }
                assertFailsWith<IllegalArgumentException>(bad) { store.read(bad) }
                assertFailsWith<IllegalArgumentException>(bad) { store.delete(bad) }
            }
            for (reserved in listOf("a.tmp", "b/c.new", "d.crswap")) {
                assertFailsWith<IllegalArgumentException>(reserved) { store.writeAtomic(reserved, ByteArray(0)) }
            }
        }

    @Test
    fun recoverDiscardsPartialWritesAndRollsCompletedOnesForward() =
        runTest {
            val ops =
                CrashingDocumentOps(
                    mapOf(
                        "typewright.json" to "a old".encodeToByteArray(),
                        "typewright.json.tmp" to "a par".encodeToByteArray(),
                        "lessons/workbook-latn.json.new" to "b new".encodeToByteArray(),
                        "HyleDeco-Regular.ufo/glyphs/c.glif" to "c old".encodeToByteArray(),
                        "HyleDeco-Regular.ufo/glyphs/c.glif.new" to "c new".encodeToByteArray(),
                        "scrapbook/manifest.json.crswap" to "d par".encodeToByteArray(),
                        SaveScenario.FOREIGN_README to "mine".encodeToByteArray(),
                        ".git/objects/x.tmp" to "git's own".encodeToByteArray(),
                    ),
                    setOf("lessons", "scrapbook", "HyleDeco-Regular.ufo", "HyleDeco-Regular.ufo/glyphs", ".git", ".git/objects"),
                )
            val report = store(ops).recover()

            assertEquals(listOf("HyleDeco-Regular.ufo/glyphs/c.glif", "lessons/workbook-latn.json"), report.rolledForward)
            assertEquals(listOf("scrapbook/manifest.json.crswap", "typewright.json.tmp"), report.discarded)
            assertEquals(
                mapOf(
                    "typewright.json" to "a old",
                    "lessons/workbook-latn.json" to "b new",
                    "HyleDeco-Regular.ufo/glyphs/c.glif" to "c new",
                    SaveScenario.FOREIGN_README to "mine",
                    ".git/objects/x.tmp" to "git's own",
                ),
                ops.text(),
            )
            assertTrue(store(ops).recover().isEmpty, "recovery is idempotent")
        }

    @Test
    fun recoverLeavesOtherToolsTempFilesAlone() =
        runTest {
            val foreign =
                mapOf(
                    "README.md.new" to "the user's next draft",
                    "README.md" to "the user's notes",
                    "notes.txt.tmp" to "an editor's swap",
                    "HyleDeco-Regular.ufo/data/com.example.tool/cache.tmp" to "another tool's cache",
                    "HyleDeco-Regular.ufo/data/com.example.tool/settings.json.new" to "another tool's pending settings",
                    "HyleDeco-Regular.ufo/images/sketch.png.tmp" to "an image being written",
                    "HyleDeco-Regular.ufo/glyphs.background/a.glif.new" to "another layer",
                )
            val ops =
                CrashingDocumentOps(
                    foreign.mapValues { it.value.encodeToByteArray() },
                    setOf(
                        "HyleDeco-Regular.ufo",
                        "HyleDeco-Regular.ufo/data",
                        "HyleDeco-Regular.ufo/data/com.example.tool",
                        "HyleDeco-Regular.ufo/images",
                        "HyleDeco-Regular.ufo/glyphs.background",
                    ),
                )
            assertTrue(store(ops).recover().isEmpty)
            assertEquals(foreign, ops.text())
        }

    @Test
    fun listSkipsTheGitDirectory() =
        runTest {
            val ops = CrashingDocumentOps(mapOf(".git/HEAD" to ByteArray(1), "typewright.json" to ByteArray(1)), setOf(".git"))
            assertEquals(listOf("typewright.json"), store(ops).list())
        }

    @Test
    fun aCrashAtEveryOperationLeavesEveryFileWhollyOldOrWhollyNew() =
        runTest {
            val clean = CrashingDocumentOps(SaveScenario.initialBytes, SaveScenario.initialDirectories)
            SaveScenario.run(store(clean))
            val total = clean.operations
            assertEquals(SaveScenario.steps.size, SaveScenario.assertConsistent(clean.files, "no crash"))

            val appliedCounts = mutableSetOf<Int>()
            for (crashAt in 1..total) {
                val ops = CrashingDocumentOps(SaveScenario.initialBytes, SaveScenario.initialDirectories, crashAt)
                assertFailsWith<SimulatedCrash> { SaveScenario.run(store(ops)) }
                assertTrue(ops.crashed)

                val restarted = ops.restart()
                val report = store(restarted).recover()
                appliedCounts += SaveScenario.assertConsistent(restarted.files, "crash at operation $crashAt of $total")
                assertTrue(report.discarded.all { it.endsWith(ProjectPath.TMP_SUFFIX) })
                assertTrue(store(restarted).recover().isEmpty, "crash at $crashAt: a second recovery finds nothing")
            }
            // Some crash point falls between every pair of steps, and one after the last rename.
            assertEquals((0..SaveScenario.steps.size).toSet(), appliedCounts)
        }

    @Test
    fun aCrashDuringRecoveryIsRecoveredOnTheNextOpen() =
        runTest {
            val clean = CrashingDocumentOps(SaveScenario.initialBytes, SaveScenario.initialDirectories)
            SaveScenario.run(store(clean))
            for (crashAt in 1..clean.operations) {
                val crashed = CrashingDocumentOps(SaveScenario.initialBytes, SaveScenario.initialDirectories, crashAt)
                assertFailsWith<SimulatedCrash> { SaveScenario.run(store(crashed)) }
                val probe = crashed.restart()
                store(probe).recover()
                for (recoveryCrashAt in 1..probe.operations) {
                    val recovering = crashed.restart(crashAt = recoveryCrashAt)
                    val outcome = runCatching { store(recovering).recover() }
                    if (outcome.isSuccess) continue
                    assertTrue(outcome.exceptionOrNull() is SimulatedCrash)
                    val reopened = recovering.restart()
                    store(reopened).recover()
                    SaveScenario.assertConsistent(reopened.files, "save crash at $crashAt, recovery crash at $recoveryCrashAt")
                }
            }
        }

    @Test
    fun aWriteThatFailsBeforeItsNewFileExistsRemovesItsTempFile() =
        runTest {
            val ops = FlakyDocumentOps(CrashingDocumentOps(mapOf("x.json" to "old".encodeToByteArray())), failOn = "rename")
            val store = store(ops)
            assertFailsWith<IllegalStateException> { store.writeAtomic("x.json", "new".encodeToByteArray()) }
            assertEquals(mapOf("x.json" to "old"), ops.delegate.text())
        }

    @Test
    fun aWriteWhoseLastStepsFailIsFinishedByTheNextOperation() =
        runTest {
            val inner = CrashingDocumentOps(mapOf("x.json" to "old".encodeToByteArray()))
            val ops = FlakyDocumentOps(inner, failOn = "delete")
            val store = store(ops)
            assertFailsWith<IllegalStateException> { store.writeAtomic("x.json", "new".encodeToByteArray()) }
            assertEquals(mapOf("x.json" to "old", "x.json.new" to "new"), inner.text())

            // The next operation finishes the write first, so it reads the new bytes.
            assertEquals("new", store.read("x.json")!!.decodeToString())
            assertEquals(listOf("x.json"), store.list())
            assertEquals(mapOf("x.json" to "new"), inner.text())
            store.writeAtomic("x.json", "newer".encodeToByteArray())
            assertEquals(mapOf("x.json" to "newer"), inner.text())
        }

    @Test
    fun oneWriteLeasePerLocationInTheProcess() =
        runTest {
            val location = ProjectLocation.SafTree("content://test/tree", "lease-${this.hashCode()}")
            val first = SwapProtocolStore(CrashingDocumentOps(), location, "A")
            val second = SwapProtocolStore(CrashingDocumentOps(), location, "A")
            val lease = assertNotNull(first.acquireWriteLease())
            assertNull(second.acquireWriteLease())
            lease.release()
            lease.release()
            val again = assertNotNull(second.acquireWriteLease())
            again.release()
        }

    @Test
    fun writtenBytesAreCopied() =
        runTest {
            val ops = CrashingDocumentOps()
            val store = store(ops)
            val bytes = "abc".encodeToByteArray()
            store.writeAtomic("a.txt", bytes)
            bytes[0] = 'z'.code.toByte()
            assertContentEquals("abc".encodeToByteArray(), store.read("a.txt"))
        }
}

/** Wraps [delegate] and fails the first [failOn] operation once with an ordinary exception, as a flaky provider would. */
class FlakyDocumentOps(
    val delegate: CrashingDocumentOps,
    private val failOn: String,
) : DocumentOps by delegate {
    private var failed = false

    private fun maybeFail(operation: String) {
        if (!failed && operation == failOn) {
            failed = true
            throw IllegalStateException("provider refused $operation")
        }
    }

    override suspend fun rename(
        path: String,
        newName: String,
    ) {
        maybeFail("rename")
        delegate.rename(path, newName)
    }

    override suspend fun delete(path: String) {
        maybeFail("delete")
        delegate.delete(path)
    }
}
