// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.project.ProjectFiles
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Google Fonts fix-ups the desktop backend runs on the font fontmake wrote: that they run after fontmake, on that file, once; what the
 * log says of them; and that a failure of theirs never fails the build. The script itself, run by the real Python on a real font, is in
 * [FontmakePythonIntegrationTest].
 */
class GoogleFontsFixesTest {
    private val fakes = mutableListOf<FakePython>()

    @AfterTest
    fun cleanUp() {
        fakes.forEach { it.cleanUp() }
    }

    private fun fake(fixes: String?) = FakePython(FakePython.succeedsAfter(0), fixes = fixes).also { fakes += it }

    private val request = CompileRequest(fixtureProject(), CompileSource.Ufo("Fixture-Regular.ufo"))

    private val says =
        """
        echo "some noise from fontTools"
        echo "typewright-fix: OS/2 fsType 4 -> 0 (installable embedding)"
        echo "typewright-fix: gasp table added (grid-fit and smooth at every size)"
        echo "typewright-fix: done"
        exit 0
        """.trimIndent()

    @Test
    fun theFixUpsRunAfterFontmakeOnTheFileItWroteAndTheLogListsEachChange() {
        val python = fake(says)
        val result = runBlocking { python.backend().compile(request) }
        assertIs<CompileResult.Success>(result)
        val ran = File(python.directory, "fixes-args.txt").readText().trim()
        assertTrue(ran.startsWith("-c SCRIPT ") && ran.endsWith("/out/font.ttf"), ran)
        val messages = result.log.map { it.message }
        assertContains(messages, "Google Fonts fix-up: OS/2 fsType 4 -> 0 (installable embedding)")
        assertContains(messages, "Google Fonts fix-up: gasp table added (grid-fit and smooth at every size)")
        assertTrue(messages.none { it.contains("some noise") }, "only the script's marked lines are for the log")
        val building = messages.indexOfFirst { it.startsWith("Running fontmake") }
        assertTrue(messages.indexOfFirst { it.startsWith("Google Fonts fix-up") } > building, "the fix-ups come after fontmake")
        assertEquals(
            "FAKE-TTF-BYTES",
            result.binaries
                .single()
                .bytes
                .decodeToString(),
            "the stand-in did not touch the font",
        )
    }

    private fun stating(vararg entries: Pair<String, PlistValue>) =
        fixtureProject(UfoFontInfo(familyName = "Fixture", styleName = "Regular", unitsPerEm = 1000, other = entries.toList()))

    private val restrictedEmbedding = "openTypeOS2Type" to PlistValue.PArray(listOf(PlistValue.PInteger(1)))

    @Test
    fun aProjectThatStatesItsEmbeddingTellsTheScriptToLeaveItAndTheLogSaysSo() {
        val python =
            fake(
                """
                echo "typewright-kept: OS/2 fsType stays 2: the project states it (openTypeOS2Type); Google Fonts asks for 0"
                echo "typewright-fix: gasp table added (grid-fit and smooth at every size)"
                echo "typewright-fix: done"
                exit 0
                """.trimIndent(),
            )
        val result = runBlocking { python.backend().compile(CompileRequest(stating(restrictedEmbedding), request.source)) }
        assertIs<CompileResult.Success>(result)
        val ran = File(python.directory, "fixes-args.txt").readText().trim()
        assertTrue(ran.endsWith("/out/font.ttf fsType"), "the script is told the project states fsType: $ran")
        val left = result.log.single { it.message.startsWith("Google Fonts fix-up not applied") }
        assertEquals(CompileLogLine.Level.WARNING, left.level, "a value left alone is something the person should see")
        assertContains(left.message, "OS/2 fsType stays 2")
        assertContains(left.message, "openTypeOS2Type")
        assertContains(result.log.map { it.message }, "Google Fonts fix-up: gasp table added (grid-fit and smooth at every size)")
    }

    @Test
    fun aProjectThatStatesNothingTellsTheScriptNothing() {
        val python = fake(says)
        runBlocking { python.backend().compile(request) }
        val ran = File(python.directory, "fixes-args.txt").readText().trim()
        assertTrue(ran.endsWith("/out/font.ttf"), "nothing is stated, so nothing follows the font: $ran")
    }

    @Test
    fun theScriptIsToldEveryValueTheProjectStates() {
        assertEquals(emptyList(), GoogleFontsFixes.statedBy(emptyMap()), "no fontinfo.plist states nothing")
        assertEquals(emptyList(), GoogleFontsFixes.statedBy(ufoFiles(stating())), "a font info with neither key states nothing")
        assertEquals(listOf("fsType"), GoogleFontsFixes.statedBy(ufoFiles(stating(restrictedEmbedding))))
        val selection = "openTypeOS2Selection" to PlistValue.PArray(listOf(PlistValue.PInteger(8)))
        assertEquals(listOf("fsSelection"), GoogleFontsFixes.statedBy(ufoFiles(stating(selection))))
        assertEquals(listOf("fsType", "fsSelection"), GoogleFontsFixes.statedBy(ufoFiles(stating(restrictedEmbedding, selection))))
        val emptyArray = "openTypeOS2Type" to PlistValue.PArray(emptyList())
        assertEquals(listOf("fsType"), GoogleFontsFixes.statedBy(ufoFiles(stating(emptyArray))), "an empty list is still a statement")
        assertEquals(emptyList(), GoogleFontsFixes.statedBy(mapOf("fontinfo.plist" to "not a plist".encodeToByteArray())))
    }

