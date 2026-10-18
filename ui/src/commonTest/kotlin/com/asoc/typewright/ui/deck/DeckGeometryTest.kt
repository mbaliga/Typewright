// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [DeckGeometry] against the numbers drawn in ui/v1-screens/Deck.dc.html. */
class DeckGeometryTest {
    private fun assertNear(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.05,
    ) = assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun theArcsAreTheCanvasLengths() {
        // The canvas's own constants: L1 = 441.5 (the section arc) and L2 = 245.0 (the toggle arc).
        assertNear(441.5, DeckGeometry.SECTION_LENGTH)
        assertNear(245.0, DeckGeometry.TOGGLE_LENGTH)
    }

    @Test
    fun theArcsShareTheCanvasEndpoints() {
        // Section arc `M -19.3 153.6 A 527 … 409.3 153.6`; toggle arc `M 73.4 62.8 A 585 … 316.6 62.8`.
        val (sx0, sy0) = DeckGeometry.pointAt(DeckGeometry.SECTION_RADIUS, DeckGeometry.SECTION_HALF_ANGLE, 0.0)
        val (sx1, sy1) = DeckGeometry.pointAt(DeckGeometry.SECTION_RADIUS, DeckGeometry.SECTION_HALF_ANGLE, DeckGeometry.SECTION_LENGTH)
        assertNear(-19.3, sx0, 0.2)
        assertNear(153.6, sy0, 0.2)
        assertNear(409.3, sx1, 0.2)
        assertNear(153.6, sy1, 0.2)
        val (tx0, ty0) = DeckGeometry.pointAt(DeckGeometry.TOGGLE_RADIUS, DeckGeometry.TOGGLE_HALF_ANGLE, 0.0)
        val (tx1, ty1) = DeckGeometry.pointAt(DeckGeometry.TOGGLE_RADIUS, DeckGeometry.TOGGLE_HALF_ANGLE, DeckGeometry.TOGGLE_LENGTH)
        assertNear(73.4, tx0, 0.2)
        assertNear(62.8, ty0, 0.2)
        assertNear(316.6, tx1, 0.2)
        assertNear(62.8, ty1, 0.2)
    }

    @Test
    fun theMiddleOfEachArcIsStraightAboveTheCentre() {
        val (x, y) = DeckGeometry.pointAt(DeckGeometry.TOGGLE_RADIUS, DeckGeometry.TOGGLE_HALF_ANGLE, DeckGeometry.TOGGLE_LENGTH / 2)
        assertNear(195.0, x)
        assertNear(DeckGeometry.CENTER_Y - DeckGeometry.TOGGLE_RADIUS, y)
    }

    @Test
    fun sectionLabelsAreTwoEitherSideAtTheCanvasOpacities() {
        val draw = DeckGeometry.sectionLabels(DeckSection.DRAW)
        assertEquals(listOf("CAPTURE", "TRACE", "DRAW", "SPACE", "CHECK"), draw.map { it.text })
        assertEquals(listOf(0.38, 0.72, 1.0, 0.72, 0.38), draw.map { it.alpha })
        assertEquals(listOf(false, false, true, false, false), draw.map { it.current })
        assertNear(0.5 * DeckGeometry.SECTION_LENGTH, draw[2].centerLength)
        assertNear(4 * 7.6 + 24, draw[2].bandLength)
    }

    @Test
    fun anEdgeSectionLeavesBlanksOnItsOpenSide() {
        val home = DeckGeometry.sectionLabels(DeckSection.HOME)
        assertEquals(listOf("", "", "HOME", "CAPTURE", "TRACE"), home.map { it.text })
        assertEquals(listOf(0.0, 0.0, 1.0, 0.72, 0.38), home.map { it.alpha })
    }

    @Test
    fun thereIsNoBriefSection() {
        assertTrue(DeckSection.entries.none { it.label.equals("Brief", ignoreCase = true) })
    }

    @Test
    fun toggleLabelsAreCentredAndGroupsLeaveAGap() {
        // Draw's own: Outline* | Fill || Construction*
        val toggles = listOf(DeckToggle("Outline", on = true), DeckToggle("Fill"), DeckToggle("Construction", on = true, groupStart = true))
        val labels = DeckGeometry.toggleLabels(toggles)
        assertEquals(listOf("OUTLINE", "FILL", "CONSTRUCTION"), labels.map { it.text })
        assertEquals(listOf(true, false, true), labels.map { it.current })
        // widths are length * 6.4 + 13: 57.8, 38.6, 89.8; total with the 12 gap = 198.2.
        assertNear(57.8, labels[0].bandLength)
        assertNear(38.6, labels[1].bandLength)
        assertNear(89.8, labels[2].bandLength)
        val start = labels[0].centerLength - labels[0].bandLength / 2
        val end = labels[2].centerLength + labels[2].bandLength / 2
        assertNear(DeckGeometry.TOGGLE_LENGTH / 2, (start + end) / 2)
        assertNear(12.0, (labels[2].centerLength - labels[2].bandLength / 2) - (labels[1].centerLength + labels[1].bandLength / 2))
    }

    @Test
    fun noMoreThanSixToggles() {
        assertEquals(6, DeckGeometry.toggleLabels((1..9).map { DeckToggle("T$it") }).size)
    }

    @Test
    fun noTogglesLeaveNoLabels() {
        assertTrue(DeckGeometry.toggleLabels(emptyList()).isEmpty())
    }
}
