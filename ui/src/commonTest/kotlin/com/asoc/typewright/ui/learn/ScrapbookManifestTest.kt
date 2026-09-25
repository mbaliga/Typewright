// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Portable (JVM + Wasm) unit tests for [ScrapbookManifest]'s own JSON round trip
 * ([ScrapbookManifestCodec]) and the deterministic pinned-look rotation
 * ([stablePinRotationDegrees]) -- exactly the two things this task's own instructions name for
 * real unit tests. [SampleScrabook]'s own shape (four pins, three driving) is pinned here too, so
 * a future edit to it is a deliberate, visible diff rather than a silent drift from what
 * `ScrapbookTabScreenshotTest` actually renders.
 */
class ScrapbookManifestTest {
    // ---- ScrapbookManifestCodec: encode/decode round trip ----

    @Test
    fun encodeThenDecodeRoundTripsAFullPinExactly() {
        val pin =
            ScrapbookPin(
                id = "p1",
                kind = ScrapbookPinKind.PHOTO,
                captionTitle = "Signage · Charminar",
                captionSource = "photo",
                imagePath = "scrapbook/signage-charminar.jpg",
                noteText = null,
                rotationDegrees = -2.375,
                drivesDesign = true,
            )
        val manifest = ScrapbookManifest(pins = listOf(pin))

        val decoded = ScrapbookManifestCodec.decode(ScrapbookManifestCodec.encode(manifest))

        assertEquals(manifest, decoded)
    }

    @Test
    fun encodeThenDecodeRoundTripsANotePinWithNoImagePath() {
        val pin =
            ScrapbookPin(
                id = "p2",
                kind = ScrapbookPinKind.NOTE,
                captionTitle = "Reflection · Task 3",
                captionSource = "note",
                noteText = "Round ends everywhere, or nowhere. Decide before the s.",
                rotationDegrees = 1.5,
                drivesDesign = false,
            )
        val manifest = ScrapbookManifest(pins = listOf(pin))

        val decoded = ScrapbookManifestCodec.decode(ScrapbookManifestCodec.encode(manifest))

        assertEquals(manifest, decoded)
        assertNull(decoded.pins.single().imagePath)
    }

    @Test
    fun encodeThenDecodeRoundTripsAnEmptyManifest() {
        val manifest = ScrapbookManifest(pins = emptyList())
        assertEquals(manifest, ScrapbookManifestCodec.decode(ScrapbookManifestCodec.encode(manifest)))
    }

    @Test
    fun encodeThenDecodeRoundTripsTheRealSampleManifest() {
        val decoded = ScrapbookManifestCodec.decode(ScrapbookManifestCodec.encode(SampleScrapbook.MANIFEST))
        assertEquals(SampleScrapbook.MANIFEST, decoded)
    }

    @Test
    fun decodeUsesSnakeCaseFieldNamesMatchingDataLearnFacesManifestJsonsOwnConvention() {
        val json =
            """
            {"pins":[{"id":"p3","kind":"note","caption_title":"Title","caption_source":"note",
            "note_text":"hello","rotation_degrees":3.0,"drives_design":true}]}
            """.trimIndent()

        val manifest = ScrapbookManifestCodec.decode(json)

        val pin = manifest.pins.single()
        assertEquals("p3", pin.id)
        assertEquals(ScrapbookPinKind.NOTE, pin.kind)
        assertEquals("Title", pin.captionTitle)
        assertEquals("hello", pin.noteText)
        assertEquals(3.0, pin.rotationDegrees)
        assertTrue(pin.drivesDesign)
    }

    @Test
    fun decodeIgnoresUnknownFieldsForForwardCompatibility() {
        val json =
            """{"pins":[{"id":"p4","kind":"photo","caption_title":"T","caption_source":"S","future_field":"new"}],"future_top_level":1}"""
        val manifest = ScrapbookManifestCodec.decode(json)
        assertEquals("p4", manifest.pins.single().id)
    }

    // ---- ScrapbookManifest.drivingCount ----

