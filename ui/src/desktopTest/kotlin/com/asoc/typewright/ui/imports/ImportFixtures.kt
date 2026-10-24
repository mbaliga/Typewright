// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import com.asoc.typewright.core.font.sfnt.readSfntTableDirectory
import java.io.File
import kotlin.test.assertTrue

/** Tests only: real fonts from the repository, and fonts made from them by changing one name record. */
internal object ImportFixtures {
    val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    fun fontBytes(path: String): ByteArray = File(repoRoot, path).readBytes()

    /** [bytes] with every name record for [nameId] rewritten to [text] (padded with spaces or cut to the record's own length). */
    fun withNameRecord(
        bytes: ByteArray,
        nameId: Int,
        text: String,
    ): ByteArray {
        val out = bytes.copyOf()
        val table = checkNotNull(readSfntTableDirectory(out)["name"]).offset

        fun u16(at: Int) = ((out[at].toInt() and 0xFF) shl 8) or (out[at + 1].toInt() and 0xFF)
        val count = u16(table + 2)
        val stringOffset = u16(table + 4)
        var patched = 0
        for (i in 0 until count) {
            val record = table + 6 + i * 12
            if (u16(record + 6) != nameId) continue
            val length = u16(record + 8)
            val start = table + stringOffset + u16(record + 10)
            if (u16(record) == 1) {
                text.padEnd(length).take(length).forEachIndexed { k, c -> out[start + k] = c.code.toByte() }
            } else {
                text.padEnd(length / 2).take(length / 2).forEachIndexed { k, c ->
                    out[start + 2 * k] = (c.code shr 8).toByte()
                    out[start + 2 * k + 1] = c.code.toByte()
                }
            }
            patched++
        }
        assertTrue(patched > 0, "the font has no name record $nameId")
        return out
    }

    /** Poppins with a proprietary licence in name ID 13 and an end-user licence URL in ID 14. */
    fun proprietaryPoppins(): ByteArray =
        withNameRecord(
            withNameRecord(
                fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf"),
                13,
                "This font is proprietary software of Example Foundry. It may not be modified or redistributed.",
            ),
            14,
            "http://example.invalid/eula",
        )

    /** Poppins with no licence text and no licence URL at all. */
    fun unlicensedPoppins(): ByteArray =
        withNameRecord(withNameRecord(fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf"), 13, ""), 14, "")
}
