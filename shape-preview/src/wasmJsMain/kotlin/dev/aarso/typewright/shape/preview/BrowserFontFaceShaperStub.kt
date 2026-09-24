package dev.aarso.typewright.shape.preview

/**
 * Web shaper, planned: the browser's text engine with the font loaded through `FontFace`.
 * Browsers render shaped text but expose neither glyph ids nor clusters, so a glyph-level
 * preview on the web needs another route (docs/OPEN_QUESTIONS.md, P0).
 */
class BrowserFontFaceShaperStub : Shaper {
    override val stack: ShapingStack = ShapingStack.BROWSER_FONT_FACE

    override suspend fun shape(request: ShapeRequest): ShapeResult =
        ShapeResult.NotImplemented(
            stack = stack,
            planned =
                "Not built yet. Planned: register the font with FontFace and let the browser shape " +
                    "and render the text; glyph ids and clusters are not available from this API.",
        )
}

actual fun platformShaper(): Shaper = BrowserFontFaceShaperStub()
