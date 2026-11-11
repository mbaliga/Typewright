// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import androidx.compose.runtime.CompositionLocalProvider
import com.asoc.typewright.campaign.WorkbookLatinContent
import com.asoc.typewright.project.Master
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.imports.ImportFixtures
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Workbook's figures for a font that is not the sample. Opening Poppins must show Poppins' own stems, counter
 * and overshoot, not Hyle Deco's (stem 44, counter 292, overshoot 0), and what it shows must be what the font
 * measures. The expected numbers were read from the same files with fontTools 4.67, by flattening each outline
 * finely and intersecting it with the probe line (n at 35% of its height, H at 20%, o at 50%), a route that shares no
 * code with the app's probe. The constants below are what that measurement printed.
 */
class WorkbookYourFontRealFontsTest {
    private fun fontOf(
        path: String,
        name: String,
    ): ProjectFont {
        val ready = assertIs<ImportReview.Ready>(reviewFontFile(name, ImportFixtures.fontBytes(path)))
        return ProjectFont(Master("regular", "$name.ufo", "Regular", isDefault = true, ufo = ready.candidate.ufo))
    }

    private val poppins by lazy { fontOf("data/learn-faces/poppins/Poppins-Regular.ttf", "Poppins-Regular.ttf") }
    private val hyleDeco by lazy { fontOf("fonts/HyleDeco-Regular.ttf", "HyleDeco-Regular.ttf") }

    private fun assertClose(
        expected: Double,
        actual: Double?,
        what: String,
        tolerance: Double = 0.5,
    ) {
        assertNotNull(actual, what)
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$what: fontTools measures $expected, the app shows $actual")
    }

    @Test
    fun poppinsIsMeasuredAsPoppinsAndMatchesTheIndependentMeasurement() {
        val r = measureYourFont(poppins)
        assertEquals(listOf(91.0, 90.0), r.nStems.map { Math.round(it).toDouble() }, "n's stems")
        assertEquals(listOf(91.0, 91.0), r.hStems.map { Math.round(it).toDouble() }, "H's stems")
        assertClose(93.0, r.oStroke, "o's left stroke")
        assertClose(368.0, r.oCounter, "o's counter")
        assertEquals(548, r.xHeight)
        assertClose(9.0, r.oOvershootTop, "o's overshoot at the top")
        assertClose(9.0, r.oOvershootBottom, "o's overshoot at the bottom")
        assertClose(77.0, r.nSidebearings?.first, "n's LSB")
        assertClose(72.0, r.nSidebearings?.second, "n's RSB")
        assertClose(43.0, r.oSidebearings?.first, "o's LSB")
        assertClose(43.0, r.oSidebearings?.second, "o's RSB")
    }

    @Test
    fun hyleDecoMeasuresAsItselfSoTheLessonsFiguresAreRightForTheSampleAndOnlyForIt() {
        val r = measureYourFont(hyleDeco)
        assertEquals(listOf(44.0, 44.0), r.nStems.map { Math.round(it).toDouble() })
        assertEquals(listOf(43.0, 45.0), r.hStems.map { Math.round(it).toDouble() })
        assertClose(44.0, r.oStroke, "o's left stroke")
        assertClose(291.52, r.oCounter, "o's counter")
        assertClose(0.0, r.oOvershootTop, "o's overshoot at the top")
        assertClose(0.0, r.oOvershootBottom, "o's overshoot at the bottom")
        assertClose(46.0, r.nSidebearings?.first, "n's LSB")
        assertClose(51.0, r.nSidebearings?.second, "n's RSB")
    }

    @Test
    fun theFiguresShownForPoppinsAreNotTheSamplesAndAreWhatWasMeasured() {
        val tasks = WorkbookLatinContent.load().associateBy { it.index }
        val shown = mutableMapOf<Int, List<String>>()
        for (index in listOf(2, 3, 4, 5, 7)) {
            val task = tasks.getValue(index)
            val view = demonstrationView(index, task.demonstration, poppins)
            shown[index] = assertNotNull(view.yourFont, "task $index shows the open font's own figures").lines
            val text = shown.getValue(index).joinToString("\n")
            // Not the sample's: the lesson's own lines for the sample, word for word, never appear among the person's.
            val lesson = (task.demonstration as com.asoc.typewright.campaign.Demonstration.StatDemonstration).stats
            for (line in lesson) assertFalse(line in shown.getValue(index), "task $index shows the lesson's line as the person's: $line")
            assertFalse("Hyle Deco" in text, "task $index: $text")
        }
        assertEquals("stem 93 -- counter 368 (o, through its middle)", shown.getValue(4)[0])
        assertEquals("overshoot +9 at the top, +9 at the bottom", shown.getValue(4)[1])
        assertEquals(
            listOf(
                "baseline 0 -- x-height 548 (your font's own figure)",
                "stem 91 units (n)",
                "o: overshoot +9 at the top, +9 at the bottom",
            ),
            shown.getValue(3),
        )
        assertEquals("n's stems: 91 and 90; H's stems: 91 and 91", shown.getValue(5).single())
        assertEquals("n: LSB 77 -- RSB 72", shown.getValue(7)[0])
        assertEquals("o: LSB 43 -- RSB 43", shown.getValue(7)[1])

        // And they differ from what the same screens show for the sample font, task by task.
        for (index in listOf(2, 3, 4, 5, 7)) {
            val sampleLines = assertNotNull(demonstrationView(index, tasks.getValue(index).demonstration, hyleDeco).yourFont).lines
            assertNotEquals(sampleLines, shown.getValue(index), "task $index shows the same figures for Poppins and for Hyle Deco")
        }
    }

    @Test
    fun theLessonsOwnFiguresStayTheLessonsOwnWhateverFontIsOpenAndAreHeadedAsNotYours() {
        val tasks = WorkbookLatinContent.load().associateBy { it.index }
        for (index in tasks.keys) {
            val task = tasks.getValue(index)
            val onPoppins = demonstrationView(index, task.demonstration, poppins)
            val onSample = demonstrationView(index, task.demonstration, hyleDeco)
            assertEquals(task.demonstration, onPoppins.lesson, "task $index: the lesson's text is not rewritten for the open font")
            assertEquals(onSample.lessonHeading, onPoppins.lessonHeading)
            assertTrue("not your font" in onPoppins.lessonHeading || "lesson scene" in onPoppins.lessonHeading, onPoppins.lessonHeading)
        }
    }

    @Test
    fun theWorkbookScreenIsPicturedForPoppinsAndForTheSample() {
        val poppinsWorkspace =
            SampleProject.workspaceFor("Poppins-Regular.ttf", ImportFixtures.fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf"))
        val sampleWorkspace = SampleProject.workspace()
        for ((name, workspace) in listOf("poppins" to poppinsWorkspace, "sample" to sampleWorkspace)) {
            val snapshot = loadWorkbookCampaignSnapshot(SampleProject.openProject(workspace))
            assertIs<WorkbookCampaignSnapshot.Loaded>(snapshot)
            for (task in listOf(4, 3)) {
                val shot =
                    ScreenshotHarness.capture(name = "workbook-$name-task$task", width = 800, height = 2000, density = 2f) {
                        CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                            WorkbookScreen(
                                texture = CanvasTextures.PAPER,
                                uiState = WorkbookScreenUiState(snapshot, initialTaskIndex = task),
                            )
                        }
                    }
                assertTrue(shot.file.length() > 0)
            }
        }
    }
}
