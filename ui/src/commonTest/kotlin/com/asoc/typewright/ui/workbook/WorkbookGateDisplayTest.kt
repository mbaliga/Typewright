// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import com.asoc.typewright.campaign.GateCheckResult
import com.asoc.typewright.campaign.GateCheckStatus
import com.asoc.typewright.campaign.WorkbookGateResult
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.FamilyEntry
import com.asoc.typewright.qa.corpus.GlyphDist
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyPack
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.qa.corpus.StyleClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun rectGlyph(
    name: String,
    x1: Int,
    y1: Int,
): Glyph =
    Glyph(
        name,
        advanceWidth = 500,
        contours =
            listOf(
                Contour(
                    listOf(
                        ContourPoint(Point(0, 0), true),
                        ContourPoint(Point(x1, 0), true),
                        ContourPoint(Point(x1, y1), true),
                        ContourPoint(Point(0, y1), true),
                    ),
                    CurveFormat.QUADRATIC,
                ),
            ),
    )

private fun fixtureCorpus(): NodeEconomyCorpus =
    NodeEconomyCorpus(
        NodeEconomyPack(
            source = "test",
            glyphs = "on",
            styles =
                mapOf(
                    "sans-geometric" to
                        StyleClass(
                            tags = listOf("/Test"),
                            families = listOf(FamilyEntry("Alpha", "alpha.ttf", "quadratic", 90.0, mapOf("n" to listOf(4, 0, 1)))),
                            dist =
                                mapOf(
                                    "n" to
                                        GlyphDist(
                                            on = Quartiles(min = 2.0, q1 = 12.0, med = 13.0, q3 = 14.25, max = 20.0, n = 1),
                                            off = Quartiles(min = 0.0, q1 = 0.0, med = 0.0, q3 = 0.0, max = 0.0, n = 1),
                                        ),
                                ),
                        ),
                ),
        ),
    )

class WorkbookGateDisplayTest {
    @Test
    fun aGlyphKeyedTaskIsMarkedAsGlyphHeroWithNoBoxRange() {
        val gate = WorkbookGateResult(5, "geometric sanity", listOf(GateCheckResult("b", GateCheckStatus.PASS, "no findings")))
        val rows = workbookGateRows(taskIndex = 5, gate = gate, task4Report = null)
        val row = rows.single()
        assertEquals("b", row.heroText)
        assertEquals(true, row.isGlyphHero)
        assertEquals(GateCheckStatus.PASS, row.status)
        assertEquals("PASS", row.valueText)
        assertNull(row.boxText)
    }

    @Test
    fun aNonGlyphKeyedTaskIsMarkedAsNotGlyphHero() {
        val gate =
            WorkbookGateResult(
                1,
                "no automated check yet",
                listOf(GateCheckResult("no automated check yet", GateCheckStatus.NOT_IMPLEMENTED, "a judgment call")),
            )
        val rows = workbookGateRows(taskIndex = 1, gate = gate, task4Report = null)
        val row = rows.single()
        assertEquals(false, row.isGlyphHero)
        assertEquals("NOT IMPLEMENTED", row.valueText)
        assertNull(row.boxText)
    }

    @Test
    fun task4RowsShowTheRealOnCurveCountAndBoxRangeFromTheNodeEconomyReport() {
        val glyph = rectGlyph("n", 6, 6) // a 4-point rectangle -> 4 on-curve points, well below the fixture box (12-14.25).
        val project = UfoProject(UfoFontInfo(familyName = "Fixture"), listOf(glyph))
        val corpus = fixtureCorpus()
        val report = checkNodeEconomy(project, "sans-geometric", corpus)
        val gate =
            WorkbookGateResult(
                4,
                "node economy against the geometric box (sans-geometric)",
                listOf(GateCheckResult("n", GateCheckStatus.WARN, "detail")),
            )

        val rows = workbookGateRows(taskIndex = 4, gate = gate, task4Report = report)

        val row = rows.single()
        assertEquals("4", row.valueText, "the real raw on-curve count (4), not the check's own status word")
        assertEquals("box 12 – 14", row.boxText, "Q1/Q3 rounded to whole numbers, matching #s-workbook's own 'box 12 - 14' style")
    }

    @Test
    fun task4FallsBackToTheStatusWordWhenNoMatchingReportEntryExists() {
        val gate =
            WorkbookGateResult(
                4,
                "node economy",
                listOf(GateCheckResult("missing", GateCheckStatus.NOT_IMPLEMENTED, "not in this project")),
            )
        val emptyReport = checkNodeEconomy(UfoProject(UfoFontInfo(), emptyList()), "sans-geometric", fixtureCorpus())

        val rows = workbookGateRows(taskIndex = 4, gate = gate, task4Report = emptyReport)

        val row = rows.single()
        assertEquals("NOT IMPLEMENTED", row.valueText)
        assertNull(row.boxText)
    }

    @Test
    fun roundToDisplayIntRoundsToTheNearestWholeNumber() {
        assertEquals(22, 22.25.roundToDisplayInt())
        // kotlin.math.round ties towards the even integer (the same behaviour
        // NumberFormatTest's own exactTiesRoundToEven documents for toFixedString) -- 22.5's two
        // nearest integers are 22 and 23, and 22 is even.
        assertEquals(22, 22.5.roundToDisplayInt())
        assertEquals(24, 23.5.roundToDisplayInt())
        assertEquals(12, 12.0.roundToDisplayInt())
    }
}
