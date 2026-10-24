// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.readGlyphFileNames
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.EncodeCaches
import com.asoc.typewright.project.FontState
import com.asoc.typewright.project.GlyphLock
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.Master
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.ProjectEncoder
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectManifest
import com.asoc.typewright.project.ProjectManifestCodec
import com.asoc.typewright.project.ProjectMeta
import com.asoc.typewright.project.ProjectPreferences
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.project.ProjectStore
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.SavePhases
import com.asoc.typewright.project.SaveStatus
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The new project's spec for this font. Every glyph starts approved and locked
 * ([ApprovalOrigin.IMPORT]): the imported outlines are the person's drawing, and law 1 says
 * approved drawings are not regenerated; to change one, unlock it, which leaves a stored diff. The
 * brief records that the project began from a font ([BriefSource.FONT]) with [declaredStyleClass]
 * only when the person has declared one (an import does not guess).
 *
 * When the font reserves a name the audit says it [ImportAudit.mustRename], and [newFamily] must be
 * given: the project starts under it, with every name a compiler would emit renamed ([renameFamily]),
 * and nothing about that is an undoable edit.
 */
fun ImportCandidate.toNewProjectSpec(
    projectName: String,
    newFamily: String? = null,
    declaredStyleClass: String? = null,
): NewProjectSpec {
    require(!audit.mustRename || newFamily != null) {
        "this font reserves ${audit.reservedFontNames.joinToString(" and ")}; a new family name is required"
    }
    val font = if (newFamily != null) renameFamily(ufo, newFamily, audit.reservedFontNames) else ufo
    return NewProjectSpec(
        name = projectFolderName(projectName),
        brief = Brief(source = BriefSource.FONT, styleClass = StyleClass(declared = declaredStyleClass, confirmed = null)),
        scripts = report.scriptCodes,
        masters = listOf(NewMaster(styleName = projectFolderName(font.fontInfo.styleName ?: "Regular"), ufo = font)),
        lockGlyphsAs = ApprovalOrigin.IMPORT,
    )
}

/** The result of [ProjectImporter]: the new project is open, or nothing was imported and [message] says why. */
sealed interface ImportOutcome {
    /** The project was made and is open ([session]); [location] is where it lives. */
    data class Imported(
        val session: ProjectSession,
        val location: ProjectLocation,
    ) : ImportOutcome

    /** Nothing was imported. [restoredPrevious] is true when the project that was open before has been opened again. */
    data class Failed(
        val message: String,
        val restoredPrevious: Boolean,
    ) : ImportOutcome
}

/**
 * Turns an [ImportCandidate] into the open project, through [workspace]. The project that is open
 * is closed first (its session flushed and its write lease released), and if the import then fails
 * it is opened again, so a failed import costs the person nothing. It catches whatever the storage
 * or the codecs throw and answers with [ImportOutcome.Failed].
 */
