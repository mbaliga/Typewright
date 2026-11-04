// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.StyleClass
import kotlin.math.roundToInt

/**
 * S-clean: a font that is clean on purpose, for the golden path's Ship step, which needs a project Check has nothing against: Hyle Deco (S-ttf)
 * is a traced font and Check fails it, rightly, so it can only ever be the failing seed.
 *
 * "Seed Boxes" is built here from rectangles and ellipses, one glyph for each of the 319 characters of Google Fonts' `GF_Latin_Core` (the
 * smallest set it onboards a family with, from the `glyphsets` package in `tools/requirements-golden-path.txt`), so that the compiled font also
 * clears the Google Fonts profile's coverage check. Every letter and figure is an ellipse of four cubic arcs (Check fails a font whose curved
 * letters are polygons, and which letters are curved is the corpus's to say, so none is left straight), the other marks have four points,
 * widths vary so the font is not read as monospaced, the base letters carry a `top` anchor, and the vertical metrics follow Google Fonts'
 * schema. It states every
 * fact a repository folder needs (names, version, designer, copyright with its address, the Open Font License, a vendor ID), so that nothing in
 * the folder is left out. The designer and the address are made up for this seed, which is the one place that is allowed: a fixture, in a test.
 */
object CleanSeed {
    const val FAMILY = "Seed Boxes"
    const val FILE_STEM = "SeedBoxes-Regular"
    const val COPYRIGHT = "Copyright 2026 The Seed Boxes Project Authors (https://github.com/example/seed-boxes)"
    const val DESIGNER = "A. Seed Designer"

    /** `GF_Latin_Core` (glyphsets 1.0.0) as inclusive ranges of code points, in hexadecimal. */
    private const val CORE_RANGES =
        "0020-007E 00A0-00A3 00A5 00A7-00AB 00AE-00B0 00B4 00B6-00B8 00BA-00BB 00BF-0107 010A-0113 0116-011B 011E-0123 0126-0127 " +
            "012A-012B 012E-0131 0136-0137 0139-013E 0141-0148 0150-0155 0158-015B 015E-0161 0164-0165 016A-016B 016E-017E 0218-021B " +
            "0237 02C6-02C7 02D8-02DD 0300-0304 0306-0308 030A-030C 0326-0328 1E80-1E85 1E9E 1EF2-1EF3 2013-2014 2018-201A 201C-201E " +
            "2022 2026 2039-203A 20AC 2122 2212"

    /** Every code point of `GF_Latin_Core`, ascending. */
    val codepoints: List<Int> =
        CORE_RANGES.split(' ').flatMap { range ->
            val (from, to) = range.split('-').let { it[0] to (it.getOrNull(1) ?: it[0]) }
            (from.toInt(16)..to.toInt(16)).toList()
        }

    private const val UNITS_PER_EM = 1000
    private const val CAP_HEIGHT = 700
    private const val X_HEIGHT = 500
    private const val ASCENT = 720
    private const val DESCENT = -200
    private const val HHEA_ASCENDER = 940
    private const val HHEA_DESCENDER = -300

