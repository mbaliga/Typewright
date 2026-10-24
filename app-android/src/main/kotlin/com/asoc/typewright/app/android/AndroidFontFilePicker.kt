// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.ui.imports.FontFilePicker
import com.asoc.typewright.ui.imports.ImportDestination
import com.asoc.typewright.ui.imports.PickResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume

/** The largest font file read into memory. Real fonts are far smaller; this only stops a wrong file from filling memory. */
internal const val MAX_FONT_BYTES: Long = 128L * 1024 * 1024

internal const val NO_UFO_ON_ANDROID: String =
    "Opening a UFO folder isn't offered on Android: the system file picker gives Typewright one file at a time, " +
        "not a folder it can read as a UFO, and adopting a UFO in place needs the folder's path, which Android does not give an app. " +
        "Open the font file itself instead, or open the UFO on a desktop."

/**
 * "Open a font…" on Android: the system document picker (`ActivityResultContracts.OpenDocument`),
 * then a read of the chosen document through the content resolver. Nothing is read until the person
 * has chosen a document (law 3). A UFO folder is not offered ([NO_UFO_ON_ANDROID] says why).
 *
 * [registry] must be the host activity's, and this must be built in `onCreate`, before the activity
 * is started, as the registry's contract requires. When the activity is destroyed ([release]) the
 * launcher is unregistered, and a pick in flight is cancelled with it, so nothing waits on a result
 * that has no screen to go to. If Android delivers a result to a recreated activity after the old
 * one's pick was cancelled, it has nothing waiting and is dropped; the person chooses the font
 * again. This is compile-verified only: nothing here has run on a device (CLAUDE.md law 4), and the
 * owner is asked to try a pick with the screen rotated in the middle.
 */
class AndroidFontFilePicker(
    registry: ActivityResultRegistry,
    private val contentResolver: ContentResolver,
    key: String = "typewright-open-font",
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FontFilePicker {
    private var pending: CancellableContinuation<Uri?>? = null

    private val launcher: ActivityResultLauncher<Array<String>> =
        registry.register(key, ActivityResultContracts.OpenDocument()) { uri -> deliver(uri) }

    override val canPickUfoFolder: Boolean get() = false
    override val ufoFolderUnavailableReason: String get() = NO_UFO_ON_ANDROID

    override suspend fun pickUfoFolder(): PickResult = PickResult.Failed(NO_UFO_ON_ANDROID)

    override suspend fun pickFont(): PickResult {
        val chosen: Uri? =
            try {
                chooseDocument()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return PickResult.Failed("Android could not open its file picker: ${e.message ?: e::class.simpleName}.")
            }
        if (chosen == null) return PickResult.Cancelled
        return withContext(io) { readDocument(chosen) }
    }

    /** The activity is going away: stops listening for results and cancels a pick in flight. */
    fun release() {
        launcher.unregister()
        pending?.cancel()
        pending = null
    }

    private suspend fun chooseDocument(): Uri? =
        suspendCancellableCoroutine { continuation ->
            if (pending != null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            launcher.launch(FONT_MIME_TYPES)
        }

    private fun deliver(uri: Uri?) {
        val continuation = pending ?: return
        pending = null
        continuation.resume(uri)
    }

    private fun readDocument(uri: Uri): PickResult {
        val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "font"
        return try {
            val stream = contentResolver.openInputStream(uri) ?: return PickResult.Failed("Android could not open “$name”.")
            stream.use { readFontStream(name, it, MAX_FONT_BYTES) }
        } catch (e: SecurityException) {
            PickResult.Failed("“$name” couldn't be read: access was refused.")
        } catch (e: IOException) {
            PickResult.Failed("“$name” couldn't be read: ${e.message ?: e::class.simpleName}.")
        }
    }

    private fun displayName(uri: Uri): String? =
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: RuntimeException) {
            null
        }

    private companion object {
        /** Font files are labelled `font/ttf`, `application/x-font-ttf` or, by providers that do not know, `application/octet-stream`. */
        val FONT_MIME_TYPES: Array<String> =
            arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")
    }
}

/**
 * Reads all of [input] as the font named [name], or answers [PickResult.Failed] in plain words when it
 * holds more than [maxBytes]. It reads at most one byte past the limit, so a wrong, huge file is
 * refused without being held in memory.
 */
internal fun readFontStream(
    name: String,
    input: InputStream,
    maxBytes: Long,
): PickResult {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total >
            maxBytes
        ) {
            return PickResult.Failed("“$name” is larger than ${maxBytes / BYTES_PER_MEGABYTE} MB, which is too large to be a font.")
        }
        out.write(buffer, 0, n)
    }
    return PickResult.Font(name, out.toByteArray())
}

private const val BUFFER_BYTES = 64 * 1024
private const val BYTES_PER_MEGABYTE = 1024L * 1024

/**
 * Where a project made from an imported font is kept on Android: the person is asked for a folder
 * with the system folder picker when they press Import ([choose], `MainActivity.pickProjectParentFolder`),
 * and `<name>/` is made inside it through the SAF storage provider.
 */
class AndroidImportDestination(
    private val choose: suspend () -> ProjectLocation?,
) : ImportDestination {
    override val persists: Boolean get() = true

    override suspend fun parentFolder(): ProjectLocation? = null

    override suspend fun chooseParentFolder(): ProjectLocation? = choose()

    override fun describe(
        parent: ProjectLocation?,
        folderName: String,
    ): String =
        "When you press Import, Android asks which folder to keep the project in, then Typewright makes a folder named $folderName there and writes the project in it."
}
