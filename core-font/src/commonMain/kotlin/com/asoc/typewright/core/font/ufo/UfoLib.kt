// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import com.asoc.typewright.core.geometry.CurveFormat

/**
 * The lib key, in `lib.plist` and in a `.glif` file's `<lib>`, that says which [CurveFormat] a
 * contour made only of `type="line"` points belongs to. Such a contour reads identically as
 * either format, so the file alone cannot say; but the two formats count its points differently
 * (a cubic straight segment carries two degenerate off-curve controls, a quadratic one none), and
 * reading a quadratic project's straight glyphs back as cubic would change their counts across a
 * reopen. Its value is `"quadratic"` or `"cubic"`.
 *
 * In `lib.plist` it is the project's default for such contours and is written only when that
 * default is [CurveFormat.QUADRATIC] (a project that came from TrueType). In a `.glif` it is
 * written only for a glyph whose contours are all straight and whose format differs from the
 * project default, which happens only in a project holding both formats. See [parseGlif] for the
 * full reading rule.
 */
const val LINE_CONTOUR_FORMAT_LIB_KEY = "com.asoc.typewright.lineContourFormat"

/** The UFO 3 font lib key holding the font's glyph order (unifiedfontobject.org/versions/ufo3/lib.plist). */
internal const val GLYPH_ORDER_LIB_KEY = "public.glyphOrder"

/**
 * A UFO 3 project's `lib.plist`, as `core-font` models it: [lineContourFormat] (the
 * [LINE_CONTOUR_FORMAT_LIB_KEY] default, see there for why it exists), [glyphOrderExtras], and
 * every other key the file holds, carried verbatim in [other] so a project opened from another tool
 * keeps its lib data when it is saved again. Values inside [other] are kept exactly as given, nested
 * order included, `<data>` and `<date>` values too.
 *
 * `public.glyphOrder` is the order of [UfoProject.glyphs] itself, written by [writeUfoProject] on
 * every save and used by [readUfoProject] to order the glyphs it reads. The one part of it the
 * glyphs cannot hold is a name they do not include (a glyph set a designer plans to draw, say):
 * [readUfoProject] keeps those names in [glyphOrderExtras], in the order the file lists them, and
 * [writeUfoProject] writes them after the project's glyphs, skipping any that has since become a
 * glyph. A file listing such a name among its glyphs therefore gets it moved after them on the
 * first save, but never loses it. A project that adds a glyph under one of these names should drop
 * the name here too: the glyph's own place is what is written, so the name no longer comes back as
 * an extra when the project is read again, and a project that kept it would not read back equal.
 *
 * **What is written.** Every [other] key in ascending order together with the modelled ones; a
 * modelled key (`public.glyphOrder`, [LINE_CONTOUR_FORMAT_LIB_KEY]) in [other] is not written,
 * since the property it duplicates wins; when [other] repeats a key its last entry is written, as
 * `plistlib` reads a repeated key; and a name repeated in [glyphOrderExtras] is written once.
 * [other] may therefore be in any order.
 *
 * **Equality** is equality of what is written: two libs are equal when they give the same
 * `lib.plist` for a project with no glyphs, whatever the order of [other] or its repeated or
 * overridden keys. That keeps `readUfoProject(writeUfoProject(p)) == p`, since reading returns
 * [other] sorted and with only the keys that are written.
 */
