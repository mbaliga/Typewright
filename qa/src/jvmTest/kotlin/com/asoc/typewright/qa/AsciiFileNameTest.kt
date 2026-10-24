// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The temporary copy of a font that layer one's tools read has a plain ASCII name whatever the family is called. */
class AsciiFileNameTest {
    @Test
    fun accentsAreTakenOffAndOtherNonAsciiLettersBecomeUnderscores() {
        assertEquals("HyleDeco-Regular.ttf", asciiFileName("Hyl\u00E9Deco-Regular.ttf"))
        assertEquals("Cafe-Bold.ttf", asciiFileName("Caf\u00E9-Bold.ttf"))
        assertEquals("__-Regular.ttf", asciiFileName("\u3042\u3044-Regular.ttf"))
    }

    @Test
    fun aPathIsReducedToItsLastPartAndNothingIsLeftEmpty() {
        assertEquals("Font-Regular.ttf", asciiFileName("/home/user/fonts/Font-Regular.ttf"))
        assertEquals("Font-Regular.ttf", asciiFileName("C:\\fonts\\Font-Regular.ttf"))
        assertEquals("font.ttf", asciiFileName(""))
        assertEquals("font.ttf", asciiFileName("/"))
    }

    @Test
    fun aFontWithAnAccentedNameIsHandedToTheToolUnderAnAsciiName() {
        val seen = mutableListOf<List<String>>()
        val scratch = mutableListOf<File>()
        val runner =
            object : CommandRunner {
                override fun run(
                    command: List<String>,
                    timeoutMillis: Long,
                ): Captured {
                    seen += command
                    return when {
                        command.getOrNull(1) == "-c" -> {
                            Captured(0, "python ok 3.11.0\n")
                        }

                        command.contains("--version") -> {
                            Captured(0, "0.12.10\n")
                        }

                        command.contains("--list-checks") -> {
                            Captured(0, "com.google.fonts/check/foo\n")
                        }

                        else -> {
                            // The check itself: the font file is the last argument, and it must exist under a plain ASCII name now.
                            val font = File(command.last())
                            assertTrue(font.isFile, "the tool was handed ${font.path}, which is not there")
                            assertTrue(font.name.all { it.code in 0x20..0x7E }, font.name)
                            scratch += font.parentFile
                            val json = File(command[command.indexOf("--json") + 1])
                            json.writeText(
                                """{"sections": [{"checks": [{"description": "d", "key": ["s", "<FontBakeryCheck:x/a>", []],
                                   "logs": [], "result": "PASS"}]}]}""",
                            )
                            Captured(0, "")
                        }
                    }
                }
            }
        val checker = FontbakeryCliChecker({ null }, runner, 1_000L)
        val report = runSuspend { checker.check(LayerOneInput("Hyl\u00E9Deco-Regular.ttf", byteArrayOf(1, 2, 3))) }
        assertEquals(1, report.results.size, report.results.toString())
        assertEquals(CheckStatus.PASS, report.results.single().status)
        assertTrue(seen.any { it.last().endsWith("HyleDeco-Regular.ttf") }, seen.toString())
        // The scratch directory the copy was made in is gone afterwards.
        assertTrue(scratch.isNotEmpty() && scratch.none { it.exists() }, scratch.toString())
    }
}
