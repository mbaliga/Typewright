// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * S02's folder picker on the desktop (docs/PROJECT_MODEL.md §5, D9): `zenity
 * --file-selection --directory`, then `kdialog --getexistingdirectory`, then a Swing
 * `JFileChooser(DIRECTORIES_ONLY)` with the system look-and-feel. Each is a subprocess or a
 * library call, never linked; inside a Flatpak or Snap only Swing is tried ([pickerOrder]); the
 * first one that is actually installed answers. Returns null when the user cancels, or when none
 * of the three could be shown at all (no binaries, or no display).
 *
 * Every back-end is a modal dialog that waits for a person for as long as it takes, so nothing is
 * tried unless [hasDisplay] says there is a screen to show it on. A test passes `{ false }`: under
 * `xvfb-run` there is a display and nobody to answer, and the dialog would wait forever.
 */
class DesktopFolderPicker(
    private val sandbox: LinuxSandbox = LinuxSandbox.detect(),
    private val hasDisplay: () -> Boolean = { displayAvailable() },
) {
    suspend fun pick(initialDirectory: Path? = null): Path? =
        withContext(Dispatchers.IO) {
            if (!hasDisplay()) {
                null
            } else {
                pickerOrder(sandbox).firstNotNullOfOrNull { backend ->
                    when (backend) {
                        PickerBackend.Zenity -> tryZenity(initialDirectory)
                        PickerBackend.Kdialog -> tryKdialog(initialDirectory)
                        PickerBackend.Swing -> trySwing(initialDirectory)
                    }
                }
            }
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
            // The dialog couldn't be shown; owner-verified on a real desktop (CLAUDE.md law 4).
            null
        }
}

/**
 * True when a dialog has somewhere to appear: an X11 or Wayland display is named in [env], or AWT
 * itself is not headless (macOS and Windows name no display variable). Pure: both inputs are
 * injected so it is tested on any host.
 */
fun displayAvailable(
    env: Map<String, String> = System.getenv(),
    awtHeadless: Boolean = GraphicsEnvironment.isHeadless(),
): Boolean = !env["DISPLAY"].isNullOrEmpty() || !env["WAYLAND_DISPLAY"].isNullOrEmpty() || !awtHeadless
