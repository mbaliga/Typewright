// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

import dev.aarso.typewright.core.font.sfnt.readSfntFont
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P2a's honesty check (CLAUDE.md "When unsure: stop and report rather than guess"; this task's
 * shared instructions, "Honesty rule"): runs the exact same, glyph-agnostic
 * [fitClosedContourToCubics]/[fitGlyphContoursToCubics] this module's other tests exercise on
 * synthetic shapes against the *real* `fonts/HyleDeco-Regular.ttf` `T`/`o`/`n`/`H` glyphs, and
 * reports what actually comes out next to `TYPEWRIGHT_BUILD_BRIEF.md` section 7's fitted targets
 * (T 8*0, o 16*16, n 14*8, H 12*0 -- on-curve*off-curve).
 *
 * **The dense-polyline input, exactly as this task's shared instructions specify.** All four
 * glyphs are, per `HyleDecoCrossCheckTest`, pure polygons (0 off-curve points anywhere in this
 * font): so for these four glyphs specifically, "the dense polyline this corner-detect+fit
 * pipeline consumes" is simply each contour's own sequence of on-curve [Point]s, read through
 * core-font's [readSfntFont], with the (here, redundant) `onCurve` tag dropped -- see
 * [Contour.points]. This is a legitimate, realistic input for a raster-trace-style dense polyline
 * (a P3 capture/trace chain, not yet built, would hand this same fitter exactly this shape of
 * input), never a shortcut built around these four glyphs: [fitClosedContourToCubics] itself has
 * no idea these points came from a font at all.
 *
 * **This test does not assert an exact fixture match**, on purpose. It asserts only structural
 * sanity (the output is a valid CUBIC contour, with the same number of contours as the source
 * glyph) and prints the actual counts and the fitted contours' maximum deviation from the source
 * polygon, so a reader gets the honest, reproducible answer either way -- following the same
 * pattern P1b's style detector already set (`qa/corpus`'s `StyleDetectorRealFontValidationTest`
 * and `docs/OPEN_QUESTIONS.md` item 19: "8/19 correct top-1 (42%)", reported plainly rather than
 * padded). It is JVM-only for the same reason `StyleDetectorRealFontValidationTest` is (see this
 * module's `build.gradle.kts`): reading an arbitrary file off disk is not something Kotlin/Wasm
 * under Node can do reliably from a `commonTest`/`jvmTest`-style relative path during a Gradle
 * test run.
 */
class CubicFittingHyleDecoValidationTest {
    private val font by lazy { readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes()) }

    @Test
    fun capitalT() = reportOneGlyph("T", codePoint = 'T'.code, targetOnCurve = 8, targetOffCurve = 0)

    @Test
    fun lowercaseO() = reportOneGlyph("o", codePoint = 'o'.code, targetOnCurve = 16, targetOffCurve = 16)

    @Test
    fun lowercaseN() = reportOneGlyph("n", codePoint = 'n'.code, targetOnCurve = 14, targetOffCurve = 8)

    @Test
    fun capitalH() = reportOneGlyph("H", codePoint = 'H'.code, targetOnCurve = 12, targetOffCurve = 0)

    private fun reportOneGlyph(
        label: String,
        codePoint: Int,
        targetOnCurve: Int,
        targetOffCurve: Int,
    ) {
        val glyph = font.glyphForCodePoint(codePoint) ?: error("no glyph mapped for '$label'")
        val sourcePolylines = glyph.contours.map { contour -> contour.points.map { it.point } }

        val fitted = fitGlyphContoursToCubics(sourcePolylines)
        assertEquals(glyph.contours.size, fitted.size, "'$label': contour count must be preserved")
        for (contour in fitted) assertEquals(CurveFormat.CUBIC, contour.format)

        val totalOn = fitted.sumOf { it.count().onCurveEquivalent }
        val totalOff = fitted.sumOf { it.count().offCurve }
        val maxDeviation =
            fitted.indices.maxOf { i -> maxDeviationFromSourcePolygon(fitted[i], sourcePolylines[i]) }

        println(
            "P2a Hyle Deco '$label': source ${glyph.count().onCurveEquivalent} on-curve / " +
                "${glyph.contours.size} contour(s) -> fitted $totalOn on-curve / $totalOff off-curve " +
                "(target $targetOnCurve/$targetOffCurve), max deviation from source polygon " +
                "$maxDeviation units",
        )

        // Structural sanity only (see this class's KDoc for why an exact fixture match is not
        // asserted here): every contour must have come out as a well-formed, non-trivial CUBIC
        // contour, and the fitted curve must actually track the source polygon reasonably closely
        // rather than, say, degenerating to a near-straight line across the whole glyph.
        assertTrue(totalOn >= fitted.size, "'$label': every fitted contour must have at least 1 on-curve anchor")
        assertTrue(maxDeviation < 50.0, "'$label': fitted contour deviates implausibly far ($maxDeviation units) from the source polygon")
    }
}

/**
 * The maximum, over a dense sampling of every fitted segment, of the distance to the *nearest
 * edge* of [sourcePolygon] (its consecutive-point chords, cyclic -- not the nearest *vertex*:
 * Hyle Deco's own polygons are not uniformly dense, and a long straight run some of them encode
 * with only its 2 endpoints would otherwise look like a huge, entirely spurious "deviation" at its
 * own midpoint even when the fitted curve sits exactly on top of it). Test-only diagnostic, not
 * this fitter's own error metric (which is [computeMaxError], measured against the actual input
 * points it was fitting, not a nearest-edge search).
 */
private fun maxDeviationFromSourcePolygon(
    contour: Contour,
    sourcePolygon: List<Point>,
): Double {
    if (sourcePolygon.size < 2) return 0.0
    val sourceVecs = sourcePolygon.map { it.toVec2() }
    var maxDeviation = 0.0
    for (segment in contour.segments()) {
        for (s in 0..20) {
            val t = s / 20.0
            val p = segment.pointAt(t)
            val nearest =
                (0 until sourceVecs.size).minOf { i ->
                    val a = sourceVecs[i]
                    val b = sourceVecs[(i + 1) % sourceVecs.size]
                    distanceToSegment(p, a, b)
                }
            if (nearest > maxDeviation) maxDeviation = nearest
        }
    }
    return maxDeviation
}

/** The Euclidean distance from [p] to the closest point on the line segment `a -> b`. */
private fun distanceToSegment(
    p: Vec2,
    a: Vec2,
    b: Vec2,
): Double {
    val ab = b - a
    val lengthSquared = ab.dot(ab)
    if (lengthSquared == 0.0) return (p - a).length()
    val t = ((p - a).dot(ab) / lengthSquared).coerceIn(0.0, 1.0)
    val closest = a + ab * t
    return (p - closest).length()
}
