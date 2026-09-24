package dev.aarso.typewright.core.font.ufo

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
fun PlistValue.PDict.doubleOrNull(key: String): Double? =
    when (val v = get(key)) {
        is PlistValue.PReal -> v.value
        is PlistValue.PInteger -> v.value.toDouble()
        else -> null
    }

/** This dict's value for [key] as a [PlistValue.PArray], or `null` if absent or a different kind. */
fun PlistValue.PDict.arrayOrNull(key: String): PlistValue.PArray? = get(key) as? PlistValue.PArray
