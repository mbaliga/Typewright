package dev.aarso.typewright.shape.preview

/**
 * Shapes text in the user's compiled font so conjuncts, joins and marks preview as they will
 * render (brief §3: HarfBuzz through each platform's stack, not through JNI, in v1).
 */
interface Shaper {
    /** The platform stack this shaper uses, so the UI and bug reports can name it. */
    val stack: ShapingStack

    /** Shapes [request]. A stub returns [ShapeResult.NotImplemented]; no shaper fakes glyphs. */
    suspend fun shape(request: ShapeRequest): ShapeResult
}

/** The v1 shaper for the platform this code runs on. */
expect fun platformShaper(): Shaper

/** The planned platform stacks, each HarfBuzz underneath. */
enum class ShapingStack(
    val description: String,
) {
    ANDROID_TEXT_RUN_SHAPER("android.graphics.text.TextRunShaper, HarfBuzz inside Android 12+ (API 31)"),
    SKIKO_SHAPER("Skiko's Shaper, Skia's SkShaper backed by HarfBuzz, on the JVM desktop"),
    BROWSER_FONT_FACE("the browser's text engine, with the font loaded through the FontFace API"),
}

/** A compiled font (TTF or OTF bytes) to shape with. */
class ShapeFont(
    val bytes: ByteArray,
    val unitsPerEm: Int,
)

/** Text to shape and how. */
class ShapeRequest(
    val text: String,
    val font: ShapeFont,
    /** ISO 15924 script code such as "Deva"; null lets the stack detect it. */
    val script: String? = null,
    /** BCP 47 language tag such as "hi"; null lets the stack choose. */
    val language: String? = null,
    val rightToLeft: Boolean = false,
    /** OpenType feature settings, such as "liga" to 0. */
    val features: Map<String, Int> = emptyMap(),
)

/** One glyph placed by the shaper, in font units, y up. */
data class PositionedGlyph(
    val glyphId: Int,
    val x: Float,
    val y: Float,
)

/** The text range [textStart, textEnd) that produced the glyphs [glyphStart, glyphEnd). */
data class GlyphCluster(
    val textStart: Int,
    val textEnd: Int,
    val glyphStart: Int,
    val glyphEnd: Int,
)

/** A shaped run. [clusters] is null when the platform stack withholds cluster data. */
data class ShapedRun(
    val glyphs: List<PositionedGlyph>,
    val clusters: List<GlyphCluster>?,
)

/** The outcome of [Shaper.shape]. */
sealed interface ShapeResult {
    data class Shaped(
        val run: ShapedRun,
        val stack: ShapingStack,
    ) : ShapeResult

    /** The shaper is a stub: it shaped nothing and says what it will do. */
    data class NotImplemented(
        val stack: ShapingStack,
        val planned: String,
    ) : ShapeResult
}
