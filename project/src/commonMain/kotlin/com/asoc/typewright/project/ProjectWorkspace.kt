// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/** J5's resume point: which room, master and glyph to reopen on (docs/PROJECT_MODEL.md §5.4). */
data class ResumePoint(
    val section: String,
    val master: String?,
    val glyph: String?,
)

/** S01's row cache for one recent project; the project itself stays the source of truth. */
data class ProjectSummary(
    val glyphs: Int,
    val outliers: Int,
    val styleClass: String?,
)

/** One entry of `recent-projects.json`. */
data class RecentProject(
    val location: ProjectLocation,
    val name: String,
    val lastOpened: Instant,
    val lastEdited: Instant?,
    val resume: ResumePoint?,
    val summary: ProjectSummary?,
)

/**
 * The current session plus the app's recent projects (docs/PROJECT_MODEL.md §4 "Workspace",
 * §5.4). Owns at most one open [ProjectSession] at a time; opening or creating another first
 * leaves the previous one for the caller to have closed (this class does not close it for you,
 * so a caller that wants a clean handoff calls [closeCurrent] first).
 */
class ProjectWorkspace(
    private val storage: StorageProvider,
    private val config: AppConfigStore,
    private val env: () -> SessionEnvironment,
) {
    private val currentFlow = MutableStateFlow<ProjectSession?>(null)
    val current: StateFlow<ProjectSession?> = currentFlow.asStateFlow()

    private val recentsFlow = MutableStateFlow<List<RecentProject>>(emptyList())
    val recents: StateFlow<List<RecentProject>> = recentsFlow.asStateFlow()

    private val recentsMutex = Mutex()

    /** Opens the project remembered as last opened, or null if there is none or it fails to look up. */
    suspend fun reopenLast(): OpenResult? {
        val list = loadRecents()
        val index = list.lastOpen ?: return null
        val entry = list.projects.getOrNull(index) ?: return null
        return open(entry.location)
    }

    /** Opens the project at [location]; on success it becomes [current] and its recents entry is updated. */
    suspend fun open(location: ProjectLocation): OpenResult {
        val store =
            when (val lookup = storage.storeFor(location)) {
                is StoreLookup.Available -> lookup.store

                is StoreLookup.Missing -> return OpenResult.Damaged(displayNameOf(location), "The project's folder was moved or deleted.")

                is StoreLookup.PermissionNeeded -> return OpenResult.Damaged(
                    displayNameOf(location),
                    "Access to this project needs to be granted again.",
                )
            }
        val result = ProjectSession.open(store, env())
        sessionOf(result)?.let { session ->
            currentFlow.value = session
            rememberOpened(location, session)
        }
        return result
    }

    /** Creates `<spec.name>/` inside [parent] and opens it; on success it becomes [current] and is added to recents. */
    suspend fun create(
        parent: ProjectLocation,
        spec: NewProjectSpec,
    ): CreateResult {
        val childLocation = storage.createChild(parent, spec.name)
        val store =
            when (val lookup = storage.storeFor(childLocation)) {
                is StoreLookup.Available -> lookup.store
                else -> return CreateResult.NotWritable("Could not open the new project's folder.")
            }
        val result = ProjectSession.create(store, spec, env())
        if (result is CreateResult.Created) {
            currentFlow.value = result.session
            rememberOpened(childLocation, result.session)
        }
        return result
    }

    /** Opens [files] (a web `.zip` unpacked in memory) as an in-memory, tab-only project. Never added to recents. */
    suspend fun openInMemory(
        files: Map<String, ByteArray>,
        name: String,
    ): OpenResult {
        val store = InMemoryProjectStore(name, files)
        val result = ProjectSession.open(store, env())
        sessionOf(result)?.let { currentFlow.value = it }
        return result
    }

    /** Flushes the current session, if any. */
    suspend fun flush() {
        currentFlow.value?.flush()
    }

    /** Closes the current session (flushing first) and clears [current]. */
    suspend fun closeCurrent() {
        val session = currentFlow.value ?: return
        rememberEdited(session)
        session.close()
        currentFlow.value = null
    }

    /** Records [resume] against the current session's recents entry, so a copy of the project reopens on Home (D4) but this one resumes where it was. */
    suspend fun rememberResume(resume: ResumePoint) {
        val session = currentFlow.value ?: return
        updateRecents { list -> withResume(list, session.store.location, resume) }
    }

    /** Removes [location] from recents (a SAF grant, if any, is released by the storage layer). */
    suspend fun forget(location: ProjectLocation) {
        updateRecents { list -> withoutLocation(list, location) }
    }

    // ---- internals -------------------------------------------------------------------------------

    private fun sessionOf(result: OpenResult): ProjectSession? =
        when (result) {
            is OpenResult.Opened -> result.session
            is OpenResult.NewerFormat -> result.session
            else -> null
        }

    private suspend fun rememberOpened(
        location: ProjectLocation,
        session: ProjectSession,
    ) {
        updateRecents { list -> withOpened(list, location, session) }
    }

    private suspend fun rememberEdited(session: ProjectSession) {
        updateRecents { list -> withOpened(list, session.store.location, session) }
    }

    private fun summaryOf(session: ProjectSession): ProjectSummary {
        val state = session.state.value
        val glyphs = state.font.masters.sumOf { it.ufo.glyphs.size }
        val styleClass = state.meta.manifest.brief.styleClass.confirmed ?: state.meta.manifest.brief.styleClass.declared
        return ProjectSummary(glyphs, outliers = 0, styleClass = styleClass)
    }

    private fun withOpened(
        list: RecentProjectsFile,
        location: ProjectLocation,
        session: ProjectSession,
    ): RecentProjectsFile {
        val now = env().now()
        val existingIndex = list.projects.indexOfFirst { it.location == location }
        val entry =
            RecentProject(
                location = location,
                name = session.state.value.meta.manifest.name,
                lastOpened = now,
                lastEdited = now,
                resume = if (existingIndex >= 0) list.projects[existingIndex].resume else null,
                summary = summaryOf(session),
            )
        val projects = list.projects.toMutableList()
        val index = if (existingIndex >= 0) existingIndex.also { projects[it] = entry } else projects.size.also { projects.add(entry) }
        return RecentProjectsFile(projects, index)
    }

    private fun withResume(
        list: RecentProjectsFile,
        location: ProjectLocation,
        resume: ResumePoint,
    ): RecentProjectsFile {
        val index = list.projects.indexOfFirst { it.location == location }
        if (index < 0) return list
        val projects = list.projects.toMutableList()
        projects[index] = projects[index].copy(resume = resume)
        return RecentProjectsFile(projects, list.lastOpen)
    }

    private fun withoutLocation(
        list: RecentProjectsFile,
        location: ProjectLocation,
    ): RecentProjectsFile {
        val index = list.projects.indexOfFirst { it.location == location }
        if (index < 0) return list
        val projects = list.projects.toMutableList()
        projects.removeAt(index)
        val lastOpen =
            when {
                list.lastOpen == null -> null
                list.lastOpen == index -> null
                list.lastOpen > index -> list.lastOpen - 1
                else -> list.lastOpen
            }
        return RecentProjectsFile(projects, lastOpen)
    }

    private suspend fun loadRecents(): RecentProjectsFile {
        val bytes = config.read(RecentProjectsCodec.FILE_NAME) ?: return RecentProjectsFile(emptyList(), null)
        val list = runCatching { RecentProjectsCodec.decode(bytes.decodeToString()) }.getOrElse { RecentProjectsFile(emptyList(), null) }
        recentsFlow.value = list.projects
        return list
    }

    private suspend fun updateRecents(transform: (RecentProjectsFile) -> RecentProjectsFile) {
        recentsMutex.withLock {
            val updated = transform(loadRecents())
            config.writeAtomic(RecentProjectsCodec.FILE_NAME, RecentProjectsCodec.encode(updated).encodeToByteArray())
            recentsFlow.value = updated.projects
        }
    }

    private fun displayNameOf(location: ProjectLocation): String =
        when (location) {
            is ProjectLocation.FileSystem -> location.path
            is ProjectLocation.SafTree -> location.documentId
            is ProjectLocation.BrowserHandle -> location.key
            is ProjectLocation.InMemory -> location.name
        }
}

/** `recent-projects.json`'s content (docs/PROJECT_MODEL.md §5.4). */
internal data class RecentProjectsFile(
    val projects: List<RecentProject>,
    val lastOpen: Int?,
)
