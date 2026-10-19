// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * One thing a Brief question asks about, in the person's words ("How round are the rounds?"),
 * and the measured [features] that answer it. Serifs are two features: whether there is a serif,
 * and how it joins the stem.
 */
enum class Dimension(
    val id: String,
    val title: String,
    val prompt: String,
    val why: String,
    val features: List<Feature>,
) {
    CONTRAST(
        "contrast",
        "Contrast",
        "How much does the stroke change between thick and thin?",
        "Contrast is read before anything else, even from across a room. It places a face as surely as serifs do.",
        listOf(Feature.CONTRAST),
    ),
    STRESS(
        "stress",
        "Stress",
        "Where do the thick parts of the o sit?",
        "A broad pen held at an angle tilts the thick parts; an engraver or a pointed pen stands them upright.",
        listOf(Feature.STRESS),
    ),
    SERIFS(
        "serifs",
        "Serifs",
        "How do the strokes end?",
        "Whether there are serifs, and how they meet the stem, carries the period and the voice.",
        listOf(Feature.SERIF, Feature.BRACKET),
    ),
    A_FORM(
        "aForm",
        "The a",
        "Which a?",
        "The one-storey a is the mark of geometric faces and many deco ones; the two-storey a is the reading letter.",
        listOf(Feature.A_STOREYS),
    ),
    G_FORM(
        "gForm",
        "The g",
        "Which g?",
        "A two-storey g is bookish and traditional; one storey is plainer and more modern.",
        listOf(Feature.G_STOREYS),
    ),
    TERMINALS(
        "terminals",
        "Terminals",
        "How are the open strokes cut?",
        "The ends of c, e, s, a and r set the tone: flat is neutral, round is friendly, angled feels made by hand.",
        listOf(Feature.TERMINAL),
    ),
    ROUNDNESS(
        "roundness",
        "Rounds",
        "How round are the round letters?",
        "Circular rounds are geometric; squarish rounds read technical and save width.",
        listOf(Feature.ROUNDNESS),
    ),
    APERTURE(
        "aperture",
        "Apertures",
        "How open are c, e and s?",
        "Open apertures stay legible small and at a glance; closed ones look compact and even.",
        listOf(Feature.APERTURE),
    ),
    X_HEIGHT(
        "xHeight",
        "x-height",
        "How tall is the lowercase next to the capitals?",
        "A large x-height reads bigger at the same size; a small one is bookish and elegant.",
        listOf(Feature.X_HEIGHT),
    ),
    WIDTH(
        "width",
        "Width",
        "How wide are the letters?",
        "Narrow saves space and looks tall; wide looks stable and technical.",
        listOf(Feature.WIDTH),
    ),
    WEIGHT(
        "weight",
        "Weight",
        "How heavy is the first weight you will draw?",
        "Most families start from the Regular. A watch face's always-on mode may want a light one.",
        listOf(Feature.WEIGHT),
    ),
    WAIST(
        "waist",
        "Crossbar",
        "Where do the bars of H, E and A sit?",
        "Moving the bar off the middle is the quickest way to the 1920s; a centred bar is calm and neutral.",
        listOf(Feature.WAIST),
    ),
    ;

    /** The answers to this dimension, in the order a question shows them. */
    val levels: List<Level> get() = Level.entries.filter { it.dimension == this }

    companion object {
        /** The dimension with this id, or null. */
        fun byId(id: String): Dimension? = entries.firstOrNull { it.id == id }
    }
}

/** Which faces a quantile band is taken over: the ten node-economy classes, or every face the atlas measured. */
enum class Population { CORPUS, ALL }

/**
 * How an answer becomes a measured target. Every anchor names real fonts in the style atlas, so
 * a target is always a statement about professional typefaces (CLAUDE.md law 5).
 */
sealed interface Anchor {
    val feature: Feature

    /** The quantile band [lo]..[hi] of [feature] over the faces of [genres] pooled (the middle half by default). */
    data class Pooled(
        override val feature: Feature,
        val genres: List<String>,
        val lo: Double = 0.25,
        val hi: Double = 0.75,
    ) : Anchor

