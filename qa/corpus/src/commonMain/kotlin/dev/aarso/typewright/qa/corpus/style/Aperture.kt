package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.geometry.Glyph

/**
 * Aperture openness (brief 8.4: "the gap width of the opening relative to x-height or counter
 * size"), on `c`, `e` or `s`: [narrowestThroat]'s (`Geometry2D.kt`) width on the glyph's outer
 * contour, divided by [xHeight]. `null` if the glyph has no outer contour, `xHeight` is not
 * positive, or no throat was found at all (an outline with fewer usable sample points than
 * [narrowestThroat] needs).
 *
 * Known limitation (law 5, "our heuristic"): in a very high-contrast face, a hairline's own
 * thinnest point can be narrower than the true aperture and win this search instead, since
 * nothing here distinguishes "two points across a counter" from "two points across a thin
 * stroke" beyond [narrowestThroat]'s topological-separation filter. [extractFeatures] mitigates
 * this only by averaging over every one of `c`/`e`/`s` that is present, not by detecting the
 * failure directly.
 *
 * Public (P6, `ui`'s Anatomy Lens): [dev.aarso.typewright.ui.learn.AnatomyLensData] wires this
 * function to the "aperture" lens term on the user's own `c`/`e`/`s`, so it has to cross the
 * `:qa:corpus` module boundary -- see that file's KDoc for the rest of the wiring.
 */
fun apertureOpenness(
    glyph: Glyph,
    xHeight: Double,
): Double? {
    if (xHeight <= 0.0) return null
    val outer = glyph.outerContour() ?: return null
    val throat = narrowestThroat(glyph, outer) ?: return null
    return throat.width / xHeight
}
