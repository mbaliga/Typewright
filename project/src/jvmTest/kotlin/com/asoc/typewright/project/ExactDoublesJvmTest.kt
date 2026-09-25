// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import java.io.File
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks [formatDouble] and [parseJsonDouble] against references the JVM has and the common test
 * can't reach: `java.lang.Double.parseDouble` (correctly rounded on every JDK), the JDK's own
 * shortest `Double.toString` (JDK 19 and later), and Python's `repr` when Python is installed.
 */
class ExactDoublesJvmTest {
    @Test
    fun readingAgreesWithTheJdkOnRandomDecimalsAndNearMidpoints() {
        val random = Random(1_790_345_591)
        repeat(100_000) {
            val digits = (1..random.nextInt(1, 26)).joinToString("") { random.nextInt(10).toString() }.trimStart('0').ifEmpty { "0" }
            val point = random.nextInt(0, digits.length + 1)
            val mantissa =
                if (point ==
                    digits.length
                ) {
                    digits
                } else {
                    digits.substring(0, point).ifEmpty { "0" } + "." + digits.substring(point)
                }
            val text = mantissa + "e" + random.nextInt(-345, 312)
            val json = if (mantissa.startsWith("0") && mantissa.length > 1 && mantissa[1] != '.') null else text
            if (json != null) {
                assertEquals(
                    java.lang.Double
                        .parseDouble(json)
                        .toRawBits(),
                    parseJsonDouble(json)!!.toRawBits(),
                    json,
                )
            }
        }
        // The exact midpoint between two neighbouring doubles, and a hair either side of it.
        repeat(20_000) {
            var bits = random.nextLong() and Long.MAX_VALUE
            if ((bits ushr 52) >= 0x7FE) bits = bits and 0x3FFFFFFFFFFFFFFFL
            val low = BigDecimal(Double.fromBits(bits))
            val high = BigDecimal(Double.fromBits(bits + 1))
            val midpoint = low.add(high).divide(BigDecimal(2))
            val nudge = midpoint.ulp().divide(BigDecimal(10))
            for (value in listOf(midpoint, midpoint.add(nudge), midpoint.subtract(nudge))) {
                val text = value.toString().replace("E+", "E")
                assertEquals(
                    java.lang.Double
                        .parseDouble(text)
                        .toRawBits(),
                    parseJsonDouble(text)!!.toRawBits(),
                    text,
                )
            }
        }
    }

    @Test
    fun spellingAgreesWithTheJdksShortestDigits() {
        if (Runtime.version().feature() < 19) return println("SKIPPED: Double.toString is shortest only from JDK 19")
        val random = Random(20_260_925)
        var compared = 0
        repeat(200_000) {
            val bits = random.nextLong()
            val value = Double.fromBits(bits)
            if (!value.isFinite() || value == 0.0) return@repeat
            val ours = digitsAndExponent(formatDouble(value))
            val jdk = digitsAndExponent(value.toString())
            // The JDK also considers two digits when one would do, and keeps the nearer.
            if (ours.first.length == 1 && jdk.first.length == 2) {
                assertEquals(value, parseJsonDouble(formatDouble(value)), "one digit reads back for $value")
            } else {
                assertEquals(jdk, ours, "digits of $value (bits $bits)")
                compared++
            }
        }
        assertTrue(compared > 190_000)
    }

    @Test
    fun spellingAgreesWithPythonsRepr() {
        val python = listOf("python3", "python").firstOrNull { command -> pathEntries().any { File(it, command).canExecute() } }
        if (python == null) return println("SKIPPED: no python to compare repr with")
        val random = Random(4_242)
        val values =
            List(20_000) { index ->
                if (index % 2 == 0) {
                    var bits = random.nextLong()
                    while (!Double.fromBits(bits).isFinite()) bits = random.nextLong()
                    Double.fromBits(bits)
                } else {
                    random.nextDouble(-1000.0, 1000.0)
                }
            }
        val script =
            "import sys, struct\n" +
                "for line in sys.stdin:\n" +
                "    print(repr(struct.unpack('<d', struct.pack('<q', int(line)))[0]))\n"
        val process = ProcessBuilder(python, "-c", script).redirectErrorStream(true).start()
        // Feed stdin from another thread so neither pipe can fill up and stall both processes.
        val feeder =
            thread {
                process.outputStream.bufferedWriter().use { writer ->
                    values.forEach { writer.write("${it.toRawBits()}\n") }
                }
            }
        val reprs = process.inputStream.bufferedReader().readLines()
        feeder.join()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS))
        assertEquals(values.size, reprs.size, reprs.take(3).joinToString())
        for ((value, repr) in values.zip(reprs)) {
            assertEquals(digitsAndExponent(repr), digitsAndExponent(formatDouble(value)), "Python repr $repr")
        }
    }

    // The significant digits and the exponent that puts the point before them, from any of the
    // three spellings (ours, the JDK's, Python's).
    private fun digitsAndExponent(text: String): Pair<String, Int> {
        val unsigned = text.removePrefix("-")
        val exponentAt = unsigned.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa = if (exponentAt < 0) unsigned else unsigned.substring(0, exponentAt)
        val power = if (exponentAt < 0) 0 else unsigned.substring(exponentAt + 1).removePrefix("+").toInt()
        val integer = mantissa.substringBefore('.')
        val all = integer + mantissa.substringAfter('.', "")
        val leading = all.indexOfFirst { it != '0' }
        return all.substring(leading).trimEnd('0') to integer.length - leading + power
    }

    private fun pathEntries(): List<String> = System.getenv("PATH").orEmpty().split(File.pathSeparator)
}
