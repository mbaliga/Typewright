// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The two scenarios docs/PROJECT_MODEL.md §12 sketches for golden-path steps 3 and 7, proven here
 * against `project`'s real [ProjectSession] API (same method names, same semantics), as the WP2
 * work package's own verification. This does not touch `golden-path/` or depend on it: it builds
 * its own seed from core-font's sfnt bridge over the repository's `fonts/HyleDeco-Regular.ttf`,
 * exactly as golden-path's `Seeds.kt` does, using the same `typewright.repoRoot` system property
 * `project/build.gradle.kts` already sets. Wiring these scenarios into `golden-path` itself
 * (`GoldenPathSteps.kt`, `ProjectFixtures.kt`, `passing-steps.txt`) is WP4's job.
 */
class GoldenPathScenariosTest {
    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    private fun tempDir(name: String): Path = Files.createTempDirectory("typewright-project-$name-").also { tempDirs.add(it) }

    private val repoRoot: File by lazy {
        val path = System.getProperty("typewright.repoRoot") ?: error("system property 'typewright.repoRoot' is not set")
        File(path)
    }

    /** S-ufo, in-memory: S-ttf (`fonts/HyleDeco-Regular.ttf`) through core-font's bridge, quadratic. */
    private fun newSUfo() = readSfntFont(File(repoRoot, "fonts/HyleDeco-Regular.ttf").readBytes()).toUfoProject()

    private fun env(scope: CoroutineScope) = SessionEnvironment(scope = scope, io = Dispatchers.IO, clock = SteppingClock(Fixtures.EPOCH))

