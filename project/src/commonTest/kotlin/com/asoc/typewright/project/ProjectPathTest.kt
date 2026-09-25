// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectPathTest {
    @Test
    fun acceptsRelativeSlashSeparatedPaths() {
        val good =
            listOf(
                "typewright.json",
                "HyleDeco-Regular.ufo/glyphs/T_.glif",
                "Hyle Deco/a b.json",
                ".gitignore",
                "build/.session-lock",
                "a..b",
            )
        for (path in good) {
            assertTrue(ProjectPath.isValid(path), path)
            assertEquals(path, ProjectPath.validate(path))
        }
    }

    @Test
    fun refusesPathsThatEscapeOrAreAmbiguous() {
        val bad = listOf("", "/abs", "../up", "a/../b", "a/..", "..", ".", "./a", "a/./b", "a//b", "a/", "a\\b", "..\\x", "a\u0000b")
        for (path in bad) {
            assertFalse(ProjectPath.isValid(path), path)
            assertFailsWith<IllegalArgumentException>(path) { ProjectPath.validate(path) }
        }
    }

    @Test
    fun refusesWritingNamesReservedForAtomicWrites() {
        for (path in listOf("a.tmp", "dir/b.new", "c.crswap")) {
            assertTrue(ProjectPath.isValid(path))
            assertFailsWith<IllegalArgumentException>(path) { ProjectPath.validateWritable(path) }
        }
        assertEquals("a.tmp.json", ProjectPath.validateWritable("a.tmp.json"))
    }

    @Test
    fun splitsAndJoins() {
        assertEquals("", ProjectPath.parent("a.json"))
        assertEquals("locks/regular", ProjectPath.parent("locks/regular/T_.glif"))
        assertEquals("T_.glif", ProjectPath.name("locks/regular/T_.glif"))
        assertEquals(listOf("locks", "regular", "T_.glif"), ProjectPath.segments("locks/regular/T_.glif"))
        assertEquals("a.json", ProjectPath.join("", "a.json"))
        assertEquals("locks/a.json", ProjectPath.join("locks", "a.json"))
        assertFailsWith<IllegalArgumentException> { ProjectPath.join("locks", "..") }
    }

    @Test
    fun knowsTheGitDirectory() {
        assertTrue(ProjectPath.isInGitDirectory(".git"))
        assertTrue(ProjectPath.isInGitDirectory(".git/HEAD"))
        assertFalse(ProjectPath.isInGitDirectory(".gitignore"))
        assertFalse(ProjectPath.isInGitDirectory("build/.git"))
    }

    @Test
    fun recoveryPlanSortsTempFilesOfFormatPathsOnly() {
        val plan =
            RecoveryPlan.of(
                listOf(
                    "HyleDeco-Regular.ufo/glyphs/b.glif.tmp",
                    "typewright.json.new",
                    "x.tmp.json",
                    ".git/index.tmp",
                    "scrapbook/manifest.json.crswap",
                    ".tmp",
                    "locks/.new",
                    "locks/regular/e.glif.new",
                    "typewright.json",
                    "notes.txt.new",
                    "README.md.tmp",
                    "HyleDeco-Regular.ufo/data/com.example/cache.tmp",
                ),
            )
        assertEquals(listOf("HyleDeco-Regular.ufo/glyphs/b.glif.tmp", "scrapbook/manifest.json.crswap"), plan.discard)
        assertEquals(listOf("locks/regular/e.glif.new", "typewright.json.new"), plan.rollForward)
        assertEquals("locks/regular/e.glif", RecoveryPlan.targetOf("locks/regular/e.glif.new"))
        assertTrue(RecoveryPlan.of(listOf("typewright.json")).isEmpty)
    }

    @Test
    fun theFormatPathsAreExactlyWhatAProjectWrites() {
        val written =
            listOf(
                "typewright.json",
                "HyleDeco-Regular.ufo/metainfo.plist",
                "HyleDeco-Regular.ufo/fontinfo.plist",
                "HyleDeco-Regular.ufo/layercontents.plist",
                "HyleDeco-Regular.ufo/lib.plist",
                "HyleDeco-Regular.ufo/groups.plist",
                "HyleDeco-Regular.ufo/kerning.plist",
                "HyleDeco-Regular.ufo/features.fea",
                "HyleDeco-Regular.ufo/glyphs/contents.plist",
                "HyleDeco-Regular.ufo/glyphs/T_.glif",
                "locks/regular/T_.glif",
                "locks/regular/T_.20260925T110241Z.diff",
                "scrapbook/manifest.json",
                "scrapbook/pin-1.jpg",
                "scrapbook/pin-2.png",
                "scrapbook/pin-3.webp",
                "lessons/workbook-latn.json",
                "comparisons/overlays.json",
                "comparisons/WorkSans-Regular.ttf",
                "build/.gitignore",
                "build/HyleDeco-Regular.ttf",
                "build/reports/check.json",
            )
        for (path in written) assertTrue(ProjectLayout.isFormatPath(path), path)
        val foreign =
            listOf(
                "README.md",
                "notes.txt",
                ".git/HEAD",
                ".gitignore",
                "typewright.json/x",
                "sources/typewright.json",
                "HyleDeco-Regular.ufo/data/com.example/settings.json",
                "HyleDeco-Regular.ufo/images/sketch.png",
                "HyleDeco-Regular.ufo/glyphs.background/a.glif",
                "HyleDeco-Regular.ufo/glyphs/layerinfo.plist",
                "HyleDeco-Regular.ufo/README.txt",
                ".ufo/metainfo.plist",
                "locks/T_.glif",
                "locks/regular/notes.txt",
                "locks/regular/deeper/T_.glif",
                "scrapbook/notes.txt",
                "scrapbook/deeper/pin.png",
                "lessons/notes.json",
                "comparisons/Jost.otf",
                "build",
                "a\\b",
            )
        for (path in foreign) assertFalse(ProjectLayout.isFormatPath(path), path)
    }

    @Test
    fun inMemoryProjectFilesListsSortedAndCopies() {
        val files = InMemoryProjectFiles("p", mapOf("b.json" to byteArrayOf(1), "a/c.glif" to byteArrayOf(2)))
        assertEquals(listOf("a/c.glif", "b.json"), files.listFiles())
        val bytes = files.readBytes("b.json")
        bytes[0] = 9
        assertContentEquals(byteArrayOf(1), files.readBytes("b.json"))
        assertFailsWith<NoSuchElementException> { files.readBytes("missing") }
        assertFailsWith<IllegalArgumentException> { InMemoryProjectFiles("p", mapOf("../x" to byteArrayOf())) }
    }
}
