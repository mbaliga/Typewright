// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.project.AppConfigStore
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.InMemoryProjectStore
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.ProjectZip
import com.asoc.typewright.project.SaveStatus
import com.asoc.typewright.project.SessionEnvironment
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The P11 WP5 stopgap wiring (docs/PROJECT_MODEL.md §13 "The thinnest non-visual wiring"): a
// ProjectWorkspace instance reachable from com.asoc.typewright.ui.TypewrightApp, desktop and web
// only, with no real folder picker or platform storage behind it yet -- WP3's
// DesktopStorageProvider/DesktopFolderPicker/WebFolderPicker are not in this worktree. Every type
// in this file is a plain, honestly in-memory fallback so the real ProjectWorkspace path (create,
// open, save, close) compiles and runs end to end without blocking on WP3; a real picker or store
// replaces these without the command palette entries themselves changing, since they only ever
// call through ProjectWorkspace and ProjectFolderPicker.

/** The open project's session, if any -- provided once, near the root, by [com.asoc.typewright.ui.TypewrightApp]. Null in any composition that never provides it (every existing screenshot test), so nothing downstream needs to change to keep behaving exactly as it did with no project open. */
val LocalProjectWorkspace: ProvidableCompositionLocal<ProjectWorkspace?> = staticCompositionLocalOf { null }

/** Whether the current platform can transfer a project as a `.zip` (web's in-memory mode, docs/PROJECT_MODEL.md §13's palette table); false anywhere that isn't provided. */
val LocalZipTransferSupported: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** The in-memory root every [InMemoryProjectFolderPicker] creates new projects inside, since there is no real folder to choose one within yet. */
val IN_MEMORY_WORKSPACE_ROOT: ProjectLocation.InMemory = ProjectLocation.InMemory("workspace")

/**
 * Where "New project…"/"Open project…" get a folder from (docs/PROJECT_MODEL.md §13's palette
 * table). The command palette entries call only through this interface, never a concrete picker,
 * so a real one (WP3) drops in without touching [com.asoc.typewright.ui.glass.CommandPalette].
 */
interface ProjectFolderPicker {
    /** A parent folder for a new project, or null if the person cancelled (or no real picker exists to ask). */
    suspend fun pickNewProjectParent(): ProjectLocation?

    /** An existing project's folder to open, or null if the person cancelled (or there is nothing to open). */
    suspend fun pickProjectToOpen(): ProjectLocation?
}

/**
 * The only [ProjectFolderPicker] this worktree has: no real folder browser (WP3's
 * `DesktopFolderPicker`/`AndroidFolderPicker`/`WebFolderPicker` are not landed here yet), so
 * [pickNewProjectParent] always resolves to [IN_MEMORY_WORKSPACE_ROOT] and [pickProjectToOpen]
 * reopens whichever project [workspace] most recently touched -- real [ProjectWorkspace] calls on
 * real (if in-memory) storage, not a faked result, so "New project…"/"Open project…" stay
 * testable end to end rather than being stubbed out as simply unavailable (CLAUDE.md law 4: a
 * stub says so, and every palette result string here does).
 */
class InMemoryProjectFolderPicker(
    private val workspace: ProjectWorkspace,
) : ProjectFolderPicker {
    override suspend fun pickNewProjectParent(): ProjectLocation = IN_MEMORY_WORKSPACE_ROOT

    override suspend fun pickProjectToOpen(): ProjectLocation? =
        workspace.recents.value
            .maxByOrNull { it.lastOpened }
            ?.location
}

/** An [AppConfigStore] held in memory: this worktree's fallback for recents until a real platform config store (WP3) is wired in. */
class InMemoryAppConfigStore : AppConfigStore {
    private val mutex = Mutex()
    private val files = mutableMapOf<String, ByteArray>()

    override suspend fun read(name: String): ByteArray? = mutex.withLock { files[name]?.copyOf() }

    override suspend fun writeAtomic(
        name: String,
        bytes: ByteArray,
    ) {
        mutex.withLock { files[name] = bytes.copyOf() }
    }
}

/**
 * A [StorageProvider] over [InMemoryProjectStore]s, keyed by the child location it minted for
 * them: this worktree's fallback until real platform storage (WP3) is wired in. [createChild]
 * always returns the *same* location for the same (parent, name) pair, so creating "Untitled"
 * twice inside the same parent finds the first attempt's own files still there -- the same
 * "already exists" signal real storage would give, which is what lets a caller retry "Untitled 2".
 */
class InMemoryStorageProvider : StorageProvider {
    private val stores = mutableMapOf<ProjectLocation, InMemoryProjectStore>()

    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        val store = stores[location] ?: return StoreLookup.Missing
        return StoreLookup.Available(store)
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        val parentName = (parent as? ProjectLocation.InMemory)?.name ?: IN_MEMORY_WORKSPACE_ROOT.name
        val location = ProjectLocation.InMemory("$parentName/$name")
        stores.getOrPut(location) { InMemoryProjectStore(location.name) }
        return location
    }
}

/**
 * A plain, non-`@Composable` [ProjectWorkspace] factory, usable both from a composable's
 * `remember` block ([rememberProjectWorkspace]) and from a platform's own `main()` (app-desktop,
 * app-web) so that entry point can hold the same instance for its own lifecycle hooks (window
 * close, tab visibility) without reaching back into Compose for it.
 */
