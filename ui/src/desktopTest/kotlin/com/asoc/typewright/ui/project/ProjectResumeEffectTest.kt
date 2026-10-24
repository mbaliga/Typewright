// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.ResumePoint
import com.asoc.typewright.ui.deck.DeckSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The router's section and glyph are recorded against the open project and come back when it is opened again. */
class ProjectResumeEffectTest {
    /** Composes [ProjectResumeEffect] under [workspace] until [done] holds (or fails after twenty seconds), answering what it asked to restore. */
    private fun compose(
        workspace: ProjectWorkspace,
        section: DeckSection,
        glyph: String?,
        done: () -> Boolean,
    ): List<String?> {
        val restored = mutableListOf<String?>()
        ImageComposeScene(width = 100, height = 100) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                ProjectResumeEffect(section, glyph) { restored += it }
            }
        }.use { scene ->
            val deadline = System.nanoTime() + 20_000_000_000L
            var frame = 0L
            while (!done()) {
                check(
                    System.nanoTime() < deadline,
                ) { "the resume effect did not finish: restored=$restored recents=${workspace.recents.value}" }
                scene.render(frame * 16_000_000L)
                frame++
                Thread.sleep(5)
            }
        }
        return restored
    }

    private fun resumeOf(workspace: ProjectWorkspace): ResumePoint? =
        workspace.recents.value
            .singleOrNull()
            ?.resume

    @Test
    fun theSectionAndGlyphAreRecordedAfterTheRestoreAndComeBackWhenTheProjectIsOpenedAgain() {
        val workspace = SampleProject.workspace()
        // First time: nothing to restore (so a glyph left from another project is dropped), then the point is written.
        val first = compose(workspace, DeckSection.DRAW, "T") { resumeOf(workspace)?.glyph == "T" }
        assertEquals(listOf<String?>(null), first)
        val master = checkNotNull(SampleProject.openProject(workspace).master).id
        assertEquals(ResumePoint("draw", master, "T"), resumeOf(workspace))

        // A new composition over the same project (as after a reopen) restores that glyph, then keeps recording.
        val second = compose(workspace, DeckSection.SPACE, "o") { resumeOf(workspace)?.section == "space" }
        assertEquals(listOf<String?>("T"), second)
        assertEquals(ResumePoint("space", master, "o"), resumeOf(workspace))
    }

    @Test
    fun aGlyphTheProjectNoLongerHasIsNotRestored() {
        val workspace = SampleProject.workspace()
        compose(workspace, DeckSection.DRAW, "no-such-glyph") { resumeOf(workspace)?.glyph == "no-such-glyph" }
        val again =
            compose(workspace, DeckSection.DRAW, null) {
                resumeOf(workspace)?.glyph == null &&
                    resumeOf(workspace)?.section == "draw"
            }
        assertEquals(listOf<String?>(null), again)
        assertNull(resumeOf(workspace)?.glyph)
        assertTrue(resumeOf(workspace) != null)
    }
}
