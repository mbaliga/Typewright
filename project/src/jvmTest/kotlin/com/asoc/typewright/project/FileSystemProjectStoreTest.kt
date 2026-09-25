// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemProjectStoreTest {
    private val temp: Path = Files.createTempDirectory("fs-store-test")

    @AfterTest
    fun cleanUp() {
        temp.toFile().deleteRecursively()
    }

    private fun projectDir(name: String = "Hyle Deco"): Path = temp.resolve(name).also { it.createDirectories() }

    /** Every regular file under [root], relative and `/`-separated, with its bytes. */
    private fun tree(root: Path): Map<String, ByteArray> =
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .toList()
                .associate { root.relativize(it).joinToString("/") to Files.readAllBytes(it) }
        }

    private fun seed(root: Path) {
        for ((path, text) in SaveScenario.initial) root.resolve(path).also { it.parent.createDirectories() }.writeText(text)
        for (directory in SaveScenario.initialDirectories) root.resolve(directory).createDirectories()
    }

    @Test
    fun namesAndLocatesTheDirectory() {
        val root = projectDir()
        val store = FileSystemProjectStore(root)
        assertEquals("Hyle Deco", store.displayName)
        assertEquals(ProjectLocation.FileSystem(root.toAbsolutePath().normalize().toString()), store.location)
        assertEquals(Durability.PERSISTENT, store.durability)
    }

    @Test
    fun writesReadsAndListsFilesButNotDirectoriesOrGit(): Unit =
        runBlocking {
            val root = projectDir()
            val store = FileSystemProjectStore(root)
            store.writeAtomic("typewright.json", "{}".encodeToByteArray())
            store.writeAtomic("HyleDeco-Regular.ufo/glyphs/a.glif", "a".encodeToByteArray())
            store.writeAtomic("HyleDeco-Regular.ufo/glyphs/a.glif", "a2".encodeToByteArray())
            store.writeAtomic("scrapbook/pin 1.png", byteArrayOf(1, 2))
            store.ensureDirectory("comparisons")
            root.resolve(".git/objects").createDirectories()
            root.resolve(".git/HEAD").writeText("ref: refs/heads/main\n")

            assertEquals(
                listOf("HyleDeco-Regular.ufo/glyphs/a.glif", "scrapbook/pin 1.png", "typewright.json"),
                store.list(),
            )
            assertEquals("a2", store.read("HyleDeco-Regular.ufo/glyphs/a.glif")!!.decodeToString())
            assertNull(store.read("missing.json"))
            assertNull(store.read("comparisons"), "a directory is not a file")
            assertTrue(root.resolve("comparisons").isDirectory())
            assertTrue(tree(root).keys.none { it.endsWith(".tmp") })
        }

    @Test
    fun aFailedWriteRemovesItsTempFile(): Unit =
        runBlocking {
            val root = projectDir()
            root.resolve("blocked.json/inside").createDirectories()
            val store = FileSystemProjectStore(root)
            assertFailsWith<java.io.IOException> { store.writeAtomic("blocked.json", "x".encodeToByteArray()) }
            assertFalse(root.resolve("blocked.json.tmp").exists())
            assertTrue(root.resolve("blocked.json").isDirectory())
        }

    @Test
    fun refusesPathsOutsideTheProject(): Unit =
        runBlocking {
            val store = FileSystemProjectStore(projectDir())
            for (bad in listOf("../escape.txt", "/etc/passwd", "a\\b", "a\u0000b", "a/../../b", "")) {
                assertFailsWith<IllegalArgumentException>(bad) { store.writeAtomic(bad, ByteArray(0)) }
                assertFailsWith<IllegalArgumentException>(bad) { store.read(bad) }
                assertFailsWith<IllegalArgumentException>(bad) { store.delete(bad) }
            }
            assertFailsWith<IllegalArgumentException> { store.writeAtomic("sneaky.tmp", ByteArray(0)) }
            assertFalse(temp.resolve("escape.txt").exists())
        }

    @Test
    fun deletePrunesEmptyLockDirectoriesOnly(): Unit =
        runBlocking {
            val root = projectDir()
            val store = FileSystemProjectStore(root)
            store.writeAtomic("locks/regular/T_.glif", "t".encodeToByteArray())
            store.writeAtomic("locks/regular/H_.glif", "h".encodeToByteArray())
            store.writeAtomic("scrapbook/pin-1.png", "p".encodeToByteArray())
            store.delete("missing.txt")

            store.delete("scrapbook/pin-1.png")
            assertTrue(root.resolve("scrapbook").isDirectory(), "scrapbook/ stays")
            store.delete("locks/regular/T_.glif")
            assertTrue(root.resolve("locks/regular").isDirectory(), "H_.glif is still there")
            store.delete("locks/regular/H_.glif")
            assertFalse(root.resolve("locks").exists(), "locks/ goes when its last file does")
            assertTrue(root.exists())
        }

    @Test
    fun recoverDiscardsPartialWritesRollsCompletedOnesForwardAndLeavesForeignFiles(): Unit =
        runBlocking {
            val root = projectDir()
            root.resolve("typewright.json").writeText("a old")
            root.resolve("typewright.json.tmp").writeText("a par")
            root.resolve("HyleDeco-Regular.ufo/glyphs").createDirectories()
            root.resolve("HyleDeco-Regular.ufo/glyphs/b.glif.new").writeText("b new")
            root.resolve("scrapbook").createDirectories()
            root.resolve("scrapbook/manifest.json.crswap").writeText("c par")
            root.resolve("README.md").writeText("mine")
            root.resolve(".git").createDirectories()
            root.resolve(".git/index.tmp").writeText("git's")

            val report = FileSystemProjectStore(root).recover()
            assertEquals(
                RecoveryReport(
                    rolledForward = listOf("HyleDeco-Regular.ufo/glyphs/b.glif"),
                    discarded = listOf("scrapbook/manifest.json.crswap", "typewright.json.tmp"),
                ),
                report,
            )
            assertEquals(
                mapOf(
                    "typewright.json" to "a old",
                    "HyleDeco-Regular.ufo/glyphs/b.glif" to "b new",
                    "README.md" to "mine",
                    ".git/index.tmp" to "git's",
                ),
                tree(root).mapValues { it.value.decodeToString() },
            )
            assertTrue(FileSystemProjectStore(root).recover().isEmpty)
        }

    @Test
    fun recoverLeavesUnmodelledUfoDataAndTheUsersFilesAlone(): Unit =
        runBlocking {
            val root = projectDir()
            val foreign =
                mapOf(
                    "HyleDeco-Regular.ufo/data/com.example.tool/cache.tmp" to "another tool's cache",
                    "HyleDeco-Regular.ufo/data/com.example.tool/settings.json" to "another tool's settings",
                    "HyleDeco-Regular.ufo/data/com.example.tool/settings.json.new" to "another tool's pending settings",
                    "HyleDeco-Regular.ufo/images/sketch.png.tmp" to "an image being written",
                    "notes.txt" to "my notes",
                    "notes.txt.new" to "my next draft of notes",
                )
            for ((path, text) in foreign) root.resolve(path).also { it.parent.createDirectories() }.writeText(text)
            assertTrue(FileSystemProjectStore(root).recover().isEmpty)
            assertEquals(foreign, tree(root).mapValues { it.value.decodeToString() })
        }

    @Test
    fun aCrashAtAnyPointOfASaveRecoversToWhollyOldOrWhollyNewFiles(): Unit =
        runBlocking {
            var case = 0
            for (crashBefore in 0..SaveScenario.steps.size) {
                val step = SaveScenario.steps.getOrNull(crashBefore)
                // What the interrupted step left: nothing, half its bytes, or all of them, but
                // always still in X.tmp, because the rename had not happened.
                val leftovers = if (step is SaveScenario.Write) listOf(null, 0.5, 1.0) else listOf(null)
                for (fraction in leftovers) {
                    val root = projectDir("case-${case++}")
                    seed(root)
                    val store = FileSystemProjectStore(root)
                    for (done in SaveScenario.steps.take(crashBefore)) {
                        when (done) {
                            is SaveScenario.Write -> store.writeAtomic(done.path, done.text.encodeToByteArray())
                            is SaveScenario.Delete -> store.delete(done.path)
                        }
                    }
                    if (step is SaveScenario.Write && fraction != null) {
                        val bytes = step.text.encodeToByteArray()
                        val target = root.resolve(step.path)
                        target.parent.createDirectories()
                        AtomicFiles.tempOf(target).writeBytes(bytes.copyOf((bytes.size * fraction).toInt()))
                    }

                    val report = FileSystemProjectStore(root).recover()
                    val description = "crash before step $crashBefore with ${fraction ?: "no"} temp file"
                    assertEquals(crashBefore, SaveScenario.assertConsistent(tree(root), description))
                    assertEquals(if (fraction != null) listOf("${step!!.path}.tmp") else emptyList(), report.discarded, description)
                }
            }
        }

    @Test
    fun aSecondStoreOnTheSameProjectInThisProcessGetsNoLease(): Unit =
        runBlocking {
            val root = projectDir()
            val first = FileSystemProjectStore(root)
            val second = FileSystemProjectStore(root.resolve("../Hyle Deco"))
            val lease = assertNotNull(first.acquireWriteLease())
            assertTrue(root.resolve("build/.session-lock").exists())
            assertNull(second.acquireWriteLease())
            assertNull(first.acquireWriteLease())
            assertTrue(root.resolve("build/.session-lock").exists(), "a refused claim doesn't disturb the holder")

            lease.release()
            lease.release()
            assertTrue(
                root.resolve("build/.session-lock").exists(),
                "releasing unlocks the file but keeps it, so every claimant locks the same file",
            )
            val again = assertNotNull(second.acquireWriteLease())
            again.release()

            val other = assertNotNull(FileSystemProjectStore(projectDir("Other")).acquireWriteLease())
            other.release()
        }

    @Test
    fun anotherProcessHoldingTheLockMakesTheProjectReadOnly(): Unit =
        runBlocking {
            val python = python() ?: return@runBlocking println("SKIPPED: no python to hold a POSIX lock from another process")
            val root = projectDir()
            val lockFile = root.resolve("build/.session-lock").also { it.parent.createDirectories() }
            val script =
                "import fcntl, sys\n" +
                    "f = open(sys.argv[1], 'a')\n" +
                    "fcntl.lockf(f, fcntl.LOCK_EX | fcntl.LOCK_NB)\n" +
                    "print('locked', flush=True)\n" +
                    "sys.stdin.read()\n"
            val holder = ProcessBuilder(python, "-c", script, lockFile.toString()).redirectErrorStream(true).start()
            try {
                assertEquals("locked", holder.inputStream.bufferedReader().readLine())
                assertNull(FileSystemProjectStore(root).acquireWriteLease(), "another process holds the project")
            } finally {
                holder.outputStream.close()
                holder.waitFor(10, TimeUnit.SECONDS)
                holder.destroyForcibly()
            }
            val lease = assertNotNull(FileSystemProjectStore(root).acquireWriteLease(), "the OS freed the lock with the process")
            lease.release()
        }

    @Test
    fun aClaimantThatOpenedTheLockFileBeforeAReleaseCannotLockItAfterAnotherClaim(): Unit =
        runBlocking {
            val python = python() ?: return@runBlocking println("SKIPPED: no python to open the lock file from another process")
            val root = projectDir()
            val lease = assertNotNull(FileSystemProjectStore(root).acquireWriteLease())
            val lockFile = root.resolve("build/.session-lock")
            // Another Typewright opens the lock file while this one still holds it, then is
            // held up before it tries to lock.
            val script =
                "import fcntl, sys\n" +
                    "f = open(sys.argv[1], 'a')\n" +
                    "print('opened', flush=True)\n" +
                    "sys.stdin.readline()\n" +
                    "try:\n" +
                    "    fcntl.lockf(f, fcntl.LOCK_EX | fcntl.LOCK_NB)\n" +
                    "    print('locked', flush=True)\n" +
                    "except OSError:\n" +
                    "    print('busy', flush=True)\n" +
                    "sys.stdin.read()\n"
            val slow = ProcessBuilder(python, "-c", script, lockFile.toString()).redirectErrorStream(true).start()
            try {
                val output = slow.inputStream.bufferedReader()
                assertEquals("opened", output.readLine())
                lease.release()
                val next = assertNotNull(FileSystemProjectStore(root).acquireWriteLease(), "the project is free after the release")
                slow.outputStream.write("go\n".toByteArray())
                slow.outputStream.flush()
                assertEquals("busy", output.readLine(), "the slow claimant locks the same file, so it must find it taken")
                next.release()
            } finally {
                slow.outputStream.close()
                slow.waitFor(10, TimeUnit.SECONDS)
                slow.destroyForcibly()
            }
        }

    @Test
    fun aSymlinkedProjectDirectoryIsListedRecoveredAndWritten(): Unit =
        runBlocking {
            val real = projectDir("real")
            real.resolve("typewright.json").writeText("{}")
            real.resolve("HyleDeco-Regular.ufo/glyphs").createDirectories()
            real.resolve("HyleDeco-Regular.ufo/glyphs/A_.glif").writeText("a")
            real.resolve("typewright.json.tmp").writeText("partial")
            val link = Files.createSymbolicLink(temp.resolve("Hyle Deco"), real)
            val store = FileSystemProjectStore(link)

            assertEquals("Hyle Deco", store.displayName)
            assertEquals(ProjectLocation.FileSystem(link.toString()), store.location)
            assertEquals(listOf("HyleDeco-Regular.ufo/glyphs/A_.glif", "typewright.json", "typewright.json.tmp"), store.list())
            assertEquals(RecoveryReport(rolledForward = emptyList(), discarded = listOf("typewright.json.tmp")), store.recover())
            assertFalse(real.resolve("typewright.json.tmp").exists())
            store.writeAtomic("scrapbook/manifest.json", "{}".encodeToByteArray())
            assertEquals("{}", real.resolve("scrapbook/manifest.json").readText())
            store.delete("HyleDeco-Regular.ufo/glyphs/A_.glif")
            assertEquals(listOf("scrapbook/manifest.json", "typewright.json"), store.list())
            assertNotNull(store.acquireWriteLease()).release()
        }

    @Test
    fun linksInsideTheProjectAreNeverFollowed(): Unit =
        runBlocking {
            val outside = temp.resolve("outside").also { it.createDirectories() }
            outside.resolve("secret.txt").writeText("outside the project")
            outside.resolve("elsewhere.json").writeText("outside too")
            val root = projectDir()
            Files.createSymbolicLink(root.resolve("comparisons"), outside)
            Files.createSymbolicLink(root.resolve("typewright.json"), outside.resolve("elsewhere.json"))
            root.resolve("lessons").createDirectories()
            val store = FileSystemProjectStore(root)

            assertEquals(emptyList(), store.list(), "links are left out of the listing")
            assertNull(store.read("comparisons/secret.txt"), "a file through a linked directory is not in the project")
            assertNull(store.read("typewright.json"), "a linked file is not in the project")
            for (write in listOf<suspend () -> Unit>(
                { store.writeAtomic("comparisons/new.txt", "x".encodeToByteArray()) },
                { store.writeAtomic("typewright.json", "{}".encodeToByteArray()) },
                { store.delete("comparisons/secret.txt") },
                { store.delete("typewright.json") },
                { store.ensureDirectory("comparisons/deeper") },
            )) {
                assertFailsWith<FileSystemException> { write() }
            }
            assertEquals(setOf("secret.txt", "elsewhere.json"), outside.toFile().list()!!.toSet())
            assertEquals("outside the project", outside.resolve("secret.txt").readText())
            assertEquals("outside too", outside.resolve("elsewhere.json").readText())

            // A stray temp file that is a link fails the write instead of truncating its target.
            Files.createSymbolicLink(root.resolve("lessons/workbook-latn.json.tmp"), outside.resolve("secret.txt"))
            assertFailsWith<java.io.IOException> { store.writeAtomic("lessons/workbook-latn.json", "{}".encodeToByteArray()) }
            assertEquals("outside the project", outside.resolve("secret.txt").readText())
            assertFalse(Files.exists(root.resolve("lessons/workbook-latn.json.tmp"), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            store.writeAtomic("lessons/workbook-latn.json", "{}".encodeToByteArray())
            assertEquals(listOf("lessons/workbook-latn.json"), store.list())
        }

    @Test
    fun appConfigFilesAreWrittenAtomically(): Unit =
        runBlocking {
            val dir = temp.resolve("config/typewright")
            val config = FileAppConfigStore(dir)
            assertNull(config.read("recent-projects.json"))
            config.writeAtomic("recent-projects.json", "{\"projects\": []}\n".encodeToByteArray())
            config.writeAtomic("recent-projects.json", "{\"projects\": [1]}\n".encodeToByteArray())
            assertEquals("{\"projects\": [1]}\n", config.read("recent-projects.json")!!.decodeToString())
            assertEquals(listOf("recent-projects.json"), dir.toFile().list()!!.toList())
            assertEquals("{\"projects\": [1]}\n", dir.resolve("recent-projects.json").readText())
            assertFailsWith<IllegalArgumentException> { config.read("../outside.json") }
            assertFailsWith<IllegalArgumentException> { config.writeAtomic("/abs.json", ByteArray(0)) }
        }

    private fun python(): String? = listOf("python3", "python").firstOrNull { commandExists(it) }

    private fun commandExists(command: String): Boolean =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .any { File(it, command).canExecute() }
}
