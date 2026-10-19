// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectSummary
import com.asoc.typewright.project.RecentProject
import com.asoc.typewright.project.ResumePoint
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProjectsScreenTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val project =
        RecentProject(
            location = ProjectLocation.FileSystem("/home/me/Fonts/hyle-deco"),
            name = "Hyle Deco Redraw",
            lastOpened = now - 3.hours,
            lastEdited = now - 2.hours,
            resume = ResumePoint(section = "DRAW", master = null, glyph = "o"),
            summary = ProjectSummary(glyphs = 338, outliers = 38, styleClass = "display-artdeco"),
        )

    @Test
    fun theRowSaysWhatTheProjectHoldsAndWhenItWasEdited() {
        assertEquals("338 glyphs · 38 outliers · edited 2 h ago", summaryLine(project, now))
        assertEquals("not opened since it was listed", summaryLine(project.copy(summary = null, lastEdited = null), now))
        assertEquals("/home/me/Fonts/hyle-deco", locationText(project.location))
    }

    @Test
    fun agoIsInTheMonoLinesOwnWords() {
        assertEquals("just now", agoText(now, now - 20.seconds))
        assertEquals("5 min ago", agoText(now, now - 5.minutes))
        assertEquals("3 d ago", agoText(now, now - 80.hours))
    }

    @Test
    fun aProjectOpensOnTheSectionItWasLeftOnOrHome() {
        assertEquals(DeckSection.DRAW, sectionFor(project))
        assertEquals(DeckSection.HOME, sectionFor(project.copy(resume = null)))
        assertEquals(DeckSection.HOME, sectionFor(project.copy(resume = ResumePoint("nonsense", null, null))))
    }

    @Test
    fun theRowAndTheEmptyScreenRender() {
        val rows =
            ScreenshotHarness.capture(name = "projects-rows", width = 720, height = 600, density = 2f) {
                Column(Modifier.padding(18.dp)) {
                    ProjectRow(project, now, CanvasTextures.PAPER, showRemove = false, onOpen = {}, onLongPress = {}, onRemove = {})
                    ProjectRow(
                        project.copy(name = "Second"),
                        now,
                        CanvasTextures.PAPER,
                        showRemove = true,
                        onOpen = {},
                        onLongPress = {},
                        onRemove = {},
                    )
                }
            }
        assertTrue(rows.file.length() > 0)
        val empty =
            ScreenshotHarness.capture(name = "projects-empty", width = 720, height = 1560, density = 2f) {
                ProjectsScreen(texture = CanvasTextures.PAPER, onOpened = {})
            }
        assertTrue(empty.file.length() > 0)
    }
}
