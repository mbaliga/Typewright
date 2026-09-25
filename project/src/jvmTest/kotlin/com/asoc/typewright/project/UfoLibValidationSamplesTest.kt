// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.UfoLib
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.writeGlif
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.reverse
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import kotlin.test.Test

/**
 * Writes the five sample projects docs/PROJECT_MODEL.md §11 lists into
 * `project/build/ufo-validation/<sample>/`, one per test, for CI's `tools/validate_ufo.py` to
 * open with fontTools' ufoLib -- a real cross-check that every file `core-font`/`:project` write
 * actually opens in the reference Python UFO tooling, not just this module's own Kotlin
 * round-trip tests. This test asserts nothing about UFO validity itself: its only job is to
 * produce real, on-disk samples and, beside each `.ufo`, an `expectations.json` naming what a
 * reader should see; the Python side is what proves them.
 *
 * Every sample's `expectations.json` is derived from the same [writeGlif] call [writeUfoProject]
 * itself uses (see [expectationsJson]), so it names exactly what is on disk rather than a
 * hand-predicted shape -- what it cross-checks is that fontTools' ufoLib reads a Typewright-written
 * `.glif` back the same way core-font's own writer meant it, not that this test can out-guess
 * `GlifCodec`.
 */
class UfoLibValidationSamplesTest {
    private val repoRoot: File by lazy {
        File(System.getProperty("typewright.repoRoot") ?: error("system property 'typewright.repoRoot' is not set"))
    }
    private val validationRoot: File by lazy {
        File(System.getProperty("typewright.ufoValidationDir") ?: error("system property 'typewright.ufoValidationDir' is not set"))
    }

    /** A fresh, empty directory for one sample, deleting anything a previous run left there. */
    private fun sampleDir(name: String): File =
        File(validationRoot, name).apply {
            deleteRecursively()
            mkdirs()
        }

    // ---- Sample 1: Hyle Deco through the sfnt bridge, with a lock, a frozen and an open diff, a pin and a reflection ----

    @Test
    fun hyleDecoThroughTheBridgeWithALockAFrozenAndAnOpenDiffAPinAndAReflection() =
        runBlocking {
            val root = sampleDir("1-hyle-deco-bridge")
            val sUfo = readSfntFont(File(repoRoot, "fonts/HyleDeco-Regular.ttf").readBytes()).toUfoProject()
            val spec =
                NewProjectSpec(
                    name = "Hyle Deco",
                    brief = Brief(source = BriefSource.FONT, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
                    scripts = listOf("Latn"),
                    masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = sUfo)),
                )
            val env = SessionEnvironment(scope = this, io = Dispatchers.IO, clock = SteppingClock(Fixtures.EPOCH))
            val session = (ProjectSession.create(FileSystemProjectStore(root.toPath()), spec, env) as CreateResult.Created).session
            val glyphT = GlyphRef("regular", "T")
            val glyphH = GlyphRef("regular", "H")

            // A frozen diff: approve, unlock, edit, then approve again (relocking closes the episode).
            session.execute(Approve(glyphT, ApprovalOrigin.TRACE))
            session.execute(Unlock(glyphT))
            session.execute(MovePoints(glyphT, setOf(PointRef(0, 0)), 0, -1))
            session.execute(Approve(glyphT, ApprovalOrigin.DRAW))

            // An open diff: approve, unlock, edit, and leave it there.
            session.execute(Approve(glyphH, ApprovalOrigin.DRAW))
            session.execute(Unlock(glyphH))
            session.execute(MovePoints(glyphH, setOf(PointRef(0, 0)), 5, 0))

            session.update(
                MetaChange.AddPin(
                    ScrapbookPin("pin-1", ScrapbookPinKind.NOTE, "Validation pin", "ufoLib validation", noteText = "A note"),
                    null,
                    null,
                ),
            )
            session.update(MetaChange.AddReflection("latn", 4, "Round ends everywhere, or nowhere."))

            session.flush()
            val finalUfo =
                session.state.value.font.masters
                    .single()
                    .ufo
            session.close()

