// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import kotlin.test.Test
import kotlin.test.assertEquals

class ReportTextTest {
    @Test
    fun thousandsAreSeparatedByCommas() {
        assertEquals("0", 0.withThousands())
        assertEquals("999", 999.withThousands())
        assertEquals("1,000", 1000.withThousands())
        assertEquals("1,763", 1763.withThousands())
        assertEquals("1,234,567", 1234567.withThousands())
        assertEquals("-1,500", (-1500).withThousands())
    }

    @Test
    fun boxStatisticsAreWholeOrToTwoPlaces() {
        assertEquals("8", 8.0.statText())
        assertEquals("12.5", 12.5.statText())
        assertEquals("23.25", 23.25.statText())
        assertEquals("10.75", 10.75.statText())
        assertEquals("28", 28.0.statText())
        assertEquals("0.5", 0.5.statText())
        assertEquals("0.05", 0.05.statText())
        assertEquals("8", 7.999.statText())
    }

    @Test
    fun pluralsCarryTheCountWithItsSeparators() {
        assertEquals("1 point", plural(1, "point"))
        assertEquals("1,763 points", plural(1763, "point"))
        assertEquals("0 places", plural(0, "place"))
        assertEquals("2 accented letters", plural(2, "accented letter"))
    }

    @Test
    fun listsReadAsSentences() {
        assertEquals("", nameList(emptyList()))
        assertEquals("o", nameList(listOf("o")))
        assertEquals("o and e", nameList(listOf("o", "e")))
        assertEquals("o, e and s", nameList(listOf("o", "e", "s")))
        assertEquals("a, b and 2 more", nameList(listOf("a", "b", "c", "d"), limit = 2))
    }

    @Test
    fun theSmallStripSeparatesWithSpacesAndCountsTheRest() {
        assertEquals("e c s", nameStrip(listOf("e", "c", "s")))
        assertEquals("a b +2", nameStrip(listOf("a", "b", "c", "d"), limit = 2))
    }

    @Test
    fun rawLinesAreCappedWithAnHonestCount() {
        assertEquals("a\nb", rawLines(listOf("a", "b")))
        assertEquals("a\nb\n… and 1 more", rawLines(listOf("a", "b", "c"), limit = 2))
        assertEquals("nothing found", rawLines(emptyList()))
    }
}
