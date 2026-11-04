// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.degreeElevatedToCubic
import com.asoc.typewright.core.geometry.enforceContourDirections
import com.asoc.typewright.project.AutosavePolicy
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.InMemoryProjectStore
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.SessionEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Real projects for Draw's tests: Hyle Deco's own outlines (`fonts/HyleDeco-Regular.ttf`), as the
 * quadratic contours the importer reads, and the same outlines degree-elevated to cubic so the cubic
 * editing paths run on real letters too. Fixtures live in tests only; shipped code never reads them.
 */
object DrawTestProjects {
    private val ttf: ByteArray by lazy { File("../fonts/HyleDeco-Regular.ttf").readBytes() }

    /** Hyle Deco as imported: every glyph quadratic. */
    fun hyleDecoQuadratic(): UfoProject = readSfntFont(ttf).toUfoProject()

    /** Hyle Deco with each glyph's outline exactly degree-elevated to cubic (outer contours counter-clockwise). */
    fun hyleDecoCubic(): UfoProject {
        val quadratic = hyleDecoQuadratic()
        return quadratic.copy(
            glyphs =
                quadratic.glyphs.map {
                    it.copy(
                        contours =
                            enforceContourDirections(
                                it.contours.map { c ->
                                    c.degreeElevatedToCubic()
                                },
                            ),
                    )
                },
            lib = quadratic.lib.copy(lineContourFormat = com.asoc.typewright.core.geometry.CurveFormat.CUBIC),
        )
    }

    /** A small project of just [glyphs], for tests that need a controlled outline rather than a real letter. */
    fun small(vararg glyphs: Glyph): UfoProject =
        UfoProject(
            UfoFontInfo(familyName = "Draw Test", styleName = "Regular", unitsPerEm = 1000, xHeight = 500, capHeight = 700),
            glyphs.toList(),
        )

    /**
     * A session over [ufo], autosave off so a test decides when anything is written. A [coalesceWindowMillis] of 0
     * turns the session's merging of quick repeated edits off, so a test can tell one undo step made by construction
     * from one made by the clock.
     */
    fun session(
        ufo: UfoProject,
        coalesceWindowMillis: Long = DEFAULT_COALESCE_MILLIS,
    ): ProjectSession =
        runBlocking {
            val spec =
                NewProjectSpec(
                    name = "Draw Test",
                    brief = Brief(source = BriefSource.BLANK),
                    scripts = listOf("Latn"),
                    masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = ufo)),
                )
            val env =
                SessionEnvironment(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    io = Dispatchers.Unconfined,
                    autosave = AutosavePolicy(enabled = false),
                    coalesceWindowMillis = coalesceWindowMillis,
                )
            (ProjectSession.create(InMemoryProjectStore("Draw Test"), spec, env) as CreateResult.Created).session
        }

    private const val DEFAULT_COALESCE_MILLIS = 1_000L

    fun editor(
        session: ProjectSession,
        name: String,
    ): DrawEditor = checkNotNull(DrawEditor.forGlyph(session, name)) { "no glyph $name" }
}
