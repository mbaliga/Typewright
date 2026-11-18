// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.storage.IosAppConfigStore
import com.asoc.typewright.project.storage.IosStorageProvider
import com.asoc.typewright.ui.TypewrightApp
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController

// One ProjectWorkspace for the process, built outside Compose so the Swift shell can flush it when
// the scene leaves the foreground (docs/PROJECT_MODEL.md section 13 asks every platform to flush
// on exit; iOS never sends a "closing" signal, only scene-phase changes).
private val workspaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private val workspace: ProjectWorkspace by lazy {
    createProjectWorkspace(
        scope = workspaceScope,
        storage = IosStorageProvider(),
        config = IosAppConfigStore(),
    )
}

/** The root view controller for the SwiftUI shell: the existing Typewright app, unchanged. */
fun mainViewController(): UIViewController = ComposeUIViewController { TypewrightApp(workspace = workspace) }

/** Writes any pending edit to disk now. The shell calls it when the scene goes inactive or to the background. */
fun flushWorkspace() {
    runBlocking { workspace.flush() }
}
