// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.decodeStyleAtlas

/**
 * A small hand-written style atlas: three genres with known, round numbers, so a test can say
 * exactly which range an answer should produce. The real atlas is exercised in jvmTest, where
 * its resource is on the classpath.
 *
 * - sans-geometric: six monoline faces (contrast 1.0 to 1.25), four with a one-storey a, all but
 *   one with a centred bar (G1's sits low, at 0.45).
 * - serif-garalde: six contrasted, bracketed faces (contrast 2.0 to 3.0, bracket 0.4), more open
 *   than the rest (aperture 0.35 against 0.25).
 * - display-artdeco: five heavy faces, three with a high bar (0.7), one centred, one low (0.35).
 * - feelings: "calm" is three garalde faces, "loud" two deco ones.
 */
internal val FIXTURE_ATLAS: StyleAtlas by lazy {
    fun face(
        classes: String,
        contrast: Double,
        serif: String,
        a: String,
        crossbar: Double,
        width: Double,
        bracket: Double? = null,
        aperture: Double = 0.25,
        roundness: Double = 2.1,
        stem: Double = 0.13,
    ): String {
        val b = bracket?.let { ""","bracket":$it""" } ?: ""
        return """{"classes":["$classes"],"contrast":$contrast,"stress":0,"aperture":$aperture,"roundness":$roundness,""" +
            """"xHeight":0.72,"width":$width,"stem":$stem,"crossbar":$crossbar,"serif":"$serif","aStoreys":"$a",""" +
            """"gStoreys":"single","terminal":"flat"$b}"""
    }
    val faces =
        listOf(
            "G1" to face("sans-geometric", 1.0, "no", "single", 0.45, 0.58),
            "G2" to face("sans-geometric", 1.05, "no", "single", 0.51, 0.58),
            "G3" to face("sans-geometric", 1.1, "no", "single", 0.51, 0.60),
            "G4" to face("sans-geometric", 1.15, "no", "single", 0.52, 0.60),
            "G5" to face("sans-geometric", 1.2, "no", "double", 0.51, 0.56),
            "G6" to face("sans-geometric", 1.25, "no", "double", 0.50, 0.56),
            "S1" to face("serif-garalde", 2.0, "yes", "double", 0.50, 0.55, bracket = 0.4, aperture = 0.35),
            "S2" to face("serif-garalde", 2.2, "yes", "double", 0.51, 0.55, bracket = 0.4, aperture = 0.35),
            "S3" to face("serif-garalde", 2.4, "yes", "double", 0.51, 0.55, bracket = 0.4, aperture = 0.35),
            "S4" to face("serif-garalde", 2.6, "yes", "double", 0.52, 0.55, bracket = 0.4, aperture = 0.35),
            "S5" to face("serif-garalde", 2.8, "yes", "double", 0.51, 0.55, bracket = 0.4, aperture = 0.35),
            "S6" to face("serif-garalde", 3.0, "yes", "double", 0.50, 0.55, bracket = 0.4, aperture = 0.35),
            "D1" to face("display-artdeco", 1.1, "no", "double", 0.70, 0.40, stem = 0.25),
            "D2" to face("display-artdeco", 1.2, "no", "double", 0.70, 0.42, stem = 0.25),
            "D3" to face("display-artdeco", 4.0, "no", "double", 0.70, 0.80, stem = 0.25),
            "D4" to face("display-artdeco", 1.1, "no", "double", 0.50, 0.85, stem = 0.25),
            "D5" to face("display-artdeco", 1.3, "no", "single", 0.35, 0.40, stem = 0.25),
        )

    fun group(prefix: String) = faces.filter { it.first.startsWith(prefix) }.joinToString(",", "[", "]") { "\"${it.first}\"" }
    val json =
        """
        {"source":"fixture","numeric":["contrast","stress","bracket","aperture","roundness","xHeight","width","stem","crossbar"],
         "categorical":["serif","aStoreys","gStoreys","terminal"],
         "classes":{
           "sans-geometric":{"corpus":true,"faces":${group("G")},"n":6},
           "serif-garalde":{"corpus":true,"faces":${group("S")},"n":6},
           "display-artdeco":{"corpus":true,"faces":${group("D")},"n":5}},
         "feelings":{
           "calm":{"faces":[{"family":"S1","score":90},{"family":"S2","score":80},{"family":"S3","score":70}],"n":3},
           "loud":{"faces":[{"family":"D3","score":90},{"family":"D4","score":80}],"n":2}},
         "faces":{${faces.joinToString(",") { "\"${it.first}\":${it.second}" }}}}
        """.trimIndent()
    StyleAtlas(decodeStyleAtlas(json))
}

/** A straight-sided outline, all on-curve. */
internal fun polygon(vararg pts: Pair<Int, Int>): Contour =
    Contour(pts.map { (x, y) -> ContourPoint(Point(x, y), onCurve = true) }, CurveFormat.QUADRATIC)

/** A rectangle from ([x0], [y0]) to ([x1], [y1]), wound counter-clockwise. */
internal fun rect(
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
): Contour = polygon(x0 to y0, x1 to y0, x1 to y1, x0 to y1)

/** A glyph of one rectangle: an I, a 1, a bar. */
internal fun barGlyph(
    name: String,
    advance: Int,
    width: Int,
    height: Int,
): Glyph = Glyph(name, advance, listOf(rect(0, 0, width, height)))

/** A square ring standing in for O or 0: [outer] wide and tall, walls [wall] thick. */
internal fun ringGlyph(
    name: String,
    advance: Int,
    outer: Int,
    wall: Int,
): Glyph =
    Glyph(
        name,
        advance,
        listOf(
            rect(0, 0, outer, outer),
            polygon(wall to wall, wall to outer - wall, outer - wall to outer - wall, outer - wall to wall),
        ),
    )
