// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.qa.GeometricFinding
import com.asoc.typewright.qa.GlyphNodeEconomyResult
import com.asoc.typewright.qa.NodeEconomyReport
import com.asoc.typewright.qa.OvershootFinding
import com.asoc.typewright.qa.checkAlignmentMiss
import com.asoc.typewright.qa.checkAnchorsPresent
import com.asoc.typewright.qa.checkCollinearSegments
import com.asoc.typewright.qa.checkExtremaOnCurve
import com.asoc.typewright.qa.checkJaggyTurns
import com.asoc.typewright.qa.checkOvershootPresence
import com.asoc.typewright.qa.checkSemiVertical
import com.asoc.typewright.qa.checkShortSegments
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import kotlin.math.abs
import kotlin.math.ln

/** The share of judged letters that must be outliers before node economy is a fail rather than a warning: our heuristic. */
internal const val NODE_ECONOMY_FAIL_SHARE = 0.25

/** The share of curved letters drawn as polygons above which "curves are polygons" is a fail: our heuristic. */
internal const val POLYGON_FAIL_SHARE = 0.5

/** Fontbakery stays silent about an outline heuristic that fires more often than this in one font, taking it for design intent. */
internal const val FONTBAKERY_SILENT_ABOVE = 100

/** An outlier whose count is more than this many times the largest count in the box is what a tracer leaves behind: our heuristic. */
private const val TRACER_FOOTPRINT_FACTOR = 10

private const val REVIEWER_QUOTE_NODES = "lots of precision, many nodes"
private const val REVIEWER_QUOTE_DIACRITICS = "the diacritics may need some work."

/** Letters that are drawn with curves in every style, used only when there is no measured box to say so: our heuristic. */
private val ROUND_LETTERS = listOf("o", "c", "e", "s", "O", "C", "G", "S", "Q")

private val ACCENTED_RANGES = listOf(0xC0..0xFF, 0x100..0x24F)

private fun Glyph.hasInk(): Boolean = contours.isNotEmpty()

// ---- node economy ----------------------------------------------------------------------------

