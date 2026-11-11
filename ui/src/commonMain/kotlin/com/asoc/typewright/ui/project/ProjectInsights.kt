// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.brief.genreByKey
import com.asoc.typewright.brief.readAs
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.GlyphCount
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.GlyphLock
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.ProjectManifest
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.qa.GlyphNodeEconomyResult
import com.asoc.typewright.qa.NodeEconomyReport
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.loadStyleAtlas
import com.asoc.typewright.qa.corpus.style.STYLE_MEASUREMENT_GLYPHS
import com.asoc.typewright.qa.corpus.style.measureStyle
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.StationStatus

/** Where the project's style class came from, which decides how the app speaks about it. */
enum class StyleOrigin {
    /** The person confirmed it. */
    CONFIRMED,

    /** The person declared it in the brief. */
    DECLARED,

    /**
     * The app read it off the project's letters. This is our heuristic: read left one out over the 242 faces of the atlas it puts a
     * face's own class first 52% of the time and in its top three 83% of the time (`ReadingAccuracyTest`), so it is a reading, never a fact.
     */
    DETECTED,
}

/**
 * The style class a project's letters are judged against, and where it came from. There is one
 * function that answers this ([projectStyle]) and every screen that needs it (Home, the deck's
 * stations, the Glyphs grid, the Workbook gates) reads that one answer, so they cannot disagree.
 */
data class ProjectStyle(
    val key: String,
    val origin: StyleOrigin,
) {
    /** The class as a person names it (`Geometric sans`). */
    val name: String get() = genreByKey(key)?.name ?: key

    /** Whether the class is a reading the app made rather than something the person said. */
    val isHeuristic: Boolean get() = origin == StyleOrigin.DETECTED

    /** One short phrase for where the class came from. */
    val provenance: String
        get() =
            when (origin) {
                StyleOrigin.CONFIRMED -> "confirmed by you"
                StyleOrigin.DECLARED -> "declared by you"
                StyleOrigin.DETECTED -> "read from your letters by our heuristic"
            }
}

/**
 * The data packs the judgments come from, loaded once. Either is null on a target that cannot read
 * its pack (the browser build cannot yet), and every caller then says what it could not measure
 * instead of showing a number.
 */
object ProjectDataPacks {
    /** The Latin node-economy pack (`data/node-economy-latin.json`). */
    val corpus: NodeEconomyCorpus? by lazy { runCatching { NodeEconomyCorpus.load() }.getOrNull() }

    /** The style atlas (`data/style-atlas-latin.json`), the population the detector reads against. */
    val atlas: StyleAtlas? by lazy { runCatching { loadStyleAtlas() }.getOrNull() }
}

/**
 * Remembers the last answer to a question about the project, so a screen asking it again for the
 * same glyphs does not repeat the work. The keys and the answer are one immutable entry replaced in a
 * single write, so a caller on another thread (the deck's Trace status is worked out off the UI
 * thread) sees either the old answer with its keys or the new one, never a mixture; at worst two
 * threads both compute.
 */
private class LastAnswer<V> {
    private class Entry<V>(
        val keys: List<Any?>,
        val value: V,
    )

    private var last: Entry<V>? = null

    fun get(
        vararg newKeys: Any?,
        compute: () -> V,
    ): V {
        val entry = last
        if (entry != null && entry.keys.size == newKeys.size &&
            entry.keys.indices.all { entry.keys[it] === newKeys[it] || (entry.keys[it] is String && entry.keys[it] == newKeys[it]) }
        ) {
            return entry.value
        }
        val value = compute()
        last = Entry(newKeys.toList(), value)
        return value
    }
}

private val detection = LastAnswer<String?>()
private val economyAnswer = LastAnswer<ProjectEconomy?>()
private val countsAnswer = LastAnswer<ProjectCounts>()

/**
 * The style class [font] is judged against: the one the person confirmed, else the one they
 * declared, else the class the detector reads off the project's own letters (a heuristic, so
 * [ProjectStyle.isHeuristic] is true and every screen that shows it says "our heuristic"). Null when
 * nothing is declared and the detector cannot read: no atlas on this target, or too few letters
 * drawn to measure.
 */
