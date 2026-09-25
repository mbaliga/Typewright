// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.math.abs

/*
 * Doubles in project files are written and read by this file's own code, never by the platform's
 * `Double.toString` or `String.toDouble`. Those differ: Kotlin/Wasm's `toString` sometimes gives
 * 17 digits where 16 read back, and its parser can land one ulp away from the correctly rounded
 * double, so the same value would be saved as different bytes on the web and the desktop. Both
 * directions here are exact, using arbitrary-precision integers, so every platform agrees
 * bit for bit.
 */

/**
 * [value] in the canonical spelling every project file uses:
 * - the shortest decimal digits that read back to exactly [value], and of those the one
 *   nearest to [value] (an even last digit breaks a tie);
 * - plain notation, with at least one digit after the point, for magnitudes from 1e-6 up to
 *   1e21 (`-1.8`, `3.0`, `0.000001`, `100000000000000000000.0`);
 * - `d.dddE±n` outside that range (`1.0E-7`, `1.0E21`, `5.0E-324`);
 * - `0.0` and `-0.0` for the zeros.
 *
 * Non-finite values have no JSON spelling and throw [IllegalArgumentException].
 */
internal fun formatDouble(value: Double): String {
    require(value.isFinite()) { "JSON has no spelling for $value" }
    if (value == 0.0) return if (1.0 / value < 0) "-0.0" else "0.0"
    val (digits, exponent) = ShortestDecimal.of(abs(value))
    // value = 0.d1d2d3… × 10^exponent: the point sits after `exponent` digits.
    val body =
        when {
            exponent in 1..21 && digits.length <= exponent -> {
                digits + "0".repeat(exponent - digits.length) + ".0"
            }

            exponent in 1..21 -> {
                digits.substring(0, exponent) + "." + digits.substring(exponent)
            }

            exponent in -5..0 -> {
                "0." + "0".repeat(-exponent) + digits
            }

            else -> {
                val mantissa = if (digits.length == 1) "$digits.0" else digits[0] + "." + digits.substring(1)
                val power = exponent - 1
                mantissa + "E" + (if (power < 0) "-" else "") + abs(power)
            }
        }
    return if (value < 0) "-$body" else body
}

/**
 * The double nearest to the JSON number [text] (ties to even), or null when [text] is not a
 * JSON number (`-?int frac? exp?`). A magnitude beyond the largest double gives an infinity,
 * and one below half the smallest gives a zero, both with the number's sign.
 */
internal fun parseJsonDouble(text: String): Double? {
    val match = JSON_NUMBER.matchEntire(text) ?: return null
    val negative = text.startsWith('-')
    val fraction = match.groupValues[2]
    var digits = (match.groupValues[1] + fraction).trimStart('0')
    if (digits.isEmpty()) return if (negative) -0.0 else 0.0
    var power = clampedExponent(match.groupValues[3]) - fraction.length
    val significant = digits.trimEnd('0')
    power += digits.length - significant.length
    digits = significant
    // Beyond 800 significant digits only whether anything non-zero follows can matter: the
    // exact midpoint between two doubles has fewer than 800. Keep that as one final 1.
    if (digits.length > MAX_PARSED_DIGITS) {
        power += digits.length - (MAX_PARSED_DIGITS + 1)
        digits = digits.substring(0, MAX_PARSED_DIGITS) + "1"
    }
    val magnitude = digits.length + power // the value lies in [10^(magnitude-1), 10^magnitude)
    val result =
        when {
            magnitude > 310 -> Double.POSITIVE_INFINITY
            magnitude < -330 -> 0.0
            else -> nearestDouble(BigNat.parseDecimal(digits), power)
        }
    return if (negative) -result else result
}

/** [value] as a JSON number element in the canonical spelling, for building project JSON by hand. */
@OptIn(ExperimentalSerializationApi::class)
internal fun jsonDouble(value: Double): JsonPrimitive = JsonUnquotedLiteral(formatDouble(value))

/**
 * Writes a `Double` property of a project file with [formatDouble] and reads it with
 * [parseJsonDouble], so the value and its bytes are the same on every platform. Outside JSON it
 * falls back to the format's own double.
 */
internal object ProjectDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.asoc.typewright.project.ProjectDouble", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Double,
    ) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(jsonDouble(value)) else encoder.encodeDouble(value)
    }

    override fun deserialize(decoder: Decoder): Double {
        if (decoder !is JsonDecoder) return decoder.decodeDouble()
        val element = decoder.decodeJsonElement()
        if (element !is JsonPrimitive || element.isString ||
            element is JsonNull
        ) {
            throw SerializationException("Expected a number, got $element")
        }
        val value = parseJsonDouble(element.content)
        if (value == null || !value.isFinite()) throw SerializationException("Not a finite JSON number: ${element.content}")
        return value
    }
}

