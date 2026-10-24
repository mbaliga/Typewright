// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.project.Master
import com.asoc.typewright.ui.project.ProjectFont

/** Small synthetic fonts for Space's pure tests: letters that are boxes, so every sidebearing is a number a test can write down. */
internal object SpaceFixtures {
    /** A glyph named [name] for [char] with a box of ink from [inkLeft] to [inkRight] (nothing when [drawn] is false), [advance] wide. */
    fun glyph(
        name: String,
        char: Char?,
        advance: Int,
        inkLeft: Int = 50,
        inkRight: Int = advance - 50,
        drawn: Boolean = true,
    ): Glyph =
        Glyph(
            name = name,
            advanceWidth = advance,
            contours = if (drawn) listOf(box(inkLeft, 0, inkRight, 500)) else emptyList(),
            unicodes = listOfNotNull(char?.code),
        )

    fun box(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Contour =
        Contour(
            listOf(
                ContourPoint(Point(x0, y0), onCurve = true),
                ContourPoint(Point(x1, y0), onCurve = true),
                ContourPoint(Point(x1, y1), onCurve = true),
                ContourPoint(Point(x0, y1), onCurve = true),
            ),
            CurveFormat.QUADRATIC,
        )

    fun ufo(
        glyphs: List<Glyph>,
        kerning: UfoKerning = UfoKerning(),
    ): UfoProject =
        UfoProject(
            fontInfo =
                UfoFontInfo(
                    familyName = "Boxes",
                    styleName = "Regular",
                    unitsPerEm = 1000,
                    ascender = 800,
                    descender = -200,
                    xHeight = 500,
                    capHeight = 700,
                ),
            glyphs = glyphs,
            kerningInfo = kerning,
        )

    fun master(
        glyphs: List<Glyph>,
        kerning: UfoKerning = UfoKerning(),
    ): Master = Master("regular", "Boxes-Regular.ufo", "Regular", isDefault = true, ufo = ufo(glyphs, kerning))

    fun font(
        glyphs: List<Glyph>,
        kerning: UfoKerning = UfoKerning(),
    ): ProjectFont = ProjectFont(master(glyphs, kerning))

    /** n, o, H, O as boxes: n and o 500 and 520 wide, H and O 700 and 760. */
    fun noho(kerning: UfoKerning = UfoKerning()): ProjectFont =
        font(
            listOf(
                glyph("n", 'n', 500, 46, 449),
                glyph("o", 'o', 520, 47, 472),
                glyph("H", 'H', 700, 62, 638),
                glyph("O", 'O', 760, 47, 713),
                glyph("space", ' ', 250, drawn = false),
            ),
            kerning,
        )
}