fun projectStyle(
    manifest: ProjectManifest,
    font: ProjectFont,
    atlas: StyleAtlas? = ProjectDataPacks.atlas,
): ProjectStyle? {
    val brief = manifest.brief.styleClass
    brief.confirmed?.let { return ProjectStyle(it, StyleOrigin.CONFIRMED) }
    brief.declared?.let { return ProjectStyle(it, StyleOrigin.DECLARED) }
    if (atlas == null) return null
    val key = detectStyleKey(font, atlas) ?: return null
    return ProjectStyle(key, StyleOrigin.DETECTED)
}

/** The corpus class [readAs] ranks first for [font]'s letters, or null when too little was measured. */
private fun detectStyleKey(
    font: ProjectFont,
    atlas: StyleAtlas,
): String? {
    val letters = STYLE_MEASUREMENT_GLYPHS.map { font.glyphFor(it) }
    return detection.get(atlas, font.unitsPerEm.toString(), *letters.toTypedArray()) {
        val present =
            STYLE_MEASUREMENT_GLYPHS
                .toList()
                .zip(letters)
                .mapNotNull { (char, glyph) -> glyph?.let { char to it } }
                .toMap()
        if (present.isEmpty()) {
            null
        } else {
            runCatching { readAs(measureStyle(present, font.unitsPerEm), atlas) }
                .getOrNull()
                ?.genres
                ?.firstOrNull { it.genre.corpus }
                ?.genre
                ?.key
        }
    }
}

/**
 * The node-economy judgment of a project's glyphs against [style]'s box in the corpus (brief §8):
 * how many are outliers, above the box or in range, and each glyph's own verdict. The fence's
 * `0.25 · Q3` minimum is our heuristic and the screens that name the fence say so.
 */
class ProjectEconomy(
    val style: ProjectStyle,
    val report: NodeEconomyReport,
    /** How many corpus faces the class is built from (the "30" in "against 30 geometric sans"). */
    val classFaces: Int,
) {
    private val byGlyph: Map<String, GlyphNodeEconomyResult> = report.glyphs.associateBy { it.glyphName }

    /** Glyphs that have a box to be judged against (the project's letters the corpus covers). */
    val judged: Int = report.glyphs.count { it.onCurveVerdict != null }
    val outliers: Int = report.glyphs.count { it.onCurveVerdict == NodeEconomyVerdict.OUTLIER }
    val above: Int = report.glyphs.count { it.onCurveVerdict == NodeEconomyVerdict.ABOVE }
    val inRange: Int = report.glyphs.count { it.onCurveVerdict == NodeEconomyVerdict.IN_RANGE }

    /** [glyphName]'s on-curve verdict, or null where the corpus has no box for it. */
    fun verdict(glyphName: String): NodeEconomyVerdict? = byGlyph[glyphName]?.onCurveVerdict

    /** [glyphName]'s whole result (its counts and the box it was judged against), or null for a glyph the check did not see. */
    fun result(glyphName: String): GlyphNodeEconomyResult? = byGlyph[glyphName]
}

/** Judges [font] against its style class, or null when there is no class to judge against or this target cannot read the corpus. */
fun projectEconomy(
    manifest: ProjectManifest,
    font: ProjectFont,
    corpus: NodeEconomyCorpus? = ProjectDataPacks.corpus,
    atlas: StyleAtlas? = ProjectDataPacks.atlas,
): ProjectEconomy? {
    if (corpus == null) return null
    val style = projectStyle(manifest, font, atlas) ?: return null
    if (style.key !in corpus.styleKeys) return null
    return economyAnswer.get(corpus, font.ufo.glyphs, style.key, style.origin.name) {
        ProjectEconomy(style, checkNodeEconomy(font.ufo, style.key, corpus), corpus.families(style.key).size)
    }
}

/** The totals Home's counts line shows. */
data class ProjectCounts(
    val glyphs: Int,
    /** On-curve points, counting the points a quadratic curve leaves implied (the measure the corpus uses). */
    val points: Int,
    /** Curved segments, not counting the straight lines a cubic contour spells with two control points. */
    val curves: Int,
)