    /** Every regular file under [root], relative and `/`-separated, with its bytes. */
    private fun tree(root: Path): Map<String, ByteArray> =
        Files
            .walk(root)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .map { root.relativize(it).toString().replace(File.separatorChar, '/') to Files.readAllBytes(it) }
                    .toList()
            }.toMap()

    private fun assertNoTempFiles(root: Path) {
        for (path in tree(root).keys) {
            assertTrue(!path.endsWith(".tmp") && !path.endsWith(".new"), "$path was left behind")
        }
    }

    private fun assertTreesEqual(
        expected: Map<String, ByteArray>,
        actual: Map<String, ByteArray>,
    ) {
        assertEquals(expected.keys, actual.keys)
        for (key in expected.keys) assertTrue(expected.getValue(key).contentEquals(actual.getValue(key)), "\"$key\" differs")
    }

    // ---- Step 7: close and reopen gives back the exact same project ---------------------------

    @Test
    fun step7CloseAndReopenRoundTripsExactlyAndReSavingWritesNothing() =
        runBlocking {
            val root = tempDir("step7")
            val spec =
                NewProjectSpec(
                    name = "Hyle Deco",
                    brief = Brief(source = BriefSource.FONT, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
                    scripts = listOf("Latn"),
                    masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = newSUfo())),
                )
            val a = (ProjectSession.create(FileSystemProjectStore(root), spec, env(this)) as CreateResult.Created).session
            val glyphT = GlyphRef("regular", "T")
            val glyphH = GlyphRef("regular", "H")

            a.execute(Approve(glyphT, ApprovalOrigin.TRACE))
            a.execute(Unlock(glyphT))
            a.execute(MovePoints(glyphT, setOf(PointRef(0, 0)), 0, -1))
            a.execute(Approve(glyphT, ApprovalOrigin.DRAW)) // frozen diff: the episode just opened is relocked

            a.execute(Approve(glyphH, ApprovalOrigin.DRAW))
            a.execute(Unlock(glyphH))
            a.execute(MovePoints(glyphH, setOf(PointRef(0, 0)), 5, 0)) // left open

            a.execute(SetKerning("regular", "T", "o", -40.0))
            val fontInfo =
                a.state.value.font.masters
                    .single()
                    .ufo.fontInfo
            a.execute(UpdateFontInfo("regular", fontInfo.copy(openTypeNameDesigner = "Golden Path")))

            a.update(MetaChange.AddPin(ScrapbookPin("pin-1", ScrapbookPinKind.NOTE, "Note", "note", noteText = "A note"), null, null))
            a.update(MetaChange.AddReflection("latn", 4, "Round ends everywhere, or nowhere."))
            a.update(MetaChange.SetTaskConfirmed("latn", 1, true))

            val expected = a.state.value
            a.close()

            assertNoTempFiles(root)
            val hashes = tree(root)

            val b = (ProjectSession.open(FileSystemProjectStore(root), env(this)) as OpenResult.Opened).session
            assertEquals(expected, b.state.value) // ⚑ project, locks, diffs, typewright.json, scrapbook, lessons
            assertTrue(b.openReport.externalEdits.isEmpty())
            b.close()
            assertTreesEqual(hashes, tree(root)) // ⚑ saving with no changes: no writes

            val c = (ProjectSession.open(FileSystemProjectStore(root), env(this)) as OpenResult.Opened).session
            c.rewriteAll()
            c.close()
            assertTreesEqual(hashes, tree(root)) // ⚑ re-encoding is byte-identical
            assertNoTempFiles(root)
        }

    // ---- Step 3: point moves, live economy, kerning, locks, undo-all ---------------------------

    @Test
    fun step3PointMovesEconomyKerningAndLocksBehaveAsSpecified() =
        runBlocking {
            val root = tempDir("step3")
            val spec =
                NewProjectSpec(
                    name = "Hyle Deco",
                    brief = Brief(source = BriefSource.FONT, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
                    scripts = listOf("Latn"),
                    masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = newSUfo())),
                )
            val session = (ProjectSession.create(FileSystemProjectStore(root), spec, env(this)) as CreateResult.Created).session
            val masterPath =
                session.state.value.font.masters
                    .single()
                    .path
            val initial = session.state.value.font
            val treeAfterCreate = tree(root)
            val glyphN = GlyphRef("regular", "n")
            val glyphH = GlyphRef("regular", "H")

            // A point move changes only that point.
            val before = initial.glyph(glyphN)!!
            session.execute(MovePoints(glyphN, setOf(PointRef(0, 0)), 10, 0))
            val afterMove = session.state.value.font
            val movedGlyph = afterMove.glyph(glyphN)!!
            assertEquals(
                before.contours[0]
                    .points[0]
                    .point.x + 10,
                movedGlyph.contours[0]
                    .points[0]
                    .point.x,
            )
            for (master in afterMove.masters) {
                for (glyph in master.ufo.glyphs) {
                    if (GlyphRef(master.id, glyph.name) == glyphN) continue
                    assertEquals(
                        initial
                            .master(master.id)!!
                            .ufo.glyphs
                            .single { it.name == glyph.name },
                        glyph,
                    )
                }
            }

            // ⚑ Live economy: replacing an outline changes the count, and the session's own economy agrees with it.
            val economyBefore = session.economy.value.count(glyphN)
            val newContours = movedGlyph.contours + movedGlyph.contours
            session.execute(ReplaceOutline(glyphN, newContours, "Duplicate contour"))
            assertEquals(
                session.state.value.font
                    .glyph(glyphN)!!
                    .count(),
                session.economy.value.count(glyphN),
            )
            assertTrue(economyBefore != session.economy.value.count(glyphN))

            // Kerning, then flush and read back kerning.plist from disk.
            session.execute(SetKerning("regular", "T", "o", -40.0))
            session.flush()
            val kerningText = FileSystemProjectStore(root).read("$masterPath/kerning.plist")!!.decodeToString()
            assertEquals(
                -40.0,
                com.asoc.typewright.core.font.ufo
                    .readKerningPlist(kerningText)
                    .getValue("T")
                    .getValue("o"),
            )

            // Locked glyph: refused, then unlocked, moved, flushed and a diff appears containing the change.
            session.execute(Approve(glyphH, ApprovalOrigin.DRAW))
            val refusal = session.execute(MovePoints(glyphH, setOf(PointRef(0, 0)), 1, 0))
            assertEquals(EditResult.Refused(Refusal.Locked(glyphH)), refusal)
            session.execute(Unlock(glyphH))
            session.execute(MovePoints(glyphH, setOf(PointRef(0, 0)), 1, 0))
            session.flush()
            val store = FileSystemProjectStore(root)
            val diffPaths = store.list().filter { it.startsWith("locks/regular/H_.") && it.endsWith(".diff") }
            assertEquals(1, diffPaths.size)
            val diffText = store.read(diffPaths.single())!!.decodeToString()
            assertTrue(diffText.contains("-") && diffText.contains("+"))

            // Undo-all restores the initial font and, once flushed, the initial file tree (kerning.plist and locks/ gone).
            while (session.undo()) { /* keep undoing */ }
            assertEquals(initial, session.state.value.font)
            session.flush()
            assertTreesEqual(treeAfterCreate, tree(root))

            while (session.redo()) { /* keep redoing */ }
            session.flush()
            assertEquals(
                -40.0,
                session.state.value.font.masters
                    .single()
                    .ufo.kerningInfo.kerning
                    .getValue("T")
                    .getValue("o"),
            )
            // H was approved, then unlocked and moved (never re-approved), so it ends UNLOCKED with one open episode.
            assertEquals(
                LockState.UNLOCKED,
                session.state.value.font.locks
                    .getValue(glyphH)
                    .state,
            )
            assertEquals(
                1,
                session.state.value.font.locks
                    .getValue(glyphH)
                    .episodes.size,
            )

            session.close()
            assertNoTempFiles(root)
        }
}
