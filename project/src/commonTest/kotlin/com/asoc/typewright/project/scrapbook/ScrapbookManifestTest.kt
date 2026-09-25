// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.scrapbook

import com.asoc.typewright.project.NewerFormatException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The scrapbook manifest's JSON round trip and bytes, and the stable pinned-look rotation. Runs on the JVM and Wasm. */
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
    fun encodesTheDocumentedBytesWithEveryKeyAndExplicitNulls() {
        val manifest =
            ScrapbookManifest(
                pins =
                    listOf(
                        ScrapbookPin(
                            id = "signage-charminar",
                            kind = ScrapbookPinKind.PHOTO,
                            captionTitle = "Signage · Charminar",
                            captionSource = "photo",
                            imagePath = "scrapbook/signage-charminar.jpg",
                            rotationDegrees = -1.8,
                            drivesDesign = true,
                        ),
                        ScrapbookPin(
                            id = "pin-7",
                            kind = ScrapbookPinKind.NOTE,
                            captionTitle = "Note",
                            captionSource = "note",
                            noteText = "Keep the \"a\" single-storey.",
                            rotationDegrees = stablePinRotationDegrees("pin-7"),
                        ),
                    ),
            )
        val expected =
            """
            {
              "format_version": 1,
              "pins": [
                {
                  "id": "signage-charminar",
                  "kind": "photo",
                  "caption_title": "Signage · Charminar",
                  "caption_source": "photo",
                  "image_path": "scrapbook/signage-charminar.jpg",
                  "note_text": null,
                  "rotation_degrees": -1.8,
                  "drives_design": true
                },
                {
                  "id": "pin-7",
                  "kind": "note",
                  "caption_title": "Note",
                  "caption_source": "note",
                  "image_path": null,
                  "note_text": "Keep the \"a\" single-storey.",
                  "rotation_degrees": 0.5359999999999996,
                  "drives_design": false
                }
              ]
            }
            """.trimIndent() + "\n"
        assertEquals(expected, ScrapbookManifestCodec.encode(manifest))
        assertEquals("{\n  \"format_version\": 1,\n  \"pins\": []\n}\n", ScrapbookManifestCodec.encode(ScrapbookManifest()))
    }

    @Test
    fun decodeUsesSnakeCaseFieldNames() {
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
    fun aManifestWithoutFormatVersionReadsAsVersionOneAndIsWrittenWithIt() {
        val manifest = ScrapbookManifestCodec.decode("""{"pins":[]}""")
        assertEquals(ScrapbookManifest(), manifest)
        assertEquals(ScrapbookManifest(), ScrapbookManifestCodec.decode("""{"format_version":1}"""))
        assertEquals("{\n  \"format_version\": 1,\n  \"pins\": []\n}\n", ScrapbookManifestCodec.encode(manifest))
    }

    @Test
    fun theConstructorTakesPinsFirstAsTheUiModelDid() {
        val pins = listOf(ScrapbookPin(id = "a", kind = ScrapbookPinKind.NOTE, captionTitle = "A", captionSource = "note"))
        // Positional, as ScrapbookTab calls it.
        assertEquals(pins, ScrapbookManifest(pins).pins)
    }

    @Test
    fun handEditedRotationsKeepTheirValueAndSpellingOnEveryPlatform() {
        // Values whose platform spellings or readings differ between the JVM and Wasm.
        for ((written, bits) in listOf(
            "-0.0078772" to -4647677518435677841L,
            "0.05664342901411119" to 4588323999461424458L,
            "-4.705731567235238" to -4606387835694211354L,
            "8.30047891231946" to 4620862372271823222L,
            "3" to 4613937818241073152L,
        )) {
            val text =
                """{"format_version":1,"pins":[{"id":"p","kind":"note","caption_title":"T","caption_source":"note",""" +
                    """"rotation_degrees":$written}]}"""
            val pin = ScrapbookManifestCodec.decode(text).pins.single()
            assertEquals(bits, pin.rotationDegrees.toRawBits(), "reading $written")
            val canonical = if (written == "3") "3.0" else written
            assertTrue(
                ScrapbookManifestCodec.encode(ScrapbookManifest(listOf(pin))).contains("\"rotation_degrees\": $canonical,\n"),
                "writing $written",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ScrapbookManifestCodec.decode(
                """{"pins":[{"id":"p","kind":"note","caption_title":"T","caption_source":"n","rotation_degrees":"1.5"}]}""",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ScrapbookManifestCodec.decode(
                """{"pins":[{"id":"p","kind":"note","caption_title":"T","caption_source":"n","rotation_degrees":1e999}]}""",
            )
        }
    }

    @Test
    fun aCaptionCutInsideAnEmojiRoundTripsExactly() {
        val pin = ScrapbookPin(id = "p", kind = ScrapbookPinKind.NOTE, captionTitle = "Signage \uD83D", captionSource = "note")
        val text = ScrapbookManifestCodec.encode(ScrapbookManifest(listOf(pin)))
        assertTrue(text.contains("\"caption_title\": \"Signage \\ud83d\","), text)
        assertEquals(pin, ScrapbookManifestCodec.decode(text).pins.single())
    }

    @Test
    fun aNewerFormatVersionIsRefusedWithItsNumber() {
        val error = assertFailsWith<NewerFormatException> { ScrapbookManifestCodec.decode("""{"format_version":2,"pins":[]}""") }
        assertEquals(2, error.found)
        assertEquals(1, error.supported)
        assertFailsWith<IllegalArgumentException> { ScrapbookManifestCodec.decode("""{"format_version":"one","pins":[]}""") }
        assertFailsWith<IllegalArgumentException> { ScrapbookManifestCodec.decode("""[]""") }
        assertFailsWith<IllegalArgumentException> { ScrapbookManifestCodec.decode("""{"pins":[{"id":"x","kind":"video"}]}""") }
        assertFailsWith<IllegalArgumentException> { ScrapbookManifestCodec.decode("not json") }
    }

    @Test
    fun decodeIgnoresUnknownFieldsForForwardCompatibility() {
        val json =
            """{"pins":[{"id":"p4","kind":"photo","caption_title":"T","caption_source":"S","future_field":"new"}],"future_top_level":1}"""
        val manifest = ScrapbookManifestCodec.decode(json)
        assertEquals("p4", manifest.pins.single().id)
    }

    // ---- ScrapbookManifest.drivingCount and nextPinId ----

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

    @Test
    fun newPinIdsAreOneMoreThanTheHighestPinNumber() {
        fun pin(id: String) = ScrapbookPin(id = id, kind = ScrapbookPinKind.NOTE, captionTitle = "", captionSource = "note")
        assertEquals("pin-1", ScrapbookManifest().nextPinId())
        assertEquals("pin-1", ScrapbookManifest(pins = listOf(pin("signage-charminar"))).nextPinId())
        assertEquals("pin-8", ScrapbookManifest(pins = listOf(pin("pin-2"), pin("pin-7"), pin("pin-3"), pin("pin-x"))).nextPinId())
    }

    // ---- stablePinRotationDegrees: deterministic, bounded, not a constant function ----

    @Test
    fun sameIdAlwaysProducesTheSameRotation() {
        assertEquals(stablePinRotationDegrees("signage-charminar"), stablePinRotationDegrees("signage-charminar"))
        assertEquals(0.5599999999999996, stablePinRotationDegrees("signage-charminar"))
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
        // Not a proof of no collisions (impossible for a small hash into a bounded range), just
        // proof this is not a constant function.
        val degrees = (1..20).map { stablePinRotationDegrees("pin-$it") }
        assertTrue(degrees.toSet().size > 1, "expected some variation across ids, got a single repeated value $degrees")
    }

    @Test
    fun stablePinHashIsDeterministicNonNegativeAndPortable() {
        assertEquals(stablePinHash("same"), stablePinHash("same"))
        assertNotEquals(stablePinHash("left"), stablePinHash("right"))
        assertTrue(stablePinHash("") >= 0)
        assertTrue(stablePinHash("anything at all, long or short") >= 0)
        assertEquals(106669567, stablePinHash("pin-7"))
        assertEquals(2061565570, stablePinHash("signage-charminar"))
    }
}
