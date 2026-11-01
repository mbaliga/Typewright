// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.asoc.typewright.ui.capture.CaptureFilePicker
import com.asoc.typewright.ui.capture.CaptureSource
import com.asoc.typewright.ui.capture.ExportOutcome
import com.asoc.typewright.ui.capture.FileExporter
import com.asoc.typewright.ui.capture.FilePickOutcome
import com.asoc.typewright.ui.capture.PickedFile
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

/** The largest picture read into memory; it only stops a wrong file from filling memory. */
internal const val MAX_PICTURE_BYTES: Long = 96L * 1024 * 1024

/**
 * Adds pictures on Android: the system photo picker for the Gallery (several photos), and the system document picker for File
 * (images and SVG drawings, several). Nothing is read until the person has chosen (law 3). The Camera is not a pick: it is the
 * Camera screen, which [com.asoc.typewright.ui.capture.PlatformCaptureSupport.camera] supplies.
 *
 * [registry] must be the host activity's and this must be built in `onCreate`, before the activity starts. [release] unregisters
 * the launchers and cancels a pick in flight. Compile-verified only: nothing here has run on a device (CLAUDE.md law 4); the owner
 * is asked to pick from the gallery and from Files, and to rotate the screen in the middle of a pick.
 */
class AndroidCaptureFilePicker(
    registry: ActivityResultRegistry,
    private val contentResolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CaptureFilePicker {
    private var pending: CancellableContinuation<List<Uri>>? = null

    private val gallery: ActivityResultLauncher<PickVisualMediaRequest> =
        registry.register("typewright-capture-gallery", ActivityResultContracts.PickMultipleVisualMedia()) { deliver(it) }
    private val files: ActivityResultLauncher<Array<String>> =
        registry.register("typewright-capture-files", ActivityResultContracts.OpenMultipleDocuments()) { deliver(it) }

    override val sources: List<CaptureSource> get() = listOf(CaptureSource.GALLERY, CaptureSource.FILE)

    override suspend fun pick(source: CaptureSource): FilePickOutcome {
        val chosen: List<Uri> =
            try {
                choose(source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return FilePickOutcome.Failed("Android could not open its picker: ${e.message ?: e::class.simpleName}.")
            }
        if (chosen.isEmpty()) return FilePickOutcome.Cancelled
        return withContext(io) { readAll(chosen) }
    }

    /** The activity is going away: stops listening for results and cancels a pick in flight. */
    fun release() {
        gallery.unregister()
        files.unregister()
        pending?.cancel()
        pending = null
    }

    private suspend fun choose(source: CaptureSource): List<Uri> =
        suspendCancellableCoroutine { continuation ->
            if (pending != null) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            when (source) {
                CaptureSource.GALLERY -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else -> files.launch(arrayOf("image/*", "image/svg+xml", "application/octet-stream"))
            }
        }

    private fun deliver(uris: List<Uri>) {
        val continuation = pending ?: return
        pending = null
        continuation.resume(uris)
    }

    private fun readAll(uris: List<Uri>): FilePickOutcome {
        val read = ArrayList<PickedFile>()
        for (uri in uris) {
            val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "picture"
            try {
                val stream = contentResolver.openInputStream(uri) ?: return FilePickOutcome.Failed("Android could not open “$name”.")
                val bytes = stream.use { readCapped(it, MAX_PICTURE_BYTES) }
                if (bytes == null) {
                    return FilePickOutcome.Failed(
                        "“$name” is larger than ${MAX_PICTURE_BYTES / BYTES_PER_MEGABYTE} MB, which is too large to be a picture of a sheet.",
                    )
                }
                read += PickedFile(name, bytes)
            } catch (e: SecurityException) {
                return FilePickOutcome.Failed("“$name” couldn't be read: access was refused.")
            } catch (e: IOException) {
                return FilePickOutcome.Failed("“$name” couldn't be read: ${e.message ?: e::class.simpleName}.")
            }
        }
        return FilePickOutcome.Picked(read)
    }

    private fun displayName(uri: Uri): String? =
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: RuntimeException) {
            null
        }
}

/** All of [input], or null when it holds more than [maxBytes]; reads at most one byte past the limit. */
internal fun readCapped(
    input: java.io.InputStream,
    maxBytes: Long,
): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > maxBytes) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private const val BUFFER_BYTES = 64 * 1024
private const val BYTES_PER_MEGABYTE = 1024L * 1024

/**
 * Saves a file on Android with the system's create-document screen (`CreateDocument`): the person chooses where, and the bytes are
 * written to what they chose. One launcher per kind of file the app makes (the sheets' PDF and the SVG pack's zip), registered up
 * front as the activity result registry requires. Compile-verified only (CLAUDE.md law 4).
 */
class AndroidFileExporter(
    registry: ActivityResultRegistry,
    private val contentResolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FileExporter {
    private var pending: CancellableContinuation<Uri?>? = null

    private val launchers: Map<String, ActivityResultLauncher<String>> =
        listOf("application/pdf", "application/zip").associateWith { mime ->
            registry.register("typewright-export-$mime", ActivityResultContracts.CreateDocument(mime)) { uri -> deliver(uri) }
        }

    override val describe: String get() = "the system's save screen"

    override suspend fun export(
        suggestedName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ExportOutcome {
        val launcher = launchers[mimeType] ?: return ExportOutcome.Failed("This kind of file ($mimeType) cannot be saved here.")
        val target: Uri? =
            try {
                suspendCancellableCoroutine { continuation ->
                    if (pending != null) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    pending = continuation
                    continuation.invokeOnCancellation { pending = null }
                    launcher.launch(suggestedName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ExportOutcome.Failed("Android could not open its save screen: ${e.message ?: e::class.simpleName}.")
            }
        if (target == null) return ExportOutcome.Cancelled
        return withContext(io) {
            try {
                val stream =
                    contentResolver.openOutputStream(target)
                        ?: return@withContext ExportOutcome.Failed("Android could not open the file to write.")
                stream.use { it.write(bytes) }
                ExportOutcome.Saved(suggestedName)
            } catch (e: SecurityException) {
                ExportOutcome.Failed("$suggestedName couldn't be written: access was refused.")
            } catch (e: IOException) {
                ExportOutcome.Failed("$suggestedName couldn't be written: ${e.message ?: e::class.simpleName}.")
            }
        }
    }

    /** The activity is going away: stops listening for results and cancels a save in flight. */
    fun release() {
        launchers.values.forEach { it.unregister() }
        pending?.cancel()
        pending = null
    }

    private fun deliver(uri: Uri?) {
        val continuation = pending ?: return
        pending = null
        continuation.resume(uri)
    }
}
