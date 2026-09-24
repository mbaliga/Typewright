package dev.aarso.typewright.qa

import dev.aarso.typewright.core.font.ufo.UfoProject
import dev.aarso.typewright.core.geometry.count
import dev.aarso.typewright.qa.corpus.NodeEconomyCorpus
import dev.aarso.typewright.qa.corpus.NodeEconomyVerdict
import dev.aarso.typewright.qa.corpus.Quartiles
import dev.aarso.typewright.qa.corpus.verdictFor
import kotlin.math.round

/**
 * One glyph's node-economy result: its measured on/off-curve counts (`core-geometry`'s
 * [dev.aarso.typewright.core.geometry.count], the exact rule the corpus itself was built with --
 * see that function's own KDoc), the box each was measured against (`null` when [styleKey] has no
 * box for this glyph), the resulting [NodeEconomyVerdict] for each axis, and a plain-language
 * [rationale] restating brief §8.3's box-plot legend ("middle half of the N faces · your glyph ·
 * whiskers: full range · log scale") as a sentence, for a caller with no chart to draw yet.
 */
data class GlyphNodeEconomyResult(
    val glyphName: String,
    val onCurve: Int,
    val offCurve: Int,
    val onCurveBox: Quartiles?,
    val offCurveBox: Quartiles?,
    val onCurveVerdict: NodeEconomyVerdict?,
    val offCurveVerdict: NodeEconomyVerdict?,
    val rationale: String,
)

/** Every glyph in a project checked against one style class's node-economy box (brief §8). */
data class NodeEconomyReport(
    val styleKey: String,
    val glyphs: List<GlyphNodeEconomyResult>,
)

/**
 * Checks every glyph in [project] against [styleKey]'s node-economy box in [corpus] (brief §8).
 * Counts are measured from the UFO's own outlines, never from a compiled binary
 * (docs/ARCHITECTURE_REVIEW.md section 3 `:qa`, risk 2; section 6 decision 3) -- a compiler's
 * overlap-removal pass changes point counts, so measuring the binary would judge the compiler,
 * not the user's drawing (law 1).
 *
 * On-curve counts from a cubic UFO glyph and the corpus's TrueType-derived boxes are comparable
 * for the common case (an anchor stays an anchor across `cu2qu`'s conversion), but not exactly
 * (docs/ARCHITECTURE_REVIEW.md section 5 item 15) -- and *off*-curve counts are structurally
 * different by format: a [dev.aarso.typewright.core.geometry.CurveFormat.CUBIC] contour always
 * carries exactly two off-curve points per on-curve one, while a quadratic contour typically
 * carries about one, so an unfitted cubic UFO glyph's off-curve axis will read high against these
 * boxes almost by construction. This function still reports both axes, as asked; a caller judging
 * a UFO project should weight the on-curve verdict more than the off-curve one until that gap is
 * resolved.
 */
fun checkNodeEconomy(
    project: UfoProject,
    styleKey: String,
    corpus: NodeEconomyCorpus,
): NodeEconomyReport {
    val glyphResults =
        project.glyphs.map { glyph ->
            val counts = glyph.count()
            val onBox = corpus.onCurveBox(styleKey, glyph.name)
            val offBox = corpus.offCurveBox(styleKey, glyph.name)
            val onVerdict = onBox?.let { verdictFor(counts.onCurveEquivalent, it) }
            val offVerdict = offBox?.let { verdictFor(counts.offCurve, it) }
            GlyphNodeEconomyResult(
                glyphName = glyph.name,
                onCurve = counts.onCurveEquivalent,
                offCurve = counts.offCurve,
                onCurveBox = onBox,
                offCurveBox = offBox,
                onCurveVerdict = onVerdict,
                offCurveVerdict = offVerdict,
                rationale =
                    nodeEconomyRationale(glyph.name, styleKey, counts.onCurveEquivalent, counts.offCurve, onBox, onVerdict, offVerdict),
            )
        }
    return NodeEconomyReport(styleKey, glyphResults)
}

private fun nodeEconomyRationale(
    glyphName: String,
    styleKey: String,
    onCurve: Int,
    offCurve: Int,
    onBox: Quartiles?,
    onVerdict: NodeEconomyVerdict?,
    offVerdict: NodeEconomyVerdict?,
): String {
    if (onBox == null || onVerdict == null) {
        return "'$glyphName' has $onCurve on-curve and $offCurve off-curve points; " +
            "$styleKey has no measured box for this glyph, so there is nothing to compare against."
    }
    val sentence = StringBuilder()
    sentence
        .append("'$glyphName' has $onCurve on-curve points. ")
        .append("The middle half of $styleKey's ${onBox.n} faces runs ${fmt(onBox.q1)}-${fmt(onBox.q3)} ")
        .append("(median ${fmt(onBox.med)}); the whiskers span the full range, ${fmt(onBox.min)}-${fmt(onBox.max)}. ")
        .append("Your glyph is ${onVerdict.plainWord()}.")
    if (offVerdict != null) {
        sentence.append(" Off-curve, it has $offCurve points and is ${offVerdict.plainWord()}.")
    }
    return sentence.toString()
}

private fun NodeEconomyVerdict.plainWord(): String =
    when (this) {
        NodeEconomyVerdict.OUTLIER -> "an outlier"
        NodeEconomyVerdict.ABOVE -> "above the middle half, but inside the fence"
        NodeEconomyVerdict.IN_RANGE -> "in range"
    }

/** [value] rounded to 2 decimal places, printed without a trailing ".0" when it is a whole number. */
private fun fmt(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    val whole = rounded.toLong()
    return if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
}
