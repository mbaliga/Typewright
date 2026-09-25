// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Guideline
import kotlin.math.floor

/**
 * A UFO 3 `fontinfo.plist`, as `core-font` models it. Every property is named exactly as its
 * `fontinfo.plist` key (unifiedfontobject.org/versions/ufo3/fontinfo.plist), so the file and the
 * code read the same:
 * - names and version: [familyName], [styleName], [versionMajor], [versionMinor],
 *   [openTypeNamePreferredFamilyName], [openTypeNamePreferredSubfamilyName],
 *   [postscriptFontName];
 * - vertical metrics: [unitsPerEm], [ascender], [descender], [xHeight], [capHeight], and the
 *   three sets a compiled font carries separately: hhea ([openTypeHheaAscender],
 *   [openTypeHheaDescender], [openTypeHheaLineGap]), OS/2 typo ([openTypeOS2TypoAscender],
 *   [openTypeOS2TypoDescender], [openTypeOS2TypoLineGap]) and OS/2 win ([openTypeOS2WinAscent],
 *   [openTypeOS2WinDescent]);
 * - people and rights: [copyright], [trademark], [openTypeNameDesigner],
 *   [openTypeNameDesignerURL], [openTypeNameManufacturer], [openTypeNameManufacturerURL],
 *   [openTypeNameLicense], [openTypeNameLicenseURL], [openTypeOS2VendorID];
 * - [guidelines], the font-wide guidelines (per-glyph ones are `Glyph.guidelines`).
 *
 * A `null` property is left out of the file, which the spec allows for every key. The integer
 * properties also take a whole-number `<real>` (`<real>1000.0</real>`, as interpolating tools
 * write `unitsPerEm`), which is written back as an `<integer>` of the same value.
 *
 * Every other key a `fontinfo.plist` holds is kept in [other], verbatim, so a project from another
 * tool keeps its PostScript hints, style-mapping fields and the rest when it is saved again. A
 * modelled key whose value its property cannot hold (a fractional `ascender`, say, or a `<string>`
 * `unitsPerEm`) also stays in [other], with its property `null`, rather than being dropped or
 * rounded. [init] refuses the opposite: an [other] entry whose value its key's property could hold
 * belongs in the property.
 *
 * **What is written.** [writeUfoProject] writes every set property and every [other] entry, all
 * keys in ascending order. A set property wins over an [other] entry under the same key, so
 * setting `ascender` on a font info read with a fractional one (`copy(ascender = 750)`) writes
 * 750; [other] still holds the file's value, and it is written again only if the property goes
 * back to `null`. When [other] repeats a key, its last entry is written, as `plistlib` reads a
 * repeated key. [other] may be in any order.
 *
 * **Equality** is equality of what is written: two font infos are equal when they give the same
 * `fontinfo.plist`, whatever the order of [other], its repeated keys, or the entries a set property
 * overrides. That keeps `readUfoProject(writeUfoProject(p)) == p` for every [UfoFontInfo], since
 * reading returns [other] sorted and without the overridden entries.
 */
data class UfoFontInfo(
    val familyName: String? = null,
    val styleName: String? = null,
    val unitsPerEm: Int? = null,
    val ascender: Int? = null,
    val descender: Int? = null,
    val xHeight: Int? = null,
    val capHeight: Int? = null,
    val versionMajor: Int? = null,
    val versionMinor: Int? = null,
    val guidelines: List<Guideline>? = null,
    val copyright: String? = null,
    val trademark: String? = null,
    val openTypeNameDesigner: String? = null,
    val openTypeNameDesignerURL: String? = null,
    val openTypeNameManufacturer: String? = null,
    val openTypeNameManufacturerURL: String? = null,
    val openTypeNameLicense: String? = null,
    val openTypeNameLicenseURL: String? = null,
    val openTypeNamePreferredFamilyName: String? = null,
    val openTypeNamePreferredSubfamilyName: String? = null,
    val postscriptFontName: String? = null,
    val openTypeOS2VendorID: String? = null,
    val openTypeHheaAscender: Int? = null,
    val openTypeHheaDescender: Int? = null,
    val openTypeHheaLineGap: Int? = null,
    val openTypeOS2TypoAscender: Int? = null,
    val openTypeOS2TypoDescender: Int? = null,
    val openTypeOS2TypoLineGap: Int? = null,
    val openTypeOS2WinAscent: Int? = null,
    val openTypeOS2WinDescent: Int? = null,
    val other: List<Pair<String, PlistValue>> = emptyList(),
) {
    init {
        for ((key, value) in other) {
            require(!holdsModelledKind(key, value)) {
                "UfoFontInfo.other holds '$key' with a value its own property can hold; set the property instead"
            }
        }
    }

    /** Whether [other] is a [UfoFontInfo] that writes the same `fontinfo.plist` as this one; see the class KDoc. */
    override fun equals(other: Any?): Boolean = this === other || (other is UfoFontInfo && fontInfoToPlist(this) == fontInfoToPlist(other))

    override fun hashCode(): Int = fontInfoToPlist(this).hashCode()
}

