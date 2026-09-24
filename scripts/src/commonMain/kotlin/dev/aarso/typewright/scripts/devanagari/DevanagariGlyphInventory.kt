package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.GlyphInventory
import dev.aarso.typewright.scripts.GlyphSpec
import dev.aarso.typewright.scripts.WritingScript

/**
 * Devanagari's real glyph inventory, built from `scripts/templates/hyle-all-templates.zip`'s own
 * `svg/Devanagari/` folder listing -- not invented independently of it (this task's own
 * instruction). That folder holds 68 template files (confirmed: `unzip -l
 * scripts/templates/hyle-all-templates.zip` lists `svg/Devanagari/000_...svg` through
 * `svg/Devanagari/067_...svg`, and the pack's own `HOW_TO_USE.md` states "Devanagari | svg/
 * Devanagari | 68 | vowels, consonants, matras, digits; conjuncts come later in shaping" --
 * matching exactly), but two of those 68 files are byte-identical duplicates of an earlier file
 * (confirmed by content hash, see `data/scripts/build_devanagari_template_manifest.py` and the
 * generated `scripts/templates/devanagari-manifest.json`): `011_DEVANAGARI_LETTER_A.svg`
 * duplicates `000_DEVANAGARI_LETTER_A.svg`, and `056_DEVANAGARI_SIGN_ANUSVARA.svg` duplicates
 * `012_DEVANAGARI_SIGN_ANUSVARA.svg`. This inventory therefore lists 66 distinct glyphs -- one
 * [GlyphSpec] per distinct Unicode name the folder covers, never two for the same glyph. The
 * folder's own template *count* (68) is preserved honestly in [DevanagariTemplateSheet], whose
 * 68 entries include both duplicate files pointed at the one glyph they each really draw. See
 * `docs/OPEN_QUESTIONS.md` for the write-up of this finding.
 *
 * Every glyph is a real Unicode character the folder's own filenames name (independent vowels,
 * the 33 standard consonants, the ten dependent vowel signs / matras, anusvara, visarga, and the
 * ten digits -- no conjuncts, no vowels or consonants beyond the folder's own coverage). Names
 * end `-deva` per this build's own naming convention (handoff M8 / `ScriptSpec.name`'s own KDoc)
 * and `docs/RESEARCH_font_quality.md`'s citation of the Glyphs Devanagari recipe ("names use the
 * `-deva` suffix"); a dependent vowel sign is named `<vowel>Matra-deva`, the exact pattern that
 * same citation evidences by name twice over ("Glyphs' workflow starts with `aaMatra-deva` (the
 * vertical stem) ... several `iMatra-deva` length variants"). [GlyphSpec.unicodeName] is the
 * folder's own Unicode character name with underscores turned back into spaces (e.g.
 * `013_DEVANAGARI_LETTER_KA.svg` -> "DEVANAGARI LETTER KA"), and [GlyphSpec.codepoint] is that
 * name's real Unicode scalar value, looked up mechanically (Python's `unicodedata.lookup`, in
 * `build_devanagari_template_manifest.py`) rather than typed from memory.
 */
object DevanagariGlyphInventory {
    // Independent vowels -- svg/Devanagari/000-010 (011 duplicates 000, see class KDoc).
    private val independentVowels =
        listOf(
            GlyphSpec("a-deva", "DEVANAGARI LETTER A", 0x0905),
            GlyphSpec("aa-deva", "DEVANAGARI LETTER AA", 0x0906),
            GlyphSpec("i-deva", "DEVANAGARI LETTER I", 0x0907),
            GlyphSpec("ii-deva", "DEVANAGARI LETTER II", 0x0908),
            GlyphSpec("u-deva", "DEVANAGARI LETTER U", 0x0909),
            GlyphSpec("uu-deva", "DEVANAGARI LETTER UU", 0x090A),
            GlyphSpec("vocalicR-deva", "DEVANAGARI LETTER VOCALIC R", 0x090B),
            GlyphSpec("e-deva", "DEVANAGARI LETTER E", 0x090F),
            GlyphSpec("ai-deva", "DEVANAGARI LETTER AI", 0x0910),
            GlyphSpec("o-deva", "DEVANAGARI LETTER O", 0x0913),
            GlyphSpec("au-deva", "DEVANAGARI LETTER AU", 0x0914),
        )

    // svg/Devanagari/012 (056 duplicates it, see class KDoc).
    private val anusvara = GlyphSpec("anusvara-deva", "DEVANAGARI SIGN ANUSVARA", 0x0902)

