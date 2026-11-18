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
import kotlinx.coroutines.withTimeoutOrNull

/** The window title. */
const val APP_TITLE: String = "Typewright"

private const val SHUTDOWN_FLUSH_MILLIS: Long = 3_000L

fun main() {
    // P11 WP5 (docs/PROJECT_MODEL.md §13): one ProjectWorkspace for the app's whole run, built
    // and held here rather than inside TypewrightApp's own default, so onCloseRequest below can
    // flush this exact instance even as the composition itself is torn down by exitApplication().
    val workspaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val workspace = createProjectWorkspace(workspaceScope)

    // The window's close button is not the only way out on Linux: a logout, `kill` or a system
    // shutdown sends SIGTERM/SIGHUP, which skips onCloseRequest and runs only JVM shutdown hooks.
    // Flush there too, bounded so a stuck write cannot hang the shutdown. SIGKILL and a crash still
    // lose the last debounced edit; the swap-protocol store keeps what was already written intact.
    // CODE-ONLY: written without a compile here (the Android SDK the module graph needs is absent).
    val shutdownFlush =
        Thread(
            { runBlocking { withTimeoutOrNull(SHUTDOWN_FLUSH_MILLIS) { workspace.flush() } } },
            "typewright-shutdown-flush",
        )
    Runtime.getRuntime().addShutdownHook(shutdownFlush)

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
