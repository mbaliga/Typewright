// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SheetIdentityTest {
    @Test
    fun everyPossibleIdentityDecodesBackToItself() {
        var checked = 0
        for (script in PrintScript.entries) {
            for (paper in PaperSize.entries) {
                for (count in 1..SheetIdentity.MAX_SHEETS) {
                    for (index in 0 until count) {
                        val identity = SheetIdentity(script, index, count, paper)
                        val bits = identity.encode()
                        assertEquals(SheetIdentity.BIT_COUNT, bits.size)
                        assertEquals(identity, SheetIdentity.decode(bits))
                        checked++
                    }
                }
            }
        }
        assertEquals(4 * 2 * (16 * 17 / 2), checked)
    }

    @Test
    fun anySingleFlippedModuleIsRejectedNotMisread() {
        val identity = SheetIdentity(PrintScript.LATIN, 2, 5, PaperSize.A4)
        val bits = identity.encode()
        for (i in bits.indices) {
            val flipped = bits.toMutableList().also { it[i] = !it[i] }
            assertNull(SheetIdentity.decode(flipped), "flipping module $i must not decode to a sheet")
        }
    }

    @Test
    fun aReadingTurnedUpsideDownOrOfBlankPaperIsNotASheet() {
        val bits = SheetIdentity(PrintScript.KANA, 3, 5, PaperSize.LETTER).encode()
        assertNull(SheetIdentity.decode(bits.reversed()))
        assertNull(SheetIdentity.decode(List(SheetIdentity.BIT_COUNT) { false }))
        assertNull(SheetIdentity.decode(List(SheetIdentity.BIT_COUNT) { true }))
        assertNull(SheetIdentity.decode(bits.dropLast(1)))
    }

    @Test
    fun theStartPatternLeadsTheCodeAndTheCrcFollowsThePayload() {
        val bits = SheetIdentity(PrintScript.NASKH, 1, 3, PaperSize.A4).encode()
        assertEquals(SheetIdentity.START, bits.subList(0, 4))
        assertEquals(SheetIdentity.crc6(bits.subList(4, 16)), bits.subList(16, 22).fold(0) { a, b -> (a shl 1) or (if (b) 1 else 0) })
        // Two different sheets differ in at least one module, and a different paper changes the code too.
        assertNotEquals(bits, SheetIdentity(PrintScript.NASKH, 1, 3, PaperSize.LETTER).encode())
        assertNotEquals(bits, SheetIdentity(PrintScript.NASKH, 2, 3, PaperSize.A4).encode())
    }

    @Test
    fun crc6DetectsAnySingleBitErrorInAPayload() {
        val payload = List(12) { it % 3 == 0 }
        val good = SheetIdentity.crc6(payload)
        for (i in payload.indices) {
            val damaged = payload.toMutableList().also { it[i] = !it[i] }
            assertNotEquals(good, SheetIdentity.crc6(damaged), "bit $i")
        }
    }

    @Test
    fun anImpossibleSheetNumberIsRefused() {
        assertFailsWith<IllegalArgumentException> { SheetIdentity(PrintScript.LATIN, 5, 5, PaperSize.A4) }
        assertFailsWith<IllegalArgumentException> { SheetIdentity(PrintScript.LATIN, 0, 17, PaperSize.A4) }
        assertFailsWith<IllegalArgumentException> { SheetIdentity(PrintScript.LATIN, -1, 5, PaperSize.A4) }
    }
}
