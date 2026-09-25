// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

/**
 * SCAFFOLD (docs/LESSONS_SCAFFOLD.md's convention: "ships marked SCAFFOLD in the UI until
 * Madhav's material replaces or augments it"). Written to be overwritten.
 *
 * One term of the style-identification feature vocabulary: a plain-language name, a
 * plain-language definition a learner can read without already knowing type terminology, and the
 * glyphs where the term applies. This is data, not code documentation -- TYPEWRIGHT_BUILD_BRIEF.md
 * 8.4's own last sentence is the reason it lives here rather than only as KDoc in
 * `qa:corpus`'s style detector: "the inference uses the same feature vocabulary the Lineages
 * lesson teaches, so identifying your own font is the exam." [STYLE_FEATURE_VOCABULARY] is the
 * nine features `dev.aarso.typewright.qa.corpus.style`'s extractor measures, in the same order
 * brief 8.4 lists them, worded for a learner rather than for a reviewer of the scorer's code.
 *
 * P6 (the scene-YAML engine, docs/ARCHITECTURE_REVIEW.md section 7) is what turns this into an
 * actual Lineages scene; this file deliberately stops at the vocabulary itself, per P1b's own
 * scope note ("just get the vocabulary itself into a place P6 can consume later").
 */
public data class FeatureVocabularyEntry(
    val termName: String,
    val plainLanguageDefinition: String,
    val exampleGlyphs: List<String>,
)

/**
 * SCAFFOLD. The nine style-identification features, plain-language, in
 * TYPEWRIGHT_BUILD_BRIEF.md 8.4's own order: contrast ratio, stress angle, serif presence and
 * bracket, storeys, terminal style, aperture, roundness, x-height ratio, width class.
 */
public val STYLE_FEATURE_VOCABULARY: List<FeatureVocabularyEntry> =
    listOf(
        FeatureVocabularyEntry(
            termName = "Contrast",
            plainLanguageDefinition =
                "How much thicker a letter's thickest stroke is than its thinnest, as a ratio. " +
                    "A contrast of 1 means every stroke is the same width -- monoline, like a felt-tip pen. " +
                    "A contrast of 5 or more means some strokes are hairline-thin next to strokes five times " +
                    "as thick, the look of a pointed pen pressed hard on the down-strokes.",
            exampleGlyphs = listOf("o", "O", "e"),
        ),
        FeatureVocabularyEntry(
            termName = "Stress",
            plainLanguageDefinition =
                "The direction a curved stroke is thickest in, measured as an angle from straight up and down. " +
                    "Zero degrees is called vertical stress -- the O is thickest at its top and bottom, like " +
                    "modern Didone types. An oblique stress leans the thick parts toward a diagonal, the way " +
                    "a broad nib held at an angle naturally draws, the mark of an old-style face like Garamond.",
            exampleGlyphs = listOf("O", "o", "e"),
        ),
        FeatureVocabularyEntry(
            termName = "Serif and bracket",
            plainLanguageDefinition =
                "A serif is a small foot or cap added at the end of a main stroke. A bracket is the curve " +
                    "that eases a serif into the stroke it belongs to: a bracketed serif blends in smoothly, " +
                    "the way a chisel-cut letterform does; an unbracketed serif meets the stroke at a sharp, " +
                    "square corner, like a slab of stone or a strip of tape.",
            exampleGlyphs = listOf("T", "I", "H"),
        ),
        FeatureVocabularyEntry(
            termName = "Storeys",
            plainLanguageDefinition =
                "Whether the lowercase a and g have one closed bowl (single storey, the shape a child's " +
                    "printing often uses, and the shape geometric sans faces like Futura draw) or two " +
                    "(double storey: a small bowl sitting over a larger one, the shape most books are set in).",
            exampleGlyphs = listOf("a", "g"),
        ),
        FeatureVocabularyEntry(
            termName = "Terminal",
            plainLanguageDefinition =
                "How an open stroke ends -- the tip of a c, or the arm of an r or f. A flat terminal is cut " +
                    "straight across, square to the page; a round terminal tapers into a soft, ball-like end; " +
                    "an angled terminal is cut on a diagonal, the way a broad pen naturally lifts off the page " +
                    "mid-stroke.",
            exampleGlyphs = listOf("c", "r", "f", "s"),
        ),
        FeatureVocabularyEntry(
            termName = "Aperture",
            plainLanguageDefinition =
                "The width of the gap where a curved counter opens to the outside, in letters like c, e and " +
                    "s. A closed (or tight) aperture leaves only a narrow slit; an open aperture leaves the " +
                    "counter's mouth wide, which is what keeps a letter reading clearly at small sizes.",
            exampleGlyphs = listOf("c", "e", "s"),
        ),
        FeatureVocabularyEntry(
            termName = "Roundness",
            plainLanguageDefinition =
                "How close a round letter's outline is to a true circle versus a rounded square, fitted as a " +
                    "single number (a superellipse exponent): 2 describes a perfect ellipse; higher numbers " +
                    "describe a shape that squares off toward the corners, the way a rounded rectangle does.",
            exampleGlyphs = listOf("o", "O", "Q"),
        ),
        FeatureVocabularyEntry(
            termName = "x-height ratio",
            plainLanguageDefinition =
                "How tall the lowercase letters (measured to the top of an x) stand next to the capitals " +
                    "(measured to the top of an H), as a fraction. A small x-height, common in old-style " +
                    "faces, leaves generous white space above lowercase letters; a large one, common in " +
                    "faces made to read well at small sizes or on screens, makes lowercase text sit taller.",
            exampleGlyphs = listOf("x", "H"),
        ),
        FeatureVocabularyEntry(
            termName = "Width",
            plainLanguageDefinition =
                "How wide a font's letters sit on average, relative to their height -- normal, condensed, or " +
                    "extended -- measured here as the average advance width of a sample of letters divided by " +
                    "the font's units-per-em, so it compares fairly across fonts drawn at different unit sizes.",
            exampleGlyphs = listOf("n", "o", "H"),
        ),
    )