internal fun nodeEconomyItem(
    inputs: CheckInputs,
    economy: NodeEconomyReport?,
): CheckItem {
    val style = inputs.style
    val title = "Node economy"
    val notMeasured = { why: String ->
        CheckItem(
            id = ReviewOrder.NODE_ECONOMY,
            group = CheckGroup.OUTLINES,
            title = title,
            detail = null,
            severity = Severity.NOT_MEASURED,
            summary = why,
            reviewerQuote = REVIEWER_QUOTE_NODES,
            action = null,
            basis = Basis.MEASURED,
            basisNote = "Point counts are judged against the measured faces in qa/corpus; this project has none to be judged against.",
            raw = why,
        )
    }
    if (inputs.corpus == null) return notMeasured("This build cannot read the node-economy data, so the point counts are not judged.")
    if (style == null || economy == null) {
        return notMeasured(
            "No style class is set for this project and none could be read from its letters, so there is no box of real faces " +
                "to judge the point counts against. Declare one in the Brief.",
        )
    }
    val judged = economy.glyphs.filter { it.onCurveVerdict != null }
    if (judged.isEmpty()) {
        return notMeasured("The ${style.name} data has no box for any of this font's glyphs, so none of them could be judged.")
    }

    val outliers = judged.filter { it.onCurveVerdict == NodeEconomyVerdict.OUTLIER }
    val above = judged.count { it.onCurveVerdict == NodeEconomyVerdict.ABOVE }
    val inRange = judged.count { it.onCurveVerdict == NodeEconomyVerdict.IN_RANGE }
    val noun = if (judged.all { it.glyphName.length == 1 && it.glyphName[0].isLetter() }) "letters" else "glyphs"
    val severity =
        when {
            outliers.isEmpty() -> Severity.PASS
            outliers.size.toDouble() / judged.size >= NODE_ECONOMY_FAIL_SHARE -> Severity.FAIL
            else -> Severity.WARN
        }
    val worst = outliers.maxByOrNull { ln(it.onCurve.coerceAtLeast(1).toDouble() / it.onCurveBox!!.q3.coerceAtLeast(1.0)) }
    val sentence =
        if (worst == null) {
            "All ${judged.size} $noun are inside the fence of ${judged.first().onCurveBox!!.n} ${style.name} faces: " +
                "$inRange in the middle half of their box and $above a little above it."
        } else {
            val box = worst.onCurveBox!!
            val facts =
                "Your ${worst.glyphName} has ${plural(worst.onCurve, "point")}. In ${box.n} ${style.name} faces a ${worst.glyphName} has " +
                    "${box.q1.statText()} to ${box.q3.statText()}, at most ${box.max.statText()}."
            val tracer =
                if (worst.onCurve > TRACER_FOOTPRINT_FACTOR * box.max) {
                    " A point at every step of a pixel is the footprint a tracer leaves."
                } else {
                    ""
                }
            facts + tracer
        }
    val heuristicNote = if (style.heuristic) " The style class was read from your letters by our heuristic." else ""
    return CheckItem(
        id = ReviewOrder.NODE_ECONOMY,
        group = CheckGroup.OUTLINES,
        title = title,
        detail =
            if (outliers.isEmpty()) {
                "no outliers among ${judged.size} $noun"
            } else {
                "${outliers.size} of ${judged.size} $noun are outliers"
            },
        severity = severity,
        summary = sentence + heuristicNote,
        reviewerQuote = REVIEWER_QUOTE_NODES,
        action = CheckAction("See every glyph against its box", CheckRoute.OpenEconomy),
        basis = Basis.HEURISTIC,
        basisNote =
            "Each verdict is measured against the ${judged.first().onCurveBox!!.n} ${style.name} faces in qa/corpus. The fence's " +
                "minimum slack of a quarter of Q3, and the quarter of letters that turns a warning into a fail, are our heuristic.",
        raw =
            rawLines(
                listOf("style class: ${style.key}${if (style.heuristic) " (read by our heuristic)" else ""}") +
                    judged.sortedByDescending { it.onCurve }.map(::economyRawLine),
            ),
    )
}

private fun economyRawLine(result: GlyphNodeEconomyResult): String {
    val box = result.onCurveBox!!
    val spread = listOf(box.min, box.q1, box.med, box.q3, box.max).joinToString(" / ") { it.statText() }
    return "${result.glyphName}: ${result.onCurve} on-curve, ${result.offCurve} off-curve; " +
        "box $spread (n ${box.n}); ${result.onCurveVerdict?.name?.lowercase()}"
}

// ---- curves are polygons -----------------------------------------------------------------------

/** The off-curve points a glyph really uses: a cubic side's controls on its own ends do not count. */
internal fun realOffCurvePoints(glyph: Glyph): Int =
    glyph.contours.sumOf { contour ->
        when (contour.format) {
            CurveFormat.QUADRATIC -> {
                contour.points.count { !it.onCurve }
            }

            CurveFormat.CUBIC -> {
                contour.segments().sumOf { segment ->
                    if (segment is CurveSegment.Cubic) {
                        (if (segment.control1 != segment.start) 1 else 0) + (if (segment.control2 != segment.end) 1 else 0)
                    } else {
                        0
                    }
                }
            }
        }
    }