    /** The project's UFO: a font info that states everything, and one glyph per code point plus `.notdef`. */
    fun ufo(): UfoProject {
        val info =
            UfoFontInfo(
                familyName = FAMILY,
                styleName = "Regular",
                unitsPerEm = UNITS_PER_EM,
                ascender = HHEA_ASCENDER,
                descender = HHEA_DESCENDER,
                xHeight = X_HEIGHT,
                capHeight = CAP_HEIGHT,
                versionMajor = 1,
                versionMinor = 0,
                copyright = COPYRIGHT,
                openTypeNameDesigner = DESIGNER,
                openTypeNameLicense =
                    "This Font Software is licensed under the SIL Open Font License, Version 1.1. " +
                        "This license is available with a FAQ at: https://openfontlicense.org",
                openTypeNameLicenseURL = "https://openfontlicense.org",
                postscriptFontName = FILE_STEM,
                openTypeOS2VendorID = "SEED",
                openTypeHheaAscender = HHEA_ASCENDER,
                openTypeHheaDescender = HHEA_DESCENDER,
                openTypeHheaLineGap = 0,
                openTypeOS2TypoAscender = HHEA_ASCENDER,
                openTypeOS2TypoDescender = HHEA_DESCENDER,
                openTypeOS2TypoLineGap = 0,
                openTypeOS2WinAscent = HHEA_ASCENDER,
                openTypeOS2WinDescent = -HHEA_DESCENDER,
                other =
                    listOf(
                        "openTypeOS2WeightClass" to PlistValue.PInteger(400),
                        "openTypeOS2Selection" to PlistValue.PArray(listOf(PlistValue.PInteger(7))),
                    ),
            )
        return UfoProject(info, listOf(notdef()) + codepoints.map(::glyphFor))
    }

