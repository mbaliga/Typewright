// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.capturetestkit

import com.asoc.typewright.engine.trace.NodeCount
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript

/** What a photograph in the library is there to test; the accuracy report is given per setting. */
enum class Setting(
    val label: String,
) {
    STRAIGHT("straight on"),
    TILTED("tilted, lit unevenly"),
    HARDEST("hardest: tilt 24-25, blur, noise, heavy compression"),
    ORIENTATION("held sideways or upside down"),
    PAPER("US Letter, no guides, bright desk"),
    GUIDES("printer's guides differ from the template's"),
    FAINT("light pencil (darkness 0.22 and up)"),
    FAINT_LIMIT("fainter than the claim: darkness 0.16 (measured, no floor)"),
    OTHER_SCRIPT("kana, Devanagari, Naskh (ring drawings)"),
}

/** One photograph: sheet [sheetIndex] of [script] on [paper], seen as [spec] describes, with drawings from Hyle Deco or, when [rings], ring stand-ins. */
class PhotoCase(
    val label: String,
    val setting: Setting,
    val script: PrintScript,
    val sheetIndex: Int,
    val paper: PaperSize,
    val spec: PhotoSpec,
    val guides: GuideStyle = GuideStyle.LIGHT,
    val rings: Boolean = false,
)

/**
 * The synthetic photographs the capture is held to, in one place so the unit tests and the golden path judge the same set. All are
 * SYNTHETIC (see [PhotoSpec]); none is a photograph of paper.
 */
object PhotoLibrary {
    val hardestA =
        PhotoSpec(
            seed = 15,
            tiltDegrees = 25.0,
            tiltAxisDegrees = 135.0,
            rollDegrees = 8.0,
            gradient = 0.35,
            fill = 0.7,
            blurSigmaPx = 1.2,
            noiseSigma = 6.0,
            jpegQuality = 50,
            tint = Triple(225, 238, 255),
        )

    val hardestB =
        PhotoSpec(
            seed = 16,
            tiltDegrees = 25.0,
            tiltAxisDegrees = 200.0,
            rollDegrees = -8.0,
            gradient = 0.3,
            gradientAngleDegrees = 120.0,
            fill = 0.7,
            blurSigmaPx = 1.6,
            noiseSigma = 9.0,
            jpegQuality = 35,
            inkGray = 90,
        )

    val hardestC =
        PhotoSpec(
            seed = 17,
            tiltDegrees = 24.0,
            tiltAxisDegrees = 300.0,
            rollDegrees = 7.0,
            vignette = 0.4,
            gradient = 0.2,
            fill = 0.7,
            blurSigmaPx = 1.4,
            noiseSigma = 8.0,
            jpegQuality = 40,
            tint = Triple(235, 250, 225),
        )