/** Counts [font]'s glyphs, points and curved segments. */
fun projectCounts(font: ProjectFont): ProjectCounts =
    countsAnswer.get(font.ufo.glyphs) {
        var points = 0
        var curves = 0
        for (glyph in font.glyphs) {
            points += glyph.count().onCurveEquivalent
            for (contour in glyph.contours) {
                curves +=
                    contour.segments().count { segment ->
                        when (segment) {
                            is CurveSegment.Line -> false
                            is CurveSegment.Quadratic -> true
                            is CurveSegment.Cubic -> segment.control1 != segment.start || segment.control2 != segment.end
                        }
                    }
            }
        }
        ProjectCounts(font.glyphs.size, points, curves)
    }

// ---- The Glyphs grid ----------------------------------------------------------------------------

/** One cell of the Glyphs grid. */
data class GlyphCell(
    val glyph: Glyph,
    val count: GlyphCount,
    /** The on-curve verdict, or null where the corpus has no box for this glyph (or no class to judge by). */
    val verdict: NodeEconomyVerdict?,
    val locked: Boolean,
    /** False for a glyph with no outline that is not meant to be empty (a space is). */
    val drawn: Boolean,
)

/** The grid's filter chips. */
enum class GlyphFilter(
    val label: String,
) {
    ALL("All"),
    OUTLIERS("●"),
    ABOVE("◐"),
    IN_RANGE("✓"),
    LOCKED("Locked"),
    NOT_DRAWN("Not drawn"),
}

/** The cells for every glyph of [font], judged by [economy] where there is one, locked where [locks] says. */
fun glyphCells(
    font: ProjectFont,
    economy: ProjectEconomy?,
    locks: Map<GlyphRef, GlyphLock>,
): List<GlyphCell> =
    font.glyphs.map { glyph ->
        GlyphCell(
            glyph = glyph,
            count = glyph.count(),
            verdict = economy?.verdict(glyph.name),
            locked = locks[GlyphRef(font.master.id, glyph.name)]?.state == LockState.LOCKED,
            drawn = glyph.contours.isNotEmpty() || isMeantToBeEmpty(glyph),
        )
    }

/** Whether [glyph] is empty on purpose: a space, a joiner, a mark of no ink. */
fun isMeantToBeEmpty(glyph: Glyph): Boolean {
    val name = glyph.name.lowercase()
    if (name in EMPTY_BY_NAME || (name == ".notdef" && glyph.unicodes.isEmpty())) return true
    val cp = glyph.unicodes.firstOrNull() ?: return false
    return cp in EMPTY_CODE_POINTS || EMPTY_RANGES.any { cp in it }
}

private val EMPTY_BY_NAME = setOf("space", ".null", "nonmarkingreturn", "cr")

/** Space, no-break space, soft hyphen, ideographic space and the zero-width no-break space. */
private val EMPTY_CODE_POINTS = setOf(0x20, 0xA0, 0xAD, 0x3000, 0xFEFF)

/** The control characters, the general punctuation spaces and joiners, the line and paragraph separators, and the invisible operators. */
private val EMPTY_RANGES = listOf(0x0..0x1F, 0x2000..0x200F, 0x2028..0x202F, 0x205F..0x206F)

/** [cells] that pass [filter], then those whose name or character contains [query] (ignoring case). */
fun filterCells(
    cells: List<GlyphCell>,
    filter: GlyphFilter,
    query: String,
): List<GlyphCell> {
    val passing =
        cells.filter { cell ->
            when (filter) {
                GlyphFilter.ALL -> true
                GlyphFilter.OUTLIERS -> cell.verdict == NodeEconomyVerdict.OUTLIER
                GlyphFilter.ABOVE -> cell.verdict == NodeEconomyVerdict.ABOVE
                GlyphFilter.IN_RANGE -> cell.verdict == NodeEconomyVerdict.IN_RANGE
                GlyphFilter.LOCKED -> cell.locked
                GlyphFilter.NOT_DRAWN -> !cell.drawn
            }
        }
    val needle = query.trim()
    if (needle.isEmpty()) return passing
    return passing.filter { cell ->
        cell.glyph.name.contains(needle, ignoreCase = true) ||
            cell.glyph.unicodes.any { cp -> cp in 0..0xFFFF && cp.toChar().toString() == needle }
    }
}