internal fun curvesArePolygonsItem(inputs: CheckInputs): CheckItem {
    val project = inputs.project
    val style = inputs.style
    val corpus = inputs.corpus
    val inked = project.glyphs.filter { it.hasInk() }
    val byName = inked.associateBy { it.name }
    val measured = style != null && corpus != null
    val expected: List<Glyph> =
        if (measured) {
            inked.filter { corpus!!.offCurveBox(style!!.key, it.name)?.let { box -> box.q1 >= 1.0 } == true }
        } else {
            ROUND_LETTERS.mapNotNull { byName[it] }
        }
    val totalOff = inked.sumOf { realOffCurvePoints(it) }
    // The letters that most need their curves first: the ones the reference faces give the most off-curve points.
    val polygons =
        expected
            .filter { realOffCurvePoints(it) == 0 }
            .sortedWith(
                compareByDescending<Glyph> {
                    if (measured) {
                        corpus!!
                            .offCurveBox(
                                style!!.key,
                                it.name,
                            )?.med ?: 0.0
                    } else {
                        0.0
                    }
                }.thenBy { it.name },
            )
    val detail = "${plural(totalOff, "off-curve point")} in ${plural(inked.size, "glyph")}"
    val basisNote =
        if (measured) {
            "A letter counts as curved when at least three quarters of the ${style!!.name} faces in qa/corpus draw it with an " +
                "off-curve point (measured). How many polygons make a fail is our heuristic."
        } else {
            "No measured box is available, so the curved letters are o c e s O C G S Q: our heuristic."
        }
    val basis = if (measured) Basis.MEASURED else Basis.HEURISTIC
    if (expected.isEmpty()) {
        return CheckItem(
            id = ReviewOrder.CURVES_ARE_POLYGONS,
            group = CheckGroup.OUTLINES,
            title = "Curves are polygons",
            detail = detail.takeIf { inked.isNotEmpty() },
            severity = Severity.NOT_MEASURED,
            summary = "None of the letters that are normally drawn with curves are in this font yet, so there is nothing to judge.",
            reviewerQuote = REVIEWER_QUOTE_NODES,
            action = null,
            basis = basis,
            basisNote = basisNote,
            raw = "curved letters present: none",
        )
    }
    val severity =
        when {
            polygons.isEmpty() -> Severity.PASS
            polygons.size.toDouble() / expected.size > POLYGON_FAIL_SHARE -> Severity.FAIL
            else -> Severity.WARN
        }
    val sentence =
        when {
            polygons.isEmpty() -> {
                "Every one of the ${expected.size} letters that are normally drawn with curves has off-curve points " +
                    "that pull its lines into curves."
            }

            totalOff == 0 -> {
                "Every curve is a many-sided polygon pretending. Outlines have a second kind of point that pulls a line into a curve; " +
                    "this font has none."
            }

            else -> {
                "${polygons.size} of the ${expected.size} letters that are normally drawn with curves " +
                    "(${nameList(polygons.map { it.name }, 6)}) " +
                    "are polygons: a many-sided shape pretending to be a curve."
            }
        }
    val refit = polygons.map { it.name }
    return CheckItem(
        id = ReviewOrder.CURVES_ARE_POLYGONS,
        group = CheckGroup.OUTLINES,
        title = "Curves are polygons",
        detail = detail,
        severity = severity,
        summary = sentence,
        reviewerQuote = REVIEWER_QUOTE_NODES,
        action =
            refit.takeIf { it.isNotEmpty() }?.let {
                CheckAction("Refit ${refit.take(3).joinToString(" ")}", CheckRoute.OpenTrace(refit))
            },
        basis = basis,
        basisNote = basisNote,
        raw =
            rawLines(
                listOf("curved letters present: ${expected.joinToString(" ") { it.name }}") +
                    polygons.map(::polygonRawLine),
            ),
    )
}

private fun polygonRawLine(glyph: Glyph): String =
    "${glyph.name}: polygon, ${glyph.contours.size} contours, ${plural(realOffCurvePoints(glyph), "off-curve point")}"

// ---- overshoot ---------------------------------------------------------------------------------