    private fun ufoFiles(project: ProjectFiles): Map<String, ByteArray> =
        project.listFiles().filter { it.startsWith("Fixture-Regular.ufo/") }.associate {
            it.removePrefix("Fixture-Regular.ufo/") to
                project.readBytes(it)
        }

    @Test
    fun aFontThatNeedsNothingSaysSo() {
        val python = fake("echo \"typewright-fix: done\"\nexit 0")
        val result = runBlocking { python.backend().compile(request) }
        assertContains(result.log.map { it.message }, "Google Fonts fix-ups: nothing to change.")
    }

    @Test
    fun theOptionTurnsThemOff() {
        val python = fake(says)
        val result = runBlocking { python.backend().compile(request.copy(options = CompileOptions(googleFontsFixes = false))) }
        assertIs<CompileResult.Success>(result)
        assertFalse(python.fixesArgsFile.exists(), "the fix-up script must not run when the option is off")
        assertTrue(result.log.none { it.message.contains("Google Fonts fix-up") })
    }

    @Test
    fun aFailureOfTheFixUpsWarnsAndNeverFailsTheBuild() {
        val python = fake("echo \"Traceback (most recent call last):\"\necho \"KeyError: 'OS/2'\"\nexit 1")
        val result = runBlocking { python.backend().compile(request) }
        assertIs<CompileResult.Success>(result)
        val warning = result.log.single { it.level == CompileLogLine.Level.WARNING && it.message.contains("fix-ups could not be applied") }
        assertContains(warning.message, "KeyError: 'OS/2'")
        assertContains(warning.message, "The font is as fontmake wrote it.")
        assertEquals(1, result.binaries.size)
    }

    @Test
    fun aStandInThatDoesNotRunTheScriptIsSilent() {
        val python = fake(null)
        val result = runBlocking { python.backend().compile(request) }
        assertIs<CompileResult.Success>(result)
        assertTrue(result.log.none { it.message.contains("fix-up") }, result.log.joinToString("\n") { it.message })
    }

    @Test
    fun theFixUpsDoNotRunWhenFontmakeFailedOrWroteNothing() {
        val failing = FakePython(FakePython.FAILS, fixes = says).also { fakes += it }
        assertIs<CompileResult.Failure>(runBlocking { failing.backend().compile(request) })
        assertFalse(failing.fixesArgsFile.exists())
        val nothing = FakePython(FakePython.WRITES_NOTHING, fixes = says).also { fakes += it }
        assertIs<CompileResult.Failure>(runBlocking { nothing.backend().compile(request) })
        assertFalse(nothing.fixesArgsFile.exists())
    }

    // --- reading the script's output -------------------------------------------------------------

    @Test
    fun theOutputIsReadOnlyThroughItsMarkedLinesAndOnlyWhenItRanToTheEnd() {
        val finished = "noise\ntypewright-fix: a change\ntypewright-fix: another\ntypewright-fix: done\n"
        assertEquals(listOf("a change", "another"), GoogleFontsFixes.parse(finished))
        assertEquals(emptyList(), GoogleFontsFixes.parse("typewright-fix: done"))
        assertNull(GoogleFontsFixes.parse("typewright-fix: a change\n"), "no 'done' means the script did not finish")
        assertNull(GoogleFontsFixes.parse("fontmake ok 9.9.9\npython ok 3.11.0\n"), "a probe's answer is not a fix-up report")
        assertNull(GoogleFontsFixes.parse(""))
    }

    @Test
    fun theReportCarriesTheValuesLeftAloneApartFromTheChanges() {
        val finished = "typewright-fix: a change\ntypewright-kept: left alone\nnoise\ntypewright-fix: done\n"
        val report = assertNotNull(GoogleFontsFixes.parseReport(finished))
        assertEquals(listOf("a change"), report.changes)
        assertEquals(listOf("left alone"), report.kept)
        assertNull(GoogleFontsFixes.parseReport("typewright-kept: left alone\n"), "no 'done' means the script did not finish")
    }

    @Test
    fun theScriptIsPythonThatNamesEveryFixItDoesAndThePrepProgramTheCheckerLooksFor() {
        val script = GoogleFontsFixes.SCRIPT
        assertTrue(script.contains("# typewright-google-fonts-fixes"), "the stand-in finds the script by this comment")
        assertTrue(script.contains("os2.fsType = 0"))
        assertTrue(script.contains("stated = set(sys.argv[2:])"), "the script is told what the project states")
        assertTrue(script.contains("\"fsType\" in stated") && script.contains("\"fsSelection\" in stated"))
        assertTrue(script.contains("1 << 7"))
        assertTrue(script.contains("gaspRange = {0xFFFF: 0x000F}"))
        assertTrue(script.contains("\"PUSHW[]\", \"511\", \"SCANCTRL[]\", \"PUSHB[]\", \"4\", \"SCANTYPE[]\""))
        assertTrue(script.contains("\"fpgm\" not in font"), "a hinted font keeps its own gasp and prep")
        assertTrue(script.contains("print(\"typewright-fix: \" + line)"))
        assertTrue(script.contains("print(\"typewright-fix: done\")"))
        assertNotNull(script.lines().firstOrNull { it.startsWith("import sys") })
    }
}
