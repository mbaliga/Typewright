// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.SampleProject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The state behind [WorkbookScreen], composed for real over an open project: the twelve gates do not run inside
 * composition, the screen says it is loading until they have run, and a project that changes under it is measured
 * again without the person losing their place.
 */
class WorkbookScreenUiStateLoadingTest {
    private val workspace: ProjectWorkspace = SampleProject.workspace()

    private fun <T> withState(
        load: (OpenProject) -> WorkbookCampaignSnapshot,
        block: (scene: ImageComposeScene, state: () -> WorkbookScreenUiState) -> T,
    ): T {
        val holder = AtomicReference<WorkbookScreenUiState?>(null)
        return ImageComposeScene(width = 200, height = 200, density = Density(1f)) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                holder.set(rememberWorkbookScreenUiState(load = load))
            }
        }.use { scene ->
            scene.render(0)
            block(scene) { checkNotNull(holder.get()) }
        }
    }

    /** Renders frames until [done] holds, a frame every 10 ms of real time, up to 20 s. */
    private fun ImageComposeScene.renderUntil(done: () -> Boolean) {
        var nanos = 16_000_000L
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!done()) {
            check(System.nanoTime() < deadline) { "the condition never came true" }
            render(nanos)
            nanos += 16_000_000L
            Thread.sleep(10)
        }
    }

    @Test
    fun theGatesDoNotRunInsideCompositionAndTheScreenIsLoadingUntilTheyHave() {
        val composingThread = Thread.currentThread()
        val loadedOn = AtomicReference<Thread?>(null)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val real = SampleProject.openProject(workspace)
        withState(
            load = { project ->
                loadedOn.set(Thread.currentThread())
                entered.countDown()
                release.await(20, TimeUnit.SECONDS)
                loadWorkbookCampaignSnapshot(project)
            },
        ) { scene, state ->
            assertTrue(entered.await(20, TimeUnit.SECONDS), "the load started")
            assertNotSame(composingThread, loadedOn.get(), "the gates ran on another thread, not in composition")
            // The load is held open: composition has finished and nothing has been measured yet.
            scene.render(16_000_000)
            assertSame(WorkbookCampaignSnapshot.Loading, state().snapshot, "nothing is shown as measured until the gates have run")
            release.countDown()
            scene.renderUntil { state().snapshot is WorkbookCampaignSnapshot.Loaded }
            val loaded = assertIs<WorkbookCampaignSnapshot.Loaded>(state().snapshot)
            assertEquals(
                real.font
                    ?.ufo
                    ?.glyphs
                    ?.size,
                loaded.font.ufo.glyphs.size,
                "the snapshot is of the open project's own font",
            )
            assertEquals(loaded.currentTaskIndex, state().taskIndex, "a screen that opened on Loading lands on the current task")
        }
    }

    @Test
    fun aProjectThatChangesIsMeasuredAgainAndThePersonKeepsTheirTaskAndView() {
        val runs = AtomicInteger()
        withState(load = { project ->
            runs.incrementAndGet()
            loadWorkbookCampaignSnapshot(project)
        }) { scene, state ->
            scene.renderUntil { state().snapshot is WorkbookCampaignSnapshot.Loaded }
            val first = assertIs<WorkbookCampaignSnapshot.Loaded>(state().snapshot)
            assertEquals(1, runs.get())

            // The person is on a task other than the current one, in the All tasks list.
            val current = first.currentTaskIndex
            val away = if (current == 5) 6 else 5
            state().taskIndex = away
            state().allTasks = true

            // Saving a reflection on the current task confirms it (the project's own rule for a task with no automated
            // gate), which is a change to the project that the gates must see: the current task moves on.
            val session = assertNotNull(workspace.current.value)
            session.update(MetaChange.AddReflection("latn", current, "A reflection."))
            scene.renderUntil { runs.get() >= 2 && state().snapshot !== first }

            val second = assertIs<WorkbookCampaignSnapshot.Loaded>(state().snapshot)
            assertNotEquals(first.progress, second.progress, "the new snapshot reflects the change")
            assertNotEquals(current, second.currentTaskIndex, "the project's current task moved on")
            assertEquals(away, state().taskIndex, "the task they were on stays")
            assertTrue(state().allTasks, "the All tasks view stays")
        }
    }
}
