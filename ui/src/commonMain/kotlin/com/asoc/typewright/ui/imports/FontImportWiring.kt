// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.glass.PaletteEntry
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.shell.LocalSectionNav

/**
 * The app's one import flow, for every screen to start ("Open a font…" in the command palette, the
 * choices on Projects). Provided once near the root, together with a [FontImportHost] last in the
 * root layout; null where nothing provided it, in which case a screen that offers the flow hosts its
 * own (Projects does) and the palette says plainly that it is not available there.
 */
val LocalFontImport: ProvidableCompositionLocal<FontImportController?> = staticCompositionLocalOf { null }

/**
 * A [FontImportController] over the app's workspace, the platform's picker and destination
 * ([LocalFontImport]'s providers), remembered for the composition. [onImported] runs with the new
 * session once it is open: the router sends the person to Home. Null with no workspace.
 */
@Composable
fun rememberFontImportController(
    /** The workspace the project is made in; the app's, unless the router (which provides [LocalProjectWorkspace] below itself) passes the one it holds. */
    inWorkspace: ProjectWorkspace? = LocalProjectWorkspace.current,
    onImported: (ProjectSession) -> Unit = {},
): FontImportController? {
    val workspace = inWorkspace ?: return null
    val picker = LocalFontFilePicker.current
    val destination = LocalImportDestination.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onImported)
    return remember(workspace, picker, destination, scope) {
        FontImportController(workspace, picker, destination, scope, onImported = { latest.value(it) })
    }
}

/**
 * The command palette's "Open a font…" row. It starts the app's import flow over the section the
 * palette was opened on ([LocalSectionNav]'s current one), so the licence check (S03) appears over
 * that section and Close returns to it; [dismissPalette] closes the palette first. Where nothing
 * provided [LocalFontImport] the row stays, and says plainly that the flow is not available there.
 */
@Composable
fun rememberFontImportPaletteEntries(dismissPalette: () -> Unit): List<PaletteEntry> {
    val controller = LocalFontImport.current
    val from = LocalSectionNav.current?.current
    val dismiss = rememberUpdatedState(dismissPalette)
    return remember(controller, from) {
        listOf(
            PaletteEntry(
                id = "font-open",
                label = "Open a font…",
                description = "a .ttf file; its licence is checked first",
                run = {
                    if (controller == null) {
                        "Open a font…: not available on this screen. Use Open a font on Projects."
                    } else {
                        dismiss.value()
                        controller.openFont(from)
                        "Open a font…: choosing a file"
                    }
                },
            ),
        )
    }
}
