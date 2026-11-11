// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.project.KernSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SpaceString]: a control string set in a project's own letters, kerning applied, nothing substituted for what is not drawn. */
class SpaceStringTest {
    private val kerning =
        UfoKerning(
            groups = mapOf("public.kern1.round" to listOf("o", "O"), "public.kern2.straight" to listOf("n", "H")),
            kerning = mapOf("n" to mapOf("o" to -12.0), "public.kern1.round" to mapOf("public.kern2.straight" to -24.0)),
        )

    @Test
    fun glyphsAreSetEdgeToEdgeWithTheirAdvances() {
        val string = SpaceString.set(SpaceFixtures.noho(), "nonH")
        assertEquals(listOf("n", "o", "n", "H"), string.cells.map { it.glyph.name })
        assertEquals(listOf(0.0, 500.0, 1020.0, 1520.0), string.cells.map { it.x })
        assertEquals(2220.0, string.width)
        assertEquals(listOf(0, 1, 2, 3), string.cells.map { it.index })
    }

    @Test
    fun kerningMovesTheSecondLetterByTheValueTheLookupGives() {
        val string = SpaceString.set(SpaceFixtures.noho(kerning), "noOn")
        // n o: the pair exception -12; o O: nothing; O n: round group before straight group, -24.
        assertEquals(listOf(0.0, 488.0, 1008.0, 1744.0), string.cells.map { it.x })
        assertEquals(KernSource.PAIR, string.cells[1].kern?.source)
        assertEquals(KernSource.NONE, string.cells[2].kern?.source)
        assertEquals(KernSource.GROUP_GROUP, string.cells[3].kern?.source)
        assertNull(string.cells[0].kern, "the first letter has nothing before it to kern against")
    }

    @Test
    fun aSpaceIsAGapAndStopsKerningAcrossIt() {
        val string = SpaceString.set(SpaceFixtures.noho(kerning), "n o")
        assertEquals(listOf(0.0, 750.0), string.cells.map { it.x }, "n, then the 250-wide space, then o with no kerning against n")
        assertNull(string.cells[1].kern)
        assertEquals(1, string.gaps.size)
        assertEquals(500.0, string.gaps.single().x)
        assertEquals(250.0, string.gaps.single().width)
        assertEquals(false, string.hasJunction(0))
    }

    @Test
    fun lettersTheFontHasNotDrawnAreLeftOutAndNamedNeverSubstituted() {
        val font =
            SpaceFixtures.font(
                listOf(SpaceFixtures.glyph("n", 'n', 500), SpaceFixtures.glyph("a", 'a', 480, drawn = false)),
            )
        val string = SpaceString.set(font, "nanx")
        assertEquals(listOf("n", "n"), string.cells.map { it.glyph.name })
        assertEquals(listOf(LeftOut.NOT_DRAWN, LeftOut.NO_GLYPH), string.leftOut.map { it.why })
        assertEquals("a, x", string.leftOutText())
        assertEquals(listOf(0.0, 500.0), string.cells.map { it.x }, "the two n set side by side, as if the others were not there")
    }

    @Test
    fun aStringOfLettersNoneOfWhichIsDrawnIsEmptyAndSaysWhich() {
        val string = SpaceString.set(SpaceFixtures.noho(), "zzq")
        assertTrue(string.cells.isEmpty())
        assertEquals("z, q", string.leftOutText())
        assertNull(SpaceString.EMPTY.leftOutText())
    }

    @Test
    fun sidebearingsAreMeasuredFromTheDrawingThroughCoreGeometry() {
        val cell = SpaceString.set(SpaceFixtures.noho(), "n").cells.single()
        assertEquals(46, cell.lsb)
        assertEquals(51, cell.rsb)
        assertEquals(500.0, cell.right)
    }

    @Test
    fun junctionsAreBetweenLettersSetSideBySide() {
        val string = SpaceString.set(SpaceFixtures.noho(kerning), "no H")
        assertTrue(string.hasJunction(0))
        assertEquals(false, string.hasJunction(1), "a space separates o from H")
        assertEquals(false, string.hasJunction(2), "H is last")
        assertEquals(0, string.junctionAt(0))
        assertEquals(0, string.junctionAt(1), "o has no junction after it, so the one before it")
        assertEquals(0, string.junctionAt(2), "H has none beside it, so the first there is")
        assertNull(SpaceString.set(SpaceFixtures.noho(), "n H").junctionAt(0))
    }

    @Test
    fun theNearestJunctionIsFoundByTheMiddleOfTheGapBetweenItsLetters() {
        val string = SpaceString.set(SpaceFixtures.noho(kerning), "nonn")
        val firstGap = (string.cells[0].right + string.cells[1].x) / 2.0
        assertEquals(0, string.nearestJunction(firstGap + 5.0))
        assertEquals(2, string.nearestJunction(string.cells[3].x - 3.0))
        assertEquals(1, string.nearestJunction((string.cells[1].right + string.cells[2].x) / 2.0))
    }

    @Test
    fun theCellAtAPositionIsTheOneWhoseAdvanceHoldsIt() {
        val string = SpaceString.set(SpaceFixtures.noho(), "noH")
        assertEquals(0, string.cellAt(10.0))
        assertEquals(1, string.cellAt(520.0))
        assertEquals(2, string.cellAt(1100.0))
        assertEquals(0, string.cellAt(-40.0), "left of the string is the first letter")
        assertEquals(2, string.cellAt(99999.0), "right of it is the last")
        assertNull(SpaceString.EMPTY.cellAt(0.0))
    }

    @Test
    fun indexOfFindsTheFirstCellOfAGlyph() {
        val string = SpaceString.set(SpaceFixtures.noho(), "nnon")
        assertEquals(0, string.indexOf("n"))
        assertEquals(2, string.indexOf("o"))
        assertNull(string.indexOf("H"))
        assertNotNull(string.cells.firstOrNull())
    }
}
