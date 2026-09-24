package dev.aarso.typewright.shape.preview

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontFeature
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.ManagedString
import org.jetbrains.skia.Point
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.shaper.HbIcuScriptRunIterator
import org.jetbrains.skia.shaper.RunHandler
import org.jetbrains.skia.shaper.RunInfo
import org.jetbrains.skia.shaper.ShapingOptions
import org.jetbrains.skia.shaper.TrivialBidiRunIterator
import org.jetbrains.skia.shaper.TrivialFontRunIterator
import org.jetbrains.skia.shaper.TrivialLanguageRunIterator
import org.jetbrains.skia.shaper.TrivialScriptRunIterator
import java.util.Locale
import org.jetbrains.skia.Font as SkiaFont
import org.jetbrains.skia.shaper.Shaper as SkiaShaper

/**
 * Desktop shaper: Skiko 0.150.1's own `org.jetbrains.skia.shaper.Shaper` (Skia's SkShaper,
 * HarfBuzz-backed; brief §3, `docs/ARCHITECTURE_REVIEW.md` section 4.3). Real, run for real by
 * `SkikoShaperTest` (`desktopTest`) against `fonts/HyleDeco-Regular.ttf`,
 * `fonts/NotoSansDevanagari-Regular.ttf` and `fonts/NotoNaskhArabic-Regular.ttf`.
 *
 * The font's bytes are decoded with [FontMgr.makeFromData] and shaped at `size = unitsPerEm`,
 * which makes Skia report every position already in font units (the scale factor
 * `size / unitsPerEm` is exactly 1) -- no separate unit conversion is needed.
 *
 * No system-font fallback: [SkiaShaper.make] is called with a `null` `FontMgr`, which Skia's
 * own `SkShaper_harfbuzz.cpp` turns into `SkFontMgr::RefEmpty()` (verified from that file, kept
 * in this task's own research). A character the compiled font does not cover renders as
 * `.notdef`, not a silent substitute from a system face -- brief §5 and this interface's own
 * top KDoc ("no shaper fakes glyphs"). [TrivialFontRunIterator] reinforces this: the whole
 * string is shaped against the one [SkiaFont] given, never Skia's own font-fallback run
 * splitting.
 *
 * [ShapeRequest.rightToLeft] and [ShapeRequest.language] are forced for the whole string with
 * [TrivialBidiRunIterator]/[TrivialLanguageRunIterator] (single-run iterators Skiko itself
 * ships for exactly this "one known value for the whole text" case), matching this interface's
 * simple, non-`null` `rightToLeft: Boolean` contract rather than running ICU's own bidi
 * algorithm on a string this call already told us the direction of. [ShapeRequest.script],
 * when given, is forced the same way with [TrivialScriptRunIterator]; when `null` ("the stack
 * detects it", this interface's own KDoc), [HbIcuScriptRunIterator] runs real ICU-backed
 * detection on the text -- Skiko's own convenience `shape(text, font)` overload always does the
 * latter and has no way to force a script or language at all, which is why this class uses the
 * lower-level iterator-based overload instead.
 *
 * Positions: Skia's [Point] is y-down (confirmed empirically in `SkikoShaperTest` — a real
 * [org.jetbrains.skia.Font.metrics] `ascent` comes back negative, Skia's own documented
 * convention for "up"), so every position is negated to match this interface's y-up contract.
 * [RunHandler.runOffset] always returns [Point.Companion.ZERO] here and [CollectingRunHandler]
 * does its own running-offset bookkeeping in [RunHandler.commitRun] instead, so correctness
 * does not depend on guessing whether Skiko additionally applies the returned offset itself
 * (`RunHandler.kt`'s own KDoc leaves that ambiguous for anything but `Shaper.makeCoreText`,
 * which this class never uses).
 *
 * Clusters: real, not null. [ShapedRun.clusters] is built by [buildSkikoClusters] from the
 * `clusters: IntArray` [RunHandler.commitRun] hands over, which Skiko's own KDoc already
 * documents as UTF-16 offsets (see [buildSkikoClusters]'s own KDoc for how that was checked,
 * not just trusted). This is the empirical finding this task's own instructions asked for: on
 * desktop, real cluster data is genuinely available from the platform's own API, so no
 * HarfBuzz-via-JNI fallback binding is needed here (`docs/OPEN_QUESTIONS.md`, P8).
 */