/**
 * A UFO 3 project's contents as `core-font` models them: [fontInfo], every glyph on the default
 * layer ([glyphs], whose order is the font's glyph order, written as `lib.plist`'s
 * `public.glyphOrder`), [kerningInfo] (`groups.plist`, `kerning.plist` and `features.fea`; see
 * [UfoKerning] for why those three travel together) and [lib] (the rest of `lib.plist`). A UFO's
 * other layers, `images/` and `data/` are not modelled; see `core-font/README.md`.
 */
data class UfoProject(
    val fontInfo: UfoFontInfo,
    val glyphs: List<Glyph>,
    val kerningInfo: UfoKerning = UfoKerning(),
    val lib: UfoLib = UfoLib(),
)

private const val UFO_CREATOR = "com.asoc.typewright.core.font"
private const val UFO_FORMAT_VERSION = 3L
private const val DEFAULT_LAYER_NAME = "public.default"
private const val DEFAULT_LAYER_DIRECTORY = "glyphs"
private const val GLIF_SUFFIX = ".glif"

/**
 * Writes [project] as a complete UFO 3 project, as a map from path (relative to the `.ufo`
 * directory) to file content. `core-font` never touches a filesystem itself (CLAUDE.md law 2); a
 * caller writes the map out.
 *
 * Always written: `metainfo.plist`, `fontinfo.plist`, `lib.plist` (it always carries
 * `public.glyphOrder`: the glyphs' names, then [UfoLib.glyphOrderExtras]), `layercontents.plist`,
 * `glyphs/contents.plist` and one `.glif` per glyph, each written with [UfoLib.lineContourFormat]
 * as its straight-contour default (see [writeGlif]).
 * Written only when there is something to write, as the spec makes them optional: `groups.plist`
 * (non-empty [UfoKerning.groups]), `kerning.plist` (non-empty [UfoKerning.kerning]) and
 * `features.fea` (non-`null` [UfoKerning.features]).
 *
 * **Glyph file names.** A glyph named in [fileNameHints] keeps that `.glif` file name, so a
 * project opened from disk and saved again never renames its files; pass [readGlyphFileNames] of
 * the files it was read from. A hint is used only when it is a plain `.glif` file name (no path
 * separator) that no earlier glyph's hint already claimed, compared case-insensitively as the spec
 * requires; otherwise, and for every glyph without a hint, the name is made by
 * [userNameToFileName] against every name already taken, the hinted ones included, so a new glyph
 * can never take the file an existing one keeps.
 *
 * Byte-stable: the same [project] and [fileNameHints] always give the same map.
 */
