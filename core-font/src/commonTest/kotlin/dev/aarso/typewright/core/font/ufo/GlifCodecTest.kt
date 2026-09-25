// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.ufo

import dev.aarso.typewright.core.geometry.Anchor
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Guideline
import dev.aarso.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GlifCodecTest {
    private fun cubicPoint(
        x: Int,
        y: Int,
        onCurve: Boolean,
    ) = ContourPoint(Point(x, y), onCurve)

    /** A closed cubic "o"-like shape: two on-curve anchors, each with two off-curve controls. */
    private fun cubicLensContour(): Contour =
        Contour(
            listOf(
                cubicPoint(0, 0, true),
                cubicPoint(20, 10, false),
                cubicPoint(40, 30, false),
                cubicPoint(50, 50, true),
                cubicPoint(40, 70, false),
                cubicPoint(20, 90, false),
            ),
            CurveFormat.CUBIC,
        )

    @Test
    fun writesAndParsesASingleCurveContourLosslessly() {
        val glyph = Glyph("o", 600, listOf(cubicLensContour()))
        val xml = writeGlif(glyph)
        val parsed = parseGlif(xml)
        assertEquals(glyph, parsed)
    }

    @Test
    fun writtenGlifHasTheExpectedShape() {
        val glyph = Glyph("o", 600, listOf(cubicLensContour()))
        val xml = writeGlif(glyph)
        assertTrue(xml.contains("<glyph name=\"o\" format=\"2\">"))
        assertTrue(xml.contains("<advance width=\"600\"/>"))
        assertTrue(xml.contains("<point x=\"0\" y=\"0\" type=\"curve\"/>"))
        assertTrue(xml.contains("<point x=\"20\" y=\"10\"/>")) // off-curve: no type attribute
    }

    @Test
    fun aLineTypedSegmentBecomesADegenerateCubicOnReadAndBackToLineOnWrite() {
        val glif =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <glyph name="square" format="2">
              <advance width="500"/>
              <outline>
                <contour>
                  <point x="0" y="0" type="line"/>
                  <point x="100" y="0" type="line"/>
                  <point x="100" y="100" type="line"/>
                  <point x="0" y="100" type="line"/>
                </contour>
              </outline>
            </glyph>
            """.trimIndent()
        val glyph = parseGlif(glif)
        val contour = glyph.contours.single()
        assertEquals(CurveFormat.CUBIC, contour.format)
        assertEquals(12, contour.points.size) // 4 on-curve points, each with 2 synthesized off-curve controls
        // The degenerate cubic for the first segment (0,0)->(100,0) has both controls sitting on the anchors.
        assertEquals(Point(0, 0), contour.points[0].point)
        assertEquals(Point(0, 0), contour.points[1].point)
        assertEquals(Point(100, 0), contour.points[2].point)

        val rewritten = writeGlif(glyph)
        assertTrue(rewritten.contains("<point x=\"0\" y=\"0\" type=\"line\"/>"))
        assertEquals(false, rewritten.contains("type=\"curve\""))
        assertEquals(glyph, parseGlif(rewritten))
    }

    @Test
    fun aContourCanMixLineAndCurveSegments() {
        // A triangle-ish shape: P0 -line-> P1 -curve(C1,C2)-> P2 -line-> (wraps back to) P0.
        // Point-list convention: a point's `type` describes the segment *arriving* at it, so the
        // curve's controls (C1, C2) are written between P1 and P2, and P2 carries type="curve".
        val glif =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <glyph name="mixed" format="2">
              <advance width="500"/>
              <outline>
                <contour>
                  <point x="0" y="0" type="line"/>
                  <point x="100" y="0" type="line"/>
                  <point x="150" y="20"/>
                  <point x="180" y="60"/>
                  <point x="150" y="100" type="curve"/>
                </contour>
              </outline>
            </glyph>
            """.trimIndent()
        val glyph = parseGlif(glif)
        val contour = glyph.contours.single()
        // 3 on-curve vertices; the P1->P2 segment keeps its 2 real controls, the other two
        // (line) segments each get a synthesized pair -> 9 points total.
        assertEquals(9, contour.points.size)
        assertEquals(Point(150, 20), contour.points[4].point) // C1, the curve segment's first control
        assertEquals(Point(180, 60), contour.points[5].point) // C2
        val rewritten = parseGlif(writeGlif(glyph))
        assertEquals(glyph, rewritten)
    }

    @Test
    fun writesNoOutlineElementForAGlyphWithNoContours() {
        val glyph = Glyph("space", 300, emptyList())
        val xml = writeGlif(glyph)
        assertEquals(false, xml.contains("<outline>"))
        assertEquals(glyph, parseGlif(xml))
    }

    @Test
    fun advanceWidthDefaultsToZeroWhenAbsent() {
        val glif = "<?xml version=\"1.0\"?><glyph name=\"g\" format=\"2\"></glyph>"
        val glyph = parseGlif(glif)
        assertEquals(0, glyph.advanceWidth)
        assertEquals(emptyList(), glyph.contours)
    }

    @Test
    fun writesAndParsesAnchorsLosslessly() {
        val glyph =
            Glyph(
                "a",
                600,
                listOf(cubicLensContour()),
                anchors = listOf(Anchor("top", Point(300, 700)), Anchor("_top", Point(300, 0))),
            )
        val xml = writeGlif(glyph)
        assertTrue(xml.contains("<anchor x=\"300\" y=\"700\" name=\"top\"/>"))
        assertTrue(xml.contains("<anchor x=\"300\" y=\"0\" name=\"_top\"/>"))
        assertEquals(glyph, parseGlif(xml))
    }

    @Test
    fun aGlyphWithNoAnchorsWritesNoAnchorElement() {
        val glyph = Glyph("space", 300, emptyList())
        val xml = writeGlif(glyph)
        assertEquals(false, xml.contains("<anchor"))
        assertEquals(emptyList(), parseGlif(xml).anchors)
    }

    @Test
    fun rejectsAnAnchorWithNoName() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2">
              <anchor x="10" y="20"/>
            </glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun unmodelledElementsAreSkippedNotErrors() {
        val glif =
            """
            <?xml version="1.0"?>
            <glyph name="A" format="2">
              <advance width="500"/>
              <unicode hex="0041"/>
              <anchor x="10" y="20" name="top"/>
              <outline>
                <contour>
                  <point x="0" y="0" type="line"/>
                  <point x="10" y="0" type="line"/>
                  <point x="5" y="10" type="line"/>
                </contour>
              </outline>
              <lib><dict><key>com.example</key><string>note</string></dict></lib>
              <note>a note</note>
            </glyph>
            """.trimIndent()
        val glyph = parseGlif(glif)
        assertEquals("A", glyph.name)
        assertEquals(500, glyph.advanceWidth)
        assertEquals(1, glyph.contours.size)
    }

    @Test
    fun rejectsAnOpenContour() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="move"/>
              <point x="10" y="0" type="line"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsAQCurvePoint() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="qcurve"/>
              <point x="10" y="0"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsACurveWithTheWrongNumberOfControls() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="curve"/>
              <point x="10" y="0"/>
              <point x="20" y="0" type="curve"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsWritingAQuadraticContour() {
        val quadratic =
            Contour(
                listOf(ContourPoint(Point(0, 0), true), ContourPoint(Point(10, 10), false), ContourPoint(Point(20, 0), true)),
                CurveFormat.QUADRATIC,
            )
        assertFailsWith<IllegalArgumentException> { writeGlif(Glyph("g", 100, listOf(quadratic))) }
    }

    @Test
    fun rejectsAGlyphElementMissingAName() {
        val glif = "<?xml version=\"1.0\"?><glyph format=\"2\"><advance width=\"100\"/></glyph>"
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun roundsFractionalCoordinates() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0.4" y="0.6" type="line"/>
              <point x="10.5" y="0" type="line"/>
              <point x="5" y="10" type="line"/>
            </contour></outline></glyph>
            """.trimIndent()
        val glyph = parseGlif(glif)
        assertEquals(
            Point(0, 1),
            glyph.contours
                .single()
                .points[0]
                .point,
        )
    }

    @Test
    fun writesAndParsesGuidelinesLosslessly() {
        val glyph =
            Glyph(
                "o",
                600,
                listOf(cubicLensContour()),
                guidelines =
                    listOf(
                        Guideline(x = 500.0), // vertical
                        Guideline(y = -12.0, name = "overshoot"), // horizontal
                        Guideline(x = 100.0, y = 200.0, angle = 12.5, name = "italic", color = "1,0,0,1", identifier = "g1"), // angled
                    ),
            )
        val xml = writeGlif(glyph)
        assertTrue(xml.contains("<guideline x=\"500\"/>"))
        assertTrue(xml.contains("<guideline y=\"-12\" name=\"overshoot\"/>"))
        assertTrue(
            xml.contains(
                "<guideline x=\"100\" y=\"200\" angle=\"12.5\" name=\"italic\" color=\"1,0,0,1\" identifier=\"g1\"/>",
            ),
        )
        assertEquals(glyph, parseGlif(xml))
    }

    @Test
    fun aGlyphWithNoGuidelinesWritesNoGuidelineElement() {
        val glyph = Glyph("space", 300, emptyList())
        val xml = writeGlif(glyph)
        assertEquals(false, xml.contains("<guideline"))
        assertEquals(emptyList(), parseGlif(xml).guidelines)
    }

    @Test
    fun rejectsAGuidelineWithAnAngleButNoY() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2">
              <guideline x="10" angle="45"/>
            </glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsAGuidelineWithNeitherXNorY() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2">
              <guideline name="stray"/>
            </glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsAGuidelineWithANonNumericCoordinate() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2">
              <guideline x="not-a-number"/>
            </glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun matchesTheUfo3SpecsOwnGlifGuidelineExample() {
        // unifiedfontobject.org/versions/ufo3/glyphs/glif -- the spec's own example glyph.
        val glif =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <glyph name="period" format="2">
              <advance width="268"/>
              <unicode hex="002E"/>
              <image fileName="period sketch.png" xScale="0.5" yScale="0.5"/>
              <guideline y="-12" name="overshoot"/>
              <anchor x="74" y="197" name="top"/>
              <outline>
              </outline>
              <lib>
              </lib>
            </glyph>
            """.trimIndent()
        val glyph = parseGlif(glif)
        assertEquals("period", glyph.name)
        assertEquals(268, glyph.advanceWidth)
        assertEquals(listOf(Guideline(y = -12.0, name = "overshoot")), glyph.guidelines)
        assertEquals(listOf(Anchor("top", Point(74, 197))), glyph.anchors)
    }

    @Test
    fun writingAndParsingAMultiContourGlyphRoundTrips() {
        val outer =
            Contour(
                listOf(
                    cubicPoint(0, 0, true),
                    cubicPoint(0, 0, false),
                    cubicPoint(100, 0, false),
                    cubicPoint(100, 0, true),
                    cubicPoint(100, 0, false),
                    cubicPoint(0, 100, false),
                ),
                CurveFormat.CUBIC,
            )
        val inner = cubicLensContour()
        val glyph = Glyph("compound", 700, listOf(outer, inner))
        assertEquals(glyph, parseGlif(writeGlif(glyph)))
    }
}
