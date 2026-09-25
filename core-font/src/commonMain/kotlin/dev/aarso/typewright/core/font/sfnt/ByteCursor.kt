// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.sfnt

/**
 * A big-endian cursor over a raw sfnt byte buffer, written by hand in plain Kotlin because
 * `core-font` may import neither `java.*` nor `android.*` (CLAUDE.md law 2;
 * docs/ARCHITECTURE_REVIEW.md section 3 `:core-font` risk 2 notes that `java.util.zip` and
 * `javax.xml` specifically are not available here). Every accessor advances [position] past the
 * value it reads, matching how the OpenType spec itself describes a table as a sequence of
 * fixed-width fields read in order.
 *
 * All multi-byte values in an sfnt font are big-endian ("network byte order"); this cursor has no
 * little-endian mode because nothing in `core-font`'s scope needs one.
 */
class ByteCursor(
    private val bytes: ByteArray,
    position: Int = 0,
) {
    var position: Int = position
        private set

    /** The size of the underlying buffer, in bytes. */
    val size: Int get() = bytes.size

    /** Moves this cursor to an absolute byte offset and returns it, for chaining. */
    fun seek(newPosition: Int): ByteCursor {
        require(newPosition in 0..bytes.size) {
            "seek $newPosition out of range [0, ${bytes.size}]"
        }
        position = newPosition
        return this
    }

    /**
     * A new, independent [ByteCursor] over the same backing buffer, positioned at [newPosition].
     * Table formats that embed relative offsets to other data in the same table (`cmap` format
     * 4's `idRangeOffset`, `post` format 2.0's name-index table) read from one of these instead of
     * disturbing this cursor's own [position].
     */
    fun at(newPosition: Int): ByteCursor {
        require(newPosition in 0..bytes.size) {
            "at($newPosition) out of range [0, ${bytes.size}]"
        }
        return ByteCursor(bytes, newPosition)
    }

    /** Advances past [n] bytes without reading them, and returns this cursor for chaining. */
    fun skip(n: Int): ByteCursor {
        requireRemaining(n)
        position += n
        return this
    }

    private fun requireRemaining(n: Int) {
        require(n >= 0) { "cannot read a negative byte count ($n)" }
        require(position + n <= bytes.size) {
            "read of $n bytes at offset $position overruns a buffer of ${bytes.size} bytes"
        }
    }

    /** An unsigned 8-bit integer (sfnt `uint8`/`Offset8`), as an [Int] in `0..255`. */
    fun u8(): Int {
        requireRemaining(1)
        val v = byteAt(position)
        position += 1
        return v
    }

    /** A signed 8-bit integer (sfnt `int8`), as an [Int] in `-128..127`. */
    fun i8(): Int {
        requireRemaining(1)
        val v = bytes[position].toInt()
        position += 1
        return v
    }

    /** An unsigned 16-bit integer (sfnt `uint16`/`Offset16`/`FWORD`... unsigned forms). */
    fun u16(): Int {
        requireRemaining(2)
        val v = (byteAt(position) shl 8) or byteAt(position + 1)
        position += 2
        return v
    }

    /** A signed 16-bit integer (sfnt `int16`/`FWORD`), sign-extended from [u16]'s bit pattern. */
    fun i16(): Int {
        val v = u16()
        return if (v >= 0x8000) v - 0x10000 else v
    }

    /**
     * An unsigned 32-bit integer (sfnt `uint32`/`Offset32`), as a [Long] because a table length,
     * checksum or `loca` offset can exceed [Int.MAX_VALUE]'s bit pattern when read as unsigned.
     */
    fun u32(): Long {
        requireRemaining(4)
        var v = 0L
        for (i in 0 until 4) v = (v shl 8) or byteAt(position + i).toLong()
        position += 4
        return v
    }

    /** A signed 32-bit integer (sfnt `int32`). */
    fun i32(): Int {
        requireRemaining(4)
        var v = 0
        for (i in 0 until 4) v = (v shl 8) or byteAt(position + i)
        position += 4
        return v
    }

    /** A 4-byte ASCII table tag (sfnt `Tag`), such as `"glyf"` or `"cmap"`. */
    fun tag(): String {
        requireRemaining(4)
        val sb = StringBuilder(4)
        for (i in 0 until 4) sb.append(byteAt(position + i).toChar())
        position += 4
        return sb.toString()
    }

    /** The next [n] bytes, copied out, without interpreting them. */
    fun bytes(n: Int): ByteArray {
        requireRemaining(n)
        val out = bytes.copyOfRange(position, position + n)
        position += n
        return out
    }

    /** A 16.16 fixed-point value (sfnt `Fixed`), as a [Double]. */
    fun fixed(): Double = i32() / 65536.0

    /** A 2.14 fixed-point value (sfnt `F2Dot14`; composite-glyph scale factors), as a [Double]. */
    fun f2Dot14(): Double = i16() / 16384.0

    /**
     * An 8-byte signed count of seconds since 1904-01-01T00:00:00Z (sfnt `LONGDATETIME`, `head`'s
     * `created`/`modified`), left as a raw [Long]: nothing in `core-font`'s scope converts it to a
     * calendar date, and doing that without `java.time` is a later module's problem if one ever
     * needs it.
     */
    fun longDateTime(): Long {
        requireRemaining(8)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or byteAt(position + i).toLong()
        position += 8
        return v
    }

    private fun byteAt(index: Int): Int = bytes[index].toInt() and 0xFF
}
