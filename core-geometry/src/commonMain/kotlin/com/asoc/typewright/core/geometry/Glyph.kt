// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

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
 * A UFO 3 guideline (unifiedfontobject.org/versions/ufo3): an alignment line at any angle, used
 * either font-wide (`fontinfo.plist`'s `guidelines` list — `core-font`'s `UfoFontInfo.guidelines`)
 * or per-glyph (a `.glif` file's `<guideline>` elements — [Glyph.guidelines]). Both forms share
 * these same six attributes, so one type models both; it lives in `core-geometry` rather than
 * `core-font` for exactly the reason [Anchor] does (see [Glyph]'s KDoc): [Glyph] needs it, and
 * `core-font` depends on `core-geometry`, never the reverse. `core-font`'s `GlifCodec` and
 * `UfoProject` are the only code that reads or writes one; nothing here interprets a guideline
 * (no snapping, no "closest guideline" queries) — that belongs to a future editing-tools module.
 *
 * Per the spec, a guideline is exactly one of three shapes, decided by which of [x]/[y]/[angle] are
 * present:
 * - **vertical**: only [x] is set ([y] and [angle] both `null`) — an infinite vertical line at
 *   that x;
 * - **horizontal**: only [y] is set ([x] and [angle] both `null`) — an infinite horizontal line
 *   at that y;
 * - **angled**: [x], [y] and [angle] are all set — an infinite line through (x, y) at [angle]
 *   degrees counter-clockwise from horizontal, 0–360 ("guides at any angle", brief §10 v1-must-have
 *   item 5).
 *
 * [init] enforces exactly this — at least one of [x]/[y] must be set, and [angle] may only be set
 * when both [x] and [y] are — so a [Guideline] this module ever hands back out, whether built
 * directly or read from a file, is always one of the three valid shapes; a file that violates this
 * is rejected on read with a clear message rather than silently coerced into something else.
 * [name], [color] and [identifier] are optional per the spec and are carried verbatim, unvalidated
 * (the spec has its own syntax for `color` and rules for `name`, but nothing downstream needs them
 * enforced yet); this module gives `color` no meaning of its own — CLAUDE.md law 8's violet/cyan
 * are for live geometry only, never a stored guideline's colour — and `identifier` no generation
 * logic, both simply round-trip.
 */
data class Guideline(
    val x: Double? = null,
    val y: Double? = null,
    val angle: Double? = null,
    val name: String? = null,
    val color: String? = null,
    val identifier: String? = null,
) {
    init {
        require(x != null || y != null) { "a Guideline needs at least one of x or y set" }
        require(angle == null || (x != null && y != null)) {
            "a Guideline's angle can only be set when both x and y are set (found x=$x, y=$y, angle=$angle)"
        }
        require(angle == null || angle in 0.0..360.0) {
            "a Guideline's angle must be between 0 and 360 degrees, found $angle"
        }
    }
}

/**
 * One glyph: a name, its advance width in font units, its outline as a list of [Contour]s, its
 * mark-attachment [anchors], and its local [guidelines] (per-glyph alignment lines; a font's
 * font-wide guidelines are a project-level concept, not a glyph one, so they live instead on
 * `core-font`'s `UfoFontInfo.guidelines`). All contours share one [CurveFormat] in practice (a
 * glyph is read from one font, which is either all-quadratic or all-cubic), but that is a
 * convention of the readers that build a [Glyph], not a constraint this type enforces. [anchors]
 * and [guidelines] both default to empty so every existing caller that builds a [Glyph]
 * positionally with just (name, advanceWidth, contours) keeps compiling unchanged; a TrueType
 * [Glyph] (`core-font`'s sfnt reader has no GDEF anchor support and no guideline concept) is always
 * built with both defaults, honestly empty.
 */
data class Glyph(
    val name: String,
    val advanceWidth: Int,
    val contours: List<Contour>,
    val anchors: List<Anchor> = emptyList(),
    val guidelines: List<Guideline> = emptyList(),
)
