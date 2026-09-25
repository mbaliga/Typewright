// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryProjectStoreTest {
    @Test
    fun isTabOnlyAndLocatedInMemory() {
        val store = InMemoryProjectStore("Hyle Deco")
        assertEquals(Durability.TAB_ONLY, store.durability)
        assertEquals(ProjectLocation.InMemory("Hyle Deco"), store.location)
        assertEquals("Hyle Deco", store.displayName)
    }

    @Test
    fun writesReadsListsAndCopiesBytes() =
        runTest {
            val store = InMemoryProjectStore("p")
            val bytes = "abc".encodeToByteArray()
            store.writeAtomic("b/c.json", bytes)
            store.writeAtomic("a.json", "a".encodeToByteArray())
            bytes[0] = 'z'.code.toByte()

            assertEquals(listOf("a.json", "b/c.json"), store.list())
            val read = assertNotNull(store.read("b/c.json"))
            assertContentEquals("abc".encodeToByteArray(), read)
            read[0] = 'y'.code.toByte()
            assertContentEquals("abc".encodeToByteArray(), store.read("b/c.json"))
            assertNull(store.read("missing"))
            assertEquals(listOf("b"), store.directories())
        }

    @Test
    fun runsTheSaveScenarioAndKeepsTheForeignReadme() =
        runTest {
            val store = InMemoryProjectStore("p", SaveScenario.initialBytes)
            SaveScenario.initialDirectories.forEach { store.ensureDirectory(it) }
            SaveScenario.run(store)
            val files = store.snapshot().files
            assertEquals(SaveScenario.steps.size, SaveScenario.assertConsistent(files, "in memory"))
            assertTrue(store.directories().containsAll(SaveScenario.initialDirectories))
        }

    @Test
    fun deletePrunesEmptyLockDirectoriesOnly() =
        runTest {
            val store = InMemoryProjectStore("p")
            store.ensureDirectory("scrapbook")
            store.writeAtomic("locks/regular/T_.glif", ByteArray(1))
            store.writeAtomic("scrapbook/pin-1.png", ByteArray(1))
            store.delete("scrapbook/pin-1.png")
            store.delete("locks/regular/T_.glif")
            store.delete("locks/regular/T_.glif")
            assertEquals(listOf("scrapbook"), store.directories())
            assertEquals(emptyList(), store.list())
        }

    @Test
    fun recoverSweepsSeededTempFiles() =
        runTest {
            val store =
                InMemoryProjectStore(
                    "p",
                    mapOf(
                        "typewright.json" to "old".encodeToByteArray(),
                        "typewright.json.tmp" to "partial".encodeToByteArray(),
                        "lessons/workbook-latn.json.new" to "complete".encodeToByteArray(),
                        "notes.txt.new" to "someone else's".encodeToByteArray(),
                    ),
                )
            val report = store.recover()
            assertEquals(
                RecoveryReport(rolledForward = listOf("lessons/workbook-latn.json"), discarded = listOf("typewright.json.tmp")),
                report,
            )
            assertEquals(listOf("lessons/workbook-latn.json", "notes.txt.new", "typewright.json"), store.list())
            assertEquals("complete", store.read("lessons/workbook-latn.json")!!.decodeToString())
        }

    @Test
    fun grantsOneWriteLeaseAtATime() =
        runTest {
            val store = InMemoryProjectStore("p")
            val lease = assertNotNull(store.acquireWriteLease())
            assertNull(store.acquireWriteLease())
            lease.release()
            lease.release()
            assertNotNull(store.acquireWriteLease())
        }

    @Test
    fun refusesInvalidPathsAndFileDirectoryClashes() =
        runTest {
            val store = InMemoryProjectStore("p")
            assertFailsWith<IllegalArgumentException> { store.writeAtomic("../x", ByteArray(0)) }
            assertFailsWith<IllegalArgumentException> { store.writeAtomic("x.new", ByteArray(0)) }
            assertFailsWith<IllegalArgumentException> { InMemoryProjectStore("p", mapOf("/abs" to ByteArray(0))) }
            store.ensureDirectory("d")
            assertFailsWith<IllegalArgumentException> { store.writeAtomic("d", ByteArray(0)) }
            store.writeAtomic("f", ByteArray(0))
            assertFailsWith<IllegalArgumentException> { store.ensureDirectory("f") }
            assertFailsWith<IllegalArgumentException> { store.writeAtomic("f/inside.json", ByteArray(0)) }
            assertFailsWith<IllegalArgumentException> { store.ensureDirectory("f/sub") }
        }
}
