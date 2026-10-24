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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) : ImportState {
        private val candidate: ImportCandidate? get() = (review as? ImportReview.Ready)?.candidate

        /** Whether the font reserves a name, so the project needs a new one. */
        val needsName: Boolean get() = candidate?.audit?.mustRename == true

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
 * - One flow at a time: a call while the state is not [ImportState.Idle] does nothing.
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
        if (mutableState.value != ImportState.Idle) return
        mutableState.value = ImportState.Picking(from)
        job =
            scope.launch {
                try {
                    when (val picked = pick()) {
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
                    mutableState.value =
                        ImportState.Message(from, "Something went wrong opening that file: ${e.message ?: e::class.simpleName}.")
                }
            }
    }

    private suspend fun showAudit(
        from: DeckSection?,
        review: ImportReview,
        sample: Boolean,
    ) {
        val candidate = (review as? ImportReview.Ready)?.candidate
        val parent = if (candidate != null && candidate.ufoFolder == null) destination.parentFolder() else null
        val where = if (candidate != null) whereText(candidate, parent, projectNameFor(candidate, "")) else ""
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
                where = whereText(candidate, current.parent, projectNameFor(candidate, text)),
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
        if (candidate.ufoFolder != null) {
            val parentPath =
                candidate.siblings?.parentPath
                    ?: return ImportOutcome.Failed(
                        "The folder that holds this UFO is not known, so nothing was imported.",
                        restoredPrevious = false,
                    )
            return importer.adopt(candidate, ProjectLocation.FileSystem(parentPath), projectName, declared)
        }
        val parent =
            auditing.parent
                ?: destination.chooseParentFolder()
                ?: return ImportOutcome.Failed("No folder was chosen, so nothing was imported.", restoredPrevious = true)
        val newFamily = if (auditing.needsName) auditing.newName.trim() else null
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
    ): String = candidate.ufoFolder?.let { adoptionText(candidate, it) } ?: destination.describe(parent, projectFolderName(projectName))
}

/**
 * What adopting a UFO in place will write and where, said exactly: the folder that holds the UFO is
 * the project folder, so it gets `typewright.json` and the project's folders; the UFO's own files are
 * left as they are; and any other UFO in that folder is named as left alone.
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
        append("$ufoFolder itself is not rewritten; a glyph in it changes only when you edit that glyph.")
        if (others.isNotEmpty()) {
            append(" That folder also holds ${others.joinToString(", ")}. Only $ufoFolder is imported; ")
            append(if (others.size == 1) "the other one is left as it is." else "the others are left as they are.")
        }
    }
}
