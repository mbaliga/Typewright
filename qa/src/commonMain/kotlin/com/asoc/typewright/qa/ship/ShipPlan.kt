// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.project.imports.LicenceKind

/** The two kinds of repository Ship writes: Google Fonts' upstream layout, or a plain one under a licence the person chose. */
enum class ShipMode(
    val label: String,
) {
    GOOGLE_FONTS("Google Fonts"),
    OWN_LICENCE("Own licence"),
}

/**
 * The five categories Google Fonts' `METADATA.pb` accepts for a family (Fontbakery's `metadata/category` check lists exactly these).
 * [fromStyleKey] is Typewright's own reading of a style class, which the screen calls our heuristic: the person can choose another.
 */
enum class GfCategory(
    val protoName: String,
    val label: String,
) {
    SANS_SERIF("SANS_SERIF", "Sans serif"),
    SERIF("SERIF", "Serif"),
    DISPLAY("DISPLAY", "Display"),
    HANDWRITING("HANDWRITING", "Handwriting"),
    MONOSPACE("MONOSPACE", "Monospace"),
    ;

    companion object {
        /**
         * The category Typewright reads a style-atlas class (`sans-geometric`, `slab`, `script-formal`…) as, or null for a key it does not
         * know: a class added to the atlas later reads as none until it is put here, never as a guess. The table follows the groups the brief's
         * own genre list puts each class in (a test in `ui` holds the two together): a fat face is a display face, a script is handwriting.
         */
        fun fromStyleKey(key: String?): GfCategory? = key?.let { BY_STYLE_KEY[it] }

        private val BY_STYLE_KEY: Map<String, GfCategory> =
            mapOf(
                "sans-geometric" to SANS_SERIF,
                "sans-grotesque" to SANS_SERIF,
                "sans-neogrotesque" to SANS_SERIF,
                "sans-humanist" to SANS_SERIF,
                "sans-rounded" to SANS_SERIF,
                "sans-superellipse" to SANS_SERIF,
                "sans-glyphic" to SANS_SERIF,
                "serif-garalde" to SERIF,
                "serif-transitional" to SERIF,
                "serif-didone" to SERIF,
                "serif-venetian" to SERIF,
                "serif-scotch" to SERIF,
                "serif-fatface" to DISPLAY,
                "slab" to SERIF,
                "script-formal" to HANDWRITING,
                "script-informal" to HANDWRITING,
                "script-handwritten" to HANDWRITING,
                "display-artdeco" to DISPLAY,
                "display-techno" to DISPLAY,
                "display-stencil" to DISPLAY,
                "display-inline" to DISPLAY,
                "blackletter" to DISPLAY,
                "monospace" to MONOSPACE,
            )
    }
}

/** What the font covers, for the subsets `METADATA.pb` names and the description's one measured paragraph. */
class ShipFont(
    val glyphCount: Int,
    val codepoints: Set<Int>,
    /** The scripts the project says it covers, by ISO 15924 tag (`Latn`). */
    val scripts: List<String>,
    val capHeight: Int?,
    val xHeight: Int?,
)

/** What the brief says in words, when it says anything: the genre's name and the uses. Both are the person's choices; neither is ever guessed. */
class ShipBrief(
    val genre: String? = null,
    val uses: List<String> = emptyList(),
)

/** What a layer-one tool said of the compiled font, for the line in the request text. */
class LayerOneSummary(
    val tool: String,
    val fail: Int,
    val warn: Int,
    val pass: Int,
)

/** Everything [buildShipPlan] reads. [ufo] holds the master UFO's files exactly as the session wrote them, keyed by path inside the UFO. */
class ShipInputs(
    val mode: ShipMode,
    val facts: ShipFacts,
    val font: ShipFont,
    val brief: ShipBrief,
    val ufoFolderName: String,
    val ufo: Map<String, ByteArray>,
    /** The compiled font, or null where nothing was compiled (a phone or a browser): then only the files that do not describe a binary are written. */
    val binary: ByteArray?,
    val category: GfCategory?,
    val layerOne: LayerOneSummary?,
    /** The day the folder is made, `2026-10-24`. */
    val today: String,
)

/** A file the plan leaves out and the reason, in a sentence the screen can show. */
data class Omission(
    val path: String,
    val reason: String,
)

/**
 * The repository folder as data: every file by path, the files left out and why, the text of the request a person pastes, and the
 * family directory Google Fonts would receive. It is a pure function of [ShipInputs]; writing it is a platform's business.
 */
class ShipPlan(
    val files: Map<String, ByteArray>,
    val omissions: List<Omission>,
    /** The request text for an issue or a pull request, or null in a mode that does not ask Google Fonts for anything. */
    val requestText: String?,
    /** `hyledeco`: the directory name under `ofl/` that Google Fonts would give the family. */
    val familyDirectory: String?,
    /** The files as `upstream.yaml` maps them into Google Fonts' `ofl/<family>/`: destination path to bytes. */
    val familyFiles: Map<String, ByteArray>,
    /** The folders a writer replaces as a whole (the UFO), so that a glyph deleted since the last write does not linger in the old copy. */
    val ownedDirectories: List<String>,
) {
    /** Every path in the folder, sorted. */
    val paths: List<String> get() = files.keys.sorted()

    /**
     * The folder as a person reads it: every file, with the UFO's files folded into the one line `sources/<name>.ufo/`, sorted. This is the
     * list the page shows under "Writes to" and the list a finished write reports.
     */
    val tree: List<String>
        get() =
            paths
                .map { path -> ownedDirectories.firstOrNull { path.startsWith("$it/") }?.let { "$it/" } ?: path }
                .distinct()
}

