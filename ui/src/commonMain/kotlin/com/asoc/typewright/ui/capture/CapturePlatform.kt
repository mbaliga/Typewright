// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.asoc.typewright.engine.trace.GrayscaleRaster
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** One file the person gave Capture, with every byte of it. */
class PickedFile(
    val name: String,
    val bytes: ByteArray,
)

/** Where a picture can come from. The plus menu lists the ones the platform offers. */
enum class CaptureSource(
    val label: String,
    val note: String,
) {
    CAMERA("Camera", "photograph a printed sheet · not yet tried on a phone"),
    GALLERY("Gallery", "pick a photo or a scan"),
    FILE("File", "an image, a scan or an SVG drawing"),
}

/** What a picker answered. */
sealed interface FilePickOutcome {
    class Picked(
        val files: List<PickedFile>,
    ) : FilePickOutcome

    /** The person closed the picker without choosing. */
    data object Cancelled : FilePickOutcome

    /** The picker could not be shown or a file could not be read; [message] says why in plain words. */
    data class Failed(
        val message: String,
    ) : FilePickOutcome
}

/**
 * Where Capture's pictures come from, one interface over each platform's own picker (a file dialog on the desktop, the system
 * document picker and gallery on Android, an `<input type=file>` in the browser). Each call asks the person; none reads a file
 * they did not choose (law 3).
 */
interface CaptureFilePicker {
    /** The sources this platform offers, in the order the plus menu lists them. The camera is listed only where [PlatformCaptureSupport.camera] is set. */
    val sources: List<CaptureSource>

    suspend fun pick(source: CaptureSource): FilePickOutcome
}

/** A picker for a build that has none wired: it offers nothing and answers every call with [reason]. */
class UnavailableCaptureFilePicker(
    private val reason: String,
) : CaptureFilePicker {
    override val sources: List<CaptureSource> get() = emptyList()

    override suspend fun pick(source: CaptureSource): FilePickOutcome = FilePickOutcome.Failed(reason)
}

/**
 * Takes the person to Templates (the sheets to print and the SVG pack) from a screen that has no other way there, such as New
 * project's "draw on paper". Null where nothing provided it, and the screens that read it then leave the link out.
 */
val LocalOpenTemplates: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/** What saving a file for the person came to. */
sealed interface ExportOutcome {
    /** Written; [where] says where in words ("typewright-latin-sheets-a4.pdf in Downloads"). */
    data class Saved(
        val where: String,
    ) : ExportOutcome

    /** Given to the browser as a download. The page cannot know whether the person kept it or where it went, so it says only that. */
    data class Handed(
        val sentence: String,
    ) : ExportOutcome

    data object Cancelled : ExportOutcome

    data class Failed(
        val message: String,
    ) : ExportOutcome
}

/** Hands a file to the person: a save dialog on the desktop, the system create-document screen on Android, a download in the browser. */
interface FileExporter {
    /** What this platform does, in a few words, for the button's note: "save dialog", "download". */
    val describe: String

