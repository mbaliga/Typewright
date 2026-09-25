// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10

/**
 * Formats a finite [value] exactly as Python's `repr(float)` does, which is how fontTools writes a
 * plist `<real>` and a `.glif` guideline number: the shortest decimal that reads back as [value]
 * (the one nearest [value] when several are that short), positional when its decimal exponent is
 * from -4 to 15 (`0.0001`, `-12.5`, `750.0`, `1000000000000000.0`) and in exponent form otherwise
 * (`1e-05`, `1.5e+20`).
 *
 * The digits are computed here, exactly, rather than taken from [Double.toString]: that lays the
 * same number out differently per platform (`1.0E-5` on the JVM, `0.00001` on Wasm), and on Wasm
 * its digits are not always the shortest (`1.5200000000000002E23` for `1.52e+23`). Encoding must
 * give the same bytes for the same project on every target, so every real `core-font` writes goes
 * through here.
 */
internal fun formatReal(value: Double): String {
    require(value.isFinite()) { "cannot write $value as a real number in a UFO file" }
    val (digits, pointPosition) = if (value == 0.0) "0" to 1 else shortestDigits(abs(value))
    val sign = if (value.toRawBits() < 0L) "-" else ""
    val body =
        if (pointPosition in -3..16) {
            when {
                pointPosition <= 0 -> "0." + "0".repeat(-pointPosition) + digits
                pointPosition >= digits.length -> digits + "0".repeat(pointPosition - digits.length) + ".0"
                else -> digits.substring(0, pointPosition) + "." + digits.substring(pointPosition)
            }
        } else {
            val mantissa = if (digits.length == 1) digits else digits.substring(0, 1) + "." + digits.substring(1)
            val exponent = pointPosition - 1
            mantissa + "e" + (if (exponent < 0) "-" else "+") + abs(exponent).toString().padStart(2, '0')
        }
    return sign + body
}

/**
 * Reads [text] as a real number, rounded correctly to the nearest [Double] (ties to even), the same
 * on every platform: a decimal with an optional sign, fraction and exponent (`-12.5`, `.5`,
 * `1e-05`, `1.5E+20`), surrounding whitespace allowed. Other text is read by [String.toDoubleOrNull]
 * as before (so `NaN` and `Infinity` still read), and `null` means it is not a number at all.
 *
 * [String.toDouble] is not used for decimals because on Wasm it is not always correctly rounded
 * (`"0.51307701"` reads one unit in the last place high), which would make the same file read as a
 * different project there than on the JVM.
 */
internal fun parseRealOrNull(text: String): Double? {
    val trimmed = text.trim()
    // Most numbers in a UFO are plain integers (every point coordinate); below 2^53 they are exact.
    trimmed.toLongOrNull()?.let { whole -> if (whole != 0L && abs(whole) < 1L shl 53) return whole.toDouble() }
    val match = DECIMAL.matchEntire(trimmed) ?: return trimmed.toDoubleOrNull()
    val (sign, integerPart, fractionPart, exponentPart) = match.destructured
    val negative = sign == "-"
    val allDigits = (integerPart + fractionPart).trimStart('0')
    if (allDigits.isEmpty()) return if (negative) -0.0 else 0.0
    val significand = allDigits.trimEnd('0')
    // Clamped: past +-100000 the result is 0 or infinite whatever the digits, and the sum below stays an Int.
    val writtenExponent =
        when {
            exponentPart.isEmpty() -> 0
            else -> exponentPart.toLongOrNull()?.coerceIn(-100_000L, 100_000L)?.toInt() ?: if (exponentPart[0] == '-') -100_000 else 100_000
        }
    val exponent = writtenExponent - fractionPart.length + (allDigits.length - significand.length)
    val magnitude = decimalToDouble(significand, exponent)
    return if (negative) -magnitude else magnitude
}

/** [parseRealOrNull], throwing for text that is not a number. */
internal fun parseReal(text: String): Double = parseRealOrNull(text) ?: throw NumberFormatException("'$text' is not a number")

