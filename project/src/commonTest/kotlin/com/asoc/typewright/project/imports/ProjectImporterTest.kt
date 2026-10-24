// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.Fixtures
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.InMemoryAppConfigStore
import com.asoc.typewright.project.InMemoryProjectStore
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.MovePoints
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Storage that hands back the same store for the same (parent, name), as a real folder does, so a name clash is real. */
private class SharedNameStorage : StorageProvider {
    val stores = mutableMapOf<ProjectLocation, InMemoryProjectStore>()
    var failCreateChild: Boolean = false

    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        val store = stores[location] ?: return StoreLookup.Missing
        return StoreLookup.Available(store)
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        check(!failCreateChild) { "the disk is full" }
        val parentName = (parent as? ProjectLocation.InMemory)?.name ?: "root"
        val store = stores.values.firstOrNull { it.displayName == "$parentName/$name" } ?: InMemoryProjectStore("$parentName/$name")
        stores[store.location] = store
        return store.location
    }
}

class ProjectImporterTest {
    private val root = ProjectLocation.InMemory("root")

    private fun workspace(
        scope: CoroutineScope,
        storage: StorageProvider,
    ) = ProjectWorkspace(storage, InMemoryAppConfigStore()) { Fixtures.env(scope) }

    private fun candidate(
        info: UfoFontInfo =
            UfoFontInfo(
                familyName = "Fixture",
                styleName = "Regular",
                unitsPerEm = 1000,
                copyright = "Copyright 2020 The Fixture Authors",
                openTypeNameLicense = "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
            ),
        ufoFolder: String? = null,
    ): ImportCandidate {
        val ufo =
            Fixtures.ufoProject(listOf("A", "B", "n")).let { project ->
                project.copy(fontInfo = info, glyphs = project.glyphs.map { it.copy(unicodes = listOf(it.name.first().code)) })
            }
        val audit = auditLicence("Fixture.ttf", info, AuditSource.FONT_FILE)
        return ImportCandidate("Fixture.ttf", ufo, audit, reportFor(ImportFormat.FONT_FILE, ufo, emptyList()), ufoFolder)
    }

    @Test
    fun anImportMakesAnOpenProjectWhoseGlyphsAreAllApprovedAndLockedAsImported() =
        runTest {
            val ws = workspace(backgroundScope, SharedNameStorage())
            val outcome = ProjectImporter(ws).create(candidate(), root, "Fixture")
            assertIs<ImportOutcome.Imported>(outcome)
            val session = outcome.session
            assertEquals(session, ws.current.value)
            val state = session.state.value
            assertEquals("Fixture", state.meta.manifest.name)
            assertEquals(BriefSource.FONT, state.meta.manifest.brief.source)
            assertNull(state.meta.manifest.brief.styleClass.declared, "an import does not guess a style class")
            assertEquals(listOf("Latn"), state.meta.manifest.scripts)
            assertEquals(3, state.font.locks.size)
            for ((ref, lock) in state.font.locks) {
                assertEquals(LockState.LOCKED, lock.state, ref.glyph)
                assertEquals(ApprovalOrigin.IMPORT, lock.origin, ref.glyph)
            }
            assertEquals(false, session.history.value.canUndo, "importing is not an edit")
        }