fun writeUfoProject(
    project: UfoProject,
    fileNameHints: Map<String, String> = emptyMap(),
): Map<String, String> {
    val files = LinkedHashMap<String, String>()

    files["metainfo.plist"] =
        writePlist(
            PlistValue.PDict(
                listOf(
                    "creator" to PlistValue.PString(UFO_CREATOR),
                    "formatVersion" to PlistValue.PInteger(UFO_FORMAT_VERSION),
                ),
            ),
        )

    files["fontinfo.plist"] = writePlist(fontInfoToPlist(project.fontInfo))

    files["lib.plist"] = writePlist(libToPlist(project.lib, project.glyphs.map { it.name }))

    files["layercontents.plist"] =
        writePlist(
            PlistValue.PArray(
                listOf(
                    PlistValue.PArray(listOf(PlistValue.PString(DEFAULT_LAYER_NAME), PlistValue.PString(DEFAULT_LAYER_DIRECTORY))),
                ),
            ),
        )

    val fileNames = assignGlyphFileNames(project.glyphs, fileNameHints)
    val contentsEntries = mutableListOf<Pair<String, PlistValue>>()
    for ((glyph, fileName) in project.glyphs.zip(fileNames)) {
        contentsEntries += glyph.name to PlistValue.PString(fileName)
        files["$DEFAULT_LAYER_DIRECTORY/$fileName"] = writeGlif(glyph, project.lib.lineContourFormat)
    }
    files["$DEFAULT_LAYER_DIRECTORY/contents.plist"] = writePlist(PlistValue.PDict(contentsEntries))

    if (project.kerningInfo.groups.isNotEmpty()) {
        files["groups.plist"] = writeGroupsPlist(project.kerningInfo.groups)
    }
    if (project.kerningInfo.kerning.isNotEmpty()) {
        files["kerning.plist"] = writeKerningPlist(project.kerningInfo.kerning)
    }
    project.kerningInfo.features?.let { files["features.fea"] = it }

    return files
}

/** Each of [glyphs]' `.glif` file names, in order; see [writeUfoProject] for how [hints] are honoured. */
private fun assignGlyphFileNames(
    glyphs: List<Glyph>,
    hints: Map<String, String>,
): List<String> {
    val used = mutableSetOf<String>()
    val hinted = arrayOfNulls<String>(glyphs.size)
    for ((i, glyph) in glyphs.withIndex()) {
        val hint = hints[glyph.name] ?: continue
        if (isPlainGlifFileName(hint) && used.add(hint.lowercase())) hinted[i] = hint
    }
    return glyphs.mapIndexed { i, glyph ->
        hinted[i] ?: userNameToFileName(glyph.name, used, suffix = GLIF_SUFFIX).also { used += it.lowercase() }
    }
}

/** Whether [name] is a file name that can sit directly in a layer directory as a `.glif`. */
private fun isPlainGlifFileName(name: String): Boolean =
    name.length > GLIF_SUFFIX.length &&
        name.endsWith(GLIF_SUFFIX) &&
        name.none { it == '/' || it == '\\' || it == '\u0000' }

/**
 * Reads a UFO 3 project from [files] (as [writeUfoProject] produces, or any spec-conforming UFO's
 * files read off disk by a caller). `metainfo.plist` must declare format version 3.
 * `fontinfo.plist` and `lib.plist` are read when present. Every glyph `contents.plist` lists is
 * read from the default layer's directory, which `layercontents.plist` names (`glyphs` when that
 * file is absent, as some tools still write), with `lib.plist`'s straight-contour default (see
 * [parseGlif]). The glyphs come back in `public.glyphOrder`'s order when `lib.plist` has one;
 * glyphs it does not list follow in `contents.plist` order, and names it lists that no glyph has
 * are kept in [UfoLib.glyphOrderExtras]. `groups.plist`, `kerning.plist` and `features.fea` are
 * read when present (absent gives, respectively, an empty map, an empty map and `null`).
 */
fun readUfoProject(files: Map<String, String>): UfoProject {
    val metaInfoXml =
        files["metainfo.plist"] ?: throw IllegalArgumentException("UFO project is missing required file metainfo.plist")
    val metaInfo = parsePlistDict(metaInfoXml)
    val formatVersion = metaInfo.longOrNull("formatVersion")
    require(formatVersion == UFO_FORMAT_VERSION) {
        "core-font reads UFO format version 3 only, found ${formatVersion ?: "none"}"
    }

    val fontInfo = files["fontinfo.plist"]?.let { fontInfoFromPlist(parsePlistDict(it)) } ?: UfoFontInfo()
    val readLib = files["lib.plist"]?.let { libFromPlist(parsePlistDict(it)) }
    val fileLib = readLib?.lib ?: UfoLib()

    val layerDirectory = readDefaultLayerDirectory(files["layercontents.plist"])
    val glyphsInContentsOrder =
        readContents(files, layerDirectory).map { (glyphName, fileName) ->
            val glifPath = "$layerDirectory/$fileName"
            val glifXml =
                files[glifPath]
                    ?: throw IllegalArgumentException("contents.plist references missing file $glifPath (glyph '$glyphName')")
            parseGlif(glifXml, fileLib.lineContourFormat)
        }
    val glyphs = orderGlyphs(glyphsInContentsOrder, readLib?.glyphOrder)
    val glyphNames = glyphs.map { it.name }.toSet()
    val lib =
        fileLib.copy(
            glyphOrderExtras =
                readLib
                    ?.glyphOrder
                    .orEmpty()
                    .distinct()
                    .filterNot { it in glyphNames },
        )

    val kerningInfo =
        UfoKerning(
            groups = files["groups.plist"]?.let { readGroupsPlist(it) } ?: emptyMap(),
            kerning = files["kerning.plist"]?.let { readKerningPlist(it) } ?: emptyMap(),
            features = files["features.fea"],
        )

    return UfoProject(fontInfo, glyphs, kerningInfo, lib)
}