data class UfoLib(
    val lineContourFormat: CurveFormat = CurveFormat.CUBIC,
    val other: PlistValue.PDict = PlistValue.PDict(emptyList()),
    val glyphOrderExtras: List<String> = emptyList(),
) {
    /** Whether [other] is a [UfoLib] that writes the same `lib.plist` as this one; see the class KDoc. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is UfoLib && libToPlist(this, emptyList()) == libToPlist(other, emptyList()))

    override fun hashCode(): Int = libToPlist(this, emptyList()).hashCode()
}

/**
 * [lib] as `lib.plist`'s root dict for a project whose glyphs are named [glyphNames], in order,
 * every key ascending (as fontTools writes a plist): `public.glyphOrder` ([glyphNames], then
 * [UfoLib.glyphOrderExtras] that are not among them), [LINE_CONTOUR_FORMAT_LIB_KEY] when it is not
 * the default, and [UfoLib.other]'s other keys, the last of a repeated one.
 */
internal fun libToPlist(
    lib: UfoLib,
    glyphNames: List<String>,
): PlistValue.PDict {
    val written = HashMap<String, PlistValue>()
    for ((key, value) in lib.other.entries) written[key] = value
    written.remove(LINE_CONTOUR_FORMAT_LIB_KEY)
    if (lib.lineContourFormat == CurveFormat.QUADRATIC) {
        written[LINE_CONTOUR_FORMAT_LIB_KEY] = PlistValue.PString(lib.lineContourFormat.libValue())
    }
    val glyphNameSet = glyphNames.toSet()
    val order = glyphNames + lib.glyphOrderExtras.distinct().filterNot { it in glyphNameSet }
    written[GLYPH_ORDER_LIB_KEY] = PlistValue.PArray(order.map { PlistValue.PString(it) })
    return PlistValue.PDict(written.entries.map { it.key to it.value }.sortedBy { it.first })
}

/** What [libFromPlist] reads out of a `lib.plist`: the [UfoLib] and, when the file has one, its glyph order. */
internal class ReadLib(
    val lib: UfoLib,
    val glyphOrder: List<String>?,
)

/**
 * Reads `lib.plist`'s root [dict]. `public.glyphOrder` must be an array of strings and
 * [LINE_CONTOUR_FORMAT_LIB_KEY] one of its two values; either malformed is an error rather than
 * something silently dropped, as ufoLib's validating reader also treats a malformed glyph order.
 * Every other key goes to [UfoLib.other], sorted by key. A repeated key keeps its last value, as
 * Python's `plistlib` does. [UfoLib.glyphOrderExtras] is left empty: which listed names are extras
 * depends on the glyphs, so [readUfoProject] fills it in.
 */
internal fun libFromPlist(dict: PlistValue.PDict): ReadLib {
    var lineContourFormat = CurveFormat.CUBIC
    var glyphOrder: List<String>? = null
    val other = mutableListOf<Pair<String, PlistValue>>()
    for ((key, value) in dict.entries.toMap()) {
        when (key) {
            LINE_CONTOUR_FORMAT_LIB_KEY -> {
                val text =
                    (value as? PlistValue.PString)?.value
                        ?: throw IllegalArgumentException(
                            "lib.plist '$LINE_CONTOUR_FORMAT_LIB_KEY' must be a <string>, found <${value.elementName()}>",
                        )
                lineContourFormat = parseLineContourFormat(text, "lib.plist")
            }

            GLYPH_ORDER_LIB_KEY -> {
                require(value is PlistValue.PArray) {
                    "lib.plist '$GLYPH_ORDER_LIB_KEY' must be an <array> of glyph names, found <${value.elementName()}>"
                }
                glyphOrder =
                    value.items.map {
                        (it as? PlistValue.PString)?.value
                            ?: throw IllegalArgumentException(
                                "lib.plist '$GLYPH_ORDER_LIB_KEY' must hold only <string> glyph names, found <${it.elementName()}>",
                            )
                    }
            }

            else -> {
                other += key to value
            }
        }
    }
    return ReadLib(UfoLib(lineContourFormat, PlistValue.PDict(other.sortedBy { it.first })), glyphOrder)
}

/** [this] format as the [LINE_CONTOUR_FORMAT_LIB_KEY] value that names it. */
internal fun CurveFormat.libValue(): String =
    when (this) {
        CurveFormat.QUADRATIC -> "quadratic"
        CurveFormat.CUBIC -> "cubic"
    }

/** The [CurveFormat] a [LINE_CONTOUR_FORMAT_LIB_KEY] value names; [where] says which file, for the error message. */
internal fun parseLineContourFormat(
    value: String,
    where: String,
): CurveFormat =
    when (value) {
        "quadratic" -> CurveFormat.QUADRATIC

        "cubic" -> CurveFormat.CUBIC

        else -> throw IllegalArgumentException(
            "$where '$LINE_CONTOUR_FORMAT_LIB_KEY' must be \"quadratic\" or \"cubic\", found \"$value\"",
        )
    }