    /** The quantile band [lo]..[hi] of [feature] over a whole [population]. */
    data class Band(
        override val feature: Feature,
        val population: Population,
        val lo: Double,
        val hi: Double,
    ) : Anchor

    /** A categorical value. */
    data class Value(
        override val feature: Feature,
        val value: String,
    ) : Anchor
}

/**
 * One answer to a [Dimension]: its [label] on the option, the [phrase] it adds to the ethos
 * sentence, what it means in measured terms ([anchors]), and the [cue] it is shown in. A level's
 * cue face is checked against its own anchors in this module's tests, so the font shown for an
 * answer really has that answer. The answers at either end of a scale are open-ended (a quantile
 * band running to 0 or 1): nothing is "too monoline" for Even or too contrasted for Extreme.
 */
enum class Level(
    val dimension: Dimension,
    val id: String,
    val label: String,
    val phrase: String,
    val anchors: List<Anchor>,
    val cue: Cue,
) {
    CONTRAST_EVEN(
        Dimension.CONTRAST,
        "even",
        "Even",
        "monoline",
        listOf(Anchor.Pooled(Feature.CONTRAST, listOf("sans-geometric", "sans-neogrotesque", "sans-rounded"), lo = 0.0)),
        Cue(CueFace.GEOMETRIC, "o"),
    ),
    CONTRAST_GENTLE(
        Dimension.CONTRAST,
        "gentle",
        "Gentle",
        "gently modulated",
        listOf(Anchor.Pooled(Feature.CONTRAST, listOf("sans-humanist", "sans-grotesque", "slab", "sans-superellipse"))),
        Cue(CueFace.HUMANIST, "o"),
    ),
    CONTRAST_STRONG(
        Dimension.CONTRAST,
        "strong",
        "Strong",
        "high-contrast",
        listOf(Anchor.Pooled(Feature.CONTRAST, listOf("serif-garalde", "serif-transitional", "serif-venetian"))),
        Cue(CueFace.TRANSITIONAL, "o"),
    ),
    CONTRAST_EXTREME(
        Dimension.CONTRAST,
        "extreme",
        "Extreme",
        "hairline-contrast",
        listOf(Anchor.Pooled(Feature.CONTRAST, listOf("serif-didone", "serif-fatface"), hi = 1.0)),
        Cue(CueFace.DIDONE, "o"),
    ),
    STRESS_VERTICAL(
        Dimension.STRESS,
        "vertical",
        "Upright",
        "vertical stress",
        listOf(Anchor.Pooled(Feature.STRESS, listOf("serif-didone", "serif-transitional"), lo = 0.0)),
        Cue(CueFace.DIDONE, "o"),
    ),
    STRESS_DIAGONAL(
        Dimension.STRESS,
        "diagonal",
        "Tilted, like a pen",
        "pen-angled stress",
        listOf(Anchor.Pooled(Feature.STRESS, listOf("serif-garalde", "serif-venetian"), hi = 1.0)),
        Cue(CueFace.GARALDE, "o"),
    ),
    SERIFS_NONE(
        Dimension.SERIFS,
        "none",
        "No serifs",
        "sans-serif",
        listOf(Anchor.Value(Feature.SERIF, "no")),
        Cue(CueFace.GROTESQUE, "n"),
    ),
    SERIFS_BRACKETED(
        Dimension.SERIFS,
        "bracketed",
        "Bracketed",
        "bracketed serifs",
        listOf(
            Anchor.Value(Feature.SERIF, "yes"),
            Anchor.Pooled(Feature.BRACKET, listOf("serif-garalde", "serif-transitional", "serif-venetian"), hi = 1.0),
        ),
        Cue(CueFace.GARALDE, "n"),
    ),
    SERIFS_SLAB(
        Dimension.SERIFS,
        "slab",
        "Slab",
        "slab serifs",
        listOf(Anchor.Value(Feature.SERIF, "yes"), Anchor.Pooled(Feature.BRACKET, listOf("slab"))),
        Cue(CueFace.SLAB, "n"),
    ),
    SERIFS_HAIRLINE(
        Dimension.SERIFS,
        "hairline",
        "Hairline",
        "hairline serifs",
        listOf(Anchor.Value(Feature.SERIF, "yes"), Anchor.Pooled(Feature.BRACKET, listOf("serif-didone"))),
        Cue(CueFace.DIDONE, "n"),
    ),
    A_SINGLE(
        Dimension.A_FORM,
        "single",
        "One storey",
        "a one-storey a",
        listOf(Anchor.Value(Feature.A_STOREYS, "single")),
        Cue(CueFace.GEOMETRIC, "a"),
    ),
    A_DOUBLE(
        Dimension.A_FORM,
        "double",
        "Two storeys",
        "a two-storey a",
        listOf(Anchor.Value(Feature.A_STOREYS, "double")),
        Cue(CueFace.GROTESQUE, "a"),
    ),
    G_SINGLE(
        Dimension.G_FORM,
        "single",
        "One storey",
        "a one-storey g",
        listOf(Anchor.Value(Feature.G_STOREYS, "single")),
        Cue(CueFace.NEOGROTESQUE, "g"),
    ),
    G_DOUBLE(
        Dimension.G_FORM,
        "double",
        "Two storeys",
        "a two-storey g",
        listOf(Anchor.Value(Feature.G_STOREYS, "double")),
        Cue(CueFace.HUMANIST, "g"),
    ),
    TERMINALS_FLAT(
        Dimension.TERMINALS,
        "flat",
        "Flat",
        "flat-cut terminals",
        listOf(Anchor.Value(Feature.TERMINAL, "flat")),
        Cue(CueFace.NEOGROTESQUE, "c"),
    ),
    TERMINALS_ROUND(
        Dimension.TERMINALS,
        "round",
        "Round",
        "round terminals",
        listOf(Anchor.Value(Feature.TERMINAL, "round")),
        Cue(CueFace.ROUNDED, "c"),
    ),
    TERMINALS_ANGLED(
        Dimension.TERMINALS,
        "angled",
        "Angled",
        "angled terminals",
        listOf(Anchor.Value(Feature.TERMINAL, "angled")),
        Cue(CueFace.LOW_WAIST, "c"),
    ),
    ROUND_CIRCLE(
        Dimension.ROUNDNESS,
        "circle",
        "Circles",
        "circular rounds",
        listOf(Anchor.Pooled(Feature.ROUNDNESS, listOf("sans-geometric", "serif-didone", "serif-garalde"), lo = 0.0)),
        Cue(CueFace.GEOMETRIC, "o"),
    ),
    ROUND_SOFT_SQUARE(
        Dimension.ROUNDNESS,
        "softSquare",
        "Squarish",
        "squarish rounds",
        listOf(Anchor.Pooled(Feature.ROUNDNESS, listOf("sans-superellipse"))),
        Cue(CueFace.SUPERELLIPSE, "o"),
    ),
    ROUND_SQUARE(
        Dimension.ROUNDNESS,
        "square",
        "Square",
        "square rounds",
        listOf(Anchor.Pooled(Feature.ROUNDNESS, listOf("display-techno"), lo = 0.75, hi = 1.0)),
        Cue(CueFace.TECHNO, "o"),
    ),
    APERTURE_CLOSED(
        Dimension.APERTURE,
        "closed",
        "Closed",
        "closed apertures",
        listOf(Anchor.Band(Feature.APERTURE, Population.CORPUS, 0.0, 1.0 / 3)),
        Cue(CueFace.CONDENSED, "ces"),
    ),
    APERTURE_MIDDLE(
        Dimension.APERTURE,
        "middle",
        "In between",
        "moderate apertures",
        listOf(Anchor.Band(Feature.APERTURE, Population.CORPUS, 1.0 / 3, 2.0 / 3)),
        Cue(CueFace.NEOGROTESQUE, "ces"),
    ),
    APERTURE_OPEN(
        Dimension.APERTURE,
        "open",
        "Open",
        "open apertures",
        listOf(Anchor.Band(Feature.APERTURE, Population.CORPUS, 2.0 / 3, 1.0)),
        Cue(CueFace.HUMANIST, "ces"),
    ),
    X_SMALL(
        Dimension.X_HEIGHT,
        "small",
        "Small",
        "a small x-height",
        listOf(Anchor.Band(Feature.X_HEIGHT, Population.CORPUS, 0.0, 1.0 / 3)),
        Cue(CueFace.GARALDE, "Hx"),
    ),
    X_MEDIUM(
        Dimension.X_HEIGHT,
        "medium",
        "Medium",
        "a medium x-height",
        listOf(Anchor.Band(Feature.X_HEIGHT, Population.CORPUS, 1.0 / 3, 2.0 / 3)),
        Cue(CueFace.ROUNDED, "Hx"),
    ),
    X_LARGE(
        Dimension.X_HEIGHT,
        "large",
        "Large",
        "a large x-height",
        listOf(Anchor.Band(Feature.X_HEIGHT, Population.CORPUS, 2.0 / 3, 1.0)),
        Cue(CueFace.GEOMETRIC, "Hx"),
    ),
    WIDTH_NARROW(
        Dimension.WIDTH,
        "narrow",
        "Narrow",
        "narrow",
        listOf(Anchor.Band(Feature.WIDTH, Population.ALL, 0.0, 0.15)),
        Cue(CueFace.CONDENSED, "HO"),
    ),
    WIDTH_NORMAL(
        Dimension.WIDTH,
        "normal",
        "Normal",
        "normal width",
        listOf(Anchor.Band(Feature.WIDTH, Population.ALL, 0.25, 0.75)),
        Cue(CueFace.GROTESQUE, "HO"),
    ),
    WIDTH_WIDE(
        Dimension.WIDTH,
        "wide",
        "Wide",
        "wide",
        listOf(Anchor.Band(Feature.WIDTH, Population.ALL, 0.85, 1.0)),
        Cue(CueFace.SUPERELLIPSE, "HO"),
    ),
    WEIGHT_LIGHT(
        Dimension.WEIGHT,
        "light",
        "Light",
        "light",
        listOf(Anchor.Band(Feature.WEIGHT, Population.CORPUS, 0.0, 0.15)),
        Cue(CueFace.WEIGHT_LIGHT, "Hn"),
    ),
    WEIGHT_REGULAR(
        Dimension.WEIGHT,
        "regular",
        "Regular",
        "regular weight",
        listOf(Anchor.Band(Feature.WEIGHT, Population.CORPUS, 0.25, 0.75)),
        Cue(CueFace.WEIGHT_REGULAR, "Hn"),
    ),
    WEIGHT_BOLD(
        Dimension.WEIGHT,
        "bold",
        "Heavy",
        "heavy",
        listOf(Anchor.Band(Feature.WEIGHT, Population.CORPUS, 0.85, 1.0)),
        Cue(CueFace.WEIGHT_BOLD, "Hn"),
    ),
    WAIST_LOW(
        Dimension.WAIST,
        "low",
        "Low",
        "low-waisted",
        listOf(Anchor.Band(Feature.WAIST, Population.ALL, 0.0, 0.08)),
        Cue(CueFace.LOW_WAIST, "HE"),
    ),
    WAIST_MIDDLE(
        Dimension.WAIST,
        "middle",
        "In the middle",
        "centred bars",
        listOf(Anchor.Band(Feature.WAIST, Population.ALL, 0.15, 0.85)),
        Cue(CueFace.GROTESQUE, "HE"),
    ),
    WAIST_HIGH(
        Dimension.WAIST,
        "high",
        "High",
        "high-waisted",
        listOf(Anchor.Band(Feature.WAIST, Population.ALL, 0.92, 1.0)),
        Cue(CueFace.DECO, "HE"),
    ),
    ;

    companion object {
        /** The level of [dimension] with this id, or null. */
        fun of(
            dimension: Dimension,
            id: String,
        ): Level? = entries.firstOrNull { it.dimension == dimension && it.id == id }
    }
}
