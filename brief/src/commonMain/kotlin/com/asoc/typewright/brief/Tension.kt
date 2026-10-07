// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * Two things in a brief that pull against each other. Not an error: a tension can be the point
 * of a design. [signature] names the dimension a person can mark as a deliberate departure to
 * settle it; otherwise they change one of the two answers.
 */
data class Tension(
    val id: String,
    val title: String,
    val body: String,
    val source: Source,
    val dimensions: List<Dimension>,
    val signature: Dimension? = null,
)

private val SMALL_SIZE_USES = setOf("app-ui", "watch-face", "e-ink", "signage")

/** Every tension in [model], most serious first: rules before measurements before conventions. */
internal fun findTensions(model: BriefModel): List<Tension> {
    val out = mutableListOf<Tension>()
    val genre = model.genre
    val atlas = model.atlas
    val useIds = model.uses.map { it.id }.toSet()

    fun answered(level: Level) = model.answer(level.dimension) == level

    // A person's answer against their own genre: fewer than a quarter of its measured faces would give it.
    if (genre != null && atlas != null) {
        val faces =
            atlas.pack.classes[genre.key]
                ?.faces
                .orEmpty()
        for ((dimId, levelId) in model.ethos.levels) {
            val dimension = Dimension.byId(dimId) ?: continue
            if (dimension in model.signatures) continue
            val level = Level.of(dimension, levelId) ?: continue
            val counts = levelCounts(dimension, faces, atlas)
            val total = counts.values.sum()
            val inside = counts[level] ?: 0
            if (total < MIN_GENRE_FACES || inside.toDouble() / total >= GENRE_VALUE_SHARE) continue
            val share = if (inside == 0) "None of the $total" else "Only $inside of the $total"
            out +=
                Tension(
                    id = "departs.${dimension.id}",
                    title = "${level.label}: ${dimension.title.lowercase()} unusual for ${genre.name.lowercase()}",
                    body =
                        "$share ${genre.name.lowercase()} faces measured do this. Keep it as a deliberate signature, the " +
                            "thing that makes yours different, or move it back toward the genre.",
                    source = Source(SourceKind.MEASURED, "Style atlas, ${genre.key}"),
                    dimensions = listOf(dimension),
                    signature = dimension,
                )
        }
    }

    // Feelings that pull a feature in opposite directions.
    for (feature in Feature.entries) {
        val pulls = model.feelings.mapNotNull { p -> p.tendencies.firstOrNull { it.feature == feature }?.let { p.feeling to it.effect } }
        val up = pulls.filter { it.second > 0 }
        val down = pulls.filter { it.second < 0 }
        if (up.isNotEmpty() && down.isNotEmpty()) {
            val dimension = Dimension.entries.firstOrNull { feature in it.features } ?: continue
            if (model.answer(dimension) != null) continue
            val a = up.first().first.word
            val b = down.first().first.word
            out +=
                Tension(
                    id = "feelings.${feature.key}",
                    title = "$a and $b pull the ${feature.label.lowercase()} apart",
                    body =
                        "Faces tagged $a on Google Fonts are ${direction(feature, true)} than most; $b faces are " +
                            "${direction(feature, false)}. Answer the ${dimension.title.lowercase()} question to choose.",
                    source = Source(SourceKind.MEASURED, "Style atlas, feelings"),
                    dimensions = listOf(dimension),
                )
        }
    }

    // References that disagree.
    for ((feature, values) in model.referenceSpread) {
        val dimension = Dimension.entries.firstOrNull { feature in it.features } ?: continue
        if (model.answer(dimension) != null) continue
        out +=
            Tension(
                id = "references.${feature.key}",
                title = "Your references disagree on ${feature.label.lowercase()}",
                body =
                    values.joinToString("; ") { "${it.first}: ${it.second}" } +
                        ". Answer the ${dimension.title.lowercase()} question to decide.",
                source = Source(SourceKind.MEASURED, "Style atlas"),
                dimensions = listOf(dimension),
            )
    }

    // Uses against answers.
    val contrastLo = model.targets[Feature.CONTRAST]?.range?.start
    val hairlineGenre = genre?.key in setOf("serif-didone", "serif-fatface")
    val smallUses = model.uses.filter { it.id in SMALL_SIZE_USES }
    if (smallUses.isNotEmpty() && (answered(Level.CONTRAST_EXTREME) || hairlineGenre || (contrastLo != null && contrastLo >= 3.0))) {
        out +=
            Tension(
                id = "use.hairlines",
                title = "Hairlines at small sizes",
                body =
                    "Extreme contrast is at home large. For ${smallUses.joinToString(" and ") { it.name.lowercase() }}, " +
                        "hairlines thin out or vanish: draw sturdier thins, or plan a separate text cut.",
                source = Source(SourceKind.CONVENTION, "Optical size practice (OpenType opsz)"),
                dimensions = listOf(Dimension.CONTRAST),
                signature = Dimension.CONTRAST,
            )
    }
    if ("signage" in useIds && genre != null &&
        (genre.group == GenreGroup.SCRIPT || genre.key == "blackletter" || genre.key.startsWith("display-"))
    ) {
        out +=
            Tension(
                id = "use.signage.forms",
                title = "${genre.name} on accessible signs",
                body = "Accessible signs need conventional letterforms: no script, italic or highly decorative faces.",
                source =
                    Source(
                        SourceKind.RULE,
                        "2010 ADA Standards 703.5.1",
                        "https://www.ada.gov/law-and-regs/design-standards/2010-stds/",
                    ),
                dimensions = emptyList(),
            )
    }
    if ("signage" in useIds && answered(Level.WEIGHT_LIGHT)) {
        out +=
            Tension(
                id = "use.signage.stroke",
                title = "A light weight on signs",
                body = "Sign letters need a stroke of at least 10% of the height of I; light weights fall under it.",
                source =
                    Source(
                        SourceKind.RULE,
                        "2010 ADA Standards 703.5.7",
                        "https://www.ada.gov/law-and-regs/design-standards/2010-stds/",
                    ),
                dimensions = listOf(Dimension.WEIGHT),
            )
    }
    if ("watch-face" in useIds && model.ethos.useOptions["watch-face.aod"] == "yes" && answered(Level.WEIGHT_BOLD)) {
        out +=
            Tension(
                id = "use.watch.ambient",
                title = "A heavy face in always-on mode",
                body =
                    "Heavy figures light more of the screen; Wear OS allows 15% on average in ambient mode. Draw a light " +
                        "companion for ambient, or open the figures up.",
                source =
                    Source(
                        SourceKind.RULE,
                        "Wear OS app quality WO-P7",
                        "https://developer.android.com/develop/adaptive-apps/quality-guidelines/wear-app-quality",
                    ),
                dimensions = listOf(Dimension.WEIGHT),
            )
    }
    if (answered(Level.APERTURE_CLOSED) && model.uses.any { it.id in setOf("app-ui", "watch-face", "signage") }) {
        out +=
            Tension(
                id = "use.apertures",
                title = "Closed apertures, read small or at a glance",
                body = "Closed c, e and s fill in at small sizes and blur at a glance; open them a little, or keep them as a signature.",
                source = Source(SourceKind.CONVENTION, "Legibility practice; Clearview studies (FHWA)"),
                dimensions = listOf(Dimension.APERTURE),
                signature = Dimension.APERTURE,
            )
    }
    if (answered(Level.X_SMALL) && "app-ui" in useIds) {
        out +=
            Tension(
                id = "use.xheight",
                title = "A small x-height on screens",
                body = "Screen faces average a larger x-height; a small one reads smaller at the same point size.",
                source = Source(SourceKind.EVIDENCE, "Legge and Bigelow, 2011"),
                dimensions = listOf(Dimension.X_HEIGHT),
                signature = Dimension.X_HEIGHT,
            )
    }
    if ("body-text" in useIds &&
        (answered(Level.WIDTH_NARROW) || genre?.group == GenreGroup.SCRIPT || genre?.group == GenreGroup.DISPLAY)
    ) {
        out +=
            Tension(
                id = "use.longreading",
                title = "A display voice for long reading",
                body = "Narrow, decorative or script letters tire the eye over pages; consider a quieter text companion.",
                source = Source(SourceKind.CONVENTION, "Book typography practice"),
                dimensions = listOf(Dimension.WIDTH),
            )
    }
    if ("code" in useIds && genre != null && genre.key != "monospace") {
        out +=
            Tension(
                id = "use.code.pitch",
                title = "Code needs one width",
                body = "A ${genre.name.lowercase()} becomes a code face only when every glyph shares one advance.",
                source =
                    Source(
                        SourceKind.RULE,
                        "OpenType post table, isFixedPitch",
                        "https://learn.microsoft.com/en-us/typography/opentype/spec/post",
                    ),
                dimensions = emptyList(),
            )
    }

    // Answers that contradict each other.
    if (answered(Level.SERIFS_HAIRLINE) && answered(Level.CONTRAST_EVEN)) {
        out +=
            Tension(
                id = "answers.hairline.even",
                title = "Hairline serifs on an even stroke",
                body = "Hairline serifs belong to high contrast; on a monoline stroke they read as slab serifs drawn thin.",
                source = Source(SourceKind.CONVENTION, "Type classification practice"),
                dimensions = listOf(Dimension.SERIFS, Dimension.CONTRAST),
            )
    }
    // Without the atlas there is no measured departure, so the convention speaks instead.
    val offCentre = answered(Level.WAIST_HIGH) || answered(Level.WAIST_LOW)
    if (atlas == null && offCentre && genre != null && genre.group != GenreGroup.DISPLAY && Dimension.WAIST !in model.signatures) {
        out +=
            Tension(
                id = "answers.waist",
                title = "An off-centre bar outside display type",
                body = "A high or low crossbar reads as 1920s deco wherever it appears. Keep it as a signature, or centre it.",
                source = Source(SourceKind.CONVENTION, "Type history"),
                dimensions = listOf(Dimension.WAIST),
                signature = Dimension.WAIST,
            )
    }
    return out.distinctBy { it.id }.sortedBy { it.source.kind.ordinal }
}

/** Fewer genre faces than this and a share means nothing. */
private const val MIN_GENRE_FACES = 4

private fun direction(
    feature: Feature,
    up: Boolean,
): String =
    when (feature) {
        Feature.CONTRAST -> if (up) "higher in contrast" else "more even"
        Feature.APERTURE -> if (up) "more open" else "more closed"
        Feature.ROUNDNESS -> if (up) "squarer" else "rounder"
        Feature.X_HEIGHT -> if (up) "taller in the lowercase" else "shorter in the lowercase"
        Feature.WIDTH -> if (up) "wider" else "narrower"
        Feature.WEIGHT -> if (up) "heavier" else "lighter"
        Feature.WAIST -> if (up) "higher in the bar" else "lower in the bar"
        else -> if (up) "higher" else "lower"
    }
