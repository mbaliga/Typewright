// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.ui.Screenshot
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.tokens.CanvasTextures
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Home (S04) and Font info (S06) drawn from the open project, as PNGs under `ui/build/screenshots/`
 * and checked for what they must contain. Phone width (390 dp at density 2). Nothing here asserts
 * that it looks right; the PNGs are for a person to look at, and the assertions are that the screen
 * is not blank, that it changes when the project changes, and that no project means no letters.
 */
class HomeScreenScreenshotTest {
    private val sample = SampleProject.workspace()
    private val poppins by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        SampleProject.workspaceFor("Poppins-Regular.ttf", File(file, "data/learn-faces/poppins/Poppins-Regular.ttf").readBytes())
    }

    @Composable
    private fun With(
        workspace: com.asoc.typewright.project.ProjectWorkspace?,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalProjectWorkspace provides workspace, content = content)
    }

    private fun shot(
        name: String,
        workspace: com.asoc.typewright.project.ProjectWorkspace?,
        view: HomeView = HomeView.SPECIMEN,
        height: Int = 1688,
    ): Screenshot =
        ScreenshotHarness.capture(name = name, width = 780, height = height, density = 2f) {
            With(workspace) { HomeScreen(texture = CanvasTextures.PAPER, initialView = view) }
        }

    private fun inkFraction(shot: Screenshot): Double {
        val pixels = shot.bitmap.toPixelMap()
        val background = pixels[0, 0]
        var ink = 0
        var total = 0
        for (x in 0 until pixels.width step 2) {
            for (y in 0 until pixels.height step 2) {
                total++
                val p = pixels[x, y]
                if (p.red != background.red || p.green != background.green || p.blue != background.blue) ink++
            }
        }
        return ink.toDouble() / total
    }

    private fun differentPixels(
        a: Screenshot,
        b: Screenshot,
    ): Int {
        val pa = a.bitmap.toPixelMap()
        val pb = b.bitmap.toPixelMap()
        var different = 0
        for (x in 0 until minOf(pa.width, pb.width) step 2) {
            for (y in 0 until minOf(pa.height, pb.height) step 2) if (pa[x, y] != pb[x, y]) different++
        }
        return different
    }

    @Test
    fun specimenGlyphsAndFontInfoAreDrawnFromTheSample() {
        val specimen = shot("home-specimen", sample)
        val glyphs = shot("home-glyphs", sample, HomeView.GLYPHS)
        val info = shot("home-font-info", sample, HomeView.FONT_INFO)
        for (s in listOf(specimen, glyphs, info)) {
            assertTrue(s.file.length() > 0)
            assertTrue(inkFraction(s) > 0.02, "${s.file.name} is nearly blank")
        }
    }

    @Test
    fun theScreensChangeWhenTheProjectChanges() {
        for ((view, name) in listOf(HomeView.SPECIMEN to "specimen", HomeView.GLYPHS to "glyphs", HomeView.FONT_INFO to "font-info")) {
            val mine = shot("home-$name-sample", sample, view)
            val theirs = shot("home-$name-poppins", poppins, view)
            assertTrue(differentPixels(mine, theirs) > 2000, "$name looks the same for two different fonts: it is not reading the project")
        }
    }

    @Test
    fun withNoProjectOpenThereAreNoLettersJustAnInvitationToOpenOne() {
        val empty = shot("home-no-project", null, height = 900)
        val withProject = shot("home-no-project-compare", sample, height = 900)
        assertTrue(inkFraction(empty) < inkFraction(withProject), "an empty Home should hold less than a font's")
    }
}
