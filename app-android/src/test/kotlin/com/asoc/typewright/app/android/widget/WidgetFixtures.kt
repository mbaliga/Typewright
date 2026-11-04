// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.FontState
import com.asoc.typewright.project.GlyphLock
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.Master
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectManifest
import com.asoc.typewright.project.ProjectPreferences
import com.asoc.typewright.project.ProjectSummary
import com.asoc.typewright.project.RecentProject
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.project.WorkbookRecord
import kotlin.time.Instant

/** Small, hand-built projects for the widget tests; nothing here stands in for a user's font in the app. */
object WidgetFixtures {
    val EPOCH: Instant = Instant.fromEpochMilliseconds(1_790_000_000_000)

    fun glyph(name: String): Glyph = Glyph(name = name, advanceWidth = 500, contours = emptyList())

    fun ufo(names: List<String>): UfoProject =
        UfoProject(
            fontInfo = UfoFontInfo(familyName = "Fixture", styleName = "Regular", unitsPerEm = 1000),
            glyphs = names.map(::glyph),
        )

    /** One default master over [names], with [locked] and [unlocked] glyphs carrying locks in those states. */
    fun font(
        names: List<String>,
        locked: Set<String> = emptySet(),
        unlocked: Set<String> = emptySet(),
        path: String = "Fixture-Regular.ufo",
    ): FontState {
        val master = Master(id = "regular", path = path, styleName = "Regular", isDefault = true, ufo = ufo(names))

        fun lock(
            name: String,
            state: LockState,
        ) = GlyphRef(master.id, name) to GlyphLock(state, ApprovalOrigin.DRAW, EPOCH, glyph(name), emptyList())
        val locks = locked.map { lock(it, LockState.LOCKED) } + unlocked.map { lock(it, LockState.UNLOCKED) }
        return FontState(listOf(master), locks.toMap())
    }

    fun manifest(
        name: String = "Fixture Project",
        styleClass: StyleClass = StyleClass(declared = null, confirmed = null),
        confirmedTasks: Set<Int> = emptySet(),
    ): ProjectManifest =
        ProjectManifest(
            name = name,
            created = EPOCH,
            brief = Brief(source = BriefSource.BLANK, styleClass = styleClass),
            scripts = listOf("Latn"),
            workbook = if (confirmedTasks.isEmpty()) emptyMap() else mapOf("latn" to WorkbookRecord(confirmedTasks)),
            comparisonFonts = emptyList(),
            preferences = ProjectPreferences(),
        )

    fun recent(
        name: String,
        openedAtMillis: Long,
        glyphs: Int? = 338,
        location: ProjectLocation = ProjectLocation.SafTree("content://tree/$name", "primary:Fonts/$name"),
    ): RecentProject =
        RecentProject(
            location = location,
            name = name,
            lastOpened = Instant.fromEpochMilliseconds(openedAtMillis),
            lastEdited = Instant.fromEpochMilliseconds(openedAtMillis),
            resume = null,
            summary = glyphs?.let { ProjectSummary(it, outliers = 0, styleClass = null) },
        )

    fun summary(
        states: String = "DDDCTTTTTTTT",
        checks: Map<CheckState, Int> = mapOf(CheckState.FAIL to 3, CheckState.PASS to 1),
    ): WorkbookSummary =
        WorkbookSummary(
            states = states.map { code -> TaskState.entries.single { it.code == code.toString() } },
            currentIndex = states.indexOf('C') + 1,
            currentTitle = "Control characters",
            currentGateSummary = "node economy against the box",
            currentChecks = checks,
            currentScaffold = true,
        )

    fun snapshot(
        project: OpenProject?,
        writer: String = "writer-a",
        writtenAtMillis: Long = 1_790_000_100_000,
    ): ProjectWidgetSnapshot = ProjectWidgetSnapshot(writtenAtMillis, writer, project)

    fun openProject(
        glyphs: Int = 338,
        approved: Int = 52,
        unlocked: Int = 3,
        save: SaveState = SaveState.SAVED,
        savedAtMillis: Long? = 1_790_000_050_000,
        workbook: WorkbookOutcome = WorkbookOutcome.Ready(summary()),
    ): OpenProject = OpenProject("Hyle Deco", glyphs, approved, unlocked, save, savedAtMillis, workbook)
}
