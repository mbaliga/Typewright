// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.parseGlif
import com.asoc.typewright.core.font.ufo.readGlyphFileNames
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeGlif
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import com.asoc.typewright.project.scrapbook.ScrapbookManifestCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Owns one open project (docs/PROJECT_MODEL.md §1, §4): holds its state, routes every font edit
 * through one undo/redo history, enforces law 1, autosaves, and reopens the project exactly as it
 * was. No Compose dependency; the UI observes [state], [history], [saveStatus] and [economy]
 * through [kotlinx.coroutines.flow.StateFlow].
 *
 * Every non-`suspend` method runs on one caller thread; it mutates in-memory state synchronously
 * and, when that leaves something to save, schedules autosave on [SessionEnvironment.scope]. Only
 * one save runs at a time ([saveMutex]).
 */
class ProjectSession private constructor(
    val store: ProjectStore,
    private val env: SessionEnvironment,
    initialState: ProjectState,
    initialCaches: EncodeCaches,
    initialLastWritten: Map<String, ByteArray>,
    val readOnly: String?,
    val openReport: OpenReport,
    private val lease: WriteLease?,
) {
    private val sessionScope = CoroutineScope(env.scope.coroutineContext + SupervisorJob(env.scope.coroutineContext[Job]))

    private val stateFlow = MutableStateFlow(initialState)
    val state: StateFlow<ProjectState> = stateFlow.asStateFlow()

    private val historyStack = HistoryStack(env.historyLimit, env.coalesceWindowMillis, env.monotonic)
    private val historyFlow = MutableStateFlow(HistoryState.EMPTY)
    val history: StateFlow<HistoryState> = historyFlow.asStateFlow()

    private val economyCache = EconomyCache()
    private val economyFlow = MutableStateFlow(economyCache.recompute(initialState.font))
    val economy: StateFlow<EconomySnapshot> = economyFlow.asStateFlow()

    private val initialSaveStatus: SaveStatus =
        when {
            readOnly != null -> SaveStatus.ReadOnly(readOnly)
            store.durability == Durability.TAB_ONLY -> SaveStatus.TabOnly(unexportedChanges = false)
            else -> SaveStatus.Clean
        }
    private val saveStatusFlow = MutableStateFlow(initialSaveStatus)
    val saveStatus: StateFlow<SaveStatus> = saveStatusFlow.asStateFlow()

    private val saveMutex = Mutex()
    private var caches = initialCaches
    private var lastWritten = initialLastWritten
    private var dirty = false
    private var firstDirtyAt: Instant? = null
    private var debounceJob: Job? = null
    private var capJob: Job? = null
    private var openGesture: GestureImpl? = null
    private var closed = false

    /** Applies [command] to the open project, committing any open [Gesture] first. */
    fun execute(command: FontCommand): EditResult {
        check(!closed) { "This ProjectSession is closed" }
        openGesture?.let { commitGesture(it) }
        if (readOnly != null) return EditResult.Refused(Refusal.ReadOnly(readOnly))
        val before = stateFlow.value
        val (result, after) = FontCommandEngine.applyWithState(before.font, command, env.now())
        if (result is EditResult.Applied) {
            historyStack.push(result.label, before.font, after, command.coalesceKey)
            historyFlow.value = historyStack.snapshot()
            stateFlow.value = before.copy(font = after)
            economyFlow.value = economyCache.recompute(after)
            markDirty(immediate = command.requiresImmediateSave())
        }
        return result
    }

    /** Runs [command] against a copy of the current state without changing anything (INTERACTION_V1 §5's consequence estimator). */
    fun simulate(command: FontCommand): Simulation {
        val before = stateFlow.value.font
        val (result, after) = FontCommandEngine.applyWithState(before, command, env.now())
        return Simulation(result, countsOf(before), countsOf(after))
    }

    /** Opens a live, coalesced edit (a drag); see [Gesture]. Commits any already-open gesture first. */
    fun beginGesture(label: String): Gesture {
        check(!closed) { "This ProjectSession is closed" }
        openGesture?.let { commitGesture(it) }
        val gesture = GestureImpl(label, stateFlow.value.font)
        openGesture = gesture
        return gesture
    }

    /** Applies [change] to the project's metadata; never enters the font undo/redo history. */
    fun update(change: MetaChange): EditResult {
        check(!closed) { "This ProjectSession is closed" }
        if (readOnly != null) return EditResult.Refused(Refusal.ReadOnly(readOnly))
        val before = stateFlow.value
        val outcome = applyMetaChange(before.meta, change)
        return when (outcome) {
            is MetaOutcome.Refused -> {
                EditResult.Refused(outcome.refusal)
            }

            is MetaOutcome.NoChange -> {
                EditResult.NoChange
            }

            is MetaOutcome.Applied -> {
                stateFlow.value = before.copy(meta = outcome.meta)
                if (outcome.newImage != null) caches = caches.copy(images = caches.images + outcome.newImage)
                markDirty(immediate = false)
                EditResult.Applied(outcome.label, emptySet())
            }
        }
    }

    /** Undoes the last history entry, or cancels an open [Gesture] if one is open. */
    fun undo(): Boolean {
        check(!closed) { "This ProjectSession is closed" }
        openGesture?.let {
            cancelGesture(it)
            return true
        }
        if (readOnly != null) return false
        val restored = historyStack.undo() ?: return false
        stateFlow.value = stateFlow.value.copy(font = restored)
        economyFlow.value = economyCache.recompute(restored)
        historyFlow.value = historyStack.snapshot()
        markDirty(immediate = false)
        return true
    }

    /** Redoes the last undone history entry. */
    fun redo(): Boolean {
        check(!closed) { "This ProjectSession is closed" }
        if (readOnly != null) return false
        val restored = historyStack.redo() ?: return false
        stateFlow.value = stateFlow.value.copy(font = restored)
        economyFlow.value = economyCache.recompute(restored)
        historyFlow.value = historyStack.snapshot()
        markDirty(immediate = false)
        return true
    }

    /** [ref]'s lock, or null if it has never been approved. */
    fun lockOf(ref: GlyphRef): GlyphLock? = stateFlow.value.font.locks[ref]

    /** Writes any pending changes now, awaiting completion. */
    suspend fun flush(): SaveStatus {
        cancelScheduledSaves()
        return performSave()
    }

    /** Re-encodes every owned file from scratch, ignoring the encode cache; a repair tool, and the golden-path ⚑ that this gives byte-identical output. */
    suspend fun rewriteAll(): SaveStatus {
        cancelScheduledSaves()
        caches = EncodeCaches.EMPTY
        dirty = true
        return performSave()
    }

    /** Resolves a [SaveStatus.Conflict]: [keepMine] overwrites the externally changed files; otherwise the project is reloaded from disk, discarding in-memory changes. */
    suspend fun resolveConflict(keepMine: Boolean) {
        check(!closed) { "This ProjectSession is closed" }
        if (keepMine) {
            performSave(skipConflictCheck = true)
        } else {
            reloadFromDisk()
        }
    }

    /** An in-memory, consistent snapshot of every project file, for a build to read without racing autosave. */
    suspend fun buildSnapshot(includeBuild: Boolean): ProjectFiles {
        val encoded = ProjectEncoder.encode(stateFlow.value, caches)
        val files = encoded.files.toMutableMap()
        if (includeBuild) {
            for (path in store.list()) {
                if (path == ProjectEncoder.BUILD_GITIGNORE_PATH || !path.startsWith("build/")) continue
                store.read(path)?.let { files[path] = it }
            }
        }
        return InMemoryProjectFiles(store.displayName, files)
    }

    /** Flushes, releases the write lease and stops autosave. Safe to call more than once. */
    suspend fun close() {
        if (closed) return
        openGesture?.let { cancelGesture(it) }
        cancelScheduledSaves()
        flush()
        lease?.release()
        closed = true
        sessionScope.cancel()
    }

    // ---- Simulation helper ---------------------------------------------------------------------

    private fun countsOf(font: FontState) =
        font.masters
            .flatMap { m -> m.ufo.glyphs.map { GlyphRef(m.id, it.name) to it.count() } }
            .toMap()

    // ---- Gesture ------------------------------------------------------------------------------

    private inner class GestureImpl(
        val label: String,
        val startFont: FontState,
    ) : Gesture {
        var workingFont = startFont
        var active = true

        override fun update(command: FontCommand): EditResult {
            check(active) { "This gesture is no longer open" }
            if (readOnly != null) return EditResult.Refused(Refusal.ReadOnly(readOnly))
            val (result, after) = FontCommandEngine.applyWithState(workingFont, command, env.now())
            if (result is EditResult.Applied) {
                workingFont = after
                stateFlow.value = stateFlow.value.copy(font = after)
                economyFlow.value = economyCache.recompute(after)
            }
            return result
        }

        override fun commit() = commitGesture(this)

        override fun cancel() = cancelGesture(this)
    }

    private fun commitGesture(gesture: GestureImpl) {
        if (!gesture.active) return
        gesture.active = false
        if (openGesture === gesture) openGesture = null
        if (gesture.workingFont != gesture.startFont) {
            historyStack.pushAlways(gesture.label, gesture.startFont, gesture.workingFont)
            historyFlow.value = historyStack.snapshot()
            markDirty(immediate = false)
        }
    }

    private fun cancelGesture(gesture: GestureImpl) {
        if (!gesture.active) return
        gesture.active = false
        if (openGesture === gesture) openGesture = null
        stateFlow.value = stateFlow.value.copy(font = gesture.startFont)
        economyFlow.value = economyCache.recompute(gesture.startFont)
    }

    // ---- Autosave --------------------------------------------------------------------------------

    private fun markDirty(immediate: Boolean) {
        dirty = true
        val since = firstDirtyAt ?: env.now().also { firstDirtyAt = it }
        if (readOnly != null) {
            saveStatusFlow.value = SaveStatus.ReadOnly(readOnly)
            return
        }
        saveStatusFlow.value = SaveStatus.Pending(since)
        if (!env.autosave.enabled) return
        if (immediate) {
            cancelScheduledSaves()
            scheduleSave(delayMillis = 0)
            return
        }
        debounceJob?.cancel()
        debounceJob = scheduleSave(delayMillis = env.autosave.debounceMillis)
        if (capJob == null) capJob = scheduleSave(delayMillis = env.autosave.maxDelayMillis)
    }

    private fun scheduleSave(delayMillis: Long): Job =
        sessionScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            performSave()
        }

    private fun cancelScheduledSaves() {
        debounceJob?.cancel()
        debounceJob = null
        capJob?.cancel()
        capJob = null
    }

    // ---- Save pipeline (docs/PROJECT_MODEL.md §8.3) ------------------------------------------

    /** What the I/O phase of [performSave] found, decided entirely inside [SessionEnvironment.io]. */
    private sealed interface SaveIo {
        data class Written(
            val files: Map<String, ByteArray>,
        ) : SaveIo

        data class Conflict(
            val paths: List<String>,
        ) : SaveIo

        data class Failed(
            val message: String,
        ) : SaveIo
    }

    private suspend fun performSave(skipConflictCheck: Boolean = false): SaveStatus =
        saveMutex.withLock {
            if (closed && !dirty) return@withLock saveStatusFlow.value
            if (!dirty) return@withLock saveStatusFlow.value
            if (readOnly != null) {
                saveStatusFlow.value = SaveStatus.ReadOnly(readOnly)
                return@withLock saveStatusFlow.value
            }
            saveStatusFlow.value = SaveStatus.Saving
            val snapshot = stateFlow.value
            // Encoding is a pure function of an immutable snapshot (docs/PROJECT_MODEL.md §4 "Threading"); it runs inline,
            // on whatever dispatcher this save itself is running on (env.scope for a scheduled autosave, the caller's for
            // flush()), so a virtual-time test dispatcher governs it too. Only I/O is pinned to env.io, below.
            val encoded = ProjectEncoder.encode(snapshot, caches)
            val target = encoded.files

            val ioResult =
                withContext(env.io) {
                    if (!skipConflictCheck) {
                        val conflicts = mutableListOf<String>()
                        for (path in (target.keys + lastWritten.keys)) {
                            val previous = lastWritten[path] ?: continue
                            if (target[path]?.contentEquals(previous) == true) continue
                            val onDisk = store.read(path)
                            if (onDisk == null || !onDisk.contentEquals(previous)) conflicts += path
                        }
                        if (conflicts.isNotEmpty()) return@withContext SaveIo.Conflict(conflicts.sorted())
                    }
                    try {
                        var written = lastWritten
                        for (path in SavePhases.order(target.keys)) {
                            val bytes = target.getValue(path)
                            if (written[path]?.contentEquals(bytes) == true) continue
                            store.writeAtomic(path, bytes)
                            written = written + (path to bytes)
                        }
                        for (path in written.keys - target.keys) {
                            store.delete(path)
                            written = written - path
                        }
                        SaveIo.Written(written)
                    } catch (failure: Throwable) {
                        SaveIo.Failed(failure.message ?: "save failed")
                    }
                }
            when (ioResult) {
                is SaveIo.Conflict -> {
                    saveStatusFlow.value = SaveStatus.Conflict(ioResult.paths)
                    return@withLock saveStatusFlow.value
                }

                is SaveIo.Failed -> {
                    saveStatusFlow.value = SaveStatus.Failed(ProjectManifestCodec.PATH, ioResult.message)
                    return@withLock saveStatusFlow.value
                }

                is SaveIo.Written -> {
                    lastWritten = ioResult.files
                }
            }

            caches = encoded.caches
            dirty = false
            firstDirtyAt = null
            cancelScheduledSaves()
            val status: SaveStatus =
                if (store.durability == Durability.TAB_ONLY) SaveStatus.TabOnly(unexportedChanges = false) else SaveStatus.Saved(env.now())
            saveStatusFlow.value = status
            status
        }

    private suspend fun reloadFromDisk() {
        val loaded = loadProject(store, env)
        val opened = loaded as? LoadOutcome.Opened ?: return
        cancelScheduledSaves()
        stateFlow.value = opened.state
        caches = opened.caches
        lastWritten = opened.lastWritten
        dirty = false
        firstDirtyAt = null
        economyFlow.value = economyCache.recompute(opened.state.font)
        historyFlow.value = HistoryState.EMPTY
        saveStatusFlow.value = if (store.durability == Durability.TAB_ONLY) SaveStatus.TabOnly(false) else SaveStatus.Clean
    }

    // ---- Meta changes ---------------------------------------------------------------------------

    private sealed interface MetaOutcome {
        data class Applied(
            val meta: ProjectMeta,
            val label: String,
            val newImage: Pair<String, ByteArray>? = null,
        ) : MetaOutcome

        data object NoChange : MetaOutcome

        data class Refused(
            val refusal: Refusal,
        ) : MetaOutcome
    }

    private fun applyMetaChange(
        meta: ProjectMeta,
        change: MetaChange,
    ): MetaOutcome =
        when (change) {
            is MetaChange.AddPin -> {
                addPin(meta, change)
            }

            is MetaChange.UpdatePin -> {
                updatePin(meta, change)
            }

            is MetaChange.RemovePin -> {
                removePin(meta, change)
            }

            is MetaChange.AddReflection -> {
                addReflection(meta, change)
            }

            is MetaChange.SetTaskConfirmed -> {
                setTaskConfirmed(meta, change)
            }

            is MetaChange.SetBrief -> {
                MetaOutcome.Applied(meta.copy(manifest = meta.manifest.copy(brief = change.brief)), "Set brief")
            }

            is MetaChange.SetScripts -> {
                MetaOutcome.Applied(meta.copy(manifest = meta.manifest.copy(scripts = change.scripts)), "Set scripts")
            }

            is MetaChange.SetComparisonFonts -> {
                MetaOutcome.Applied(meta.copy(manifest = meta.manifest.copy(comparisonFonts = change.fonts)), "Set comparison fonts")
            }

            is MetaChange.SetPreferences -> {
                MetaOutcome.Applied(meta.copy(manifest = meta.manifest.copy(preferences = change.preferences)), "Set preferences")
            }

            is MetaChange.Rename -> {
                MetaOutcome.Applied(meta.copy(manifest = meta.manifest.copy(name = change.name)), "Rename project")
            }
        }

    private fun addPin(
        meta: ProjectMeta,
        change: MetaChange.AddPin,
    ): MetaOutcome {
        if (meta.scrapbook.pins.any { it.id == change.pin.id }) return MetaOutcome.Refused(Refusal.NameClash(change.pin.id))
        val scrapbook = meta.scrapbook.copy(pins = meta.scrapbook.pins + change.pin)
        val newImage = if (change.image != null && change.pin.imagePath != null) change.pin.imagePath to change.image else null
        return MetaOutcome.Applied(meta.copy(scrapbook = scrapbook), "Add pin", newImage)
    }

    private fun updatePin(
        meta: ProjectMeta,
        change: MetaChange.UpdatePin,
    ): MetaOutcome {
        if (meta.scrapbook.pins.none { it.id == change.pin.id }) return MetaOutcome.Refused(Refusal.Invalid("No pin \"${change.pin.id}\""))
        val pins = meta.scrapbook.pins.map { if (it.id == change.pin.id) change.pin else it }
        return MetaOutcome.Applied(meta.copy(scrapbook = meta.scrapbook.copy(pins = pins)), "Update pin")
    }

    private fun removePin(
        meta: ProjectMeta,
        change: MetaChange.RemovePin,
    ): MetaOutcome {
        if (meta.scrapbook.pins.none { it.id == change.id }) return MetaOutcome.NoChange
        val pins = meta.scrapbook.pins.filterNot { it.id == change.id }
        return MetaOutcome.Applied(meta.copy(scrapbook = meta.scrapbook.copy(pins = pins)), "Remove pin")
    }

    private fun addReflection(
        meta: ProjectMeta,
        change: MetaChange.AddReflection,
    ): MetaOutcome {
        val existing = meta.lessons[change.workbook] ?: WorkbookLessons.empty(change.workbook)
        val updatedLessons = existing.withReflection(change.task, env.now(), change.text)
        val newLessons = meta.lessons + (change.workbook to updatedLessons)
        val confirmable =
            meta.manifest.workbook[change.workbook]
                ?.confirmedTasks
                .orEmpty() + change.task
        val workbook = meta.manifest.workbook + (change.workbook to WorkbookRecord(confirmable))
        return MetaOutcome.Applied(
            meta.copy(lessons = newLessons, manifest = meta.manifest.copy(workbook = workbook)),
            "Save reflection",
        )
    }

    private fun setTaskConfirmed(
        meta: ProjectMeta,
        change: MetaChange.SetTaskConfirmed,
    ): MetaOutcome {
        val record = meta.manifest.workbook[change.workbook] ?: WorkbookRecord()
        val already = change.task in record.confirmedTasks
        if (already == change.confirmed) return MetaOutcome.NoChange
        val tasks = if (change.confirmed) record.confirmedTasks + change.task else record.confirmedTasks - change.task
        val workbook = meta.manifest.workbook + (change.workbook to record.copy(confirmedTasks = tasks))
        return MetaOutcome.Applied(meta.copy(manifest = meta.manifest.copy(workbook = workbook)), "Confirm task")
    }

    companion object {
        /** Creates a new project inside [store] (which must be empty) from [spec]. */
        suspend fun create(
            store: ProjectStore,
            spec: NewProjectSpec,
            env: SessionEnvironment,
        ): CreateResult {
            val recovery = runCatching { store.recover() }.getOrElse { RecoveryReport.NONE }
            val existing =
                runCatching { store.list() }.getOrElse {
                    return CreateResult.NotWritable(
                        it.message ?: "cannot list the target",
                    )
                }
            if (existing.isNotEmpty()) return CreateResult.NotEmpty(existing)
            val lease = store.acquireWriteLease() ?: return CreateResult.NotWritable("The project is open elsewhere")

            val now = env.now()
            val masters =
                spec.masters.mapIndexed { index, m ->
                    Master(m.id, ufoDirectoryName(spec.name, m.styleName), m.styleName, isDefault = index == 0, ufo = m.ufo)
                }
            var locks = emptyMap<GlyphRef, GlyphLock>()
            if (spec.lockGlyphsAs != null) {
                locks =
                    masters
                        .flatMap { master ->
                            master.ufo.glyphs.map {
                                GlyphRef(master.id, it.name) to
                                    GlyphLock(LockState.LOCKED, spec.lockGlyphsAs, now, it, emptyList())
                            }
                        }.toMap()
            }
            val manifest =
                ProjectManifest(
                    name = spec.name,
                    created = now,
                    brief = spec.brief,
                    scripts = spec.scripts,
                    workbook = emptyMap(),
                    comparisonFonts = emptyList(),
                    preferences = ProjectPreferences(),
                )
            val meta = ProjectMeta(manifest, ScrapbookManifest(), emptyMap(), JsonObject(emptyMap()))
            val state = ProjectState(FontState(masters, locks), meta)

            for (directory in listOf("scrapbook", "lessons", "comparisons")) store.ensureDirectory(directory)

            val session =
                ProjectSession(
                    store,
                    env,
                    state,
                    EncodeCaches.EMPTY,
                    emptyMap(),
                    readOnly = null,
                    OpenReport(recovery, emptyList(), null),
                    lease,
                )
            session.dirty = true
            session.firstDirtyAt = now
            session.performSave()
            return CreateResult.Created(session)
        }

        /** Opens the project in [store]. */
        suspend fun open(
            store: ProjectStore,
            env: SessionEnvironment,
        ): OpenResult {
            val recovery = runCatching { store.recover() }.getOrElse { RecoveryReport.NONE }
            val lease = store.acquireWriteLease()
            return when (val loaded = loadProject(store, env, recovery)) {
                is LoadOutcome.NotAProject -> {
                    lease?.release()
                    OpenResult.NotAProject(loaded.ufoPaths)
                }

                is LoadOutcome.Damaged -> {
                    lease?.release()
                    OpenResult.Damaged(loaded.path, loaded.message)
                }

                is LoadOutcome.NewerFormat -> {
                    val session =
                        ProjectSession(
                            store,
                            env,
                            loaded.state,
                            EncodeCaches.EMPTY,
                            emptyMap(),
                            readOnly = "This project was saved by a newer Typewright; open it there, or upgrade.",
                            OpenReport(recovery, emptyList(), null),
                            lease,
                        )
                    OpenResult.NewerFormat(loaded.found, session)
                }

                is LoadOutcome.Opened -> {
                    val readOnlyReason = if (lease == null) "This project is open elsewhere; changes here won't be saved." else null
                    val session =
                        ProjectSession(
                            store,
                            env,
                            loaded.state,
                            loaded.caches,
                            loaded.lastWritten,
                            readOnlyReason,
                            OpenReport(recovery, loaded.externalEdits, loaded.migratedFrom),
                            lease,
                        )
                    OpenResult.Opened(session)
                }
            }
        }
    }
}

