// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.qa.LayerOneReport
import com.asoc.typewright.qa.NodeEconomyReport
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus

/**
 * Everything the report reads: the [project]'s UFO, the [style] class its letters are judged against with
 * the [corpus] that holds that class's boxes (either missing leaves node economy not measured), what the
 * project records of where its outlines came from ([provenance]), and the names of its other masters.
 */
class CheckInputs(
    val project: UfoProject,
    val style: CheckStyle? = null,
    val corpus: NodeEconomyCorpus? = null,
    val provenance: ImportProvenance? = null,
    val otherMasters: List<MasterNames> = emptyList(),
)

/**
 * The order of the report, which is the order a reviewer attacks a font in.
 *
 * A Google Fonts reviewer opens the drawing first, so the groups run Outlines, Metrics, Provenance,
 * Names, Licence ([CheckGroup]'s own order). Inside a group what fails comes first, then what warns,
 * then what could not be measured, then what passes. Items of the same severity keep the order below,
 * which is the order a reviewer meets them in: the point count and the curves, then overshoot and
 * alignment, then extrema, then the four small outline heuristics, then anchors; the metrics; the source;
 * the names; the licence.
 */
object ReviewOrder {
    const val NODE_ECONOMY = "outlines/node-economy"
    const val CURVES_ARE_POLYGONS = "outlines/curves-are-polygons"
    const val OVERSHOOT = "outlines/overshoot"
    const val ALIGNMENT_MISS = "outlines/alignment-miss"
    const val EXTREMA = "outlines/extrema"
    const val COLLINEAR = "outlines/collinear-segments"
    const val JAGGY = "outlines/jaggy-turns"
    const val SHORT_SEGMENTS = "outlines/short-segments"
    const val SEMI_VERTICAL = "outlines/semi-vertical"
    const val ANCHORS = "outlines/anchors"
    const val VERTICAL_METRICS = "metrics/vertical-metrics"
    const val SOURCE_FOSSIL = "provenance/source-fossil"
    const val NAMES = "names/family-and-style"
    const val LICENCE = "licence/copyright-and-licence"

    /** Every check's id, in the order a reviewer meets them. */
    val ALL: List<String> =
        listOf(
            NODE_ECONOMY,
            CURVES_ARE_POLYGONS,
            OVERSHOOT,
            ALIGNMENT_MISS,
            EXTREMA,
            COLLINEAR,
            JAGGY,
            SHORT_SEGMENTS,
            SEMI_VERTICAL,
            ANCHORS,
            VERTICAL_METRICS,
            SOURCE_FOSSIL,
            NAMES,
            LICENCE,
        )

    /** Where [items] sort: group, then severity (fail, warn, not measured, pass), then reviewer order. */
    fun sorted(items: List<CheckItem>): List<CheckItem> =
        items.sortedWith(
            compareBy<CheckItem> { it.group.ordinal }
                .thenBy { severityRank(it.severity) }
                .thenBy { ALL.indexOf(it.id).let { index -> if (index < 0) ALL.size else index } },
        )

    private fun severityRank(severity: Severity): Int =
        when (severity) {
            Severity.FAIL -> 0
            Severity.WARN -> 1
            Severity.NOT_MEASURED -> 2
            Severity.PASS -> 3
        }
}

/**
 * Runs the fourteen layer-two checks over [inputs] and returns the report, sorted by [ReviewOrder], with
 * [layerOne] attached when it has run. It is a pure function of its arguments: the same project and the
 * same corpus give an equal report every time, and it measures the UFO, never a compiled binary (a
 * compiler changes point counts, so measuring the binary would judge the compiler, not the drawing).
 */
fun buildCheckReport(
    inputs: CheckInputs,
    layerOne: LayerOneReport? = null,
): CheckReport {
    val economy: NodeEconomyReport? =
        if (inputs.style != null && inputs.corpus != null && inputs.style.key in inputs.corpus.styleKeys) {
            checkNodeEconomy(inputs.project, inputs.style.key, inputs.corpus)
        } else {
            null
        }
    val items =
        listOf(
            nodeEconomyItem(inputs, economy),
            curvesArePolygonsItem(inputs),
            overshootItem(inputs),
            alignmentMissItem(inputs),
            extremaItem(inputs),
            collinearItem(inputs),
            jaggyItem(inputs),
            shortSegmentsItem(inputs),
            semiVerticalItem(inputs),
            anchorsItem(inputs),
            metricsItem(inputs),
            provenanceItem(inputs),
            namesItem(inputs),
            licenceItem(inputs),
        )
    return CheckReport(ReviewOrder.sorted(items), economy, inputs.style, layerOne)
}

/**
 * "What these checks do not measure", the last section of the report: what a green report does not
 * promise (brief §12, and KNOWLEDGE F1: Fontbakery reported no failure on a font whose every letter was a
 * pixel trace). The first paragraph is the promise's limit, the second the checks Typewright has not
 * built, the third where each number comes from.
 */
object NotMeasured {
    val PARAGRAPHS: List<String> =
        listOf(
            "A report with no fails means \"will not break\", not \"is good\". These checks read the numbers in your drawing " +
                "and the tables of the compiled file; they cannot tell whether the spacing is even, the curves are smooth, " +
                "the weights agree with each other, the design hangs together, or the letters are any good. " +
                "Your eye, on a rendered proof, is the final gate.",
            "Layer two does not yet check tangent continuity, stroke consistency, spacing, terminal style, composite " +
                "completeness or whether an italic was drawn rather than slanted. " +
                "They are not built, so they are not in the count.",
            "Point counts are judged against real faces and say how many. Every other threshold is either a published " +
                "rule or marked as our heuristic on the item that uses it.",
        )
}
