// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderTreeWriterTest {
    private val tree =
        mapOf(
            "OFL.txt" to "licence".encodeToByteArray(),
            "fonts/ttf/F-Regular.ttf" to byteArrayOf(0, 1, 2, -1),
            "sources/F-Regular.ufo/fontinfo.plist" to "info".encodeToByteArray(),
            "sources/F-Regular.ufo/glyphs/A_.glif" to "A".encodeToByteArray(),
        )
    private val owned = listOf("sources/F-Regular.ufo")

    private fun parent(): File = Files.createTempDirectory("typewright-tree-").toFile()

    private fun write(
        parent: File,
        name: String = "repo",
        files: Map<String, ByteArray> = tree,
    ) = runBlocking { FolderTreeWriter(File(parent, name).toPath()).write(files, owned) }

    private fun hashes(root: File): Map<String, String> =
        root
            .walkTopDown()
            .filter { it.isFile }
            .associate {
                it.relativeTo(root).path to
                    MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
            }

    private fun dirs(root: File): List<String> =
        root
            .walkTopDown()
            .filter { it.isDirectory }
            .map { it.relativeTo(root).path }
            .sorted()
            .toList()

    @Test
    fun writesANewFolderInOneRenameAndLeavesNothingNextToIt() {
        val parent = parent()
        val report = write(parent)
        val repo = File(parent, "repo")
        assertEquals(
            tree.keys.sorted(),
            repo
                .walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(repo).path }
                .sorted()
                .toList(),
        )
        for ((path, bytes) in tree) assertContentEquals(bytes, File(repo, path).readBytes(), path)
        assertEquals(listOf("repo"), parent.list()!!.toList(), "no working folder may be left beside the result")
        assertEquals(TreeWriteReport(files = 4, replaced = 0, bytes = tree.values.sumOf { it.size.toLong() }), report)
    }

    @Test
    fun theNewFolderHasTheUsualPermissionsNotTheOwnerOnlyOnesOfATemporaryFolder() {
        val parent = parent()
        write(parent)
        val permissions = Files.getPosixFilePermissions(File(parent, "repo").toPath())
        val usual = Files.getPosixFilePermissions(Files.createDirectory(File(parent, "usual").toPath()))
        assertEquals(usual, permissions)
    }

    @Test
    fun writesIntoAnExistingEmptyFolder() {
        val parent = parent()
        File(parent, "repo").mkdir()
        write(parent)
        assertEquals(tree.keys, hashes(File(parent, "repo")).keys)
        assertEquals(listOf("repo"), parent.list()!!.toList())
    }

    @Test
    fun anExistingRepositoryKeepsEverythingTheTreeDoesNotName() {
        val parent = parent()
        val repo = File(parent, "repo").apply { mkdir() }
        File(repo, ".git").mkdir()
        File(repo, ".git/config").writeText("[core]")
        File(repo, "README.md").writeText("mine")
        File(repo, "OFL.txt").writeText("the old licence")
        File(repo, "sources/F-Regular.ufo/glyphs").mkdirs()
        File(repo, "sources/F-Regular.ufo/glyphs/Deleted.glif").writeText("deleted glyph")
        File(repo, "sources/Other.ufo").mkdirs()
        File(repo, "sources/Other.ufo/fontinfo.plist").writeText("another font")
        val report = write(parent)
        assertEquals("[core]", File(repo, ".git/config").readText())
        assertEquals("mine", File(repo, "README.md").readText())
        assertEquals("licence", File(repo, "OFL.txt").readText())
        assertFalse(File(repo, "sources/F-Regular.ufo/glyphs/Deleted.glif").exists(), "a glyph the new UFO lacks must not linger")
        assertEquals("another font", File(repo, "sources/Other.ufo/fontinfo.plist").readText())
        assertEquals("info", File(repo, "sources/F-Regular.ufo/fontinfo.plist").readText())
        assertEquals(listOf("repo"), parent.list()!!.toList(), "neither the working folder nor the set-aside one may remain")
        assertEquals(1, report.replaced, "only OFL.txt was a file the tree replaced")
    }

    @Test
    fun aFailureWhileMovingIntoAnExistingRepositoryPutsEverythingBack() {
        val parent = parent()
        val repo = File(parent, "repo").apply { mkdir() }
        File(repo, "README.md").writeText("mine")
        File(repo, "OFL.txt").writeText("the old licence")
        File(repo, "fonts").mkdir()
        File(repo, "fonts/old.ttf").writeText("old font")
        File(repo, "sources/F-Regular.ufo").mkdirs()
        File(repo, "sources/F-Regular.ufo/fontinfo.plist").writeText("old info")
        val before = hashes(repo)
        val beforeDirs = dirs(repo)
        var moves = 0
        val writer =
            FolderTreeWriter(
                repo.toPath(),
                "repo",
                beforeCommit = {},
                beforeMove = { if (++moves == 3) throw IOException("the disk went away") },
            )
        val error = assertFailsWith<TreeWriteException> { runBlocking { writer.write(tree, owned) } }
        assertTrue("the disk went away" in error.message.orEmpty() && "Nothing was changed" in error.message.orEmpty(), error.message)
        assertEquals(before, hashes(repo), "every file must be as it was")
        assertEquals(beforeDirs, dirs(repo), "no directory may be left that the failed write made")
        assertEquals(listOf("repo"), parent.list()!!.toList())
    }

    @Test
    fun aFailureOnTheVeryFirstMoveLeavesTheRepositoryAsItWas() {
        val parent = parent()
        val repo = File(parent, "repo").apply { mkdir() }
        File(repo, "OFL.txt").writeText("the old licence")
        val before = hashes(repo)
        val writer = FolderTreeWriter(repo.toPath(), "repo", beforeCommit = {}, beforeMove = { throw IOException("no") })
        assertFailsWith<TreeWriteException> { runBlocking { writer.write(tree, owned) } }
        assertEquals(before, hashes(repo))
        assertEquals(listOf("repo"), parent.list()!!.toList())
    }

    @Test
    fun cancellingBeforeTheMoveLeavesTheFolderUntouchedAndRemovesTheWorkingFolder() {
        val parent = parent()
        val repo = File(parent, "repo").apply { mkdir() }
        File(repo, "OFL.txt").writeText("the old licence")
        val before = hashes(repo)
        val reached = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val writer =
            FolderTreeWriter(
                repo.toPath(),
                "repo",
                beforeCommit = {
                    runBlocking {
                        reached.complete(Unit)
                        proceed.await()
                    }
                },
                beforeMove = {},
            )
        runBlocking {
            val job = async { runCatching { writer.write(tree, owned) } }
            reached.await()
            assertTrue(parent.list()!!.size == 2, "the working folder exists while the files are being staged: ${parent.list()!!.toList()}")
            job.cancel()
            proceed.complete(Unit)
            job.join()
        }
        assertEquals(before, hashes(repo))
        assertEquals(listOf("repo"), parent.list()!!.toList(), "the working folder must be removed")
    }

    @Test
    fun cancellingBeforeAnyFolderExistsLeavesNoFolderAtAll() {
        val parent = parent()
        val reached = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val writer =
            FolderTreeWriter(File(parent, "repo").toPath(), "repo", beforeCommit = {
                runBlocking {
                    reached.complete(Unit)
                    proceed.await()
                }
            }, beforeMove = {})
        runBlocking {
            val job = async { runCatching { writer.write(tree, owned) } }
            reached.await()
            job.cancel()
            proceed.complete(Unit)
            job.join()
        }
        assertEquals(emptyList(), parent.list()!!.toList())
    }

    @Test
    fun aRefusedPathTouchesNothing() {
        val parent = parent()
        assertFailsWith<TreeWriteException> { write(parent, files = mapOf("ok.txt" to byteArrayOf(1), "../outside.txt" to byteArrayOf(2))) }
        assertEquals(emptyList(), parent.list()!!.toList())
    }

    @Test
    fun aMissingParentFolderIsSaidInWords() {
        val parent = parent()
        val error = assertFailsWith<TreeWriteException> { write(File(parent, "missing/deeper").parentFile, "repo") }
        assertTrue("does not exist" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aTargetThatIsAFileIsRefused() {
        val parent = parent()
        File(parent, "repo").writeText("a file")
        val error = assertFailsWith<TreeWriteException> { write(parent) }
        assertTrue("is a file" in error.message.orEmpty(), error.message)
        assertEquals("a file", File(parent, "repo").readText())
    }

    @Test
    fun aFolderTheTreeWantsAsAFileIsReportedAndNothingIsChanged() {
        val parent = parent()
        val repo = File(parent, "repo").apply { mkdir() }
        File(repo, "fonts").writeText("a file where the folder fonts should be")
        File(repo, "OFL.txt").writeText("the old licence")
        val before = hashes(repo)
        assertFailsWith<TreeWriteException> { write(parent) }
        assertEquals(before, hashes(repo))
        assertEquals(listOf("repo"), parent.list()!!.toList())
    }

    @Test
    fun theDescriptionDefaultsToThePath() {
        val path = File(parent(), "repo").toPath()
        assertEquals(path.toString(), FolderTreeWriter(path).description)
        assertEquals("~/Fonts/x", FolderTreeWriter(path, "~/Fonts/x").description)
    }
}