internal fun overshootItem(inputs: CheckInputs): CheckItem {
    val info = inputs.project.fontInfo
    val findings =
        inputs.project.glyphs
            .filter { it.hasInk() }
            .flatMap { glyph ->
                // A round shape is one whose extreme is joined to a curve; a stem's foot or a flat capital's top is not, and
                // a polygon that merely imitates a curve has none to find (that is what "Curves are polygons" says).
                checkOvershootPresence(glyph, info, treatAsRound = true).filter { joinsACurve(glyph, it) }
            }
    val none = findings.filter { !it.isFlat }
    val flatNames = findings.filter { it.isFlat }.map { it.glyphName }.distinct()
    val names = none.map { it.glyphName }.distinct()
    val lines = none.map { it.metricValue }.distinct().sorted()
    val sentence =
        if (none.isEmpty()) {
            val flat =
                if (flatNames.isEmpty()) {
                    ""
                } else {
                    " ${nameList(flatNames)} ${if (flatNames.size == 1) "is" else "are"} flat-topped and flat-bottomed, " +
                        "so overshoot does not apply."
                }
            "No round shape sits exactly on a metric line.$flat"
        } else {
            val exempt =
                if (flatNames.isEmpty()) {
                    ""
                } else {
                    " The ${nameList(flatNames)} ${if (flatNames.size == 1) "is" else "are"} exempt: " +
                        "flat top and bottom, overshoot does not apply."
                }
            "Round shapes sit exactly on ${nameList(lines.map { it.toString() })}, so they read smaller than flat letters.$exempt"
        }
    val first = names.firstOrNull()
    return CheckItem(
        id = ReviewOrder.OVERSHOOT,
        group = CheckGroup.OUTLINES,
        title = "No overshoot",
        detail = nameStrip(names).takeIf { it.isNotEmpty() },
        severity = if (none.isEmpty()) Severity.PASS else Severity.WARN,
        summary = sentence,
        reviewerQuote = null,
        action = first?.let { CheckAction("Open $it in Draw", CheckRoute.OpenDraw(it)) },
        basis = Basis.HEURISTIC,
        basisNote =
            "That a shape is round where its extreme is joined to a curve, and that it should poke about 6 to 20 units past a line at " +
                "1000 units per em, are our heuristic (there is no published threshold). Flat-topped shapes are exempt.",
        raw = rawLines(findings.map { it.message }, empty = "no round shape sits on a metric line"),
    )
}

/** Whether a curved segment of the contour [finding] names starts or ends at the height of the line it sits on. */
private fun joinsACurve(
    glyph: Glyph,
    finding: OvershootFinding,
): Boolean =
    glyph.contours[finding.contourIndex].segments().any { segment ->
        val curved =
            when (segment) {
                is CurveSegment.Line -> false
                is CurveSegment.Quadratic -> true
                is CurveSegment.Cubic -> segment.control1 != segment.start || segment.control2 != segment.end
            }
        curved && (abs(segment.start.y - finding.metricValue) < ON_THE_LINE || abs(segment.end.y - finding.metricValue) < ON_THE_LINE)
    }

private const val ON_THE_LINE = 0.5

// ---- the geometric heuristics -------------------------------------------------------------------

private fun fontbakerySilence(count: Int): String =
    if (count > FONTBAKERY_SILENT_ABOVE) {
        " That is more than $FONTBAKERY_SILENT_ABOVE places. Fontbakery's own check takes that many for design intent and says nothing; " +
            "Typewright lists them anyway, because an outline drawn point by point rarely has so many."
    } else {
        ""
    }

private fun geometricItem(
    id: String,
    title: String,
    findings: List<GeometricFinding>,
    sentence: (count: Int, glyphs: Int) -> String,
    published: String,
    clean: String,
): CheckItem {
    val glyphs = findings.map { it.glyphName }.distinct()
    val first = glyphs.firstOrNull()
    return CheckItem(
        id = id,
        group = CheckGroup.OUTLINES,
        title = title,
        detail = findings.size.takeIf { it > 0 }?.let { "${plural(it, "place")} in ${plural(glyphs.size, "glyph")}" },
        severity = if (findings.isEmpty()) Severity.PASS else Severity.WARN,
        summary = if (findings.isEmpty()) clean else sentence(findings.size, glyphs.size) + fontbakerySilence(findings.size),
        reviewerQuote = null,
        action = first?.let { CheckAction("Open $it in Draw", CheckRoute.OpenDraw(it)) },
        basis = Basis.PUBLISHED,
        basisNote = "$published (Fontbakery's published outline threshold). Fontbakery reports it as a warning, never a fail.",
        raw = rawLines(findings.map(::findingRawLine)),
    )
}

