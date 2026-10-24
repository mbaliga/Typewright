// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.UfoLib
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.direction
import com.asoc.typewright.core.geometry.outlineBounds
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FontmakeSourceTest {
    private fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    /** A TrueType-style O: a clockwise outer ring and a counter-clockwise counter, each with real curved sides. */
    private val quadraticO =
        Glyph(
            name = "O",
            advanceWidth = 600,
            contours =
                listOf(
                    // Outer, clockwise (TrueType's convention), four quadratic arcs through implied midpoints.
                    Contour(
                        listOf(
                            on(300, 700),
                            off(560, 700),
                            off(560, 350),
                            off(560, 0),
                            off(300, 0),
                            off(40, 0),
                            off(40, 350),
                            off(40, 700),
                        ),
                        CurveFormat.QUADRATIC,
                    ),
                    // Inner, counter-clockwise.
                    Contour(
                        listOf(
                            on(300, 600),
                            off(150, 600),
                            off(150, 350),
                            off(150, 100),
                            on(300, 100),
                            off(450, 100),
                            off(450, 350),
                            off(450, 600),
                        ),
                        CurveFormat.QUADRATIC,
                    ),
                ),
            anchors = listOf(Anchor("top", Point(300, 700))),
            unicodes = listOf(0x4F),
        )

    private val quadraticH =
        Glyph(
            name = "H",
            advanceWidth = 640,
            contours =
                listOf(
                    Contour(
                        listOf(
                            on(80, 0),
                            on(80, 700),
                            on(160, 700),
                            on(160, 400),
                            on(480, 400),
                            on(480, 700),
                            on(560, 700),
                            on(560, 0),
                            on(480, 0),
                            on(480, 330),
                            on(160, 330),
                            on(160, 0),
                        ),
                        CurveFormat.QUADRATIC,
                    ),
                ),
            unicodes = listOf(0x48),
        )

    private val cubicX =
        Glyph(
            name = "x",
            advanceWidth = 500,
            contours =
                listOf(
                    Contour(
                        listOf(
                            on(0, 0),
                            off(0, 0),
                            off(250, 250),
                            on(250, 250),
                            off(250, 250),
                            off(500, 0),
                            on(500, 0),
                            off(500, 0),
                            off(0, 0),
                        ),
                        CurveFormat.CUBIC,
                    ),
                ),
            unicodes = listOf(0x78),
        )

    private fun ufo(
        glyphs: List<Glyph>,
        format: CurveFormat,
    ): Map<String, ByteArray> {
        val project =
            UfoProject(
                fontInfo = UfoFontInfo(familyName = "Fixture", styleName = "Regular", unitsPerEm = 1000),
                glyphs = glyphs,
                kerningInfo = UfoKerning(kerning = mapOf("H" to mapOf("O" to -20.0))),
                lib = UfoLib(lineContourFormat = format),
            )
        return writeUfoProject(project).mapValues { it.value.encodeToByteArray() }
    }

    private fun read(files: Map<String, ByteArray>): UfoProject =
        readUfoProject(
            files
                .filterKeys { it.endsWith(".plist") || it.endsWith(".glif") || it.endsWith(".fea") }
                .mapValues { it.value.decodeToString() },
        )

    @Test
    fun quadraticContoursBecomeCubicInTheCopyAndOnlyThere() {
        val original = ufo(listOf(quadraticO, quadraticH, cubicX), CurveFormat.QUADRATIC)
        val snapshot = original.mapValues { it.value.copyOf() }

        val prepared = prepareUfoForFontmake(original)

        assertEquals(listOf("O", "H"), prepared.elevatedGlyphs, "the cubic x is not touched, the two quadratic glyphs are")
        val copy = read(prepared.files)
        for (glyph in copy.glyphs) assertTrue(glyph.contours.all { it.format == CurveFormat.CUBIC }, "${glyph.name} is cubic in the copy")
        // The project's own files are exactly what they were.
        assertEquals(snapshot.keys, original.keys)
        for ((path, bytes) in snapshot) assertContentEquals(bytes, original.getValue(path), "$path was changed in the original")
        assertEquals(
            CurveFormat.QUADRATIC,
            read(original)
                .glyphs
                .first { it.name == "O" }
                .contours[0]
                .format,
        )
    }

    @Test
    fun theCopyKeepsEveryGlyphAdvanceCodePointAnchorAndTheKerning() {
        val original = ufo(listOf(quadraticO, quadraticH, cubicX), CurveFormat.QUADRATIC)
        val before = read(original)
        val after = read(prepareUfoForFontmake(original).files)

        assertEquals(before.glyphs.map { it.name }, after.glyphs.map { it.name })
        for ((b, a) in before.glyphs.zip(after.glyphs)) {
            assertEquals(b.advanceWidth, a.advanceWidth, "advance of ${b.name}")
            assertEquals(b.unicodes, a.unicodes, "code points of ${b.name}")
            assertEquals(b.anchors, a.anchors, "anchors of ${b.name}")
            assertEquals(b.contours.size, a.contours.size, "contours of ${b.name}")
        }
        assertEquals(before.kerningInfo, after.kerningInfo)
        assertEquals(before.fontInfo, after.fontInfo)
        assertEquals(CurveFormat.CUBIC, after.lib.lineContourFormat)
    }

    @Test
    fun theCurvesTraceTheSameShapeToWithinHalfAUnitPerCoordinate() {
        val original = ufo(listOf(quadraticO), CurveFormat.QUADRATIC)
        val before = read(original).glyphs.single().outlineBounds()!!
        val after = read(prepareUfoForFontmake(original).files).glyphs.single().outlineBounds()!!
        val pairs =
            listOf(
                Triple("xMin", before.xMin, after.xMin),
                Triple("yMin", before.yMin, after.yMin),
                Triple("xMax", before.xMax, after.xMax),
                Triple("yMax", before.yMax, after.yMax),
            )
        for ((label, a, b) in pairs) assertTrue(abs(a - b) <= 1, "$label moved from $a to $b")
        // A real curve: the elevated outline has off-curve controls that are not on their own ends.
        val cubic = read(prepareUfoForFontmake(original).files).glyphs.single()
        val curved =
            cubic.contours
                .flatMap { it.points.chunked(3) }
                .count { (start, c1, c2) -> c1.point != start.point || c2.point != start.point }
        assertTrue(curved > 0)
    }

    @Test
    fun outerContoursRunCounterClockwiseAndCountersClockwiseInTheCopy() {
        val original = ufo(listOf(quadraticO), CurveFormat.QUADRATIC)
        val before = read(original).glyphs.single()
        // The fixture is TrueType-style: the outer ring runs clockwise and the counter counter-clockwise.
        assertEquals(Direction.CLOCKWISE, before.contours[0].direction())
        assertEquals(Direction.COUNTER_CLOCKWISE, before.contours[1].direction())

        val after = read(prepareUfoForFontmake(original).files).glyphs.single()
        assertEquals(Direction.COUNTER_CLOCKWISE, after.contours[0].direction(), "outer")
        assertEquals(Direction.CLOCKWISE, after.contours[1].direction(), "counter")
    }

    @Test
    fun straightSidedQuadraticGlyphsBecomePlainLinesNotCurves() {
        val original = ufo(listOf(quadraticH), CurveFormat.QUADRATIC)
        val glif = prepareUfoForFontmake(original).files.getValue("glyphs/H_.glif").decodeToString()
        assertTrue("type=\"line\"" in glif, glif)
        assertTrue("type=\"curve\"" !in glif && "qcurve" !in glif, glif)
    }

    @Test
    fun aCubicProjectIsReturnedAsItIsWithNothingRewritten() {
        val original = ufo(listOf(cubicX), CurveFormat.CUBIC)
        val prepared = prepareUfoForFontmake(original)
        assertTrue(prepared.elevatedGlyphs.isEmpty())
        assertSame(original, prepared.files)
    }

    @Test
    fun filesTheCodecDoesNotModelAreCarriedAlongUntouched() {
        val extra = "an image the user keeps in the UFO".encodeToByteArray()
        val original = ufo(listOf(quadraticH), CurveFormat.QUADRATIC) + ("images/note.png" to extra)
        val prepared = prepareUfoForFontmake(original)
        assertContentEquals(extra, prepared.files.getValue("images/note.png"))
        assertNotEquals(original.getValue("glyphs/H_.glif").decodeToString(), prepared.files.getValue("glyphs/H_.glif").decodeToString())
    }

    @Test
    fun aUfoThatCannotBeReadIsRefusedWithAMessageNotAnArbitraryCrash() {
        val broken = mapOf("metainfo.plist" to "not a plist".encodeToByteArray())
        assertFailsWith<Exception>("reading a broken UFO must fail loudly") { prepareUfoForFontmake(broken) }
    }
}
