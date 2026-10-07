// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * The real typefaces the Brief asks its questions in (data/brief-cues, built by
 * data/scripts/build_brief_cues.py). Each is an OFL face subset to Basic Latin; [key] is the file
 * and the style atlas's `bundled` key, so the atlas says what each one measures. The UI turns a
 * key into a font; the engine never draws.
 */
enum class CueFace(
    val key: String,
    val family: String,
) {
    GEOMETRIC("geometric", "Poppins"),
    WEIGHT_LIGHT("weightLight", "Jost Light"),
    WEIGHT_REGULAR("weightRegular", "Jost"),
    WEIGHT_BOLD("weightBold", "Jost Bold"),
    GROTESQUE("grotesque", "Work Sans"),
    NEOGROTESQUE("neogrotesque", "Inter"),
    HUMANIST("humanist", "Open Sans"),
    GARALDE("garalde", "EB Garamond"),
    TRANSITIONAL("transitional", "Libre Baskerville"),
    DIDONE("didone", "Libre Bodoni"),
    SLAB("slab", "Zilla Slab"),
    DECO("deco", "Limelight"),
    DECO_AIRY("decoAiry", "Poiret One"),
    SUPERELLIPSE("superellipse", "Michroma"),
    TECHNO("techno", "Orbitron"),
    CONDENSED("condensed", "League Gothic"),
    ROUNDED("rounded", "Varela Round"),
    LOW_WAIST("lowWaist", "Voltaire"),
    SCRIPT("script", "Dancing Script"),
    BLACKLETTER("blackletter", "UnifrakturMaguntia"),
    FATFACE("fatface", "Abril Fatface"),
    MONO("mono", "Space Mono"),
    ;

    companion object {
        /** The cue face with this atlas key, or null. */
        fun byKey(key: String): CueFace? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What an answer is shown as: [text] set in [face]. [tabular] asks for tabular figures (a
 * watch-face proof). Letters only, never a picture of letters: brief principle 11.
 */
data class Cue(
    val face: CueFace,
    val text: String,
    val tabular: Boolean = false,
)
