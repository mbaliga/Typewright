// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import com.asoc.typewright.ui.capture.CaptureFilePicker
import com.asoc.typewright.ui.capture.CaptureSource
import com.asoc.typewright.ui.capture.ExportOutcome
import com.asoc.typewright.ui.capture.FileExporter
import com.asoc.typewright.ui.capture.FilePickOutcome
import com.asoc.typewright.ui.capture.PickedFile
import com.asoc.typewright.ui.capture.PlatformCaptureSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

/** The largest picture read into memory: a 60-megapixel scan is about this; it only stops a wrong file from filling memory. */
internal const val MAX_PICTURE_BYTES: Long = 96L * 1024 * 1024

internal const val NO_SCREEN: String = "This computer has no screen to show a file dialog on, so a file can't be chosen or saved here."

/** File endings Capture reads: the pictures the desktop can decode, and SVG drawings. */
internal val PICTURE_ENDINGS: Set<String> = setOf("jpg", "jpeg", "png", "gif", "bmp", "svg")

/**
 * Adds a picture or an SVG on the desktop: `java.awt.FileDialog` with several files allowed, shown on the AWT event thread. Only
 * File is offered (there is no camera or gallery here); a file may also be dropped on the window. Nothing is read until the
 * person has chosen (law 3). Every way it can fail answers [FilePickOutcome.Failed] with a plain sentence.
 *
 * Owner-verified only: this container has no display, so the dialog has never been shown here (CLAUDE.md law 4);
 * [readPictures], which does everything after it, is tested.
 */
class DesktopCaptureFilePicker(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CaptureFilePicker {
    override val sources: List<CaptureSource> get() = listOf(CaptureSource.FILE)

    override suspend fun pick(source: CaptureSource): FilePickOutcome =
        withContext(io) {
            val chosen =
                try {
                    onEventThread { chooseFiles() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.awt.HeadlessException) {
                    return@withContext FilePickOutcome.Failed(NO_SCREEN)
                } catch (e: Throwable) {
                    return@withContext FilePickOutcome.Failed("The file dialog could not be shown: ${e.message ?: e::class.simpleName}.")
                }
            if (chosen.isEmpty()) FilePickOutcome.Cancelled else readPictures(chosen)
        }

    private fun chooseFiles(): List<File> {
        val dialog = FileDialog(null as Frame?, "Add a picture or an SVG drawing", FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in PICTURE_ENDINGS }
        dialog.isVisible = true
        val files = dialog.files.toList()
        dialog.dispose()
        return files
    }
}

/** Reads each of [files] whole, or answers [FilePickOutcome.Failed] naming the first that is too large or cannot be read. */
internal fun readPictures(
    files: List<File>,
    maxBytes: Long = MAX_PICTURE_BYTES,
): FilePickOutcome {
    val read = ArrayList<PickedFile>()
    for (file in files) {
        try {
            if (file.length() > maxBytes) {
                return FilePickOutcome.Failed(
                    "“${file.name}” is larger than ${maxBytes / BYTES_PER_MEGABYTE} MB, which is too large to be a picture of a sheet.",
                )
            }
            read += PickedFile(file.name, file.readBytes())
        } catch (e: IOException) {
            return FilePickOutcome.Failed("“${file.name}” couldn't be read: ${e.message ?: e::class.simpleName}.")
        } catch (e: SecurityException) {
            return FilePickOutcome.Failed("“${file.name}” couldn't be read: access was refused.")
        }
    }
    return FilePickOutcome.Picked(read)
}

private const val BYTES_PER_MEGABYTE = 1024L * 1024

/** What a drop came to: the files to add, and a sentence for the ones that were not (null when every file was taken). */
internal class DropRead(
    val files: List<PickedFile>,
    val notice: String?,
)

/**
 * Files dropped on the window, from whatever the drag carried: the ones with a picture or SVG ending, each read whole. A file of
 * another kind, a folder, or one that is too large or cannot be read is named in [DropRead.notice] and does not stop the others.
 */
internal fun readDroppedFiles(
    files: List<File>,
    maxBytes: Long = MAX_PICTURE_BYTES,
): DropRead {
    val (wanted, other) = files.partition { it.isFile && it.extension.lowercase() in PICTURE_ENDINGS }
    val taken = ArrayList<PickedFile>()
    val sentences = ArrayList<String>()
    if (other.isNotEmpty()) sentences += PlatformCaptureSupport.unsupportedDropNotice(other.map { it.name })
    for (file in wanted) {
        when (val read = readPictures(listOf(file), maxBytes)) {
            is FilePickOutcome.Picked -> taken += read.files
            is FilePickOutcome.Failed -> sentences += "Not added: ${read.message}"
            FilePickOutcome.Cancelled -> Unit
        }
    }
    return DropRead(taken, sentences.joinToString(" ").ifEmpty { null })
}

/** [readDroppedFiles] without the notice: the files taken, or null when none were. */
internal fun readDropped(files: List<File>): List<PickedFile>? = readDroppedFiles(files).files.ifEmpty { null }

/**
 * Saves a file on the desktop: `java.awt.FileDialog` in save mode, then the bytes are written to the place chosen. Cancelling
 * writes nothing. Owner-verified only for the dialog; [writeExport], which does the writing, is tested.
 */
class DesktopFileExporter(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FileExporter {
    override val describe: String get() = "a save dialog"

    override suspend fun export(
        suggestedName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ExportOutcome =
        withContext(io) {
            val target =
                try {
                    onEventThread { chooseTarget(suggestedName) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.awt.HeadlessException) {
                    return@withContext ExportOutcome.Failed(NO_SCREEN)
                } catch (e: Throwable) {
                    return@withContext ExportOutcome.Failed("The save dialog could not be shown: ${e.message ?: e::class.simpleName}.")
                }
            if (target == null) ExportOutcome.Cancelled else writeExport(target, bytes)
        }

    private fun chooseTarget(suggestedName: String): File? {
        val dialog = FileDialog(null as Frame?, "Save $suggestedName", FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()
        return if (directory == null || file == null) null else File(directory, file)
    }
}

/** Writes [bytes] to [target], and says where in words; a failure says why. */
internal fun writeExport(
    target: File,
    bytes: ByteArray,
): ExportOutcome =
    try {
        target.writeBytes(bytes)
        ExportOutcome.Saved("${target.name} in ${target.parentFile?.name ?: "its folder"}")
    } catch (e: IOException) {
        ExportOutcome.Failed("${target.name} couldn't be written: ${e.message ?: e::class.simpleName}.")
    } catch (e: SecurityException) {
        ExportOutcome.Failed("${target.name} couldn't be written: access was refused.")
    }
