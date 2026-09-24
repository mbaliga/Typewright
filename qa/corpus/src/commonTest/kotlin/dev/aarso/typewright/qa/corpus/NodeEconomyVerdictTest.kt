package dev.aarso.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeEconomyVerdictTest {
    @Test
    fun fenceMatchesARealCorpusBoxWhereBothTermsTie() {
        // sans-grotesque's real "o" on-curve box: min 14, Q1 20, med 21, Q3 24, max 32.
        val box = Quartiles(min = 14.0, q1 = 20.0, med = 21.0, q3 = 24.0, max = 32.0, n = 30)
        // IQR = 4, 1.5 * IQR = 6, 0.25 * Q3 = 6 -- the two terms tie here; the next two tests
        // use boxes where they differ, to check `max` picks the right one either way.
        assertEquals(30.0, outlierFence(box))
    }

    @Test
    fun fenceUsesTheQuarterQ3MinimumWhenTheIqrIsZero() {
        // brief 8.2's own example: "several boxes have zero IQR (the geometric T is 8-9)".
        // The regenerated sans-geometric T box has Q1 = Q3 = 8 exactly for some percentiles in
        // the corpus family; use a box with a genuinely zero IQR to isolate the 0.25 * Q3 term.
        val box = Quartiles(min = 8.0, q1 = 8.0, med = 8.0, q3 = 8.0, max = 28.0, n = 30)
        // 1.5 * IQR = 0, 0.25 * Q3 = 2 -- the minimum slack is the deciding term.
        assertEquals(10.0, outlierFence(box))
    }

    @Test
    fun fenceUsesOneAndAHalfIqrWhenItExceedsTheQuarterQ3Minimum() {
        val box = Quartiles(min = 0.0, q1 = 0.0, med = 10.0, q3 = 20.0, max = 200.0, n = 10)
        // IQR = 20, 1.5 * IQR = 30, 0.25 * Q3 = 5.
        assertEquals(50.0, outlierFence(box))
    }

    @Test
    fun verdictIsInRangeAtAndBelowQ3() {
        val box = Quartiles(min = 8.0, q1 = 8.0, med = 8.0, q3 = 8.0, max = 28.0, n = 30)
        assertEquals(NodeEconomyVerdict.IN_RANGE, verdictFor(0, box))
        assertEquals(NodeEconomyVerdict.IN_RANGE, verdictFor(8, box))
    }

    @Test
    fun verdictIsAboveJustPastQ3AndAtTheFenceItself() {
        val box = Quartiles(min = 8.0, q1 = 8.0, med = 8.0, q3 = 8.0, max = 28.0, n = 30)
        // fence is 10.0 (previous test).
        assertEquals(NodeEconomyVerdict.ABOVE, verdictFor(9, box))
        assertEquals(NodeEconomyVerdict.ABOVE, verdictFor(10, box))
    }

    @Test
    fun verdictIsOutlierOnceTheCountExceedsTheFence() {
        val box = Quartiles(min = 8.0, q1 = 8.0, med = 8.0, q3 = 8.0, max = 28.0, n = 30)
        assertEquals(NodeEconomyVerdict.OUTLIER, verdictFor(11, box))
    }

    @Test
    fun verdictAppliesEquallyToAnOffCurveBox() {
        // "also expose it generically enough to apply to off-curve counts if a caller wants
        // that" -- verdictFor takes any Quartiles box, on- or off-curve.
        val offBox = Quartiles(min = 0.0, q1 = 23.0, med = 24.0, q3 = 24.0, max = 100.0, n = 30)
        assertEquals(NodeEconomyVerdict.IN_RANGE, verdictFor(16, offBox))
        assertEquals(NodeEconomyVerdict.OUTLIER, verdictFor(31, offBox))
    }
}
