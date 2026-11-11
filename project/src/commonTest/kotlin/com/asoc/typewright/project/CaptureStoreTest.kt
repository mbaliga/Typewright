// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.project.capture.CaptureKind
import com.asoc.typewright.project.capture.CaptureManifest
import com.asoc.typewright.project.capture.CaptureManifestCodec
import com.asoc.typewright.project.capture.CaptureRecord
import com.asoc.typewright.project.capture.CaptureRelabel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Captured sheets persist in `captures/` and come back when the project is closed and opened again. */
class CaptureStoreTest {
    private fun spec() =
        NewProjectSpec(
            name = "Fixture Project",
            brief = Fixtures.manifest().brief,
            scripts = listOf("Latn"),
            masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = Fixtures.ufoProject())),
        )

    private val photo = byteArrayOf(1, 2, 3, 4, 5, -1, -2)

    private fun record(
        id: String = "cap-1",
        suffix: String = ".jpg",
    ) = CaptureRecord(id, CaptureKind.PHOTO, "Photo", "captures/$id$suffix")

    @Test
    fun aCaptureIsWrittenAndComesBackAfterReopening() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = (ProjectSession.create(store, spec(), Fixtures.env(backgroundScope)) as CreateResult.Created).session
            val relabelled = record().copy(relabels = listOf(CaptureRelabel(11, "l")))
            assertIs<EditResult.Applied>(session.update(MetaChange.AddCapture(relabelled, photo)))
            session.flush()
            session.close()

            val files = store.snapshot().files
            assertContentEquals(photo, files.getValue("captures/cap-1.jpg"))
            assertTrue("captures/manifest.json" in files)

            val reopened = (ProjectSession.open(store, Fixtures.env(backgroundScope)) as OpenResult.Opened).session
            assertEquals(listOf(relabelled), reopened.state.value.meta.captures.records)
            // The stored picture is read back too, so a later save keeps it rather than dropping it.
            reopened.update(MetaChange.UpdateCapture(relabelled.copy(relabels = emptyList())))
            reopened.flush()
            assertContentEquals(photo, store.snapshot().files.getValue("captures/cap-1.jpg"))
        }

    @Test
    fun removingACaptureDeletesItsFileAndTheManifest() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = (ProjectSession.create(store, spec(), Fixtures.env(backgroundScope)) as CreateResult.Created).session
            session.update(MetaChange.AddCapture(record(), photo))
            session.flush()
            assertIs<EditResult.Applied>(session.update(MetaChange.RemoveCapture("cap-1")))
            session.flush()
            val files = store.snapshot().files
            assertFalse("captures/cap-1.jpg" in files)
            assertFalse("captures/manifest.json" in files)
            assertEquals(EditResult.NoChange, session.update(MetaChange.RemoveCapture("cap-1")))
        }

    @Test
    fun aCaptureStoredOutsideItsOwnPathOrWithAnUnknownSuffixIsRefused() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = (ProjectSession.create(store, spec(), Fixtures.env(backgroundScope)) as CreateResult.Created).session
            val elsewhere = record().copy(imagePath = "scrapbook/cap-1.jpg")
            assertIs<EditResult.Refused>(session.update(MetaChange.AddCapture(elsewhere, photo)))
            assertIs<EditResult.Refused>(session.update(MetaChange.AddCapture(record(suffix = ".exe"), photo)))
            session.update(MetaChange.AddCapture(record(), photo))
            assertEquals(EditResult.Refused(Refusal.NameClash("cap-1")), session.update(MetaChange.AddCapture(record(), photo)))
            assertIs<EditResult.Refused>(session.update(MetaChange.UpdateCapture(record("cap-9"))))
        }

    @Test
    fun theFormatOwnsCapturesButNothingElseUnderThatFolder() {
        assertTrue(ProjectLayout.isFormatPath("captures/manifest.json"))
        assertTrue(ProjectLayout.isFormatPath("captures/cap-3.svg"))
        assertFalse(ProjectLayout.isFormatPath("captures/notes.txt"))
        assertFalse(ProjectLayout.isFormatPath("captures/deeper/cap-3.png"))
    }

    @Test
    fun idsFollowTheHighestExistingNumberAndTheManifestRoundTripsByteForByte() {
        val manifest = CaptureManifest(listOf(record("cap-2"), record("cap-7", ".png").copy(batch = "b1", glyph = "T")))
        assertEquals("cap-8", manifest.nextId())
        assertEquals("cap-1", CaptureManifest().nextId())
        val text = CaptureManifestCodec.encode(manifest)
        assertEquals(manifest, CaptureManifestCodec.decode(text))
        assertEquals(text, CaptureManifestCodec.encode(CaptureManifestCodec.decode(text)))
        assertNotNull(Regex("\"format_version\": 1").find(text))
        assertTrue(text.endsWith("}\n"))
    }
}
