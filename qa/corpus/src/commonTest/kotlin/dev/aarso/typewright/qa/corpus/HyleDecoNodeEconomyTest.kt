package dev.aarso.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * CLAUDE.md's fixture line (restated 2026-09-24, P0c): `o 80·0·80 → 16·16·32` -- Hyle Deco's
 * shipped `o` has 80 on-curve points in 2 contours; its honestly fitted target is 16 on-curve +
 * 16 off-curve = 32 total (a rounded rectangle, not an ellipse -- brief 8.5). This test checks
 * that fitted target against the real, regenerated sans-geometric box (the class Hyle Deco
 * belongs to) rather than against a hand-picked number, so the P1b style-detector task and the
 * qa checks task both have one place that states the current answer.
 */
class HyleDecoNodeEconomyTest {
    private val hyleDecoOOnCurve = 16
    private val hyleDecoOOffCurve = 16

    @Test
    fun hyleDecoOOnCurveAgainstSansGeometricBoxIsInRange() {
        val corpus = NodeEconomyCorpus.load()
        val onBox = corpus.onCurveBox("sans-geometric", "o")
        assertEquals(Quartiles(min = 20.0, q1 = 23.25, med = 24.0, q3 = 24.0, max = 100.0, n = 30), onBox)
        checkNotNull(onBox)

        // Q3 = 24, fence = 24 + max(1.5 * (24 - 23.25), 0.25 * 24) = 24 + max(1.125, 6) = 30.
        assertEquals(30.0, outlierFence(onBox))
        assertEquals(NodeEconomyVerdict.IN_RANGE, verdictFor(hyleDecoOOnCurve, onBox))
    }

    @Test
    fun hyleDecoOOffCurveAgainstSansGeometricBoxIsAlsoInRange() {
        val corpus = NodeEconomyCorpus.load()
        val offBox = corpus.offCurveBox("sans-geometric", "o")
        assertEquals(Quartiles(min = 0.0, q1 = 23.0, med = 24.0, q3 = 24.0, max = 100.0, n = 30), offBox)
        checkNotNull(offBox)

        // Q3 = 24, fence = 24 + max(1.5 * (24 - 23), 0.25 * 24) = 24 + max(1.5, 6) = 30.
        assertEquals(30.0, outlierFence(offBox))
        assertEquals(NodeEconomyVerdict.IN_RANGE, verdictFor(hyleDecoOOffCurve, offBox))
    }
}
