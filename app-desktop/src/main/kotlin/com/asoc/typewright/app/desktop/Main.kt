// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.asoc.typewright.ui.TypewrightApp
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** The window title. */
const val APP_TITLE: String = "Typewright"

fun main() {
    // P11 WP5 (docs/PROJECT_MODEL.md §13): one ProjectWorkspace for the app's whole run, built
    // and held here rather than inside TypewrightApp's own default, so onCloseRequest below can
    // flush this exact instance even as the composition itself is torn down by exitApplication().
    val workspaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val workspace = createProjectWorkspace(workspaceScope)

    application {
        Window(
            onCloseRequest = {
                // A debounced autosave may not have run yet (docs/PROJECT_MODEL.md §8); flush
                // before exiting rather than risk losing the last edit. runBlocking is deliberate:
                // this *is* the app's own exit path, so a brief, bounded wait for the last write
                // is the honest trade, not a stall anyone downstream will see.
                runBlocking { workspace.flush() }
                exitApplication()
            },
            title = APP_TITLE,
        ) {
            TypewrightApp(workspace = workspace)
        }
    }
}