    @Test
    fun lockedImportedGlyphsRefuseEditsUntilUnlocked() =
        runTest {
            val ws = workspace(backgroundScope, SharedNameStorage())
            val session = (ProjectImporter(ws).create(candidate(), root, "Fixture") as ImportOutcome.Imported).session
            val ref = GlyphRef("regular", "A")
            val refused = session.execute(MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
            assertIs<com.asoc.typewright.project.EditResult.Refused>(refused)
            assertIs<com.asoc.typewright.project.Refusal.Locked>(refused.refusal)
        }

    @Test
    fun theCurrentProjectIsClosedBeforeTheNewOneIsMadeAndItsLeaseIsReleased() =
        runTest {
            val storage = SharedNameStorage()
            val ws = workspace(backgroundScope, storage)
            val importer = ProjectImporter(ws)
            val first = (importer.create(candidate(), root, "First") as ImportOutcome.Imported).session
            val firstStore = first.store
            val second = (importer.create(candidate(), root, "Second") as ImportOutcome.Imported).session
            assertEquals(second, ws.current.value)
            assertFailsWith<IllegalStateException>("the first session is closed") { first.undo() }
            val lease = firstStore.acquireWriteLease()
            assertNotNull(lease, "the first project's write lease was released, so it can be opened again")
            lease.release()
        }

    @Test
    fun aNameClashTriesTheNextFreeName() =
        runTest {
            val storage = SharedNameStorage()
            val ws = workspace(backgroundScope, storage)
            val importer = ProjectImporter(ws)
            val one = (importer.create(candidate(), root, "Fixture") as ImportOutcome.Imported).session
            val two = (importer.create(candidate(), root, "Fixture") as ImportOutcome.Imported).session
            val three = (importer.create(candidate(), root, "Fixture") as ImportOutcome.Imported).session
            assertEquals(listOf("Fixture", "Fixture 2", "Fixture 3"), listOf(one, two, three).map { it.state.value.meta.manifest.name })
            assertEquals(3, storage.stores.size)
        }

    @Test
    fun aFailedImportPutsThePreviousProjectBack() =
        runTest {
            val storage = SharedNameStorage()
            val ws = workspace(backgroundScope, storage)
            val importer = ProjectImporter(ws)
            val first = (importer.create(candidate(), root, "First") as ImportOutcome.Imported).session
            storage.failCreateChild = true
            val outcome = importer.create(candidate(), root, "Second")
            assertIs<ImportOutcome.Failed>(outcome)
            assertTrue(outcome.restoredPrevious)
            assertTrue("disk is full" in outcome.message, outcome.message)
            val now = ws.current.value
            assertNotNull(now, "the person still has a project open")
            assertEquals("First", now.state.value.meta.manifest.name)
            assertEquals(first.store.location, now.store.location)
        }

    @Test
    fun aFailedImportWithNothingOpenLeavesNothingOpen() =
        runTest {
            val storage = SharedNameStorage().also { it.failCreateChild = true }
            val ws = workspace(backgroundScope, storage)
            val outcome = ProjectImporter(ws).create(candidate(), root, "Fixture")
            assertIs<ImportOutcome.Failed>(outcome)
            assertEquals(false, outcome.restoredPrevious)
            assertNull(ws.current.value)
        }

    @Test
    fun aFontThatReservesANameCannotBeImportedWithoutANewOne() =
        runTest {
            val info =
                UfoFontInfo(
                    familyName = "Josefin Sans",
                    styleName = "Regular",
                    unitsPerEm = 1000,
                    copyright = "Copyright 2010 The Josefin Sans Project Authors, with Reserved Font Name \"Josefin Sans\".",
                    openTypeNameLicense = "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
                    postscriptFontName = "JosefinSans-Regular",
                )
            val ws = workspace(backgroundScope, SharedNameStorage())
            val importer = ProjectImporter(ws)
            val refused = importer.create(candidate(info), root, "Josefin Sans")
            assertIs<ImportOutcome.Failed>(refused)
            assertTrue("new family name is required" in refused.message, refused.message)

            val renamed = importer.create(candidate(info), root, "Lumen Script", newFamily = "Lumen Script")
            assertIs<ImportOutcome.Imported>(renamed)
            val font =
                renamed.session.state.value.font.masters
                    .single()
                    .ufo.fontInfo
            assertEquals("Lumen Script", font.familyName)
            assertEquals("LumenScript-Regular", font.postscriptFontName)
            assertEquals(emptyList(), nameTableKeysMentioning(font, listOf("Josefin Sans")))
            assertEquals(false, renamed.session.history.value.canUndo, "the project starts renamed; renaming is not an undo step")
            assertTrue("Reserved Font Name" in font.copyright.orEmpty(), "the copyright line is kept as it was")
        }

    @Test
    fun theDeclaredStyleClassIsRecordedOnlyWhenGiven() =
        runTest {
            val ws = workspace(backgroundScope, SharedNameStorage())
            val session =
                (
                    ProjectImporter(
                        ws,
                    ).create(candidate(), root, "Fixture", declaredStyleClass = "sans-geometric") as ImportOutcome.Imported
                ).session
            assertEquals(
                "sans-geometric",
                session.state.value.meta.manifest.brief.styleClass.declared,
            )
        }

    // ---- Adoption --------------------------------------------------------------------------------

    private fun ufoStore(
        parent: String,
        ufo: UfoProject,
        folder: String = "Fixture-Regular.ufo",
        extra: Map<String, String> = emptyMap(),
    ): InMemoryProjectStore {
        val files = mutableMapOf<String, ByteArray>()
        for ((path, text) in writeUfoProject(ufo)) files["$folder/$path"] = text.encodeToByteArray()
        for ((path, text) in extra) files[path] = text.encodeToByteArray()
        return InMemoryProjectStore(parent, files)
    }

    @Test
    fun adoptingAUfoWritesOnlyTheProjectFilesAndNeverTouchesTheUfo() =
        runTest {
            val storage = SharedNameStorage()
            val ufo = Fixtures.ufoProject(listOf("A", "B", "n"))
            val store =
                ufoStore(
                    "fonts",
                    ufo,
                    extra =
                        mapOf(
                            "README.md" to "# my fonts\n",
                            "Other-Bold.ufo/metainfo.plist" to "<plist/>",
                            "OFL.txt" to "licence text",
                        ),
                )
            storage.stores[store.location] = store
            val before = store.list().associateWith { store.read(it)!!.decodeToString() }

            val ws = workspace(backgroundScope, storage)
            val info = ufo.fontInfo
            val audit = auditLicence("Fixture-Regular.ufo", info, AuditSource.UFO)
            val candidate =
                ImportCandidate(
                    "Fixture-Regular.ufo",
                    readUfoProject(
                        before.filterKeys { it.startsWith("Fixture-Regular.ufo/") }.mapKeys { it.key.removePrefix("Fixture-Regular.ufo/") },
                    ),
                    audit,
                    reportFor(ImportFormat.UFO, ufo, emptyList()),
                    ufoFolder = "Fixture-Regular.ufo",
                )
            val outcome = ProjectImporter(ws).adopt(candidate, store.location, "Fixture fonts")
            assertIs<ImportOutcome.Imported>(outcome)

            val after = store.list()
            for ((path, text) in before) assertEquals(text, store.read(path)!!.decodeToString(), "$path is unchanged")
            val added = after.filterNot { it in before }
            assertTrue("typewright.json" in added)
            assertTrue(added.any { it.startsWith("locks/regular/") }, added.toString())
            assertTrue(
                added.none { it.startsWith("Fixture-Regular.ufo/") || it.startsWith("Other-Bold.ufo/") },
                "no UFO file is written: $added",
            )
            assertEquals("# my fonts\n", store.read("README.md")!!.decodeToString())

            val session = outcome.session
            assertEquals("Fixture fonts", session.state.value.meta.manifest.name)
            assertEquals(3, session.state.value.font.locks.size)
            assertTrue(
                session.state.value.font.locks.values
                    .all { it.state == LockState.LOCKED && it.origin == ApprovalOrigin.IMPORT },
            )
            assertEquals(
                "Fixture-Regular.ufo",
                session.state.value.font.masters
                    .single()
                    .path,
            )
            assertTrue(session.openReport.externalEdits.isEmpty(), "adoption must not read as an external edit")
        }

    @Test
    fun aFolderThatAlreadyHoldsAProjectIsNotAdoptedAgain() =
        runTest {
            val storage = SharedNameStorage()
            val ws = workspace(backgroundScope, storage)
            val ufo = Fixtures.ufoProject()
            val store = ufoStore("fonts", ufo, extra = mapOf("typewright.json" to "{}"))
            storage.stores[store.location] = store
            val candidate = candidateFor(ufo)
            val outcome = ProjectImporter(ws).adopt(candidate, store.location, "x")
            assertIs<ImportOutcome.Failed>(outcome)
            assertTrue("already holds a Typewright project" in outcome.message, outcome.message)
        }

    @Test
    fun aMissingFolderFailsWithoutThrowing() =
        runTest {
            val ws = workspace(backgroundScope, SharedNameStorage())
            val outcome = ProjectImporter(ws).adopt(candidateFor(Fixtures.ufoProject()), ProjectLocation.InMemory("nowhere"), "x")
            assertIs<ImportOutcome.Failed>(outcome)
        }

    @Test
    fun adoptingARepeatedlyOpenedFolderReopensThroughTheNormalOpenPath() =
        runTest {
            val storage = SharedNameStorage()
            val ws = workspace(backgroundScope, storage)
            val ufo = Fixtures.ufoProject()
            val store = ufoStore("fonts", ufo)
            storage.stores[store.location] = store
            val adopted = (ProjectImporter(ws).adopt(candidateFor(ufo), store.location, "Fixture") as ImportOutcome.Imported).session
            ws.closeCurrent()
            val reopened = ws.open(store.location)
            assertIs<OpenResult.Opened>(reopened)
            assertEquals(adopted.state.value.font.locks.keys, reopened.session.state.value.font.locks.keys)
        }

    private fun candidateFor(ufo: UfoProject): ImportCandidate =
        ImportCandidate(
            "Fixture-Regular.ufo",
            readUfoProject(writeUfoProject(ufo)),
            auditLicence("Fixture-Regular.ufo", ufo.fontInfo, AuditSource.UFO),
            reportFor(ImportFormat.UFO, ufo, emptyList()),
            ufoFolder = "Fixture-Regular.ufo",
        )
}
