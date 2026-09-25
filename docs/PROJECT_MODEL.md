# PROJECT_MODEL.md: Projects are real files (P11)

Status: design for P11, 25 Sep 2026. It covers the engine half, which lands now, and the UI half, which waits for P13's deck and the `ui/v1-screens/` mockups (OQ 123). Sources: brief §11, handoff §5, CLAUDE.md laws 1, 2, 5 and 7, PROMPTS_V1 P11/P12/P16/P17, SCREENS_V1 S01/S02/S06/S19, INTERACTION_V1 §5–§7, and OQ 75, 91, 93, 124 and 125.

## 1. Purpose

One object, the **`ProjectSession`**, owns the open project directory. It does five jobs:

- It holds the project's state in memory as immutable data.
- It routes every font edit from Draw, Space and Trace through one undo/redo history.
- It enforces law 1: an approved glyph is locked, and editing it needs an unlock, which leaves a stored diff.
- It autosaves: debounced, and atomic per file.
- It reopens the project exactly as it was (golden-path step 7).

Law 7 constrains it throughout. On disk, a project is only:
- UFO 3 directories;
- JSON;
- `.glif` snapshots and unified diffs;
- the user's own images.

Nothing Typewright-specific goes inside a UFO except two `com.asoc.typewright.*` lib keys, and only where they have a reason (§9).

The session has no Compose dependency. The UI observes it through `StateFlow`s. The golden-path harness drives it headlessly on the desktop JVM.

## 2. Module placement

| Module | Kind (plugin) | Licence | Package | Depends on | Used by |
|---|---|---|---|---|---|
| `:project` | pure, `typewright.kmp.pure` (jvm + wasmJs/Node) | **Apache-2.0** | `com.asoc.typewright.project` | `api(:core-font)` (so core-geometry), `api(kotlinx-coroutines-core)`, `kotlinx-serialization-json` | `:project:storage`, `:compile`, `:ui`, `:golden-path` |
| `:project:storage` | platform, `typewright.kmp.platform` (android + desktop + wasmJs/browser) | **Apache-2.0** | `com.asoc.typewright.project.storage` | `api(:project)`; androidMain `androidx.activity`; wasmJsMain `kotlinx-browser` | `:ui`, `:app-android` (androidTest) |

**Why two modules.**
- The session's history, lock and save logic is correctness-critical, and law 1 depends on it. By law 2 it belongs in a pure module that the SDK-free `core` CI job tests.
- SAF needs `android.*` and FSA needs browser interop, so they live in a platform module.
- The sub-module layout follows the `qa`/`qa:corpus` and `learn`/`learn:scenes` precedent. `typewrightNamespace` derives the packages above.
- `settings.gradle.kts`: `:project` goes in the pure block and `:project:storage` in the Android block.
- `tools/check_licences.py`: add `"project": APACHE`. That entry also covers `project/storage`.

**Why Apache.**
- The project format is engine-grade. Another tool (a CLI, a CI validator, a future fontc build) should be able to read and write it.
- `:compile` (Apache) must see the project's read view, and an Apache module may not depend on an FSL one.
- FSL modules (`ui`, `golden-path`, `campaign`) may depend on it.

**Consequence.** The scrapbook data model moves from `ui` (FSL) into `:project` (Apache). That relicenses about 60 lines (§14 D8).

**Reconciling `compile`'s `ProjectDirectory`.** Its read-only interface moves down, unchanged, as `ProjectFiles`:
- `compile/.../CompileRequest.kt` becomes `typealias ProjectDirectory = com.asoc.typewright.project.ProjectFiles`, and compile gains `api(project(":project"))`.
- No call site changes. That includes `HostedBuildProtocol` and golden-path's `FileProjectDirectory`.
- `ProjectFiles` stays **synchronous and read-only**. It is the snapshot type a build or export reads.
- The writable, possibly asynchronous store is a separate interface, `ProjectStore`, because FSA and SAF can't offer synchronous reads.
- A session hands compile a consistent snapshot through `buildSnapshot()`. That is in-memory bytes encoded from state, so an autosave can never race a build.

**How golden-path drives it.** `:golden-path` adds `testImplementation(project(":project"))` and uses `FileSystemProjectStore`, which is in `:project`'s `jvmMain`. There's no Compose and no `:project:storage`.

## 3. On-disk layout

```
Hyle Deco/                              the project directory; name as typed, with / \ : * ? " < > | and a leading dot replaced by _
├── typewright.json                     the project record (§3.1). Written last in every save.
├── HyleDeco-Regular.ufo/               one UFO 3 per master. Name: family without spaces, "-", style (S02; Google Fonts sources/ convention)
│   ├── metainfo.plist
│   ├── fontinfo.plist                  keys written alphabetically; unknown keys from a foreign UFO kept
│   ├── layercontents.plist
│   ├── lib.plist                       public.glyphOrder always; com.asoc.typewright.lineContourFormat only if "quadratic" (§9)
│   ├── groups.plist                    only when non-empty (existing rule)
│   ├── kerning.plist                   only when non-empty
│   ├── features.fea                    only when set
│   └── glyphs/
│       ├── contents.plist
│       └── *.glif                      glif format 2; <unicode>, qcurve, and <lib> only for the per-glyph format key (§9)
├── locks/                              law 1 (§7). Absent until the first approval.
│   └── regular/                        one folder per master id
│       ├── T_.glif                     the approved outline: a valid .glif with the same file name as in glyphs/
│       └── T_.20260925T110241Z.diff    unified diff, one per unlock episode (approved → edited)
├── scrapbook/                          created empty by S02
│   ├── manifest.json                   §3.2; absent until the first pin
│   └── <pin id>.<jpg|png|webp>         images the user pins, stored as given
├── lessons/                            created empty by S02
│   └── workbook-latn.json              §3.3; the Latin workbook's reflections; absent until the first one
├── comparisons/                        created empty by S02
│   ├── overlays.json                   saved overlays (S18); absent until one is saved; schema owned by P12
│   └── <Family>-<Style>.ttf            comparison fonts the user fetched or added (law 3: on request only). Bundled faces are referenced, not copied.
└── build/
    ├── .gitignore                      "*\n!.gitignore\n": compiled fonts and reports (P14/P15) can be regenerated
    └── .session-lock                   desktop only, while open (§5). Removed on close.
```

**Rules that apply across the tree:**
- All JSON is UTF-8 without a BOM, 2-space indent, keys in schema order, `\n` line endings, a trailing newline, and nulls written explicitly.
- Timestamps are UTC, whole seconds, ISO-8601 with `Z`. Every timestamp in a file comes from state, set by the command that caused it. Saving never mints a timestamp.
- Optional files are absent rather than empty.
- The session only ever writes or deletes paths it produced. That means the encoder's output for the current state, plus the last-written set, plus scrapbook images it copied in. It never touches `.git/`, a README, or unmodelled UFO content such as `images/`, `data/` or other layers.
- Navigation state is **not** in the project. The section, master and glyph (J5) live in the app's recent-projects file (§5.4). That keeps the project free of churn in git, and saving with no changes stays byte-identical.

### 3.1 `typewright.json` (format_version 1)

```jsonc
{
  "format": "typewright-project",           // fixed; lets any tool recognise the file
  "format_version": 1,                      // integer. A newer version opens read-only (§4 OpenResult.NewerFormat)
  "name": "Hyle Deco",                      // display name for S01. The family name lives in fontinfo.plist.
  "created": "2026-09-25T10:15:03Z",        // set at creation, never rewritten
  "brief": {                                // S02 step 2: Workbook Task 1 done up front
    "source": "font",                       // "paper" | "font" | "blank" (S02 step 1; picks the section a new project opens on)
    "intent": { "model": "sans", "use": "display" },   // "serif"|"sans" · "text"|"display"; each null until chosen
    "style_class": {
      "declared": "sans-geometric",         // qa:corpus class key from S02's chips; replaces DEFAULT_TASK4_STYLE_KEY (P12, triage gap 8)
      "confirmed": null                     // set when the user confirms a class (brief §11 "declared and confirmed"; DECISIONS D4)
    },
    "note": null                            // S02's optional one-line note
  },
  "scripts": ["Latn"],                      // ISO 15924 in the user's order: "Latn" "Hrkt" "Deva" "Arab". Which are "Preview" is app knowledge, not stored.
  "masters": [                              // V1 has exactly one; the list shape keeps brief §10 v2 (designspace) open
    { "id": "regular", "path": "HyleDeco-Regular.ufo", "style_name": "Regular", "default": true }
  ],
  "locks": {                                // law 1 (§7). Master id → glyph name → record; ordered by master, then by UFO glyph order
    "regular": {
      "T": {
        "state": "unlocked",                // "locked" | "unlocked"
        "origin": "trace",                  // "trace" (S10 accept) | "draw" (Ctrl L) | "import" (P12's choice, §14 D1)
        "approved_at": "2026-09-25T10:20:00Z",
        "approved": "locks/regular/T_.glif",
        "unlocks": [                        // oldest first; an episode with no edit is dropped when relocked
          { "at": "2026-09-25T11:02:41Z",
            "cause": "user",                // "user" | "external-edit" (§7.4)
            "reason": null,                 // optional text; V1's UI passes null
            "relocked_at": null,            // null while open
            "diff": "locks/regular/T_.20260925T110241Z.diff" }
        ]
      }
    }
  },
  "workbook": {                             // campaign progress (brief §11). Gate results are recomputed, never stored (law 5).
    "latn": {
      "confirmed_tasks": [1, 2, 3],         // ascending; tasks whose gate can't pass by construction (§10.3)
      "lessons": "lessons/workbook-latn.json"
    }
  },
  "comparison_fonts": [                     // Overlay layers 1–4 and Space's comparison, in layer order
    { "family": "Jost", "source": "bundled", "slug": "jost", "path": null },
    { "family": "Work Sans", "source": "file", "slug": null, "path": "comparisons/WorkSans-Regular.ttf" }
  ],                                        // source: "bundled" | "google-fonts" | "file"
  "preferences": {                          // working preferences that travel with the project; not navigation
    "space_control_strings": ["nnonnoonoo", "HHOHHOOHOO"],
    "overlay_word": null,
    "specimen_text": null
  }
}
```

