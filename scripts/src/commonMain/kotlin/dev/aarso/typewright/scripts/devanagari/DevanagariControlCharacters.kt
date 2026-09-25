// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.devanagari

import dev.aarso.typewright.scripts.ControlCharacter
import dev.aarso.typewright.scripts.ControlCharacterSet
import dev.aarso.typewright.scripts.WritingScript

/**
 * Devanagari's real starting control sequence -- this script's own equivalent of Latin's n/o/H/O
 * (`ControlCharacterSet`'s own KDoc). The order is not invented: it is Design With FontForge's
 * own first-glyph progression, the most concrete pedagogy `docs/RESEARCH_font_quality.md`'s
 * Devanagari section found and quotes by name: "Design With FontForge's first-glyph progression
 * (पाव -> किमीनुफू -> भरसगदह -> height extremes र्मों ड्डू)".
 * `docs/LESSONS_SCAFFOLD.md` section 5 names the same first three words again ("the first-glyph
 * progression from Design With FontForge (पाव -> किमीनुफू -> भरसगदह)"). Both are cited here
 * directly, not re-derived from memory.
 *
 * Each Devanagari word is broken into the real glyphs it is made of (the base letter plus any
 * dependent vowel sign, in Unicode logical order, matching [DevanagariGlyphInventory]'s own
 * names) so a [ControlCharacter] entry always names one real glyph. [ControlCharacterSet] has no
 * field for grouping entries by stage, so the stage and its own word are stated in every entry's
 * own [ControlCharacter.rationale] instead.
 *
 * The fourth stage, "height extremes", is two words the research quotes verbatim (र्मों ड्डू) and
 * neither is plain base-letter coverage: र्मों is रेफ (reph, RA+halant reordered above the
 * following syllable) over मों (म + the two-part ो anusvara-bearing syllable), and ड्डू is a
 * geminate ड्+ड (DDA+halant+DDA) conjunct with the below-headline ऊ-matra. `reph-deva` is a real,
 * separately named glyph the shaping rules require (`docs/RESEARCH_font_quality.md`: "the font
 * must supply reph, rakaar, half forms"); `dda_dda-deva` is this task's own conjunct name, built
 * the way the same research section's own naming example builds one ("conjuncts joined by
 * underscore" -- the section names Ka+Ssa as `ka_ssa-deva` and Ja+Nya as `ja_nya-deva`). Neither
 * `reph-deva` nor `dda_dda-deva` is a [DevanagariGlyphInventory] member: that inventory is
 * deliberately the zip's own base-letter coverage only, per this task's own instruction
 * ("conjuncts (if you include any beyond the zip's own base-letter coverage) joined by
 * underscore per the research's own naming example" -- naming rule, not an inventory
 * requirement), so a control-set entry may name a glyph the base inventory does not carry.
 */
object DevanagariControlCharacters {
    private const val STAGE_1 = "Stage 1 (पाव, pāv)"
    private const val STAGE_2 = "Stage 2 (किमीनुफू, kimīnuphū)"
    private const val STAGE_3 = "Stage 3 (भरसगदह, bharasagadaha)"
    private const val STAGE_4 = "Stage 4, height extremes (र्मों ड्डू)"

