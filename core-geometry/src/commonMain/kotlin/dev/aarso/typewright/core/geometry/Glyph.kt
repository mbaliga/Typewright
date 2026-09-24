package dev.aarso.typewright.core.geometry

/**
 * A named attachment point on a [Glyph] (UFO 3 `<anchor name="…" x="…" y="…"/>`), used for mark
 * positioning: a base letter carries `top`/`bottom`/`ogonek`, a combining mark the matching
 * `_top`/`_bottom`/`_ogonek` (docs/RESEARCH_font_quality.md, "Diacritics"). This lives in
 * `core-geometry` rather than `core-font`, even though only the UFO side reads and writes anchors
 * today, because it is referenced by [Glyph] itself: [Glyph] is the one shared representation
 * between the quadratic (TrueType) and cubic (UFO) worlds (see `CoreGeometryModule`'s KDoc), and
 * `core-font` already depends on `core-geometry`, never the reverse, so a type [Glyph] carries has
 * to live on this side of that dependency. An anchor's [point] uses the same integer, font-unit
 * [Point] every on-curve coordinate does; unlike a [ContourPoint] it is never off-curve and never
 * part of a [Contour]'s outline.
 */
data class Anchor(
    val name: String,
    val point: Point,
)

/**
 * One glyph: a name, its advance width in font units, its outline as a list of [Contour]s, and
 * its mark-attachment [anchors]. All contours share one [CurveFormat] in practice (a glyph is
 * read from one font, which is either all-quadratic or all-cubic), but that is a convention of
 * the readers that build a [Glyph], not a constraint this type enforces. [anchors] defaults to
 * empty so every existing caller that builds a [Glyph] positionally with just (name, advanceWidth,
 * contours) keeps compiling unchanged; a TrueType [Glyph] (`core-font`'s sfnt reader has no GDEF
 * anchor support yet) is always built with the default, honestly empty, list.
 */
data class Glyph(
    val name: String,
    val advanceWidth: Int,
    val contours: List<Contour>,
    val anchors: List<Anchor> = emptyList(),
)
