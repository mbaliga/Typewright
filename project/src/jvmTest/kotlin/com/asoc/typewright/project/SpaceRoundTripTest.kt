// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.readGroupsPlist
import com.asoc.typewright.core.font.ufo.readKerningPlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the Space page writes (kerning groups, pair values and `features.fea`) through the real file store: it
 * survives a save and a reopen byte for byte, re-encoding changes nothing, and undoing every edit puts the files
 * back as they were, down to the files that were not there. Over Hyle Deco's own outlines (`fonts/HyleDeco-Regular.ttf`).
 */
class SpaceRoundTripTest {
    private val tempDirs = mutableListOf<Path>()
    private val master = "regular"

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun tempDir(name: String): Path = Files.createTempDirectory("typewright-space-$name-").also { tempDirs.add(it) }

    private val repoRoot: File by lazy {
        File(System.getProperty("typewright.repoRoot") ?: error("system property 'typewright.repoRoot' is not set"))
    }

    private fun env(scope: CoroutineScope) = SessionEnvironment(scope = scope, io = Dispatchers.IO, clock = SteppingClock(Fixtures.EPOCH))

    private fun spec() =
        NewProjectSpec(
            name = "Hyle Deco",
            brief =
                Brief(
                    source = BriefSource.FONT,
                    styleClass = StyleClass(declared = "sans-geometric", confirmed = null),
                    ethos = Ethos(genre = "sans-geometric"),
                ),
            scripts = listOf("Latn"),
            masters =
                listOf(
                    NewMaster(
                        id = master,
                        styleName = "Regular",
                        ufo = readSfntFont(File(repoRoot, "fonts/HyleDeco-Regular.ttf").readBytes()).toUfoProject(),
                    ),
                ),
        )

    private suspend fun create(
        root: Path,
        scope: CoroutineScope,
    ): ProjectSession = (ProjectSession.create(FileSystemProjectStore(root), spec(), env(scope)) as CreateResult.Created).session

    private suspend fun reopen(
        root: Path,
        scope: CoroutineScope,
    ): ProjectSession = assertIs<OpenResult.Opened>(ProjectSession.open(FileSystemProjectStore(root), env(scope))).session