class SkikoShaper : Shaper {
    override val stack: ShapingStack = ShapingStack.SKIKO_SHAPER

    override suspend fun shape(request: ShapeRequest): ShapeResult {
        val unitsPerEm = request.font.unitsPerEm
        require(unitsPerEm > 0) { "ShapeFont.unitsPerEm must be positive, was $unitsPerEm" }

        val glyphs = mutableListOf<PositionedGlyph>()
        val clusterStarts = mutableListOf<Int>()

        Data.makeFromBytes(request.font.bytes).use { data ->
            val typeface =
                FontMgr.default.makeFromData(data)
                    ?: throw IllegalArgumentException(
                        "Skia could not parse these ${request.font.bytes.size} bytes as a TTF/OTF font",
                    )
            typeface.use { tf ->
                SkiaFont(tf, unitsPerEm.toFloat()).use { font ->
                    val features =
                        request.features
                            .map { (tag, value) -> FontFeature(tag, value) }
                            .toTypedArray()
                    val opts =
                        ShapingOptions.DEFAULT
                            .withLeftToRight(!request.rightToLeft)
                            .withFeatures(if (features.isEmpty()) null else features)

                    SkiaShaper.make(null).use { shaper ->
                        ManagedString(request.text).use { textUtf8 ->
                            val fontIter = TrivialFontRunIterator(request.text, font)
                            val bidiIter = TrivialBidiRunIterator(request.text, if (request.rightToLeft) 1 else 0)
                            val languageTag = request.language ?: Locale.getDefault().toLanguageTag()
                            val langIter = TrivialLanguageRunIterator(request.text, languageTag)
                            val handler = CollectingRunHandler(glyphs, clusterStarts)

                            val explicitScript = request.script
                            if (explicitScript != null) {
                                shaper.shape(
                                    textUtf8,
                                    fontIter,
                                    bidiIter,
                                    TrivialScriptRunIterator(request.text, explicitScript),
                                    langIter,
                                    opts,
                                    Float.POSITIVE_INFINITY,
                                    handler,
                                )
                            } else {
                                HbIcuScriptRunIterator(textUtf8, false).use { scriptIter ->
                                    shaper.shape(
                                        textUtf8,
                                        fontIter,
                                        bidiIter,
                                        scriptIter,
                                        langIter,
                                        opts,
                                        Float.POSITIVE_INFINITY,
                                        handler,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val clusters = buildSkikoClusters(request.text.length, glyphs.size, clusterStarts)
        return ShapeResult.Shaped(
            run = ShapedRun(glyphs = glyphs, clusters = clusters),
            stack = stack,
        )
    }
}

/**
 * Collects every run's glyphs, positions and clusters into one [ShapedRun]'s worth of parallel
 * lists, with its own manual running x/y offset -- see [SkikoShaper]'s KDoc for why.
 */
private class CollectingRunHandler(
    private val glyphs: MutableList<PositionedGlyph>,
    private val clusterStarts: MutableList<Int>,
) : RunHandler {
    private var lineX = 0f
    private var lineY = 0f

    override fun beginLine() {
        lineX = 0f
        lineY = 0f
    }

    override fun runInfo(info: RunInfo?) = Unit

    override fun commitRunInfo() = Unit

    override fun runOffset(info: RunInfo?): Point = Point(0f, 0f)

    override fun commitRun(
        info: RunInfo?,
        glyphs: ShortArray?,
        positions: Array<Point?>?,
        clusters: IntArray?,
    ) {
        val glyphCount = info?.glyphCount ?: 0
        for (i in 0 until glyphCount) {
            val position = positions?.get(i) ?: Point(0f, 0f)
            this.glyphs +=
                PositionedGlyph(
                    glyphId = (glyphs?.get(i) ?: 0).toInt() and 0xFFFF,
                    x = lineX + position.x,
                    // Normalize -0f to 0f: -(0f) is negative zero, mathematically equal to zero
                    // (`-0f == 0f` is true) but a surprising thing to hand a caller (formats as
                    // "-0.0", fails `assertEquals(0f, ...)` in a test that checks `.equals`).
                    y = (-(lineY + position.y)).let { if (it == 0f) 0f else it },
                )
            clusterStarts += clusters?.get(i) ?: 0
        }
        lineX += info?.advanceX ?: 0f
        lineY += info?.advanceY ?: 0f
    }

    override fun commitLine() = Unit
}

actual fun platformShaper(): Shaper = SkikoShaper()