    suspend fun export(
        suggestedName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ExportOutcome
}

/** An exporter for a build that has none wired. */
class UnavailableFileExporter(
    private val reason: String,
) : FileExporter {
    override val describe: String get() = "not available"

    override suspend fun export(
        suggestedName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ExportOutcome = ExportOutcome.Failed(reason)
}

/** A live camera for the Camera screen, bound for as long as the screen is on. */
interface CameraFeed {
    /** Draws the viewfinder. */
    @Composable
    fun Preview(modifier: Modifier)

    /** Starts (or, with null, stops) handing preview frames, as grey rasters of about 640 pixels on the long side, to [onFrame]. Called on a background thread. */
    fun analyse(onFrame: ((GrayscaleRaster) -> Unit)?)

    fun setFlash(on: Boolean)

    /** Takes the picture at full resolution and answers it as a JPEG, or null if the camera could not. */
    suspend fun takePhoto(): ByteArray?
}

/** Whether the camera can be used right now. */
sealed interface CameraAccess {
    class Ready(
        val feed: CameraFeed,
    ) : CameraAccess

    /** Android is showing its permission question. */
    data object Asking : CameraAccess

    data class Denied(
        val message: String,
    ) : CameraAccess

    data class Unavailable(
        val message: String,
    ) : CameraAccess
}

/** Supplies the camera. Only the Android app has one. */
fun interface CameraProvider {
    @Composable
    fun rememberAccess(): CameraAccess
}

/**
 * What each platform's `main` supplies before the first screen: its pickers, its way of saving a file, the camera where there is
 * one, and the files dropped on its window. The defaults are honest "unavailable" ones, so a build that wires none says so in the
 * UI instead of failing.
 */
object PlatformCaptureSupport {
    var picker: CaptureFilePicker = UnavailableCaptureFilePicker("This build has no file picker yet, so it cannot add a picture.")
    var exporter: FileExporter = UnavailableFileExporter("This build cannot save a file yet.")
    var camera: CameraProvider? = null

    /** Whether files can be dropped on the window, so Capture can say so. */
    var acceptsDrops: Boolean = false

    private val droppedFlow = MutableSharedFlow<List<PickedFile>>(extraBufferCapacity = DROP_BUFFER)
    private val noticeFlow = MutableStateFlow<String?>(null)

    /** Files dropped on the window; Capture adds them when it is on screen. */
    val dropped: SharedFlow<List<PickedFile>> get() = droppedFlow.asSharedFlow()

    /**
     * What the last drop could not do, in words, until Capture shows it: a file of a kind Capture does not take, one that is too
     * large, or a drop made while Capture was not on screen. A drop is never lost without saying so.
     */
    val dropNotice: StateFlow<String?> get() = noticeFlow.asStateFlow()

    /** Called by the platform when files are dropped. Files dropped while Capture is not open are not kept; the notice says so. */
    fun deliverDrop(files: List<PickedFile>) {
        if (files.isEmpty()) return
        if (droppedFlow.subscriptionCount.value == 0) {
            noticeFlow.value = "Files dropped outside Capture were not added. Open Capture, then drop them again."
            return
        }
        droppedFlow.tryEmit(files)
    }

    /** Called by the platform when a drop held files it did not pass on, with the reason in plain words. */
    fun reportDropProblem(message: String) {
        noticeFlow.value = message
    }

    /** Capture has shown the notice. */
    fun clearDropNotice() {
        noticeFlow.value = null
    }

    /** The sentence for files that were dropped but are not a kind Capture takes. */
    fun unsupportedDropNotice(names: List<String>): String =
        if (names.size == 1) {
            "“${names.single()}” was not added: file type not supported. Capture takes JPEG, PNG, GIF, BMP and SVG files."
        } else {
            "${names.size} files were not added: file type not supported. Capture takes JPEG, PNG, GIF, BMP and SVG files."
        }

    private const val DROP_BUFFER = 4
}

/** The picker Capture uses; the platform's own unless a screen or a test provides another. */
val LocalCaptureFilePicker: ProvidableCompositionLocal<CaptureFilePicker> = staticCompositionLocalOf { CurrentCapturePicker }

/** The exporter Templates uses; the platform's own unless a screen or a test provides another. */
val LocalFileExporter: ProvidableCompositionLocal<FileExporter> = staticCompositionLocalOf { CurrentExporter }

/** Looked up at each call rather than once: an Android activity that is recreated installs a new picker. */
private object CurrentCapturePicker : CaptureFilePicker {
    override val sources: List<CaptureSource>
        get() =
            (if (PlatformCaptureSupport.camera != null) listOf(CaptureSource.CAMERA) else emptyList()) +
                PlatformCaptureSupport.picker.sources.filter { it != CaptureSource.CAMERA }

    override suspend fun pick(source: CaptureSource): FilePickOutcome = PlatformCaptureSupport.picker.pick(source)
}

private object CurrentExporter : FileExporter {
    override val describe: String get() = PlatformCaptureSupport.exporter.describe

    override suspend fun export(
        suggestedName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ExportOutcome = PlatformCaptureSupport.exporter.export(suggestedName, mimeType, bytes)
}

/** Reads a picture file into a grey raster no larger than a given side, or null if it cannot be read. One actual per platform. */
interface ImageDecoder {
    suspend fun decodeGray(
        bytes: ByteArray,
        maxSide: Int = DEFAULT_MAX_SIDE,
    ): GrayscaleRaster?

    companion object {
        /** A 12-megapixel photograph is read down to this on its long side: finer than the cells need, and small enough to read quickly. */
        const val DEFAULT_MAX_SIDE: Int = 3000
    }
}

/** The platform's picture decoder: javax.imageio on the desktop, BitmapFactory on Android, the browser's own decoder on the web. */
expect fun platformImageDecoder(): ImageDecoder

/** The decoder Capture uses; the platform's own unless a test provides another. */
val LocalImageDecoder: ProvidableCompositionLocal<ImageDecoder> = staticCompositionLocalOf { platformImageDecoder() }
