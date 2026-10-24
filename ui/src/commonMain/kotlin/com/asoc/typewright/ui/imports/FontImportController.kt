// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.imports.ImportCandidate
import com.asoc.typewright.project.imports.ImportOutcome
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.ProjectImporter
import com.asoc.typewright.project.imports.projectFolderName
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.project.imports.validateFamilyName
import com.asoc.typewright.ui.deck.DeckSection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Where the "Open a font" flow is. */
sealed interface ImportState {
    /** Nothing is happening. */
    data object Idle : ImportState

    /** The platform's file picker is open. */
    data class Picking(
        val from: DeckSection?,
    ) : ImportState

    /** A file has been chosen and is being read and audited. */
    data class Reading(
        val from: DeckSection?,
        val fileName: String,
    ) : ImportState

    /** The licence audit (S03) is on screen. [from] is the section the import started from, which Close returns to. */
    data class Auditing(
        val from: DeckSection?,
        val review: ImportReview,
        /** True for "Try the sample", which says so on the audit. */
        val sample: Boolean,
        /** The folder the project folder will be made in, when the platform knows it before Import is pressed; null where it asks then. */
        val parent: ProjectLocation?,
        /** What the person has typed as the new family name, for a font that reserves one. */
        val newName: String,
        /** What is wrong with [newName], or null. */
        val nameProblem: String?,
        /** Where the files will be written, in words. Empty where nothing will be. */
        val where: String,
        /** Why the last attempt to import failed, or null. */
        val error: String?,
        /**
         * For a UFO that reserves a name: whether the person chose a renamed copy in a new project folder,
         * which leaves the `.ufo` they opened exactly as it is, instead of renaming it where it lies.
         */
        val asCopy: Boolean = false,
    ) : ImportState {
        private val candidate: ImportCandidate? get() = (review as? ImportReview.Ready)?.candidate

        /** Whether the font reserves a name, so the project needs a new one. */
        val needsName: Boolean get() = candidate?.audit?.mustRename == true

        /** Whether the choice between renaming the `.ufo` in place and importing a renamed copy is on offer: a UFO folder that reserves a name. */
        val offersCopy: Boolean get() = needsName && candidate?.ufoFolder != null

        /** The name of the `.ufo` folder the choice is about, when [offersCopy]. */
        val ufoFolder: String? get() = candidate?.ufoFolder

        /** Whether the right circle can be pressed: the audit allows it, and a needed new name is a good one. */
        val canImport: Boolean get() = candidate != null && (!needsName || (newName.isNotBlank() && nameProblem == null))

        /** Why the right circle is dimmed, when it is. */
        val whyNot: String?
            get() =
                when {
                    review is ImportReview.Refused -> review.audit.verdictWords
                    review is ImportReview.Unreadable -> review.message
                    needsName && newName.isBlank() -> "Give the font a new name first: it reserves a name of its own."
                    needsName -> nameProblem
                    else -> null
                }
    }

    /** The project is being written. Closing is not possible now: half a project would be left behind. */
    data class Creating(
        val auditing: Auditing,
    ) : ImportState

    /** The picker could not be used; [text] says why in plain words. */
    data class Message(
        val from: DeckSection?,
        val text: String,
    ) : ImportState
}

/**
 * The "Open a font" flow as a state machine with no Compose in it: pick a file (or a UFO folder, or
 * the bundled sample), read and audit it, show the audit, and on Import make the project and open
 * it. [state] is what S03 draws; nothing here touches the screen.
 *
 * - One flow at a time: a call while the state is not [ImportState.Idle] does nothing, with one exception.
 *   A request for a font or a UFO folder while the picker is open ([ImportState.Picking]) ends that wait and
 *   starts again, so a picker that never answers (a browser that sends no cancel) cannot block the next
 *   request; and an answer that arrives after its wait was ended is dropped.
 * - The open project is closed before the new one is made, and put back if the import fails
 *   ([ProjectImporter]).
 * - [close] is the deck's Close circle: it leaves an audit or a message, cancels an open picker, and
 *   does nothing while a file is being read or a project written, because stopping there would leave
 *   half of one. [cancel] is for the host going away: it cancels whatever is in flight.
 * - [onImported] is called with the new session once it is open.
 */
