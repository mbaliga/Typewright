// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/** A glyph inside one master: `masters[master].ufo.glyphs` matching [glyph] by name. */
data class GlyphRef(
    val master: String,
    val glyph: String,
)

/** One point of a glyph's outline, as an index into `Contour.points` (docs/PROJECT_MODEL.md §4). */
data class PointRef(
    val contour: Int,
    val point: Int,
)

/** The whole open project, at one instant: the font ([font], undo/redo history's subject) and everything else ([meta]). */
data class ProjectState(
    val font: FontState,
    val meta: ProjectMeta,
)

/**
 * Every master's font data plus the law-1 lock table, keyed by [GlyphRef]. This is the part
 * [ProjectSession]'s undo/redo history snapshots (docs/PROJECT_MODEL.md §6); [ProjectMeta] is not.
 */
data class FontState(
    val masters: List<Master>,
    val locks: Map<GlyphRef, GlyphLock>,
) {
    /** The master with id [id], or null. */
    fun master(id: String): Master? = masters.find { it.id == id }

    /** The glyph [ref] names, or null when its master or glyph doesn't exist. */
    fun glyph(ref: GlyphRef): Glyph? = master(ref.master)?.ufo?.glyphs?.find { it.name == ref.glyph }
}

/** One UFO master: [id] is the stable key [GlyphRef] and `typewright.json`'s `locks` use; [path] is its `.ufo` directory name. */
data class Master(
    val id: String,
    val path: String,
    val styleName: String,
    val isDefault: Boolean,
    val ufo: UfoProject,
)

/** Whether a glyph is locked by law 1. */
enum class LockState {
    LOCKED,
    UNLOCKED,
    ;

    companion object
}

/** How a glyph came to be approved: accepting a trace (S10), Draw's Ctrl L, or an import (P12). */
enum class ApprovalOrigin {
    TRACE,
    DRAW,
    IMPORT,
    ;

    companion object
}

/** Why a locked glyph became unlocked: the user asked, or the session found it changed outside Typewright (§7.4). */
enum class UnlockCause {
    USER,
    EXTERNAL_EDIT,
    ;

    companion object
}

/**
 * Law 1's record for one glyph: [state], who approved it and when ([origin], [approvedAt]), the
 * approved outline itself ([approved], snapshotted into `locks/<master>/…`), and every unlock
 * [episodes] (oldest first; docs/PROJECT_MODEL.md §7).
 */
data class GlyphLock(
    val state: LockState,
    val origin: ApprovalOrigin,
    val approvedAt: Instant,
    val approved: Glyph,
    val episodes: List<UnlockEpisode>,
)

/**
 * One span between an unlock and its relock (or, while [relockedAt] is null, the still-open
 * span). [frozenDiff] is the unified diff text frozen in at relock time (§7.3); it is null for an
 * open episode, whose diff is instead derived at save time from the current glyph and [GlyphLock.approved].
 */
data class UnlockEpisode(
    val at: Instant,
    val cause: UnlockCause,
    val reason: String?,
    val relockedAt: Instant?,
    val frozenDiff: String?,
)

/** Everything about the project that undo/redo does not touch: `typewright.json`'s metadata, the scrapbook and the lessons. */
data class ProjectMeta(
    val manifest: ProjectManifest,
    val scrapbook: ScrapbookManifest,
    val lessons: Map<String, WorkbookLessons>,
    val unknownKeys: JsonObject,
)

/** `typewright.json`'s metadata fields (docs/PROJECT_MODEL.md §3.1); `masters` and `locks` live on [FontState] instead. */
data class ProjectManifest(
    val name: String,
    val created: Instant,
    val brief: Brief,
    val scripts: List<String>,
    val workbook: Map<String, WorkbookRecord>,
    val comparisonFonts: List<ComparisonFont>,
    val preferences: ProjectPreferences,
)

/** S02 step 1's starting point for a new project. */
enum class BriefSource {
    PAPER,
    FONT,
    BLANK,
    ;

    companion object
}

/** S02's contrast model chip. */
enum class ContrastModel {
    SERIF,
    SANS,
    ;

    companion object
}

/** S02's intended use chip. */
enum class Use {
    TEXT,
    DISPLAY,
    ;

    companion object
}

/** S02 step 2's Workbook Task 1, done up front. */
data class Brief(
    val source: BriefSource,
    val model: ContrastModel? = null,
    val use: Use? = null,
    val styleClass: StyleClass = StyleClass(declared = null, confirmed = null),
    val note: String? = null,
)

/** `qa:corpus`'s style class key, [declared] from S02's chips and [confirmed] once the user agrees (brief §11). */
data class StyleClass(
    val declared: String?,
    val confirmed: String?,
)

/** One workbook's campaign progress: the judgment-call tasks the user has confirmed (§10.3). */
data class WorkbookRecord(
    val confirmedTasks: Set<Int> = emptySet(),
)

/** Where a comparison font's bytes come from (law 3: fetched or added only on request). */
enum class ComparisonSource {
    BUNDLED,
    GOOGLE_FONTS,
    FILE,
    ;

    companion object
}

/** One Overlay layer or Space's comparison font. */
data class ComparisonFont(
    val family: String,
    val source: ComparisonSource,
    val slug: String? = null,
    val path: String? = null,
)

/** Working preferences that travel with the project rather than the app (§3.1); never navigation state (D4). */
data class ProjectPreferences(
    val spaceControlStrings: List<String> = emptyList(),
    val overlayWord: String? = null,
    val specimenText: String? = null,
)
