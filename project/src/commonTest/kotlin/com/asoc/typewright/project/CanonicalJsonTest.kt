// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/** These run on the JVM and on Wasm; the same expected strings prove the bytes agree across platforms. */
class CanonicalJsonTest {
    @Test
    fun writesTwoSpaceIndentedJsonInElementOrderWithATrailingNewline() {
        val element =
            buildJsonObject {
                put("format", "typewright-project")
                put("format_version", 1)
                putJsonObject("empty_object") {}
                putJsonArray("empty_array") {}
                put("nothing", JsonNull)
                putJsonArray("list") {
                    add("Latn")
                    add(true)
                    add(buildJsonObject { put("id", "regular") })
                }
                put("zeta_before_alpha", 2)
                put("alpha", 3)
            }
        val expected =
            "{\n" +
                "  \"format\": \"typewright-project\",\n" +
                "  \"format_version\": 1,\n" +
                "  \"empty_object\": {},\n" +
                "  \"empty_array\": [],\n" +
                "  \"nothing\": null,\n" +
                "  \"list\": [\n" +
                "    \"Latn\",\n" +
                "    true,\n" +
                "    {\n" +
                "      \"id\": \"regular\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"zeta_before_alpha\": 2,\n" +
                "  \"alpha\": 3\n" +
                "}\n"
        assertEquals(expected, CanonicalJson.encode(element))
        assertContentEquals(expected.encodeToByteArray(), CanonicalJson.encodeToBytes(element))
    }

    @Test
    fun escapesOnlyWhatJsonRequiresAndKeepsUnicodeAsUtf8() {
        val text = "quote \" backslash \\ newline \n tab \t cr \r bell \u0007 middle dot · Devanagari क"
        val encoded = CanonicalJson.encode(JsonPrimitive(text))
        assertEquals("\"quote \\\" backslash \\\\ newline \\n tab \\t cr \\r bell \\u0007 middle dot · Devanagari क\"\n", encoded)
        assertEquals(text, Json.parseToJsonElement(encoded).let { (it as JsonPrimitive).content })
        val bytes = CanonicalJson.encodeToBytes(JsonPrimitive("·"))
        assertContentEquals(byteArrayOf('"'.code.toByte(), 0xC2.toByte(), 0xB7.toByte(), '"'.code.toByte(), '\n'.code.toByte()), bytes)
    }

    @Test
    fun anUnpairedSurrogateIsEscapedSoItsBytesAgreeAndItReadsBack() {
        // A caption cut in the middle of an emoji: the high half stays, the low half is gone.
        val cut = "a\uD83Db"
        val bytes = CanonicalJson.encodeToBytes(JsonPrimitive(cut))
        assertEquals("\"a\\ud83db\"\n", bytes.decodeToString())
        assertContentEquals("\"a\\ud83db\"\n".encodeToByteArray(), bytes)
        assertEquals(cut, (Json.parseToJsonElement(bytes.decodeToString()) as JsonPrimitive).content)

        val strays = "\uDE00 x \uD83D\uD83D\uDE00 \uD83D\uDE00\uDE00 \uD83D"
        val encoded = CanonicalJson.encode(JsonPrimitive(strays))
        assertEquals("\"\\ude00 x \\ud83d\uD83D\uDE00 \uD83D\uDE00\\ude00 \\ud83d\"\n", encoded)
        assertEquals(strays, (Json.parseToJsonElement(encoded) as JsonPrimitive).content)
        // A proper pair is plain UTF-8: F0 9F 98 80.
        assertContentEquals(
            byteArrayOf(0x22, 0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte(), 0x22, 0x0A),
            CanonicalJson.encodeToBytes(JsonPrimitive("\uD83D\uDE00")),
        )
    }

    @Test
    fun numberLiteralsAreReadAndWrittenExactly() {
        // Kotlin/Wasm's own parser reads -0.0078772 one ulp off and its toString gives 17 digits
        // for 8.30047891231946; the canonical form must not depend on either.
        val element = Json.parseToJsonElement("""[-0.0078772, 8.300478912319459, 1e400, -1E-400, 2.50, 7]""")
        assertEquals("[\n  -0.0078772,\n  8.30047891231946,\n  1e400,\n  -0.0,\n  2.5,\n  7\n]\n", CanonicalJson.encode(element))
        assertEquals("-0.0078772\n", CanonicalJson.encode(jsonDouble(-0.0078772)))
        assertEquals("8.30047891231946\n", CanonicalJson.encode(jsonDouble(8.30047891231946)))
    }

    @Test
    fun doublesHaveOneSpellingOnEveryPlatform() {
        val cases =
            listOf(
                -1.8 to "-1.8",
                2.375 to "2.375",
                3.0 to "3.0",
                0.5 to "0.5",
                0.1 + 0.2 to "0.30000000000000004",
                1.0 / 3.0 to "0.3333333333333333",
                -40.0 to "-40.0",
                0.000001 to "0.000001",
                0.0000001 to "1.0E-7",
                1.25e-10 to "1.25E-10",
                10_000_000.0 to "10000000.0",
                123456.789 to "123456.789",
                1e20 to "100000000000000000000.0",
                1e21 to "1.0E21",
                1.7976931348623157e308 to "1.7976931348623157E308",
                2.2250738585072014e-308 to "2.2250738585072014E-308",
                0.0 to "0.0",
                -0.0 to "-0.0",
                0.5359999999999996 to "0.5359999999999996",
            )
        for ((value, expected) in cases) {
            assertEquals(expected, formatDouble(value), "formatting $value")
            assertEquals(value.toRawBits(), parseJsonDouble(expected)!!.toRawBits(), "$expected reads back as $value")
            assertEquals(expected + "\n", CanonicalJson.encode(jsonDouble(value)))
        }
        assertFailsWith<IllegalArgumentException> { formatDouble(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { formatDouble(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun integersAndForeignLiteralsKeepTheirSpelling() {
        val element = Json.parseToJsonElement("""[12345678901234567890, -7, 1e3, 2.50]""")
        assertEquals("[\n  12345678901234567890,\n  -7,\n  1000.0,\n  2.5\n]\n", CanonicalJson.encode(element))
        assertEquals("[\n  1\n]\n", CanonicalJson.encode(buildJsonArray { add(1) }))
    }

    @Test
    fun timestampsAreWholeSecondsInUtc() {
        val at = Instant.fromEpochSeconds(1_790_345_591)
        assertEquals("2026-09-25T14:13:11Z", ProjectTimestamps.format(at))
        assertEquals("20260925T141311Z", ProjectTimestamps.formatCompact(at))
        assertEquals(at, ProjectTimestamps.parse("2026-09-25T14:13:11Z"))
        assertEquals("1970-01-01T00:00:00Z", ProjectTimestamps.format(Instant.fromEpochSeconds(0)))
        assertFailsWith<IllegalArgumentException> { ProjectTimestamps.format(Instant.fromEpochMilliseconds(1_790_345_591_500)) }
        for (bad in listOf("2026-09-25T14:13:11.000Z", "2026-09-25T14:13:11+00:00", "2026-09-25 14:13:11Z", "2026-09-25T14:13Z", "")) {
            assertFailsWith<IllegalArgumentException>(bad) { ProjectTimestamps.parse(bad) }
        }
    }

    @Test
    fun crc32MatchesTheStandardCheckValues() {
        assertEquals(0, Crc32.of(ByteArray(0)))
        assertEquals(0xCBF43926.toInt(), Crc32.of("123456789".encodeToByteArray()))
        assertEquals(0x414FA339, Crc32.of("The quick brown fox jumps over the lazy dog".encodeToByteArray()))
    }
}
