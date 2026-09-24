package dev.aarso.typewright.core.font.sfnt

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GlyfTableTest {
    /** A 3-point all-on-curve triangle, as raw `glyf`-table bytes for one simple glyph (starting at `numberOfContours`). */
    private fun triangleGlyphBytes(): ByteArray =
        TestBytes()
            .i16(1) // numberOfContours
            .i16(0)
            .i16(0)
            .i16(100)
            .i16(100) // bounding box (unused by the reader)
            .u16(2) // endPtsOfContours[0]
            .u16(0) // instructionLength
            .u8(0x01)
            .u8(0x01)
            .u8(0x01) // 3 on-curve points, no repeat, full-width deltas
            .i16(0)
            .i16(100)
            .i16(-50) // x deltas: (0,0) (100,0) (50,100)
            .i16(0)
            .i16(0)
            .i16(100) // y deltas
            .toByteArray()

    private val triangleContour =
        Contour(
            listOf(
                ContourPoint(Point(0, 0), true),
                ContourPoint(Point(100, 0), true),
                ContourPoint(Point(50, 100), true),
            ),
            CurveFormat.QUADRATIC,
        )

    @Test
    fun readsASimpleGlyphsContours() {
        val glyfBytes = triangleGlyphBytes()
        val loca = LocaTable(intArrayOf(0, glyfBytes.size))
        val record = readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = 0)
        require(record is RawGlyfRecord.Simple)
        assertEquals(listOf(triangleContour), record.contours)
    }

    @Test
    fun equalLocaOffsetsMeanAnEmptyGlyph() {
        val loca = LocaTable(intArrayOf(0, 0))
        val record = readGlyfRecord(ByteArray(0), glyfTableOffset = 0, loca = loca, gid = 0)
        assertEquals(RawGlyfRecord.Empty, record)
    }

    @Test
    fun zeroContourGlyphIsEmptyButNotSkipped() {
        val bytes =
            TestBytes()
                .i16(0)
                .i16(0)
                .i16(0)
                .i16(0)
                .i16(0)
                .toByteArray()
        val loca = LocaTable(intArrayOf(0, bytes.size))
        val record = readGlyfRecord(bytes, glyfTableOffset = 0, loca = loca, gid = 0)
        require(record is RawGlyfRecord.Simple)
        assertTrue(record.contours.isEmpty())
    }

    @Test
    fun readsAGlyphWithARepeatedFlagRun() {
        // 4 on-curve points, using REPEAT_FLAG to encode the last 3 identically, and X/Y short
        // vectors to exercise that branch of coordinate decoding too.
        val bytes =
            TestBytes()
                .i16(1)
                .i16(0)
                .i16(0)
                .i16(10)
                .i16(10)
                .u16(3) // endPtsOfContours[0]: 4 points (indices 0..3)
                .u16(0)
                .u8(0x01 or 0x02 or 0x08 or 0x10 or 0x04 or 0x20) // ON | X_SHORT | REPEAT | X_SAME/POS | Y_SHORT | Y_SAME/POS
                .u8(3) // repeat the same flag 3 more times (4 points total)
                // x deltas: short vector, "same or positive" bit set -> each byte is a positive delta
                .u8(2)
                .u8(3)
                .u8(2)
                .u8(3)
                // y deltas: short vector, "same or positive" bit set -> positive deltas
                .u8(1)
                .u8(1)
                .u8(1)
                .u8(1)
                .toByteArray()
        val loca = LocaTable(intArrayOf(0, bytes.size))
        val record = readGlyfRecord(bytes, glyfTableOffset = 0, loca = loca, gid = 0)
        require(record is RawGlyfRecord.Simple)
        val points = record.contours.single().points
        assertEquals(4, points.size)
        assertEquals(Point(2, 1), points[0].point)
        assertEquals(Point(5, 2), points[1].point)
        assertEquals(Point(7, 3), points[2].point)
        assertEquals(Point(10, 4), points[3].point)
        assertTrue(points.all { it.onCurve })
    }

    @Test
    fun readsANegativeShortVectorDelta() {
        val bytes =
            TestBytes()
                .i16(1)
                .i16(0)
                .i16(0)
                .i16(10)
                .i16(10)
                .u16(0) // endPtsOfContours[0]: a single-point contour
                .u16(0)
                .u8(0x01 or 0x02 or 0x04) // ON | X_SHORT | Y_SHORT, "same/positive" bits unset -> negative deltas
                .u8(5) // x delta magnitude 5, negative
                .u8(7) // y delta magnitude 7, negative
                .toByteArray()
        val loca = LocaTable(intArrayOf(0, bytes.size))
        val record = readGlyfRecord(bytes, glyfTableOffset = 0, loca = loca, gid = 0)
        require(record is RawGlyfRecord.Simple)
        assertEquals(
            Point(-5, -7),
            record.contours
                .single()
                .points
                .single()
                .point,
        )
    }

    private fun compositeGlyphBytes(
        components: List<Triple<Int, Int, Int>>, // (glyphIndex, dx, dy), ARG_1_AND_2_ARE_WORDS + ARGS_ARE_XY_VALUES
    ): ByteArray {
        val out = TestBytes()
        out.i16(-1) // numberOfContours < 0 marks a composite
        out
            .i16(0)
            .i16(0)
            .i16(0)
            .i16(0) // bounding box
        for ((index, component) in components.withIndex()) {
            val (glyphIndex, dx, dy) = component
            val more = index != components.lastIndex
            var flags = 0x0001 or 0x0002 // ARG_1_AND_2_ARE_WORDS | ARGS_ARE_XY_VALUES
            if (more) flags = flags or 0x0020 // MORE_COMPONENTS
            out
                .u16(flags)
                .u16(glyphIndex)
                .i16(dx)
                .i16(dy)
        }
        return out.toByteArray()
    }

    @Test
    fun decomposesASimpleTwoComponentComposite() {
        val glyfBytes = triangleGlyphBytes() + compositeGlyphBytes(listOf(Triple(0, 0, 0), Triple(0, 200, 0)))
        val loca = LocaTable(intArrayOf(0, triangleGlyphBytes().size, glyfBytes.size))
        val records = List(2) { gid -> readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = gid) }
        val decomposed = decomposeGlyf(records, gid = 1)
        assertEquals(2, decomposed.size)
        assertEquals(triangleContour, decomposed[0])
        val shifted = decomposed[1]
        assertEquals(listOf(Point(200, 0), Point(300, 0), Point(250, 100)), shifted.points.map { it.point })
    }

    @Test
    fun appliesAUniformScaleTransform() {
        val out = TestBytes()
        out.i16(-1)
        out
            .i16(0)
            .i16(0)
            .i16(0)
            .i16(0)
        val flags = 0x0001 or 0x0002 or 0x0008 // WORDS | XY_VALUES | WE_HAVE_A_SCALE
        // 1.5 (not 2.0): F2Dot14 is a signed 2.14 fixed format, whose representable range is
        // [-2.0, 2.0) -- 2.0 itself overflows into the sign bit and silently encodes as -2.0.
        out
            .u16(flags)
            .u16(0)
            .i16(0)
            .i16(0)
            .f2Dot14(1.5)
        val glyfBytes = triangleGlyphBytes() + out.toByteArray()
        val loca = LocaTable(intArrayOf(0, triangleGlyphBytes().size, glyfBytes.size))
        val records = List(2) { gid -> readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = gid) }
        val decomposed = decomposeGlyf(records, gid = 1)
        assertEquals(listOf(Point(0, 0), Point(150, 0), Point(75, 150)), decomposed.single().points.map { it.point })
    }

    @Test
    fun appliesATwoByTwoTransform() {
        val out = TestBytes()
        out.i16(-1)
        out
            .i16(0)
            .i16(0)
            .i16(0)
            .i16(0)
        val flags = 0x0001 or 0x0002 or 0x0080 // WORDS | XY_VALUES | WE_HAVE_A_TWO_BY_TWO
        // A 90-degree rotation: xScale=0, scale01=1, scale10=-1, yScale=0, so (x,y) -> (-y, x).
        out
            .u16(flags)
            .u16(0)
            .i16(10)
            .i16(20)
            .f2Dot14(0.0)
            .f2Dot14(1.0)
            .f2Dot14(-1.0)
            .f2Dot14(0.0)
        val glyfBytes = triangleGlyphBytes() + out.toByteArray()
        val loca = LocaTable(intArrayOf(0, triangleGlyphBytes().size, glyfBytes.size))
        val records = List(2) { gid -> readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = gid) }
        val decomposed = decomposeGlyf(records, gid = 1)
        // (0,0)->(0,0)+off ; (100,0)->(-0,100)+off ; (50,100)->(-100,50)+off, offset (10,20)
        assertEquals(listOf(Point(10, 20), Point(10, 120), Point(-90, 70)), decomposed.single().points.map { it.point })
    }

    @Test
    fun scaledComponentOffsetAppliesTheTransformToTheOffsetToo() {
        val out = TestBytes()
        out.i16(-1)
        out
            .i16(0)
            .i16(0)
            .i16(0)
            .i16(0)
        val flags = 0x0001 or 0x0002 or 0x0008 or 0x0800 // WORDS | XY_VALUES | WE_HAVE_A_SCALE | SCALED_COMPONENT_OFFSET
        out
            .u16(flags)
            .u16(0)
            .i16(10)
            .i16(4)
            .f2Dot14(1.5)
        val glyfBytes = triangleGlyphBytes() + out.toByteArray()
        val loca = LocaTable(intArrayOf(0, triangleGlyphBytes().size, glyfBytes.size))
        val records = List(2) { gid -> readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = gid) }
        val decomposed = decomposeGlyf(records, gid = 1)
        // offset itself is scaled by 1.5: (10,4) -> (15,6); then every point is scaled by 1.5 and shifted by (15,6).
        assertEquals(Point(15, 6), decomposed.single().points[0].point)
        assertEquals(Point(165, 6), decomposed.single().points[1].point)
    }

    @Test
    fun readsAByteEncodedComponentOffset() {
        val out = TestBytes()
        out.i16(-1)
        out
            .i16(0)
            .i16(0)
            .i16(0)
            .i16(0)
        val flags = 0x0002 // ARGS_ARE_XY_VALUES only: args are signed bytes, no MORE_COMPONENTS
        out
            .u16(flags)
            .u16(0)
            .i8(-5)
            .i8(7)
        val glyfBytes = triangleGlyphBytes() + out.toByteArray()
        val loca = LocaTable(intArrayOf(0, triangleGlyphBytes().size, glyfBytes.size))
        val records = List(2) { gid -> readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = gid) }
        val decomposed = decomposeGlyf(records, gid = 1)
        assertEquals(Point(-5, 7), decomposed.single().points[0].point)
    }

    @Test
    fun rejectsAPointMatchedComponent() {
        val out = TestBytes()
        out.i16(-1)
        out
            .i16(0)
            .i16(0)
            .i16(0)
            .i16(0)
        val flags = 0x0001 // ARG_1_AND_2_ARE_WORDS only: ARGS_ARE_XY_VALUES is unset -> point matching
        out
            .u16(flags)
            .u16(0)
            .u16(0)
            .u16(0)
        val glyfBytes = triangleGlyphBytes() + out.toByteArray()
        val loca = LocaTable(intArrayOf(0, triangleGlyphBytes().size, glyfBytes.size))
        assertFailsWith<IllegalArgumentException> {
            readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = 1)
        }
    }

    @Test
    fun decomposesANestedComposite() {
        // gid 0: triangle. gid 1: composite of gid 0, shifted by (10, 0). gid 2: composite of gid 1, shifted by (0, 10).
        val glyph0 = triangleGlyphBytes()
        val glyph1 = compositeGlyphBytes(listOf(Triple(0, 10, 0)))
        val glyph2 = compositeGlyphBytes(listOf(Triple(1, 0, 10)))
        val glyfBytes = glyph0 + glyph1 + glyph2
        val loca = LocaTable(intArrayOf(0, glyph0.size, glyph0.size + glyph1.size, glyfBytes.size))
        val records = List(3) { gid -> readGlyfRecord(glyfBytes, glyfTableOffset = 0, loca = loca, gid = gid) }
        val decomposed = decomposeGlyf(records, gid = 2)
        assertEquals(listOf(Point(10, 10), Point(110, 10), Point(60, 110)), decomposed.single().points.map { it.point })
    }

    @Test
    fun decomposeGuardsAgainstUnboundedRecursion() {
        assertFailsWith<IllegalArgumentException> {
            decomposeGlyf(listOf(RawGlyfRecord.Empty), gid = 0, maxDepth = 0)
        }
    }
}