private val JSON_NUMBER = Regex("""-?(0|[1-9][0-9]*)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?""")

private const val MAX_PARSED_DIGITS = 800

// Anything past ±1,000,000 is already an infinity or a zero, whatever the digits.
private fun clampedExponent(text: String): Int {
    if (text.isEmpty()) return 0
    val negative = text.startsWith('-')
    val digits = text.trimStart('+', '-').trimStart('0')
    val magnitude = if (digits.length > 7) 1_000_000 else minOf(digits.ifEmpty { "0" }.toInt(), 1_000_000)
    return if (negative) -magnitude else magnitude
}

private const val SIGNIFICAND_BITS = 52
private const val HIDDEN_BIT = 1L shl SIGNIFICAND_BITS
private const val FRACTION_MASK = HIDDEN_BIT - 1
private const val EXPONENT_BIAS = 1023
private const val MIN_EXPONENT = -1074 // weight of the smallest subnormal's one bit
private const val MAX_BIASED_EXPONENT = 0x7FF

/**
 * The double nearest to [digits] × 10^[power] (ties to even). The quotient of the exact
 * rational is taken to 56 or 57 bits, and the remainder says whether anything is left below
 * them, which is all correct rounding needs.
 */
private fun nearestDouble(
    digits: BigNat,
    power: Int,
): Double {
    val numerator = if (power >= 0) digits * BigNat.pow10(power) else digits
    val denominator = if (power >= 0) BigNat.ONE else BigNat.pow10(-power)
    // Scale by 2^scale so the quotient has 56 or 57 bits: value = (quotient + rest) × 2^-scale.
    val scale = 56 + denominator.bitLength - numerator.bitLength
    val dividend = if (scale >= 0) numerator.shl(scale) else numerator
    val divisor = if (scale >= 0) denominator else denominator.shl(-scale)
    var remainder = dividend
    var quotient = 0L
    for (bit in dividend.bitLength - divisor.bitLength downTo 0) {
        val shifted = divisor.shl(bit)
        if (remainder >= shifted) {
            remainder -= shifted
            quotient = quotient or (1L shl bit)
        }
    }
    val inexactBelow = !remainder.isZero
    val quotientBits = 64 - quotient.countLeadingZeroBits()
    // Keep at most 53 bits, and no bit finer than the smallest subnormal's.
    val dropped = maxOf(quotientBits - 53, scale + MIN_EXPONENT)
    if (dropped > quotientBits) return 0.0
    var significand = quotient ushr dropped
    val droppedBits = quotient and ((1L shl dropped) - 1)
    val half = 1L shl (dropped - 1)
    if (droppedBits > half || (droppedBits == half && (inexactBelow || (significand and 1L) == 1L))) significand++
    var lowBitExponent = dropped - scale
    if (significand == 1L shl 53) {
        significand = significand ushr 1
        lowBitExponent++
    }
    if (significand < HIDDEN_BIT) {
        check(lowBitExponent == MIN_EXPONENT) { "A subnormal must sit at the smallest exponent" }
        return Double.fromBits(significand)
    }
    val biased = lowBitExponent + SIGNIFICAND_BITS + EXPONENT_BIAS
    if (biased >= MAX_BIASED_EXPONENT) return Double.POSITIVE_INFINITY
    return Double.fromBits((biased.toLong() shl SIGNIFICAND_BITS) or (significand - HIDDEN_BIT))
}

/**
 * The shortest decimal digits of a positive finite double, by definition rather than by a
 * digit-generation shortcut:
 *
 * 1. Expand the double exactly: it is m × 2^e, a finite decimal.
 * 2. Every decimal strictly inside the half-way points to its two neighbouring doubles reads
 *    back as it; a decimal exactly on one does too when m is even (reading rounds ties to
 *    even). The lower half-way point is closer when m is a power of two above the smallest
 *    normal, because the gap below it is half the gap above.
 * 3. For n digits, only the exact expansion cut to n digits (rounded down) and the next n-digit
 *    decimal up can be nearest; if neither lies in that interval, no n-digit decimal does, and
 *    if one does, one of n + 1 digits does too. So a binary search over n from 1 to 17 finds
 *    the shortest length, and the nearer of the two candidates that fit is the answer.
 */
