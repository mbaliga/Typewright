// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.imports.UfoFolder
import com.asoc.typewright.ui.project.IN_MEMORY_WORKSPACE_ROOT
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** What a platform file picker answered. */
sealed interface PickResult {
    /** A font file: its name and every byte of it. */
    class Font(
        val name: String,
        val bytes: ByteArray,
    ) : PickResult

    /** A UFO folder, read. */
    class Ufo(
        val folder: UfoFolder,
    ) : PickResult

    /** The person closed the picker without choosing. */
    data object Cancelled : PickResult

    /** The picker could not be shown or the file could not be read; [message] says why in plain words. */
    data class Failed(
        val message: String,
    ) : PickResult
}

/**
 * Where "Open a font" gets its file from: one interface over each platform's own picker (a file
 * dialog on the desktop, the system document picker on Android, an `<input type=file>` in the
 * browser), so the import flow is the same everywhere and testable with a fake. Each call asks the
 * person; none reads a file they did not choose (law 3).
 */
interface FontFilePicker {
    /** Asks for a `.ttf` or `.otf` font file. */
    suspend fun pickFont(): PickResult

    /** Whether a UFO folder can be chosen on this platform. */
    val canPickUfoFolder: Boolean

    /** Why a UFO folder cannot be chosen here, in a sentence (null when it can). */
    val ufoFolderUnavailableReason: String?

    /** Asks for a UFO folder; answers [PickResult.Failed] with [ufoFolderUnavailableReason] where that is not offered. */
    suspend fun pickUfoFolder(): PickResult
}

/** A picker for a build that has none wired: it answers every call with [reason] and offers no UFO folders. */
class UnavailableFontFilePicker(
    private val reason: String,
) : FontFilePicker {
    override suspend fun pickFont(): PickResult = PickResult.Failed(reason)

    override val canPickUfoFolder: Boolean get() = false
    override val ufoFolderUnavailableReason: String get() = reason

    override suspend fun pickUfoFolder(): PickResult = PickResult.Failed(reason)
}

/**
 * Where a project made from an imported font is kept, and how the audit says so. A platform whose
 * storage can be chosen without asking supplies the folder ([parentFolder]); one that must ask
 * returns null and the import asks when the person presses Import ([chooseParentFolder]); one with
 * no storage keeps the project in memory and says so.
 */
interface ImportDestination {
    /** Whether projects made here outlive the app. */
    val persists: Boolean

    /** The folder the project folder is made in, or null where it has to be asked for. */
    suspend fun parentFolder(): ProjectLocation?

    /** Asks the person for a folder, or null if they decline. Called only when [parentFolder] is null. */
    suspend fun chooseParentFolder(): ProjectLocation? = null

    /** Where `<folderName>/` will be written, in words, for the audit: a path, a plain sentence about being asked, or a plain sentence about memory. */
    fun describe(
        parent: ProjectLocation?,
        folderName: String,
    ): String
}

/** The destination of a build with no storage of its own: the project lives in memory and the audit says so. */
object InMemoryImportDestination : ImportDestination {
    override val persists: Boolean get() = false

    override suspend fun parentFolder(): ProjectLocation = IN_MEMORY_WORKSPACE_ROOT

    override fun describe(
        parent: ProjectLocation?,
        folderName: String,
    ): String = "Nothing is written to disk: this build keeps the project in memory, so it is gone when you close the app."
}

/**
 * What each platform's `main` supplies before the first screen: its file picker and where imported
 * projects go. The defaults are an honest "unavailable" picker and the in-memory destination, so a
 * build that wires neither says so in the UI instead of failing.
 */
object PlatformImportSupport {
    var filePicker: FontFilePicker = UnavailableFontFilePicker("This build has no file picker yet, so it cannot open a font.")
    var destination: ImportDestination = InMemoryImportDestination
}

/**
 * The platform's picker, looked up at each call rather than once: a composition local's default is
 * computed a single time for the life of the process, and an Android activity that is recreated
 * installs a new picker (the old one belongs to a dead activity).
 */
private object CurrentPlatformFilePicker : FontFilePicker {
    override suspend fun pickFont(): PickResult = PlatformImportSupport.filePicker.pickFont()

    override val canPickUfoFolder: Boolean get() = PlatformImportSupport.filePicker.canPickUfoFolder
    override val ufoFolderUnavailableReason: String? get() = PlatformImportSupport.filePicker.ufoFolderUnavailableReason

    override suspend fun pickUfoFolder(): PickResult = PlatformImportSupport.filePicker.pickUfoFolder()
}

/** [CurrentPlatformFilePicker]'s counterpart for [PlatformImportSupport.destination]. */
private object CurrentPlatformDestination : ImportDestination {
    override val persists: Boolean get() = PlatformImportSupport.destination.persists

    override suspend fun parentFolder(): ProjectLocation? = PlatformImportSupport.destination.parentFolder()

    override suspend fun chooseParentFolder(): ProjectLocation? = PlatformImportSupport.destination.chooseParentFolder()

    override fun describe(
        parent: ProjectLocation?,
        folderName: String,
    ): String = PlatformImportSupport.destination.describe(parent, folderName)
}

/** The picker the import flow uses; the platform's own unless a screen or a test provides another. */
val LocalFontFilePicker: ProvidableCompositionLocal<FontFilePicker> = staticCompositionLocalOf { CurrentPlatformFilePicker }

/** Where imported projects are kept; the platform's own unless a screen or a test provides another. */
val LocalImportDestination: ProvidableCompositionLocal<ImportDestination> = staticCompositionLocalOf { CurrentPlatformDestination }

/** The largest font a browser file input's answer is decoded for; real fonts are far smaller. */
const val MAX_PICKED_FONT_BYTES: Long = 128L * 1024 * 1024

/**
 * Turns the browser file input's answer, `"<file name>\u0000<base64 bytes>"` (null when the person
 * cancelled), into a [PickResult]. A missing name, bytes that are not base64, or a file over
 * [maxBytes] answers [PickResult.Failed] in plain words; nothing here throws.
 */
@OptIn(ExperimentalEncodingApi::class)
fun pickResultFromBrowserAnswer(
    answer: String?,
    maxBytes: Long = MAX_PICKED_FONT_BYTES,
): PickResult {
    if (answer == null) return PickResult.Cancelled
    val split = answer.indexOf('\u0000')
    if (split <= 0) return PickResult.Failed("The browser did not say which file was chosen.")
    val name = answer.substring(0, split)
    val encoded = answer.substring(split + 1)
    // Base64 holds three bytes in four characters.
    if (encoded.length.toLong() / 4 * 3 > maxBytes) {
        return PickResult.Failed("“$name” is larger than ${maxBytes / (1024 * 1024)} MB, which is too large to be a font.")
    }
    val bytes =
        try {
            Base64.decode(encoded)
        } catch (e: IllegalArgumentException) {
            return PickResult.Failed("“$name” could not be read from the browser.")
        }
    return PickResult.Font(name, bytes)
}
