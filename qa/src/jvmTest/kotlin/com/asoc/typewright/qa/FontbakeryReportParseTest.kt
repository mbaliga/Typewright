// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fontbakery's `--json` report read into outcomes. The sample is five real checks from a real run of
 * `check-googlefonts` (one of each result Fontbakery printed), trimmed to the fields the reader uses.
 */
class FontbakeryReportParseTest {
    private val report =
        """
        {
          "result": {"FAIL": 1, "INFO": 1, "PASS": 1, "SKIP": 1, "WARN": 1},
          "sections": [
            {"key": ["<Section: OpenType Specification Checks>"], "checks": [
              {"description": "Ensure VFs have 'ital' STAT axis.",
               "key": ["<Section: OpenType Specification Checks>", "<FontBakeryCheck:com.google.fonts/check/italic_axis_in_stat>", []],
               "logs": [{"message": {"code": null, "message": "OK"}, "status": "PASS"}], "result": "PASS"},
              {"description": "Does GPOS table have kerning information? This check skips monospaced fonts as defined by post.isFixedPitch value",
               "key": ["<Section: OpenType Specification Checks>", "<FontBakeryCheck:com.google.fonts/check/gpos_kerning_info>", [["font", 0]]],
               "logs": [{"message": {"code": "lacks-kern-info", "message": "GPOS table lacks kerning information."}, "status": "WARN"}],
               "result": "WARN"}
            ]},
            {"key": ["<Section: Universal Profile Checks>"], "checks": [
              {"description": "Ensure 'smcp' (small caps) lookups are defined before ligature lookups in the 'GSUB' table.",
               "key": ["<Section: Universal Profile Checks>", "<FontBakeryCheck:com.google.fonts/check/gsub/smallcaps_before_ligatures>", [["font", 0]]],
               "logs": [{"message": {"code": null, "message": "Font lacks a 'GSUB' table."}, "status": "SKIP"}], "result": "SKIP"},
              {"description": "List all superfamily filepaths",
               "key": ["<Section: Superfamily Checks>", "<FontBakeryCheck:com.google.fonts/check/superfamily/list>", [["font", 0]]],
               "logs": [{"message": {"code": "family-path", "message": "out-q"}, "status": "INFO"}], "result": "INFO"},
              {"description": "Checking file is named canonically.",
               "key": ["<Section: Font File Checks>", "<FontBakeryCheck:com.google.fonts/check/canonical_filename>", [["font", 0]]],
               "logs": [{"message": {"code": "bad-filename", "message": "Expected \"ExpSeed-Regular.ttf. Got q.ttf."}, "status": "FAIL"}],
               "result": "FAIL"}
            ]}
          ]
        }
        """.trimIndent()

    private val outcomes get() = parseFontbakeryReport(Json.parseToJsonElement(report))

    @Test
    fun everyCheckBecomesOneOutcomeInTheReportsOwnOrder() {
        assertEquals(
            listOf(
                "com.google.fonts/check/italic_axis_in_stat",
                "com.google.fonts/check/gpos_kerning_info",
                "com.google.fonts/check/gsub/smallcaps_before_ligatures",
                "com.google.fonts/check/superfamily/list",
                "com.google.fonts/check/canonical_filename",
            ),
            outcomes.map { it.id },
        )
    }

    @Test
    fun theStatusWordsMapAndFontbakerysOwnWordIsKeptWhereItDiffers() {
        val byId = outcomes.associateBy { it.id.substringAfterLast("check/") }
        assertEquals(CheckStatus.PASS, byId.getValue("italic_axis_in_stat").status)
        assertEquals(CheckStatus.WARN, byId.getValue("gpos_kerning_info").status)
        assertEquals(CheckStatus.SKIP, byId.getValue("gsub/smallcaps_before_ligatures").status)
        assertEquals(CheckStatus.FAIL, byId.getValue("canonical_filename").status)
        // INFO is a note, not a problem: it is its own status and is never counted as a warning.
        assertEquals(CheckStatus.INFO, byId.getValue("superfamily/list").status)
        assertNull(byId.getValue("superfamily/list").toolStatus, "INFO is its own status, so there is no other word to keep")
        assertNull(byId.getValue("canonical_filename").toolStatus)
        val counts = LayerOneCounts.of(outcomes)
        assertEquals(1, counts.info)
        assertEquals(1, counts.warn, "only the one real WARN is a warning; the INFO note is not folded in")
    }

    @Test
    fun theMessageIsWhatTheCheckLoggedAndTheTitleIsItsDescription() {
        val fail = outcomes.single { it.status == CheckStatus.FAIL }
        assertEquals("Expected \"ExpSeed-Regular.ttf. Got q.ttf.", fail.message)
        assertEquals("Checking file is named canonically.", fail.title)
    }

    @Test
    fun theCountsAreFontbakerysOwn() {
        val counts = LayerOneCounts.of(outcomes)
        // The report's own tally is {"FAIL": 1, "INFO": 1, "PASS": 1, "SKIP": 1, "WARN": 1}: the note is its own count.
        assertEquals(1, counts.fail)
        assertEquals(1, counts.warn)
        assertEquals(1, counts.info)
        assertEquals(1, counts.pass)
        assertEquals(1, counts.skip)
    }

    @Test
    fun aCrashedOrFatalCheckIsAFailureNeverAPass() {
        val json =
            """{"sections": [{"checks": [
              {"description": "a", "key": ["s", "<FontBakeryCheck:x/a>", []], "logs": [{"message": "it broke", "status": "ERROR"}], "result": "ERROR"},
              {"description": "b", "key": ["s", "<FontBakeryCheck:x/b>", []], "logs": [{"message": {"message": "unusable"}, "status": "FATAL"}], "result": "FATAL"},
              {"description": "c", "key": ["s", "<FontBakeryCheck:x/c>", []], "logs": [], "result": "SOMETHING-NEW"}
            ]}]}"""
        val read = parseFontbakeryReport(Json.parseToJsonElement(json))
        assertEquals(listOf(CheckStatus.FAIL, CheckStatus.FAIL, CheckStatus.SKIP), read.map { it.status })
        assertEquals(listOf("ERROR", "FATAL", "SOMETHING-NEW"), read.map { it.toolStatus })
        assertEquals("it broke", read[0].message)
        assertEquals("unusable", read[1].message)
        assertEquals("c", read[2].message, "a check that logged nothing falls back to its description")
    }

    @Test
    fun severalLogMessagesAreKeptTogether() {
        val json =
            """{"sections": [{"checks": [{"description": "d", "key": ["s", "<FontBakeryCheck:x/y>", []], "result": "FAIL",
              "logs": [{"message": {"message": "first problem"}, "status": "FAIL"}, {"message": {"message": "second problem"}, "status": "FAIL"}]}]}]}"""
        val only = parseFontbakeryReport(Json.parseToJsonElement(json)).single()
        assertEquals("first problem\n\nsecond problem", only.message)
    }

    @Test
    fun somethingThatIsNotAReportReadsAsNoOutcomesNotAnException() {
        assertTrue(parseFontbakeryReport(Json.parseToJsonElement("""{"nothing": true}""")).isEmpty())
        assertTrue(parseFontbakeryReport(Json.parseToJsonElement("[1, 2, 3]")).isEmpty())
        assertTrue(parseFontbakeryReport(Json.parseToJsonElement("""{"sections": [{"checks": ["x", 3]}]}""")).isEmpty())
    }
}