            File(root, "expectations.json").writeText(expectationsJson(finalUfo), Charsets.UTF_8)
        }

    // ---- Sample 2: Poppins, a second static OFL font (golden-path's own Seeds.kt resolves it the same way) ----

    @Test
    fun poppins() {
        val root = sampleDir("2-poppins")
        val file = File(repoRoot, "data/learn-faces/poppins/Poppins-Regular.ttf")
        check(file.isFile) { "S-ttf2 seed not found at ${file.path}; see golden-path/src/test/kotlin/.../Seeds.kt" }
        val project = readSfntFont(file.readBytes()).toUfoProject()
        writeBareUfoSample(root, "Poppins-Regular.ufo", project)
    }

    // ---- Sample 3: a synthetic cubic project -- curve/line points, anchors, guidelines, unicodes, groups, kerning, features, fontinfo

    @Test
    fun syntheticCubicProject() {
        val root = sampleDir("3-synthetic-cubic")
        val oGlyph =
            Glyph(
                name = "O",
                advanceWidth = 600,
                contours =
                    listOf(
                        curvedCircleContour(center = Point(300, 350), radius = 250),
                        squareCounterContour(center = Point(300, 350)),
                    ),
                anchors = listOf(Anchor("top", Point(300, 700)), Anchor("bottom", Point(300, 0))),
                guidelines = listOf(Guideline(x = 300.0, name = "stem")),
                unicodes = listOf(0x004F),
            )
        val iGlyph =
            Glyph(
                name = "I",
                advanceWidth = 300,
                contours = listOf(lineRectangleContour(Point(100, 0), Point(200, 700))),
                unicodes = listOf(0x0049),
            )
        val project =
            UfoProject(
                fontInfo = syntheticFontInfo(),
                glyphs = listOf(oGlyph, iGlyph),
                kerningInfo =
                    UfoKerning(
                        groups = mapOf("public.kern1.O" to listOf("O"), "public.kern2.I" to listOf("I")),
                        kerning = mapOf("public.kern1.O" to mapOf("public.kern2.I" to -60.0)),
                        features = "# synthetic validation feature\nfeature kern {\n    pos O I -60;\n} kern;\n",
                    ),
            )
        writeBareUfoSample(root, "ValidationSynthetic-Regular.ufo", project)
    }

    // ---- Sample 4: a mixed project that triggers the glyph-level line-contour-format lib key ----

    @Test
    fun mixedProjectTriggeringTheGlyphLevelLineContourFormatKey() {
        val root = sampleDir("4-mixed-line-contour-format")
        // The project default (UfoLib()) is CUBIC. "curved" is an ordinary cubic glyph -- no lib
        // key needed. "straightQuadratic" has only straight, on-curve points modelled as
        // CurveFormat.QUADRATIC: writeGlif can't tell that apart from a straight CUBIC contour by
        // its points alone, so it must record its own com.asoc.typewright.lineContourFormat "quadratic"
        // (GlifCodec.glyphLibLineContourFormat) so a reader parses it back QUADRATIC, not the
        // project's CUBIC default.
        val curved = Glyph("curved", 600, listOf(curvedCircleContour(center = Point(300, 350), radius = 250)), unicodes = listOf(0x004F))
        val straightQuadratic =
            Glyph(
                "straightQuadratic",
                500,
                listOf(
                    Contour(
                        listOf(
                            ContourPoint(Point(50, 0), onCurve = true),
                            ContourPoint(Point(450, 0), onCurve = true),
                            ContourPoint(Point(450, 700), onCurve = true),
                            ContourPoint(Point(50, 700), onCurve = true),
                        ),
                        CurveFormat.QUADRATIC,
                    ),
                ),
                unicodes = listOf(0x0058),
            )
        val project =
            UfoProject(
                fontInfo = UfoFontInfo(familyName = "Validation Mixed", styleName = "Regular", unitsPerEm = 1000),
                glyphs = listOf(curved, straightQuadratic),
            )
        writeBareUfoSample(root, "ValidationMixed-Regular.ufo", project)
    }

    // ---- Sample 5: an all-off-curve quadratic contour (TrueType-style implied on-curve) ----

    @Test
    fun allOffCurveQuadraticContour() {
        val root = sampleDir("5-quadratic-all-offcurve")
        val points =
            listOf(Point(0, 0), Point(250, 250), Point(500, 0), Point(250, -250)).map { ContourPoint(it, onCurve = false) }
        val glyph =
            Glyph(
                name = "o",
                advanceWidth = 500,
                contours = listOf(Contour(points, CurveFormat.QUADRATIC)),
                unicodes = listOf(0x006F),
            )
        val project =
            UfoProject(
                fontInfo = UfoFontInfo(familyName = "Validation Quadratic", styleName = "Regular", unitsPerEm = 1000),
                glyphs = listOf(glyph),
                lib = UfoLib(lineContourFormat = CurveFormat.QUADRATIC),
            )
        writeBareUfoSample(root, "ValidationQuadratic-Regular.ufo", project)
    }

    // ---- Shared construction -------------------------------------------------------------------

    private fun syntheticFontInfo(): UfoFontInfo =
        UfoFontInfo(
            familyName = "Validation Synthetic",
            styleName = "Regular",
            unitsPerEm = 1000,
            ascender = 800,
            descender = -200,
            xHeight = 500,
            capHeight = 700,
            versionMajor = 1,
            versionMinor = 0,
            guidelines = listOf(Guideline(y = 0.0, name = "baseline"), Guideline(y = 500.0, name = "x-height")),
            copyright = "Copyright 2026 A System of Cells",
            openTypeNameDesigner = "Golden Path",
            openTypeNameManufacturer = "A System of Cells",
            openTypeNameLicense = "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
            openTypeNameLicenseURL = "https://openfontlicense.org",
            openTypeNamePreferredFamilyName = "Validation Synthetic",
            openTypeNamePreferredSubfamilyName = "Regular",
            postscriptFontName = "ValidationSynthetic-Regular",
            openTypeOS2VendorID = "ASOC",
            openTypeHheaAscender = 984,
            openTypeHheaDescender = -292,
            openTypeHheaLineGap = 0,
            openTypeOS2TypoAscender = 800,
            openTypeOS2TypoDescender = -200,
            openTypeOS2TypoLineGap = 276,
            openTypeOS2WinAscent = 984,
            openTypeOS2WinDescent = 292,
        )

    /** Writes [project] as a bare `.ufo` (no `typewright.json`) named [ufoDirName] under [sampleDir], plus its `expectations.json`. */
    private fun writeBareUfoSample(
        sampleDir: File,
        ufoDirName: String,
        project: UfoProject,
    ) {
        val ufoDir = File(sampleDir, ufoDirName)
        for ((path, content) in writeUfoProject(project)) {
            val target = File(ufoDir, path)
            target.parentFile.mkdirs()
            target.writeText(content, Charsets.UTF_8)
        }
        File(sampleDir, "expectations.json").writeText(expectationsJson(project), Charsets.UTF_8)
    }

    companion object {
        /** A closed square, four degenerate cubic "line" segments (on, off=start, off=end triples), counter-clockwise. */
        private fun lineRectangleContour(
            bottomLeft: Point,
            topRight: Point,
        ): Contour {
            val corners = listOf(bottomLeft, Point(topRight.x, bottomLeft.y), topRight, Point(bottomLeft.x, topRight.y))
            val points = mutableListOf<ContourPoint>()
            for (i in corners.indices) {
                val start = corners[i]
                val end = corners[(i + 1) % corners.size]
                points += ContourPoint(start, onCurve = true)
                points += ContourPoint(start, onCurve = false)
                points += ContourPoint(end, onCurve = false)
            }
            return Contour(points, CurveFormat.CUBIC)
        }

        /** A round, genuinely curved (non-degenerate) cubic contour: four arcs of a circle, counter-clockwise. */
        private fun curvedCircleContour(
            center: Point,
            radius: Int,
        ): Contour {
            val k = (radius * 0.5523).toInt()
            val top = Point(center.x, center.y + radius)
            val right = Point(center.x + radius, center.y)
            val bottom = Point(center.x, center.y - radius)
            val left = Point(center.x - radius, center.y)
            val points =
                listOf(
                    ContourPoint(top, true),
                    ContourPoint(Point(center.x + k, center.y + radius), false),
                    ContourPoint(Point(center.x + radius, center.y + k), false),
                    ContourPoint(right, true),
                    ContourPoint(Point(center.x + radius, center.y - k), false),
                    ContourPoint(Point(center.x + k, center.y - radius), false),
                    ContourPoint(bottom, true),
                    ContourPoint(Point(center.x - k, center.y - radius), false),
                    ContourPoint(Point(center.x - radius, center.y - k), false),
                    ContourPoint(left, true),
                    ContourPoint(Point(center.x - radius, center.y + k), false),
                    ContourPoint(Point(center.x - k, center.y + radius), false),
                )
            return Contour(points, CurveFormat.CUBIC)
        }

        /** A small square counter (line points only), clockwise (CLAUDE.md's inner winding): a CCW [lineRectangleContour], reversed. */
        private fun squareCounterContour(center: Point): Contour =
            lineRectangleContour(Point(center.x - 80, center.y - 80), Point(center.x + 80, center.y + 80)).reverse()

        // ---- expectations.json -------------------------------------------------------------------

        private val EXPECTATIONS_JSON = Json { prettyPrint = true }

        /**
         * `{"glyphs": {"<name>": {"unicodes": [...], "advance": N, "contours": [[<type-or-null>, ...], ...]}}}`,
         * for every glyph of [project]: [glyph]'s own data, and, per contour, the point-"type" sequence
         * a `<point>` element would carry (`"move"`/`"line"`/`"curve"`/`"qcurve"`, or JSON `null` for an
         * off-curve point) -- read straight off the real `.glif` text [writeGlif] produces for it (the
         * same call [writeUfoProject] itself makes), never hand-predicted.
         */
        private fun expectationsJson(project: UfoProject): String {
            val obj =
                buildJsonObject {
                    putJsonObject("glyphs") {
                        for (glyph in project.glyphs) {
                            putJsonObject(glyph.name) {
                                putJsonArray("unicodes") { glyph.unicodes.forEach { add(it) } }
                                put("advance", glyph.advanceWidth)
                                putJsonArray("contours") {
                                    for (contour in pointTypeSequence(glyph, project.lib.lineContourFormat)) {
                                        add(
                                            buildJsonArray {
                                                for (type in contour) add(if (type == null) JsonNull else JsonPrimitive(type))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            return EXPECTATIONS_JSON.encodeToString(JsonObject.serializer(), obj)
        }

        /**
         * [glyph]'s written `.glif` (via [writeGlif], [lineContourDefault] exactly as [writeUfoProject] would pass it), read
         * back as one `type` token (or `null`, off-curve) per `<point>`, grouped by `<contour>`.
         */
        private fun pointTypeSequence(
            glyph: Glyph,
            lineContourDefault: CurveFormat,
        ): List<List<String?>> {
            val glifXml = writeGlif(glyph, lineContourDefault)
            val contourBlock = Regex("<contour>(.*?)</contour>", RegexOption.DOT_MATCHES_ALL)
            val pointElement = Regex("<point\\b[^>]*/>")
            val typeAttribute = Regex("""type="([a-zA-Z]+)"""")
            val contours = mutableListOf<List<String?>>()
            for (contourMatch in contourBlock.findAll(glifXml)) {
                val types = mutableListOf<String?>()
                for (pointMatch in pointElement.findAll(contourMatch.groupValues[1])) {
                    types += typeAttribute.find(pointMatch.value)?.groupValues?.get(1)
                }
                contours += types
            }
            return contours
        }
    }
}
