package dev.aarso.typewright.core.geometry

/**
 * One glyph: a name, its advance width in font units, and its outline as a list of [Contour]s.
 * All contours share one [CurveFormat] in practice (a glyph is read from one font, which is
 * either all-quadratic or all-cubic), but that is a convention of the readers that build a
 * [Glyph], not a constraint this type enforces.
 */
data class Glyph(
    val name: String,
    val advanceWidth: Int,
    val contours: List<Contour>,
)
