// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertTrue

/** S01 with a workspace: the ways to start from something you have (a font, a UFO folder, the sample), as PNGs under `ui/build/screenshots/`. */
class ProjectsChoicesScreenshotTest {
    @Test
    fun theEmptyScreenOffersAFontAUfoFolderAndTheLabelledSample() {
        val workspace = createProjectWorkspace(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val shot =
            ScreenshotHarness.capture(name = "projects-choices-empty", width = 780, height = 1688, density = 2f) {
                CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                    ProjectsScreen(texture = CanvasTextures.PAPER, onOpened = {})
                }
            }
        assertTrue(shot.file.length() > 0)
    }

    @Test
    fun withAProjectOpenTheChoicesSitBelowItsRow() {
        val workspace = SampleProject.workspace()
        val shot =
            ScreenshotHarness.capture(name = "projects-choices-with-project", width = 780, height = 1688, density = 2f) {
                CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                    ProjectsScreen(texture = CanvasTextures.PAPER, onOpened = {})
                }
            }
        assertTrue(shot.file.length() > 0)
    }
}