    /** A new project made from [ufo], as the Brief's "start from blank" makes one, with the class declared so Check has a box to judge it by. */
    fun spec(): NewProjectSpec =
        NewProjectSpec(
            name = FAMILY,
            brief = Brief(source = BriefSource.BLANK, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
            scripts = listOf("Latn"),
            masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = ufo())),
        )

    private fun notdef(): Glyph = Glyph(".notdef", 500, listOf(rectangle(50, 0, 450, CAP_HEIGHT), hole(100, 50, 400, 650)))

    private fun glyphFor(codepoint: Int): Glyph {
        val name = nameOf(codepoint)
        val category = Character.getType(codepoint)
        val unicodes = listOf(codepoint)
        return when {
            category == Character.SPACE_SEPARATOR.toInt() -> {
                Glyph(name, 250, emptyList(), unicodes = unicodes)
            }

            category == Character.NON_SPACING_MARK.toInt() -> {
                Glyph(name, 0, listOf(rectangle(-200, 600, 0, 660)), unicodes = unicodes)
            }

            category == Character.MODIFIER_LETTER.toInt() || category == Character.MODIFIER_SYMBOL.toInt() -> {
                Glyph(name, 300, listOf(rectangle(60, 600, 240, 660)), unicodes = unicodes)
            }

            else -> {
                val base = baseOf(codepoint)
                val width = widthOf(base, category)
                val contours =
                    when {
                        category == Character.UPPERCASE_LETTER.toInt() -> {
                            val box = Box(50, 0, width - 50, CAP_HEIGHT)
                            listOf(ellipse(box))
                        }

                        category == Character.LOWERCASE_LETTER.toInt() -> {
                            val bottom = if (base in DESCENDERS) DESCENT else 0
                            val top = if (base in ASCENDERS) ASCENT else X_HEIGHT
                            val box = Box(50, bottom, width - 50, top)
                            listOf(ellipse(box))
                        }

                        category == Character.DECIMAL_DIGIT_NUMBER.toInt() -> {
                            listOf(ellipse(Box(50, 0, width - 50, CAP_HEIGHT)))
                        }

                        else -> {
                            listOf(rectangle(Box(100, 200, width - 100, 400)))
                        }
                    }
                val letter = category == Character.UPPERCASE_LETTER.toInt() || category == Character.LOWERCASE_LETTER.toInt()
                val anchors =
                    if (letter) {
                        listOf(
                            Anchor(
                                "top",
                                Point(
                                    width / 2,
                                    if (category ==
                                        Character.UPPERCASE_LETTER.toInt()
                                    ) {
                                        CAP_HEIGHT
                                    } else {
                                        X_HEIGHT
                                    },
                                ),
                            ),
                        )
                    } else {
                        emptyList()
                    }
                Glyph(name, width, contours, anchors, unicodes = unicodes)
            }
        }
    }

    /** `A`..`Z` and `a`..`z` are named as the letter, which is how the node-economy boxes and the anchor check find them; the rest are `uniXXXX`. */
    private fun nameOf(codepoint: Int): String =
        if (codepoint in 0x41..0x5A ||
            codepoint in 0x61..0x7A
        ) {
            codepoint.toChar().toString()
        } else {
            "uni" + codepoint.toString(16).uppercase().padStart(4, '0')
        }

    /** The base letter of an accented one: the first letter of its canonical decomposition, which Java gives through [java.text.Normalizer]. */
    private fun baseOf(codepoint: Int): Char =
        java.text.Normalizer.normalize(String(Character.toChars(codepoint)), java.text.Normalizer.Form.NFD)[0]

    private fun widthOf(
        base: Char,
        category: Int,
    ): Int =
        when {
            base in NARROW -> 300

            base in WIDE -> 800

            category != Character.UPPERCASE_LETTER.toInt() && category != Character.LOWERCASE_LETTER.toInt() &&
                category != Character.DECIMAL_DIGIT_NUMBER.toInt() -> 420

            else -> 560
        }

    private const val DESCENDERS = "gjpqy"
    private const val ASCENDERS = "bdfhklt"
    private const val NARROW = "ilj1.,:;'|!"
    private const val WIDE = "mwMW"

    private class Box(
        val left: Int,
        val bottom: Int,
        val right: Int,
        val top: Int,
    )

    private fun rectangle(
        left: Int,
        bottom: Int,
        right: Int,
        top: Int,
    ): Contour = rectangle(Box(left, bottom, right, top))

    /** A rectangle, counter-clockwise, as four straight cubic segments (each a degenerate triple: the point and its two controls on the ends). */
    private fun rectangle(box: Box): Contour {
        val corners =
            listOf(Point(box.left, box.bottom), Point(box.right, box.bottom), Point(box.right, box.top), Point(box.left, box.top))
        return straight(corners)
    }

    /** A rectangle, clockwise: the counter of a letter. */
    private fun hole(
        left: Int,
        bottom: Int,
        right: Int,
        top: Int,
    ): Contour = straight(listOf(Point(left, bottom), Point(left, top), Point(right, top), Point(right, bottom)))

    private fun straight(corners: List<Point>): Contour {
        val points = mutableListOf<ContourPoint>()
        for ((i, start) in corners.withIndex()) {
            val end = corners[(i + 1) % corners.size]
            points += ContourPoint(start, onCurve = true)
            points += ContourPoint(start, onCurve = false)
            points += ContourPoint(end, onCurve = false)
        }
        return Contour(points, CurveFormat.CUBIC)
    }

    /** An ellipse, counter-clockwise from the top, as four cubic arcs. */
    private fun ellipse(box: Box): Contour {
        val k = 0.5523
        val cx = (box.left + box.right) / 2.0
        val cy = (box.bottom + box.top) / 2.0
        val rx = (box.right - box.left) / 2.0
        val ry = (box.top - box.bottom) / 2.0

        fun p(
            x: Double,
            y: Double,
            on: Boolean,
        ) = ContourPoint(Point(x.roundToInt(), y.roundToInt()), on)
        return Contour(
            listOf(
                p(cx, box.top.toDouble(), true),
                p(cx - k * rx, box.top.toDouble(), false),
                p(box.left.toDouble(), cy + k * ry, false),
                p(box.left.toDouble(), cy, true),
                p(box.left.toDouble(), cy - k * ry, false),
                p(cx - k * rx, box.bottom.toDouble(), false),
                p(cx, box.bottom.toDouble(), true),
                p(cx + k * rx, box.bottom.toDouble(), false),
                p(box.right.toDouble(), cy - k * ry, false),
                p(box.right.toDouble(), cy, true),
                p(box.right.toDouble(), cy + k * ry, false),
                p(cx + k * rx, box.top.toDouble(), false),
            ),
            CurveFormat.CUBIC,
        )
    }
}
