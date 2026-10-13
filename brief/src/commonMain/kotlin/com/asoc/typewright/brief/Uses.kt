// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/** A requirement a use places on the letters: what it is, why, where it comes from, and, when it can be measured, the [check]. */
data class Constraint(
    val title: String,
    val body: String,
    val source: Source,
    val check: UseCheck? = null,
)

/** One option of a use's own question. */
data class UseOption(
    val id: String,
    val label: String,
    val caption: String,
)

/** A question only one use asks ("Will it show in always-on mode?"). Its answer is stored as `"<use>.<id>"` in the ethos. */
data class UseQuestion(
    val id: String,
    val prompt: String,
    val why: String,
    val options: List<UseOption>,
)

/**
 * What the font is for. A use changes what to draw first (the plan), what the letters must meet
 * ([constraints]), which genres tend to fit ([suggestedGenres]), which questions matter most
 * ([dimensions]) and what to proof with ([proof]). Rules carry their source in words: a
 * platform's review rule is not a matter of taste. [forWhat] completes "a typeface for …".
 */
data class UseCase(
    val id: String,
    val name: String,
    val forWhat: String,
    val oneLine: String,
    val cue: Cue,
    val constraints: List<Constraint>,
    val suggestedGenres: List<String>,
    val dimensions: List<Dimension>,
    val questions: List<UseQuestion>,
    val proof: List<String>,
)

private val WEAR_QUALITY = "https://developer.android.com/develop/adaptive-apps/quality-guidelines/wear-app-quality"
private val ADA_703 = "https://www.ada.gov/law-and-regs/design-standards/2010-stds/"
private val GF_GUIDE = "https://googlefonts.github.io/gf-guide/"

