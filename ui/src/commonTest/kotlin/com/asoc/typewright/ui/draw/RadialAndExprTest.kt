// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadialModelTest {
    private val centre = Vec2(200.0, 300.0)

    @Test
    fun anAngleIsMeasuredClockwiseFromStraightUp() {
        assertEquals(0.0, RadialGeometry.angleFromTop(centre, Vec2(200.0, 200.0)), 1e-9)
        assertEquals(90.0, RadialGeometry.angleFromTop(centre, Vec2(300.0, 300.0)), 1e-9)
        assertEquals(180.0, RadialGeometry.angleFromTop(centre, Vec2(200.0, 400.0)), 1e-9)
        assertEquals(270.0, RadialGeometry.angleFromTop(centre, Vec2(100.0, 300.0)), 1e-9)
    }

    @Test
    fun sweepingSelectsTheSectorAcrossTheHubFromTheFinger() {
        // Finger down and to the right (45 degrees from straight down... that is 135 clockwise from up): the board's
        // Smooth, at the top left (sector 7), is chosen.
        assertEquals(7, RadialGeometry.sectorAt(centre, Vec2(251.0, 351.0), RadialMapping.OPPOSITE))
        // Finger straight up chooses the sector at the bottom (4); finger left (270) chooses the right (2).
        assertEquals(4, RadialGeometry.sectorAt(centre, Vec2(200.0, 250.0), RadialMapping.OPPOSITE))
        assertEquals(2, RadialGeometry.sectorAt(centre, Vec2(150.0, 300.0), RadialMapping.OPPOSITE))
    }

    @Test
    fun aLiftedRingMapsDirectlySoATapChoosesTheSectorTouched() {
        assertEquals(1, RadialGeometry.sectorAt(centre, Vec2(251.0, 249.0), RadialMapping.DIRECT))
        assertEquals(0, RadialGeometry.sectorAt(centre, Vec2(200.0, 230.0), RadialMapping.DIRECT))
        assertEquals(6, RadialGeometry.sectorAt(centre, Vec2(140.0, 300.0), RadialMapping.DIRECT))
    }

    @Test
    fun insideTheDeadZoneNothingIsChosen() {
        assertNull(RadialGeometry.sectorAt(centre, Vec2(210.0, 310.0), RadialMapping.OPPOSITE))
        assertNull(RadialGeometry.sectorAt(centre, centre, RadialMapping.DIRECT))
        assertTrue(RadialGeometry.coveredSectors(centre, Vec2(205.0, 305.0)).isEmpty())
    }

    @Test
    fun theSectorsUnderTheThumbAreTheOneItIsOverAndItsTwoNeighbours() {
        assertEquals(setOf(1, 2, 3), RadialGeometry.coveredSectors(centre, Vec2(280.0, 300.0)))
        assertEquals(setOf(7, 0, 1), RadialGeometry.coveredSectors(centre, Vec2(200.0, 230.0)))
    }

    @Test
    fun theRingIsShiftedInwardToClearTheEdgesAndTheDeck() {
        val c = RadialGeometry.ringCenter(Vec2(5.0, 10.0), width = 390.0, height = 844.0, topInsetDp = 40.0, deckHeightDp = 172.0)
        assertEquals(12.0 + 120.0, c.x, 1e-9)
        assertEquals(40.0 + 12.0 + 120.0, c.y, 1e-9)
        val low = RadialGeometry.ringCenter(Vec2(380.0, 800.0), width = 390.0, height = 844.0, topInsetDp = 40.0, deckHeightDp = 172.0)
        assertEquals(390.0 - 12.0 - 120.0, low.x, 1e-9)
        assertEquals(844.0 - 172.0 - 16.0 - 120.0, low.y, 1e-9)
        val inside = RadialGeometry.ringCenter(Vec2(200.0, 400.0), width = 390.0, height = 844.0, topInsetDp = 40.0, deckHeightDp = 172.0)
        assertEquals(Vec2(200.0, 400.0), inside)
    }

    @Test
    fun aCanvasTooSmallForTheRingStillGetsACentre() {
        val c = RadialGeometry.ringCenter(Vec2(10.0, 10.0), width = 200.0, height = 300.0, topInsetDp = 0.0, deckHeightDp = 100.0)
        assertEquals(100.0, c.x, 1e-9)
    }

    @Test
    fun theNodeRadialMatchesTheBoardAndLeavesUnbuiltCommandsEmpty() {
        assertEquals(8, RadialLayouts.NODE.size)
        assertEquals("delete", RadialLayouts.NODE[0])
        assertEquals("toggle-smooth", RadialLayouts.NODE[7])
        assertNull(RadialLayouts.NODE[2], "Split is not built, so its slot is empty rather than faked")
        assertNull(RadialLayouts.NODE[6], "Tangent is not built either")
        assertEquals(
            RadialLayouts.NODE,
            RadialLayouts.forHit(
                Hit.OnPoint(
                    com.asoc.typewright.project
                        .PointRef(0, 0),
                    true,
                    1.0,
                ),
            ),
        )
        assertEquals(RadialLayouts.GLYPH, RadialLayouts.forHit(Hit.Nothing))
    }

    @Test
    fun theSweepChipTellsWhichWayToMoveTheFinger() {
        assertEquals("hold · ↘", DrawCommands.gestureFor("toggle-smooth"))
        assertEquals("hold · ↓", DrawCommands.gestureFor("delete"))
        assertEquals("hold · ↙", DrawCommands.gestureFor("add-extremes"))
        assertEquals("hold · ↑", DrawCommands.gestureFor("reverse"))
        assertEquals("hold · ↗", DrawCommands.gestureFor("align"))
        assertNull(DrawCommands.gestureFor("actual-size"))
    }
}

