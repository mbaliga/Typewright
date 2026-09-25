// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.arabic

import com.asoc.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArabicMetricsTest {
    @Test
    fun hasExactlySevenLinesSkyToEarthInDescendingOrder() {
        val system = ArabicMetrics.build()
        assertEquals(
            listOf("sky", "toothHeight", "eyeHeight", "loopHeight", "joiningLineTop", "baseline", "earth"),
            system.lines.map { it.name },
        )
    }

    @Test
    fun namesNoLineAfterALatinMetric() {
        val latinNames = setOf("x-height", "xheight", "cap-height", "capheight", "ascender", "descender")
        for (line in ArabicMetrics.build().lines) {
            assertTrue(line.name.lowercase() !in latinNames, "${line.name} should not be a Latin metric name")
        }
    }

    @Test
    fun onlyBaselineIsFixed() {
        val fixedNames =
            ArabicMetrics
                .build()
                .lines
                .filter { it.fixed }
                .map { it.name }
        assertEquals(listOf("baseline"), fixedNames)
    }

    @Test
    fun everyLineHasANote() {
        for (line in ArabicMetrics.build().lines) {
            val note = assertNotNull(line.note, "${line.name} should have a note")
            assertTrue(note.isNotBlank(), "${line.name}'s note should not be blank")
        }
    }

    @Test
    fun theFourRealTemplateSourcedValuesMatchTheZipsOwnGuideLabelsExactly() {
        val byName = ArabicMetrics.build().lines.associateBy { it.name }
        assertEquals(720, byName.getValue("sky").value)
        assertEquals(300, byName.getValue("toothHeight").value)
        assertEquals(0, byName.getValue("baseline").value)
        assertEquals(-360, byName.getValue("earth").value)
    }

    @Test
    fun linesDescendMonotonicallyFromSkyToEarth() {
        val values = ArabicMetrics.build().lines.map { it.value }
        for (i in 0 until values.size - 1) {
            assertTrue(values[i] > values[i + 1], "expected ${values[i]} > ${values[i + 1]} at index $i")
        }
    }

    @Test
    fun unitsPerEmIsOneThousand() {
        assertEquals(1000, ArabicMetrics.build().unitsPerEm)
    }

    @Test
    fun scriptIsArabicNaskhAndRightToLeft() {
        val system = ArabicMetrics.build()
        assertEquals(WritingScript.ARABIC_NASKH, system.script)
        assertTrue(system.rightToLeft)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(ArabicMetrics.build(), ArabicMetrics.build())
    }
}
