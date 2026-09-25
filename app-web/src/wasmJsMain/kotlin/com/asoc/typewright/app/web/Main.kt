// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.asoc.typewright.ui.TypewrightApp
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.js.ExperimentalWasmJsInterop

/** The id of the element in index.html that the app mounts into. */
const val ROOT_ELEMENT_ID: String = "typewright"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // P11 WP5 (docs/PROJECT_MODEL.md §13): one ProjectWorkspace for the tab's whole life, built
    // and held here rather than inside TypewrightApp's own default, so the visibility/unload
    // hooks below can flush this exact instance independently of Compose's own lifecycle.
    // kotlinx-browser is already on this target's classpath transitively through Compose
    // Multiplatform (gradle/libs.versions.toml's own comment on the `kotlinx-browser` version).
    val workspaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val workspace = createProjectWorkspace(workspaceScope)

    // There is no reliable "the tab is about to close" event in a browser, so "no longer
    // visible" (switched away from, minimised, or genuinely closing) is the honest signal to
    // save by; `beforeunload` catches the one case (an actual close/navigate) that visibility
    // alone can arrive at too late to still flush from.
    document.addEventListener("visibilitychange") {
        if (documentIsHidden()) workspaceScope.launch { workspace.flush() }
    }
    window.addEventListener("beforeunload") {
        workspaceScope.launch { workspace.flush() }
    }

    ComposeViewport(ROOT_ELEMENT_ID) {
        // Web only: "Download project (.zip)"/"Open project (.zip)…" (docs/PROJECT_MODEL.md
        // §13's palette table, "web in-memory mode only").
        TypewrightApp(workspace = workspace, supportsZipTransfer = true)
    }
}

/**
 * `document.visibilityState === "hidden"`. `kotlinx-browser` 0.5.0's own `org.w3c.dom.Document`
 * binding has no `visibilityState` property (confirmed at `compileKotlinWasmJs`), so this reads it
 * directly with the same `js("...")` interop `shape-preview`'s own `BrowserFontFaceVisualPreview.kt`
 * already establishes for a DOM member no typed binding covers.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun documentIsHidden(): Boolean = js("document.visibilityState === 'hidden'")
