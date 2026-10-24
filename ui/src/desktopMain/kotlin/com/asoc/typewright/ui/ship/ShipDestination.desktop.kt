// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.project.FolderTreeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.io.File
import javax.swing.JFileChooser

/** The desktop's destination: a folder the person picks, written by [FolderTreeWriter]. */
actual fun platformShipDestination(): ShipDestination = DesktopShipDestination()

/**
 * A folder on the computer, chosen with a Swing folder chooser shown on the event thread, and written all at once by [FolderTreeWriter]:
 * the whole tree is made next to the folder first and moved in only when it is complete, so cancelling leaves the folder as it was.
 *
 * Owner-verified only: this container has no display, so the chooser has never been shown here (CLAUDE.md law 4). What happens after it is
 * tested, and a computer with no display answers [ShipChoice.Failed] in plain words rather than throwing.
 */
class DesktopShipDestination(
    private val chooser: (title: String, start: File?) -> File? = ::chooseFolderOnEventThread,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val home: String? = System.getProperty("user.home"),
) : ShipDestination {
    override val exit: ShipExit get() = ShipExit.FOLDER

    override suspend fun choose(
        folderName: String,
        previous: String?,
    ): ShipChoice =
        withContext(io) {
            val start = previous?.let { File(expandHome(it)) }?.takeIf { it.exists() }?.let { it.parentFile ?: it }
            val folder =
                try {
                    chooser("Choose the folder to write $folderName to", start)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HeadlessException) {
                    return@withContext ShipChoice.Failed(
                        "This computer has no screen to show a folder chooser on, so a folder can't be chosen here.",
                    )
                } catch (e: Exception) {
                    return@withContext ShipChoice.Failed("The folder chooser could not be shown: ${e.message ?: e::class.simpleName}.")
                }
            if (folder == null) {
                ShipChoice.Cancelled
            } else {
                val label = ShipText.shortPath(folder.absolutePath, home)
                ShipChoice.Chosen(ShipTarget(label, FolderTreeWriter(folder.toPath(), label)))
            }
        }

    private fun expandHome(path: String): String =
        if (path.startsWith("~/") &&
            home != null
        ) {
            home.trimEnd('/') + path.removePrefix("~")
        } else {
            path
        }
}

/** Shows a folder chooser on the AWT event thread and answers the folder, or null on cancel. Throws [HeadlessException] where there is no display. */
private fun chooseFolderOnEventThread(
    title: String,
    start: File?,
): File? {
    if (GraphicsEnvironment.isHeadless()) throw HeadlessException()
    var result: Result<File?>? = null
    EventQueue.invokeAndWait {
        result =
            runCatching {
                val chooser = JFileChooser(start)
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.isAcceptAllFileFilterUsed = false
                chooser.dialogTitle = title
                if (chooser.showDialog(null, "Choose") == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            }
    }
    return checkNotNull(result).getOrThrow()
}