    val controlCharacters: List<ControlCharacter> =
        listOf(
            // Stage 1: पाव (pa + aa-matra + va) -- the first word Design With FontForge's own
            // progression draws, per docs/RESEARCH_font_quality.md and docs/LESSONS_SCAFFOLD.md.
            ControlCharacter(
                "pa-deva",
                "$STAGE_1: first consonant drawn, hanging from the shirorekha with the " +
                    "negative sidebearing that lets it overlap the headline.",
            ),
            ControlCharacter(
                "aaMatra-deva",
                "$STAGE_1: the vertical-stem matra -- \"Glyphs' workflow starts with " +
                    "aaMatra-deva (the vertical stem)\" (docs/RESEARCH_font_quality.md).",
            ),
            ControlCharacter(
                "va-deva",
                "$STAGE_1: second consonant; पाव as a whole spaces one open, one closed and " +
                    "one round-bodied letterform against the headline before anything harder " +
                    "is attempted.",
            ),
            // Stage 2: किमीनुफू -- four consonant+matra syllables covering the pre-base
            // (i), post-base (ii, u, uu) matra positions the shaping engine reorders.
            ControlCharacter(
                "ka-deva",
                "$STAGE_2: base consonant of कि; the i-matra that follows it in reading order " +
                    "renders to its left -- \"OpenType fonts should not have substitutions " +
                    "that attempt to perform the re-ordering\" (Microsoft Devanagari spec, " +
                    "docs/RESEARCH_font_quality.md), so this pairing drills a shape the " +
                    "shaping engine, not the drawing, will reorder.",
            ),
            ControlCharacter(
                "iMatra-deva",
                "$STAGE_2: pre-base matra of कि (one of the real, cited iMatra-deva length variants).",
            ),
            ControlCharacter("ma-deva", "$STAGE_2: base consonant of मी."),
            ControlCharacter("iiMatra-deva", "$STAGE_2: post-base matra of मी."),
            ControlCharacter("na-deva", "$STAGE_2: base consonant of नु."),
            ControlCharacter("uMatra-deva", "$STAGE_2: below-base matra of नु, testing the padrekha/talrekha region."),
            ControlCharacter("pha-deva", "$STAGE_2: base consonant of फू."),
            ControlCharacter("uuMatra-deva", "$STAGE_2: below-base matra of फू, the longer companion to uMatra-deva."),
            // Stage 3: भरसगदह -- six consonants, all inherent vowel (no matra), spaced
            // together as a run of bare stems and bowls.
            ControlCharacter("bha-deva", "$STAGE_3: first of six bare-consonant stems spaced together, no matra."),
            ControlCharacter("ra-deva", "$STAGE_3: second bare consonant."),
            ControlCharacter("sa-deva", "$STAGE_3: third bare consonant."),
            ControlCharacter("ga-deva", "$STAGE_3: fourth bare consonant."),
            ControlCharacter("da-deva", "$STAGE_3: fifth bare consonant."),
            ControlCharacter("ha-deva", "$STAGE_3: sixth bare consonant, completing भरसगदह."),
            // Stage 4: height extremes, र्मों (upper: reph + anusvara + o-matra over ma) and
            // ड्डू (lower: a DDA geminate conjunct with the below-headline uu-matra).
            ControlCharacter(
                "reph-deva",
                "$STAGE_4: र्मों's reph (र् reordered above the following syllable) -- the " +
                    "topmost mark this stage tests, above even urdhvarekha.",
            ),
            ControlCharacter(
                "ma-deva",
                "$STAGE_4: र्मों's base consonant, carrying the reph above it and the o-matra/anusvara around it.",
            ),
            ControlCharacter(
                "oMatra-deva",
                "$STAGE_4: र्मों's o-matra, which itself combines a left and a right part -- " +
                    "\"several iMatra-deva length variants\" is the same general point the " +
                    "research makes about matras composing, applied here to o.",
            ),
            ControlCharacter(
                "anusvara-deva",
                "$STAGE_4: र्मों's upper-right dot, stacking with the reph and o-matra at the top extreme.",
            ),
            ControlCharacter(
                "dda_dda-deva",
                "$STAGE_4: ड्डू's geminate DDA+halant+DDA conjunct, named with the underscore " +
                    "join docs/RESEARCH_font_quality.md's own naming example uses for Ka+Ssa " +
                    "and Ja+Nya (ka_ssa-deva, ja_nya-deva) -- not a DevanagariGlyphInventory " +
                    "member; see this file's own class KDoc.",
            ),
            ControlCharacter(
                "uuMatra-deva",
                "$STAGE_4: ड्डू's below-headline matra, the bottom extreme paired against reph's top extreme.",
            ),
        )

    fun build(): ControlCharacterSet = ControlCharacterSet(script = WritingScript.DEVANAGARI, controlCharacters = controlCharacters)
}