    private fun tree(root: Path): Map<String, ByteArray> =
        Files
            .walk(root)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .map { root.relativize(it).toString().replace(File.separatorChar, '/') to Files.readAllBytes(it) }
                    .toList()
            }.toMap()

    private fun assertTreesEqual(
        expected: Map<String, ByteArray>,
        actual: Map<String, ByteArray>,
    ) {
        assertEquals(expected.keys, actual.keys)
        for (key in expected.keys) assertContentEquals(expected.getValue(key), actual.getValue(key), "\"$key\" differs")
    }

    private fun ProjectSession.kerning(): UfoKerning =
        state.value.font
            .master(master)!!
            .ufo.kerningInfo

    private fun ProjectSession.run(command: FontCommand?) {
        assertNotNull(command, "a command was expected")
        assertIs<EditResult.Applied>(execute(command))
    }

    private fun ProjectSession.run(edit: GroupEdit) = run(assertIs<GroupEdit.Run>(edit).command)

    /** Groups, a group pair, a pair exception, a features file and a respacing, written as the Space page writes them. */
    private fun ProjectSession.editAsSpaceDoes(features: String) {
        run(KerningGroupEdits.create(master, kerning(), KernSide.FIRST, "round", listOf("o", "O")))
        run(KerningGroupEdits.create(master, kerning(), KernSide.SECOND, "round", listOf("o", "O")))
        run(KerningGroupEdits.create(master, kerning(), KernSide.FIRST, "straight", listOf("n", "H")))
        run(KerningGroupEdits.create(master, kerning(), KernSide.SECOND, "straight", listOf("n", "H")))
        run(KerningEdits.set(master, KerningTable(kerning()), "o", "n", -24.0, KernScope.GROUPS))
        run(KerningEdits.set(master, KerningTable(kerning()), "n", "o", -12.5, KernScope.PAIR_ONLY))
        run(SetFeatures(master, features))
        run(SetSidebearings(GlyphRef(master, "n"), left = 40, right = 52))
    }

    private val features = "# kerning, set by hand\nfeature kern {\n    pos n o -12;\n} kern;\n"

    // ---- save and reopen ------------------------------------------------------------------------------

    @Test
    fun groupsPairValuesAndFeaturesSurviveSaveAndReopenByteForByte() =
        runBlocking {
            val root = tempDir("roundtrip")
            val a = create(root, this)
            a.editAsSpaceDoes(features)
            val expected = a.state.value
            a.close()

            val files = tree(root)
            val dir =
                expected.font.masters
                    .single()
                    .path
            for (name in listOf("groups.plist", "kerning.plist", "features.fea")) assertTrue("$dir/$name" in files, "$name was written")
            assertContentEquals(
                features.encodeToByteArray(),
                files.getValue("$dir/features.fea"),
                "features.fea is the text, byte for byte",
            )

            val b = reopen(root, this)
            assertEquals(expected, b.state.value, "the project reads back as it was")
            assertEquals(
                expected.font
                    .master(master)!!
                    .ufo.kerningInfo,
                b.kerning(),
            )
            assertEquals(
                mapOf(
                    "public.kern1.round" to listOf("o", "O"),
                    "public.kern2.round" to listOf("o", "O"),
                    "public.kern1.straight" to listOf("n", "H"),
                    "public.kern2.straight" to listOf("n", "H"),
                ),
                b.kerning().groups,
                "the groups keep their order and their members' order",
            )
            assertEquals(-24.0, KerningTable(b.kerning()).value("O", "H"))
            assertEquals(-12.5, KerningTable(b.kerning()).value("n", "o"), "a fractional value stays a fraction")
            assertEquals(features, b.kerning().features)
            assertTrue(b.openReport.externalEdits.isEmpty())
            b.close()
            assertTreesEqual(files, tree(root)) // saving what was just opened writes nothing

            val c = reopen(root, this)
            c.rewriteAll()
            c.close()
            assertTreesEqual(files, tree(root)) // re-encoding every file from scratch is byte-identical
        }

    @Test
    fun theWrittenPlistsReadBackToTheSameGroupsAndPairs() =
        runBlocking {
            val root = tempDir("plists")
            val a = create(root, this)
            a.editAsSpaceDoes(features)
            val kerning = a.kerning()
            val dir =
                a.state.value.font.masters
                    .single()
                    .path
            a.close()
            val files = tree(root)
            assertEquals(kerning.groups, readGroupsPlist(files.getValue("$dir/groups.plist").decodeToString()))
            assertEquals(kerning.kerning, readKerningPlist(files.getValue("$dir/kerning.plist").decodeToString()))
            val kerningText = files.getValue("$dir/kerning.plist").decodeToString()
            assertTrue(
                "<integer>-24</integer>" in kerningText && "<real>-12.5</real>" in kerningText,
                "whole values are integers, others reals",
            )
            assertTrue(
                files.values.all { bytes ->
                    bytes.none {
                        it < 0x20 && it != '\n'.code.toByte() && it != '\t'.code.toByte() &&
                            it != '\r'.code.toByte()
                    }
                },
            )
        }

    @Test
    fun featuresTextIsKeptExactlyWhateverItHolds() =
        runBlocking {
            val cases =
                listOf(
                    "crlf" to "\t# tab\r\nfeature kern {\r\n  pos n o -10; # ünï →\r\n} kern;",
                    "blank" to "",
                    "newline" to "\n",
                    "spaces" to "feature kern {   \n    pos n o -10;  \n} kern;\n\n\n",
                )
            for ((name, text) in cases) {
                val root = tempDir("fea-$name")
                val a = create(root, this)
                a.run(SetFeatures(master, text))
                val dir =
                    a.state.value.font.masters
                        .single()
                        .path
                a.close()
                assertContentEquals(text.encodeToByteArray(), File(root.toFile(), "$dir/features.fea").readBytes(), "$name: the file")
                val b = reopen(root, this)
                assertEquals(text, b.kerning().features, "$name: reopened")
                val files = tree(root)
                b.rewriteAll()
                b.close()
                assertTreesEqual(files, tree(root))
            }
        }

    @Test
    fun groupAndGlyphNamesWithMarkupCharactersSurviveThePlist() =
        runBlocking {
            val root = tempDir("markup")
            val a = create(root, this)
            val name = "public.kern1.a&b<c>\"d'"
            a.run(KerningGroupEdits.create(master, a.kerning(), KernSide.FIRST, "a&b<c>\"d'", listOf("o")))
            a.run(SetKerning(master, name, "n", -10.0))
            val expected = a.kerning()
            a.close()
            val b = reopen(root, this)
            assertEquals(expected, b.kerning())
            assertEquals(-10.0, KerningTable(b.kerning()).value("o", "n"))
            b.close()
        }

    // ---- undo ------------------------------------------------------------------------------------------

    @Test
    fun undoingEveryEditRestoresTheFilesByteForByteAndRemovesTheFilesThatWerentThere() =
        runBlocking {
            val root = tempDir("undo")
            val a = create(root, this)
            a.flush()
            val initial = tree(root)
            val dir =
                a.state.value.font.masters
                    .single()
                    .path
            assertFalse("$dir/kerning.plist" in initial || "$dir/groups.plist" in initial || "$dir/features.fea" in initial)

            a.editAsSpaceDoes(features)
            a.flush()
            val edited = tree(root)
            assertTrue("$dir/kerning.plist" in edited && "$dir/groups.plist" in edited && "$dir/features.fea" in edited)
            assertTrue(edited.keys != initial.keys || edited.any { (k, v) -> !v.contentEquals(initial[k]) })

            var steps = 0
            while (a.undo()) steps++
            assertTrue(steps in 1..8, "every edit was an undo step (some merge when made in a burst): $steps")
            a.flush()
            assertTreesEqual(initial, tree(root))
            a.close()
            assertTreesEqual(initial, tree(root))
        }

    @Test
    fun redoingAfterUndoingWritesTheSameBytesAsTheFirstTime() =
        runBlocking {
            val root = tempDir("redo")
            val a = create(root, this)
            a.editAsSpaceDoes(features)
            a.flush()
            val edited = tree(root)
            while (a.undo()) Unit
            while (a.redo()) Unit
            a.flush()
            assertTreesEqual(edited, tree(root))
            a.close()
        }
}
