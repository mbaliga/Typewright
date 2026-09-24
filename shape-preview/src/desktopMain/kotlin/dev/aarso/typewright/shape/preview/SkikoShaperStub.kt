package dev.aarso.typewright.shape.preview

/**
 * Desktop shaper, planned: Skiko's `Shaper` (Skia's SkShaper over HarfBuzz), whose run
 * handler reports glyph ids, positions and cluster offsets.
 */
class SkikoShaperStub : Shaper {
    override val stack: ShapingStack = ShapingStack.SKIKO_SHAPER

    override suspend fun shape(request: ShapeRequest): ShapeResult =
        ShapeResult.NotImplemented(
            stack = stack,
            planned =
                "Not built yet. Planned: make a Skia Typeface from the font bytes and shape with " +
                    "Skiko's Shaper and a RunHandler that collects glyphs, positions and clusters.",
        )
}

actual fun platformShaper(): Shaper = SkikoShaperStub()