/** A decimal: sign, integer digits, fraction digits, exponent. The lookahead requires a digit before or after the point. */
private val DECIMAL = Regex("""(?=[+-]?\.?\d)([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?""")

/** 10^0 to 10^22: the powers of ten a [Double] holds exactly, each an exact product of the one before. */
private val EXACT_POWERS_OF_TEN: DoubleArray = generateSequence(1.0) { it * 10.0 }.take(23).toList().toDoubleArray()

/**
 * The [Double] nearest `significand x 10^exponent` ([significand] a string of decimal digits with no
 * leading or trailing zeros), ties to even.
 */
private fun decimalToDouble(
    significand: String,
    exponent: Int,
): Double {
    // Both factors exact and one IEEE operation between them, so the result is correctly rounded.
    if (significand.length <= 15 && exponent in -22..22) {
        val whole = significand.toLong().toDouble()
        return if (exponent >= 0) whole * EXACT_POWERS_OF_TEN[exponent] else whole / EXACT_POWERS_OF_TEN[-exponent]
    }
    val decimalPoint = significand.length + exponent // value < 10^decimalPoint
    if (decimalPoint > 310) return Double.POSITIVE_INFINITY
    if (decimalPoint < -330) return 0.0

    // Start from the platform's reading of the first 17 digits, within a few units in the last
    // place, and step to the neighbour the value is nearer until it lies in the candidate's
    // rounding interval. A search over every double settles a guess that is further off.
    val target = BigNat.ofDecimal(significand)
    val guessDigits = significand.take(17)
    val guess = "${guessDigits}e${exponent + significand.length - guessDigits.length}".toDoubleOrNull()
    var bits = if (guess == null || guess.isNaN()) 0L else guess.toRawBits()
    repeat(16) {
        val side = sideOfRoundingInterval(target, exponent, bits)
        if (side == 0) return Double.fromBits(bits)
        bits += side
    }
    var low = 0L
    var high = INFINITY_BITS
    while (true) {
        val middle = (low + high) ushr 1
        val side = sideOfRoundingInterval(target, exponent, middle)
        when {
            side == 0 -> return Double.fromBits(middle)
            side > 0 -> low = middle + 1
            else -> high = middle - 1
        }
    }
}

/**
 * Where `digits x 10^exponent` lies relative to the numbers that round to the non-negative double
 * (or infinity) with raw [bits]: 0 inside, 1 above, -1 below. The interval's ends are its midpoints
 * with the neighbouring doubles, each included when [bits] is even, as IEEE rounds ties to even.
 */
private fun sideOfRoundingInterval(
    digits: BigNat,
    exponent: Int,
    bits: Long,
): Int {
    val odd = bits and 1L == 1L
    if (bits < INFINITY_BITS) {
        val above = compareDecimalWithMidpoint(digits, exponent, bits, bits + 1)
        if (above > 0 || (above == 0 && odd)) return 1
    }
    if (bits > 0L) {
        val below = compareDecimalWithMidpoint(digits, exponent, bits - 1, bits)
        if (below < 0 || (below == 0 && odd)) return -1
    }
    return 0
}

private const val INFINITY_BITS = 0x7FF0000000000000L

/**
 * Compares `digits x 10^exponent` with the exact midpoint of the two adjacent non-negative doubles
 * whose raw bits are [lowerBits] and [upperBits] (the pattern after `Double.MAX_VALUE` standing for
 * 2^1024, where rounding up would overflow).
 */
private fun compareDecimalWithMidpoint(
    digits: BigNat,
    exponent: Int,
    lowerBits: Long,
    upperBits: Long,
): Int {
    val (lowerSignificand, lowerExponent) = decompose(lowerBits)
    val (upperSignificand, upperExponent) = decompose(upperBits)
    val shared = minOf(lowerExponent, upperExponent)
    val midpoint =
        BigNat.of(lowerSignificand).shl(lowerExponent - shared) + BigNat.of(upperSignificand).shl(upperExponent - shared)
    val midpointExponent = shared - 1 // midpoint value = midpoint x 2^midpointExponent
    var left = digits
    var right = midpoint
    if (exponent >= 0) left = left.timesPowerOfTen(exponent) else right = right.timesPowerOfTen(-exponent)
    if (midpointExponent >= 0) right = right.shl(midpointExponent) else left = left.shl(-midpointExponent)
    return left.compareTo(right)
}

