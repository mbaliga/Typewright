package dev.aarso.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class ByteCursorTest {
    @Test
    fun readsUnsignedAndSignedBytes() {
        val cursor = ByteCursor(byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()))
        assertEquals(0, cursor.u8())
        assertEquals(127, cursor.u8())
        assertEquals(128, cursor.u8())
        assertEquals(255, cursor.u8())

        val signed = ByteCursor(byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()))
        assertEquals(0, signed.i8())
        assertEquals(127, signed.i8())
        assertEquals(-128, signed.i8())
        assertEquals(-1, signed.i8())
    }

    @Test
    fun readsUnsignedAndSigned16BitBigEndian() {
        val cursor = ByteCursor(byteArrayOf(0x01, 0x00, 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(256, cursor.u16())
        assertEquals(65535, cursor.u16())

        val signed = ByteCursor(byteArrayOf(0x01, 0x00, 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(256, signed.i16())
        assertEquals(-1, signed.i16())
    }

    @Test
    fun readsUnsignedAndSigned32BitBigEndian() {
        val cursor = ByteCursor(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(65536L, cursor.u32())
        assertEquals(4294967295L, cursor.u32())

        val signed = ByteCursor(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(65536, signed.i32())
        assertEquals(-1, signed.i32())
    }

    @Test
    fun readsATag() {
        val cursor = ByteCursor("glyf".encodeToByteArray())
        assertEquals("glyf", cursor.tag())
    }

    @Test
    fun readsRawBytes() {
        val cursor = ByteCursor(byteArrayOf(1, 2, 3, 4, 5))
        cursor.skip(1)
        val bytes = cursor.bytes(3)
        assertEquals(listOf<Byte>(2, 3, 4), bytes.toList())
        assertEquals(4, cursor.position)
    }

    @Test
    fun readsFixedAndF2Dot14() {
        // 1.0 as a 16.16 fixed value is 0x00010000.
        val fixed = ByteCursor(byteArrayOf(0x00, 0x01, 0x00, 0x00))
        assertEquals(1.0, fixed.fixed())

        // 1.0 as a 2.14 fixed value is 0x4000 (16384).
        val f2dot14 = ByteCursor(byteArrayOf(0x40, 0x00))
        assertEquals(1.0, f2dot14.f2Dot14())

        // -2.0 as a 2.14 fixed value is 0x8000.
        val negative = ByteCursor(byteArrayOf(0x80.toByte(), 0x00))
        assertEquals(-2.0, negative.f2Dot14())
    }

    @Test
    fun readsLongDateTime() {
        val cursor = ByteCursor(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 42))
        assertEquals(42L, cursor.longDateTime())
    }

    @Test
    fun seekMovesToAnAbsolutePosition() {
        val cursor = ByteCursor(byteArrayOf(1, 2, 3, 4))
        cursor.seek(2)
        assertEquals(3, cursor.u8())
    }

    @Test
    fun atReturnsAnIndependentCursorOverTheSameBuffer() {
        val cursor = ByteCursor(byteArrayOf(10, 20, 30, 40))
        val other = cursor.at(2)
        assertEquals(30, other.u8())
        // the original cursor's own position is untouched
        assertEquals(10, cursor.u8())
    }

    @Test
    fun seekRejectsAnOutOfRangePosition() {
        val cursor = ByteCursor(byteArrayOf(1, 2, 3))
        assertFailsWith<IllegalArgumentException> { cursor.seek(4) }
        assertFailsWith<IllegalArgumentException> { cursor.seek(-1) }
    }

    @Test
    fun readingPastTheEndFails() {
        val cursor = ByteCursor(byteArrayOf(1, 2))
        cursor.u16()
        assertFails { cursor.u8() }
    }

    @Test
    fun sizeReportsTheBufferLength() {
        assertEquals(5, ByteCursor(ByteArray(5)).size)
    }
}
