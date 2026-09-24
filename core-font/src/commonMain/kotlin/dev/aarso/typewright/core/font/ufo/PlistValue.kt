package dev.aarso.typewright.core.font.ufo

import kotlin.math.abs
import kotlin.math.floor

/**
 * A parsed value from a property list (plist): the restricted XML vocabulary UFO 3 uses for every
 * `.plist` file (`metainfo.plist`, `fontinfo.plist`, `layercontents.plist`,
 * `glyphs/contents.plist`, ...). Only the six value kinds a plist actually has are modelled; `data`
 * and `date` are not part of anything `core-font` reads or writes, so [parsePlist] rejects them
 * with a clear message rather than silently dropping them.
 */
sealed class PlistValue {
    data class PDict(
        val entries: List<Pair<String, PlistValue>>,
    ) : PlistValue() {
        /** The value for [key], or `null` if this dict has no such key. Plist dict keys are unique by convention. */
        operator fun get(key: String): PlistValue? = entries.firstOrNull { it.first == key }?.second
    }

    data class PArray(
        val items: List<PlistValue>,
    ) : PlistValue()

    data class PString(
        val value: String,
    ) : PlistValue()

    data class PInteger(
        val value: Long,
    ) : PlistValue()

    data class PReal(
        val value: Double,
    ) : PlistValue()

    data class PBoolean(
        val value: Boolean,
    ) : PlistValue()
}

/** This dict's value for [key] as a [PlistValue.PString], or `null` if absent or a different kind. */
fun PlistValue.PDict.stringOrNull(key: String): String? = (get(key) as? PlistValue.PString)?.value

/** This dict's value for [key] as a [PlistValue.PInteger], or `null` if absent or a different kind. */
fun PlistValue.PDict.longOrNull(key: String): Long? = (get(key) as? PlistValue.PInteger)?.value

/** This dict's value for [key] as a [PlistValue.PInteger] widened to [Int], or `null` if absent or a different kind. */
fun PlistValue.PDict.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

/**
 * This dict's value for [key] as a [Double]: plists routinely encode a whole-number real as
 * `<integer>`, so both `<integer>` and `<real>` are accepted. `null` if absent or a different kind.
 */
fun PlistValue.PDict.doubleOrNull(key: String): Double? = get(key)?.asDoubleOrNull()

/** This dict's value for [key] as a [PlistValue.PArray], or `null` if absent or a different kind. */
fun PlistValue.PDict.arrayOrNull(key: String): PlistValue.PArray? = get(key) as? PlistValue.PArray

/**
 * This value as a [Double] when it is a [PlistValue.PInteger] or [PlistValue.PReal] (plists
 * routinely encode a whole-number real as `<integer>`, so both are accepted here too), else `null`.
 * Shared by [PlistValue.PDict.doubleOrNull] and anything reading a numeric value that is not
 * sitting under a known dict key (`kerning.plist`'s per-pair values, read by
 * [readKerningPlist]).
 */
internal fun PlistValue.asDoubleOrNull(): Double? =
    when (this) {
        is PlistValue.PReal -> value
        is PlistValue.PInteger -> value.toDouble()
        else -> null
    }

/**
 * The most natural [PlistValue] for a numeric [value] the UFO 3 spec types as "integer or float"
 * (a kerning value, or a guideline's `x`/`y`/`angle`): `<integer>` when [value] is a whole number
 * within [Long] range, matching how real UFO tools write a whole-number field (the spec's own
 * `kerning.plist` example uses `<integer>7</integer>`, not `<real>7.0</real>`); `<real>` otherwise.
 */
internal fun numericPlistValue(value: Double): PlistValue =
    if (value.isFinite() && value == floor(value) && abs(value) < 1e15) {
        PlistValue.PInteger(value.toLong())
    } else {
        PlistValue.PReal(value)
    }

/** This [PlistValue]'s XML tag name (`dict`, `array`, `string`, `integer`, `real`, or `true`/`false`), for error messages. */
internal fun PlistValue.elementName(): String =
    when (this) {
        is PlistValue.PDict -> "dict"
        is PlistValue.PArray -> "array"
        is PlistValue.PString -> "string"
        is PlistValue.PInteger -> "integer"
        is PlistValue.PReal -> "real"
        is PlistValue.PBoolean -> "true/false"
    }