private object ShortestDecimal {
    /** The digits (no leading or trailing zeros) and the exponent such that value = 0.digits × 10^exponent. */
    fun of(value: Double): Pair<String, Int> {
        val bits = value.toRawBits()
        val biased = ((bits ushr SIGNIFICAND_BITS) and MAX_BIASED_EXPONENT.toLong()).toInt()
        val fraction = bits and FRACTION_MASK
        val m = if (biased == 0) fraction else fraction or HIDDEN_BIT
        val e = if (biased == 0) MIN_EXPONENT else biased - EXPONENT_BIAS - SIGNIFICAND_BITS
        val expansion = if (e >= 0) BigNat.of(m).shl(e).toDecimalString() else (BigNat.of(m) * BigNat.pow5(-e)).toDecimalString()
        val point = if (e >= 0) expansion.length else expansion.length + e
        val exact = expansion.trimEnd('0')
        val interval = Interval(m, e, lowerGapIsHalved = fraction == 0L && biased > 1)

        fun candidates(n: Int): Candidates = Candidates(exact, point, n, interval)
        var low = 1
        var high = 17
        check(candidates(high).anyFits) { "17 digits always identify a double" }
        while (low < high) {
            val middle = (low + high) / 2
            if (candidates(middle).anyFits) high = middle else low = middle + 1
        }
        val chosen = candidates(low).nearestFitting()
        val text = chosen.toString()
        return text.trimEnd('0') to text.length + (point - low)
    }

    /**
     * The doubles' rounding interval around m × 2^e, in quarters of 2^e so every bound is an
     * integer: from 4m − 2 (or 4m − 1 when the gap below is halved) to 4m + 2, bounds
     * included when m is even.
     */
    private class Interval(
        m: Long,
        private val e: Int,
        lowerGapIsHalved: Boolean,
    ) {
        private val lower = BigNat.of(4 * m - if (lowerGapIsHalved) 1 else 2)
        private val upper = BigNat.of(4 * m + 2)
        private val boundsIncluded = m % 2 == 0L

        /** Whether c × 10^power reads back as the double. */
        fun contains(
            c: Long,
            power: Int,
        ): Boolean {
            // Compare c × 10^power with bound × 2^(e−2), both scaled to integers.
            val twos = maxOf(0, 2 - e)
            val candidate = BigNat.of(c) * BigNat.pow10(maxOf(power, 0)).shl(twos)
            val boundScale = BigNat.pow10(maxOf(-power, 0)).shl(e - 2 + twos)
            val aboveLower = candidate.compareTo(lower * boundScale)
            val belowUpper = candidate.compareTo(upper * boundScale)
            val fitsLower = aboveLower > 0 || (aboveLower == 0 && boundsIncluded)
            val fitsUpper = belowUpper < 0 || (belowUpper == 0 && boundsIncluded)
            return fitsLower && fitsUpper
        }
    }

    /** The n-digit decimals just below and just above the exact expansion, and which of them read back. */
    private class Candidates(
        exact: String,
        point: Int,
        private val n: Int,
        interval: Interval,
    ) {
        private val power = point - n
        private val down = exact.take(n).padEnd(n, '0').toLong()
        private val rest = exact.drop(n)
        private val isExact = rest.isEmpty()
        private val downFits = isExact || interval.contains(down, power)
        private val upFits = !isExact && interval.contains(down + 1, power)

        val anyFits: Boolean get() = downFits || upFits

        /** The fitting candidate nearer the exact value, as an integer to scale by 10^power; an even last digit wins a tie. */
        fun nearestFitting(): Long {
            check(anyFits) { "No $n-digit decimal fits" }
            if (!upFits) return down
            if (!downFits) return down + 1
            // rest holds the digits after the cut: compare 0.rest with one half.
            return when {
                rest[0] > '5' || (rest[0] == '5' && rest.length > 1) -> down + 1
                rest[0] < '5' -> down
                down % 2 == 0L -> down
                else -> down + 1
            }
        }
    }
}

/**
 * Arbitrary-precision natural numbers, only as much as exact decimal and binary conversion
 * needs: little-endian base-2^32 limbs with no zero limb on top, immutable.
 */
