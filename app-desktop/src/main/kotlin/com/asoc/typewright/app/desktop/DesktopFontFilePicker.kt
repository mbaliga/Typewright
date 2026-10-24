// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import com.asoc.typewright.project.imports.UfoFolderRead
import com.asoc.typewright.project.imports.readUfoFolder
import com.asoc.typewright.ui.imports.FontFilePicker
import com.asoc.typewright.ui.imports.PickResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JFileChooser

/** The largest font file the picker will read into memory. Real fonts are far smaller (a large CJK font is about 25 MB); this only stops a wrong file from filling memory. */
internal const val MAX_FONT_BYTES: Long = 128L * 1024 * 1024

internal const val NO_DISPLAY_MESSAGE: String = "This computer has no screen to show a file dialog on, so a font can't be chosen here."

/**
 * "Open a font…" on the desktop: `java.awt.FileDialog` for a `.ttf` or `.otf` file, and a Swing
 * `JFileChooser` for a UFO folder (a `FileDialog` cannot choose folders on Linux). Both are
 * modal and are shown on the AWT event thread; nothing is read until the person has chosen. Every
 * way it can fail answers [PickResult.Failed] with a plain sentence: no display, an unreadable
 * file, a path this computer cannot open. Cancelling answers [PickResult.Cancelled].
 *
 * Owner-verified only: this container has no display, so the dialogs themselves have never been
 * shown here (CLAUDE.md law 4); [readPickedFont] and [readPickedUfo], which do everything after the
 * dialog, are tested.
 */
class DesktopFontFilePicker(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FontFilePicker {
    override val canPickUfoFolder: Boolean get() = true
    override val ufoFolderUnavailableReason: String? get() = null

    override suspend fun pickFont(): PickResult =
        withContext(io) {
            val chosen =
                try {
                    onEventThread { chooseFontFile() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    return@withContext PickResult.Failed(dialogFailure(e))
                }
            readPickedFont(chosen?.first, chosen?.second)
        }

    override suspend fun pickUfoFolder(): PickResult =
        withContext(io) {
            val chosen =
                try {
                    onEventThread { chooseFolder("Choose a .ufo folder") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    return@withContext PickResult.Failed(dialogFailure(e))
                }
            readPickedUfo(chosen)
        }

    private fun chooseFontFile(): Pair<String, String>? {
        val dialog = FileDialog(null as Frame?, "Open a font", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".ttf") || it.endsWith(".otf") } }
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()
        return if (directory == null || file == null) null else directory to file
    }
}

/** Shows a Swing folder chooser on the event thread and answers the chosen folder, or null on cancel. Throws where there is no display. */
internal fun chooseFolder(title: String): File? =
    onEventThread {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        chooser.dialogTitle = title
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

/** Runs [block] on the AWT event thread and answers its result; a failure inside it is rethrown as itself. Throws [java.awt.HeadlessException] where there is no display. */
internal fun <T> onEventThread(block: () -> T): T {
    if (GraphicsEnvironment.isHeadless()) throw java.awt.HeadlessException()
    var result: Result<T>? = null
    EventQueue.invokeAndWait { result = runCatching(block) }
    return checkNotNull(result).getOrThrow()
}

/** The plain sentence for a dialog that could not be shown. */
internal fun dialogFailure(e: Throwable): String =
    when (e) {
        is java.awt.HeadlessException -> NO_DISPLAY_MESSAGE
        is InvalidPathException -> "That file's name isn't one this computer can open."
        else -> "The file dialog could not be shown: ${e.message ?: e::class.simpleName}."
    }

/**
 * Reads the font the dialog chose, [fileName] inside [directory], or answers what went wrong:
 * [PickResult.Cancelled] when nothing was chosen, [PickResult.Failed] for a name this computer
 * cannot turn into a path, something that is not a file, a file larger than [maxBytes], or one the
 * operating system will not let us read.
 */
internal fun readPickedFont(
    directory: String?,
    fileName: String?,
    maxBytes: Long = MAX_FONT_BYTES,
): PickResult {
    if (fileName == null) return PickResult.Cancelled
    return try {
        val path = Path.of(directory.orEmpty(), fileName)
        if (!Files.isRegularFile(path)) return PickResult.Failed("“$fileName” isn't a file.")
        val size = Files.size(path)
        if (size > maxBytes) return PickResult.Failed("“$fileName” is ${size / BYTES_PER_MEGABYTE} MB, which is too large to be a font.")
        PickResult.Font(path.fileName.toString(), Files.readAllBytes(path))
    } catch (e: InvalidPathException) {
        PickResult.Failed("That file's name isn't one this computer can open.")
    } catch (e: IOException) {
        PickResult.Failed("“$fileName” couldn't be read: ${e.message ?: e::class.simpleName}.")
    } catch (e: SecurityException) {
        PickResult.Failed("“$fileName” couldn't be read: access was refused.")
    }
}

/** Reads the UFO folder the dialog chose, or answers [PickResult.Cancelled] when nothing was chosen. */
internal fun readPickedUfo(folder: File?): PickResult {
    if (folder == null) return PickResult.Cancelled
    return try {
        when (val read = readUfoFolder(folder.toPath())) {
            is UfoFolderRead.Read -> PickResult.Ufo(read.folder)
            is UfoFolderRead.Failed -> PickResult.Failed(read.message)
        }
    } catch (e: InvalidPathException) {
        PickResult.Failed("That folder's path isn't one this computer can open.")
    }
}

private const val BYTES_PER_MEGABYTE = 1024L * 1024