private fun findingRawLine(finding: GeometricFinding): String =
    "${finding.glyphName} contour ${finding.contourIndex} at (${finding.location.x}, ${finding.location.y}): ${finding.message}"

internal fun alignmentMissItem(inputs: CheckInputs): CheckItem {
    val info = inputs.project.fontInfo
    val lines = metricLines(info)
    val findings = inputs.project.glyphs.flatMap { checkAlignmentMiss(it, lines) }
    val example = findings.firstOrNull()
    val exampleText =
        example
            ?.let { finding ->
                val y = finding.location.y
                val nearest = lines.entries.filter { it.value != y }.minByOrNull { abs(it.value - y) }
                val distance = nearest?.let { abs(it.value - y) }
                " The ${finding.glyphName} has a point at y = $y, " +
                    "${if (distance == 1) "1 unit" else "$distance units"} off the ${nearest?.key}."
            }.orEmpty()
    val lineText = nameList(lines.entries.map { "${it.key} ${it.value}" })
    return geometricItem(
        id = ReviewOrder.ALIGNMENT_MISS,
        title = "Alignment miss",
        findings = findings,
        sentence = { count, _ -> "${plural(count, "point")} sit 1 to 2 units off a metric line ($lineText).$exampleText" },
        published = "On-curve points within 2 units of a metric line, but not on it",
        clean = "No on-curve point sits 1 to 2 units off a metric line ($lineText).",
    )
}

internal fun extremaItem(inputs: CheckInputs): CheckItem {
    val findings = inputs.project.glyphs.flatMap { checkExtremaOnCurve(it) }
    return geometricItem(
        id = ReviewOrder.EXTREMA,
        title = "Extrema off the curve",
        findings = findings,
        sentence = { count, glyphs ->
            "${plural(count, "curve")} in ${plural(glyphs, "glyph")} reach their highest, lowest, leftmost or rightmost " +
                "point between two points, " +
                "where a renderer needs a point to put the extreme on."
        },
        published = "Every local extreme of a curve should land on a point",
        clean = "Every curve reaches its highest, lowest, leftmost and rightmost points on a point.",
    )
}

internal fun collinearItem(inputs: CheckInputs): CheckItem =
    geometricItem(
        id = ReviewOrder.COLLINEAR,
        title = "Collinear segments",
        findings = inputs.project.glyphs.flatMap { checkCollinearSegments(it) },
        sentence = { count, glyphs ->
            "${plural(count, "place")} in ${plural(glyphs, "glyph")} where two neighbouring segments turn by less than " +
                "0.1 radians (5.7 degrees): " +
                "the point between them adds weight and no shape."
        },
        published = "Consecutive segments that turn by less than 0.1 radians",
        clean = "No point sits between two segments that run the same way.",
    )

internal fun jaggyItem(inputs: CheckInputs): CheckItem =
    geometricItem(
        id = ReviewOrder.JAGGY,
        title = "Jaggy turns",
        findings = inputs.project.glyphs.flatMap { checkJaggyTurns(it) },
        sentence = { count, glyphs ->
            "${plural(count, "place")} in ${plural(glyphs, "glyph")} where two segments meet at a turn of " +
                "0.25 radians (14.3 degrees) or less: " +
                "too small to read as a corner, too sharp to read as a curve."
        },
        published = "Consecutive segments that turn by 0.25 radians or less",
        clean = "No two segments meet in a turn small enough to read as a kink.",
    )

internal fun shortSegmentsItem(inputs: CheckInputs): CheckItem =
    geometricItem(
        id = ReviewOrder.SHORT_SEGMENTS,
        title = "Short segments",
        findings = inputs.project.glyphs.flatMap { checkShortSegments(it) },
        sentence = { count, glyphs ->
            "${plural(count, "segment")} in ${plural(glyphs, "glyph")} are shorter than 3 units or 0.6% of their contour: " +
                "too short to draw, long enough to catch a renderer."
        },
        published = "Segments shorter than 3 units or 0.6% of their contour",
        clean = "No segment is shorter than 3 units or 0.6% of its contour.",
    )

