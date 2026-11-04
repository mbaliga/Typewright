// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.project.FolderTreeWriter
import com.asoc.typewright.project.storage.DesktopFolderPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.io.File

/** The desktop's destination: a folder the person picks, written by [FolderTreeWriter]. */
actual fun platformShipDestination(): ShipDestination = DesktopShipDestination()

/**
 * A folder on the computer, chosen with the folder picker the project folder uses (`zenity`, then `kdialog`, then a Swing chooser with the
 * system look), and written all at once by [FolderTreeWriter]: the whole tree is made next to the folder first and moved in only when it is
 * complete, so cancelling leaves the folder as it was.
 *
 * Owner-verified only: this container has no display, so the chooser has never been shown here (CLAUDE.md law 4). What happens after it is
 * tested, and a computer with no display answers [ShipChoice.Failed] in plain words rather than throwing.
 */
class DesktopShipDestination(
    private val chooser: suspend (title: String, start: File?) -> File? = ::chooseWithProjectPicker,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val home: String? = System.getProperty("user.home"),
    private val headless: () -> Boolean = GraphicsEnvironment::isHeadless,
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
                    return@withContext ShipChoice.Failed(NO_SCREEN)
                } catch (e: Exception) {
                    return@withContext ShipChoice.Failed("The folder chooser could not be shown: ${e.message ?: e::class.simpleName}.")
                }
            if (folder == null) {
                // The shared picker answers null both for a person who backed out and for a computer that could show nothing; with no
                // display at all it is the second.
                if (headless()) ShipChoice.Failed(NO_SCREEN) else ShipChoice.Cancelled
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

    private companion object {
        const val NO_SCREEN = "This computer has no screen to show a folder chooser on, so a folder can't be chosen here."
    }
}

/** The project folder's picker, asked for a folder starting near [start]; the title is the picker's own. */
private suspend fun chooseWithProjectPicker(
    title: String,
    start: File?,
): File? = DesktopFolderPicker().pick(start?.toPath())?.toFile()
