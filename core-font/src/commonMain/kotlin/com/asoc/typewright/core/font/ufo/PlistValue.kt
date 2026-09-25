// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.floor

/**
 * A parsed value from a property list (plist): the restricted XML vocabulary UFO 3 uses for every
 * `.plist` file (`metainfo.plist`, `fontinfo.plist`, `lib.plist`, `layercontents.plist`,
 * `glyphs/contents.plist`, ...). All eight value kinds a plist has are modelled, so a key another
 * tool wrote (a `<data>` blob or a `<date>` in `lib.plist`, say) reads and writes back unchanged
 * even though nothing in `core-font` interprets it.
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

    /**
     * A `<data>` value: bytes, held as their Base64 text in canonical form (the standard alphabet,
     * padded, no whitespace), which is what [writePlist] writes. [parsePlist] accepts the text
     * wrapped over several lines, as `plistlib` writes it, and canonicalises it; [init] requires
     * the canonical form, so equal bytes are always an equal value.
     */
    data class PData(
        val base64: String,
    ) : PlistValue() {
        init {
            require(canonicalBase64OrNull(base64) == base64) {
                "PData.base64 must be canonical Base64 (standard alphabet, padded, no whitespace), found \"$base64\""
            }
        }
    }

    /**
     * A `<date>` value, held as the ISO 8601 text the file gives it (`2026-09-25T12:00:00Z`), so it
     * is written back exactly as read. [init] requires the form `plistlib` reads: a date, optionally
     * a time to the hour, minute or second, then `Z`.
     */
    data class PDate(
        val text: String,
    ) : PlistValue() {
        init {
            require(PLIST_DATE.matches(text)) {
                "PDate.text must be an ISO 8601 plist date such as 2026-09-25T12:00:00Z, found \"$text\""
            }
        }
    }
}

/** The `<date>` forms `plistlib` reads: `YYYY-MM-DD`, optionally `THH`, `:MM` and `:SS`, then `Z`. */
private val PLIST_DATE = Regex("""\d{4}-\d{2}-\d{2}(T\d{2}(:\d{2}(:\d{2})?)?)?Z""")

/**
 * [text] as canonical Base64 (see [PlistValue.PData]) once whitespace is removed, or `null` when it
 * is not Base64 at all (a character outside the alphabet, or missing or misplaced padding).
 */
internal fun canonicalBase64OrNull(text: String): String? {
    val compact = text.filterNot { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
    val bytes =
        try {
            Base64.Default.decode(compact)
        } catch (e: IllegalArgumentException) {
            return null
        }
    return Base64.Default.encode(bytes)
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

/** This [PlistValue]'s XML tag name (`dict`, `string`, `true`/`false`, `data`, ...), for error messages. */
internal fun PlistValue.elementName(): String =
    when (this) {
        is PlistValue.PDict -> "dict"
        is PlistValue.PArray -> "array"
        is PlistValue.PString -> "string"
        is PlistValue.PInteger -> "integer"
        is PlistValue.PReal -> "real"
        is PlistValue.PBoolean -> "true/false"
        is PlistValue.PData -> "data"
        is PlistValue.PDate -> "date"
    }
