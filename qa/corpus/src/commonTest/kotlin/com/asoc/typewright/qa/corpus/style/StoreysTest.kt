// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreysTest {
    @Test
    fun gWithTwoContoursIsSingleStorey() {
        assertEquals(Storeys.SINGLE, storeysFromG(placeholderContourGlyph("g", contourCount = 2)))
    }

    @Test
    fun gWithThreeContoursIsDoubleStorey() {
        assertEquals(Storeys.DOUBLE, storeysFromG(placeholderContourGlyph("g", contourCount = 3)))
    }

    @Test
    fun gWithNoContoursIsUnknown() {
        assertEquals(Storeys.UNKNOWN, storeysFromG(placeholderContourGlyph("g", contourCount = 1).let { it.copy(contours = emptyList()) }))
    }

    @Test
    fun aWithACounterFillingMostOfTheHeightIsSingleStorey() {
        val a = singleCounterGlyph("a", counterHeightFraction = 0.90)
        assertEquals(Storeys.SINGLE, storeysFromA(a))
    }

    @Test
    fun aWithALowerBowlOnlyIsDoubleStorey() {
        val a = singleCounterGlyph("a", counterHeightFraction = 0.55)
        assertEquals(Storeys.DOUBLE, storeysFromA(a))
    }

    @Test
    fun aWithOnlyOneContourIsUnknown() {
        val a =
            com.asoc.typewright.core.geometry
                .Glyph("a", 400, listOf(polygon(0 to 0, 100 to 0, 100 to 100, 0 to 100)))
        assertEquals(Storeys.UNKNOWN, storeysFromA(a))
    }

    @Test
    fun combineStoreysPrefersG() {
        assertEquals(Storeys.DOUBLE, combineStoreys(fromA = Storeys.SINGLE, fromG = Storeys.DOUBLE))
    }

    @Test
    fun combineStoreysFallsBackToAWhenGIsUnknown() {
        assertEquals(Storeys.SINGLE, combineStoreys(fromA = Storeys.SINGLE, fromG = Storeys.UNKNOWN))
    }

    @Test
    fun combineStoreysIsUnknownWhenBothAre() {
        assertEquals(Storeys.UNKNOWN, combineStoreys(fromA = Storeys.UNKNOWN, fromG = Storeys.UNKNOWN))
    }
}