/**
 * The default layer's `contents.plist` in [files], as glyph name to `.glif` file name, in file
 * order. Passing it to [writeUfoProject] as `fileNameHints` keeps every glyph's file name when
 * the project is saved again.
 */
fun readGlyphFileNames(files: Map<String, String>): Map<String, String> =
    readContents(files, readDefaultLayerDirectory(files["layercontents.plist"]))

private fun readContents(
    files: Map<String, String>,
    layerDirectory: String,
): Map<String, String> {
    val contentsXml =
        files["$layerDirectory/contents.plist"]
            ?: throw IllegalArgumentException("UFO project is missing required file $layerDirectory/contents.plist")
    val contents = LinkedHashMap<String, String>()
    for ((glyphName, fileNameValue) in parsePlistDict(contentsXml).entries) {
        val fileName =
            (fileNameValue as? PlistValue.PString)?.value
                ?: throw IllegalArgumentException("contents.plist entry '$glyphName' is not a <string> file name")
        contents[glyphName] = fileName
    }
    return contents
}

/**
 * [glyphs] in [glyphOrder]'s order (a name's first listing wins), with unlisted glyphs after them
 * in their own order; [glyphs] unchanged when there is no order.
 */
private fun orderGlyphs(
    glyphs: List<Glyph>,
    glyphOrder: List<String>?,
): List<Glyph> {
    if (glyphOrder == null) return glyphs
    val rank = HashMap<String, Int>()
    glyphOrder.forEachIndexed { i, name -> rank.getOrPut(name) { i } }
    return glyphs.sortedBy { rank[it.name] ?: Int.MAX_VALUE }
}

private fun readDefaultLayerDirectory(layerContentsXml: String?): String {
    if (layerContentsXml == null) return DEFAULT_LAYER_DIRECTORY
    val root = parsePlist(layerContentsXml)
    require(root is PlistValue.PArray) { "layercontents.plist's root must be an <array>" }
    for (entry in root.items) {
        require(entry is PlistValue.PArray && entry.items.size == 2) {
            "layercontents.plist entries must be a 2-item <array> of [layer name, directory name]"
        }
        val layerName = (entry.items[0] as? PlistValue.PString)?.value
        val directory = (entry.items[1] as? PlistValue.PString)?.value
        if (layerName == DEFAULT_LAYER_NAME && directory != null) return directory
    }
    throw IllegalArgumentException("layercontents.plist has no entry for the default layer ('$DEFAULT_LAYER_NAME')")
}

/** The modelled `<string>` keys of `fontinfo.plist`, each with its [UfoFontInfo] property. */
private val FONT_INFO_STRING_FIELDS: List<Pair<String, (UfoFontInfo) -> String?>> =
    listOf(
        "copyright" to { it.copyright },
        "familyName" to { it.familyName },
        "openTypeNameDesigner" to { it.openTypeNameDesigner },
        "openTypeNameDesignerURL" to { it.openTypeNameDesignerURL },
        "openTypeNameLicense" to { it.openTypeNameLicense },
        "openTypeNameLicenseURL" to { it.openTypeNameLicenseURL },
        "openTypeNameManufacturer" to { it.openTypeNameManufacturer },
        "openTypeNameManufacturerURL" to { it.openTypeNameManufacturerURL },
        "openTypeNamePreferredFamilyName" to { it.openTypeNamePreferredFamilyName },
        "openTypeNamePreferredSubfamilyName" to { it.openTypeNamePreferredSubfamilyName },
        "openTypeOS2VendorID" to { it.openTypeOS2VendorID },
        "postscriptFontName" to { it.postscriptFontName },
        "styleName" to { it.styleName },
        "trademark" to { it.trademark },
    )

