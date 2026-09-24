package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevanagariMetricsTest {
    @Test
    fun hasExactlySevenLevelsInTheResearchsOwnNamedOrder() {
        val system = DevanagariMetrics.build()
        assertEquals(
            listOf("urdhvarekha", "shirorekha", "skandharekha", "nabhirekha", "zanurekha", "padrekha", "talrekha"),
            system.lines.map { it.name },
        )
    }

    @Test
    fun onlyHeadlineAndBaselineAreFixed() {
        val fixedNames =
            DevanagariMetrics
                .build()
                .lines
                .filter { it.fixed }
                .map { it.name }
        assertEquals(listOf("shirorekha", "talrekha"), fixedNames)
    }

    @Test
    fun everyLineHasANote() {
        for (line in DevanagariMetrics.build().lines) {
            val note = assertNotNull(line.note, "${line.name} should have a note")
            assertTrue(note.isNotBlank(), "${line.name}'s note should not be blank")
        }
    }

    @Test
    fun headlineIs700MatchingTheTemplatePacksOwnGuide() {
        val headline = DevanagariMetrics.build().lines.first { it.name == "shirorekha" }
        assertEquals(700, headline.value)
    }

    @Test
    fun baselineIsZero() {
        val baseline = DevanagariMetrics.build().lines.first { it.name == "talrekha" }
        assertEquals(0, baseline.value)
    }

    @Test
    fun levelsDescendMonotonicallyFromUrdhvarekhaToTalrekha() {
        val values = DevanagariMetrics.build().lines.map { it.value }
        for (i in 0 until values.size - 1) {
            assertTrue(values[i] > values[i + 1], "expected ${values[i]} > ${values[i + 1]} at index $i")
        }
    }

    @Test
    fun unitsPerEmIsOneThousand() {
        assertEquals(1000, DevanagariMetrics.build().unitsPerEm)
    }

    @Test
    fun scriptIsDevanagariAndNotRightToLeft() {
        val system = DevanagariMetrics.build()
        assertEquals(WritingScript.DEVANAGARI, system.script)
        assertFalse(system.rightToLeft)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(DevanagariMetrics.build(), DevanagariMetrics.build())
    }
}
