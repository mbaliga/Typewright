// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The three valid [Guideline] shapes (vertical, horizontal, angled) and the invalid combinations its `init` block rejects. */
class GuidelineTest {
    @Test
    fun aVerticalGuidelineHasOnlyX() {
        val guideline = Guideline(x = 500.0)
        assertEquals(500.0, guideline.x)
        assertEquals(null, guideline.y)
        assertEquals(null, guideline.angle)
    }

    @Test
    fun aHorizontalGuidelineHasOnlyY() {
        val guideline = Guideline(y = -12.0)
        assertEquals(null, guideline.x)
        assertEquals(-12.0, guideline.y)
        assertEquals(null, guideline.angle)
    }

    @Test
    fun anAngledGuidelineHasXAndYAndAngle() {
        val guideline = Guideline(x = 100.0, y = 200.0, angle = 12.5)
        assertEquals(100.0, guideline.x)
        assertEquals(200.0, guideline.y)
        assertEquals(12.5, guideline.angle)
    }

    @Test
    fun angleZeroAndThreeHundredSixtyAreBothAllowed() {
        Guideline(x = 0.0, y = 0.0, angle = 0.0)
        Guideline(x = 0.0, y = 0.0, angle = 360.0)
    }

    @Test
    fun rejectsNeitherXNorY() {
        assertFailsWith<IllegalArgumentException> { Guideline(angle = null) }
    }

    @Test
    fun rejectsAnAngleWithNoX() {
        assertFailsWith<IllegalArgumentException> { Guideline(y = 100.0, angle = 45.0) }
    }

    @Test
    fun rejectsAnAngleWithNoY() {
        assertFailsWith<IllegalArgumentException> { Guideline(x = 100.0, angle = 45.0) }
    }

    @Test
    fun rejectsAnAngleOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Guideline(x = 0.0, y = 0.0, angle = -0.1) }
        assertFailsWith<IllegalArgumentException> { Guideline(x = 0.0, y = 0.0, angle = 360.1) }
    }

    @Test
    fun nameColorAndIdentifierAreOptionalMetadata() {
        val guideline = Guideline(x = 10.0, name = "left sidebearing", color = "1,0,0,1", identifier = "abc123")
        assertEquals("left sidebearing", guideline.name)
        assertEquals("1,0,0,1", guideline.color)
        assertEquals("abc123", guideline.identifier)
    }
}