/** How many of [cells] each chip would show. */
fun filterCounts(cells: List<GlyphCell>): Map<GlyphFilter, Int> = GlyphFilter.entries.associateWith { filterCells(cells, it, "").size }

/** The glyphs with the most points, most first: Home's "worst first" strip. */
fun heaviestGlyphs(
    cells: List<GlyphCell>,
    limit: Int = 5,
): List<GlyphCell> = cells.filter { it.glyph.contours.isNotEmpty() }.sortedByDescending { it.count.onCurveEquivalent }.take(limit)

// ---- The deck's stations ------------------------------------------------------------------------

/** How far the person's own looking-over of the glyphs has got. */
enum class DrawProgress {
    NOT_STARTED,
    PARTIAL,
    DONE,
}

/**
 * Draw's progress, by the one rule the project can truthfully back: a glyph has been looked at when
 * the person approved it themselves (the approval's origin is a trace accepted or Draw's approve),
 * not when the import approved it. Only glyphs that have an outline count, so an empty space does
 * not hold the station back. A project with nothing drawn yet is [DrawProgress.NOT_STARTED].
 */
fun drawProgress(
    font: ProjectFont,
    locks: Map<GlyphRef, GlyphLock>,
): DrawProgress {
    val drawn = font.glyphs.filter { it.contours.isNotEmpty() }
    if (drawn.isEmpty()) return DrawProgress.NOT_STARTED
    val looked =
        drawn.count { glyph ->
            locks[GlyphRef(font.master.id, glyph.name)]?.origin.let {
                it == ApprovalOrigin.TRACE ||
                    it == ApprovalOrigin.DRAW
            }
        }
    return when (looked) {
        0 -> DrawProgress.NOT_STARTED
        drawn.size -> DrawProgress.DONE
        else -> DrawProgress.PARTIAL
    }
}

/**
 * The status shape [section]'s label carries on the deck's section arc, for what the project can
 * truthfully say, else null (no shape rather than an invented one):
 * - Draw: ○ until the person has approved a glyph, ✓ when they have approved every drawn glyph, and
 *   no shape in between. An import approves every glyph, but that is not the person looking.
 * - Trace: ● when some glyphs are node-economy outliers against the project's class (the glyphs to
 *   refit), ✓ when none are. No shape without a class to judge by, or where this build cannot read
 *   the corpus.
 * - Every other station: null. Capture, Check, Ship and Space record nothing the project can read
 *   yet, and Home, Learn and Workbook are not stations.
 */
fun stationStatusFor(
    section: DeckSection,
    state: ProjectState,
    corpus: NodeEconomyCorpus? = ProjectDataPacks.corpus,
    atlas: StyleAtlas? = ProjectDataPacks.atlas,
): StationStatus? {
    if (section != DeckSection.DRAW && section != DeckSection.TRACE) return null
    val master = state.font.masters.firstOrNull { it.isDefault } ?: state.font.masters.firstOrNull() ?: return null
    val font = ProjectFont.of(master)
    return when (section) {
        DeckSection.DRAW -> {
            when (drawProgress(font, state.font.locks)) {
                DrawProgress.NOT_STARTED -> StationStatus.NOT_STARTED
                DrawProgress.DONE -> StationStatus.DONE
                DrawProgress.PARTIAL -> null
            }
        }

        else -> {
            projectEconomy(state.meta.manifest, font, corpus, atlas)?.let { economy ->
                if (economy.outliers > 0) StationStatus.TO_REFIT else StationStatus.DONE
            }
        }
    }
}

/** [stationStatusFor] over the session's current state: the function the router hands the deck as `SectionNav.statusOf`. */
fun projectStationStatus(session: ProjectSession): (DeckSection) -> StationStatus? =
    { section -> stationStatusFor(section, session.state.value) }
