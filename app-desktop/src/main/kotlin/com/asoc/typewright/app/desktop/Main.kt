// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.asoc.typewright.ui.TypewrightApp
import com.asoc.typewright.ui.capture.PlatformCaptureSupport
import com.asoc.typewright.ui.imports.PlatformImportSupport
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.DataFlavor
import java.io.File

/** The window title. */
const val APP_TITLE: String = "Typewright"

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun main() {
    // P11 WP5 (docs/PROJECT_MODEL.md §13): one ProjectWorkspace for the app's whole run, built
    // and held here rather than inside TypewrightApp's own default, so onCloseRequest below can
    // flush this exact instance even as the composition itself is torn down by exitApplication().
    val workspaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // "Open a font…": the desktop's file dialogs, and a folder on disk for the project (docs/PROJECT_MODEL.md §13).
    PlatformImportSupport.filePicker = DesktopFontFilePicker()
    PlatformImportSupport.destination = DesktopImportDestination()
    val workspace = createProjectWorkspace(workspaceScope, storage = DesktopImportStorage())
    // Capture: pictures and SVG drawings from a file dialog or dropped on the window, and the sheets saved with a save dialog.
    PlatformCaptureSupport.picker = DesktopCaptureFilePicker()
    PlatformCaptureSupport.exporter = DesktopFileExporter()
    PlatformCaptureSupport.acceptsDrops = true
    val dropTarget =
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dropped =
                    try {
                        (
                            event.awtTransferable.getTransferData(
                                DataFlavor.javaFileListFlavor,
                            ) as? List<*>
                        )?.filterIsInstance<File>().orEmpty()
                    } catch (e: java.io.IOException) {
                        PlatformCaptureSupport.reportDropProblem(
                            "The dropped files could not be read: ${e.message ?: "the drag ended early"}.",
                        )
                        return true
                    } catch (e: java.awt.datatransfer.UnsupportedFlavorException) {
                        PlatformCaptureSupport.reportDropProblem("What was dropped is not a file, so Capture did not take it.")
                        return true
                    }
                workspaceScope.launch(Dispatchers.IO) {
                    val read = readDroppedFiles(dropped)
                    read.notice?.let(PlatformCaptureSupport::reportDropProblem)
                    PlatformCaptureSupport.deliverDrop(read.files)
                }
                return dropped.isNotEmpty()
            }
        }

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
            Box(
                Modifier.fillMaxSize().dragAndDropTarget(
                    shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
                    target = dropTarget,
                ),
            ) {
                TypewrightApp(workspace = workspace)
            }
        }
    }
}
