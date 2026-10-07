// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `typewright.json`'s codec: encode/decode round trip, key order and determinism. Runs on the JVM and Wasm. */
class ProjectManifestCodecTest {
    private fun lockedState(): ProjectState {
        val glyphA = Fixtures.glyph("A")
        val master = Fixtures.master(ufo = Fixtures.ufoProject(listOf("A", "B")))
        val approved = glyphA
        val episode =
            UnlockEpisode(
                at = ProjectTimestamps.parse("2026-09-25T11:02:41Z"),
                cause = UnlockCause.USER,
                reason = null,
                relockedAt = ProjectTimestamps.parse("2026-09-25T11:05:00Z"),
                frozenDiff = "--- a\n+++ b\n",
            )
        val lock =
            GlyphLock(LockState.LOCKED, ApprovalOrigin.TRACE, ProjectTimestamps.parse("2026-09-25T10:20:00Z"), approved, listOf(episode))
        val font = FontState(listOf(master), mapOf(GlyphRef("regular", "A") to lock))
        val manifest =
            Fixtures
                .manifest(workbook = mapOf("latn" to WorkbookRecord(setOf(1, 2, 3))))
                .copy(comparisonFonts = listOf(ComparisonFont("Jost", ComparisonSource.BUNDLED, slug = "jost", path = null)))
        return Fixtures.projectState(font, manifest)
    }

    @Test
    fun encodesTheKnownKeysInSchemaOrder() {
        val text = ProjectManifestCodec.encode(Fixtures.projectState(), lockStems = emptyMap())
        val keys = Regex("""^  "([a-z_]+)":""", RegexOption.MULTILINE).findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "format",
                "format_version",
                "name",
                "created",
                "brief",
                "scripts",
                "masters",
                "locks",
                "workbook",
                "comparison_fonts",
                "preferences",
            ),
            keys,
        )
    }

    @Test
    fun encodeThenDecodeRoundTripsTheManifestAndMasterPointers() {
        val state = lockedState()
        val stems =
            LockPaths.assignStems(
                "regular",
                state.font.locks,
                state.font.masters
                    .single()
                    .ufo.glyphs
                    .map { it.name },
                emptyMap(),
            )
        val text = ProjectManifestCodec.encode(state, stems)
        val decoded = ProjectManifestCodec.decode(text)

        assertEquals(state.meta.manifest, decoded.manifest)
        assertEquals(listOf(DecodedMaster("regular", "Fixture-Regular.ufo", "Regular", true)), decoded.masters)
        val ref = GlyphRef("regular", "A")
        val decodedLock = decoded.locks.getValue(ref)
        assertEquals(LockState.LOCKED, decodedLock.state)
        assertEquals(ApprovalOrigin.TRACE, decodedLock.origin)
        assertEquals("locks/regular/A_.glif", decodedLock.approvedPath)
        assertEquals(1, decodedLock.episodes.size)
        assertEquals(ProjectTimestamps.parse("2026-09-25T11:05:00Z"), decodedLock.episodes.single().relockedAt)
    }

    @Test
    fun anUnknownFormatVersionThrowsNewerFormatException() {
        val text =
            ProjectManifestCodec
                .encode(
                    Fixtures.projectState(),
                    emptyMap(),
                ).replace("\"format_version\": 1", "\"format_version\": 2")
        val error = assertFailsWith<NewerFormatException> { ProjectManifestCodec.decode(text) }
        assertEquals(2, error.found)
    }

    @Test
    fun unknownTopLevelKeysAreKeptVerbatimAfterTheKnownOnes() {
        val text = ProjectManifestCodec.encode(Fixtures.projectState(), emptyMap())
        val original = ProjectJson.json.parseToJsonElement(text) as JsonObject
        val capture = buildJsonObject { putJsonArray("sheets") {} }
        val extended = JsonObject(original + ("capture" to capture))
        val decoded = ProjectManifestCodec.decode(ProjectJson.json.encodeToString(JsonObject.serializer(), extended))
        assertEquals(capture, decoded.unknownKeys["capture"])
    }

    @Test
    fun encodingIsDeterministicForTheSameState() {
        val state = lockedState()
        val stems =
            LockPaths.assignStems(
                "regular",
                state.font.locks,
                state.font.masters
                    .single()
                    .ufo.glyphs
                    .map { it.name },
                emptyMap(),
            )
        assertEquals(ProjectManifestCodec.encode(state, stems), ProjectManifestCodec.encode(state, stems))
    }

    @Test
    fun endsWithATrailingNewlineAndTwoSpaceIndent() {
        val text = ProjectManifestCodec.encode(Fixtures.projectState(), emptyMap())
        assertTrue(text.endsWith("}\n"))
        assertTrue(text.lines().any { it.startsWith("  \"name\"") })
    }

    @Test
    fun theBriefEthosRoundTrips() {
        val ethos =
            Ethos(
                genre = "display-artdeco",
                uses = listOf("watch-face"),
                useOptions = mapOf("watch-face.aod" to "yes", "watch-face.date" to "digits"),
                feelings = listOf("futuristic", "calm"),
                references = listOf("Michroma", "Poiret One"),
                levels = mapOf("contrast" to "even", "width" to "narrow"),
                signatures = listOf("aForm"),
                answered = listOf("door.style", "contrast", "width"),
                wordmark = null,
            )
        val base = Fixtures.projectState()
        val state =
            base.copy(
                meta =
                    base.meta.copy(
                        manifest =
                            base.meta.manifest.copy(
                                brief =
                                    base.meta.manifest.brief
                                        .copy(ethos = ethos),
                            ),
                    ),
            )
        val decoded = ProjectManifestCodec.decode(ProjectManifestCodec.encode(state, emptyMap()))
        assertEquals(ethos, decoded.manifest.brief.ethos)
        assertEquals(state.meta.manifest, decoded.manifest)
    }

    @Test
    fun aBriefWithNoEthosWritesNoEthosKey() {
        // A project that never visited the Brief section keeps the JSON it had before the ethos existed.
        val text = ProjectManifestCodec.encode(Fixtures.projectState(), emptyMap())
        assertTrue("\"ethos\"" !in text)
        assertEquals(
            null,
            ProjectManifestCodec
                .decode(text)
                .manifest.brief.ethos,
        )
    }

    @Test
    fun anEthosWithMissingFieldsReadsThemAsEmpty() {
        val text = ProjectManifestCodec.encode(Fixtures.projectState(), emptyMap())
        val original = ProjectJson.json.parseToJsonElement(text) as JsonObject
        val brief = original["brief"] as JsonObject
        val sparse = JsonObject(brief + ("ethos" to buildJsonObject { put("genre", "slab") }))
        val decoded =
            ProjectManifestCodec.decode(
                ProjectJson.json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(original + ("brief" to sparse)),
                ),
            )
        assertEquals(Ethos(genre = "slab"), decoded.manifest.brief.ethos)
    }
}