    // The 33 standard consonants -- svg/Devanagari/013-045.
    private val consonants =
        listOf(
            GlyphSpec("ka-deva", "DEVANAGARI LETTER KA", 0x0915),
            GlyphSpec("kha-deva", "DEVANAGARI LETTER KHA", 0x0916),
            GlyphSpec("ga-deva", "DEVANAGARI LETTER GA", 0x0917),
            GlyphSpec("gha-deva", "DEVANAGARI LETTER GHA", 0x0918),
            GlyphSpec("nga-deva", "DEVANAGARI LETTER NGA", 0x0919),
            GlyphSpec("ca-deva", "DEVANAGARI LETTER CA", 0x091A),
            GlyphSpec("cha-deva", "DEVANAGARI LETTER CHA", 0x091B),
            GlyphSpec("ja-deva", "DEVANAGARI LETTER JA", 0x091C),
            GlyphSpec("jha-deva", "DEVANAGARI LETTER JHA", 0x091D),
            GlyphSpec("nya-deva", "DEVANAGARI LETTER NYA", 0x091E),
            GlyphSpec("tta-deva", "DEVANAGARI LETTER TTA", 0x091F),
            GlyphSpec("ttha-deva", "DEVANAGARI LETTER TTHA", 0x0920),
            GlyphSpec("dda-deva", "DEVANAGARI LETTER DDA", 0x0921),
            GlyphSpec("ddha-deva", "DEVANAGARI LETTER DDHA", 0x0922),
            GlyphSpec("nna-deva", "DEVANAGARI LETTER NNA", 0x0923),
            GlyphSpec("ta-deva", "DEVANAGARI LETTER TA", 0x0924),
            GlyphSpec("tha-deva", "DEVANAGARI LETTER THA", 0x0925),
            GlyphSpec("da-deva", "DEVANAGARI LETTER DA", 0x0926),
            GlyphSpec("dha-deva", "DEVANAGARI LETTER DHA", 0x0927),
            GlyphSpec("na-deva", "DEVANAGARI LETTER NA", 0x0928),
            GlyphSpec("pa-deva", "DEVANAGARI LETTER PA", 0x092A),
            GlyphSpec("pha-deva", "DEVANAGARI LETTER PHA", 0x092B),
            GlyphSpec("ba-deva", "DEVANAGARI LETTER BA", 0x092C),
            GlyphSpec("bha-deva", "DEVANAGARI LETTER BHA", 0x092D),
            GlyphSpec("ma-deva", "DEVANAGARI LETTER MA", 0x092E),
            GlyphSpec("ya-deva", "DEVANAGARI LETTER YA", 0x092F),
            GlyphSpec("ra-deva", "DEVANAGARI LETTER RA", 0x0930),
            GlyphSpec("la-deva", "DEVANAGARI LETTER LA", 0x0932),
            GlyphSpec("va-deva", "DEVANAGARI LETTER VA", 0x0935),
            GlyphSpec("sha-deva", "DEVANAGARI LETTER SHA", 0x0936),
            GlyphSpec("ssa-deva", "DEVANAGARI LETTER SSA", 0x0937),
            GlyphSpec("sa-deva", "DEVANAGARI LETTER SA", 0x0938),
            GlyphSpec("ha-deva", "DEVANAGARI LETTER HA", 0x0939),
        )

    // Dependent vowel signs (matras) -- svg/Devanagari/046-055.
    private val matras =
        listOf(
            GlyphSpec("aaMatra-deva", "DEVANAGARI VOWEL SIGN AA", 0x093E),
            GlyphSpec("iMatra-deva", "DEVANAGARI VOWEL SIGN I", 0x093F),
            GlyphSpec("iiMatra-deva", "DEVANAGARI VOWEL SIGN II", 0x0940),
            GlyphSpec("uMatra-deva", "DEVANAGARI VOWEL SIGN U", 0x0941),
            GlyphSpec("uuMatra-deva", "DEVANAGARI VOWEL SIGN UU", 0x0942),
            GlyphSpec("vocalicRMatra-deva", "DEVANAGARI VOWEL SIGN VOCALIC R", 0x0943),
            GlyphSpec("eMatra-deva", "DEVANAGARI VOWEL SIGN E", 0x0947),
            GlyphSpec("aiMatra-deva", "DEVANAGARI VOWEL SIGN AI", 0x0948),
            GlyphSpec("oMatra-deva", "DEVANAGARI VOWEL SIGN O", 0x094B),
            GlyphSpec("auMatra-deva", "DEVANAGARI VOWEL SIGN AU", 0x094C),
        )

    // svg/Devanagari/057.
    private val visarga = GlyphSpec("visarga-deva", "DEVANAGARI SIGN VISARGA", 0x0903)

    // Digits -- svg/Devanagari/058-067.
    private val digits =
        listOf(
            GlyphSpec("zero-deva", "DEVANAGARI DIGIT ZERO", 0x0966),
            GlyphSpec("one-deva", "DEVANAGARI DIGIT ONE", 0x0967),
            GlyphSpec("two-deva", "DEVANAGARI DIGIT TWO", 0x0968),
            GlyphSpec("three-deva", "DEVANAGARI DIGIT THREE", 0x0969),
            GlyphSpec("four-deva", "DEVANAGARI DIGIT FOUR", 0x096A),
            GlyphSpec("five-deva", "DEVANAGARI DIGIT FIVE", 0x096B),
            GlyphSpec("six-deva", "DEVANAGARI DIGIT SIX", 0x096C),
            GlyphSpec("seven-deva", "DEVANAGARI DIGIT SEVEN", 0x096D),
            GlyphSpec("eight-deva", "DEVANAGARI DIGIT EIGHT", 0x096E),
            GlyphSpec("nine-deva", "DEVANAGARI DIGIT NINE", 0x096F),
        )

    /** All 66 distinct glyphs, in the template folder's own index order (duplicates collapsed). */
    val glyphs: List<GlyphSpec> =
        independentVowels + anusvara + consonants + matras + visarga + digits

    fun build(): GlyphInventory = GlyphInventory(script = WritingScript.DEVANAGARI, glyphs = glyphs)
}