/** The modelled `<integer>` keys of `fontinfo.plist`, each with its [UfoFontInfo] property. */
private val FONT_INFO_INT_FIELDS: List<Pair<String, (UfoFontInfo) -> Int?>> =
    listOf(
        "ascender" to { it.ascender },
        "capHeight" to { it.capHeight },
        "descender" to { it.descender },
        "openTypeHheaAscender" to { it.openTypeHheaAscender },
        "openTypeHheaDescender" to { it.openTypeHheaDescender },
        "openTypeHheaLineGap" to { it.openTypeHheaLineGap },
        "openTypeOS2TypoAscender" to { it.openTypeOS2TypoAscender },
        "openTypeOS2TypoDescender" to { it.openTypeOS2TypoDescender },
        "openTypeOS2TypoLineGap" to { it.openTypeOS2TypoLineGap },
        "openTypeOS2WinAscent" to { it.openTypeOS2WinAscent },
        "openTypeOS2WinDescent" to { it.openTypeOS2WinDescent },
        "unitsPerEm" to { it.unitsPerEm },
        "versionMajor" to { it.versionMajor },
        "versionMinor" to { it.versionMinor },
        "xHeight" to { it.xHeight },
    )

private const val GUIDELINES_KEY = "guidelines"
private val FONT_INFO_STRING_KEYS: Set<String> = FONT_INFO_STRING_FIELDS.map { it.first }.toSet()
private val FONT_INFO_INT_KEYS: Set<String> = FONT_INFO_INT_FIELDS.map { it.first }.toSet()

/**
 * Whether [value] is of the kind [key]'s [UfoFontInfo] property holds (`false` for a key with no
 * property): what decides whether a read puts a key in its property or in [UfoFontInfo.other].
 */
private fun holdsModelledKind(
    key: String,
    value: PlistValue,
): Boolean =
    when (key) {
        in FONT_INFO_STRING_KEYS -> value is PlistValue.PString
        in FONT_INFO_INT_KEYS -> value.asWholeIntOrNull() != null
        GUIDELINES_KEY -> value is PlistValue.PArray
        else -> false
    }

/**
 * This value as an [Int] when it is an `<integer>` in [Int]'s range, or a `<real>` holding a whole
 * number in that range (`1000.0`); else `null`.
 */
private fun PlistValue.asWholeIntOrNull(): Int? =
    when (this) {
        is PlistValue.PInteger -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else null
        is PlistValue.PReal -> if (value == floor(value) && value in INT_RANGE_AS_DOUBLE) value.toInt() else null
        else -> null
    }

private val INT_RANGE_AS_DOUBLE = Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()

/** [info]'s set modelled properties as `fontinfo.plist` entries, in no particular order. */
private fun modelledFontInfoEntries(info: UfoFontInfo): List<Pair<String, PlistValue>> {
    val entries = mutableListOf<Pair<String, PlistValue>>()
    for ((key, property) in FONT_INFO_STRING_FIELDS) property(info)?.let { entries += key to PlistValue.PString(it) }
    for ((key, property) in FONT_INFO_INT_FIELDS) property(info)?.let { entries += key to PlistValue.PInteger(it.toLong()) }
    info.guidelines?.let { guidelines -> entries += GUIDELINES_KEY to PlistValue.PArray(guidelines.map { guidelineToPlist(it) }) }
    return entries
}

/**
 * [info] as `fontinfo.plist`'s root dict, keys ascending: every set property, and every
 * [UfoFontInfo.other] entry (the last of a repeated key) whose key no set property writes.
 */
private fun fontInfoToPlist(info: UfoFontInfo): PlistValue.PDict {
    val written = HashMap<String, PlistValue>()
    for ((key, value) in info.other) written[key] = value
    for ((key, value) in modelledFontInfoEntries(info)) written[key] = value
    return PlistValue.PDict(written.entries.map { it.key to it.value }.sortedBy { it.first })
}

