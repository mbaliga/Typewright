// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.asoc.typewright.ui.TypewrightApp
import com.asoc.typewright.ui.imports.BrowserTabImportDestination
import com.asoc.typewright.ui.imports.PlatformImportSupport
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.settings.BuildChannel
import com.asoc.typewright.ui.settings.DistributionChannel
import com.asoc.typewright.ui.settings.WEB_PREVIEW_NOTICE
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
    BuildChannel.current = DistributionChannel.WEB
    showPreviewBanner()
    // P11 WP5 (docs/PROJECT_MODEL.md §13): one ProjectWorkspace for the tab's whole life, built
    // and held here rather than inside TypewrightApp's own default, so the visibility/unload
    // hooks below can flush this exact instance independently of Compose's own lifecycle.
    // kotlinx-browser is already on this target's classpath transitively through Compose
    // Multiplatform (gradle/libs.versions.toml's own comment on the `kotlinx-browser` version).
    val workspaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val workspace = createProjectWorkspace(workspaceScope)
    // "Open a font…": the browser's file input; the project lives in the tab (compile-verified only).
    PlatformImportSupport.filePicker = WebFontFilePicker()
    PlatformImportSupport.destination = BrowserTabImportDestination
    // Capture: the file input, the download, and files dropped on the page (compile-verified only).
    installWebCaptureSupport(workspaceScope)

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

/**
 * A strip above the app that says what this build cannot do ([WEB_PREVIEW_NOTICE], the text About shows
 * too). It is plain DOM, so it is there before the first frame and stays when the app is busy.
 */
fun showPreviewBanner() {
    val banner = document.createElement("div")
    banner.setAttribute("id", PREVIEW_BANNER_ID)
    banner.setAttribute("role", "note")
    banner.setAttribute(
        "style",
        "flex:0 0 auto;padding:6px 14px;background:#17150F;color:#EFE9DC;" +
            "font:500 11px/1.4 ui-monospace,Menlo,Consolas,monospace;letter-spacing:0.02em",
    )
    banner.textContent = WEB_PREVIEW_NOTICE
    document.body?.insertBefore(banner, document.getElementById(ROOT_ELEMENT_ID))
}

/** The id of the Preview banner's element. */
const val PREVIEW_BANNER_ID: String = "typewright-preview-banner"