Keys this version doesn't know (written by a newer minor version, or by P16's `capture` block) are kept verbatim, after the known keys, in their original order.

### 3.2 `scrapbook/manifest.json`

This is the existing `ScrapbookManifestCodec` schema (snake_case, `ui/.../ScrapbookManifest.kt`), moved to `:project`, plus `format_version`.

```jsonc
{ "format_version": 1,
  "pins": [ { "id": "signage-charminar", "kind": "photo",          // "photo" | "note"
              "caption_title": "Signage · Charminar", "caption_source": "photo",
              "image_path": "scrapbook/signage-charminar.jpg",     // relative to the project root; null for a note
              "note_text": null, "rotation_degrees": -1.8, "drives_design": true } ] }
```

Reflections are **not** copied in as pins. The board shows `manifest.pins` plus the reflections from lessons/, each as a note pin with the caption "Reflection · Task N" and rotation `stablePinRotationDegrees(id)`. That matches `WorkbookScreen.kt:627` today. Each fact has one source.

New pin ids are `pin-<n>`, with n one more than the highest existing number, so tests are deterministic.

### 3.3 `lessons/workbook-<script>.json`

```jsonc
{ "format": "typewright-lessons", "format_version": 1, "workbook": "latn",
  "reflections": [ { "id": "r-4-1", "task": 4, "at": "2026-09-25T14:03:11Z",
                     "text": "Round ends everywhere, or nowhere. Decide before the s." } ] }
```

Reflection ids are `r-<task>-<n>`, with n one more than the highest for that task. JSON rather than Markdown, because law 7 says "UFO 3 and JSON" (§14 D13).

## 4. ProjectSession API (Kotlin signatures)