/** A non-negative double's raw [bits] as `significand x 2^exponent`, the significand an integer. */
private fun decompose(bits: Long): Pair<Long, Int> {
    val biased = ((bits ushr 52) and 0x7FFL).toInt()
    val fraction = bits and 0xFFFFFFFFFFFFFL
    return if (biased == 0) fraction to -1074 else (fraction or (1L shl 52)) to biased - 1075
}

/**
 * The shortest digits (no leading or trailing zeros) that read back as [magnitude] (finite, above
 * zero), the one nearest [magnitude] when several are that short, and where the decimal point
 * goes: `magnitude ~ 0.<digits> x 10^position`. This is Steele and White's free-format algorithm
 * as Burger and Dybvig state it ("Printing Floating-Point Numbers Quickly and Accurately", 1996),
 * on exact integers, with IEEE round-half-even deciding whether the interval's ends are included.
 */
private fun shortestDigits(magnitude: Double): Pair<String, Int> {
    val bits = magnitude.toRawBits()
    val (significand, exponent) = decompose(bits)
    val biased = ((bits ushr 52) and 0x7FFL).toInt()
    val inclusive = significand and 1L == 0L
    // The value is r/s; the midpoints to the next double above and below are (r + mPlus)/s and (r - mMinus)/s.
    val lowerGapIsHalf = significand == 1L shl 52 && biased > 1
    var r: BigNat
    var s: BigNat
    var mPlus: BigNat
    var mMinus: BigNat
    if (exponent >= 0) {
        if (lowerGapIsHalf) {
            r = BigNat.of(significand).shl(exponent + 2)
            s = BigNat.of(4)
            mPlus = BigNat.of(1).shl(exponent + 1)
            mMinus = BigNat.of(1).shl(exponent)
        } else {
            r = BigNat.of(significand).shl(exponent + 1)
            s = BigNat.of(2)
            mPlus = BigNat.of(1).shl(exponent)
            mMinus = mPlus
        }
    } else {
        if (lowerGapIsHalf) {
            r = BigNat.of(significand).shl(2)
            s = BigNat.of(1).shl(2 - exponent)
            mPlus = BigNat.of(2)
            mMinus = BigNat.of(1)
        } else {
            r = BigNat.of(significand).shl(1)
            s = BigNat.of(1).shl(1 - exponent)
            mPlus = BigNat.of(1)
            mMinus = mPlus
        }
    }

    fun reachesTop(
        low: BigNat,
        high: BigNat,
    ): Boolean = if (inclusive) low >= high else low > high

    var position = ceil(log10(magnitude) - 1e-10).toInt()
    if (position >= 0) {
        s = s.timesPowerOfTen(position)
    } else {
        r = r.timesPowerOfTen(-position)
        mPlus = mPlus.timesPowerOfTen(-position)
        mMinus = mMinus.timesPowerOfTen(-position)
    }
    while (reachesTop(r + mPlus, s)) {
        s = s.times(10)
        position++
    }
    while (!reachesTop((r + mPlus).times(10), s)) {
        r = r.times(10)
        mPlus = mPlus.times(10)
        mMinus = mMinus.times(10)
        position--
    }

    val digits = StringBuilder()
    while (true) {
        r = r.times(10)
        mPlus = mPlus.times(10)
        mMinus = mMinus.times(10)
        var digit = 0
        while (r >= s) {
            r -= s
            digit++
        }
        val low = if (inclusive) r <= mMinus else r < mMinus
        val high = reachesTop(r + mPlus, s)
        if (!low && !high) {
            digits.append('0' + digit)
            continue
        }
        val roundUp =
            when {
                !high -> false
                !low -> true
                else -> r.shl(1).compareTo(s).let { if (it != 0) it > 0 else digit % 2 == 1 }
            }
        // Burger and Dybvig show a digit that rounds up here is never 9.
        digits.append('0' + (if (roundUp) digit + 1 else digit))
        return digits.toString() to position
    }
}