internal fun semiVerticalItem(inputs: CheckInputs): CheckItem =
    geometricItem(
        id = ReviewOrder.SEMI_VERTICAL,
        title = "Almost vertical or horizontal",
        findings = inputs.project.glyphs.flatMap { checkSemiVertical(it) },
        sentence = { count, glyphs ->
            "${plural(count, "segment")} in ${plural(glyphs, "glyph")} are within half a degree of exactly vertical or " +
                "horizontal, but not on it: " +
                "they look intended and are not quite there."
        },
        published = "Segments within 0.5 degrees of vertical or horizontal but not on it",
        clean = "No segment is within half a degree of vertical or horizontal without being on it.",
    )

/** The lines a point is checked against: the baseline, and the x-height and cap height when the font says them. */
internal fun metricLines(info: UfoFontInfo): Map<String, Int> =
    buildMap {
        put("baseline", 0)
        info.xHeight?.let { put("x-height", it) }
        info.capHeight?.let { put("cap height", it) }
    }

// ---- anchors -----------------------------------------------------------------------------------

/** Whether [codePoint] is a Latin letter with a mark on it: Latin-1 and Latin Extended-A and -B, less × and ÷. */
internal fun isAccentedLetter(codePoint: Int): Boolean = ACCENTED_RANGES.any { codePoint in it } && codePoint != 0xD7 && codePoint != 0xF7

internal fun anchorsItem(inputs: CheckInputs): CheckItem {
    val findings = checkAnchorsPresent(inputs.project)
    val basisNote =
        "Which letters should carry anchors needs a glyph inventory this app does not have yet, so the check looks at " +
            "the 52 basic Latin letters " +
            "and counts accented letters by code point: our heuristic."
    if (findings.isEmpty()) {
        return CheckItem(
            id = ReviewOrder.ANCHORS,
            group = CheckGroup.OUTLINES,
            title = "Anchors",
            detail = null,
            severity = Severity.NOT_MEASURED,
            summary = "None of the 52 basic Latin letters are in this font yet, so there is nothing to check for anchors.",
            reviewerQuote = REVIEWER_QUOTE_DIACRITICS,
            action = null,
            basis = Basis.HEURISTIC,
            basisNote = basisNote,
            raw = "base letters present: none",
        )
    }
    val missing = findings.filter { !it.hasAppropriateAnchor }
    val accented = inputs.project.glyphs.count { glyph -> glyph.unicodes.any(::isAccentedLetter) }
    val severity =
        when {
            missing.isEmpty() -> Severity.PASS
            accented > 0 && missing.size == findings.size -> Severity.FAIL
            else -> Severity.WARN
        }
    val sentence =
        when {
            missing.isEmpty() -> {
                "All ${findings.size} base letters carry an anchor, so accented letters can be built from them."
            }

            accented > 0 && missing.size == findings.size -> {
                "${plural(accented, "accented letter")} ${if (accented == 1) "is" else "are"} in this font and none of the " +
                    "${findings.size} base letters carries an anchor. Without anchors every accent is placed by hand; " +
                    "draw the mark once, place the anchor once, and every composite builds itself."
            }

            accented > 0 -> {
                "${missing.size} of ${findings.size} base letters carry no anchor, so accents on them are placed by hand."
            }

            else -> {
                "${missing.size} of ${findings.size} base letters carry no anchor. There are no accented letters yet, " +
                    "so it will matter from the first accent you add."
            }
        }
    val first = missing.firstOrNull()?.glyphName
    return CheckItem(
        id = ReviewOrder.ANCHORS,
        group = CheckGroup.OUTLINES,
        title = "Anchors",
        detail = "${missing.size} of ${findings.size} bases".takeIf { missing.isNotEmpty() },
        severity = severity,
        summary = sentence,
        reviewerQuote = REVIEWER_QUOTE_DIACRITICS,
        action = first?.let { CheckAction("Open $it in Draw", CheckRoute.OpenDraw(it)) },
        basis = Basis.HEURISTIC,
        basisNote = basisNote,
        raw = rawLines(findings.map { it.message }),
    )
}
