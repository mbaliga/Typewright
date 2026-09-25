// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.core.font.sfnt.SfntFont
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.enforceContourDirections
import com.asoc.typewright.core.geometry.segments
import java.io.File
import java.nio.file.Files
import kotlin.math.roundToInt

/**
 * The golden path's fixed, documented seeds (`PROMPTS_V1.md` P10 step 6): every `GoldenPathTest`
 * step test starts from one of these, never from another step's own output -- only [chainOpen]/
 * [com.asoc.typewright.goldenpath.GoldenPathTest.chainScan] carry real output forward, and those
 * still each start their own run from a seed here. Located through the `typewright.repoRoot`
 * system property `golden-path/build.gradle.kts` sets (the configuration-cache-safe way to hand a
 * test task the root directory without a `Project` reference at execution time).
 */
object Seeds {
    /** The repository root, from the `typewright.repoRoot` system property `golden-path/build.gradle.kts` sets. */
    val repoRoot: File by lazy {
        val path =
            System.getProperty("typewright.repoRoot")
                ?: error("system property 'typewright.repoRoot' is not set; golden-path/build.gradle.kts should set it")
        File(path)
    }

    /** S-ttf: `fonts/HyleDeco-Regular.ttf`, this repository's own dogfood fixture. */
    val sTtfBytes: ByteArray by lazy { File(repoRoot, "fonts/HyleDeco-Regular.ttf").readBytes() }

    /** S-ttf, parsed. A fresh [SfntFont] per call is not needed -- [SfntFont] is an immutable read of [sTtfBytes]. */
    val sTtfFont: SfntFont by lazy { readSfntFont(sTtfBytes) }

    /**
     * S-ttf2: `data/learn-faces/poppins/Poppins-Regular.ttf`, a second real, static, OFL-licensed
     * learn face -- picked because it is already this repository's committed static (non-variable)
     * TrueType Poppins fixture, so no new binary needs adding for the golden path. Fails loudly at
     * first use if the path ever moves, naming what a replacement needs (static, OFL, TrueType
     * `glyf` outlines -- `core-font` reads no other kind, `SfntFont.kt`'s own KDoc).
     */
    val sTtf2Bytes: ByteArray by lazy {
        val file = File(repoRoot, "data/learn-faces/poppins/Poppins-Regular.ttf")
        check(file.isFile) {
            "S-ttf2 seed not found at ${file.path}; pick another static OFL learn face from data/learn-faces " +
                "(a variable font or a CFF-outline font will not do -- core-font reads static TrueType glyf-outline fonts only)"
        }
        file.readBytes()
    }

    /** S-ttf2, parsed. */
    val sTtf2Font: SfntFont by lazy { readSfntFont(sTtf2Bytes) }

    /**
     * S-ufo: S-ttf converted through the new core-font bridge ([toUfoProject], no `familyName`
     * override -- it reads "Hyle Deco" off S-ttf's own `name` table) and written to a *fresh* temp
     * directory every call, so two step tests that both ask for S-ufo never see or mutate each
     * other's copy. The directory holds the UFO's files directly at its root (`metainfo.plist`,
     * `fontinfo.plist`, `glyphs/...`), exactly as [writeUfoProject] produces them.
     */
    fun newSUfoDirectory(): File {
        val dir = Files.createTempDirectory("typewright-golden-path-s-ufo-").toFile()
        writeUfoFiles(sTtfFont.toUfoProject(), dir)
        return dir
    }

    /**
     * S-project: S-ttf through the core-font bridge, `familyName = "Golden Path Seed"`, with every
     * glyph's QUADRATIC contours degree-elevated to CUBIC ([toCubicByDegreeElevation]) so the
     * project can actually be written to a UFO. `writeGlif` is cubic-only
     * (docs/OPEN_QUESTIONS.md item 125), so step 5's own seed is degree-elevated exactly, not
     * re-fit; how an import treats TrueType outlines is P12's decision, not this harness's.
     */
    fun newSProject(): UfoProject {
        val quadratic = sTtfFont.toUfoProject(familyName = "Golden Path Seed")
        return quadratic.copy(glyphs = quadratic.glyphs.map { it.toCubicByDegreeElevation() })
    }
}

/**
 * [this] glyph with every contour exactly degree-elevated from [CurveFormat.QUADRATIC] to
 * [CurveFormat.CUBIC] (see [Contour.degreeElevatedToCubic]), then [enforceContourDirections] run
 * over the result so the glyph's contours read outer counter-clockwise, inner clockwise (CLAUDE.md's
 * convention for cubic sources) -- glyf's own convention is the reverse, and a straight point-order
 * copy would carry that reversed winding into the UFO uncorrected. A test-only conversion, kept
 * private to this file rather than added to core-geometry or core-font: how a real import should
 * treat a TrueType outline (re-fit? keep quadratic and only elevate at the UFO-write boundary?) is
 * P12's decision, not this harness's.
 */
private fun Glyph.toCubicByDegreeElevation(): Glyph = copy(contours = enforceContourDirections(contours.map { it.degreeElevatedToCubic() }))

/**
 * [this] QUADRATIC contour, re-expressed as an exactly equivalent CUBIC one: each of
 * [Contour.segments]' [CurveSegment.Line]s becomes a degenerate cubic triple (`control1 = start`,
 * `control2 = end`) -- the exact convention [core-font's `GlifCodec`][com.asoc.typewright.core.font.ufo]
 * writes back out as a plain `type="line"` point with no controls -- and each
 * [CurveSegment.Quadratic] `(P0, Q, P2)` becomes the cubic `(P0, P0 + 2/3(Q - P0), P2 + 2/3(Q -
 * P2), P2)`, the standard exact quadratic-to-cubic degree elevation (the two curves are
 * point-for-point identical; nothing here approximates a shape). Every coordinate is rounded to
 * the nearest integer ("integers at rest", CLAUDE.md) -- at most 0.5 units of deviation, small
 * next to step 5's own +-2-unit bbox tolerance against this same seed.
 */
private fun Contour.degreeElevatedToCubic(): Contour {
    require(format == CurveFormat.QUADRATIC) { "expected a QUADRATIC contour to degree-elevate, found $format" }
    val points = mutableListOf<ContourPoint>()
    for (segment in segments()) {
        val (control1, control2) =
            when (segment) {
                is CurveSegment.Line -> {
                    segment.start to segment.end
                }

                is CurveSegment.Quadratic -> {
                    val c1 = segment.start + (segment.control - segment.start) * (2.0 / 3.0)
                    val c2 = segment.end + (segment.control - segment.end) * (2.0 / 3.0)
                    c1 to c2
                }

                is CurveSegment.Cubic -> {
                    error("a QUADRATIC contour's own segments() never produces a Cubic segment")
                }
            }
        points += ContourPoint(segment.start.rounded(), onCurve = true)
        points += ContourPoint(control1.rounded(), onCurve = false)
        points += ContourPoint(control2.rounded(), onCurve = false)
    }
    return Contour(points, CurveFormat.CUBIC)
}

/** [this] rounded to the nearest integer font-unit [Point], each axis independently. */
private fun Vec2.rounded(): Point = Point(x.roundToInt(), y.roundToInt())

/** Writes every file [writeUfoProject] produces for [project] under [targetDir], creating parent directories as needed. */
fun writeUfoFiles(
    project: UfoProject,
    targetDir: File,
) {
    targetDir.mkdirs()
    for ((path, content) in writeUfoProject(project)) {
        val target = File(targetDir, path)
        target.parentFile.mkdirs()
        target.writeText(content, Charsets.UTF_8)
    }
}