```kotlin
package com.asoc.typewright.project

// ---- Files and storage ------------------------------------------------------------------------
/** Synchronous read-only view; compile's `ProjectDirectory` is a typealias of this. */
interface ProjectFiles {
    val displayName: String
    fun listFiles(): List<String>                 // relative, '/'-separated, sorted
    fun readBytes(path: String): ByteArray
}
class InMemoryProjectFiles(override val displayName: String, val files: Map<String, ByteArray>) : ProjectFiles

enum class Durability { PERSISTENT, TAB_ONLY }

interface ProjectStore {
    val location: ProjectLocation
    val displayName: String
    val durability: Durability
    suspend fun list(): List<String>                          // files only; paths validated (no .., absolute, '\', NUL)
    suspend fun read(path: String): ByteArray?                // null if absent
    suspend fun writeAtomic(path: String, bytes: ByteArray)   // creates parents; after return or crash the file is wholly old or wholly new
    suspend fun delete(path: String)                          // no-op if absent; prunes empty dirs under locks/
    suspend fun ensureDirectory(path: String)
    suspend fun recover(): RecoveryReport                     // sweeps *.tmp / *.new / *.crswap (§5)
    suspend fun acquireWriteLease(): WriteLease?              // null: open elsewhere, so open read-only
}
data class RecoveryReport(val rolledForward: List<String>, val discarded: List<String>)
fun interface WriteLease { suspend fun release() }

@Serializable sealed interface ProjectLocation {
    @Serializable @SerialName("file") data class FileSystem(val path: String) : ProjectLocation
    @Serializable @SerialName("saf") data class SafTree(val treeUri: String, val documentId: String) : ProjectLocation
    @Serializable @SerialName("browser-handle") data class BrowserHandle(val key: String) : ProjectLocation
    @Serializable @SerialName("memory") data class InMemory(val name: String) : ProjectLocation  // never put in recents
}

/** For backends without an atomic rename-over (SAF); SwapProtocolStore builds the §5 protocol on top of it. */
interface DocumentOps {
    suspend fun list(): List<String>
    suspend fun read(path: String): ByteArray?
    suspend fun writeTruncate(path: String, bytes: ByteArray)   // create or truncate, then sync
    suspend fun rename(path: String, newName: String)           // same directory; fails if the target exists
    suspend fun delete(path: String)
    suspend fun ensureDirectory(path: String)
}
class SwapProtocolStore(ops: DocumentOps, override val location: ProjectLocation, override val displayName: String) : ProjectStore
class InMemoryProjectStore(name: String) : ProjectStore                     // web fallback and tests; TAB_ONLY
// jvmMain:
class FileSystemProjectStore(root: java.nio.file.Path) : ProjectStore      // desktop and golden-path
class FileAppConfigStore(dir: java.nio.file.Path) : AppConfigStore

interface AppConfigStore {
    suspend fun read(name: String): ByteArray?
    suspend fun writeAtomic(name: String, bytes: ByteArray)
}
interface StorageProvider {
    suspend fun storeFor(location: ProjectLocation): StoreLookup
    suspend fun createChild(parent: ProjectLocation, name: String): ProjectLocation  // S02 creates <name>/ inside the chosen folder
}
sealed interface StoreLookup {
    data class Available(val store: ProjectStore) : StoreLookup
    data object Missing : StoreLookup                  // S01 "moved or deleted, Locate…"
    data object PermissionNeeded : StoreLookup         // S01 Android/web state
}

// ---- Model ------------------------------------------------------------------------------------
data class GlyphRef(val master: String, val glyph: String)
data class PointRef(val contour: Int, val point: Int)                        // indexes into Contour.points

data class ProjectState(val font: FontState, val meta: ProjectMeta)
data class FontState(val masters: List<Master>, val locks: Map<GlyphRef, GlyphLock>) {
    fun glyph(ref: GlyphRef): Glyph?
}
data class Master(val id: String, val path: String, val styleName: String, val isDefault: Boolean, val ufo: UfoProject)

enum class LockState { LOCKED, UNLOCKED }
enum class ApprovalOrigin { TRACE, DRAW, IMPORT }
enum class UnlockCause { USER, EXTERNAL_EDIT }
data class GlyphLock(val state: LockState, val origin: ApprovalOrigin, val approvedAt: Instant,
                     val approved: Glyph, val episodes: List<UnlockEpisode>)
data class UnlockEpisode(val at: Instant, val cause: UnlockCause, val reason: String?,
                         val relockedAt: Instant?, val frozenDiff: String?)  // frozenDiff is set on relock; an open episode's diff is derived

data class ProjectMeta(val manifest: ProjectManifest, val scrapbook: ScrapbookManifest,
                       val lessons: Map<String, WorkbookLessons>, val unknownKeys: JsonObject)
data class ProjectManifest(val name: String, val created: Instant, val brief: Brief, val scripts: List<String>,
                           val workbook: Map<String, WorkbookRecord>, val comparisonFonts: List<ComparisonFont>,
                           val preferences: ProjectPreferences)
data class Brief(val source: BriefSource, val model: ContrastModel?, val use: Use?, val styleClass: StyleClass, val note: String?)
data class StyleClass(val declared: String?, val confirmed: String?)
data class WorkbookRecord(val confirmedTasks: Set<Int>)
data class WorkbookLessons(val workbook: String, val reflections: List<Reflection>)
data class Reflection(val id: String, val task: Int, val at: Instant, val text: String)
data class ComparisonFont(val family: String, val source: ComparisonSource, val slug: String?, val path: String?)
data class ProjectPreferences(val spaceControlStrings: List<String>, val overlayWord: String?, val specimenText: String?)

// ---- Commands ---------------------------------------------------------------------------------
sealed interface FontCommand { val label: String; val coalesceKey: Any? get() = null }
data class MovePoints(val glyph: GlyphRef, val points: Set<PointRef>, val dx: Int, val dy: Int) : FontCommand
data class ReplaceOutline(val glyph: GlyphRef, val contours: List<Contour>, override val label: String) : FontCommand
data class SetAnchors(val glyph: GlyphRef, val anchors: List<Anchor>) : FontCommand
data class SetGlyphGuidelines(val glyph: GlyphRef, val guidelines: List<Guideline>) : FontCommand
data class SetAdvance(val glyph: GlyphRef, val width: Int) : FontCommand
data class SetSidebearings(val glyph: GlyphRef, val left: Int?, val right: Int?) : FontCommand
data class SetUnicodes(val glyph: GlyphRef, val unicodes: List<Int>) : FontCommand
data class AddGlyph(val master: String, val glyph: Glyph, val index: Int? = null) : FontCommand
data class RemoveGlyph(val glyph: GlyphRef) : FontCommand
data class RenameGlyph(val glyph: GlyphRef, val newName: String) : FontCommand      // renames kerning/group references and the lock key
data class SetKerning(val master: String, val first: String, val second: String, val value: Double?) : FontCommand  // null removes
data class SetGroup(val master: String, val name: String, val members: List<String>?) : FontCommand
data class SetFeatures(val master: String, val text: String?) : FontCommand
data class UpdateFontInfo(val master: String, val info: UfoFontInfo) : FontCommand
data class Approve(val glyph: GlyphRef, val origin: ApprovalOrigin) : FontCommand
data class Unlock(val glyph: GlyphRef, val reason: String? = null) : FontCommand
data class RevertToApproved(val glyph: GlyphRef) : FontCommand                      // restores the approved outline and relocks
data class AcceptTrace(val glyph: GlyphRef, val contours: List<Contour>, val advanceWidth: Int?) : FontCommand  // replace + approve, one entry
data class Batch(override val label: String, val commands: List<FontCommand>) : FontCommand   // all or nothing

sealed interface MetaChange {                                    // not in the font history; autosaved
    data class AddPin(val pin: ScrapbookPin, val image: ByteArray?, val imageExtension: String?) : MetaChange
    data class UpdatePin(val pin: ScrapbookPin) : MetaChange
    data class RemovePin(val id: String) : MetaChange
    data class AddReflection(val workbook: String, val task: Int, val text: String) : MetaChange
    data class SetTaskConfirmed(val workbook: String, val task: Int, val confirmed: Boolean) : MetaChange
    data class SetBrief(val brief: Brief) : MetaChange
    data class SetScripts(val scripts: List<String>) : MetaChange
    data class SetComparisonFonts(val fonts: List<ComparisonFont>) : MetaChange
    data class SetPreferences(val preferences: ProjectPreferences) : MetaChange
    data class Rename(val name: String) : MetaChange
}

sealed interface EditResult {
    data class Applied(val label: String, val changed: Set<GlyphRef>) : EditResult
    data object NoChange : EditResult
    data class Refused(val refusal: Refusal) : EditResult
}
sealed interface Refusal {
    val message: String                                          // one plain English sentence; the UI may restyle it
    data class Locked(val glyph: GlyphRef) : Refusal             // "T is approved and locked. Unlock it to edit; the change is kept as a diff."
    data class NoSuchGlyph(val glyph: GlyphRef) : Refusal
    data class NameClash(val name: String) : Refusal
    data class MixedCurveFormats(val glyph: GlyphRef) : Refusal  // §9: one curve format per glyph
    data class Invalid(override val message: String) : Refusal
    data class ReadOnly(override val message: String) : Refusal
}

// ---- Session ----------------------------------------------------------------------------------
class SessionEnvironment(
    val scope: CoroutineScope,                // autosave jobs; close() cancels its own child job
    val io: CoroutineDispatcher,
    val clock: Clock = Clock.System,          // kotlin.time; the session truncates to seconds
    val monotonic: TimeSource = TimeSource.Monotonic,
    val autosave: AutosavePolicy = AutosavePolicy(),
    val historyLimit: Int = 500,
    val coalesceWindowMillis: Long = 1_000,
)
data class AutosavePolicy(val debounceMillis: Long = 1_500, val maxDelayMillis: Long = 10_000, val enabled: Boolean = true)

data class NewProjectSpec(val name: String, val brief: Brief, val scripts: List<String>,
                          val masters: List<NewMaster>, val lockGlyphsAs: ApprovalOrigin? = null)
data class NewMaster(val id: String = "regular", val styleName: String = "Regular", val ufo: UfoProject)

class ProjectSession private constructor(/* … */) {
    val store: ProjectStore
    val state: StateFlow<ProjectState>
    val history: StateFlow<HistoryState>
    val saveStatus: StateFlow<SaveStatus>
    val economy: StateFlow<EconomySnapshot>   // updated synchronously inside execute/undo/redo/gesture updates
    val openReport: OpenReport
    val readOnly: String?                     // null when writable

    fun execute(command: FontCommand): EditResult          // commits an open gesture first
    fun simulate(command: FontCommand): Simulation         // INTERACTION_V1 §5 "consequence estimator": runs on a copy
    fun beginGesture(label: String): Gesture
    fun update(change: MetaChange): EditResult
    fun undo(): Boolean                                    // with a gesture open: cancels it
    fun redo(): Boolean
    fun lockOf(ref: GlyphRef): GlyphLock?

    suspend fun flush(): SaveStatus                        // write pending changes now
    suspend fun rewriteAll(): SaveStatus                   // re-encode every owned file, ignoring caches (repair; golden-path ⚑)
    suspend fun resolveConflict(keepMine: Boolean)
    suspend fun buildSnapshot(includeBuild: Boolean = false): ProjectFiles
    suspend fun close()                                    // flush, release the lease, stop autosave

    companion object {
        suspend fun create(store: ProjectStore, spec: NewProjectSpec, env: SessionEnvironment): CreateResult
        suspend fun open(store: ProjectStore, env: SessionEnvironment): OpenResult
    }
}
interface Gesture { fun update(command: FontCommand): EditResult; fun commit(); fun cancel() }
data class HistoryState(val undoLabels: List<String>, val redoLabels: List<String>) {
    val canUndo: Boolean get() = undoLabels.isNotEmpty()
    val canRedo: Boolean get() = redoLabels.isNotEmpty()
}
data class EconomySnapshot(val counts: Map<GlyphRef, GlyphCount>) { fun count(ref: GlyphRef): GlyphCount? = counts[ref] }
data class Simulation(val result: EditResult, val before: Map<GlyphRef, GlyphCount>, val after: Map<GlyphRef, GlyphCount>)
sealed interface SaveStatus {
    data object Clean : SaveStatus
    data class Pending(val since: Instant) : SaveStatus
    data object Saving : SaveStatus
    data class Saved(val at: Instant) : SaveStatus
    data class Failed(val path: String, val message: String) : SaveStatus
    data class Conflict(val paths: List<String>) : SaveStatus  // a file changed on disk since we last wrote it (§8.5)
    data class TabOnly(val unexportedChanges: Boolean) : SaveStatus
    data class ReadOnly(val reason: String) : SaveStatus
}
sealed interface OpenResult {
    data class Opened(val session: ProjectSession) : OpenResult
    data class NotAProject(val ufoPaths: List<String>) : OpenResult   // UFOs but no typewright.json: offer import (P12)
    data class Damaged(val path: String, val message: String) : OpenResult   // nothing written, nothing auto-fixed
    data class NewerFormat(val found: Int, val session: ProjectSession) : OpenResult   // read-only session
}
sealed interface CreateResult {
    data class Created(val session: ProjectSession) : CreateResult
    data class NotEmpty(val entries: List<String>) : CreateResult
    data class NotWritable(val message: String) : CreateResult        // S02 state
}
data class OpenReport(val recovery: RecoveryReport, val externalEdits: List<GlyphRef>, val migratedFrom: Int?)

// ---- Workspace: current session plus recents (§5.4) -----------------------------------------------
class ProjectWorkspace(storage: StorageProvider, config: AppConfigStore, env: () -> SessionEnvironment) {
    val current: StateFlow<ProjectSession?>
    val recents: StateFlow<List<RecentProject>>
    suspend fun reopenLast(): OpenResult?
    suspend fun open(location: ProjectLocation): OpenResult
    suspend fun create(parent: ProjectLocation, spec: NewProjectSpec): CreateResult   // makes <spec.name>/ inside parent
    suspend fun openInMemory(files: Map<String, ByteArray>, name: String): OpenResult  // web .zip
    suspend fun flush()
    suspend fun closeCurrent()
    suspend fun rememberResume(resume: ResumePoint)
    suspend fun forget(location: ProjectLocation)
}
data class ResumePoint(val section: String, val master: String?, val glyph: String?)

// ---- Zip (web export/import, and P14 D) --------------------------------------------------------
object ProjectZip {
    suspend fun write(files: ProjectFiles, root: String, deflate: (suspend (ByteArray) -> ByteArray)?): ByteArray  // fixed 1980 timestamps; sorted
    suspend fun read(bytes: ByteArray, inflate: suspend (ByteArray) -> ByteArray): Map<String, ByteArray>          // zip-slip safe; strips one shared top folder
}
```

**Threading.** Every non-`suspend` session call happens on one owner thread: the UI thread, or the test thread. State is immutable, so encoding runs on `Dispatchers.Default` against a snapshot and I/O runs on `env.io`. One `Mutex` means only one save runs at a time.

**How the UI observes.** It uses `session.state.collectAsState()`, and does the same for `history`, `saveStatus` and `economy`, all inside `ui`. It narrows with `derivedStateOf` (for example, the current glyph only), so a drag doesn't recompose the whole app. No Compose type enters `:project`.

**Live economy.** `economy` is recomputed inside every state transition, and synchronously. The cache is per glyph slot: `(ref) → (Glyph instance, GlyphCount)`, recounted only when `!==`. So S11's strip and S04's counts line follow edits with no manual recompute, and the golden-path ⚑ can read it straight after `execute`.

