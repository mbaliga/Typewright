// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.shape.preview

/**
 * Web shaper: the browser's text engine with the font loaded through `FontFace`. Still a stub,
 * and re-verified for real rather than trusted from P0 (P9, `docs/OPEN_QUESTIONS.md` has the
 * full write-up): no standard, shipped, non-flagged browser API hands back per-glyph glyph ids
 * or a text-to-glyph cluster map for already-shaped text, so [Shaper.shape] genuinely cannot
 * return a [ShapeResult.Shaped] here -- there is nothing to build a [ShapedRun] from.
 *
 * **What was actually checked, live, in this sandbox's own real, stable, non-flagged Chromium
 * 141.0.7390.37 (`/opt/pw-browsers/chromium-1194`, via Playwright)**, not assumed from memory: a
 * real `FontFace` built from `fonts/NotoSansDevanagari-Regular.ttf`'s real bytes was loaded and
 * added to `document.fonts`, and real Devanagari conjunct text (क्ष, U+0915 U+094D U+0937 -- the
 * same fixture `SkikoShaperTest.shapesRealDevanagariConjunctAndFindsRealClusterData` shapes on
 * desktop) was drawn with it:
 * - `CanvasRenderingContext2D.measureText(text)`'s [TextMetrics] came back with exactly `width`,
 *   `actualBoundingBoxLeft/Right`, `fontBoundingBoxAscent/Descent`, `actualBoundingBoxAscent/Descent`,
 *   `hangingBaseline`, `alphabeticBaseline`, `ideographicBaseline` -- whole-string metrics only,
 *   confirmed by dumping every own+inherited property on a real `TextMetrics` instance, not by
 *   reading a spec. No glyph count, no glyph ids, no per-character or per-cluster boxes anywhere
 *   on it.
 * - `TextMetrics.getSelectionRects` (the one experimental candidate that sometimes ships
 *   partially flagged) does not exist on this real, stable build: `typeof tm.getSelectionRects
 *   === 'function'` is `false`.
 * - The "CSS Font Metrics API" does not exist as a readable API on a real `FontFace` here either:
 *   `FontFace.prototype`'s own real property list is `family, style, weight, stretch,
 *   unicodeRange, variant, featureSettings, display, ascentOverride, descentOverride,
 *   lineGapOverride, sizeAdjust, status, loaded, load, variationSettings` -- the `*Override`
 *   entries are write-only-in-spirit `@font-face` descriptors for metric *overriding*, not a
 *   read API for an already-loaded face's real metrics, and there is no readable `ascent`/
 *   `descent`/`lineGap` getter at all.
 * - `OffscreenCanvas`'s `CanvasRenderingContext2D` exposes the exact same restricted
 *   `TextMetrics` surface (checked directly) and no extra glyph method
 *   (`getGlyphs`/`getGlyphIds` do not exist).
 * - No `Canvas2D` prototype method matches `/glyph|shape|cluster|run/i` at all (checked by
 *   listing `CanvasRenderingContext2D.prototype`'s own real property names).
 * - The Local Font Access API (`navigator.fonts.query`) is not present in this build
 *   (`'fonts' in navigator` is `false` here); even where it does ship, it reads a *locally
 *   installed* system font's raw table bytes by postscript name, not shaped output from an
 *   arbitrary `FontFace` -- a "read the font and shape it yourself" route, not "the browser's
 *   own text engine hands back structure", so it would not answer this differently anyway.
 * - `window` has no `GlyphRun` (or similar) primitive.
 * - Real shaping is genuinely still happening under all of this, confirmed by width alone: the
 *   same conjunct measured 71.7 CSS px wide at `font: 100px`, a single unligated "क" measured
 *   76.8 px, and three separate base consonants ("ककक", no virama) measured 230.4 px -- the
 *   conjunct is *narrower than one single consonant*, real evidence of real ligation into one
 *   compact glyph, not three glyphs painted in sequence. The browser's text engine is doing real,
 *   correct HarfBuzz-backed work; it simply never publishes the intermediate structure through
 *   any API checked above.
 *
 * Given all of that still holds, empirically, today: rather than force a fake [ShapeResult.Shaped]
 * to make this interface look filled (this interface's own top KDoc: "no shaper fakes glyphs"),
 * this class stays a real, honest stub, and [BrowserFontFaceVisualPreview] -- a *different*,
 * clearly-disclosed kind of artifact, not a `Shaper` -- was built instead: a real `FontFace`-based
 * *visual* preview a user can actually look at (see its own KDoc for why that is still genuinely
 * useful, and why it is deliberately not named or shaped like this interface).
 */
class BrowserFontFaceShaperStub : Shaper {
    override val stack: ShapingStack = ShapingStack.BROWSER_FONT_FACE

    override suspend fun shape(request: ShapeRequest): ShapeResult =
        ShapeResult.NotImplemented(
            stack = stack,
            planned =
                "Not built: no standard, shipped, non-flagged browser API hands back per-glyph glyph " +
                    "ids or a text-to-glyph cluster map (re-verified live in real, stable Chromium " +
                    "141.0.7390.37 for P9 -- this class's own KDoc has the full, itemized check: " +
                    "Canvas 2D TextMetrics, TextMetrics.getSelectionRects, FontFace's own metric " +
                    "properties, OffscreenCanvas, every Canvas2D method, the Local Font Access API, " +
                    "and window.GlyphRun, none of them expose it). Real ligation still happens " +
                    "under FontFace-loaded text (measured directly: a real Devanagari conjunct came " +
                    "out narrower than a single unligated consonant), the browser just never " +
                    "publishes the shaped structure -- see BrowserFontFaceVisualPreview for a " +
                    "visual (not glyph-level) preview built on that real rendering instead.",
        )
}

actual fun platformShaper(): Shaper = BrowserFontFaceShaperStub()