/** Reads `fontinfo.plist`'s root dict; a repeated key keeps its last value, as Python's `plistlib` does. */
private fun fontInfoFromPlist(dict: PlistValue.PDict): UfoFontInfo {
    val byKey: Map<String, PlistValue> = dict.entries.toMap()

    fun string(key: String): String? = (byKey[key] as? PlistValue.PString)?.value

    fun int(key: String): Int? = byKey[key]?.asWholeIntOrNull()

    val guidelines =
        (byKey[GUIDELINES_KEY] as? PlistValue.PArray)?.items?.map {
            require(it is PlistValue.PDict) { "fontinfo.plist guidelines entry must be a <dict>, found <${it.elementName()}>" }
            guidelineFromPlist(it)
        }
    return UfoFontInfo(
        familyName = string("familyName"),
        styleName = string("styleName"),
        unitsPerEm = int("unitsPerEm"),
        ascender = int("ascender"),
        descender = int("descender"),
        xHeight = int("xHeight"),
        capHeight = int("capHeight"),
        versionMajor = int("versionMajor"),
        versionMinor = int("versionMinor"),
        guidelines = guidelines,
        copyright = string("copyright"),
        trademark = string("trademark"),
        openTypeNameDesigner = string("openTypeNameDesigner"),
        openTypeNameDesignerURL = string("openTypeNameDesignerURL"),
        openTypeNameManufacturer = string("openTypeNameManufacturer"),
        openTypeNameManufacturerURL = string("openTypeNameManufacturerURL"),
        openTypeNameLicense = string("openTypeNameLicense"),
        openTypeNameLicenseURL = string("openTypeNameLicenseURL"),
        openTypeNamePreferredFamilyName = string("openTypeNamePreferredFamilyName"),
        openTypeNamePreferredSubfamilyName = string("openTypeNamePreferredSubfamilyName"),
        postscriptFontName = string("postscriptFontName"),
        openTypeOS2VendorID = string("openTypeOS2VendorID"),
        openTypeHheaAscender = int("openTypeHheaAscender"),
        openTypeHheaDescender = int("openTypeHheaDescender"),
        openTypeHheaLineGap = int("openTypeHheaLineGap"),
        openTypeOS2TypoAscender = int("openTypeOS2TypoAscender"),
        openTypeOS2TypoDescender = int("openTypeOS2TypoDescender"),
        openTypeOS2TypoLineGap = int("openTypeOS2TypoLineGap"),
        openTypeOS2WinAscent = int("openTypeOS2WinAscent"),
        openTypeOS2WinDescent = int("openTypeOS2WinDescent"),
        other =
            byKey.entries
                .filterNot { (key, value) -> holdsModelledKind(key, value) }
                .map { it.key to it.value }
                .sortedBy { it.first },
    )
}

/**
 * A [Guideline]'s six attributes (see its own KDoc for the vertical/horizontal/angled shapes the
 * spec allows) as a `fontinfo.plist` `guidelines` array entry: the same attribute names a
 * `.glif` file's `<guideline>` element uses (`x`, `y`, `angle`, `name`, `color`, `identifier`), but
 * as plist dict keys instead of XML attributes, since `fontinfo.plist`'s `guidelines` list is a
 * plist array of dicts, not XML elements of its own vocabulary. `x`/`y`/`angle` go through
 * [numericPlistValue] like a kerning value does, for the same "integer or float" spec reason.
 */
private fun guidelineToPlist(guideline: Guideline): PlistValue.PDict {
    val entries = mutableListOf<Pair<String, PlistValue>>()
    guideline.x?.let { entries += "x" to numericPlistValue(it) }
    guideline.y?.let { entries += "y" to numericPlistValue(it) }
    guideline.angle?.let { entries += "angle" to numericPlistValue(it) }
    guideline.name?.let { entries += "name" to PlistValue.PString(it) }
    guideline.color?.let { entries += "color" to PlistValue.PString(it) }
    guideline.identifier?.let { entries += "identifier" to PlistValue.PString(it) }
    return PlistValue.PDict(entries)
}

/** The inverse of [guidelineToPlist]; [Guideline]'s own `init` block rejects a structurally invalid entry (e.g. `angle` with no `x`/`y`). */
private fun guidelineFromPlist(dict: PlistValue.PDict): Guideline =
    Guideline(
        x = dict.doubleOrNull("x"),
        y = dict.doubleOrNull("y"),
        angle = dict.doubleOrNull("angle"),
        name = dict.stringOrNull("name"),
        color = dict.stringOrNull("color"),
        identifier = dict.stringOrNull("identifier"),
    )