/**
 * A non-negative integer of any size, with just the arithmetic exact decimal and binary
 * conversion needs. Immutable; 32-bit limbs, least significant first, no leading zero limb.
 */
private class BigNat private constructor(
    private val limbs: IntArray,
) : Comparable<BigNat> {
    operator fun plus(other: BigNat): BigNat {
        val size = maxOf(limbs.size, other.limbs.size)
        val out = IntArray(size + 1)
        var carry = 0L
        for (i in 0 until size) {
            val sum = limb(i) + other.limb(i) + carry
            out[i] = sum.toInt()
            carry = sum ushr 32
        }
        out[size] = carry.toInt()
        return normalized(out)
    }

    /** `this - other`; [other] must not be larger. */
    operator fun minus(other: BigNat): BigNat {
        val out = IntArray(limbs.size)
        var borrow = 0L
        for (i in limbs.indices) {
            var difference = limb(i) - other.limb(i) - borrow
            borrow = if (difference < 0) 1L else 0L
            if (difference < 0) difference += 1L shl 32
            out[i] = difference.toInt()
        }
        check(borrow == 0L) { "BigNat subtraction would go below zero" }
        return normalized(out)
    }

    /** `this x factor`, [factor] in `0..Int.MAX_VALUE`. */
    fun times(factor: Int): BigNat {
        val out = IntArray(limbs.size + 1)
        var carry = 0L
        for (i in limbs.indices) {
            val product = limb(i) * factor + carry
            out[i] = product.toInt()
            carry = product ushr 32
        }
        out[limbs.size] = carry.toInt()
        return normalized(out)
    }

    fun timesPowerOfTen(power: Int): BigNat {
        var result = this
        var left = power
        while (left >= 9) {
            result = result.times(1_000_000_000)
            left -= 9
        }
        return if (left == 0) result else result.times(POWERS_OF_TEN_BELOW_A_BILLION[left])
    }

    /** `this x 2^count`. */
    fun shl(count: Int): BigNat {
        if (limbs.isEmpty() || count == 0) return this
        val limbShift = count / 32
        val bitShift = count % 32
        val out = IntArray(limbs.size + limbShift + 1)
        for (i in limbs.indices) {
            val shifted = limb(i) shl bitShift
            out[i + limbShift] = out[i + limbShift] or shifted.toInt()
            out[i + limbShift + 1] = out[i + limbShift + 1] or (shifted ushr 32).toInt()
        }
        return normalized(out)
    }

    override fun compareTo(other: BigNat): Int {
        if (limbs.size != other.limbs.size) return limbs.size.compareTo(other.limbs.size)
        for (i in limbs.indices.reversed()) {
            val a = limb(i)
            val b = other.limb(i)
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun limb(i: Int): Long = if (i < limbs.size) limbs[i].toLong() and 0xFFFFFFFFL else 0L

    companion object {
        private val POWERS_OF_TEN_BELOW_A_BILLION = intArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000)

        /** [value], which must not be negative. */
        fun of(value: Long): BigNat {
            require(value >= 0L) { "BigNat holds no negative value, found $value" }
            return normalized(intArrayOf(value.toInt(), (value ushr 32).toInt()))
        }

        /** The integer a string of decimal [digits] names. */
        fun ofDecimal(digits: String): BigNat {
            var result = of(0L)
            var start = 0
            val firstChunk = digits.length % 9
            if (firstChunk > 0) {
                result = of(digits.substring(0, firstChunk).toLong())
                start = firstChunk
            }
            while (start < digits.length) {
                result = result.times(1_000_000_000) + of(digits.substring(start, start + 9).toLong())
                start += 9
            }
            return result
        }

        private fun normalized(limbs: IntArray): BigNat {
            var size = limbs.size
            while (size > 0 && limbs[size - 1] == 0) size--
            return BigNat(if (size == limbs.size) limbs else limbs.copyOf(size))
        }
    }
}
