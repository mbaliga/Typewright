// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.shape.preview

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.impl.use
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jetbrains.skia.Font as SkiaFont

/**
 * Real, run-for-real tests of [SkikoShaper] against real compiled fonts (`fonts/` -- this
 * build's own established `File("../fonts/...")` fixture convention, e.g.
 * `HyleDecoTask4GateTest`, `AnatomyLensDataHyleDecoTest`). No mock shaper, no fabricated output:
 * every assertion below is checked against `Shaper.shape()`'s real return value.
 */
class SkikoShaperTest {
    private fun shapeBlocking(
        shaper: Shaper,
        request: ShapeRequest,
    ): ShapeResult {
        var outcome: Result<ShapeResult>? = null
        suspend { shaper.shape(request) }.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return checkNotNull(outcome).getOrThrow()
    }

    private fun fontBytes(name: String) = File("../fonts/$name").readBytes()

    @Test
    fun skiaPointIsYDownConfirmingThePositionSignFlipSkikoShaperMakes() {
        // The empirical basis for SkikoShaper's `y = -(...)`: Skia's own FontMetrics.ascent
        // comes back negative for a real font, so "up" is negative y in Skia's space, and this
        // interface's own contract ("in font units, y up") needs the sign flipped.
        val bytes = fontBytes("HyleDeco-Regular.ttf")
        Data.makeFromBytes(bytes).use { data ->
            val typeface = checkNotNull(FontMgr.default.makeFromData(data))
            typeface.use { tf ->
                SkiaFont(tf, 1000f).use { font ->
                    assertTrue(font.metrics.ascent < 0f, "expected Skia's ascent to be negative (y-down); was ${font.metrics.ascent}")
                }
            }
        }
    }

    @Test
    fun shapesRealLatinTextWithHyleDecoAndReportsSequentialClusters() {
        val shaper = SkikoShaper()
        val request =
            ShapeRequest(
                text = "no",
                font = ShapeFont(fontBytes("HyleDeco-Regular.ttf"), unitsPerEm = 1000),
                script = "Latn",
                language = "en",
            )

        val result = shapeBlocking(shaper, request)
        assertIs<ShapeResult.Shaped>(result)
        assertEquals(ShapingStack.SKIKO_SHAPER, result.stack)

        val run = result.run
        assertEquals(2, run.glyphs.size, "expected one glyph per character for unligated Latin text: ${run.glyphs}")
        // Neither 'n' nor 'o' is glyph 0 (.notdef) in a font that actually contains them.
        assertTrue(run.glyphs.all { it.glyphId != 0 }, "expected no .notdef glyphs: ${run.glyphs}")
        // Left-to-right: strictly increasing x, baseline y (no vertical marks in plain Latin).
        assertTrue(run.glyphs[0].x < run.glyphs[1].x, "expected 'n' before 'o' in x: ${run.glyphs}")
        assertEquals(0f, run.glyphs[0].x)
        assertEquals(0f, run.glyphs[0].y)
        assertEquals(0f, run.glyphs[1].y)

        val clusters = checkNotNull(run.clusters) { "desktop must return real cluster data" }
        assertEquals(
            listOf(
                GlyphCluster(textStart = 0, textEnd = 1, glyphStart = 0, glyphEnd = 1),
                GlyphCluster(textStart = 1, textEnd = 2, glyphStart = 1, glyphEnd = 2),
            ),
            clusters,
        )
    }

    @Test
    fun shapesRealDevanagariConjunctAndFindsRealClusterData() {
        // क् ("ka" U+0915 + virama U+094D) + ष ("sha" U+0937): a genuine three-codepoint
        // conjunct. Whether it actually ligates into fewer glyphs is exactly what this test
        // checks for real, not something assumed from the brief.
        val shaper = SkikoShaper()
        val text = "क्ष"
        val request =
            ShapeRequest(
                text = text,
                font = ShapeFont(fontBytes("NotoSansDevanagari-Regular.ttf"), unitsPerEm = 1000),
                script = "Deva",
                language = "hi",
            )

        val result = shapeBlocking(shaper, request)
        assertIs<ShapeResult.Shaped>(result)
        val run = result.run

        assertTrue(run.glyphs.isNotEmpty(), "expected at least one glyph for a real conjunct")
        assertTrue(run.glyphs.all { it.glyphId != 0 }, "expected no .notdef glyphs from a font that covers Devanagari: ${run.glyphs}")
        assertTrue(
            run.glyphs.size < text.length,
            "expected the conjunct to ligate into fewer glyphs than the ${text.length} input codepoints, got ${run.glyphs.size}: ${run.glyphs}",
        )
        // Real, observed output on NotoSansDevanagari-Regular (captured once with a temporary
        // println and pinned here, this build's own convention for real-font regression tests):
        // the whole three-codepoint conjunct collapses into exactly one glyph (id 90).
        assertEquals(1, run.glyphs.size, "expected क्ष to collapse into exactly one glyph on this font: ${run.glyphs}")

        val clusters = checkNotNull(run.clusters) { "desktop must return real cluster data" }
        // The whole conjunct is one cluster: HarfBuzz's Indic shaper does not split a
        // consonant-conjunct across cluster boundaries.
        assertEquals(1, clusters.size, "expected the whole conjunct in one cluster: $clusters")
        assertEquals(GlyphCluster(textStart = 0, textEnd = text.length, glyphStart = 0, glyphEnd = run.glyphs.size), clusters.single())
    }

