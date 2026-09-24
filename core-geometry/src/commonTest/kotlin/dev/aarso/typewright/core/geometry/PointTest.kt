package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class PointTest {
    @Test
    fun addsAndSubtractsComponentwise() {
        assertEquals(Point(4, 6), Point(1, 2) + Point(3, 4))
        assertEquals(Point(-2, -2), Point(1, 2) - Point(3, 4))
    }

    @Test
    fun convertsExactlyToVec2() {
        assertEquals(Vec2(1.0, -2.0), Point(1, -2).toVec2())
    }

    @Test
    fun vec2ArithmeticMatchesComponentwiseMath() {
        assertEquals(Vec2(4.0, 6.0), Vec2(1.0, 2.0) + Vec2(3.0, 4.0))
        assertEquals(Vec2(-2.0, -2.0), Vec2(1.0, 2.0) - Vec2(3.0, 4.0))
        assertEquals(Vec2(2.0, 4.0), Vec2(1.0, 2.0) * 2.0)
    }

    @Test
    fun dotProductOfPerpendicularVectorsIsZero() {
        assertEquals(0.0, Vec2(1.0, 0.0).dot(Vec2(0.0, 1.0)))
        assertEquals(11.0, Vec2(1.0, 2.0).dot(Vec2(3.0, 4.0)))
    }

    @Test
    fun crossProductGivesTheSignedParallelogramArea() {
        assertEquals(1.0, Vec2(1.0, 0.0).cross(Vec2(0.0, 1.0)))
        assertEquals(-1.0, Vec2(0.0, 1.0).cross(Vec2(1.0, 0.0)))
        assertEquals(0.0, Vec2(2.0, 2.0).cross(Vec2(1.0, 1.0)))
    }

    @Test
    fun lengthIsEuclidean() {
        assertEquals(5.0, Vec2(3.0, 4.0).length())
        assertEquals(0.0, Vec2(0.0, 0.0).length())
    }

    @Test
    fun midpointIsExact() {
        assertEquals(Vec2(2.0, 3.0), midpoint(Vec2(1.0, 2.0), Vec2(3.0, 4.0)))
        assertEquals(Vec2(4.5, 5.5), midpoint(Vec2(3.0, 4.0), Vec2(6.0, 7.0)))
    }
}