    /** The accuracy set: 23 photographs covering all five Latin sheets, both papers, four scripts, every orientation and the hardest settings the generator has. */
    val accuracy: List<PhotoCase> =
        listOf(
            PhotoCase("straight-on, clean", Setting.STRAIGHT, PrintScript.LATIN, 0, PaperSize.A4, PhotoSpec(seed = 11)),
            PhotoCase(
                "tilt 10, roll 3",
                Setting.TILTED,
                PrintScript.LATIN,
                1,
                PaperSize.A4,
                PhotoSpec(seed = 12, tiltDegrees = 10.0, tiltAxisDegrees = 0.0, rollDegrees = 3.0, fill = 0.8),
            ),
            PhotoCase(
                "tilt 15 side-on, roll -4, light falloff 15%",
                Setting.TILTED,
                PrintScript.LATIN,
                2,
                PaperSize.A4,
                PhotoSpec(seed = 13, tiltDegrees = 15.0, tiltAxisDegrees = 90.0, rollDegrees = -4.0, gradient = 0.15, fill = 0.78),
            ),
            PhotoCase(
                "tilt 20, roll 6, falloff 25%, warm paper",
                Setting.TILTED,
                PrintScript.LATIN,
                3,
                PaperSize.A4,
                PhotoSpec(
                    seed = 14,
                    tiltDegrees = 20.0,
                    tiltAxisDegrees = 45.0,
                    rollDegrees = 6.0,
                    gradient = 0.25,
                    fill = 0.76,
                    tint = Triple(255, 244, 214),
                    noiseSigma = 4.0,
                ),
            ),
            PhotoCase(
                "HARDEST A: tilt 25, roll 8, falloff 35%, blur 1.2, noise 6, q50, cool paper",
                Setting.HARDEST,
                PrintScript.LATIN,
                4,
                PaperSize.A4,
                hardestA,
            ),
            PhotoCase(
                "HARDEST B: tilt 25, roll -8, falloff 30%, blur 1.6, noise 9, q35, pencil",
                Setting.HARDEST,
                PrintScript.LATIN,
                0,
                PaperSize.A4,
                hardestB,
            ),
            PhotoCase(
                "HARDEST C: tilt 24, vignette 40%, falloff 20%, blur 1.4, noise 8, q40, green paper",
                Setting.HARDEST,
                PrintScript.LATIN,
                2,
                PaperSize.A4,
                hardestC,
            ),
            PhotoCase(
                "HARDEST D: tilt 25, roll 5, blur 1.6, noise 9, q35, shadow, sheet 4",
                Setting.HARDEST,
                PrintScript.LATIN,
                3,
                PaperSize.A4,
                PhotoSpec(
                    seed = 27,
                    tiltDegrees = 25.0,
                    tiltAxisDegrees = 20.0,
                    rollDegrees = 5.0,
                    fill = 0.7,
                    gradient = 0.3,
                    blurSigmaPx = 1.6,
                    noiseSigma = 9.0,
                    jpegQuality = 35,
                    shadowDepth = 0.25,
                    tint = Triple(250, 240, 230),
                ),
            ),
            PhotoCase(
                "held sideways (roll 90)",
                Setting.ORIENTATION,
                PrintScript.LATIN,
                1,
                PaperSize.A4,
                PhotoSpec(
                    seed = 18,
                    widthPx = 2000,
                    heightPx = 1500,
                    rollDegrees = 90.0,
                    fill = 1.05,
                    tiltDegrees = 8.0,
                    tiltAxisDegrees = 60.0,
                ),
            ),
            PhotoCase(
                "upside down (roll 180)",
                Setting.ORIENTATION,
                PrintScript.LATIN,
                3,
                PaperSize.A4,
                PhotoSpec(seed = 19, rollDegrees = 180.0, tiltDegrees = 12.0, tiltAxisDegrees = 110.0, fill = 0.8),
            ),
            PhotoCase(
                "held sideways the other way (roll 270), noisy",
                Setting.ORIENTATION,
                PrintScript.LATIN,
                4,
                PaperSize.A4,
                PhotoSpec(
                    seed = 20,
                    widthPx = 2000,
                    heightPx = 1500,
                    rollDegrees = 270.0,
                    fill = 1.05,
                    tiltDegrees = 10.0,
                    noiseSigma = 5.0,
                    jpegQuality = 60,
                ),
            ),
            PhotoCase(
                "US Letter, tilt 14",
                Setting.PAPER,
                PrintScript.LATIN,
                2,
                PaperSize.LETTER,
                PhotoSpec(seed = 21, tiltDegrees = 14.0, tiltAxisDegrees = 30.0, rollDegrees = 2.0, fill = 0.8, gradient = 0.15),
            ),
            PhotoCase(
                "no printed guides",
                Setting.PAPER,
                PrintScript.LATIN,
                1,
                PaperSize.A4,
                PhotoSpec(seed = 22, tiltDegrees = 12.0, tiltAxisDegrees = 250.0, fill = 0.8, noiseSigma = 4.0),
                GuideStyle.NONE,
            ),
            PhotoCase(
                "bright desk, tilt 18",
                Setting.PAPER,
                PrintScript.LATIN,
                0,
                PaperSize.A4,
                PhotoSpec(
                    seed = 23,
                    tiltDegrees = 18.0,
                    tiltAxisDegrees = 10.0,
                    rollDegrees = -5.0,
                    fill = 0.76,
                    deskGray = 215,
                    gradient = 0.15,
                ),
            ),
            PhotoCase(
                "guides twice as dark and twice as wide, shifted 0.25 mm, tilt 15",
                Setting.GUIDES,
                PrintScript.LATIN,
                0,
                PaperSize.A4,
                PhotoSpec(
                    seed = 28,
                    tiltDegrees = 15.0,
                    tiltAxisDegrees = 70.0,
                    rollDegrees = 3.0,
                    fill = 0.78,
                    blurSigmaPx = 0.8,
                    guideDarknessScale = 2.0,
                    guideWidthScale = 2.0,
                    guideOffsetMm = 0.25,
                ),
            ),
            PhotoCase(
                "guides half as dark and half as wide, hard conditions",
                Setting.GUIDES,
                PrintScript.LATIN,
                2,
                PaperSize.A4,
                PhotoSpec(
                    seed = 29,
                    tiltDegrees = 22.0,
                    tiltAxisDegrees = 160.0,
                    rollDegrees = -5.0,
                    fill = 0.72,
                    blurSigmaPx = 1.2,
                    noiseSigma = 6.0,
                    jpegQuality = 50,
                    guideDarknessScale = 0.5,
                    guideWidthScale = 0.5,
                ),
            ),
            PhotoCase(
                "light pencil (darkness 0.33), tilt 12",
                Setting.FAINT,
                PrintScript.LATIN,
                1,
                PaperSize.A4,
                PhotoSpec(seed = 30, tiltDegrees = 12.0, tiltAxisDegrees = 40.0, fill = 0.8, inkGray = 170),
            ),
            PhotoCase(
                "very light pencil (darkness 0.25), tilt 8",
                Setting.FAINT,
                PrintScript.LATIN,
                3,
                PaperSize.A4,
                PhotoSpec(seed = 31, tiltDegrees = 8.0, tiltAxisDegrees = 200.0, fill = 0.8, inkGray = 190, noiseSigma = 3.0),
            ),
            PhotoCase(
                "faint pencil (darkness 0.22) with doubled guides",
                Setting.FAINT,
                PrintScript.LATIN,
                4,
                PaperSize.A4,
                PhotoSpec(
                    seed = 32,
                    tiltDegrees = 10.0,
                    tiltAxisDegrees = 100.0,
                    fill = 0.8,
                    inkGray = 200,
                    guideDarknessScale = 2.0,
                    guideWidthScale = 1.5,
                ),
            ),
            PhotoCase(
                "barely there pencil (darkness 0.16), tilt 6",
                Setting.FAINT_LIMIT,
                PrintScript.LATIN,
                2,
                PaperSize.A4,
                PhotoSpec(seed = 34, tiltDegrees = 6.0, tiltAxisDegrees = 300.0, fill = 0.8, inkGray = 215),
            ),
            PhotoCase(
                "Kana sheet 2, rings, tilt 16",
                Setting.OTHER_SCRIPT,
                PrintScript.KANA,
                1,
                PaperSize.A4,
                PhotoSpec(
                    seed = 24,
                    tiltDegrees = 16.0,
                    tiltAxisDegrees = 150.0,
                    rollDegrees = 5.0,
                    fill = 0.78,
                    gradient = 0.2,
                    blurSigmaPx = 0.8,
                ),
                rings = true,
            ),
            PhotoCase(
                "Devanagari sheet 3 (18 cells), rings, Letter",
                Setting.OTHER_SCRIPT,
                PrintScript.DEVANAGARI,
                2,
                PaperSize.LETTER,
                PhotoSpec(seed = 25, tiltDegrees = 12.0, tiltAxisDegrees = 70.0, rollDegrees = -3.0, fill = 0.8, noiseSigma = 5.0),
                rings = true,
            ),
            PhotoCase(
                "Naskh sheet 1, rings, tilt 20",
                Setting.OTHER_SCRIPT,
                PrintScript.NASKH,
                0,
                PaperSize.A4,
                PhotoSpec(seed = 26, tiltDegrees = 20.0, tiltAxisDegrees = 220.0, rollDegrees = 4.0, fill = 0.74, gradient = 0.25),
                rings = true,
            ),
        )