class FontImportController(
    private val workspace: ProjectWorkspace,
    private val picker: FontFilePicker,
    private val destination: ImportDestination,
    private val scope: CoroutineScope,
    private val reviewDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onImported: (ProjectSession) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** Whether the platform can choose a UFO folder, for the entry points that offer one. */
    val canPickUfoFolder: Boolean get() = picker.canPickUfoFolder

    /** Why it cannot, in a sentence. */
    val ufoFolderUnavailableReason: String? get() = picker.ufoFolderUnavailableReason

    /** "Open a font": asks for a font file. [from] is the section the import started from. */
    fun openFont(from: DeckSection?) = start(from) { picker.pickFont() }

    /** "Open a UFO folder": asks for a UFO folder, where the platform offers that. */
    fun openUfoFolder(from: DeckSection?) = start(from) { picker.pickUfoFolder() }

    /** "Try the sample": audits the bundled sample font like any other, only because the person asked. */
    fun trySample(from: DeckSection?) {
        if (mutableState.value != ImportState.Idle) return
        mutableState.value = ImportState.Reading(from, BundledSampleFont.FILE_NAME)
        job =
            scope.launch {
                val review = withContext(reviewDispatcher) { BundledSampleFont.review() }
                showAudit(from, review, sample = true)
            }
    }

    private fun start(
        from: DeckSection?,
        pick: suspend () -> PickResult,
    ) {
        when (mutableState.value) {
            ImportState.Idle -> Unit

            // A picker that never answers (a browser whose file input sends no cancel) must not block the next
            // request for good: the stale wait is ended and this request takes its place.
            is ImportState.Picking -> job?.cancel()

            else -> return
        }
        mutableState.value = ImportState.Picking(from)
        job =
            scope.launch {
                try {
                    val picked = pick()
                    // A pick that was cancelled (Close, or a newer request) and then answered anyway is dropped here,
                    // so a late answer cannot move the state out from under whatever the person is doing now.
                    currentCoroutineContext().ensureActive()
                    when (picked) {
                        PickResult.Cancelled -> {
                            mutableState.value = ImportState.Idle
                        }

                        is PickResult.Failed -> {
                            mutableState.value = ImportState.Message(from, picked.message)
                        }

                        is PickResult.Font -> {
                            mutableState.value = ImportState.Reading(from, picked.name)
                            showAudit(from, withContext(reviewDispatcher) { reviewFontFile(picked.name, picked.bytes) }, sample = false)
                        }

                        is PickResult.Ufo -> {
                            mutableState.value = ImportState.Reading(from, picked.folder.name)
                            showAudit(from, withContext(reviewDispatcher) { picked.folder.review() }, sample = false)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A wait that was ended (Close, or a newer request) and then failed on its way out says nothing now.
                    if (isActive) {
                        mutableState.value =
                            ImportState.Message(from, "Something went wrong opening that file: ${e.message ?: e::class.simpleName}.")
                    }
                }
            }
    }

    private suspend fun showAudit(
        from: DeckSection?,
        review: ImportReview,
        sample: Boolean,
    ) {
        val candidate = (review as? ImportReview.Ready)?.candidate
        // A UFO that is adopted in place needs no folder, but one that reserves a name may be imported as a copy instead.
        val parent =
            if (candidate != null &&
                (candidate.ufoFolder == null || candidate.audit.mustRename)
            ) {
                destination.parentFolder()
            } else {
                null
            }
        val where = if (candidate != null) whereText(candidate, parent, projectNameFor(candidate, ""), asCopy = false) else ""
        mutableState.value =
            ImportState.Auditing(from, review, sample, parent, newName = "", nameProblem = null, where = where, error = null)
    }

    /** Sets the new family name a font that reserves one must be imported under, checking it against the reserved names. */
    fun setNewName(text: String) {
        val current = mutableState.value as? ImportState.Auditing ?: return
        val candidate = (current.review as? ImportReview.Ready)?.candidate ?: return
        val problem =
            if (text.isBlank()) null else validateFamilyName(text, candidate.audit.reservedFontNames, candidate.ufo.fontInfo.familyName)
        mutableState.value =
            current.copy(
                newName = text,
                nameProblem = problem,
                where = whereText(candidate, current.parent, projectNameFor(candidate, text), current.asCopy),
                error = null,
            )
    }

    /**
     * For a UFO that reserves a name: chooses between renaming the `.ufo` where it lies ([asCopy] false, the default) and
     * importing a renamed copy into a new project folder, which leaves the `.ufo` untouched. Does nothing where the choice is not on offer.
     */
    fun setAsCopy(asCopy: Boolean) {
        val current = mutableState.value as? ImportState.Auditing ?: return
        if (!current.offersCopy) return
        val candidate = (current.review as ImportReview.Ready).candidate
        mutableState.value =
            current.copy(
                asCopy = asCopy,
                where = whereText(candidate, current.parent, projectNameFor(candidate, current.newName), asCopy),
                error = null,
            )
    }

    /** The right circle: imports the audited font. Does nothing unless the audit is on screen and allows it. */
    fun confirm() {
        val auditing = mutableState.value as? ImportState.Auditing ?: return
        if (!auditing.canImport) return
        val candidate = (auditing.review as ImportReview.Ready).candidate
        mutableState.value = ImportState.Creating(auditing)
        job =
            scope.launch {
                val outcome =
                    try {
                        runImport(candidate, auditing)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ImportOutcome.Failed(e.message ?: "The import failed.", restoredPrevious = false)
                    }
                when (outcome) {
                    is ImportOutcome.Imported -> {
                        mutableState.value = ImportState.Idle
                        onImported(outcome.session)
                    }

                    is ImportOutcome.Failed -> {
                        mutableState.value = auditing.copy(error = outcome.message)
                    }
                }
            }
    }

    private suspend fun runImport(
        candidate: ImportCandidate,
        auditing: ImportState.Auditing,
    ): ImportOutcome {
        val importer = ProjectImporter(workspace)
        val declared = if (auditing.sample) BundledSampleFont.DECLARED_STYLE_CLASS else null
        val projectName = projectNameFor(candidate, auditing.newName)
        val newFamily = if (auditing.needsName) auditing.newName.trim() else null
        // A UFO is adopted where it lies, and renamed there when it reserves a name, unless the person chose a copy.
        if (candidate.ufoFolder != null && !(auditing.needsName && auditing.asCopy)) {
            val parentPath =
                candidate.siblings?.parentPath
                    ?: return ImportOutcome.Failed(
                        "The folder that holds this UFO is not known, so nothing was imported.",
                        restoredPrevious = false,
                    )
            return importer.adopt(candidate, ProjectLocation.FileSystem(parentPath), projectName, newFamily, declared)
        }
        val parent =
            auditing.parent
                ?: destination.chooseParentFolder()
                ?: return ImportOutcome.Failed("No folder was chosen, so nothing was imported.", restoredPrevious = true)
        return importer.create(candidate, parent, projectName, newFamily, declared)
    }

    /** The deck's Close circle. See the class comment for when it does nothing. */
    fun close() {
        when (mutableState.value) {
            is ImportState.Auditing, is ImportState.Message -> mutableState.value = ImportState.Idle
            is ImportState.Picking -> cancel()
            is ImportState.Reading, is ImportState.Creating, ImportState.Idle -> Unit
        }
    }

    /** Cancels whatever is in flight and returns to [ImportState.Idle]; for a host that is going away. */
    fun cancel() {
        job?.cancel()
        job = null
        mutableState.value = ImportState.Idle
    }

    private fun projectNameFor(
        candidate: ImportCandidate,
        newName: String,
    ): String =
        if (candidate.audit.mustRename && newName.isNotBlank()) {
            newName.trim()
        } else {
            candidate.ufo.fontInfo.familyName
                ?.takeIf { it.isNotBlank() }
                ?: candidate.fileName.substringBeforeLast('.')
        }

    private fun whereText(
        candidate: ImportCandidate,
        parent: ProjectLocation?,
        projectName: String,
        asCopy: Boolean,
    ): String {
        val ufoFolder = candidate.ufoFolder
        return when {
            ufoFolder == null -> {
                destination.describe(parent, projectFolderName(projectName))
            }

            candidate.audit.mustRename && asCopy -> {
                copyText(
                    candidate,
                    ufoFolder,
                    destination.describe(parent, projectFolderName(projectName)),
                )
            }

            else -> {
                adoptionText(candidate, ufoFolder)
            }
        }
    }
}

/**
 * What importing a renamed copy of a UFO that reserves a name will do: [destination] says where the new
 * project folder goes, and the original [ufoFolder] is left exactly as it is. The copy holds what Typewright
 * reads of a UFO (the default layer's glyphs, font info, groups, kerning, features and lib), so images, data
 * and other layers stay in the original.
 */
internal fun copyText(
    candidate: ImportCandidate,
    ufoFolder: String,
    destination: String,
): String =
    buildString {
        append("A renamed copy: $ufoFolder is not touched. $destination ")
        append("The copy holds the default layer's glyphs, font info, groups, kerning, features and lib; ")
        append("images, data and other layers stay in $ufoFolder.")
        val others = candidate.siblings?.otherUfos.orEmpty()
        if (others.isNotEmpty()) append(" The other .ufo folders beside it (${others.joinToString(", ")}) are not read.")
    }

/**
 * What adopting a UFO in place will write and where, said exactly: the folder that holds the UFO is
 * the project folder, so it gets `typewright.json` and the project's folders; the UFO's own files are
 * left as they are; and any other UFO in that folder is named as left alone.
 *
 * The one exception is a font that reserves a name (`candidate.audit.mustRename`): the new name has
 * to be written somewhere a compiler reads it, which is the UFO's `fontinfo.plist`, so the text says
 * that file will be edited for the rename, that nothing else in the UFO is, and that importing a
 * renamed copy is the way to leave the `.ufo` alone.
 */
internal fun adoptionText(
    candidate: ImportCandidate,
    ufoFolder: String,
): String {
    val parent = candidate.siblings?.parentPath ?: "the folder it is in"
    val others = candidate.siblings?.otherUfos.orEmpty()
    return buildString {
        append(
            "Typewright will add typewright.json, and the folders locks, scrapbook, lessons, comparisons and build, to $parent, next to $ufoFolder. ",
        )
        if (candidate.audit.mustRename) {
            append("Because this font reserves a name, $ufoFolder will be edited for the rename: its fontinfo.plist is rewritten ")
            append(
                "with your new family name, and no other file in it changes. That is the only edit made to $ufoFolder, and only for this. ",
            )
            append(
                "To leave $ufoFolder exactly as it is, choose a renamed copy instead. A glyph in it changes only when you edit that glyph.",
            )
        } else {
            append("$ufoFolder itself is not rewritten; a glyph in it changes only when you edit that glyph.")
        }
        if (others.isNotEmpty()) {
            append(" That folder also holds ${others.joinToString(", ")}. Only $ufoFolder is imported; ")
            append(if (others.size == 1) "the other one is left as it is." else "the others are left as they are.")
        }
    }
}