## 5. Storage actuals per platform

| | Desktop (Linux) | Android | Web, Chromium | Web, others |
|---|---|---|---|---|
| Store | `FileSystemProjectStore` (`:project` jvmMain) | `SwapProtocolStore(SafDocumentOps)` (`:project:storage` androidMain) | `FsaProjectStore` (wasmJsMain) | `InMemoryProjectStore`, `TAB_ONLY` |
| Atomic replace | write `X.tmp` (FileChannel, `force(true)`), then `Files.move(tmp, X, ATOMIC_MOVE, REPLACE_EXISTING)` in the same directory; best-effort directory fsync | **swap protocol**: write `X.tmp` + sync → rename to `X.new` → delete `X` → rename `X.new` → `X` | `createWritable({keepExistingData:false})` → `write` → `close()`. By spec the file changes only on close; on error, `abort()` | map put |
| Recovery on open | delete `*.tmp` | delete `*.tmp` (may be partial); `X.new` is complete, so roll forward: delete `X` if present, rename `X.new` → `X` | delete stale `*.crswap` | — |
| Picker | `zenity --file-selection --directory`, then `kdialog --getexistingdirectory`, then Swing `JFileChooser(DIRECTORIES_ONLY)` with the system look-and-feel | `ActivityResultContracts.OpenDocumentTree` with `EXTRA_INITIAL_URI`; then `takePersistableUriPermission(READ|WRITE)` | `window.showDirectoryPicker({mode:"readwrite"})` inside the user gesture | hidden `<input type=file accept=.zip>` for **Open project (.zip)** |
| App config | `$XDG_CONFIG_HOME/typewright/` (default `~/.config/typewright/`) via `FileAppConfigStore` | `filesDir/config/` via `FileAppConfigStore` | `localStorage["typewright.recent-projects"]`, with handles in IndexedDB `typewright/handles` keyed by `BrowserHandle.key` | same |
| Survives process death | files, plus recents with `last_open` | files, plus the persisted URI grant, plus recents; the app reopens the last project on launch | the handle in IndexedDB; on reload `queryPermission` must be `granted` (else a gesture is needed: `PermissionNeeded`) | **no**. Only the downloaded .zip survives; `beforeunload` prompts while there are unexported changes |
| Single writer | `FileChannel.tryLock()` on `build/.session-lock` (the OS frees it on crash); otherwise read-only | one app instance | Web Locks API `navigator.locks.request("typewright:<key>", {ifAvailable:true})`; otherwise read-only | — |

**Notes each implementation must honour:**
- **SAF** documents are created with MIME `application/octet-stream`, so the provider doesn't append an extension. Check the returned display name.
- **SAF** caches each directory listing (name → documentId) per directory, with one child-cursor query per directory, and invalidates it on its own writes.
- **SAF**'s `createChild` makes the project directory with `Document.MIME_TYPE_DIR` inside the granted parent tree. S02's model is "folder location" plus `<name>/`.
- A SAF tree whose root lacks create, delete or rename support (checked on a probe file in `createChild`/`open`) is refused with S02's "folder isn't writable" state, rather than saved unsafely.
- Removing a SAF project from recents calls `releasePersistableUriPermission`. Android caps persisted grants.
- **FSA** is hand-bound with `js()` externals. Promises are awaited with `kotlinx.coroutines`' `await()`. Recursive listing uses one JS helper that collects `entries()` into an array.
- **FSA**'s browser test runs against **OPFS** (`navigator.storage.getDirectory()`), which has the same handle API with no picker, in headless Chrome through Karma, as `ui/build.gradle.kts` does.
- **Zip (web).** `ProjectZip` in common code, with deflate through `CompressionStream("deflate-raw")` and inflate through `DecompressionStream("deflate-raw")`. `build/` is excluded by default. The three empty folders are written as directory entries.

**Why the swap protocol is safe.** A `.tmp` may be partial, so it is always discarded. A `.new` exists only after a completed write and sync, so rolling forward is always correct. After recovery every file is wholly old or wholly new, and no `.tmp` or `.new` remains. `SwapProtocolStore` is common code, so a crash-injection fake in commonTest tests it exhaustively; Android only supplies `DocumentOps`.

### 5.4 Recent projects (`recent-projects.json` in app config)

```jsonc
{ "format": "typewright-recent-projects", "format_version": 1,
  "last_open": 0,                                   // index, or null
  "projects": [ {
      "location": { "kind": "saf", "tree_uri": "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
                    "document_id": "primary:Documents/Hyle Deco" },
      "name": "Hyle Deco", "last_opened": "…", "last_edited": "…",
      "resume": { "section": "draw", "master": "regular", "glyph": "T" },      // J5
      "summary": { "glyphs": 338, "outliers": 38, "style_class": "sans-geometric" } } ] }  // S01 row cache, refreshed on close; the project stays the truth
```

## 6. History

**Mechanism: snapshots, not inverse commands.** Every `FontCommand` is a pure function `FontState → FontState | Refusal`. An entry stores `(label, before: FontState, after: FontState, coalesceKey, at)`. Undo sets `font = before`. Redo sets `font = after`.

Lists of immutable `Glyph`s share structure, so an entry costs one list copy (about 3 KB for 338 glyphs) plus the changed glyph. Undo is exact by construction, which is what P17's "200 random operations, then undo-all gives byte-identical UFO output" needs.

- **In history:** everything in §4's `FontCommand`. That is every Draw, Space and Trace edit, plus font info (S06), glyph add, remove and rename, and lock and unlock.
- **Not in history:** `MetaChange` (scrapbook, lessons, workbook confirmations, brief, scripts, comparison fonts, preferences). Undo in Draw must never delete a reflection written in the Workbook.
- **Only the font part is snapshotted.** The state is `ProjectState(font, meta)`, and an entry holds only `FontState`, so meta is untouched by undo.

**Coalescing.**

| Command | Merges into the top entry when | Key |
|---|---|---|
| any command inside a `Gesture` (a drag) | always: `beginGesture` → `update`… → `commit` gives one entry; `cancel` restores | — |
| `MovePoints` (arrow nudges) | same glyph and the same point set, within 1,000 ms | `(glyph, points)` |
| `SetKerning` | same master and pair, within 1,000 ms | `(master, first, second)` |
| `SetAdvance` / `SetSidebearings` | same glyph and side, within 1,000 ms | `(glyph, side)` |
| `UpdateFontInfo` (scrubbing a value) | same master and the same changed field, within 1,000 ms | `(master, field)` |
| `SetFeatures` | same master, within 1,000 ms | `(master)` |
| `ReplaceOutline`, Add/Remove/Rename glyph, `SetUnicodes`, `SetGroup`, `Batch` | never | — |
| `Approve`, `Unlock`, `AcceptTrace`, `RevertToApproved` | never, and they end any coalescing run | — |

**Gesture rules:**
- While a gesture is open, state and economy update live, and autosave waits.
- `execute` commits the open gesture first.
- `undo()` cancels it.

**Size.** 500 entries. The oldest are dropped. That's 2.5 times P17's 200-operation fuzz.

**Persistence: not kept across reopen.** The reasons:
1. Law 1's durable record is the lock table and the diffs, which *are* on disk.
2. A history journal would be a Typewright-only blob next to the UFO (law 7), and it would churn in git.
3. Glyphs, RoboFont and FontForge don't keep undo across reopen either.
4. It keeps step 7's "B equals A" exact.

History survives an Android configuration change, because the workspace is process-scoped rather than held by the Activity. It is lost on process death. Data is not lost (§8).

## 7. Locks and diffs (law 1)

### 7.1 What is locked, and how refusal works
- `Approve` (Ctrl L in Draw; `AcceptTrace` from S10; import if P12 chooses) records a `GlyphLock`: state LOCKED, origin, `approvedAt`, and the approved `Glyph`, snapshotted to `locks/<master>/<glif name>`.
- **Enforcement is by effect, not by command type**, so a future command can't bypass it. After computing a new `FontState`, the session compares every glyph that was LOCKED before and has changed (`!==`). If its **shape** differs, or it was removed, the whole command is refused as `EditResult.Refused(Refusal.Locked(ref))`. State, history and autosave are untouched. No exception is thrown.
- "Shape" means contours and anchors, compared **modulo one horizontal translation**. So Space's sidebearing and advance edits, kerning, unicodes and rename all work on locked glyphs. Spacing doesn't regenerate the drawing (§14 D2).
- When a sidebearing shift moves a locked glyph, the same `dx` is applied to its approved snapshot, so later diffs stay minimal.
- `simulate()` returns the same refusal. That lets the command registry show disabled rows with their reason (INTERACTION_V1 §5 item 6).
- **Re-trace never overwrites.** `AcceptTrace` and `ReplaceOutline` on a LOCKED glyph are refused. S10 shows "Unlock to re-trace". `engine-trace` never touches the project; it only returns contours. A bulk "trace all" (P16) issues one `AcceptTrace` per glyph and reports the refused ones, because `Batch` is all-or-nothing.