/** Whether [FontCommand] must autosave immediately rather than debounce (law-1 events, docs/PROJECT_MODEL.md §8.1). */
internal fun FontCommand.requiresImmediateSave(): Boolean =
    when (this) {
        is Approve, is Unlock, is AcceptTrace, is RevertToApproved -> true
        is Batch -> commands.any { it.requiresImmediateSave() }
        else -> false
    }

private fun ufoDirectoryName(
    familyName: String,
    styleName: String,
): String = "${familyName.replace(" ", "")}-${styleName.replace(" ", "")}.ufo"

// ---- Loading ------------------------------------------------------------------------------------

private sealed interface LoadOutcome {
    data class Opened(
        val state: ProjectState,
        val caches: EncodeCaches,
        val lastWritten: Map<String, ByteArray>,
        val externalEdits: List<GlyphRef>,
        val migratedFrom: Int?,
    ) : LoadOutcome

    data class NewerFormat(
        val found: Int,
        val state: ProjectState,
    ) : LoadOutcome

    data class NotAProject(
        val ufoPaths: List<String>,
    ) : LoadOutcome

    data class Damaged(
        val path: String,
        val message: String,
    ) : LoadOutcome
}

private suspend fun loadProject(
    store: ProjectStore,
    env: SessionEnvironment,
    recovery: RecoveryReport = RecoveryReport.NONE,
): LoadOutcome {
    val allPaths = runCatching { store.list() }.getOrElse { return LoadOutcome.Damaged("", it.message ?: "cannot list the project") }
    val manifestBytes = store.read(ProjectManifestCodec.PATH)
    if (manifestBytes == null) {
        val ufoDirs =
            allPaths
                .map { it.substringBefore('/') }
                .filter { it.endsWith(".ufo") }
                .distinct()
                .sorted()
        return LoadOutcome.NotAProject(ufoDirs)
    }
    val manifestText = manifestBytes.decodeToString()
    val decoded =
        try {
            ProjectManifestCodec.decode(manifestText)
        } catch (newer: NewerFormatException) {
            val bareState =
                ProjectState(
                    FontState(emptyList(), emptyMap()),
                    ProjectMeta(
                        ProjectManifest(
                            store.displayName,
                            env.now(),
                            Brief(BriefSource.BLANK),
                            emptyList(),
                            emptyMap(),
                            emptyList(),
                            ProjectPreferences(),
                        ),
                        ScrapbookManifest(),
                        emptyMap(),
                        JsonObject(emptyMap()),
                    ),
                )
            return LoadOutcome.NewerFormat(newer.found, bareState)
        } catch (invalid: IllegalArgumentException) {
            return LoadOutcome.Damaged(ProjectManifestCodec.PATH, invalid.message ?: "invalid typewright.json")
        }

    val lastWritten = mutableMapOf<String, ByteArray>(ProjectManifestCodec.PATH to manifestBytes)
    val glyphFiles = mutableMapOf<String, Pair<Glyph, ByteArray>>()
    val fileNameHints = mutableMapOf<String, Map<String, String>>()
    val masters = mutableListOf<Master>()
    for (decodedMaster in decoded.masters) {
        val prefix = "${decodedMaster.path}/"
        val ufoTexts = mutableMapOf<String, String>()
        for (path in allPaths.filter { it.startsWith(prefix) }) {
            val bytes = store.read(path) ?: continue
            ufoTexts[path.removePrefix(prefix)] = bytes.decodeToString()
            lastWritten[path] = bytes
        }
        val ufo =
            try {
                readUfoProject(ufoTexts)
            } catch (invalid: IllegalArgumentException) {
                return LoadOutcome.Damaged(decodedMaster.path, invalid.message ?: "invalid UFO")
            }
        val hints = readGlyphFileNames(ufoTexts)
        fileNameHints[decodedMaster.id] = hints
        for (glyph in ufo.glyphs) {
            val fileName = hints[glyph.name] ?: continue
            val path = "${prefix}glyphs/$fileName"
            lastWritten[path]?.let { glyphFiles[path] = glyph to it }
        }
        masters += Master(decodedMaster.id, decodedMaster.path, decodedMaster.styleName, decodedMaster.isDefault, ufo)
    }

    val lockStemHints = mutableMapOf<String, MutableMap<String, String>>()
    val locks = mutableMapOf<GlyphRef, GlyphLock>()
    for ((ref, decodedLock) in decoded.locks) {
        val master =
            masters.find { it.id == ref.master }
                ?: return LoadOutcome.Damaged(ProjectManifestCodec.PATH, "lock master \"${ref.master}\" not found")
        val approvedBytes =
            store.read(decodedLock.approvedPath) ?: return LoadOutcome.Damaged(decodedLock.approvedPath, "missing approved snapshot")
        lastWritten[decodedLock.approvedPath] = approvedBytes
        val approvedGlyph =
            try {
                parseGlif(approvedBytes.decodeToString(), master.ufo.lib.lineContourFormat)
            } catch (invalid: IllegalArgumentException) {
                return LoadOutcome.Damaged(decodedLock.approvedPath, invalid.message ?: "invalid approved snapshot")
            }
        glyphFiles[decodedLock.approvedPath] = approvedGlyph to approvedBytes
        val stem = ProjectPath.name(decodedLock.approvedPath).removeSuffix(".glif")
        lockStemHints.getOrPut(ref.master) { mutableMapOf() }[ref.glyph] = stem
        val bareEpisodes = decodedLock.episodes.map { UnlockEpisode(it.at, it.cause, it.reason, it.relockedAt, null) }
        val diffPaths = LockPaths.diffPaths(ref.master, stem, bareEpisodes)
        val episodes =
            decodedLock.episodes.mapIndexed { index, decodedEpisode ->
                val diffPath = diffPaths[index]
                val diffBytes = store.read(diffPath)
                if (diffBytes != null) lastWritten[diffPath] = diffBytes
                if (decodedEpisode.relockedAt != null) {
                    val text = diffBytes?.decodeToString() ?: return LoadOutcome.Damaged(diffPath, "missing frozen diff")
                    UnlockEpisode(decodedEpisode.at, decodedEpisode.cause, decodedEpisode.reason, decodedEpisode.relockedAt, text)
                } else {
                    UnlockEpisode(decodedEpisode.at, decodedEpisode.cause, decodedEpisode.reason, decodedEpisode.relockedAt, null)
                }
            }
        locks[ref] = GlyphLock(decodedLock.state, decodedLock.origin, decodedLock.approvedAt, approvedGlyph, episodes)
    }

    // §7.4: a locked glyph whose current outline no longer matches its snapshot was edited outside Typewright.
    val externalEdits = mutableListOf<GlyphRef>()
    for ((ref, lock) in locks.entries.toList()) {
        if (lock.state != LockState.LOCKED) continue
        val current =
            masters
                .find { it.id == ref.master }
                ?.ufo
                ?.glyphs
                ?.find { it.name == ref.glyph } ?: continue
        val dx = GlyphShape.translationDx(lock.approved, current)
        if (dx == null) {
            val episode = UnlockEpisode(env.now(), UnlockCause.EXTERNAL_EDIT, null, relockedAt = null, frozenDiff = null)
            locks[ref] = lock.copy(state = LockState.UNLOCKED, episodes = lock.episodes + episode)
            externalEdits += ref
        } else if (dx != 0) {
            locks[ref] = lock.copy(approved = GlyphShape.shiftX(lock.approved, dx))
        }
    }

    val scrapbookBytes = store.read(ScrapbookManifest.PATH)
    val scrapbook = scrapbookBytes?.let { ScrapbookManifestCodec.decode(it.decodeToString()) } ?: ScrapbookManifest()
    if (scrapbookBytes != null) lastWritten[ScrapbookManifest.PATH] = scrapbookBytes
    val images = mutableMapOf<String, ByteArray>()
    for (pin in scrapbook.pins) {
        val path = pin.imagePath ?: continue
        val bytes = store.read(path) ?: continue
        images[path] = bytes
        lastWritten[path] = bytes
    }

    val lessons = mutableMapOf<String, WorkbookLessons>()
    for (workbookKey in decoded.manifest.workbook.keys) {
        val path = WorkbookLessonsCodec.path(workbookKey)
        val bytes = store.read(path) ?: continue
        lastWritten[path] = bytes
        lessons[workbookKey] = WorkbookLessonsCodec.decode(bytes.decodeToString())
    }

    store.read(ProjectEncoder.BUILD_GITIGNORE_PATH)?.let { lastWritten[ProjectEncoder.BUILD_GITIGNORE_PATH] = it }

    val meta = ProjectMeta(decoded.manifest, scrapbook, lessons, decoded.unknownKeys)
    val state = ProjectState(FontState(masters, locks), meta)
    val caches = EncodeCaches(glyphFiles, fileNameHints, lockStemHints, images)
    return LoadOutcome.Opened(state, caches, lastWritten, externalEdits, migratedFrom = null)
}