fun createProjectWorkspace(
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.Default,
    storage: StorageProvider = InMemoryStorageProvider(),
    config: AppConfigStore = InMemoryAppConfigStore(),
): ProjectWorkspace = ProjectWorkspace(storage, config) { SessionEnvironment(scope = scope, io = io) }

/** [createProjectWorkspace], remembered for the composition's lifetime -- [com.asoc.typewright.ui.TypewrightApp]'s own default when no platform `main()` supplies one. */
@Composable
fun rememberProjectWorkspace(): ProjectWorkspace {
    val scope = rememberCoroutineScope()
    return remember { createProjectWorkspace(scope) }
}

/** [status] as the header's own mono slot shows it (docs/PROJECT_MODEL.md §13: "`<name> · saved/saving/in this tab only`"). */
fun saveStatusWord(status: SaveStatus): String =
    when (status) {
        is SaveStatus.Clean, is SaveStatus.Saved -> "saved"
        is SaveStatus.Pending, is SaveStatus.Saving -> "saving"
        is SaveStatus.TabOnly -> "in this tab only"
        is SaveStatus.Failed -> "save failed"
        is SaveStatus.Conflict -> "save conflict"
        is SaveStatus.ReadOnly -> "read-only"
    }

/** A blank new project's spec: exactly `UfoFontInfo(familyName = name, styleName = "Regular", unitsPerEm = 1000)`, nothing else invented (docs/PROJECT_MODEL.md §13's palette table). */
fun blankNewProjectSpec(name: String): NewProjectSpec =
    NewProjectSpec(
        name = name,
        brief = Brief(source = BriefSource.BLANK),
        scripts = listOf(BLANK_PROJECT_SCRIPT),
        masters =
            listOf(
                NewMaster(
                    ufo =
                        UfoProject(
                            fontInfo = UfoFontInfo(familyName = name, styleName = "Regular", unitsPerEm = 1000),
                            glyphs = emptyList(),
                        ),
                ),
            ),
    )

/**
 * "New project…": a folder from [picker], then `Untitled/` inside it -- retried as `Untitled 2`,
 * `Untitled 3`, … on a name clash (S02 semantics, docs/PROJECT_MODEL.md §13's palette table:
 * "creates Untitled/ inside; name clash gives Untitled 2"). Returns the command palette's own
 * result-line text.
 */
suspend fun createUntitledProject(
    workspace: ProjectWorkspace,
    picker: ProjectFolderPicker,
): String {
    val parent = picker.pickNewProjectParent() ?: return "New project…: no folder was chosen."
    for (suffix in 1..MAX_UNTITLED_ATTEMPTS) {
        val name = if (suffix == 1) "Untitled" else "Untitled $suffix"
        when (val result = workspace.create(parent, blankNewProjectSpec(name))) {
            is CreateResult.Created -> return "New project…: created \"$name\"."

            // "Untitled" (or "Untitled N") is already taken; loop around to try the next name.
            is CreateResult.NotEmpty -> Unit

            is CreateResult.NotWritable -> return "New project…: ${result.message}"
        }
    }
    return "New project…: too many existing \"Untitled\" projects."
}

/** "Open project…": a location from [picker], then [ProjectWorkspace.open] on it. Returns the command palette's own result-line text. */
suspend fun openPickedProject(
    workspace: ProjectWorkspace,
    picker: ProjectFolderPicker,
): String {
    val location = picker.pickProjectToOpen() ?: return "Open project…: nothing to open yet (no real folder picker in this build)."
    return when (val result = workspace.open(location)) {
        is OpenResult.Opened -> "Open project…: opened \"${result.session.state.value.meta.manifest.name}\"."
        is OpenResult.NewerFormat -> "Open project…: opened read-only (saved by a newer Typewright)."
        is OpenResult.NotAProject -> "Open project…: that folder has no Typewright project in it."
        is OpenResult.Damaged -> "Open project…: ${result.message}"
    }
}

/** "Save now": flushes the open project, if any. Returns the command palette's own result-line text. */
suspend fun saveProjectNow(workspace: ProjectWorkspace): String {
    val session = workspace.current.value ?: return "Save now: no project is open."
    workspace.flush()
    return "Save now: ${saveStatusWord(session.saveStatus.value)}."
}

/** "Close project": closes the open project, if any. Returns the command palette's own result-line text. */
suspend fun closeCurrentProject(workspace: ProjectWorkspace): String {
    val hadOne = workspace.current.value != null
    workspace.closeCurrent()
    return if (hadOne) "Close project: closed." else "Close project: no project is open."
}

/**
 * "Download project (.zip)": builds the open project's own real `.zip` bytes
 * ([ProjectSession.buildSnapshot], [ProjectZip.write], uncompressed -- `deflate = null` needs no
 * platform compression stream). This worktree has no way to hand those bytes to the browser as an
 * actual download (that needs DOM APIs `ui`'s common code cannot reach, and app-web has no such
 * plumbing yet either); the result line says so honestly rather than pretending a download
 * happened.
 */
suspend fun downloadProjectZip(workspace: ProjectWorkspace): String {
    val session = workspace.current.value ?: return "Download project (.zip): no project is open."
    val files = session.buildSnapshot(includeBuild = false)
    val bytes = ProjectZip.write(files, root = "", deflate = null)
    return "Download project (.zip): built ${bytes.size} bytes (this build has no real browser download wired up yet)."
}

private const val BLANK_PROJECT_SCRIPT = "Latn"
private const val MAX_UNTITLED_ATTEMPTS = 1_000