### 7.2 What an unlock records
`Unlock(ref, reason)` appends an `UnlockEpisode`:
- `at`, the command time (UTC, seconds);
- `cause = USER`;
- `reason`, which is null from V1's UI;
- `relockedAt = null`;
- a diff path `locks/<master>/<glif stem>.<yyyyMMdd'T'HHmmss'Z'>.diff`, with `-2`, `-3` and so on if two land in the same second.

The approved snapshot stays as it is.

### 7.3 The diff format
A plain **unified diff** (`diff -u`, 3 lines of context) of `writeGlif(approved)` against `writeGlif(current)`. It's generated in pure Kotlin (Myers line diff in `:project`), and a glif has one point per line, so it reads well:

```
--- locks/regular/T_.glif	2026-09-25T10:20:00Z
+++ HyleDeco-Regular.ufo/glyphs/T_.glif	2026-09-25T11:02:41Z
@@ -6,7 +6,7 @@
     <contour>
-      <point x="42" y="1" type="line"/>
+      <point x="42" y="0" type="line"/>
```

- **While the episode is open,** the diff is derived at save time: written when current ≠ approved, deleted when they're equal again.
- **On relock** (`Approve` or `AcceptTrace` on an UNLOCKED glyph), the diff text is frozen into the episode, `relockedAt` is set, and the snapshot is replaced with the new approved outline. An episode with no edit is dropped.
- `patch -R` against the current glif reconstructs each earlier approved version, so the chain is a complete audit trail in plain files.
- Undo removes episodes and diff files like any other state. The net change is zero, so nothing is lost.

### 7.4 Edits made outside Typewright
Law 7 invites edits in RoboFont or FontForge. On open, each LOCKED glyph is compared with its snapshot using the same shape rule. If it differs, the session:
- appends an episode with `cause = EXTERNAL_EDIT`, whose diff runs from the snapshot to the current outline;
- sets the glyph UNLOCKED;
- lists it in `OpenReport.externalEdits`.

It never reverts the change, and never silently relocks it (§14 D10).

## 8. Autosave

1. **Debounce: 1,500 ms** after the last change, with a **10 s cap** from the first unsaved change, so continuous editing still saves.
   - `Approve`, `Unlock`, `AcceptTrace` and `RevertToApproved` save **immediately**, because law-1 events shouldn't wait.
   - An open gesture delays saving until it's committed.
2. **Immediate flush on:**
   - `close()`;
   - Android `onStop` (on the app scope);
   - desktop window close (`runBlocking { workspace.closeCurrent() }`) plus a shutdown hook;
   - web `visibilitychange → hidden`;
   - "Save now".
3. **One save:**
   - (a) Snapshot the state.
   - (b) Encode to an ordered `path → bytes` map.
   - (c) Compare with `lastWritten`.
   - (d) Write the changed files in phase order: **leaf** files (`locks/**`, scrapbook and lessons JSON, `.glif`), then UFO **index** files (`contents.plist`, `layercontents.plist`, `fontinfo.plist`, `groups/kerning.plist`, `features.fea`, `lib.plist`, `metainfo.plist`), then **`typewright.json`**.
   - (e) Delete what `lastWritten` has and the target doesn't.
   - (f) Update `lastWritten`.

   The order matters: a crash between any two writes leaves every reference pointing at a file that exists, as either the old or the new version.
4. **Which change rewrites what.** This falls out of the byte diff:

| Change | Files written |
|---|---|
| point move, outline op, anchors, glyph guidelines | that glyph's `.glif` (plus its open lock diff) |
| add, remove or rename a glyph | the `.glif`(s), `contents.plist`, `lib.plist` (glyph order) |
| kerning, groups, features | `kerning.plist` / `groups.plist` / `features.fea` (created or deleted as they become non-empty or empty) |
| font info | `fontinfo.plist` |
| approve / relock | `locks/<m>/<g>.glif`, the frozen diff, `typewright.json` |
| unlock | `typewright.json` (and the diff after the first edit) |
| pin / reflection / confirmation | `scrapbook/manifest.json` (plus the image) / `lessons/workbook-latn.json` / `typewright.json` |

5. **Only changed glyphs are re-serialized.** An encode cache per glyph slot holds `(master, name) → (Glyph instance, bytes)`. At load it's seeded **with the on-disk bytes**. The fontinfo, kerning and lib slots are cached the same way. Consequences:
   - an unedited glyph in a foreign UFO keeps its original formatting and any unmodelled `<note>`/`<lib>`;
   - undo-all restores the original bytes;
   - `writeUfoProject` gets the loaded `contents.plist` mapping as `fileNameHints`, so glif file names never shift.
6. **Crash safety.** Each file is atomic (§5), and phase order holds references together across files. The most that can be lost is the edits since the last completed save: at most 1.5 s, or 10 s under continuous editing. On Android, `onStop` makes that about zero for normal backgrounding. A kill within milliseconds of `onStop` can still lose the last save; this is stated, not hidden.
7. **Conflict guard.** Before overwriting a file listed in `lastWritten`, the save reads it back. If the bytes differ, the file changed outside Typewright. The session then:
   - stops saving;
   - keeps the state dirty;
   - reports `SaveStatus.Conflict(paths)`.

   `resolveConflict(keepMine)` either overwrites or reopens. That protects the user's edits in other tools, which are the user's drawing.
8. **Never leaving `*.tmp` behind:**
   - every `writeAtomic` deletes its temp file in a `finally` on failure;
   - `recover()` runs on every open;
   - `close()` checks that the owned tree has no `*.tmp` or `*.new`;
   - a crash-injection test aborts at every write index and asserts recovery leaves nothing behind and every file wholly old or new.
9. **Saving with no changes is byte-identical** (step 7 ⚑):
   - no change means no save job, and `flush()` and `close()` write nothing;
   - opening writes nothing unless it recovered temp files or found an external edit, both reported;
   - migrations are written only with the first real edit;
   - `rewriteAll()` re-encodes everything and still gives identical bytes. Encoding is a pure, deterministic function of state (fixed key order, sorted lists, canonical numbers, no timestamps made at save time), and codec tests prove that reading what we wrote gives back the same state.

## 9. Unicodes and quadratics

### 9.1 OQ 124: unicodes live on core-geometry's `Glyph`
```kotlin
data class Glyph(val name: String, val advanceWidth: Int, val contours: List<Contour>,
                 val anchors: List<Anchor> = emptyList(), val guidelines: List<Guideline> = emptyList(),
                 val unicodes: List<Int> = emptyList())   // first = primary (UFO spec); init: each in 0..0x10FFFF, no duplicates
```

