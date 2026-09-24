package dev.aarso.typewright.core.font.ufo

import dev.aarso.typewright.core.geometry.Glyph

/**
 * The `fontinfo.plist` fields `core-font` models: font-wide metadata every screen in the app
 * needs (family/style name for display, `unitsPerEm`/`ascender`/`descender`/`xHeight`/`capHeight`
 * to size the sheet and to give `qa`'s alignment-miss check its metric lines). The UFO 3 spec's
 * `fontinfo.plist` has many more optional keys (kerning groups, PostScript hints, OpenType
 * name-table overrides, ...); [writeFontInfo]/[readFontInfo] round-trip exactly these nine and
 * silently keep only these on a read of a richer file, since nothing downstream of `core-font`
 * yet needs the rest. All keys are optional in both this type and the file: a `null` field is
 * simply left out of `fontinfo.plist`, which the spec allows.
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
)

/**
 * A UFO 3 project's contents as `core-font` models them: [fontInfo] plus every glyph on the
 * default layer ([glyphs], in [Glyph.name]'s natural — that is, caller-chosen — order, which
 * becomes `glyphs/contents.plist`'s order). UFO's non-default layers, kerning, groups and features
 * are out of this task's scope (see `core-font/README.md` for what comes next).
 */
data class UfoProject(
    val fontInfo: UfoFontInfo,
    val glyphs: List<Glyph>,
)

private const val UFO_CREATOR = "dev.aarso.typewright.core.font"
private const val UFO_FORMAT_VERSION = 3L
private const val DEFAULT_LAYER_NAME = "public.default"
private const val DEFAULT_LAYER_DIRECTORY = "glyphs"

/**
 * Writes [project] as a complete UFO 3 project: `metainfo.plist`, `fontinfo.plist`,
 * `layercontents.plist`, `glyphs/contents.plist` and one `.glif` file per glyph — every file
 * the UFO 3 spec requires, as a path-to-content map. `core-font` never touches a filesystem
 * itself (CLAUDE.md law 2); a caller (a platform module, or a JVM test using `java.io`) writes
 * this map's values to files at its keys' paths, relative to the project's `.ufo` directory.
 * Byte-stable: the same [UfoProject] always produces the same map (see [writePlist] and
 * [writeGlif]'s own byte-stability notes) — including glyph file names, since
 * [userNameToFileName] is run against a running "already used" set in [UfoProject.glyphs]' own
 * order, not against Kotlin's (unspecified beyond insertion order) map iteration.
 */
fun writeUfoProject(project: UfoProject): Map<String, String> {
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

    files["layercontents.plist"] =
        writePlist(
            PlistValue.PArray(
                listOf(
                    PlistValue.PArray(listOf(PlistValue.PString(DEFAULT_LAYER_NAME), PlistValue.PString(DEFAULT_LAYER_DIRECTORY))),
                ),
            ),
        )

    val usedFileNames = mutableSetOf<String>()
    val contentsEntries = mutableListOf<Pair<String, PlistValue>>()
    for (glyph in project.glyphs) {
        val fileName = userNameToFileName(glyph.name, usedFileNames, suffix = ".glif")
        usedFileNames += fileName.lowercase()
        contentsEntries += glyph.name to PlistValue.PString(fileName)
        files["$DEFAULT_LAYER_DIRECTORY/$fileName"] = writeGlif(glyph)
    }
    files["$DEFAULT_LAYER_DIRECTORY/contents.plist"] = writePlist(PlistValue.PDict(contentsEntries))

    return files
}

/**
 * Reads a UFO 3 project from [files] (as [writeUfoProject] produces, or any spec-conforming UFO's
 * files read off disk by a caller): validates `metainfo.plist`'s `formatVersion` is 3, then reads
 * `fontinfo.plist` (if present) and every glyph `glyphs/contents.plist` lists (the default layer's
 * directory is read from `layercontents.plist` when present, defaulting to `"glyphs"` per the spec
 * when the file is absent — a minimal, non-conforming-but-common project some tools still emit).
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

    val layerDirectory = readDefaultLayerDirectory(files["layercontents.plist"])
    val contentsXml =
        files["$layerDirectory/contents.plist"]
            ?: throw IllegalArgumentException("UFO project is missing required file $layerDirectory/contents.plist")
    val contents = parsePlistDict(contentsXml)

    val glyphs =
        contents.entries.map { (glyphName, fileNameValue) ->
            val fileName =
                (fileNameValue as? PlistValue.PString)?.value
                    ?: throw IllegalArgumentException("contents.plist entry '$glyphName' is not a <string> file name")
            val glifPath = "$layerDirectory/$fileName"
            val glifXml = files[glifPath] ?: throw IllegalArgumentException("contents.plist references missing file $glifPath")
            parseGlif(glifXml)
        }

    return UfoProject(fontInfo, glyphs)
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

private fun fontInfoToPlist(info: UfoFontInfo): PlistValue.PDict {
    val entries = mutableListOf<Pair<String, PlistValue>>()
    info.ascender?.let { entries += "ascender" to PlistValue.PInteger(it.toLong()) }
    info.capHeight?.let { entries += "capHeight" to PlistValue.PInteger(it.toLong()) }
    info.descender?.let { entries += "descender" to PlistValue.PInteger(it.toLong()) }
    info.familyName?.let { entries += "familyName" to PlistValue.PString(it) }
    info.styleName?.let { entries += "styleName" to PlistValue.PString(it) }
    info.unitsPerEm?.let { entries += "unitsPerEm" to PlistValue.PInteger(it.toLong()) }
    info.versionMajor?.let { entries += "versionMajor" to PlistValue.PInteger(it.toLong()) }
    info.versionMinor?.let { entries += "versionMinor" to PlistValue.PInteger(it.toLong()) }
    info.xHeight?.let { entries += "xHeight" to PlistValue.PInteger(it.toLong()) }
    return PlistValue.PDict(entries)
}

private fun fontInfoFromPlist(dict: PlistValue.PDict): UfoFontInfo =
    UfoFontInfo(
        familyName = dict.stringOrNull("familyName"),
        styleName = dict.stringOrNull("styleName"),
        unitsPerEm = dict.intOrNull("unitsPerEm"),
        ascender = dict.intOrNull("ascender"),
        descender = dict.intOrNull("descender"),
        xHeight = dict.intOrNull("xHeight"),
        capHeight = dict.intOrNull("capHeight"),
        versionMajor = dict.intOrNull("versionMajor"),
        versionMinor = dict.intOrNull("versionMinor"),
    )
