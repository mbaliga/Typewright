// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.Point
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
    fun unmodelledElementsAndForeignLibKeysAreSkippedNotErrors() {
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
        assertEquals(listOf(0x41), glyph.unicodes)
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
    fun fractionalAndHugeGuidelineNumbersAreWrittenAsFontToolsWritesThemOnEveryPlatform() {
        val glyph =
            Glyph(
                "o",
                600,
                emptyList(),
                guidelines =
                    listOf(
                        Guideline(x = 1e-5, y = 12345678.9, angle = 0.5),
                        Guideline(x = 1.5e20),
                        Guideline(y = -0.25),
                    ),
            )
        val xml = writeGlif(glyph)
        assertTrue("<guideline x=\"1e-05\" y=\"12345678.9\" angle=\"0.5\"/>" in xml, xml)
        assertTrue("<guideline x=\"1.5e+20\"/>" in xml, xml)
        assertTrue("<guideline y=\"-0.25\"/>" in xml, xml)
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
        assertEquals(listOf(0x2E), glyph.unicodes)
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

    // --- Unicodes -------------------------------------------------------------------------------

    @Test
    fun writesUnicodesRightAfterTheAdvanceInListOrderAsFourDigitUppercaseHex() {
        val glyph = Glyph("space", 250, emptyList(), unicodes = listOf(0xA0, 0x20, 0x1F600))
        val xml = writeGlif(glyph)
        val lines = xml.lines().map { it.trim() }
        val advance = lines.indexOf("<advance width=\"250\"/>")
        assertEquals(
            listOf("<unicode hex=\"00A0\"/>", "<unicode hex=\"0020\"/>", "<unicode hex=\"1F600\"/>"),
            lines.subList(advance + 1, advance + 4),
        )
        assertEquals(glyph, parseGlif(xml))
    }

    @Test
    fun aGlyphWithNoUnicodesWritesNoUnicodeElement() {
        val xml = writeGlif(Glyph(".notdef", 500, emptyList()))
        assertEquals(false, xml.contains("<unicode"))
        assertEquals(emptyList(), parseGlif(xml).unicodes)
    }

    @Test
    fun readsUnicodeHexCaseInsensitivelyAndDropsRepeatsKeepingTheFirst() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="a" format="2">
              <unicode hex="00e9"/>
              <unicode hex="0061"/>
              <unicode hex="00E9"/>
              <unicode hex="10ffff"/>
            </glyph>
            """.trimIndent()
        assertEquals(listOf(0xE9, 0x61, 0x10FFFF), parseGlif(glif).unicodes)
    }

    @Test
    fun rejectsAUnicodeThatIsNotHex() {
        val glif = "<?xml version=\"1.0\"?><glyph name=\"a\" format=\"2\"><unicode hex=\"00G1\"/></glyph>"
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsASignedUnicodeHexValue() {
        val glif = "<?xml version=\"1.0\"?><glyph name=\"a\" format=\"2\"><unicode hex=\"-41\"/></glyph>"
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsAUnicodeOutsideTheCodeSpace() {
        val glif = "<?xml version=\"1.0\"?><glyph name=\"a\" format=\"2\"><unicode hex=\"110000\"/></glyph>"
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsAUnicodeWithNoHex() {
        val glif = "<?xml version=\"1.0\"?><glyph name=\"a\" format=\"2\"><unicode/></glyph>"
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    // --- Quadratic contours ---------------------------------------------------------------------

    private fun on(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = true)

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    private fun quadratic(vararg points: ContourPoint) = Contour(points.toList(), CurveFormat.QUADRATIC)

    /** The lines between `<contour>` and `</contour>` of [xml]'s first contour, trimmed. */
    private fun firstContourLines(xml: String): List<String> {
        val lines = xml.lines().map { it.trim() }
        return lines.subList(lines.indexOf("<contour>") + 1, lines.indexOf("</contour>"))
    }

    @Test
    fun writesTheWorkedQCurveExampleExactly() {
        val contour = quadratic(on(250, 0), off(460, 0), off(460, 300), on(250, 300), off(40, 300), off(40, 0))
        val xml = writeGlif(Glyph("o", 500, listOf(contour)), CurveFormat.QUADRATIC)
        assertEquals(
            listOf(
                "<point x=\"250\" y=\"0\" type=\"qcurve\"/>",
                "<point x=\"460\" y=\"0\"/>",
                "<point x=\"460\" y=\"300\"/>",
                "<point x=\"250\" y=\"300\" type=\"qcurve\"/>",
                "<point x=\"40\" y=\"300\"/>",
                "<point x=\"40\" y=\"0\"/>",
            ),
            firstContourLines(xml),
        )
    }

    @Test
    fun aQuadraticContourRoundTripsWithItsImpliedOnCurvePointsStillImplied() {
        val contour = quadratic(on(250, 0), off(460, 0), off(460, 300), on(250, 300), off(40, 300), off(40, 0))
        val glyph = Glyph("o", 500, listOf(contour))
        val parsed = parseGlif(writeGlif(glyph))
        assertEquals(glyph, parsed)
        assertEquals(
            6,
            parsed.contours
                .single()
                .points.size,
        )
    }

    @Test
    fun aQuadraticContourStartingOffCurveKeepsItsStartPoint() {
        // Poppins-Regular's `o` outer contour: [off, off, off, on] four times, starting off-curve.
        val contour =
            quadratic(
                off(239, -9),
                off(114, 61),
                off(43, 190),
                on(43, 275),
                off(43, 359),
                off(116, 488),
                off(242, 557),
                on(320, 557),
                off(398, 557),
                off(524, 488),
                off(597, 360),
                on(597, 275),
                off(597, 190),
                off(522, 61),
                off(394, -9),
                on(316, -9),
            )
        val glyph = Glyph("o", 640, listOf(contour))
        val xml = writeGlif(glyph)
        val lines = firstContourLines(xml)
        assertEquals("<point x=\"239\" y=\"-9\"/>", lines.first())
        assertEquals("<point x=\"43\" y=\"275\" type=\"qcurve\"/>", lines[3])
        assertEquals(glyph, parseGlif(xml))
    }

    @Test
    fun anOnCurvePointAfterAnOnCurvePointIsALineInAQuadraticContour() {
        val glyph = Glyph("d", 200, listOf(quadratic(on(0, 0), on(100, 0), off(150, 50), on(100, 100), on(0, 100))))
        val xml = writeGlif(glyph)
        assertEquals(
            listOf(
                "<point x=\"0\" y=\"0\" type=\"line\"/>",
                "<point x=\"100\" y=\"0\" type=\"line\"/>",
                "<point x=\"150\" y=\"50\"/>",
                "<point x=\"100\" y=\"100\" type=\"qcurve\"/>",
                "<point x=\"0\" y=\"100\" type=\"line\"/>",
            ),
            firstContourLines(xml),
        )
        assertEquals(glyph, parseGlif(xml))
    }

    @Test
    fun aContourWithNoOnCurvePointIsWrittenUntypedAndReadBackQuadratic() {
        val glyph = Glyph("dot", 200, listOf(quadratic(off(0, 100), off(100, 200), off(200, 100), off(100, 0))))
        val xml = writeGlif(glyph)
        assertEquals(false, xml.contains("type="))
        // Read with a cubic default: an all-off contour is quadratic by its own points.
        assertEquals(glyph, parseGlif(xml, CurveFormat.CUBIC))
    }

    @Test
    fun aQCurvePointReadsAsOnCurveInAQuadraticContour() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="qcurve"/>
              <point x="10" y="0"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertEquals(quadratic(on(0, 0), off(10, 0)), parseGlif(glif).contours.single())
    }

    @Test
    fun aQCurveWithNoPrecedingOffCurvePointIsStillAnOnCurvePoint() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="qcurve"/>
              <point x="10" y="0" type="qcurve"/>
              <point x="5" y="10"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertEquals(quadratic(on(0, 0), on(10, 0), off(5, 10)), parseGlif(glif).contours.single())
    }

    @Test
    fun anExplicitOffcurveTypeReadsAsOffCurve() {
        val cubic =
            """
            <?xml version="1.0"?><glyph name="c" format="2"><outline><contour>
              <point x="0" y="0" type="curve"/>
              <point x="20" y="10" type="offcurve"/>
              <point x="40" y="30" type="offcurve"/>
              <point x="50" y="50" type="curve"/>
              <point x="40" y="70" type="offcurve"/>
              <point x="20" y="90" type="offcurve"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertEquals(Glyph("c", 0, listOf(cubicLensContour())), parseGlif(cubic))

        val quadraticGlif =
            """
            <?xml version="1.0"?><glyph name="q" format="2"><outline><contour>
              <point x="0" y="0" type="qcurve"/>
              <point x="10" y="10" type="offcurve"/>
              <point x="20" y="0" type="qcurve"/>
              <point x="10" y="-10" type="offcurve"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertEquals(quadratic(on(0, 0), off(10, 10), on(20, 0), off(10, -10)), parseGlif(quadraticGlif).contours.single())
    }

    @Test
    fun rejectsAContourMixingCurveAndQCurvePoints() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="qcurve"/>
              <point x="10" y="10"/>
              <point x="20" y="10"/>
              <point x="30" y="0" type="curve"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsALinePointPrecededByAnOffCurvePoint() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="qcurve"/>
              <point x="10" y="10"/>
              <point x="20" y="0" type="line"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsALinePointPrecededCyclicallyByATrailingOffCurvePoint() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="line"/>
              <point x="20" y="0" type="qcurve"/>
              <point x="10" y="10"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun rejectsAnUnknownPointType() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2"><outline><contour>
              <point x="0" y="0" type="spline"/>
              <point x="10" y="0" type="line"/>
            </contour></outline></glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    // --- Straight glyphs and the line-contour format key ----------------------------------------

    private fun straightSquare(format: CurveFormat): Contour =
        when (format) {
            CurveFormat.QUADRATIC -> {
                quadratic(on(0, 0), on(100, 0), on(100, 100), on(0, 100))
            }

            CurveFormat.CUBIC -> {
                Contour(
                    listOf(
                        on(0, 0),
                        off(0, 0),
                        off(100, 0),
                        on(100, 0),
                        off(100, 0),
                        off(100, 100),
                        on(100, 100),
                        off(100, 100),
                        off(0, 100),
                        on(0, 100),
                        off(0, 100),
                        off(0, 0),
                    ),
                    CurveFormat.CUBIC,
                )
            }
        }

    @Test
    fun aStraightGlyphInTheDefaultFormatWritesNoLib() {
        for (format in CurveFormat.entries) {
            val glyph = Glyph("square", 100, listOf(straightSquare(format)))
            val xml = writeGlif(glyph, lineContourDefault = format)
            assertEquals(false, xml.contains("<lib>"), "format $format")
            assertEquals(glyph, parseGlif(xml, lineContourDefault = format), "format $format")
        }
    }

    @Test
    fun aStraightGlyphReadsInTheFormatItsReaderIsTold() {
        val xml = writeGlif(Glyph("square", 100, listOf(straightSquare(CurveFormat.CUBIC))))
        assertEquals(CurveFormat.QUADRATIC, parseGlif(xml, CurveFormat.QUADRATIC).contours.single().format)
        assertEquals(CurveFormat.CUBIC, parseGlif(xml, CurveFormat.CUBIC).contours.single().format)
        assertEquals(CurveFormat.CUBIC, parseGlif(xml).contours.single().format)
    }

    @Test
    fun aStraightGlyphOutsideTheDefaultFormatCarriesTheGlyphLibKey() {
        val glyph = Glyph("square", 100, listOf(straightSquare(CurveFormat.CUBIC)))
        val xml = writeGlif(glyph, lineContourDefault = CurveFormat.QUADRATIC)
        assertTrue(
            xml.endsWith(
                "  </outline>\n" +
                    "  <lib>\n" +
                    "    <dict>\n" +
                    "      <key>com.asoc.typewright.lineContourFormat</key>\n" +
                    "      <string>cubic</string>\n" +
                    "    </dict>\n" +
                    "  </lib>\n" +
                    "</glyph>\n",
            ),
            xml,
        )
        assertEquals(glyph, parseGlif(xml, lineContourDefault = CurveFormat.QUADRATIC))

        val quadraticGlyph = Glyph("square", 100, listOf(straightSquare(CurveFormat.QUADRATIC)))
        val quadraticXml = writeGlif(quadraticGlyph, lineContourDefault = CurveFormat.CUBIC)
        assertTrue(quadraticXml.contains("<string>quadratic</string>"))
        assertEquals(quadraticGlyph, parseGlif(quadraticXml))
    }

    @Test
    fun aStraightContourTakesTheFormatOfItsGlyphsCurvedContours() {
        val lensQuadratic = quadratic(on(0, 0), off(25, 50), on(50, 0), off(25, -50))
        val quadraticGlyph = Glyph("i", 100, listOf(straightSquare(CurveFormat.QUADRATIC), lensQuadratic))
        val quadraticXml = writeGlif(quadraticGlyph, lineContourDefault = CurveFormat.CUBIC)
        assertEquals(false, quadraticXml.contains("<lib>"))
        assertEquals(quadraticGlyph, parseGlif(quadraticXml, lineContourDefault = CurveFormat.CUBIC))

        val cubicGlyph = Glyph("i", 100, listOf(straightSquare(CurveFormat.CUBIC), cubicLensContour()))
        val cubicXml = writeGlif(cubicGlyph, lineContourDefault = CurveFormat.QUADRATIC)
        assertEquals(false, cubicXml.contains("<lib>"))
        assertEquals(cubicGlyph, parseGlif(cubicXml, lineContourDefault = CurveFormat.QUADRATIC))
    }

    @Test
    fun refusesToWriteStraightContoursTheGlifCouldNotReadBackInTheirOwnFormat() {
        val besideCurved = Glyph("x", 100, listOf(straightSquare(CurveFormat.QUADRATIC), cubicLensContour()))
        assertFailsWith<IllegalArgumentException> { writeGlif(besideCurved) }

        val bothStraight = Glyph("x", 100, listOf(straightSquare(CurveFormat.QUADRATIC), straightSquare(CurveFormat.CUBIC)))
        assertFailsWith<IllegalArgumentException> { writeGlif(bothStraight) }
    }

    @Test
    fun theGlyphLibKeyIsReadAmongOtherToolsKeysWhichAreSkipped() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="square" format="2">
              <outline><contour>
                <point x="0" y="0" type="line"/><point x="10" y="0" type="line"/><point x="10" y="10" type="line"/>
              </contour></outline>
              <lib><dict>
                <key>com.example.blob</key><data>AAEC</data>
                <key>com.asoc.typewright.lineContourFormat</key><string>quadratic</string>
                <key>public.markColor</key><string>1,0,0,1</string>
                <key>public.objectLibs</key><dict><key>id</key><dict><key>k</key><array><integer>1</integer></array></dict></dict>
              </dict></lib>
            </glyph>
            """.trimIndent()
        assertEquals(quadratic(on(0, 0), on(10, 0), on(10, 10)), parseGlif(glif, CurveFormat.CUBIC).contours.single())
    }

    @Test
    fun theGlyphLibKeyDoesNotOverrideAGlyphsCurvedContours() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2">
              <outline><contour>
                <point x="0" y="0" type="curve"/><point x="0" y="10"/><point x="10" y="10"/>
              </contour></outline>
              <lib><dict><key>com.asoc.typewright.lineContourFormat</key><string>quadratic</string></dict></lib>
            </glyph>
            """.trimIndent()
        assertEquals(CurveFormat.CUBIC, parseGlif(glif).contours.single().format)
    }

    @Test
    fun rejectsAnUnknownLineContourFormatValue() {
        val glif =
            """
            <?xml version="1.0"?><glyph name="g" format="2">
              <lib><dict><key>com.asoc.typewright.lineContourFormat</key><string>cubicish</string></dict></lib>
            </glyph>
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> { parseGlif(glif) }
    }

    @Test
    fun writesElementsInFontToolsOrder() {
        val glyph =
            Glyph(
                "a",
                600,
                listOf(straightSquare(CurveFormat.CUBIC)),
                anchors = listOf(Anchor("top", Point(300, 700))),
                guidelines = listOf(Guideline(y = 500.0)),
                unicodes = listOf(0x61),
            )
        val xml = writeGlif(glyph, lineContourDefault = CurveFormat.QUADRATIC)
        val order = listOf("<advance", "<unicode", "<guideline", "<anchor", "<outline>", "<lib>").map { xml.indexOf(it) }
        assertTrue(order.all { it >= 0 } && order == order.sorted(), xml)
    }
}
