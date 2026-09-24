package dev.aarso.typewright.shape.preview

/**
 * Android shaper, planned: `android.graphics.text.TextRunShaper` (API 31). Its
 * `PositionedGlyphs` gives glyph ids and positions but no cluster map, so [ShapedRun.clusters]
 * will be null here unless a script's preview needs a direct HarfBuzz binding (brief §3).
 */
class TextRunShaperStub : Shaper {
    override val stack: ShapingStack = ShapingStack.ANDROID_TEXT_RUN_SHAPER

    override suspend fun shape(request: ShapeRequest): ShapeResult =
        ShapeResult.NotImplemented(
            stack = stack,
            planned =
                "Not built yet. Planned: load the font with Font.Builder, shape with " +
                    "TextRunShaper.shapeTextRun and map PositionedGlyphs to glyph ids and positions.",
        )
}

actual fun platformShaper(): Shaper = TextRunShaperStub()
