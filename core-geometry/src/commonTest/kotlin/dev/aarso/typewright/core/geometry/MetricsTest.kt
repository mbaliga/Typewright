package dev.aarso.typewright.core.geometry

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsTest {
    @Test
    fun lengthIsEuclideanDistance() {
        assertEquals(5.0, Segment(Point(0, 0), Point(3, 4)).length())
        assertEquals(0.0, Segment(Point(2, 2), Point(2, 2)).length())
    }

    @Test
    fun unitVectorIsNormalized() {
        val v = Segment(Point(0, 0), Point(3, 4)).unitVector()
        assertEquals(0.6, v.x, absoluteTolerance = 1e-12)
        assertEquals(0.8, v.y, absoluteTolerance = 1e-12)
        assertEquals(1.0, v.length(), absoluteTolerance = 1e-12)
    }

    @Test
    fun aZeroLengthSegmentHasTheZeroUnitVector() {
        assertEquals(Vec2(0.0, 0.0), Segment(Point(5, 5), Point(5, 5)).unitVector())
    }

    @Test
    fun collinearSameDirectionSegmentsHaveZeroAngleBetweenThem() {
        val a = Segment(Point(0, 0), Point(10, 0))
        val b = Segment(Point(20, 0), Point(25, 0))
        assertEquals(0.0, angleBetween(a, b), absoluteTolerance = 1e-12)
    }

    @Test
    fun perpendicularSegmentsHaveARightAngleBetweenThem() {
        val horizontal = Segment(Point(0, 0), Point(10, 0))
        val vertical = Segment(Point(0, 0), Point(0, 10))
        assertEquals(PI / 2, angleBetween(horizontal, vertical), absoluteTolerance = 1e-12)
        assertEquals(-PI / 2, angleBetween(vertical, horizontal), absoluteTolerance = 1e-12)
    }

    @Test
    fun oppositeDirectionSegmentsHaveAStraightAngleBetweenThem() {
        val a = Segment(Point(0, 0), Point(10, 0))
        val b = Segment(Point(0, 0), Point(-5, 0))
        assertEquals(PI, angleBetween(a, b), absoluteTolerance = 1e-12)
    }

    @Test
    fun aFortyFiveDegreeTurn() {
        val a = Segment(Point(0, 0), Point(1, 0))
        val b = Segment(Point(0, 0), Point(1, 1))
        assertEquals(PI / 4, angleBetween(a, b), absoluteTolerance = 1e-12)
    }
}
