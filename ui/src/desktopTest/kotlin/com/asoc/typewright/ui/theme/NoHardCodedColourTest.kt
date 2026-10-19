// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every colour in `ui/` reads from the theme roles (SCREENS_V1 §3). This fails on any colour
 * literal written outside the theme module (`tokens/` and `theme/`), so a screen that paints with
 * its own hex cannot ship, and a theme can then really recolour the app. Comments are ignored.
 */
class NoHardCodedColourTest {
    private val literal =
        Regex("""Color\(0x|Color\.(White|Black|Red|Green|Blue|Gray|DarkGray|LightGray|Yellow|Cyan|Magenta)\b|Argb\.rgb\(|Argb\(0x""")

    private fun sources(): List<File> {
        val root = File("src/commonMain/kotlin")
        assertTrue(root.isDirectory, "run from the ui module: ${File(".").absolutePath}")
        return root
            .walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file -> file.invariantSeparatorsPath.let { "/ui/tokens/" !in it && "/ui/theme/" !in it } }
            .toList()
    }

    @Test
    fun noColourLiteralIsWrittenOutsideTheThemeModule() {
        val offenders =
            sources().flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.trim()
                    val comment = code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
                    if (!comment && literal.containsMatchIn(code)) "${file.name}:${index + 1}: $code" else null
                }
            }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun theScanActuallyLooksAtTheScreens() {
        assertTrue(sources().size > 40, "scanned ${sources().size} files")
    }
}