    /** How a trace photograph is held to the fitted targets. */
    enum class TraceClass(
        val label: String,
    ) {
        /** The fitted targets are met exactly. */
        EXACT("held to the fitted targets"),

        /** The hardest settings: measured and reported, held only to the claim [TraceTargets.hardClaim] states. */
        HARD("hardest settings, held to the stated claim"),
    }

    class TracePhoto(
        val label: String,
        val traceClass: TraceClass,
        val spec: PhotoSpec,
    )

    /** The photographs the trace is judged on: the straight-on, tilted and soft ones are held to the targets exactly; the hardest ones to the stated claim. */
    val trace: List<TracePhoto> =
        listOf(
            TracePhoto("straight-on", TraceClass.EXACT, PhotoSpec(seed = 11)),
            TracePhoto(
                "tilt 10, roll 3",
                TraceClass.EXACT,
                PhotoSpec(seed = 12, tiltDegrees = 10.0, tiltAxisDegrees = 0.0, rollDegrees = 3.0, fill = 0.8),
            ),
            TracePhoto(
                "tilt 20, roll 6, falloff 25%, warm paper, noise 4",
                TraceClass.EXACT,
                PhotoSpec(
                    seed = 14,
                    tiltDegrees = 20.0,
                    tiltAxisDegrees = 45.0,
                    rollDegrees = 6.0,
                    gradient = 0.25,
                    fill = 0.76,
                    tint = Triple(255, 244, 214),
                    noiseSigma = 4.0,
                ),
            ),
            TracePhoto(
                "tilt 16, blur 0.9, noise 5, q70",
                TraceClass.EXACT,
                PhotoSpec(
                    seed = 33,
                    tiltDegrees = 16.0,
                    tiltAxisDegrees = 120.0,
                    rollDegrees = -3.0,
                    fill = 0.78,
                    blurSigmaPx = 0.9,
                    noiseSigma = 5.0,
                    jpegQuality = 70,
                ),
            ),
            TracePhoto("HARDEST A: tilt 25, falloff 35%, blur 1.2, noise 6, q50", TraceClass.HARD, hardestA),
            TracePhoto("HARDEST B: tilt 25, blur 1.6, noise 9, q35, pencil", TraceClass.HARD, hardestB),
            TracePhoto("HARDEST C: tilt 24, vignette 40%, blur 1.4, noise 8, q40", TraceClass.HARD, hardestC),
        )
}