/** Every use the Brief offers, in the picker's order. */
val USES: List<UseCase> =
    listOf(
        UseCase(
            id = "watch-face",
            name = "Watch face",
            forWhat = "watch faces",
            oneLine = "Figures read at a glance on a small, often round, OLED screen.",
            cue = Cue(CueFace.SUPERELLIPSE, "10:08", tabular = true),
            constraints =
                listOf(
                    Constraint(
                        "Tabular figures",
                        "Every figure from 0 to 9 on one width, so the time doesn't shift as it changes. Wear OS's watch " +
                            "face format can't switch OpenType features on, so the default figures have to be the tabular ones.",
                        Source(
                            SourceKind.GUIDELINE,
                            "Wear OS Material 3 typography; Watch Face Format text",
                            "https://developer.android.com/training/wearables/wff/text",
                        ),
                        UseCheck.TabularFigures,
                    ),
                    Constraint(
                        "Always-on budget",
                        "In ambient mode Wear OS rejects faces that light more than 15% of the screen on average; Garmin's " +
                            "AMOLED low-power mode allows 10%. Light, open figures leave room for everything else.",
                        Source(SourceKind.RULE, "Wear OS app quality WO-P7; Garmin Connect IQ", WEAR_QUALITY),
                        UseCheck.AmbientLitShare(),
                    ),
                    Constraint(
                        "Tell the figures apart at a glance",
                        "1 and 7, 3 and 8, 6, 9 and 0 must stay distinct when read in a fraction of a second; open apertures " +
                            "and distinct shapes help more than detail does.",
                        Source(SourceKind.CONVENTION, "Glance-legibility practice"),
                    ),
                    Constraint(
                        "Inside the round",
                        "On a round dial the largest square is 70.7% of the diameter, an inset of 14.6% on each side. " +
                            "Keep the time inside it, and set margins in percent.",
                        Source(
                            SourceKind.DERIVED,
                            "Geometry; Wear OS screen sizes guidance",
                            "https://developer.android.com/design/ui/wear/guides/foundations/screen-sizes",
                        ),
                    ),
                    Constraint(
                        "Thin weights need size",
                        "Apple advises against Ultralight, Thin and Light at small sizes on watchOS; a thin custom face needs " +
                            "to run larger than the system sizes.",
                        Source(
                            SourceKind.GUIDELINE,
                            "Apple Human Interface Guidelines, typography",
                            "https://developer.apple.com/design/human-interface-guidelines/typography",
                        ),
                    ),
                ),
            suggestedGenres =
                listOf(
                    "sans-superellipse",
                    "display-techno",
                    "sans-geometric",
                    "display-artdeco",
                    "sans-neogrotesque",
                    "sans-rounded",
                ),
            dimensions = listOf(Dimension.WEIGHT, Dimension.WIDTH, Dimension.APERTURE, Dimension.ROUNDNESS),
            questions =
                listOf(
                    UseQuestion(
                        "aod",
                        "Will it show in always-on (ambient) mode?",
                        "Ambient mode limits how much of the screen may light up; it usually wants a lighter, more open face.",
                        listOf(
                            UseOption("yes", "Yes", "Always-on, so lit pixels count"),
                            UseOption("no", "No", "Only when the wrist is raised"),
                        ),
                    ),
                    UseQuestion(
                        "date",
                        "Just the time, or the date too?",
                        "Day and month names need 22 capitals (MON to SUN, JAN to DEC, AM, PM); the time needs eleven glyphs.",
                        listOf(
                            UseOption("time", "Time only", "0 to 9 and the colon"),
                            UseOption("date", "Time and date", "Adds the capitals for day and month names"),
                        ),
                    ),
                    UseQuestion(
                        "dial",
                        "Round or square screen?",
                        "A round dial gives the figures less room at the edges than a square one of the same size.",
                        listOf(
                            UseOption("round", "Round", "Most Wear OS and Garmin watches"),
                            UseOption("square", "Square", "Apple Watch and some others"),
                        ),
                    ),
                ),
            proof = listOf("10:08", "23:59", "12:47", "MON 07 OCT"),
        ),
        UseCase(
            id = "app-ui",
            name = "App interface",
            forWhat = "app interfaces",
            oneLine = "Labels, buttons and text on a screen, at 11 to 17 points.",
            cue = Cue(CueFace.NEOGROTESQUE, "Settings"),
            constraints =
                listOf(
                    Constraint(
                        "Small sizes",
                        "iOS sets body text at 17 points with 11 as the minimum; Material's body text runs 14 to 16 sp and " +
                            "labels go down to 11.",
                        Source(
                            SourceKind.GUIDELINE,
                            "Apple HIG; Material 3 type scale",
                            "https://developer.apple.com/design/human-interface-guidelines/typography",
                        ),
                    ),
                    Constraint(
                        "A larger x-height reads bigger",
                        "Faces made for screens average about 0.52 of the em in x-height, against about 0.45 for book serifs.",
                        Source(SourceKind.EVIDENCE, "Legge and Bigelow, 2011"),
                    ),
                    Constraint(
                        "Spacing that survives being widened",
                        "Readers may set letter spacing to 0.12 em, word spacing to 0.16 em and line height to 1.5; text must " +
                            "not break.",
                        Source(
                            SourceKind.RULE,
                            "WCAG 2.2, success criterion 1.4.12",
                            "https://www.w3.org/WAI/WCAG22/Understanding/text-spacing.html",
                        ),
                    ),
                    Constraint(
                        "Hinting",
                        "Google Fonts hints static fonts with ttfautohint, or ships them unhinted with the gasp table set to 0x000A.",
                        Source(SourceKind.RULE, "Google Fonts guide, statics", GF_GUIDE + "statics.html"),
                    ),
                ),
            suggestedGenres = listOf("sans-neogrotesque", "sans-humanist", "sans-grotesque", "sans-geometric"),
            dimensions = listOf(Dimension.X_HEIGHT, Dimension.APERTURE, Dimension.WIDTH),
            questions = emptyList(),
            proof = listOf("Settings", "Save changes", "3 new messages", "Hamburgefonstiv"),
        ),
        UseCase(
            id = "body-text",
            name = "Books and long reading",
            forWhat = "long reading",
            oneLine = "Paragraphs read for an hour at a time.",
            cue = Cue(CueFace.GARALDE, "Reading"),
            constraints =
                listOf(
                    Constraint(
                        "A modest x-height",
                        "In 51 serif book faces the x-height ran 0.36 to 0.55 of the em, averaging 0.45.",
                        Source(SourceKind.EVIDENCE, "Legge and Bigelow, 2011"),
                    ),
                    Constraint(
                        "Serifs are a choice, not a requirement",
                        "Serifs on their own don't change reading speed; spacing and size matter more.",
                        Source(SourceKind.EVIDENCE, "Arditi and Cho, 2005"),
                    ),
                    Constraint(
                        "Line spacing in the font",
                        "For Google Fonts, ascender plus descender is 1.2 to 1.3 times the units per em, with a line gap of 0.",
                        Source(SourceKind.RULE, "Google Fonts guide, vertical metrics", GF_GUIDE + "metrics.html"),
                    ),
                    Constraint(
                        "The whole character set",
                        "Google Fonts accepts a Latin family only with at least GF Latin Core, about 324 glyphs.",
                        Source(SourceKind.RULE, "Google Fonts guide, requirements", GF_GUIDE + "requirements.html"),
                    ),
                ),
            suggestedGenres = listOf("serif-garalde", "serif-transitional", "serif-venetian", "sans-humanist", "serif-scotch"),
            dimensions = listOf(Dimension.CONTRAST, Dimension.SERIFS, Dimension.X_HEIGHT),
            questions = emptyList(),
            proof = listOf("Hamburgefonstiv", "The quick brown fox jumps over the lazy dog."),
        ),
        UseCase(
            id = "headline",
            name = "Headlines and posters",
            forWhat = "headlines",
            oneLine = "Large words that have to be seen first.",
            cue = Cue(CueFace.FATFACE, "Extra!"),
            constraints =
                listOf(
                    Constraint(
                        "Large sizes allow more",
                        "Hairlines, tight spacing and strong contrast that would fail small are at home large; that is what " +
                            "optical sizes are for.",
                        Source(
                            SourceKind.RULE,
                            "OpenType opsz axis",
                            "https://learn.microsoft.com/en-us/typography/opentype/spec/dvaraxistag_opsz",
                        ),
                    ),
                    Constraint(
                        "Check it reversed",
                        "Light type on a dark ground looks bolder; check headlines both ways.",
                        Source(
                            SourceKind.GUIDELINE,
                            "Adobe, keeping type consistent",
                            "https://adobe.design/ideas/keeping-type-consistent-in-changing-conditions",
                        ),
                    ),
                ),
            suggestedGenres = listOf("serif-didone", "serif-fatface", "display-artdeco", "sans-grotesque", "display-inline", "slab"),
            dimensions = listOf(Dimension.CONTRAST, Dimension.WIDTH, Dimension.WEIGHT),
            questions = emptyList(),
            proof = listOf("EXTRA", "Hamburg", "Quartz"),
        ),
        UseCase(
            id = "signage",
            name = "Signs and wayfinding",
            forWhat = "signs",
            oneLine = "Read from a distance, often in a hurry, by everyone.",
            cue = Cue(CueFace.HUMANIST, "Exit 4"),
            constraints =
                listOf(
                    Constraint(
                        "Stroke 10 to 30% of the height of I",
                        "US accessibility standards set the stroke of visual characters between 10 and 30% of the height of an uppercase I.",
                        Source(SourceKind.RULE, "2010 ADA Standards 703.5.7", ADA_703),
                        UseCheck.StrokeShareOfHeight(0.10, 0.30),
                    ),
                    Constraint(
                        "O 55 to 110% as wide as I is tall",
                        "The same standard sets the width of O between 55 and 110% of the height of I.",
                        Source(SourceKind.RULE, "2010 ADA Standards 703.5.4", ADA_703),
                        UseCheck.OWidthShareOfIHeight(0.55, 1.10),
                    ),
                    Constraint(
                        "Conventional forms only",
                        "No italic, oblique, script or highly decorative letters on accessible signs; letter spacing 10 to 35% " +
                            "of the character height.",
                        Source(SourceKind.RULE, "2010 ADA Standards 703.5.1, 703.5.8", ADA_703),
                    ),
                    Constraint(
                        "Open forms help at a distance",
                        "On US highway guide signs, a typeface with open forms and a larger x-height was read from farther " +
                            "away at night, for light letters on a dark ground.",
                        Source(
                            SourceKind.EVIDENCE,
                            "FHWA, Clearview interim approval report",
                            "https://mutcd.fhwa.dot.gov/resources/interim_approval/ia5rptcongress/ch3.htm",
                        ),
                    ),
                ),
            suggestedGenres = listOf("sans-humanist", "sans-grotesque", "sans-neogrotesque"),
            dimensions = listOf(Dimension.WEIGHT, Dimension.APERTURE, Dimension.X_HEIGHT),
            questions = emptyList(),
            proof = listOf("Exit 4", "Platform 11", "Gate B12"),
        ),
        UseCase(
            id = "wordmark",
            name = "Logo or wordmark",
            forWhat = "a wordmark",
            oneLine = "One name, drawn to be recognised.",
            cue = Cue(CueFace.DECO_AIRY, "Name"),
            constraints =
                listOf(
                    Constraint(
                        "Test it small and reversed",
                        "A wordmark lives at favicon size and in white on black; check both before you finish.",
                        Source(SourceKind.CONVENTION, "Brand identity practice"),
                    ),
                    Constraint(
                        "Space the name, not the alphabet",
                        "Only the pairs in the name matter; space them by eye as a word, then lock them.",
                        Source(SourceKind.CONVENTION, "Brand identity practice"),
                    ),
                ),
            suggestedGenres = emptyList(),
            dimensions = listOf(Dimension.WAIST, Dimension.WIDTH, Dimension.CONTRAST),
            questions = emptyList(),
            proof = emptyList(),
        ),
        UseCase(
            id = "code",
            name = "Code",
            forWhat = "code",
            oneLine = "A monospace for editors and terminals.",
            cue = Cue(CueFace.MONO, "x = 0;"),
            constraints =
                listOf(
                    Constraint(
                        "One width for everything",
                        "Every glyph on the same advance, and the post table marks the font as fixed pitch.",
                        Source(
                            SourceKind.RULE,
                            "OpenType post table, isFixedPitch",
                            "https://learn.microsoft.com/en-us/typography/opentype/spec/post",
                        ),
                        UseCheck.FixedPitch,
                    ),
                    Constraint(
                        "No look-alikes",
                        "0 and O, 1, l and I, and the comma and period must be unmistakable at 12 to 14 points: a slashed or " +
                            "dotted zero, serifs or tails on 1, l and I.",
                        Source(SourceKind.CONVENTION, "JetBrains Mono, Input design notes"),
                    ),
                ),
            suggestedGenres = listOf("monospace"),
            dimensions = listOf(Dimension.SERIFS, Dimension.A_FORM, Dimension.G_FORM),
            questions = emptyList(),
            proof = listOf("0O 1lI {}", "if (x != 0) return;"),
        ),
        UseCase(
            id = "e-ink",
            name = "E-ink and e-readers",
            forWhat = "e-readers",
            oneLine = "Long reading on a grey, low-contrast screen.",
            cue = Cue(CueFace.TRANSITIONAL, "Chapter"),
            constraints =
                listOf(
                    Constraint(
                        "Sturdy thins",
                        "E Ink Carta screens show 16 grey levels at 212 to 300 ppi, so hairlines and fine serifs break up; " +
                            "keep the thins sturdy and the counters open.",
                        Source(SourceKind.DERIVED, "E Ink Carta specification"),
                    ),
                ),
            suggestedGenres = listOf("serif-transitional", "sans-humanist", "slab"),
            dimensions = listOf(Dimension.CONTRAST, Dimension.APERTURE, Dimension.X_HEIGHT),
            questions = emptyList(),
            proof = listOf("Chapter One", "Hamburgefonstiv"),
        ),
        UseCase(
            id = "handwriting",
            name = "My handwriting",
            forWhat = "handwriting",
            oneLine = "A font of your own hand, for letters, notes and cards.",
            cue = Cue(CueFace.SCRIPT, "Dear you"),
            constraints =
                listOf(
                    Constraint(
                        "Even spacing beats perfect letters",
                        "A handwriting font reads as handwriting when the letters vary a little and the spacing doesn't.",
                        Source(SourceKind.CONVENTION, "Handwriting font practice"),
                    ),
                ),
            suggestedGenres = listOf("script-handwritten", "script-informal"),
            dimensions = listOf(Dimension.WIDTH, Dimension.CONTRAST),
            questions = emptyList(),
            proof = listOf("Dear you,", "Hamburgefonstiv"),
        ),
    )

/** The use with this id, or null. */
fun useById(id: String): UseCase? = USES.firstOrNull { it.id == id }
