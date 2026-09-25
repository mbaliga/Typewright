// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * S02's folder picker on the desktop (docs/PROJECT_MODEL.md §5, D9): `zenity
 * --file-selection --directory`, then `kdialog --getexistingdirectory`, then a Swing
 * `JFileChooser(DIRECTORIES_ONLY)` with the system look-and-feel. Each is a subprocess or a
 * library call, never linked; the first one that is actually installed (or, for Swing, that a
 * display is available for) answers. Returns null when the user cancels, or when none of the
 * three could be shown at all (no binaries, no display -- this sandbox, for example).
 */
class DesktopFolderPicker {
    suspend fun pick(initialDirectory: Path? = null): Path? =
        withContext(Dispatchers.IO) {
            tryZenity(initialDirectory) ?: tryKdialog(initialDirectory) ?: trySwing(initialDirectory)
        }

    private fun tryZenity(initialDirectory: Path?): Path? {
        val command = mutableListOf("zenity", "--file-selection", "--directory")
        if (initialDirectory != null) command += "--filename=$initialDirectory/"
        return runForSelectedPath(command)
    }

    private fun tryKdialog(initialDirectory: Path?): Path? {
        val startDirectory = initialDirectory ?: Path.of(System.getProperty("user.home"))
        return runForSelectedPath(listOf("kdialog", "--getexistingdirectory", startDirectory.toString()))
    }

    // Runs [command], reading the chosen path from stdout (zenity and kdialog both print it, one
    // line, on cancel-free success). Null on a non-zero exit (cancel), empty output, or the
    // binary not being installed at all (an IOException starting the process).
    private fun runForSelectedPath(command: List<String>): Path? =
        try {
            val process = ProcessBuilder(command).redirectErrorStream(false).start()
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotEmpty()) Path.of(output) else null
        } catch (_: IOException) {
            null
        }

    private fun trySwing(initialDirectory: Path?): Path? =
        try {
            var chosen: Path? = null
            SwingUtilities.invokeAndWait {
                runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
                val chooser = JFileChooser(initialDirectory?.toFile())
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.isAcceptAllFileFilterUsed = false
                chooser.dialogTitle = "Choose a folder"
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chosen = chooser.selectedFile.toPath()
                }
            }
            chosen
        } catch (_: Exception) {
            // Headless (no display, as in this build's own sandbox), or the dialog otherwise
            // couldn't be shown; owner-verified on a real desktop (CLAUDE.md law 4).
            null
        }
}
