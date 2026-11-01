// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.OutlineBounds
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.RasterFrame
import com.asoc.typewright.scripts.sheets.PrintGlyph
import com.asoc.typewright.scripts.sheets.PrintSheetSpec
import com.asoc.typewright.scripts.sheets.SheetIdentity
import com.asoc.typewright.scripts.sheets.SheetLayout

/**
 * One measured quantity and the limit the capture holds it to. Every limit here is [basis] "our heuristic": none of them
 * comes from a measured distribution of photographs (CLAUDE.md law 5), so the screen says so wherever it shows one.
 */
data class Measurement(
    val value: Double,
    val limit: Double,
    val unit: String,
    val basis: String = "our heuristic",
)

/** The kinds of bad light the capture names. */
enum class LightingProblem(
    val label: String,
) {
    GLARE("glare"),
    SHADOW("shadow"),
    LOW_CONTRAST("low contrast"),
}

/**
 * Why a photograph could not be used as a sheet, as a typed reason and a plain [sentence] the Capture and Camera screens
 * can show as they stand. Each says what to do about it.
 */
sealed interface CaptureFailure {
    val sentence: String

    /** Fewer than four corner marks are in view. [found] names the ones seen and [missing] the fourth when exactly three were. */
    data class CornersMissing(
        val found: List<ImageCorner>,
        val missing: List<ImageCorner>,
        val foundCount: Int,
    ) : CaptureFailure {
        override val sentence: String =
            when {
                foundCount == 3 && missing.isNotEmpty() -> {
                    "$foundCount of 4 corners: the ${missing.first().label} mark is missing. Move back until all four " +
                        "black squares are in the picture."
                }

                foundCount == 0 -> {
                    "No corner marks found. Is the whole sheet in the picture, with its four black squares?"
                }

                else -> {
                    "$foundCount of 4 corners. Move back until all four black squares are in the picture."
                }
            }
    }

    /** The four marks are there but the code between the top two does not read as a sheet. */
    data object IdentityUnreadable : CaptureFailure {
        override val sentence: String =
            "The four corners are in view, but the code between the top two could not be read, so this sheet " +
                "could not be told from the others. " +
                "Hold the page flat in good light and retake it, or say which sheet it is."
    }

    /** The marks' edges are softer than [measure]'s limit. */
    data class TooBlurry(
        val measure: Measurement,
    ) : CaptureFailure {
        override val sentence: String =
            "The photograph is too blurry: the edges of the corner marks are ${millimetres(measure.value)} mm soft and the limit is " +
                "${millimetres(measure.limit)} mm (${measure.basis}). Hold steady and let the camera focus, or use a sharper file."
    }

    /** Glare, a shadow or too little contrast beyond [measure]'s limit. */
    data class LightingTooStrong(
        val problem: LightingProblem,
        val measure: Measurement,
    ) : CaptureFailure {
        override val sentence: String =
            when (problem) {
                LightingProblem.GLARE -> {
                    "Glare washes out ${percent(measure.value)} of the sheet and the limit is ${percent(measure.limit)} " +
                        "(${measure.basis}). " +
                        "Tilt the page away from the light."
                }

                LightingProblem.SHADOW -> {
                    "A shadow darkens part of the sheet by ${percent(measure.value)} against the rest and the limit is " +
                        "${percent(measure.limit)} " +
                        "(${measure.basis}). Move so nothing shades the page."
                }

                LightingProblem.LOW_CONTRAST -> {
                    "The black squares are only ${percent(measure.value)} darker than the paper and the limit is " +
                        "${percent(measure.limit)} " +
                        "(${measure.basis}). Use more light."
                }
            }
    }

    /** The sheet is seen too much from the side: its nearer edge is [measure].value times the farther one. */
    data class PerspectiveTooExtreme(
        val measure: Measurement,
    ) : CaptureFailure {
        override val sentence: String =
            "The sheet is seen too much from the side: its nearer edge looks ${oneDecimal(measure.value)} times " +
                "as long as the far one and the " +
                "limit is ${oneDecimal(measure.limit)} (${measure.basis}). Hold the camera above the page, looking straight down."
    }
}

/** What the capture measured about a photograph that it could use, with the limits it was held to. */
data class CaptureQuality(
    /** How wide the 10-to-90% edge of a printed mark is, in millimetres of paper. Smaller is sharper. */
    val edgeWidth: Measurement,
    /** The ratio of the longer to the shorter of the sheet's opposite edges as seen. 1 is straight on. */
    val perspective: Measurement,
    /** The share of the sheet whose paper is blown out to white. */
    val glare: Measurement,
    /** How much darker the darkest twentieth of the paper is than its middle, as a fraction. */
    val shadow: Measurement,
    /** How much darker the marks are than the paper, as a fraction of the paper's brightness. Larger is better. */
    val markContrast: Measurement,
)