**Why on `Glyph`, not a UFO wrapper:**
- `Glyph` already carries UFO-level `anchors` and `guidelines`.
- `qa` (cmap coverage, S04's "Latin 116/324"), `campaign` and `ui` all take `Glyph`/`UfoProject`. A wrapper would force every consumer to unwrap.
- A trailing parameter with a default breaks no call site, and `copy()` preserves it.

**GlifCodec:**
- `<unicode hex="0041"/>` elements are written right after `<advance>` (fontTools' element order), formatted `%04X`, in list order.
- On read: hex, case-insensitive, deduplicated keeping the first occurrence (as ufoLib does); invalid hex throws.

**Bridge.** `SfntFont.toUfoProject()` fills `unicodes` from `cmap.bestMapping`, inverted to glyph id → ascending code points. This is in P11, because S-ufo and step 7 must round-trip real code points. P12 keeps step 1a's "every cmap entry is present" check and the licence audit.

### 9.2 OQ 125: writing `qcurve`, in P11
This lands in **P11**, not P12. `Seeds.newSUfoDirectory()` writes Hyle Deco's quadratic outlines, and `writeGlif` refuses them (`GlifCodec.kt:246`). So the seeds for steps 3, 4 and 7 can't be built today. P12 keeps the import decisions: labelling outlines as quadratic, and whether imports are locked.

**Writing a QUADRATIC contour.** The TrueType point order is kept verbatim (law 1: the source outline, including its start point, which may be off-curve). Implied on-curve points are **not** made explicit; UFO's `qcurve` keeps TrueType's implied-midpoint rule.
- off-curve → `<point x y/>`;
- on-curve whose cyclic predecessor is off-curve → `type="qcurve"`;
- on-curve after an on-curve → `type="line"`;
- a contour with no on-curve point → every point untyped (the all-implied TrueType case).

```
QUADRATIC points: on(250,0) off(460,0) off(460,300) on(250,300) off(40,300) off(40,0)
<contour>
  <point x="250" y="0" type="qcurve"/>    <!-- predecessor off(40,0) → qcurve -->
  <point x="460" y="0"/>
  <point x="460" y="300"/>                <!-- implied on-curve (460,150) stays implied -->
  <point x="250" y="300" type="qcurve"/>
  <point x="40" y="300"/>
  <point x="40" y="0"/>
</contour>
```

**Reading back.**
- A contour with any `qcurve` point, or no typed point at all, becomes QUADRATIC, with points in file order, typed ones on-curve, untyped and `type="offcurve"` ones off-curve.
- `curve` gives CUBIC, as today.
- `curve` mixed with `qcurve` in one contour is refused with a clear message (P12 may convert).
- `line` preceded by off-curves is refused, per spec.
- **Fix in the same change:** an explicit `type="offcurve"` must read as off-curve. Today any non-null type is treated as on-curve.

**The all-line ambiguity.** A contour of only `line` points (H, T, E…) is identical as either format, but the counts differ: `countCubic` counts a degenerate line's two controls as off-curve, while QUADRATIC counts 0 (the OQ 20 issue). Reading H back as cubic would change 12·0 to 12·24 after a reopen, which breaks law 5. The rule:
- **Invariant, enforced by the session:** one `CurveFormat` per glyph (already `Glyph`'s KDoc convention). Violations are refused with `MixedCurveFormats`. P17's tools convert explicitly (exact elevation).
- **A glyph's format on read:** any `qcurve` → QUADRATIC; any `curve` → CUBIC; otherwise the glyph lib key `com.asoc.typewright.lineContourFormat`; otherwise the font lib key of the same name; otherwise CUBIC.
- **Font lib key** (`lib.plist`): `"quadratic"` for projects created from TrueType (the bridge sets `UfoLib.lineContourFormat = QUADRATIC`). Absent for cubic projects.
- **Glyph lib key** (`<lib>` in the glif, keys alphabetical): written **only** for an all-line glyph whose format differs from the font default. That happens only in mixed projects, such as J2 after a glyph is converted to cubic.

The effect is that `readUfoProject(writeUfoProject(p)) == p` for every project the session accepts, and the counts never change across a reopen. If P16 later gives cubic `Contour` a real line kind (OQ 20), the glyph key becomes unnecessary.

**Core-font API changes:**
```kotlin
data class UfoLib(val lineContourFormat: CurveFormat = CurveFormat.CUBIC, val other: PlistValue.PDict = PlistValue.PDict(emptyList()))
data class UfoProject(val fontInfo: UfoFontInfo, val glyphs: List<Glyph>, val kerningInfo: UfoKerning = UfoKerning(), val lib: UfoLib = UfoLib())
fun parseGlif(xml: String, lineContourDefault: CurveFormat = CurveFormat.CUBIC): Glyph
fun writeGlif(glyph: Glyph, lineContourDefault: CurveFormat = CurveFormat.CUBIC): String
fun writeUfoProject(project: UfoProject, fileNameHints: Map<String, String> = emptyMap()): Map<String, String>  // lib.plist always carries public.glyphOrder
fun readUfoProject(files: Map<String, String>): UfoProject                                                       // orders by public.glyphOrder when present
fun readGlyphFileNames(files: Map<String, String>): Map<String, String>                                          // the contents.plist map, for hints
```

**`UfoFontInfo` gains S06's fields**, so the UI half and OQ 93's designer aren't blocked:
- `copyright`, `trademark`;
- `openTypeNameDesigner(URL)`, `openTypeNameManufacturer(URL)`, `openTypeNameLicense(URL)`;
- `openTypeNamePreferredFamilyName`, `openTypeNamePreferredSubfamilyName`, `postscriptFontName`, `openTypeOS2VendorID`;
- hhea, typo and win vertical metrics;
- `other: List<Pair<String, PlistValue>>`, which keeps unknown keys.

All keys are written alphabetically, as today.

## 10. Migration of the in-memory state

1. **Scrapbook (OQ 75).**
   - `ScrapbookPinKind`, `ScrapbookPin`, `ScrapbookManifest` (plus `format_version`), `ScrapbookManifestCodec`, `stablePinRotationDegrees`, `PIN_ROTATION_RANGE_DEGREES` and `stableHashCode` (made public as `stablePinHash`) move to `project/.../project/scrapbook/ScrapbookManifest.kt`. `ScrapbookManifestTest` moves with them.
   - `SampleScrapbook` and the look helpers stay in `ui`.
   - `ScrapbookTab` (`ScrapbookTab.kt:100`): with a project open, pins are `state.meta.scrapbook.pins` plus reflection pins, and "+ note"/"+ photo" call `update(AddPin …)`. With no project open, it behaves as today, with its disclosure.
2. **Workbook reflections (OQ 93).** `WorkbookReflectionSection` (`WorkbookScreen.kt:584-586`): with a project open, "Save to scrapbook" calls `update(AddReflection("latn", task.index, text))`, and the "N saved" line counts persisted reflections for the task. With no project, today's in-memory behaviour stays.
   - The designer stand-in "Madhav": `ShipMetadata.from(fontInfo)` reads `openTypeNameDesigner`. The reference-project swap is P12's.
3. **Workbook progress (OQ 91).** `campaignProgress(tasks, gates, confirmedTasks: Set<Int> = emptySet())`.
   - A task is **confirmable** when its gate can't pass by construction: `!task.gate.isImplemented` (tasks 1, 2, 7, 8, 10), or every check is `INFO` (task 3's overshoot gate, `WorkbookGates.kt:131`).
   - A confirmed, confirmable task reads DONE. Task 11 (implemented, needs a compiled font) is not confirmable.
   - Confirmations are stored in `typewright.json` `workbook.latn.confirmed_tasks`.
   - **Trigger (§14 D3):** saving a reflection on a confirmable task confirms it. S19's ⋯ "Reset task" unconfirms it (UI half).
4. Nothing was ever persisted before, so there is no on-disk migration. A scrapbook manifest without `format_version` decodes as version 1. `ProjectCodec.migrate(json, from)` exists with no steps yet.

## 11. Validation (ufoLib)

- **Kotlin writer test:** `project/src/jvmTest/kotlin/com/asoc/typewright/project/UfoLibValidationSamplesTest.kt`, in the SDK-free `core` job's `jvmTest`. It writes full projects into `project/build/ufo-validation/<sample>/`, using the system properties `typewright.ufoValidationDir` and `typewright.repoRoot`:
  1. Hyle Deco through the bridge (quadratic, `qcurve`, unicodes, font key), with a lock, a frozen and an open diff, a pin and a reflection;
  2. Poppins (S-ttf2);
  3. a synthetic cubic project: curve and line points, anchors, glyph and font guidelines, unicodes, groups, kerning, features, the full S06 fontinfo;
  4. a mixed project that triggers the glyph key;
  5. an all-off-curve quadratic contour.

  Beside each `.ufo` it writes `expectations.json`: for each glyph, its unicodes, advance and point-type sequence per contour.
- **Validator:** `tools/validate_ufo.py` (Apache-2.0), with `tools/requirements-ufolib.txt` pinning `fonttools==4.66.0`. Confirm that version exists on PyPI when WP4 runs; otherwise pin the latest 4.x. For each `.ufo`:
  - `UFOReader(path, validate=True)`: `readMetaInfo`, `readInfo`, `readGroups`, `readKerning`, `readLib`, `readFeatures`;
  - for every layer, `getGlyphSet(validateRead=True)`, and `readGlyph(name, obj, pen, validate=True)` for **every glyph**, once through a point recorder and once through `PointToSegmentPen(RecordingPen())`;
  - the result is compared with `expectations.json`.

  It also:
  - loads `typewright.json`, `scrapbook/manifest.json` and `lessons/*.json` and checks `format`/`format_version`;
  - checks that every path they reference exists;
  - parses each `locks/**/*.glif` with `glifLib.readGlyphFromString(validate=True)`;
  - for each open episode, checks that applying the diff to the snapshot reproduces the current glif.

  It exits 1 and prints every problem.
- **CI:** new final steps in the **`core`** job: `actions/setup-python` (3.12), then `pip install -r tools/requirements-ufolib.txt`, then `python3 tools/validate_ufo.py project/build/ufo-validation`. Also, in the `android` job, `:app-android:assembleDebugAndroidTest`, so the device tests compile (they aren't run in CI; law 4). In the `web` job, `:project:storage:wasmJsBrowserTest`.

## 12. Golden-path mapping

P11 makes **step 3 and step 7 pass**, both headlessly on the desktop JVM. It adds `3` and `7` to `golden-path/passing-steps.txt`. Step 1a's `notBuilt` message changes to name only P12: import flow and licence audit.

- **Harness changes:**
  - `golden-path/build.gradle.kts` adds `testImplementation(project(":project"))`.
  - A new `ProjectFixtures.kt` gives a `SteppingClock` (starts at 2026-09-25T10:00:00Z, one second per call), an `env()` using `runBlocking` and a real scope, `hashTree(root)`, `assertNoTempFiles(root)`, and `readUfoDir(dir): Map<String, String>`.
  - Seeds are unchanged. S-ufo now writes, because of §9.2.

**Step 7 (P11 alone):**
```kotlin
val root = tempDir("step7"); val sUfo = readUfoProject(readUfoDir(Seeds.newSUfoDirectory()))
val a = (ProjectSession.create(FileSystemProjectStore(root.toPath()),
    NewProjectSpec("Hyle Deco", Brief(source = FONT, styleClass = StyleClass("sans-geometric", null), …), listOf("Latn"),
                   listOf(NewMaster(ufo = sUfo))), env()) as Created).session
val T = GlyphRef("regular", "T"); val H = GlyphRef("regular", "H")
a.execute(Approve(T, TRACE)); a.execute(Unlock(T)); a.execute(MovePoints(T, setOf(PointRef(0, 0)), 0, -1)); a.execute(Approve(T, DRAW))  // frozen diff
a.execute(Approve(H, DRAW)); a.execute(Unlock(H)); a.execute(MovePoints(H, setOf(PointRef(0, 0)), 5, 0))                           // open diff
a.execute(SetKerning("regular", "T", "o", -40.0)); a.execute(UpdateFontInfo("regular", a.font().fontInfo.copy(openTypeNameDesigner = "Golden Path")))
a.update(AddPin(ScrapbookPin(...note...), null, null)); a.update(AddReflection("latn", 4, "…")); a.update(SetTaskConfirmed("latn", 1, true))
val expected = a.state.value; a.close()
assertNoTempFiles(root); val hashes = hashTree(root)
val b = (ProjectSession.open(FileSystemProjectStore(root.toPath()), env()) as Opened).session
assertEquals(expected, b.state.value)                                                          // project, locks, diffs, typewright.json, scrapbook, lessons
assertEquals(expected.font.masters.single().ufo, readUfoProject(readUfoDir(root / "HyleDeco-Regular.ufo")))   // ⚑
b.close(); assertEquals(hashes, hashTree(root))                                                // ⚑ saving with no changes: no writes
val c = (ProjectSession.open(…) as Opened).session; c.rewriteAll(); c.close(); assertEquals(hashes, hashTree(root))  // ⚑ re-encoding is byte-identical
assertNoTempFiles(root)
```

**Step 3.** Its sketch is entirely the session API, so P11 makes it pass:
- *A point move changes only that point:* `MovePoints(n, {(0,0)}, 10, 0)`, then compare every other point of every glyph.
- ⚑ *Live economy:* after `ReplaceOutline(n, …)` inserts one on-curve midpoint on a straight run, `economy.value.count(n) == state.glyph(n).count()`, and it differs from the count before.
- *Kerning:* `SetKerning(T, o, -40)`. ⚑ After `flush()`, `readKerningPlist` of `kerning.plist` on disk gives −40.
- *Locked glyph:* after `Approve(H)`, `MovePoints(H)` returns `Refused(Locked)`. After `Unlock(H)` and a move, then `flush()`, `locks/regular/H_.<ts>.diff` exists and contains the `-`/`+` lines for that point.
- *Undo-all:* `while (undo())`; `font == initial`; `flush()`. The hash tree equals the tree right after `create`: `kerning.plist` and `locks/**` are deleted, and `typewright.json` is identical. Then `while (redo())` restores −40 and the diff.

**What stays P17's:**
- routing every Draw, Space and palette tool through these commands (tools compute outlines with core-geometry and submit `ReplaceOutline`/`MovePoints`/`Gesture`);
- the tool-level commands (delete-node-keeping-curve, knife, booleans, primitives bake);
- the 200-operation fuzz with undo-all byte identity, which is its own acceptance;
- the kerning-groups UI and `.fea` editing;
- the undo gestures (2- and 3-finger tap, Ctrl Z / Ctrl ⇧ Z).

## 13. The P11 engine half versus the UI half

**Engine half (lands now, no mockups needed):**
- the model, codec and session in `:project`;
- the storage actuals and pickers in `:project:storage`;
- core-font fidelity (§9);
- campaign confirmations;
- the compile typealias;
- golden-path steps 3 and 7;
- ufoLib validation in CI;
- the Android instrumented test source and the owner script.

**The Android instrumented test**, owner-run (law 4). It lives in `app-android/src/androidTest/…`:
- **`ProjectSaveDeviceTest`:**
  - launch `MainActivity` and call `activity.pickProjectParentFolder()` (WP5), opening at `primary:Documents`;
  - UiAutomator presses "Use this folder" and then "Allow", by resource id with a case-insensitive text fallback. If neither is found, it fails naming the missing control;
  - `workspace.create(parent, spec)` makes `TypewrightDeviceTest-<ts>/`;
  - approve, unlock, edit, kerning, pin, reflection;
  - `scenario.recreate()`: assert the same session instance and `canUndo`;
  - `close()`, then record the file hashes and the location in `noBackupFilesDir/device-test/p11.json`.
- **`ProjectRestoreDeviceTest`,** in a fresh process:
  - the persisted grant is present;
  - launching `MainActivity` reopens the last project;
  - state and file hashes match the record;
  - no `.tmp` or `.new` files;
  - `canUndo == false` (history is documented as not kept);
  - it deletes the test folder.
- **`tools/device/p11-save-kill-restore.sh`:** installs both APKs, runs the save class, runs `adb shell am force-stop com.asoc.typewright`, then runs the restore class.

**The thinnest non-visual wiring (possible without inventing a look):**
- **Command palette entries.** These reuse the existing `CommandPalette` component and its existing result line:

  | Entry | Description |
  |---|---|
  | New project… | "choose a folder; creates Untitled/ inside" (S02 semantics; name clash gives `Untitled 2`) |
  | Open project… | |
  | Save now | |
  | Close project | |
  | Download project (.zip) | web in-memory mode only |
  | Open project (.zip)… | web in-memory mode only |

  A blank new project is `UfoFontInfo(familyName = name, styleName = "Regular", unitsPerEm = 1000)` and nothing invented.
- **Header text.** The existing header mono slot (§2.5 "glyph, counts, file") shows `<name> · saved/saving/in this tab only`.
- **Launch:** reopen the last project.
- **Scrapbook and Workbook:** persist through the session (§10).
- **Lifecycle flushes:** Android `onStop`, desktop close, web visibility and `beforeunload`.
- **Draw and Space still show sample data** until P12 swaps in the open project and P17 routes edits. This is stated in the header status.
- **Android: no stopgap without a hardware keyboard.** The palette is only reachable with Ctrl K, and adding a button would invent a look (law 6). A phone user gets projects with S01 on the P13 deck. The instrumented test drives the picker programmatically.

**UI half (after P13 and the `ui/v1-screens/` export, OQ 123):**
- **S01:** Recent · All; rows with the specimen strip in the project's own font; the empty, missing-folder and Android-permission states; the long-press list: Reveal · Duplicate · Remove from list · Delete from disk.
- **S02:** three steps, class chips with corpus n, metrics prefilled from qa:corpus medians, the name-clash and not-writable states.
- **S06:** the fields, the name-table preview (IDs 1/2/4/6/16/17), numeric entry, Reset to class medians.
- J5 resume.
- a real image picker for "+ photo";
- a designed note composer (OQ 75's `NoteDraftRow`);
- S19's ⋯ "Reset task";
- S21's default projects folder;
- the web in-memory banner.

## 14. Risks and open decisions for Madhav

| # | Decision or risk | Recommendation |
|---|---|---|
| D1 | Are glyphs in an imported font *approved*, so locked on import? (P12) | **Yes.** It's the user's drawing (law 1); J2's "Tidy T" becomes Unlock → Tidy with a stored diff. |
| D2 | Lock scope: are sidebearings, advance, kerning, unicodes and rename allowed on locked glyphs? | **Yes.** Otherwise Space needs every glyph unlocked. Shape is compared modulo horizontal translation. |
| D3 | What confirms a judgment-call Workbook task (OQ 91)? | Saving its reflection. "Reset task" undoes it. |
| D4 | Keep navigation (section, glyph) in app config rather than the project? | **Yes.** It avoids git churn and keeps no-change saves byte-identical. A copied project opens on Home. |
| D5 | Keep undo history across reopen? | **No** (§6). |
| D6 | Web without FSA: tab-only memory. Add an OPFS crash mirror? | Not in P11 (the prompt says in-memory); consider it for v1.1. |
| D7 | P16 will need `capture/` (sheets, cells, trace parameters), which isn't in brief §11's list | Allow it as a versioned extension. |
| D8 | Moving the scrapbook model from `ui` (FSL) to `:project` (Apache) relicenses about 60 lines | Approve. You're the sole licensor (LICENSING §1.1). |
| D9 | Desktop picker runs `zenity`/`kdialog` as a subprocess (not linked), then falls back to Swing | Approve. FileKit (MIT) is the alternative dependency. |
| D10 | A locked glyph edited outside Typewright is auto-unlocked with an "external-edit" diff, never reverted | Approve. |
| D11 | UFO name `HyleDeco-Regular.ufo` (spaces removed) versus S02's literal `<name>-Regular.ufo` | Remove spaces (Google Fonts `sources/` convention). |
| D12 | `build/.gitignore` written at creation | Approve. |
| D13 | Lessons as JSON, not Markdown | JSON (law 7's "UFO 3 and JSON"). |
| D14 | SAF folders whose storage app can't rename are refused | Approve. The alternative is an unsafe write-in-place. |
| R1 | Mockups missing (OQ 123) | The UI half can't start. The palette stopgap is desktop and web only. |
| R2 | First save of a large import through SAF (hundreds of `createDocument` calls) | Show progress; measure on the owner's device. |
| R3 | Float formatting (guidelines, kerning reals, JSON doubles) may differ between JVM and Wasm | Codec tests assert the same bytes on both targets; if they differ, add a shortest-representation formatter. |
| R4 | Foreign UFO data on **edited** glyphs is dropped: point names, glif `<lib>`/`<note>`/`<image>`. Rewriting `layercontents.plist` orphans other layers | P12's import audit must list it. P17 must extend the codec before layers are saved (`public.background`, `images/`). |
| R5 | `countCubic` counts degenerate line controls as off-curve (OQ 20) | P16's line kind would simplify §9.2's glyph key. |
| R6 | `kotlin.time.Clock`/`Instant` may still need `@OptIn(ExperimentalTime)` on 2.4.20 | Check at build. |
| R7 | The `fonttools==4.66.0` pin must exist | WP4 verifies it. |
| R8 | Android: a kill within milliseconds of `onStop` can lose the last save | Stated in §8.6. |

---

## Implementation plan

**WP0 (the lead, one commit before fanning out; done in the commit that added this document, except the androidx test libraries, which WP3 adds with the versions it verifies).** This commit covers:
- `settings.gradle.kts`: add `:project` to the pure block and `:project:storage` to the Android block.
- `project/build.gradle.kts`: pure plugin plus serialization; jvmTest system properties `typewright.repoRoot` and `typewright.ufoValidationDir`.
- `project/storage/build.gradle.kts`: platform plugin; wasmJs browser tests on through Karma, as in `ui`.
- A README and `LICENSE` pointer in each new module.
- `gradle/libs.versions.toml`: `kotlinx-coroutines-test` 1.9.0, `androidx-test-runner`, `androidx-test-rules`, `androidx-test-ext-junit`, `androidx-test-uiautomator`, `androidx-test-orchestrator`.
- `THIRD_PARTY.md`: entries for those libraries, and for fontTools as CI-only (MIT).
- `tools/check_licences.py`: `"project": APACHE`.
- `golden-path/build.gradle.kts`: `testImplementation(project(":project"))`.
- `app-android/build.gradle.kts`: `testInstrumentationRunner` and the androidTest dependencies.

**Merge order:** WP1 → WP2 → WP3 → WP4 → WP5. WP2 starts on its parts that don't need WP1's API (storage, zip, diff, the scrapbook model) while WP1 runs. WP3, WP4 and WP5 code against the §4 signatures. They can compile only once WP2 is merged, so their executors rebase on it before finishing.

| WP | Scope | Files (exclusive to this WP) | Tests |
|---|---|---|---|
| **WP1 UFO fidelity** (Sonnet executes; Opus reviews) | §9: `Glyph.unicodes`; glif `<unicode>`, `qcurve` read and write, `offcurve` fix, line-format keys, glif `<lib>` for the one key; `UfoLib` with `public.glyphOrder` and passthrough; the S06 `UfoFontInfo` fields plus `other`; `fileNameHints`; the bridge fills unicodes and sets QUADRATIC | `core-geometry/.../Glyph.kt`, `GlyphTest.kt`; `core-font/.../ufo/GlifCodec.kt`, `UfoProject.kt`, new `UfoLib.kt`; `core-font/.../sfnt/SfntFontUfoBridge.kt`; `core-font/src/commonTest/.../ufo/{GlifCodecTest,UfoProjectTest}.kt`, `sfnt/SfntFontUfoBridgeTest.kt`, `jvmTest/.../UfoProjectDiskWriteTest.kt`; `core-font/README.md` | `qcurve` round trip incl. the all-off contour and an off-curve start; all-line glyph round trip in cubic, quadratic and mixed projects (the key only where needed); Hyle Deco full round trip `read(write(p)) == p` and byte-stable; `<unicode>` order, dedupe, `%04X`; `type="offcurve"`; glyph order through `public.glyphOrder`; file-name hints stable; the fontinfo `other` passthrough. Replace `rejectsAQCurvePoint` and `rejectsWritingAQuadraticContour` |
| **WP2 `:project` engine** (Sonnet executes; Opus reviews) | §3–§8 and §10's data side: model, `ProjectCodec` (JSON with unknown-key passthrough, phases, encode cache), `UnifiedDiff`, locks by effect, history and coalescing, `Gesture`, `ProjectSession`, autosave/flush/conflict/`rewriteAll`, `SwapProtocolStore`, `InMemoryProjectStore`, `FileSystemProjectStore` + `FileAppConfigStore` (jvmMain), `ProjectWorkspace` and recents, `ProjectZip` (container, CRC32), the scrapbook model moved in, reflection pins | `project/src/commonMain/**`, `project/src/jvmMain/**`, `project/src/commonTest/**`, `project/src/jvmTest/**`, **except** `UfoLibValidationSamplesTest.kt` | Codec round trip and determinism (commonTest on jvm and wasm); a lock matrix (every command against LOCKED gives `Refused`, shape equal modulo translation, sidebearing on a locked glyph shifts the snapshot, external edit gives an episode); diff generation plus reverse-apply; history (coalescing window, gesture commit and cancel, limit 500, undo-all identity); economy updated synchronously; autosave with virtual time (debounce, cap, immediate on locks, gesture defers); crash injection at every write index for `SwapProtocolStore` and `FileSystemProjectStore` (wholly old or new, no `.tmp`/`.new`); conflict detection; owned-set safety (a foreign `README.md` survives); zip round trip and zip-slip; `OpenResult` cases |
| **WP3 platform storage + device tests** (Sonnet; Opus reviews SAF) | §5: `SafDocumentOps` + `AndroidStorageProvider` + `AndroidFolderPicker` (`ActivityResultRegistry`); desktop `DesktopStorageProvider`, XDG dirs, `DesktopFolderPicker` (zenity/kdialog/Swing); web `FsaProjectStore`, IndexedDB handle registry, `LocalStorageAppConfigStore`, deflate/inflate streams, `WebFolderPicker`, Web Locks; the Android instrumented tests and owner script | `project/storage/src/{androidMain,desktopMain,wasmJsMain,commonMain,wasmJsTest,desktopTest}/**`; `app-android/src/androidTest/**`; `tools/device/p11-save-kill-restore.sh` | wasmJsBrowserTest: `FsaProjectStore` against OPFS (write, list, delete, `.crswap` sweep, zip through the streams); desktopTest: XDG path resolution, provider `Missing`; android host tests of pure helpers only; device tests: `ProjectSaveDeviceTest`, `ProjectRestoreDeviceTest` (owner-run) |
| **WP4 golden path + validation** (Sonnet) | §11 and §12: steps 3 and 7; `passing-steps.txt` += `3`, `7`; step 1a message; README; the samples writer; the Python validator; CI | `golden-path/src/test/**` (`GoldenPathSteps.kt`, new `ProjectFixtures.kt`), `golden-path/passing-steps.txt`, `golden-path/README.md`, `project/src/jvmTest/.../UfoLibValidationSamplesTest.kt`, `tools/validate_ufo.py`, `tools/requirements-ufolib.txt`, `.github/workflows/ci.yml` | `./gradlew :golden-path:goldenPath :golden-path:goldenPathRatchet` shows 3 and 7 passing; `python3 tools/validate_ufo.py project/build/ufo-validation` exits 0 locally and in the `core` job |
| **WP5 wiring** (Sonnet) | §10 and §13's stopgap: scrapbook imports and ScrapbookTab/Workbook persistence; `campaignProgress(confirmedTasks)`, confirmability, `ShipMetadata` designer; palette entries; header status; the workspace holder passed into `TypewrightApp`; lifecycle flushes; compile typealias | `ui/src/commonMain/.../learn/{ScrapbookTab.kt, ScrapbookManifest.kt (delete; SampleScrapbook moves to SampleScrapbook.kt)}`, `ui/.../workbook/{WorkbookScreen.kt, WorkbookCampaignSnapshot.kt}`, `ui/.../glass/{CommandPalette.kt, Header.kt}`, `ui/.../TypewrightApp.kt`, new `ui/.../project/ProjectUi.kt`; `ui/src/commonTest/.../learn/ScrapbookManifestTest.kt` (delete; it moves in WP2); `campaign/.../WorkbookProgress.kt`, `WorkbookProgressTest.kt`; `compile/.../CompileRequest.kt`, `compile/build.gradle.kts`; `app-android/.../MainActivity.kt`; `app-desktop/.../Main.kt`; `app-web/.../Main.kt` | campaign: confirmed judgment and INFO-only tasks read DONE, task 11 never does; ui desktopTest: the palette entries create and open a project in a temp dir through a fake picker; the Scrapbook tab shows persisted pins after a reopen; existing screenshot tests are unchanged with no project open; compile tests unchanged (the typealias compiles) |

**After the merges:**
- The lead updates `docs/OPEN_QUESTIONS.md`: close 75, 91, 124 and 125, and close 93's reflection and designer-field parts.
- Run `./gradlew :golden-path:goldenPath` and paste the summary. Expect 2 of 7 steps (3 and 7).

### Files to start from
- /home/user/Typewright/core-font/src/commonMain/kotlin/com/asoc/typewright/core/font/ufo/GlifCodec.kt
- /home/user/Typewright/core-font/src/commonMain/kotlin/com/asoc/typewright/core/font/ufo/UfoProject.kt
- /home/user/Typewright/core-geometry/src/commonMain/kotlin/com/asoc/typewright/core/geometry/Glyph.kt
- /home/user/Typewright/golden-path/src/test/kotlin/com/asoc/typewright/goldenpath/GoldenPathSteps.kt
- /home/user/Typewright/compile/src/commonMain/kotlin/com/asoc/typewright/compile/CompileRequest.kt