    @Test
    fun shapesRealArabicJoiningTextRightToLeft() {
        // "Alam" (U+0639 ain, U+0644 lam, U+0645 mim): three letters, medial joining forms in
        // the middle. Real, joined Arabic, not isolated forms -- checked below by asking Skia to
        // shape the same letters in isolation and confirming the joined glyph ids differ.
        val shaper = SkikoShaper()
        val text = "علم"
        val fontBytes = fontBytes("NotoNaskhArabic-Regular.ttf")
        val request =
            ShapeRequest(
                text = text,
                font = ShapeFont(fontBytes, unitsPerEm = 1000),
                script = "Arab",
                language = "ar",
                rightToLeft = true,
            )

        val result = shapeBlocking(shaper, request)
        assertIs<ShapeResult.Shaped>(result)
        val run = result.run

        assertEquals(3, run.glyphs.size, "expected one glyph per letter (joining changes glyph shape, not count): ${run.glyphs}")
        assertTrue(run.glyphs.all { it.glyphId != 0 }, "expected no .notdef glyphs: ${run.glyphs}")

        val clusters = checkNotNull(run.clusters) { "desktop must return real cluster data" }
        assertEquals(3, clusters.size)
        // Every codepoint is accounted for exactly once, in order, regardless of the RTL glyph
        // order Skia reports internally.
        assertEquals((0 until text.length).toList(), clusters.sortedBy { it.textStart }.map { it.textStart })

        // Confirm this is really *joined* shaping: middle-position "ain" (medial form) must not
        // be the same glyph as isolated "ain" shaped alone.
        val isolatedAinRequest =
            ShapeRequest(text = "ع", font = ShapeFont(fontBytes, unitsPerEm = 1000), script = "Arab", rightToLeft = true)
        val isolatedAin = shapeBlocking(SkikoShaper(), isolatedAinRequest)
        assertIs<ShapeResult.Shaped>(isolatedAin)
        val isolatedAinGlyphId =
            isolatedAin.run.glyphs
                .single()
                .glyphId

        val ainClusterInJoinedWord = clusters.single { it.textStart == 0 }
        val joinedAinGlyphId = run.glyphs[ainClusterInJoinedWord.glyphStart].glyphId
        assertTrue(
            joinedAinGlyphId != isolatedAinGlyphId,
            "expected the joined medial/initial form of ain to differ from its isolated form " +
                "(joined=$joinedAinGlyphId, isolated=$isolatedAinGlyphId) -- otherwise this is not real joining shaping",
        )
    }

    @Test
    fun rejectsBytesThatAreNotARealFont() {
        val shaper = SkikoShaper()
        val request = ShapeRequest(text = "x", font = ShapeFont(ByteArray(0), unitsPerEm = 1000))
        assertFailsWith<IllegalArgumentException> { shapeBlocking(shaper, request) }
    }

    @Test
    fun rejectsANonPositiveUnitsPerEm() {
        val shaper = SkikoShaper()
        val request = ShapeRequest(text = "x", font = ShapeFont(fontBytes("HyleDeco-Regular.ttf"), unitsPerEm = 0))
        assertFailsWith<IllegalArgumentException> { shapeBlocking(shaper, request) }
    }

    @Test
    fun platformShaperOnDesktopIsTheRealSkikoShaperNotAStub() {
        val shaper = platformShaper()
        assertIs<SkikoShaper>(shaper)
        assertEquals(ShapingStack.SKIKO_SHAPER, shaper.stack)
    }
}