    @Test
    fun drivingCountCountsOnlyPinsMarkedTrue() {
        val manifest =
            ScrapbookManifest(
                pins =
                    listOf(
                        ScrapbookPin(
                            id = "a",
                            kind = ScrapbookPinKind.PHOTO,
                            captionTitle = "A",
                            captionSource = "photo",
                            drivesDesign = true,
                        ),
                        ScrapbookPin(
                            id = "b",
                            kind = ScrapbookPinKind.PHOTO,
                            captionTitle = "B",
                            captionSource = "photo",
                            drivesDesign = false,
                        ),
                        ScrapbookPin(
                            id = "c",
                            kind = ScrapbookPinKind.NOTE,
                            captionTitle = "C",
                            captionSource = "note",
                            drivesDesign = true,
                        ),
                    ),
            )
        assertEquals(2, manifest.drivingCount)
    }

    // ---- SampleScrapbook: the real fixture ScrapbookTab renders ----

    @Test
    fun sampleScrapbookHasFourPinsThreeDrivingTheDesign() {
        assertEquals(4, SampleScrapbook.MANIFEST.pins.size)
        assertEquals(3, SampleScrapbook.MANIFEST.drivingCount)
        assertEquals("4 pins · 3 driving the design", pinCountLabel(SampleScrapbook.MANIFEST))
    }

    @Test
    fun sampleScrapbookHasExactlyOneNotePinAndItCarriesNoteText() {
        val notePins = SampleScrapbook.MANIFEST.pins.filter { it.kind == ScrapbookPinKind.NOTE }
        assertEquals(1, notePins.size)
        assertTrue(notePins.single().noteText!!.isNotBlank())
    }

    @Test
    fun sampleScrapbookPinsAllHaveDistinctIds() {
        val ids = SampleScrapbook.MANIFEST.pins.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
    }

    // ---- stablePinRotationDegrees: deterministic, bounded, not a constant function ----

    @Test
    fun sameIdAlwaysProducesTheSameRotation() {
        val a = stablePinRotationDegrees("signage-charminar")
        val b = stablePinRotationDegrees("signage-charminar")
        assertEquals(a, b)
    }

    @Test
    fun rotationStaysWithinTheDocumentedRange() {
        val ids = listOf("a", "b", "c", "pin-1", "pin-2", "signage-charminar", "1912-primer-scan", "", "a very long pin id indeed")
        for (id in ids) {
            val degrees = stablePinRotationDegrees(id)
            assertTrue(
                degrees in -PIN_ROTATION_RANGE_DEGREES..PIN_ROTATION_RANGE_DEGREES,
                "expected $id's rotation $degrees within +/-$PIN_ROTATION_RANGE_DEGREES",
            )
        }
    }

    @Test
    fun differentIdsUsuallyProduceDifferentRotations() {
        // Not a hash-collision-freedom proof (impossible for a small hash into a bounded range) --
        // just proof this is not secretly a constant function.
        val degrees = (1..20).map { stablePinRotationDegrees("pin-$it") }
        assertTrue(degrees.toSet().size > 1, "expected some variation across ids, got a single repeated value $degrees")
    }

    @Test
    fun rotationIsNotTheSameForEveryRealSamplePin() {
        val degrees = SampleScrapbook.MANIFEST.pins.map { it.rotationDegrees }
        assertTrue(degrees.toSet().size > 1, "expected the four real sample pins to carry more than one distinct rotation, got $degrees")
    }

    @Test
    fun stableHashCodeIsDeterministicAndNonNegative() {
        assertEquals(stableHashCode("same"), stableHashCode("same"))
        assertNotEquals(stableHashCode("left"), stableHashCode("right"))
        assertTrue(stableHashCode("") >= 0)
        assertTrue(stableHashCode("anything at all, long or short") >= 0)
    }

    // ---- patternVariantForPinId: same determinism guarantee, for the photo-pin pattern pick ----

    @Test
    fun patternVariantIsDeterministicPerId() {
        assertEquals(patternVariantForPinId("signage-charminar"), patternVariantForPinId("signage-charminar"))
    }
}