private class BigNat private constructor(
    private val limbs: IntArray,
) : Comparable<BigNat> {
    val isZero: Boolean get() = limbs.isEmpty()

    val bitLength: Int get() = if (limbs.isEmpty()) 0 else (limbs.size - 1) * 32 + (32 - limbs.last().countLeadingZeroBits())

    /** This × [factor], for 0 ≤ [factor] < 2^31. */
    fun timesSmall(factor: Int): BigNat {
        require(factor >= 0)
        if (factor == 0 || isZero) return ZERO
        val out = IntArray(limbs.size + 1)
        var carry = 0L
        for (i in limbs.indices) {
            val t = (limbs[i].toLong() and MASK) * factor + carry
            out[i] = t.toInt()
            carry = t ushr 32
        }
        out[limbs.size] = carry.toInt()
        return normalized(out)
    }

    /** This + [addend], for [addend] ≥ 0. */
    fun plusSmall(addend: Int): BigNat {
        require(addend >= 0)
        val out = limbs.copyOf(limbs.size + 1)
        var carry = addend.toLong()
        var i = 0
        while (carry != 0L) {
            val t = (out[i].toLong() and MASK) + carry
            out[i] = t.toInt()
            carry = t ushr 32
            i++
        }
        return normalized(out)
    }

    operator fun times(other: BigNat): BigNat {
        if (isZero || other.isZero) return ZERO
        val out = IntArray(limbs.size + other.limbs.size)
        for (i in limbs.indices) {
            val a = limbs[i].toLong() and MASK
            var carry = 0L
            for (j in other.limbs.indices) {
                // At most (2^32 − 1)² + 2(2^32 − 1) = 2^64 − 1: exact as an unsigned 64-bit value.
                val t = a * (other.limbs[j].toLong() and MASK) + (out[i + j].toLong() and MASK) + carry
                out[i + j] = t.toInt()
                carry = t ushr 32
            }
            out[i + other.limbs.size] = carry.toInt()
        }
        return normalized(out)
    }

    /** This × 2^[bits]. */
    fun shl(bits: Int): BigNat {
        require(bits >= 0)
        if (isZero || bits == 0) return this
        val words = bits / 32
        val shift = bits % 32
        val out = IntArray(limbs.size + words + 1)
        for (i in limbs.indices) {
            val shifted = (limbs[i].toLong() and MASK) shl shift
            out[i + words] = out[i + words] or shifted.toInt()
            out[i + words + 1] = (shifted ushr 32).toInt()
        }
        return normalized(out)
    }

    /** This − [other]; [other] must not be larger. */
    operator fun minus(other: BigNat): BigNat {
        require(this >= other) { "BigNat subtraction would go negative" }
        val out = IntArray(limbs.size)
        var borrow = 0L
        for (i in limbs.indices) {
            val subtrahend = if (i < other.limbs.size) other.limbs[i].toLong() and MASK else 0L
            val t = (limbs[i].toLong() and MASK) - subtrahend - borrow
            out[i] = t.toInt()
            borrow = if (t < 0) 1L else 0L
        }
        return normalized(out)
    }

    override fun compareTo(other: BigNat): Int {
        if (limbs.size != other.limbs.size) return limbs.size.compareTo(other.limbs.size)
        for (i in limbs.indices.reversed()) {
            val a = limbs[i].toLong() and MASK
            val b = other.limbs[i].toLong() and MASK
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is BigNat && limbs.contentEquals(other.limbs)

    override fun hashCode(): Int = limbs.contentHashCode()

    /** The decimal digits, without leading zeros (`"0"` for zero). */
    fun toDecimalString(): String {
        if (isZero) return "0"
        val chunks = ArrayList<Int>()
        var rest = limbs.copyOf()
        var size = rest.size
        while (size > 0) {
            var remainder = 0L
            for (i in size - 1 downTo 0) {
                val t = (remainder shl 32) or (rest[i].toLong() and MASK)
                rest[i] = (t / BILLION).toInt()
                remainder = t % BILLION
            }
            chunks += remainder.toInt()
            while (size > 0 && rest[size - 1] == 0) size--
        }
        val out = StringBuilder(chunks.last().toString())
        for (i in chunks.size - 2 downTo 0) out.append(chunks[i].toString().padStart(9, '0'))
        return out.toString()
    }

    companion object {
        private const val MASK = 0xFFFFFFFFL
        private const val BILLION = 1_000_000_000L
        private const val FIVE_TO_THE_13 = 1_220_703_125

        val ZERO = BigNat(IntArray(0))
        val ONE = of(1)

        fun of(value: Long): BigNat {
            require(value >= 0)
            return normalized(intArrayOf(value.toInt(), (value ushr 32).toInt()))
        }

        /** The number a string of decimal digits spells. */
        fun parseDecimal(digits: String): BigNat {
            var n = ZERO
            var at = 0
            var chunk = digits.length % 9
            if (chunk == 0) chunk = 9
            while (at < digits.length) {
                val part = digits.substring(at, at + chunk)
                n = n.timesSmall(POWERS_OF_TEN[chunk]).plusSmall(part.toInt())
                at += chunk
                chunk = 9
            }
            return n
        }

        fun pow5(exponent: Int): BigNat {
            require(exponent >= 0)
            var n = ONE
            var left = exponent
            while (left >= 13) {
                n = n.timesSmall(FIVE_TO_THE_13)
                left -= 13
            }
            var small = 1
            repeat(left) { small *= 5 }
            return n.timesSmall(small)
        }

        fun pow10(exponent: Int): BigNat = pow5(exponent).shl(exponent)

        private val POWERS_OF_TEN =
            IntArray(10).also { powers ->
                powers.indices.forEach {
                    powers[it] =
                        if (it == 0) 1 else powers[it - 1] * 10
                }
            }

        private fun normalized(limbs: IntArray): BigNat {
            var size = limbs.size
            while (size > 0 && limbs[size - 1] == 0) size--
            return BigNat(if (size == limbs.size) limbs else limbs.copyOf(size))
        }
    }
}
