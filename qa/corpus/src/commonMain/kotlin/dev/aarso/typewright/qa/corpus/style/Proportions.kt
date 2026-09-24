package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.geometry.Glyph

/**
 * x-height to cap-height ratio (brief 8.4): "from `UfoFontInfo` if present, else derived from `x`
 * and `H` ink extents". `core-font`'s `UfoFontInfo` does not model `xHeight`/`capHeight` yet (see
 * its KDoc), so this package always takes the "else" branch -- `x`'s and `H`'s own ink-bounds top
 * ([Bounds.maxY], assuming a baseline at `y = 0`, the font-unit convention CLAUDE.md states).
 * `null` if either glyph is missing or `H`'s ink height is not positive.
 */
internal fun xHeightToCapHeightRatio(
    x: Glyph?,
    capH: Glyph?,
): Double? {
    val xHeight = x?.inkBounds()?.maxY ?: return null
    val capHeight = capH?.inkBounds()?.maxY ?: return null
    if (capHeight <= 0.0) return null
    return xHeight / capHeight
}

/**
 * Width class (brief 8.4: "average advance width relative to a reference"): the mean advance
 * width of every glyph present in [glyphs], divided by [unitsPerEm] -- a proportion, comparable
 * across fonts regardless of units-per-em, higher meaning wider on average. `null` if [glyphs] is
 * empty or [unitsPerEm] is not positive.
 */
internal fun widthClass(
    glyphs: Collection<Glyph>,
    unitsPerEm: Int,
): Double? {
    if (glyphs.isEmpty() || unitsPerEm <= 0) return null
    val mean = glyphs.sumOf { it.advanceWidth } / glyphs.size.toDouble()
    return mean / unitsPerEm
}