/** Why a cell is put to the person to check rather than taken as read. */
enum class UncertainReason(
    val sentence: String,
) {
    TOUCHES_EDGE("The ink runs into the edge of its box, so part of it may be cut off or belong to a neighbour."),
    FAINT("The ink is very faint; it may be a stray mark and not the glyph."),
}

/** What a list of relabel candidates is ranked by. */
enum class RankBasis(
    val sentence: String,
) {
    SHAPE_SIMILARITY("Ranked by how closely each looks like what you drew in this cell (our heuristic, not a probability)."),
    MANIFEST_ORDER("Ranked by position in the template: the nearest cells first (no other drawing to compare with)."),
}

/** One name offered for a cell on the relabel list, with the shape distance it was ranked by when there was one (smaller is more alike). */
data class RelabelCandidate(
    val glyphName: String,
    val distance: Double?,
)

/** Where one cell stands after reading. */
sealed interface CellStatus {
    /** Ink was found and nothing about it is in doubt. */
    data object Read : CellStatus

    /** Nothing is drawn in this cell. */
    data object Empty : CellStatus

    /** Ink was found but something about it is in doubt: [candidates], best first, are the names to offer, ranked [rankedBy]. */
    data class Uncertain(
        val reason: UncertainReason,
        val candidates: List<RelabelCandidate>,
        val rankedBy: RankBasis,
    ) : CellStatus
}

/**
 * One cell of a captured sheet. [glyphName] is the name the template gives the cell. When ink was found, [raster] is the
 * cell's drawing cut out of the photograph flat and square (ink dark on white, the printed guides taken out) and [frame]
 * places it in font units, so [com.asoc.typewright.engine.trace.traceCell] can trace it as it stands.
 */
class CapturedCell(
    val index: Int,
    val glyphName: String,
    val status: CellStatus,
    val raster: GrayscaleRaster?,
    val frame: RasterFrame?,
    /** The drawing's own box in font units, or null for an empty cell. */
    val inkBounds: OutlineBounds?,
    /** The pen's stroke width in font units, or null for an empty cell. */
    val strokeWidthUnits: Double?,
    /** The share of the drawing area that is ink. */
    val inkFraction: Double,
)

/** A photograph read as one printed sheet. */
class CapturedSheet(
    val identity: SheetIdentity,
    val layout: SheetLayout,
    val spec: PrintSheetSpec,
    /** Page millimetres to photograph pixels. */
    val pageToPhoto: Homography,
    val marks: List<FoundMark>,
    val quality: CaptureQuality,
    val cells: List<CapturedCell>,
) {
    val readCount: Int get() = cells.count { it.status is CellStatus.Read }
    val uncertainCount: Int get() = cells.count { it.status is CellStatus.Uncertain }
    val emptyCount: Int get() = cells.count { it.status is CellStatus.Empty }

    /** "2 need you · 22 read", the Capture row's second line. */
    val summary: String
        get() =
            buildString {
                if (uncertainCount > 0) append("$uncertainCount need you · ")
                append("$readCount read")
                if (emptyCount > 0) append(" · $emptyCount empty")
            }

    /** The glyph names, in cell order. */
    val glyphNames: List<String> get() = cells.map { it.glyphName }

    fun glyph(index: Int): PrintGlyph = spec.glyphs[index]
}

/** The outcome of reading a photograph. */
sealed interface CaptureResult {
    data class Success(
        val sheet: CapturedSheet,
    ) : CaptureResult

    /** [failure] says why not; [quality] holds whatever was measured before it was found out. */
    data class Failure(
        val failure: CaptureFailure,
        val quality: CaptureQuality? = null,
    ) : CaptureResult
}

/** How a photograph is read. The defaults are the app's; tests and the manual override change them. */
data class CaptureOptions(
    /** When false, blur, bad light and an extreme angle are measured and reported in [CaptureQuality] but do not stop the read. */
    val enforceQualityGates: Boolean = true,
    /** The resolution cells are cut out at, in pixels per millimetre of paper. */
    val cellPxPerMm: Double = 10.0,
    val maxEdgeWidthMm: Double = 1.0,
    val maxPerspective: Double = 1.8,
    val maxGlareFraction: Double = 0.08,
    val maxShadowDepth: Double = 0.45,
    val minMarkContrast: Double = 0.45,
)

internal fun millimetres(value: Double): String = oneDecimal(value)

internal fun percent(fraction: Double): String = "${(fraction * 100.0).let { kotlin.math.round(it).toInt() }}%"

internal fun oneDecimal(x: Double): String {
    val tenths = kotlin.math.round(x * 10.0).toInt()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}