/** The path of the request text in the folder. */
const val REQUEST_TEXT_PATH: String = "documentation/GOOGLE_FONTS_REQUEST.md"

/**
 * The folder for [inputs]. Nothing is invented: a file whose content needs a fact the project does not state is left out and listed in
 * [ShipPlan.omissions], and a line of a file whose fact is missing is left out of that file.
 *
 * Google Fonts mode writes `OFL.txt`, `AUTHORS.txt`, `DESCRIPTION.en_us.html` (a draft from what the project and its brief say),
 * `METADATA.pb`, `upstream.yaml`, `fonts/ttf/<Family>-<Style>.ttf`, `sources/<Family>-<Style>.ufo/` and the request text. Own licence mode
 * writes the font, the sources and the licence file the person's licence calls for, and nothing that is only for Google Fonts.
 */
fun buildShipPlan(inputs: ShipInputs): ShipPlan {
    val facts = inputs.facts
    val stem = shipFileStem(facts, inputs.ufoFolderName)
    val files = LinkedHashMap<String, ByteArray>()
    val omissions = mutableListOf<Omission>()

    fun put(
        path: String,
        text: String,
    ) {
        files[path] = text.encodeToByteArray()
    }
    for ((inside, bytes) in inputs.ufo) files["sources/$stem.ufo/$inside"] = bytes
    inputs.binary?.let { files["fonts/ttf/$stem.ttf"] = it }
    if (inputs.binary == null) {
        omissions += Omission("fonts/ttf/$stem.ttf", "nothing was compiled here, so there is no font file")
    }

    val copyright = facts.copyright
    when (inputs.mode) {
        ShipMode.GOOGLE_FONTS -> {
            if (copyright == null) {
                omissions += Omission("OFL.txt", "the project has no copyright line to put first in it")
            } else if (facts.licence != LicenceKind.OFL) {
                omissions += Omission("OFL.txt", "the project's licence is not the SIL Open Font License")
            } else {
                put("OFL.txt", generateOflFromCopyright(copyright))
            }
            val authors = facts.designer?.let(::authorsText)
            if (authors != null) put("AUTHORS.txt", authors) else omissions += Omission("AUTHORS.txt", "the project names no designer")
            put("DESCRIPTION.en_us.html", descriptionHtml(inputs))
            if (inputs.binary != null) {
                put("METADATA.pb", metadataPb(inputs, stem, omissions))
                put("upstream.yaml", upstreamYaml(facts, stem, files.keys, omissions))
            } else {
                omissions += Omission("METADATA.pb", "it describes the font file, and nothing was compiled here")
                omissions += Omission("upstream.yaml", "it maps the font file, and nothing was compiled here")
            }
            put(REQUEST_TEXT_PATH, requestText(inputs, stem))
        }

        ShipMode.OWN_LICENCE -> {
            ownLicenceFile(inputs)?.let { (path, text) -> put(path, text) }
                ?: omissions.add(Omission("LICENSE.txt", ownLicenceOmission(facts)))
        }
    }

    val sorted = files.entries.sortedBy { it.key }.associate { it.key to it.value }
    val (directory, family) = familyFilesOf(inputs, stem, sorted)
    return ShipPlan(
        files = sorted,
        omissions = omissions,
        requestText = if (inputs.mode == ShipMode.GOOGLE_FONTS) files[REQUEST_TEXT_PATH]?.decodeToString() else null,
        familyDirectory = directory,
        familyFiles = family,
        ownedDirectories = if (inputs.ufo.isEmpty()) emptyList() else listOf("sources/$stem.ufo"),
    )
}

/**
 * What the folder calls the font, its UFO and its request: `HyleDeco-Regular` from the family and the style the project states, and only
 * when it states no family or no style the name of the UFO folder it already has. The compiled binary is checked under this name too, since
 * Google Fonts' profile judges a file by its name, and a UFO that was adopted from elsewhere can be called anything.
 */
fun shipFileStem(
    facts: ShipFacts,
    ufoFolderName: String,
): String = facts.fileStem ?: ufoFolderName.removeSuffix(".ufo")

/** `ofl/<directory>` for [family]: lower case, letters and digits only, as Google Fonts' own check expects it. */
fun googleFontsDirectoryName(family: String): String = family.lowercase().filter { it.isLetterOrDigit() }

private fun familyFilesOf(
    inputs: ShipInputs,
    stem: String,
    files: Map<String, ByteArray>,
): Pair<String?, Map<String, ByteArray>> {
    if (inputs.mode != ShipMode.GOOGLE_FONTS) return null to emptyMap()
    val directory =
        inputs.facts.family
            ?.let(::googleFontsDirectoryName)
            ?.takeIf { it.isNotEmpty() }
    val mapped = linkedMapOf<String, ByteArray>()
    for ((from, to) in upstreamFileMap(stem, files.keys)) files[from]?.let { mapped[to] = it }
    return directory to mapped
}

/** Which files of the repository `upstream.yaml` hands to Google Fonts, from where in the repository to where in the family directory. */
internal fun upstreamFileMap(
    stem: String,
    present: Collection<String>,
): List<Pair<String, String>> =
    listOf(
        "OFL.txt" to "OFL.txt",
        "DESCRIPTION.en_us.html" to "DESCRIPTION.en_us.html",
        "METADATA.pb" to "METADATA.pb",
        "fonts/ttf/$stem.ttf" to "$stem.ttf",
    ).filter { it.first in present }
