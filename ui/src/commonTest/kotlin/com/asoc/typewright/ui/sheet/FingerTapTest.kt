// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FingerTapTest {
    private val middle = Vec2(180.0, 400.0)

    @Test
    fun twoFingersDownTogetherAndLiftedQuicklyIsATap() {
        val tap = FingerTap(2)
        assertFalse(tap.onPointers(2, middle, 60.0, 20))
        assertFalse(tap.onPointers(1, middle, 0.0, 120))
        assertTrue(tap.onPointers(0, middle, 0.0, 140), "the last finger up ends a tap")
    }

    @Test
    fun aSingleFingerTapIsNeverAMultiFingerTap() {
        val tap = FingerTap(2)
        tap.onPointers(1, middle, 0.0, 0)
        assertFalse(tap.onPointers(0, middle, 0.0, 80))
    }

    @Test
    fun heldTooLongItIsAPressAndNotATap() {
        val tap = FingerTap(2)
        tap.onPointers(2, middle, 60.0, 0)
        tap.onPointers(2, middle, 60.0, 200)
        assertFalse(tap.onPointers(0, middle, 0.0, FingerTap.DEFAULT_MAX_MS + 50))
    }

    @Test
    fun fingersThatTravelAreAPanAndFingersThatSpreadAreAPinch() {
        val pan = FingerTap(2)
        pan.onPointers(2, middle, 60.0, 0)
        pan.onPointers(2, Vec2(middle.x + 40.0, middle.y), 60.0, 60)
        assertFalse(pan.onPointers(0, middle, 0.0, 100), "the middle of the two moved 40 dp")

        val pinch = FingerTap(2)
        pinch.onPointers(2, middle, 60.0, 0)
        pinch.onPointers(2, middle, 120.0, 60)
        assertFalse(pinch.onPointers(0, middle, 0.0, 100), "the gap between them doubled")
    }

    @Test
    fun aThirdFingerMakesItNotATwoFingerTap() {
        val tap = FingerTap(2)
        tap.onPointers(2, middle, 60.0, 0)
        tap.onPointers(3, middle, 60.0, 30)
        tap.onPointers(2, middle, 60.0, 60)
        assertFalse(tap.onPointers(0, middle, 0.0, 90))
    }

    @Test
    fun threeFingersAreATapOfTheirOwnAndTheTwoFingerReaderIgnoresIt() {
        val two = FingerTap(2)
        val three = FingerTap(3)
        // The fingers land one after another, so the pointers pass through two on the way to three.
        for (reader in listOf(two, three)) {
            reader.onPointers(2, middle, 60.0, 0)
            reader.onPointers(3, middle, 70.0, 40)
        }
        val twoTapped = two.onPointers(0, middle, 0.0, 120)
        val threeTapped = three.onPointers(0, middle, 0.0, 120)
        assertFalse(twoTapped, "three fingers are not an undo")
        assertTrue(threeTapped, "they are a redo")
    }

    @Test
    fun theNextGestureStartsCleanAfterATapAndAfterAMiss() {
        val tap = FingerTap(2)
        tap.onPointers(2, middle, 60.0, 0)
        assertTrue(tap.onPointers(0, middle, 0.0, 50))
        assertFalse(tap.onPointers(0, middle, 0.0, 60), "nothing is left over to tap twice")
        tap.onPointers(2, middle, 60.0, 1000)
        tap.onPointers(2, Vec2(0.0, 0.0), 60.0, 1050)
        assertFalse(tap.onPointers(0, middle, 0.0, 1100))
        tap.onPointers(2, middle, 60.0, 2000)
        assertTrue(tap.onPointers(0, middle, 0.0, 2040), "a clean tap after a spoiled one is a tap")
    }

    @Test
    fun resettingForgetsAGestureInProgress() {
        val tap = FingerTap(2)
        tap.onPointers(2, middle, 60.0, 0)
        tap.reset()
        assertEquals(false, tap.onPointers(0, middle, 0.0, 40))
    }

    @Test
    fun aTapNeedsAtLeastTwoFingers() {
        assertFailsWith<IllegalArgumentException> { FingerTap(1) }
    }
}