class NumericExprTest {
    private val scope =
        object : ExprScope {
            override fun metric(name: String): Double? =
                mapOf("xheight" to 500.0, "capheight" to 700.0, "baseline" to 0.0, "upm" to 1000.0)[name]

            override fun glyphValue(
                glyph: String?,
                property: String,
            ): Double? =
                when (glyph) {
                    null -> mapOf("adv" to 475.0, "lsb" to 47.0, "rsb" to 48.0)[property]
                    "o" -> mapOf("adv" to 475.0, "lsb" to 47.0, "rsb" to 48.0)[property]
                    "a.alt" -> 10.0
                    else -> null
                }
        }

    private fun value(text: String): Int = (NumericExpr.evaluate(text, scope) as ExprResult.Value).units

    private fun error(text: String): String = (NumericExpr.evaluate(text, scope) as ExprResult.Error).message

    @Test
    fun aPlainNumberIsReadAsItIs() {
        assertEquals(427, value("427"))
        assertEquals(-12, value(" -12 "))
        assertEquals(0, value("0.4"))
        assertEquals(1, value("0.5"), "half rounds up")
    }

    @Test
    fun anEqualsSignOpensExpressionsOverMetricsAndGlyphProperties() {
        assertEquals(500, value("=xheight"))
        assertEquals(52, value("=o.rsb+4"))
        assertEquals(100, value("=(capheight-xheight)/2"))
        assertEquals(94, value("= lsb * 2"))
        assertEquals(-700, value("=-capheight"))
        assertEquals(10, value("=a.alt.adv"))
    }

    @Test
    fun precedenceAndBracketsFollowArithmetic() {
        assertEquals(14, value("=2+3*4"))
        assertEquals(20, value("=(2+3)*4"))
        assertEquals(2, value("=10/5"))
    }

    @Test
    fun aMistakeComesBackAsAMessageNotAnException() {
        assertTrue(error("hello").contains("not a number"))
        assertTrue(error("=nonsense").contains("not a name"))
        assertTrue(error("=zz.rsb").contains("no glyph called"))
        assertTrue(error("=1/0").contains("divide"))
        assertTrue(error("=(1+2").contains("bracket"))
        assertTrue(error("=1 2").contains("Unexpected"))
        assertTrue(error("=").contains("too soon"))
        assertTrue(error("").contains("Type a number"))
    }

    @Test
    fun aValueIsRoundedToAWholeUnit() {
        val result = NumericExpr.evaluate("=7/2", scope)
        assertIs<ExprResult.Value>(result)
        assertEquals(3.5, result.value, 1e-9)
        assertEquals(4, result.units)
    }
}
