// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus.style

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTest {
    @Test
    fun radialCutReadsAsFlat() {
        val c = straightCutCGlyph()
        val style = terminalStyle(c, c.outerContour()!!)
        assertEquals(TerminalStyle.FLAT, style)
    }

    @Test
    fun diagonalCutReadsAsAngled() {
        val c = angledCutBracketGlyph()
        val style = terminalStyle(c, c.outerContour()!!)
        assertEquals(TerminalStyle.ANGLED, style)
    }

    @Test
    fun curvedCapReadsAsRound() {
        val c = roundCutBracketGlyph()
        val style = terminalStyle(c, c.outerContour()!!)
        assertEquals(TerminalStyle.ROUND, style)
    }

    // extractFeatures (FeatureExtractorTest) covers TerminalStyle.UNKNOWN: it is what a glyph
    // set with no `c` at all produces, since terminalStyle is then never called.
}
