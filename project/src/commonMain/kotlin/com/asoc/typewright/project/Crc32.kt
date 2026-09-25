// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/**
 * CRC-32 as zip uses it (ISO 3309 / ITU-T V.42: reflected polynomial 0xEDB88320, initial and
 * final value 0xFFFFFFFF), in common code so the same zip bytes come out on every platform.
 */
internal object Crc32 {
    private val TABLE =
        IntArray(256) { n ->
            var c = n
            repeat(8) { c = if ((c and 1) != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            c
        }

    /** The CRC-32 of [bytes] as an unsigned 32-bit value in an [Int]. */
    fun of(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) crc = TABLE[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }
}
