// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/**
 * Small, synthetic project fixtures for `project`'s own commonTest suite (no dependency on
 * `golden-path`'s `Seeds`, and no real font bytes): a hand-built square glyph, a tiny two-glyph
 * UFO project, a one-master [FontState] and a full [ProjectState], all in [CurveFormat.CUBIC] so
 * they run identically on the JVM and Wasm.
 */
object Fixtures {
    /** A closed square contour from ([x0], [y0]) to ([x1], [y1]), written as four degenerate cubic "line" segments. */
    fun squareContour(
        x0: Int = 50,
        y0: Int = 0,
        x1: Int = 450,
        y1: Int = 700,
    ): Contour {
        val corners = listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1))
        val points = mutableListOf<ContourPoint>()
        for (i in corners.indices) {
            val start = corners[i]
            val end = corners[(i + 1) % corners.size]
            points += ContourPoint(start, onCurve = true)
            points += ContourPoint(start, onCurve = false)
            points += ContourPoint(end, onCurve = false)
        }
        return Contour(points, CurveFormat.CUBIC)
    }

    /** A one-contour glyph named [name]; [dx] shifts the whole square right (for building a "same shape, translated" fixture). */
    fun glyph(
        name: String,
        dx: Int = 0,
        advanceWidth: Int = 500,
    ): Glyph = Glyph(name, advanceWidth, listOf(squareContour(50 + dx, 0, 450 + dx, 700)))

    /** A tiny UFO project with glyphs [names], each a fresh, distinctly shaped square (so no two glyphs are accidentally equal). */
    fun ufoProject(names: List<String> = listOf("A", "B", "C")): UfoProject =
        UfoProject(
            fontInfo = UfoFontInfo(familyName = "Fixture", styleName = "Regular", unitsPerEm = 1000),
            glyphs = names.mapIndexed { index, name -> glyph(name, dx = index * 10) },
        )

    /** One master over [ufo]. */
    fun master(
        id: String = "regular",
        styleName: String = "Regular",
        isDefault: Boolean = true,
        ufo: UfoProject = ufoProject(),
    ): Master = Master(id, "Fixture-$styleName.ufo", styleName, isDefault, ufo)

    /** A one-master [FontState], with [locks] empty by default. */
    fun fontState(
        master: Master = master(),
        locks: Map<GlyphRef, GlyphLock> = emptyMap(),
    ): FontState = FontState(listOf(master), locks)

    /** A minimal but complete [ProjectManifest]. */
    fun manifest(
        name: String = "Fixture Project",
        created: Instant = EPOCH,
        workbook: Map<String, WorkbookRecord> = emptyMap(),
    ): ProjectManifest =
        ProjectManifest(
            name = name,
            created = created,
            brief = Brief(source = BriefSource.BLANK, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
            scripts = listOf("Latn"),
            workbook = workbook,
            comparisonFonts = emptyList(),
            preferences = ProjectPreferences(),
        )

    /** A full [ProjectState] over [font]. */
    fun projectState(
        font: FontState = fontState(),
        manifest: ProjectManifest = manifest(),
    ): ProjectState = ProjectState(font, ProjectMeta(manifest, ScrapbookManifest(), emptyMap(), JsonObject(emptyMap())))

    /** `2026-09-25T10:00:00Z`, the golden path's own epoch (PROMPTS_V1 P10 step 6), reused here for a readable fixed clock. */
    val EPOCH: Instant = ProjectTimestamps.parse("2026-09-25T10:00:00Z")

    /** A [SessionEnvironment] wired for deterministic tests: [SteppingClock], a [TestTimeSource] for history coalescing, and [scope] for autosave. */
    fun env(
        scope: CoroutineScope,
        clock: Clock = SteppingClock(EPOCH),
        monotonic: TimeSource.WithComparableMarks = TestTimeSource(),
        autosave: AutosavePolicy = AutosavePolicy(),
        historyLimit: Int = 500,
        coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MS,
    ): SessionEnvironment =
        SessionEnvironment(
            scope = scope,
            io = kotlinx.coroutines.Dispatchers.Unconfined,
            clock = clock,
            monotonic = monotonic,
            autosave = autosave,
            historyLimit = historyLimit,
            coalesceWindowMillis = coalesceWindowMillis,
        )
}

/** A [Clock] that starts at [start] and advances by one second on every call to [now] (deterministic, ordered timestamps). */
class SteppingClock(
    start: Instant,
) : Clock {
    private var current = start

    override fun now(): Instant {
        val at = current
        current += 1.seconds
        return at
    }
}
