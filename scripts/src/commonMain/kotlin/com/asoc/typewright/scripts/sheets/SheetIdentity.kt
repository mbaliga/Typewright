// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

/**
 * The binary code printed along the top of every sheet, between the two upper corner marks, that says which sheet it is
 * so a photograph can be read back without OCR: a row of [BIT_COUNT] square modules, black for 1 and paper for 0, read
 * left to right as the page is meant to be read.
 *
 * | bits | meaning |
 * |---|---|
 * | 0-3 | start pattern `1 1 0 1`; a reading that does not begin with it is not a sheet, or is upside down |
 * | 4-6 | script ([PrintScript.id]) |
 * | 7-10 | sheet number, counting from 0 |
 * | 11-14 | number of sheets in the script, minus 1 |
 * | 15 | paper: 0 = A4, 1 = Letter ([PaperSize.code]) |
 * | 16-21 | CRC-6 (polynomial x^6 + x + 1, start value 0) of bits 4-15 |
 *
 * The code carries the paper size because the sheet's geometry depends on it and the detector has to know that to
 * sample the code; it simply tries both. A photograph is accepted as a sheet only when the start pattern and the CRC
 * both hold, so a misread cannot name a sheet that is not there.
 */
data class SheetIdentity(
    val script: PrintScript,
    val index: Int,
    val count: Int,
    val paper: PaperSize,
) {
    init {
        require(count in 1..MAX_SHEETS && index in 0 until count) { "sheet $index of $count is out of range 0..${MAX_SHEETS - 1}" }
    }

    /** The [BIT_COUNT] bits of the code, `true` for a black module. */
    fun encode(): List<Boolean> {
        val payload = bitsOf(script.id, 3) + bitsOf(index, 4) + bitsOf(count - 1, 4) + bitsOf(paper.code, 1)
        return START + payload + bitsOf(crc6(payload), 6)
    }

    companion object {
        /** The start pattern. */
        val START: List<Boolean> = listOf(true, true, false, true)

        /** Modules in the code. */
        const val BIT_COUNT: Int = 22

        /** The most sheets a script may have (four bits). */
        const val MAX_SHEETS: Int = 16

        /** The identity [bits] spell, or null when the start pattern, the CRC or any field is wrong. */
        fun decode(bits: List<Boolean>): SheetIdentity? {
            if (bits.size != BIT_COUNT || bits.subList(0, START.size) != START) return null
            val payload = bits.subList(4, 16)
            if (crc6(payload) != valueOf(bits.subList(16, 22))) return null
            val script = PrintScript.fromId(valueOf(payload.subList(0, 3))) ?: return null
            val index = valueOf(payload.subList(3, 7))
            val count = valueOf(payload.subList(7, 11)) + 1
            val paper = PaperSize.fromCode(valueOf(payload.subList(11, 12))) ?: return null
            if (index >= count) return null
            return SheetIdentity(script, index, count, paper)
        }

        private fun bitsOf(
            value: Int,
            width: Int,
        ): List<Boolean> = List(width) { (value shr (width - 1 - it)) and 1 == 1 }

        private fun valueOf(bits: List<Boolean>): Int = bits.fold(0) { acc, bit -> (acc shl 1) or (if (bit) 1 else 0) }

        /** CRC-6 with polynomial x^6 + x + 1 over [bits], most significant first, start value 0. */
        fun crc6(bits: List<Boolean>): Int {
            var register = 0
            for (bit in bits) {
                val feedback = ((register shr 5) and 1 == 1) != bit
                register = (register shl 1) and 0x3F
                if (feedback) register = register xor 0x03
            }
            return register
        }
    }
}
