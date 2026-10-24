// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.project.Fixtures
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UfoFolderReaderTest {
    private val oflInfo =
        UfoFontInfo(
            familyName = "Fixture",
            styleName = "Regular",
            unitsPerEm = 1000,
            copyright = "Copyright 2020 The Fixture Authors",
            openTypeNameLicense = "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
        )

    private fun writeUfo(
        parent: Path,
        name: String,
    ): Path {
        val dir = parent.resolve(name)
        val ufo = Fixtures.ufoProject(listOf("A", "n")).copy(fontInfo = oflInfo)
        for ((path, text) in writeUfoProject(ufo)) {
            val file = dir.resolve(path)
            file.parent.createDirectories()
            file.writeText(text)
        }
        return dir
    }

    @Test
    fun readsTheTextFilesAndListsTheRestBesideTheSiblings() {
        val parent = createTempDirectory("typewright-ufo-reader")
        try {
            val dir = writeUfo(parent, "Fixture-Regular.ufo")
            writeUfo(parent, "Fixture-Bold.ufo")
            dir.resolve("images").createDirectories()
            dir.resolve("images/ref.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            parent
                .resolve(
                    "OFL.txt",
                ).writeText("Copyright 2020, with Reserved Font Name \"Fixture Sans\".\nSIL OPEN FONT LICENSE Version 1.1")
            parent.resolve("notes.txt").writeText("not a licence")

            val read = readUfoFolder(dir)
            assertIs<UfoFolderRead.Read>(read)
            val folder = read.folder
            assertEquals("Fixture-Regular.ufo", folder.name)
            assertEquals(parent.toAbsolutePath().normalize().toString(), folder.parentPath)
            assertEquals(listOf("Fixture-Bold.ufo"), folder.otherUfos)
            assertEquals(listOf("images/ref.png"), folder.otherEntries)
            assertTrue("metainfo.plist" in folder.files && "glyphs/contents.plist" in folder.files)
            assertEquals(1, folder.licenceFiles.size)

            val review = folder.review()
            assertIs<ImportReview.Ready>(review)
            val candidate = review.candidate
            assertEquals("Fixture-Regular.ufo", candidate.ufoFolder)
            assertEquals(parent.toAbsolutePath().normalize().toString(), candidate.siblings?.parentPath)
            assertEquals(listOf("Fixture-Bold.ufo"), candidate.siblings?.otherUfos)
            assertEquals(
                listOf("Fixture Sans"),
                candidate.audit.reservedFontNames,
                "the reserved name comes from the OFL.txt beside the UFO",
            )
            assertEquals(ImportVerdict.RENAME, candidate.audit.verdict)
            assertTrue(candidate.report.notes.any { "images and data folders are left in place" in it }, candidate.report.notes.toString())
        } finally {
            parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFolderThatIsNotAUfoOrNotAFolderFailsInPlainWords() {
        val parent = createTempDirectory("typewright-ufo-reader")
        try {
            val plain = parent.resolve("Plain").also { it.createDirectories() }
            val notUfo = readUfoFolder(plain)
            assertIs<UfoFolderRead.Failed>(notUfo)
            assertTrue("no metainfo.plist" in notUfo.message)
            val file = parent.resolve("file.txt").also { it.writeText("x") }
            assertEquals("That isn't a folder.", (readUfoFolder(file) as UfoFolderRead.Failed).message)
            assertIs<UfoFolderRead.Failed>(readUfoFolder(parent.resolve("missing.ufo")))
        } finally {
            parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun aUfoOfAnOlderFormatIsUnreadableNotAnException() {
        val parent = createTempDirectory("typewright-ufo-reader")
        try {
            val dir = writeUfo(parent, "Old.ufo")
            dir
                .resolve(
                    "metainfo.plist",
                ).writeText(Files.readString(dir.resolve("metainfo.plist")).replace("<integer>3</integer>", "<integer>2</integer>"))
            val read = readUfoFolder(dir) as UfoFolderRead.Read
            val review = read.folder.review()
            assertIs<ImportReview.Unreadable>(review)
            assertTrue("UFO format version 3" in review.message, review.message)
        } finally {
            parent.toFile().deleteRecursively()
        }
    }
}
