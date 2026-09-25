// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.campaign

import dev.aarso.typewright.core.font.sfnt.readSfntFont
import dev.aarso.typewright.core.font.ufo.UfoFontInfo
import dev.aarso.typewright.core.font.ufo.UfoProject
import dev.aarso.typewright.qa.checkNodeEconomy
import dev.aarso.typewright.qa.corpus.NodeEconomyCorpus
import dev.aarso.typewright.qa.corpus.NodeEconomyVerdict
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 4's own real gate, run for real against this project's own real reference font
 * (this task's own instruction: "compute the REAL result by running this function against this
 * build's own reference font, do not hardcode the mockup's numbers as if measured").
 *
 * There is no SFNT-to-[UfoProject] bridge in `core-font` (this task's own note), so this follows
 * this build's own established precedent (`core-geometry`'s `FitPipelineHyleDecoValidationTest`,
 * `ui`'s `HyleDecoProjectFontBytes.kt`): read `fonts/HyleDeco-Regular.ttf` with `core-font`'s real
 * SFNT reader ([readSfntFont]) to get real [dev.aarso.typewright.core.geometry.Glyph] objects --
 * with their real point counts, matching CLAUDE.md's own pinned fixture numbers for `n`/`H` (and
 * `o`, restated in `qa/corpus`'s own `HyleDecoNodeEconomyTest`) -- and build a small real
 * [UfoProject] around them, `n`/`o`/`H`/`O`, for [WorkbookGates.task4ControlCharacters] to run
 * against for real. Opt-in by construction, not by a skip check: `fonts/HyleDeco-Regular.ttf` is
 * this repository's own committed test fixture (unlike the third-party corpus binaries
 * `qa/corpus`'s own `StyleDetectorRealFontValidationTest` fetches at test time), so it is always
 * present and this test always runs.
 *
 * **Real measured result (2026-09-24, this task), independently cross-checked against fontTools
 * 4.62.1 reading the same file directly.** Hyle Deco's shipped `n`/`o`/`H`/`O` are pure polygons
 * (0 off-curve points anywhere, exactly like `T`/`L`, `core-geometry`'s own
 * `FitPipelineHyleDecoValidationTest`'s finding for those two): `n` 44 on-curve, `o` 80 on-curve
 * (2 contours), `H` 1,252 on-curve, `O` 83 on-curve (2 contours) -- `n`/`o`/`H` match CLAUDE.md's
 * own pinned shipped-glyph fixture line exactly; `O` has no CLAUDE.md fixture, so this test only
 * reports it, never asserts an invented target (law 5). Against the real, regenerated
 * `sans-geometric` node-economy box (`data/node-economy-latin.json`, the class Hyle Deco belongs
 * to per `qa/corpus`'s own `HyleDecoNodeEconomyTest`) all four are on-curve **OUTLIER**s -- the
 * shipped font is, honestly, still a full trace, not the "fitted" target the brief's fixture line
 * separately names -- while their off-curve axis (0 in every case) is trivially **IN_RANGE**
 * against every one of these boxes' own Q3, matching `craft.a1-tracing-destroys-the-drawing.yaml`'s
 * own identical finding for `T`'s shipped 0 off-curve.
 */
class HyleDecoTask4GateTest {
    private val project: UfoProject by lazy {
        val bytes = File("../fonts/HyleDeco-Regular.ttf").readBytes()
        val font = readSfntFont(bytes)
        val glyphs = listOf("n", "o", "H", "O").map { name -> font.glyph(name) ?: error("Hyle Deco has no glyph named '$name'") }
        UfoProject(UfoFontInfo(familyName = "Hyle Deco", styleName = "Regular", xHeight = 500), glyphs)
    }

    @Test
    fun realHyleDecoControlCharactersMatchTheShippedFixtureCounts() {
        val byName = project.glyphs.associateBy { it.name }
        // n/o/H match CLAUDE.md's own pinned "shipped" fixture line exactly (44/80/1,252 on-curve, 0 off-curve).
        assertEquals(44, byName.getValue("n").contours.sumOf { c -> c.points.count { it.onCurve } })
        assertEquals(80, byName.getValue("o").contours.sumOf { c -> c.points.count { it.onCurve } })
        assertEquals(1252, byName.getValue("H").contours.sumOf { c -> c.points.count { it.onCurve } })
        // O has no CLAUDE.md fixture; independently cross-checked against fontTools 4.62.1 for this task.
        assertEquals(83, byName.getValue("O").contours.sumOf { c -> c.points.count { it.onCurve } })
        for (glyph in project.glyphs) {
            assertEquals(
                0,
                glyph.contours.sumOf { c ->
                    c.points.count { !it.onCurve }
                },
                "'${glyph.name}' should be a pure polygon, 0 off-curve",
            )
        }
    }

    @Test
    fun realHyleDecoControlCharactersAreAllOnCurveOutliersAgainstTheRealSansGeometricBox() {
        val corpus = NodeEconomyCorpus.load()
        val result = WorkbookGates.task4ControlCharacters(project, corpus, "sans-geometric")

        assertEquals(4, result.taskIndex)
        for (glyphName in listOf("n", "o", "H", "O")) {
            val check = result.checks.single { it.label == glyphName }
            assertEquals(
                GateCheckStatus.FAIL,
                check.status,
                "'$glyphName' should be an on-curve OUTLIER (FAIL) on the real shipped font: ${check.detail}",
            )
        }
    }

    @Test
    fun realHyleDecoOffCurveAxisIsTriviallyInRangeOnEveryControlCharacter() {
        // Off-curve is 0 for all four (pure polygons); 0 is always <= any box's own Q3, so this
        // axis is IN_RANGE, matching craft.a1's own identical finding for T's shipped 0 off-curve.
        val corpus = NodeEconomyCorpus.load()
        val report = checkNodeEconomy(project, "sans-geometric", corpus)
        for (glyphName in listOf("n", "o", "H", "O")) {
            val glyphResult = report.glyphs.single { it.glyphName == glyphName }
            assertEquals(0, glyphResult.offCurve)
            assertEquals(NodeEconomyVerdict.IN_RANGE, glyphResult.offCurveVerdict)
        }
    }
}
