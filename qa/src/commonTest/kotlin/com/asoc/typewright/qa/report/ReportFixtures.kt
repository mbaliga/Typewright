// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.qa.corpus.FamilyEntry
import com.asoc.typewright.qa.corpus.GlyphDist
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyPack
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.qa.corpus.StyleClass

/** Small, exact fixtures for the report's tests: a clean font that passes all fourteen checks, and the corpus it is judged by. */
internal object ReportFixtures {
    const val STYLE_KEY = "sans-geometric"
    val style = CheckStyle(STYLE_KEY, "geometric sans", heuristic = false)

    fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    fun quadratic(vararg points: ContourPoint) = Contour(points.toList(), CurveFormat.QUADRATIC)

    /** A quadratic polygon through [points] (every point on the curve). */
    fun polygon(vararg points: Pair<Int, Int>) = quadratic(*points.map { on(it.first, it.second) }.toTypedArray())

    fun glyph(
        name: String,
        vararg contours: Contour,
        unicodes: List<Int> = emptyList(),
        anchors: List<Anchor> = emptyList(),
    ) = Glyph(name, 600, contours.toList(), anchors, unicodes = unicodes)

    private val top = listOf(Anchor("top", Point(250, 700)))

    /** A T of eight points on the baseline and the cap height at 700, with a top anchor. */
    val cleanT: Glyph =
        glyph(
            "T",
            polygon(200 to 0, 200 to 620, 20 to 620, 20 to 700, 480 to 700, 480 to 620, 300 to 620, 300 to 0),
            unicodes = listOf(0x54),
            anchors = top,
        )

    /** An o whose four quadratic arcs reach their extremes on points, with 10 units of overshoot top and bottom. */
    val cleanO: Glyph =
        glyph(
            "o",
            quadratic(on(250, -10), off(500, -10), on(500, 250), off(500, 510), on(250, 510), off(0, 510), on(0, 250), off(0, -10)),
            unicodes = listOf(0x6F),
            anchors = top,
        )

    val cleanGlyphs: List<Glyph> = listOf(cleanT, cleanO)

    /** Vertical metrics at 127% of the em, equal in hhea and typo, no line gap, bit 7 set, win metrics that cover the ink. */
    val cleanInfo =
        UfoFontInfo(
            familyName = "Fixture Sans",
            styleName = "Regular",
            unitsPerEm = 1000,
            ascender = 1010,
            descender = -260,
            xHeight = 500,
            capHeight = 700,
            copyright = "Copyright 2026 The Fixture Sans Project Authors (https://example.org/fixture)",
            openTypeNameLicense =
                "This Font Software is licensed under the SIL Open Font License, Version 1.1. " +
                    "This license is available with a FAQ at: https://openfontlicense.org",
            openTypeNameLicenseURL = "https://openfontlicense.org",
            openTypeHheaAscender = 1010,
            openTypeHheaDescender = -260,
            openTypeHheaLineGap = 0,
            openTypeOS2TypoAscender = 1010,
            openTypeOS2TypoDescender = -260,
            openTypeOS2TypoLineGap = 0,
            openTypeOS2WinAscent = 1010,
            openTypeOS2WinDescent = 260,
            other = listOf("openTypeOS2Selection" to PlistValue.PArray(listOf(PlistValue.PInteger(7)))),
        )

    fun project(
        glyphs: List<Glyph> = cleanGlyphs,
        info: UfoFontInfo = cleanInfo,
    ) = UfoProject(info, glyphs)

    private fun box(
        min: Double,
        q1: Double,
        med: Double,
        q3: Double,
        max: Double,
    ) = Quartiles(min, q1, med, q3, max, n = 4)

    /** Four geometric sans faces: a T of 8 to 12.5 points (at most 28), an o of 20 to 100, and an H with no curves. */
    val corpus: NodeEconomyCorpus =
        NodeEconomyCorpus(
            NodeEconomyPack(
                source = "test",
                glyphs = "HTone",
                styles =
                    mapOf(
                        STYLE_KEY to
                            StyleClass(
                                tags = listOf("/Sans/Geometric"),
                                families =
                                    listOf("Alpha", "Beta", "Gamma", "Delta").mapIndexed { i, name ->
                                        FamilyEntry(
                                            name,
                                            "$name.ttf",
                                            "quadratic",
                                            90.0,
                                            mapOf(
                                                "T" to listOf(listOf(8, 8, 9, 28)[i], 0, 1),
                                                "o" to listOf(listOf(20, 24, 24, 100)[i], 24, 2),
                                                "H" to listOf(12, 0, 1),
                                                "e" to listOf(26, 22, 2),
                                                "n" to listOf(19, 10, 1),
                                            ),
                                        )
                                    },
                                dist =
                                    mapOf(
                                        "T" to GlyphDist(on = box(8.0, 8.0, 8.0, 12.5, 28.0), off = box(0.0, 0.0, 0.0, 0.0, 16.0)),
                                        "o" to GlyphDist(on = box(20.0, 23.25, 24.0, 24.0, 100.0), off = box(0.0, 23.0, 24.0, 24.0, 100.0)),
                                        "H" to GlyphDist(on = box(12.0, 12.0, 12.0, 18.0, 44.0), off = box(0.0, 0.0, 0.0, 0.0, 29.0)),
                                        "e" to GlyphDist(on = box(20.0, 24.0, 26.5, 30.0, 48.0), off = box(0.0, 20.0, 22.0, 25.0, 36.0)),
                                        "n" to GlyphDist(on = box(8.0, 17.0, 19.0, 22.25, 38.0), off = box(0.0, 8.0, 10.0, 11.0, 27.0)),
                                    ),
                            ),
                    ),
            ),
        )

    /** A project that began in Typewright and was not imported from a compiled font. */
    val drawnHere = ImportProvenance(fromCompiledFont = false, untouchedGlyphs = 0, outlineGlyphs = 2)

    fun inputs(
        glyphs: List<Glyph> = cleanGlyphs,
        info: UfoFontInfo = cleanInfo,
        style: CheckStyle? = this.style,
        corpus: NodeEconomyCorpus? = this.corpus,
        provenance: ImportProvenance? = drawnHere,
        otherMasters: List<MasterNames> = emptyList(),
    ) = CheckInputs(project(glyphs, info), style, corpus, provenance, otherMasters)

    fun report(
        glyphs: List<Glyph> = cleanGlyphs,
        info: UfoFontInfo = cleanInfo,
        style: CheckStyle? = this.style,
        corpus: NodeEconomyCorpus? = this.corpus,
        provenance: ImportProvenance? = drawnHere,
        otherMasters: List<MasterNames> = emptyList(),
    ) = buildCheckReport(inputs(glyphs, info, style, corpus, provenance, otherMasters))

    /** A polygon with [points] on-curve points around a big rectangle, as a tracer would leave: many small steps, none off the curve. */
    fun tracedRectangle(points: Int): Contour {
        val perSide = points / 4
        val pts = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until perSide) pts += (i * 3) to 0
        for (i in 0 until perSide) pts += (perSide * 3) to (i * 3)
        for (i in 0 until perSide) pts += (perSide * 3 - i * 3) to (perSide * 3)
        for (i in 0 until perSide) pts += 0 to (perSide * 3 - i * 3)
        return polygon(*pts.toTypedArray())
    }
}