/** CLAUDE.md's fitted targets for the four fixture glyphs, on - off, and where each sits on the Latin sheets. */
object TraceTargets {
    class Target(
        val char: Char,
        val sheet: Int,
        val cell: Int,
        val nodes: NodeCount,
        val contours: Int,
    )

    val all =
        listOf(
            Target('T', 0, 19, NodeCount(8, 0), 1),
            Target('H', 0, 7, NodeCount(12, 0), 1),
            Target('o', 1, 16, NodeCount(16, 16), 2),
            Target('n', 1, 15, NodeCount(14, 8), 1),
        )

    /** The metric lines of the Latin templates: descender, baseline, x-height, cap and ascender. */
    val metricLines = listOf(-210, 0, 500, 700)

    /** The ink box of a traced glyph may differ from the source's by this much: 2% of the em. */
    const val BOX_TOLERANCE_UNITS: Int = 20

    /**
     * The claim made for the hardest photographs, which do not all reach the targets: every glyph keeps its contour count and its box,
     * and has no more on-curve and off-curve points than the most the three hardest photographs gave it when they were measured
     * (docs/CAPTURE.md has the table). It is a statement of what was measured, written after measuring, not a quality bar and not a
     * target; it exists so that a fitter that gets worse on the hardest photographs fails the test instead of passing under a loose bound.
     */
    fun hardClaim(target: Target): NodeCount =
        when (target.char) {
            'T' -> NodeCount(9, 2)
            'H' -> NodeCount(12, 0)
            'o' -> NodeCount(22, 28)
            'n' -> NodeCount(18, 16)
            else -> error("no measured claim for '${target.char}'")
        }
}
