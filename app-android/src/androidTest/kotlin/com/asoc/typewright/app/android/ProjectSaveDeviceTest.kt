// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Approve
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.ReplaceOutline
import com.asoc.typewright.project.SetKerning
import com.asoc.typewright.project.Unlock
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * docs/PROJECT_MODEL.md §13's save half, owner-run on a real device (CLAUDE.md law 4): picks a
 * folder through SAF, creates `TypewrightDeviceTest-<epoch millis>/`, exercises approve, unlock,
 * edit, kern, pin and reflection through the *real* [com.asoc.typewright.project.ProjectSession],
 * survives an Activity recreate with the same session instance, closes, then records the
 * project's file hashes for [ProjectRestoreDeviceTest] to compare against in a fresh process.
 *
 * Needs `MainActivity.pickProjectParentFolder()` (WP5) and a `MainActivity.workspace` accessor
 * onto the app's real, process-scoped [ProjectWorkspace] -- neither exists yet (WP3's own report
 * says so); this file is written against the shape docs/PROJECT_MODEL.md §13 describes, for WP5
 * to wire up.
 */
@RunWith(AndroidJUnit4::class)
class ProjectSaveDeviceTest {
    @Test
    fun createEditAndRecordAProjectAcrossARecreate() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val parent = pickParentFolder(scenario, device)

            val projectName = DeviceTestSupport.freshProjectName()
            val spec = DeviceTestSupport.fixtureSpec(projectName)

            val workspace = readWorkspace(scenario)
            val createResult = runBlocking { workspace.create(parent, spec) }
            assertIs<CreateResult.Created>(createResult, "Creating \"$projectName\" failed: $createResult")
            val session = createResult.session

            val glyphA = GlyphRef("regular", "A")

            assertApplied(session.execute(Approve(glyphA, ApprovalOrigin.DRAW)), "Approve")
            assertApplied(session.execute(Unlock(glyphA)), "Unlock")
            assertApplied(session.execute(ReplaceOutline(glyphA, listOf(DeviceTestSupport.editedContour()), "Edit")), "Edit")
            assertApplied(session.execute(SetKerning("regular", "A", "B", -20.0)), "Kern")
            assertApplied(
                session.update(
                    MetaChange.AddPin(
                        ScrapbookPin(
                            id = "device-test-pin",
                            kind = ScrapbookPinKind.NOTE,
                            captionTitle = "Device test",
                            captionSource = "P11 WP3",
                            noteText = "Pinned during ProjectSaveDeviceTest",
                        ),
                        image = null,
                        imageExtension = null,
                    ),
                ),
                "Pin",
            )
            assertApplied(
                session.update(MetaChange.AddReflection(workbook = "latn", task = 1, text = "Reflected during ProjectSaveDeviceTest")),
                "Reflection",
            )

            runBlocking { session.flush() }

            scenario.recreate()

            // §6: the workspace is process-scoped, not held by the Activity, so recreating it
            // must not create a second session, and history must not have been cleared either.
            scenario.onActivity { activity ->
                val afterRecreate = activity.workspace.current.value
                assertSame(session, afterRecreate, "The same ProjectSession instance must survive an Activity recreate")
                assertTrue(afterRecreate!!.history.value.canUndo, "Undo history must survive an Activity recreate")
            }

            runBlocking { workspace.closeCurrent() }

            val hashes = runBlocking { DeviceTestSupport.hashFiles(session.store.list(), session.store::read) }
            val location = session.store.location as ProjectLocation.SafTree
            DeviceTestSupport.writeRecord(context, DeviceTestRecord(location, projectName, hashes))
        }
    }

    // MainActivity.pickProjectParentFolder() (WP5): opens SAF's OpenDocumentTree at
    // primary:Documents. It must be launched on the main thread (an ActivityResultLauncher
    // requirement) but not *awaited* there: the OS folder-picker Activity that follows delivers
    // its result back through this same main thread's Looper, so blocking that thread here (a
    // bare runBlocking) would deadlock against the very UiAutomator taps below that let it
    // finish. A short-lived MainScope launches the suspend call and this method waits on a
    // CompletableDeferred from the test's own thread instead, leaving the Looper free.
    private fun pickParentFolder(
        scenario: ActivityScenario<MainActivity>,
        device: UiDevice,
    ): ProjectLocation.SafTree {
        val result = CompletableDeferred<ProjectLocation.SafTree?>()
        val scope = MainScope()
        scenario.onActivity { activity ->
            scope.launch { result.complete(activity.pickProjectParentFolder()) }
        }

        DeviceTestSupport.clickByIdOrText(
            device,
            resourceIds = listOf("com.android.documentsui:id/container_save", "android:id/button1"),
            text = "use this folder",
        )
        DeviceTestSupport.clickByIdOrText(
            device,
            resourceIds =
                listOf(
                    "com.android.permissioncontroller:id/permission_allow_button",
                    "com.android.packageinstaller:id/permission_allow_button",
                ),
            text = "allow",
        )

        val picked = runBlocking { withTimeout(20_000) { result.await() } }
        scope.cancel()
        return checkNotNull(picked) { "pickProjectParentFolder() did not resolve to a folder" }
    }

    private fun readWorkspace(scenario: ActivityScenario<MainActivity>): ProjectWorkspace {
        var workspace: ProjectWorkspace? = null
        scenario.onActivity { activity -> workspace = activity.workspace }
        return checkNotNull(workspace) { "MainActivity.workspace was null" }
    }

    private fun assertApplied(
        result: EditResult,
        step: String,
    ) {
        assertIs<EditResult.Applied>(result, "$step was refused: $result")
    }
}
