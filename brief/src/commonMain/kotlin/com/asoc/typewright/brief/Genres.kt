// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/** The shelves a genre sits on in the style picker. */
enum class GenreGroup(
    val title: String,
) {
    SANS("Sans serif"),
    SERIF("Serif"),
    DISPLAY("Display"),
    SCRIPT("Script"),
    MONO("Monospace"),
}

/**
 * A craft tip: what to do and why, with its [source]. Tips are short on purpose; the Learn
 * section holds the long form.
 */
data class Tip(
    val title: String,
    val body: String,
    val source: Source,
)

/**
 * A genre a brief can aim at. [key] is the style atlas's class key, so every genre has measured
 * ranges behind it; the ten with [corpus] keys also have node-economy boxes in Check. [aka] are
 * the other names people use ("grotesk", "deco"), for search. [askAbout] is what the genre leaves
 * open when there is no atlas to measure that (the web preview); with the atlas the Brief asks
 * about whatever the genre's own faces disagree on. [drawFirst] are the letters where the genre's
 * voice lives, drawn straight after the control characters.
 */
data class Genre(
    val key: String,
    val name: String,
    val aka: List<String>,
    val group: GenreGroup,
    val cue: Cue?,
    val oneLine: String,
    val signatures: List<String>,
    val lineagesScene: String?,
    val drawFirst: String,
    val askAbout: List<Dimension>,
    val tips: List<Tip>,
) {
    /** True for the ten node-economy classes, which have Economy boxes in Check. */
    val corpus: Boolean get() = key in CORPUS_GENRE_KEYS
}

/** The ten node-economy classes (TYPEWRIGHT_BUILD_BRIEF.md 8.1). */
val CORPUS_GENRE_KEYS: Set<String> =
    setOf(
        "sans-geometric",
        "sans-grotesque",
        "sans-neogrotesque",
        "sans-humanist",
        "serif-garalde",
        "serif-transitional",
        "serif-didone",
        "slab",
        "display-artdeco",
        "blackletter",
    )

private fun convention(citation: String = "Type design practice") = Source(SourceKind.CONVENTION, citation)

private val OVERSHOOT_TIP =
    Tip(
        "Overshoot the rounds",
        "Let o, c, e and s pass the baseline and the x-height slightly, about 1 to 3% of the height, or they look " +
            "smaller than n and x. A flat-topped round has no overshoot to give.",
        convention("Workbook task 3; docs/RESEARCH_font_quality.md"),
    )

private val OPTICAL_WEIGHT_TIP =
    Tip(
        "Weigh strokes by eye, not by ruler",
        "A horizontal looks heavier than a vertical of the same thickness. Make bars and the tops and bottoms of " +
            "rounds a little thinner than the stems, and thin the strokes where curves join stems.",
        convention(),
    )