class ProjectImporter(
    private val workspace: ProjectWorkspace,
    private val clock: Clock = Clock.System,
) {
    /**
     * Makes the project folder `<name>/` inside [parent] (`<name> 2/`, `<name> 3/` … when a folder of
     * that name already holds something) and opens it on [candidate]'s font.
     */
    suspend fun create(
        candidate: ImportCandidate,
        parent: ProjectLocation,
        projectName: String,
        newFamily: String? = null,
        declaredStyleClass: String? = null,
    ): ImportOutcome {
        val spec =
            try {
                candidate.toNewProjectSpec(projectName, newFamily, declaredStyleClass)
            } catch (e: IllegalArgumentException) {
                return ImportOutcome.Failed(e.message ?: "The font could not be prepared.", restoredPrevious = false)
            }
        return replacingCurrent { _ ->
            for (attempt in 1..MAX_NAME_ATTEMPTS) {
                val name = if (attempt == 1) spec.name else "${spec.name} $attempt"
                when (val result = workspace.create(parent, spec.copy(name = name))) {
                    is CreateResult.Created -> {
                        val status = result.session.saveStatus.value
                        if (status is SaveStatus.Failed) {
                            return@replacingCurrent Attempt.Failed("The project could not be saved: ${status.message}")
                        }
                        return@replacingCurrent Attempt.Done(result.session)
                    }

                    // The name is taken: try the next one.
                    is CreateResult.NotEmpty -> {
                        continue
                    }

                    is CreateResult.NotWritable -> {
                        return@replacingCurrent Attempt.Failed("That folder isn't writable: ${result.message}")
                    }
                }
            }
            Attempt.Failed("There are already too many folders named “${spec.name}” there.")
        }
    }

    /**
     * Adopts the UFO folder [candidate] was read from, where it lies: [folder] is the folder that
     * holds it (its parent). Writes `typewright.json`, the locks of every glyph, `build/.gitignore`
     * and the empty `scrapbook/`, `lessons/` and `comparisons/` folders there, never touching a file
     * of the UFO itself, then opens the folder as a project. Refuses a folder that already holds a
     * `typewright.json`.
     */
    suspend fun adopt(
        candidate: ImportCandidate,
        folder: ProjectLocation,
        projectName: String,
        declaredStyleClass: String? = null,
    ): ImportOutcome {
        val ufoFolder = candidate.ufoFolder ?: return ImportOutcome.Failed("This font is not a UFO folder.", restoredPrevious = false)
        return replacingCurrent { _ ->
            val prepared =
                try {
                    workspace.openAfterPreparing(folder) { store ->
                        writeAdoption(store, candidate, ufoFolder, projectFolderName(projectName), declaredStyleClass, clock.now())
                    }
                } catch (e: AdoptionRefused) {
                    return@replacingCurrent Attempt.Failed(e.message ?: "The folder could not be adopted.")
                }
            when (prepared) {
                is OpenResult.Opened -> Attempt.Done(prepared.session)
                is OpenResult.NewerFormat -> Attempt.Done(prepared.session)
                is OpenResult.NotAProject -> Attempt.Failed("The folder was prepared but did not open as a project.")
                is OpenResult.Damaged -> Attempt.Failed("The project could not be opened: ${prepared.message}")
            }
        }
    }

    private sealed interface Attempt {
        data class Done(
            val session: ProjectSession,
        ) : Attempt

        data class Failed(
            val message: String,
        ) : Attempt
    }

    /**
     * Closes the open project, runs [attempt], and puts the previous project back if it did not work
     * or was cancelled. A project the attempt left half-made is closed and forgotten first.
     */
    private suspend fun replacingCurrent(attempt: suspend (previous: ProjectSession?) -> Attempt): ImportOutcome {
        val previous = workspace.current.value
        val previousLocation = previous?.store?.location
        workspace.closeCurrent()
        val outcome =
            try {
                attempt(previous)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { restore(previousLocation) }
                throw e
            } catch (e: Exception) {
                Attempt.Failed(e.message?.takeIf { it.isNotBlank() } ?: "The import failed (${e::class.simpleName}).")
            }
        return when (outcome) {
            is Attempt.Done -> ImportOutcome.Imported(outcome.session, outcome.session.store.location)
            is Attempt.Failed -> ImportOutcome.Failed(outcome.message, restoredPrevious = restore(previousLocation))
        }
    }

    /** Closes whatever is open, forgets a half-made project, and opens [previousLocation] again; true when that project is open. */
    private suspend fun restore(previousLocation: ProjectLocation?): Boolean {
        val halfMade = workspace.current.value
        workspace.closeCurrent()
        if (halfMade != null && halfMade.store.location != previousLocation) runCatching { workspace.forget(halfMade.store.location) }
        if (previousLocation == null) return false
        val reopened = runCatching { workspace.open(previousLocation) }.getOrNull()
        return reopened is OpenResult.Opened || reopened is OpenResult.NewerFormat
    }
}

private const val MAX_NAME_ATTEMPTS = 99

/** [writeAdoption] found a reason not to touch the folder. */
internal class AdoptionRefused(
    message: String,
) : Exception(message)

/**
 * The files adoption adds to [store], which holds the UFO folder [ufoFolder] and is the folder
 * that contains it: everything the project format owns except the UFO's own files. Written in the
 * save order (leaf files, then `typewright.json` last), so a crash leaves either nothing or a
 * folder that opens.
 */
internal suspend fun writeAdoption(
    store: ProjectStore,
    candidate: ImportCandidate,
    ufoFolder: String,
    projectName: String,
    declaredStyleClass: String?,
    now: Instant,
) {
    val existing = store.list()
    if (ProjectManifestCodec.PATH in existing) {
        throw AdoptionRefused("This folder already holds a Typewright project (typewright.json), so there is nothing to import.")
    }
    if (existing.none { it.startsWith("$ufoFolder/") }) {
        throw AdoptionRefused("The UFO folder $ufoFolder is no longer there.")
    }
    val stamp = Instant.fromEpochSeconds(now.epochSeconds)
    val font = candidate.ufo
    val master = Master("regular", ufoFolder, font.fontInfo.styleName ?: "Regular", isDefault = true, ufo = font)
    val locks =
        font.glyphs.associate { glyph ->
            GlyphRef(master.id, glyph.name) to GlyphLock(LockState.LOCKED, ApprovalOrigin.IMPORT, stamp, glyph, emptyList())
        }
    val manifest =
        ProjectManifest(
            name = projectName,
            created = stamp,
            brief = Brief(source = BriefSource.FONT, styleClass = StyleClass(declared = declaredStyleClass, confirmed = null)),
            scripts = candidate.report.scriptCodes,
            workbook = emptyMap(),
            comparisonFonts = emptyList(),
            preferences = ProjectPreferences(),
        )
    val state =
        ProjectState(FontState(listOf(master), locks), ProjectMeta(manifest, ScrapbookManifest(), emptyMap(), JsonObject(emptyMap())))
    val ufoTexts = HashMap<String, String>()
    for (path in existing.filter { it.startsWith("$ufoFolder/") }) {
        store.read(path)?.let { ufoTexts[path.removePrefix("$ufoFolder/")] = it.decodeToString() }
    }
    val caches = EncodeCaches(fileNameHints = mapOf(master.id to readGlyphFileNames(ufoTexts)))
    val encoded = ProjectEncoder.encode(state, caches)
    val ours = encoded.files.filterKeys { !it.startsWith("$ufoFolder/") }
    for (directory in listOf("scrapbook", "lessons", "comparisons")) store.ensureDirectory(directory)
    for (path in SavePhases.order(ours.keys)) store.writeAtomic(path, ours.getValue(path))
}
