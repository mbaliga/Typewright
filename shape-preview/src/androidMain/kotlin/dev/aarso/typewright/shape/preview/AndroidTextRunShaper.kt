// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.shape.preview

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.PositionedGlyphs
import java.nio.ByteBuffer
import java.util.Locale
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.text.TextRunShaper as PlatformTextRunShaper

/**
 * Android shaper: `android.graphics.text.TextRunShaper` (API 31), Minikin's own HarfBuzz-backed
 * stack (brief §3, `ShapingStack.ANDROID_TEXT_RUN_SHAPER`).
 *
 * Font bytes load the same way this codebase's other Android-only, real-bytes-in font path
 * already does (`ui/src/androidMain/.../LearnFaceFonts.android.kt`'s `LearnFaceTypefaceLoader`):
 * a direct [ByteBuffer] into [PlatformFont.Builder] (API 29), wrapped in a
 * [PlatformFontFamily] and a [Typeface.CustomFallbackBuilder] (API 29), both covered by this
 * module's `minSdk` 31. [Paint.setTextSize] is set to `unitsPerEm`, the same "size equals
 * unitsPerEm" trick [SkikoShaper] uses on desktop, so [PositionedGlyphs]' coordinates come back
 * already in font units.
 *
 * **Cluster data: genuinely not available here, empirically, not by assumption.**
 * [PositionedGlyphs]' full API surface (`android.graphics.text.PositionedGlyphs`, API 31-35)
 * exposes `getGlyphId`, `getGlyphX`/`getGlyphY`, `getFont`, `getAdvance`, and (API 35)
 * `getFakeBold`/`getFakeItalic`/`getWeightOverride`/`getItalicOverride` — there is no
 * `getCluster`/`getClusterStart` or any text-range method at all, on any API level this
 * module's `minSdk` 31 through the `android-37` platform this task compiled against covers.
 * [ShapedRun.clusters] is therefore always `null` here, exactly as this interface's own KDoc
 * says a stack should when it withholds cluster data, and exactly as
 * `docs/ARCHITECTURE_REVIEW.md` section 4.3 found before this class was written. This
 * repository's v1 decision is not to add a JNI HarfBuzz binding to recover it (`Shaper.kt`'s
 * own top KDoc: "not through JNI, in v1") — see `docs/OPEN_QUESTIONS.md`, P8, for why that
 * still holds and is not overridden here.
 *
 * [ShapeRequest.script] cannot be forced through this API either: `TextRunShaper.shapeTextRun`
 * takes no script parameter, and Minikin detects script from the text itself (there is no
 * override, unlike [SkikoShaper]'s [org.jetbrains.skia.shaper.TrivialScriptRunIterator] path)
 * — it is silently ignored here rather than pretended to be honoured, and that gap is also
 * logged in `docs/OPEN_QUESTIONS.md`.
 *
 * Positions: `y` is negated the same way [SkikoShaper] negates Skia's, on the same
 * documented-but-**not device-verified** basis — Android's `Canvas`/`Paint` coordinate system
 * is the same y-down convention Skia uses (`Paint.FontMetrics.ascent` is documented negative,
 * matching Android's Skia-backed graphics stack), not because this file's own shaping output
 * was ever seen. See the disclosure below for why it could not be.
 *
 * **Compiles for real** — `:shape-preview:compileAndroidMain`, run offline against
 * `/opt/android-sdk`'s `android-37` platform, as part of this task's own verification. **Not**
 * run on a device or emulator: this container has neither (CLAUDE.md law 4). This file is real
 * code, not a stub — its on-device behaviour is simply owner-verified only, exactly like every
 * other Android-only path in this repository (e.g. `LearnFaceFonts.android.kt`, `MainActivity`).
 * Nothing in this KDoc about real shaped output, glyph coverage, fallback behaviour or the
 * exact sign of a position is a claim this task ran and watched happen on Android — only the
 * desktop half of this task (`SkikoShaperTest`) is that.
 */
class AndroidTextRunShaper : Shaper {
    override val stack: ShapingStack = ShapingStack.ANDROID_TEXT_RUN_SHAPER

    override suspend fun shape(request: ShapeRequest): ShapeResult {
        val unitsPerEm = request.font.unitsPerEm
        require(unitsPerEm > 0) { "ShapeFont.unitsPerEm must be positive, was $unitsPerEm" }

        val buffer =
            ByteBuffer.allocateDirect(request.font.bytes.size).apply {
                put(request.font.bytes)
                rewind()
            }
        val platformFont = PlatformFont.Builder(buffer).build()
        val platformFamily = PlatformFontFamily.Builder(platformFont).build()
        val typeface = Typeface.CustomFallbackBuilder(platformFamily).build()

        val paint =
            Paint().apply {
                setTypeface(typeface)
                textSize = unitsPerEm.toFloat()
                setTextLocale(request.language?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault())
                if (request.features.isNotEmpty()) {
                    fontFeatureSettings = request.features.entries.joinToString(", ") { (tag, value) -> "'$tag' $value" }
                }
            }

        val glyphs: PositionedGlyphs =
            PlatformTextRunShaper.shapeTextRun(
                request.text,
                0,
                request.text.length,
                0,
                request.text.length,
                0f,
                0f,
                request.rightToLeft,
                paint,
            )

        val positioned =
            (0 until glyphs.glyphCount()).map { i ->
                PositionedGlyph(
                    glyphId = glyphs.getGlyphId(i),
                    x = glyphs.getGlyphX(i),
                    // Normalized -0f to 0f the same way SkikoShaper does -- see its KDoc/comment.
                    y = (-glyphs.getGlyphY(i)).let { if (it == 0f) 0f else it },
                )
            }

        return ShapeResult.Shaped(
            run = ShapedRun(glyphs = positioned, clusters = null),
            stack = stack,
        )
    }
}

actual fun platformShaper(): Shaper = AndroidTextRunShaper()
