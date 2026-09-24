package dev.aarso.typewright.core.font.sfnt

import dev.aarso.typewright.core.geometry.count
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The point of P1a-font: the Kotlin sfnt reader's node-economy counts for
 * `fonts/HyleDeco-Regular.ttf`, produced by feeding [readSfntFont]'s [dev.aarso.typewright.core.geometry.Glyph]s
 * through `core-geometry`'s own [dev.aarso.typewright.core.geometry.Glyph.count], must equal the
 * numbers an independent script recomputed from scratch against the raw `glyf` table:
 *
 * > Independent script (`fonts/HyleDeco-Regular.ttf`, fontTools 4.66.0), written from scratch
 * > against the raw `glyf` table -- does not import or call `count_points()`/
 * > `build_node_economy_corpus.py`. Method: for every glyph in font order, decompose composites
 * > recursively by hand (apply each component's `(a,b,c,d,dx,dy)` affine transform to its base
 * > glyph's already-decomposed contours, recursing into nested composites), then for every contour
 * > apply the same cyclic-consecutive-off/off-pair implied-on-curve rule by hand. Cross-validated
 * > against fontTools' own `Glyph.getCoordinates(glyfTable)` primitive (independent second method)
 * > -- both agree exactly. Results: total glyphs = 338; total on-curve (flagged) = 68,941; total
 * > off-curve (flagged) = 0; total implied on-curve (from off/off pairs) = 0 (the font has zero
 * > off-curve points anywhere, confirmed -- it is a pure polygon font, as
 * > TYPEWRIGHT_BUILD_BRIEF.md already states); total on-curve EFFECTIVE (flagged+implied) =
 * > 68,941. Per glyph -- T: on=1,763, off=0, contours=1; o: on=80, off=0, contours=2; n: on=44,
 * > off=0, contours=1; H: on=1,252, off=0, contours=1 (all four unchanged from the existing
 * > CLAUDE.md/D15/brief fixtures, since none of the four is a composite glyph -- the fix only
 * > changes counts for composites and for fonts with actual off-curve points).
 *
 * (docs/ARCHITECTURE_REVIEW.md section 5's own recheck, 25,991 on-curve points, was itself
 * undercounted by the same composites-as-zero bug: it summed only the font's 177 simple glyphs,
 * missing the 161 composite (accented Latin) glyphs entirely. This test's total, 68,941, is that
 * 25,991 plus the 42,950 points decomposing those 161 composites adds.)
 *
 * The font bytes come from [HyleDecoRegularTtfBase64] (a byte-for-byte copy of the real file,
 * embedded so this test runs identically on the JVM and Kotlin/Wasm; see that file's KDoc).
 */
class HyleDecoCrossCheckTest {
    private val font by lazy {
        assertEquals(HyleDecoRegularTtfBase64.EXPECTED_SIZE, HyleDecoRegularTtfBase64.bytes.size, "embedded font byte count")
        readSfntFont(HyleDecoRegularTtfBase64.bytes)
    }

    @Test
    fun hasTheExpectedGlyphCount() {
        assertEquals(338, font.numGlyphs)
    }

    @Test
    fun wholeFontOnCurveAndOffCurveTotalsMatchTheIndependentRecount() {
        var totalOn = 0
        var totalOff = 0
        for (gid in 0 until font.numGlyphs) {
            val count = font.glyph(gid).count()
            totalOn += count.onCurveEquivalent
            totalOff += count.offCurve
        }
        assertEquals(68_941, totalOn, "total on-curve-equivalent points across all 338 glyphs")
        assertEquals(0, totalOff, "total off-curve points across all 338 glyphs (Hyle Deco is a pure polygon font)")
    }

    @Test
    fun capitalTMatchesTheFixture() {
        val glyph = font.glyphForCodePoint('T'.code) ?: error("no glyph mapped for 'T'")
        val count = glyph.count()
        assertEquals(1_763, count.onCurveEquivalent, "T on-curve")
        assertEquals(0, count.offCurve, "T off-curve")
        assertEquals(1, count.contourCount, "T contours")
    }

    @Test
    fun lowercaseOMatchesTheFixture() {
        val glyph = font.glyphForCodePoint('o'.code) ?: error("no glyph mapped for 'o'")
        val count = glyph.count()
        assertEquals(80, count.onCurveEquivalent, "o on-curve")
        assertEquals(0, count.offCurve, "o off-curve")
        assertEquals(2, count.contourCount, "o contours")
    }

    @Test
    fun lowercaseNMatchesTheFixture() {
        val glyph = font.glyphForCodePoint('n'.code) ?: error("no glyph mapped for 'n'")
        val count = glyph.count()
        assertEquals(44, count.onCurveEquivalent, "n on-curve")
        assertEquals(0, count.offCurve, "n off-curve")
        assertEquals(1, count.contourCount, "n contours")
    }

    @Test
    fun capitalHMatchesTheFixture() {
        val glyph = font.glyphForCodePoint('H'.code) ?: error("no glyph mapped for 'H'")
        val count = glyph.count()
        assertEquals(1_252, count.onCurveEquivalent, "H on-curve")
        assertEquals(0, count.offCurve, "H off-curve")
        assertEquals(1, count.contourCount, "H contours")
    }

    @Test
    fun compositeGlyphsAreDecomposedNotCountedAsZero() {
        // uni00C0 ("Agrave") is A + grave, a two-component composite (verified directly against
        // the font with fontTools while writing this test: A has 2 contours -- its triangular
        // counter is a separate inner contour -- and grave has 1, for 3 total); decomposition
        // must give it those real points and contours, not the pre-fix bug's zero.
        val agrave = font.glyph("uni00C0")
        require(agrave != null) { "font has no glyph named 'uni00C0'" }
        val count = agrave.count()
        assertTrue(count.onCurveEquivalent > 0, "composite glyph 'uni00C0' decomposed to zero on-curve points")
        assertEquals(3, count.contourCount, "uni00C0 = A (2 contours) + grave (1 contour)")
    }
}
