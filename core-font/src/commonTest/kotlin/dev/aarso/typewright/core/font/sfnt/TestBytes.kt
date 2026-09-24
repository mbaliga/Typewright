package dev.aarso.typewright.core.font.sfnt

/** A tiny big-endian byte builder, the write-side mirror of [ByteCursor], used only to build synthetic sfnt fixtures in tests. */
class TestBytes {
    private val bytes = mutableListOf<Byte>()

    val size: Int get() = bytes.size

    fun u8(v: Int): TestBytes {
        bytes.add((v and 0xFF).toByte())
        return this
    }

    fun i8(v: Int): TestBytes = u8(v)

    fun u16(v: Int): TestBytes {
        bytes.add(((v ushr 8) and 0xFF).toByte())
        bytes.add((v and 0xFF).toByte())
        return this
    }

    fun i16(v: Int): TestBytes = u16(v and 0xFFFF)

    fun u32(v: Long): TestBytes {
        for (shift in intArrayOf(24, 16, 8, 0)) bytes.add(((v shr shift) and 0xFF).toByte())
        return this
    }

    fun u32(v: Int): TestBytes = u32(v.toLong() and 0xFFFFFFFFL)

    fun i32(v: Int): TestBytes = u32(v.toLong() and 0xFFFFFFFFL)

    fun fixed(v: Double): TestBytes = i32((v * 65536.0).toInt())

    fun f2Dot14(v: Double): TestBytes = i16((v * 16384.0).toInt())

    fun tag(t: String): TestBytes {
        require(t.length == 4) { "a tag must be exactly 4 characters, was '$t'" }
        for (c in t) bytes.add(c.code.toByte())
        return this
    }

    fun bytes(b: ByteArray): TestBytes {
        for (x in b) bytes.add(x)
        return this
    }

    fun ascii(s: String): TestBytes {
        for (c in s) bytes.add(c.code.toByte())
        return this
    }

    fun utf16Be(s: String): TestBytes {
        for (c in s) u16(c.code)
        return this
    }

    fun pad(n: Int): TestBytes {
        repeat(n) { bytes.add(0) }
        return this
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}