/** Every genre the Brief offers, in the picker's order. */
val GENRES: List<Genre> =
    listOf(
        Genre(
            key = "sans-geometric",
            name = "Geometric sans",
            aka = listOf("geometric", "futura", "bauhaus"),
            group = GenreGroup.SANS,
            cue = Cue(CueFace.GEOMETRIC, "Rag"),
            oneLine = "Letters built from the circle, the square and the straight line (Futura, 1927).",
            signatures =
                listOf(
                    "circular rounds",
                    "an even stroke",
                    "a one-storey a in its purest form",
                    "pointed apexes on A, M, N, V, W",
                ),
            lineagesScene = "lineages.geometric",
            drawFirst = "a A M",
            askAbout = listOf(Dimension.A_FORM, Dimension.X_HEIGHT, Dimension.TERMINALS),
            tips =
                listOf(
                    Tip(
                        "Start from a circle, then correct it",
                        "A mathematically round o looks narrow and pinched where it meets nothing; widen it a touch and " +
                            "keep the curve full at the shoulders.",
                        convention(),
                    ),
                    OPTICAL_WEIGHT_TIP,
                    OVERSHOOT_TIP,
                    Tip(
                        "Decide the a early",
                        "The one-storey a is the signature, but it needs room inside and reads slower in long text. " +
                            "Many geometric families ship both and switch with a stylistic set.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "sans-grotesque",
            name = "Grotesque",
            aka = listOf("grotesk", "grotesque", "gothic", "akzidenz", "franklin"),
            group = GenreGroup.SANS,
            cue = Cue(CueFace.GROTESQUE, "Rag"),
            oneLine = "The first sans serifs (1830s on): a little contrast left, closed apertures, character in the details.",
            signatures = listOf("a trace of contrast", "fairly closed apertures", "a two-storey a", "flat-cut terminals"),
            lineagesScene = "lineages.grotesque",
            drawFirst = "a g R G",
            askAbout = listOf(Dimension.G_FORM, Dimension.APERTURE, Dimension.WIDTH),
            tips =
                listOf(
                    Tip(
                        "Keep a little contrast",
                        "Thin the joins where bowls meet stems. That trace of the pen is what keeps a grotesque from " +
                            "turning into a neo-grotesque.",
                        convention(),
                    ),
                    Tip(
                        "Let the curves square up slightly",
                        "Grotesque rounds sit between the circle and the square; a slightly superelliptic o keeps " +
                            "the colour dense.",
                        convention(),
                    ),
                    Tip(
                        "Allow a few quirks",
                        "A spur on G, a straight or curved leg on R, a lively tail on y: the grotesque was never neutral.",
                        convention(),
                    ),
                    OVERSHOOT_TIP,
                ),
        ),
        Genre(
            key = "sans-neogrotesque",
            name = "Neo-grotesque",
            aka = listOf("helvetica", "swiss", "neutral"),
            group = GenreGroup.SANS,
            cue = Cue(CueFace.NEOGROTESQUE, "Rag"),
            oneLine = "The grotesque tidied into neutrality (Helvetica, 1957): even stroke, closed apertures, horizontal cuts.",
            signatures = listOf("an even stroke", "horizontal terminals", "closed apertures", "a large x-height"),
            lineagesScene = "lineages.neogrotesque",
            drawFirst = "a e s",
            askAbout = listOf(Dimension.X_HEIGHT, Dimension.APERTURE),
            tips =
                listOf(
                    Tip(
                        "Cut every terminal the same way",
                        "Horizontal cuts on c, e, s, a, r and t give the even texture the style is known for; one angled " +
                            "cut stands out.",
                        convention(),
                    ),
                    Tip(
                        "Keep the curve tension the same everywhere",
                        "Neutral means consistent: the curves of s, e, c and o should share one tension.",
                        convention(),
                    ),
                    OPTICAL_WEIGHT_TIP,
                ),
        ),
        Genre(
            key = "sans-humanist",
            name = "Humanist sans",
            aka = listOf("humanist", "gill", "frutiger", "open sans"),
            group = GenreGroup.SANS,
            cue = Cue(CueFace.HUMANIST, "Rag"),
            oneLine = "A sans that remembers the pen (Gill Sans, 1928): open apertures, calligraphic proportions.",
            signatures = listOf("open apertures", "a little contrast and stress", "varied widths", "a two-storey a"),
            lineagesScene = "lineages.humanist",
            drawFirst = "a e g",
            askAbout = listOf(Dimension.G_FORM, Dimension.TERMINALS, Dimension.CONTRAST),
            tips =
                listOf(
                    Tip(
                        "Keep the throats open",
                        "The gap in c, e, s and a is the humanist's legibility; it is what holds up at small sizes and on " +
                            "signs.",
                        convention("Type design practice; Clearview studies (FHWA)"),
                    ),
                    Tip(
                        "Let the widths vary",
                        "Humanist letters keep the proportions of the written hand: a narrow e and s, a wide m and O.",
                        convention(),
                    ),
                    OVERSHOOT_TIP,
                ),
        ),
        Genre(
            key = "serif-garalde",
            name = "Garalde",
            aka = listOf("old style", "oldstyle", "garamond", "renaissance"),
            group = GenreGroup.SERIF,
            cue = Cue(CueFace.GARALDE, "Rag"),
            oneLine = "The Renaissance roman (c. 1530): a broad pen held at an angle, bracketed serifs, a small x-height.",
            signatures = listOf("pen-angled stress", "bracketed serifs", "moderate contrast", "a small x-height"),
            lineagesScene = "lineages.garalde",
            drawFirst = "a e g",
            askAbout = listOf(Dimension.X_HEIGHT, Dimension.CONTRAST),
            tips =
                listOf(
                    Tip(
                        "Draw the o as a pen stroke",
                        "With the pen at about 30 degrees the o is thinnest near 11 and 5 o'clock and thickest near 2 and 8. " +
                            "Rotate the counter inside the outer shape to get it.",
                        convention(),
                    ),
                    Tip(
                        "Bracket the serifs",
                        "Let the serif grow out of the stem in a curve. Lowercase ascenders get a sloped head serif, " +
                            "the trace of the pen's entry stroke.",
                        convention(),
                    ),
                    Tip(
                        "Keep the x-height modest",
                        "Garaldes in the atlas measure about two-thirds of the cap height; the long ascenders are part " +
                            "of the elegance.",
                        Source(SourceKind.MEASURED, "Style atlas, serif-garalde"),
                    ),
                ),
        ),
        Genre(
            key = "serif-transitional",
            name = "Transitional",
            aka = listOf("baskerville", "times", "georgian"),
            group = GenreGroup.SERIF,
            cue = Cue(CueFace.TRANSITIONAL, "Rag"),
            oneLine = "Between the pen and the engraver (Baskerville, 1757): stress standing up, sharper contrast, still bracketed.",
            signatures = listOf("near-vertical stress", "crisper contrast", "bracketed serifs", "round terminals"),
            lineagesScene = "lineages.transitional",
            drawFirst = "a g R",
            askAbout = listOf(Dimension.CONTRAST, Dimension.TERMINALS),
            tips =
                listOf(
                    Tip(
                        "Stand the stress up",
                        "Rotate the o's counter almost upright; the pen's tilt is fading but not gone.",
                        convention(),
                    ),
                    Tip(
                        "Sharpen the serifs",
                        "Flatter, crisper serifs than a garalde, still joined to the stem with a small bracket.",
                        convention(),
                    ),
                    OVERSHOOT_TIP,
                ),
        ),
        Genre(
            key = "serif-didone",
            name = "Didone",
            aka = listOf("modern", "bodoni", "didot", "fashion"),
            group = GenreGroup.SERIF,
            cue = Cue(CueFace.DIDONE, "Rag"),
            oneLine = "Vertical stress, hairline serifs, maximum contrast (Bodoni, Didot, c. 1790): drawn by the engraver.",
            signatures = listOf("hairline-contrast", "vertical stress", "unbracketed hairline serifs", "ball terminals"),
            lineagesScene = "lineages.didone",
            drawFirst = "a c f r y",
            askAbout = listOf(Dimension.WIDTH, Dimension.CONTRAST),
            tips =
                listOf(
                    Tip(
                        "Make the thins truly thin",
                        "Didones in the atlas run about 4 to 7 times thicker in the stems than in the hairlines. Decide " +
                            "the hairline once and use it everywhere.",
                        Source(SourceKind.MEASURED, "Style atlas, serif-didone"),
                    ),
                    Tip(
                        "Ball terminals on a, c, f, g, j, r, y",
                        "A round drop at the end of the curve; size them together, they read as a family.",
                        convention(),
                    ),
                    Tip(
                        "Plan for size",
                        "Hairlines vanish small. Didones are display faces unless you draw a sturdier text cut.",
                        convention("OpenType optical size (opsz) practice"),
                    ),
                ),
        ),
        Genre(
            key = "slab",
            name = "Slab serif",
            aka = listOf("egyptian", "clarendon", "typewriter", "slab"),
            group = GenreGroup.SERIF,
            cue = Cue(CueFace.SLAB, "Rag"),
            oneLine = "Serifs as heavy as the stems, made to be read across a street (1815).",
            signatures = listOf("square, unbracketed serifs", "low contrast", "sturdy, even colour"),
            lineagesScene = "lineages.slab",
            drawFirst = "a n r",
            askAbout = listOf(Dimension.CONTRAST, Dimension.A_FORM),
            tips =
                listOf(
                    Tip(
                        "Serifs as heavy as the stems",
                        "Draw the serifs close to stem weight and meet the stem at a right angle, or with a very small " +
                            "bracket for a Clarendon.",
                        convention(),
                    ),
                    Tip(
                        "Watch the joins",
                        "Where serif meets stem the ink piles up; cut tiny notches or thin the stem near the join so it " +
                            "doesn't fill in.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "display-artdeco",
            name = "Art deco",
            aka = listOf("deco", "art deco", "1920s", "jazz age"),
            group = GenreGroup.DISPLAY,
            cue = Cue(CueFace.DECO, "Rag"),
            oneLine = "Geometric bones with theatrical proportions (1925): extreme widths, moved crossbars, one bold rule throughout.",
            signatures =
                listOf(
                    "crossbars well above or below the middle",
                    "extreme widths",
                    "a strict construction",
                    "monoline or dramatic contrast",
                ),
            lineagesScene = "lineages.artdeco",
            drawFirst = "A E M R S 0 8",
            askAbout = listOf(Dimension.CONTRAST, Dimension.WAIST, Dimension.WIDTH, Dimension.ROUNDNESS),
            tips =
                listOf(
                    Tip(
                        "Choose one construction and apply it everywhere",
                        "Circles and verticals, rounded rectangles, or stacked geometric parts: deco is consistency in one " +
                            "strong idea. Write the rule down before you draw.",
                        convention(),
                    ),
                    Tip(
                        "Move the waist, then repeat it",
                        "Raise or drop the bars of A, E, F and H and the bowls of B, P and R. The atlas measures deco's " +
                            "crossbar spread at several times a text face's.",
                        Source(SourceKind.MEASURED, "Style atlas, display-artdeco crossbar"),
                    ),
                    Tip(
                        "Play width against height",
                        "Very narrow or very wide capitals; let the rounds stay wide when the straights go narrow, or the " +
                            "reverse, but decide.",
                        convention(),
                    ),
                    Tip(
                        "Put contrast where the construction says",
                        "If there is contrast, give it to the construction (heavy verticals, hairline horizontals), not to " +
                            "a pen angle.",
                        convention(),
                    ),
                    Tip(
                        "Find the voice in the capitals and figures",
                        "Deco lives in A, E, M, R, S and 0 to 9; draw them early.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "blackletter",
            name = "Blackletter",
            aka = listOf("gothic", "fraktur", "textura", "old english"),
            group = GenreGroup.DISPLAY,
            cue = Cue(CueFace.BLACKLETTER, "Rag"),
            oneLine = "The broad pen at 45 degrees, compressed into a dense, vertical rhythm (c. 1450).",
            signatures = listOf("broken, angular curves", "a 45-degree pen", "narrow letters", "tight spacing"),
            lineagesScene = "lineages.blackletter",
            drawFirst = "n m i u o",
            askAbout = listOf(Dimension.WIDTH, Dimension.CONTRAST),
            tips =
                listOf(
                    Tip(
                        "Start with the minims",
                        "n, m, i and u are built from the same upright stroke with diamond feet and heads; get that stroke " +
                            "right and half the alphabet follows.",
                        convention(),
                    ),
                    Tip(
                        "Break the curves",
                        "A blackletter o is two broken strokes meeting at angles, not a ring.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "sans-rounded",
            name = "Rounded sans",
            aka = listOf("rounded", "soft", "friendly"),
            group = GenreGroup.SANS,
            cue = Cue(CueFace.ROUNDED, "Rag"),
            oneLine = "A sans whose stroke ends and corners are rounded off: soft and friendly.",
            signatures = listOf("round terminals", "rounded corners", "an even stroke"),
            lineagesScene = null,
            drawFirst = "a e s",
            askAbout = listOf(Dimension.A_FORM, Dimension.X_HEIGHT),
            tips =
                listOf(
                    Tip(
                        "Round with one radius",
                        "Use one corner radius, scaled with the stroke, for every terminal and corner; mixed radii look " +
                            "careless.",
                        convention(),
                    ),
                    Tip(
                        "Compensate the ends",
                        "A rounded terminal looks shorter than a square one; extend it slightly past where a square end would stop.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "sans-superellipse",
            name = "Squarish sans",
            aka = listOf("superellipse", "eurostile", "squarish", "technical"),
            group = GenreGroup.SANS,
            cue = Cue(CueFace.SUPERELLIPSE, "Rag"),
            oneLine = "Rounds that are neither circles nor squares (Eurostile, 1962): technical and cool.",
            signatures = listOf("squarish rounds", "flat-cut terminals", "often wide"),
            lineagesScene = null,
            drawFirst = "o O 0 e s",
            askAbout = listOf(Dimension.WIDTH, Dimension.ROUNDNESS),
            tips =
                listOf(
                    Tip(
                        "Set the corner once",
                        "Choose the superellipse of the O and derive every round from it: o, e, c, s and the figures.",
                        convention(),
                    ),
                    Tip(
                        "Keep the straights straight",
                        "Flat sides on the rounds need the same tension at every corner, or the letter looks dented.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "sans-glyphic",
            name = "Glyphic",
            aka = listOf("flared", "optima", "incised", "chiselled"),
            group = GenreGroup.SANS,
            cue = null,
            oneLine = "Letters with the feel of cut stone: stems that flare toward their ends instead of serifs (Optima).",
            signatures = listOf("flared stems", "wedge-like terminals", "classical proportions"),
            lineagesScene = null,
            drawFirst = "n H O",
            askAbout = listOf(Dimension.CONTRAST, Dimension.WIDTH),
            tips =
                listOf(
                    Tip(
                        "Flare from the middle",
                        "Thin the stem a little at its middle and let it widen toward both ends, so the flare reads without a serif.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "serif-venetian",
            name = "Venetian",
            aka = listOf("humanist serif", "jenson", "venetian"),
            group = GenreGroup.SERIF,
            cue = null,
            oneLine = "The first romans (Jenson, 1470): strongly tilted stress, a sloped bar on e, low contrast.",
            signatures = listOf("strongly tilted stress", "a sloped crossbar on e", "low contrast", "heavy serifs"),
            lineagesScene = "lineages.garalde",
            drawFirst = "e a n",
            askAbout = listOf(Dimension.CONTRAST, Dimension.X_HEIGHT),
            tips =
                listOf(
                    Tip(
                        "Slope the bar of e",
                        "The Venetian e's crossbar rises to the right, the pen's own movement.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "serif-scotch",
            name = "Scotch roman",
            aka = listOf("scotch", "century", "victorian text"),
            group = GenreGroup.SERIF,
            cue = null,
            oneLine = "A sturdy 19th-century text roman: high contrast, ball terminals, built to print well.",
            signatures = listOf("high contrast", "ball terminals", "bracketed serifs", "robust colour"),
            lineagesScene = "lineages.didone",
            drawFirst = "a c g",
            askAbout = listOf(Dimension.CONTRAST, Dimension.SERIFS),
            tips = emptyList(),
        ),
        Genre(
            key = "serif-fatface",
            name = "Fat face",
            aka = listOf("fat face", "poster bodoni", "abril"),
            group = GenreGroup.DISPLAY,
            cue = Cue(CueFace.FATFACE, "Rag"),
            oneLine = "The didone pushed to its limit for posters (c. 1810): enormous thick strokes, hairline thins.",
            signatures = listOf("hairline-contrast", "enormous stems", "ball terminals"),
            lineagesScene = "lineages.didone",
            drawFirst = "a c R",
            askAbout = listOf(Dimension.WIDTH),
            tips =
                listOf(
                    Tip(
                        "Keep the counters open",
                        "With stems this heavy the counters close first; size them before you size anything else.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "script-formal",
            name = "Formal script",
            aka = listOf("copperplate", "calligraphy", "wedding"),
            group = GenreGroup.SCRIPT,
            cue = Cue(CueFace.SCRIPT, "Rag"),
            oneLine = "Pointed-pen copperplate: connected letters, steep slant, swelling strokes.",
            signatures = listOf("joined letters", "a steep slant", "pressure contrast", "a small x-height"),
            lineagesScene = null,
            drawFirst = "a e n o r s",
            askAbout = listOf(Dimension.X_HEIGHT, Dimension.WIDTH),
            tips =
                listOf(
                    Tip(
                        "Design the joins first",
                        "Every lowercase letter must enter and leave at the same height and angle, or the word breaks " +
                            "apart. Fix the connecting stroke before the letters.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "script-informal",
            name = "Casual script",
            aka = listOf("brush", "marker", "casual", "signpainter"),
            group = GenreGroup.SCRIPT,
            cue = null,
            oneLine = "Brush or marker lettering: lively, upright or gently slanted, joined or not.",
            signatures = listOf("a brush or marker stroke", "round terminals", "lively rhythm"),
            lineagesScene = null,
            drawFirst = "a e n o",
            askAbout = listOf(Dimension.CONTRAST, Dimension.WIDTH),
            tips = emptyList(),
        ),
        Genre(
            key = "script-handwritten",
            name = "Handwriting",
            aka = listOf("handwriting", "my hand", "personal"),
            group = GenreGroup.SCRIPT,
            cue = null,
            oneLine = "Everyday handwriting made into type.",
            signatures = listOf("the writer's own rhythm", "round terminals", "natural variation"),
            lineagesScene = null,
            drawFirst = "a e n o",
            askAbout = listOf(Dimension.WIDTH),
            tips =
                listOf(
                    Tip(
                        "Write each letter several times",
                        "Fill the template more than once and keep the best of each; the font should look like you on a good day.",
                        convention(),
                    ),
                ),
        ),
        Genre(
            key = "display-techno",
            name = "Techno",
            aka = listOf("techno", "sci-fi", "futuristic", "orbitron"),
            group = GenreGroup.DISPLAY,
            cue = Cue(CueFace.TECHNO, "Rag"),
            oneLine = "Square rounds, cut corners, machine forms.",
            signatures = listOf("square rounds", "flat-cut terminals", "wide proportions"),
            lineagesScene = null,
            drawFirst = "o O 0 a e",
            askAbout = listOf(Dimension.WIDTH, Dimension.ROUNDNESS),
            tips = emptyList(),
        ),
        Genre(
            key = "display-stencil",
            name = "Stencil",
            aka = listOf("stencil", "military", "crate"),
            group = GenreGroup.DISPLAY,
            cue = null,
            oneLine = "Letters with bridges across the strokes, as if cut from a sheet.",
            signatures = listOf("bridges that break the strokes", "heavy weight"),
            lineagesScene = null,
            drawFirst = "O A R",
            askAbout = listOf(Dimension.WEIGHT),
            tips = emptyList(),
        ),
        Genre(
            key = "display-inline",
            name = "Inline",
            aka = listOf("inline", "outline", "decorative"),
            group = GenreGroup.DISPLAY,
            cue = null,
            oneLine = "A line or channel runs inside the strokes.",
            signatures = listOf("an inline inside every stroke"),
            lineagesScene = null,
            drawFirst = "H O A",
            askAbout = listOf(Dimension.WEIGHT, Dimension.WIDTH),
            tips = emptyList(),
        ),
        Genre(
            key = "monospace",
            name = "Monospace",
            aka = listOf("mono", "code", "typewriter"),
            group = GenreGroup.MONO,
            cue = Cue(CueFace.MONO, "Rag"),
            oneLine = "Every character on the same width, from the typewriter to the code editor.",
            signatures = listOf("one advance width for every character", "distinct 0 O, 1 l I"),
            lineagesScene = null,
            drawFirst = "m i 0 O 1 l I",
            askAbout = listOf(Dimension.SERIFS, Dimension.A_FORM),
            tips =
                listOf(
                    Tip(
                        "Squeeze m and w, stretch i and l",
                        "Every letter gets the same box. Give the narrow letters serifs or tails to fill it, and thin the " +
                            "strokes of m and w so they don't go black.",
                        convention("JetBrains Mono and Input design notes"),
                    ),
                ),
        ),
    )

/** The genre with this atlas key, or null. */
fun genreByKey(key: String?): Genre? = key?.let { k -> GENRES.firstOrNull { it.key == k } }

/** Genres whose name or other names contain [query], ignoring case. */
fun searchGenres(query: String): List<Genre> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return GENRES
    return GENRES.filter { g -> g.name.lowercase().contains(q) || g.aka.any { it.contains(q) } }
}
