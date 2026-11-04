// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.capturetestkit.HyleDecoFixture
import com.asoc.typewright.capturetestkit.RepoFiles
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.OutlineBounds
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.engine.trace.NodeCount
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.traceOutlines
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The SVG import against the real pack, `scripts/templates/hyle-all-templates.zip`: every file of it is read, and drawings are made
 * the way a person makes them (a filled shape put in the empty `DRAW_HERE` layer of the real file) and traced from Fit.
 */
class SvgPackZipTest {
    private val zip = ZipFile(RepoFiles.file("scripts/templates/hyle-all-templates.zip"))

    private fun svgFiles(): Map<String, String> =
        zip
            .entries()
            .asSequence()
            .filter { it.name.endsWith(".svg") }
            .associate { it.name to zip.getInputStream(it).readBytes().decodeToString() }

    private fun fileFor(
        script: PrintScript,
        glyphName: String,
    ): Pair<String, String> {
        val glyph = PrintSheets.glyphs(script).first { it.glyphName == glyphName }
        val path = "${glyph.templateFolder}/${glyph.templateFileName}"
        return path to zip.getInputStream(zip.getEntry(path)).readBytes().decodeToString()
    }

    private fun draw(
        template: String,
        inner: String,
    ): String {
        assertTrue(template.contains("<g id=\"DRAW_HERE\"></g>"), "the real file has an empty drawing layer")
        return template.replace("<g id=\"DRAW_HERE\"></g>", "<g id=\"DRAW_HERE\">$inner</g>")
    }

    @Test
    fun everyTemplateOfThePackIsMatchedToItsGlyphAndReadsAsBlank() {
        val files = svgFiles()
        assertEquals(398, files.size, "the pack holds 398 svg files: 120 Latin, 112 kana, 68 Devanagari, 49 Naskh, 49 Nastaliq")
        val pack = SvgTemplateImport.importPack(files)
        // Latin 120, Hiragana 55, Katakana 57, Devanagari 68 (two of them copies of other templates), Naskh 49: all blank templates.
        assertEquals(120 + 55 + 57 + 68 + 49, pack.blank, pack.unusable.take(3).joinToString { it.sentence })
        assertEquals(0, pack.drawings.size, "the guides, the labels and the faint reference glyph are not drawings")
        // Nastaliq is in the pack but has no printable sheet; each of its 49 files says so.
        assertEquals(49, pack.unusable.size)
        assertTrue(pack.unusable.all { it.fileName.contains("/Nastaliq/") && it.sentence.contains("Nastaliq") })
    }

    @Test
    fun eachLatinFileNamesTheGlyphTheSheetPutsInThatCell() {
        for ((name, text) in svgFiles().filter { it.key.startsWith("svg/Latin/") }) {
            val read = SvgTemplateImport.import(name, text)
            assertIs<SvgImport.Blank>(read, name)
            val expected = PrintSheets.glyphs(PrintScript.LATIN).single { it.templateFileName == name.substringAfterLast('/') }
            assertEquals(expected.glyphName, read.glyph.glyphName, name)
        }
    }

    @Test
    fun theReferenceGlyphOfARealKanaFileIsNotImportedButWhenDrawnItIs() {
        val (path, text) = fileFor(PrintScript.KANA, PrintSheets.glyphs(PrintScript.KANA).first().glyphName)
        assertTrue(text.contains("reference_delete_me") && text.contains("<path d=\"M"), "the real file carries a reference glyph")
        assertIs<SvgImport.Blank>(SvgTemplateImport.import(path, text))
        // The person draws over the reference: its paths are put in the drawing layer.
        val referencePaths = Regex("<g id=\"reference_delete_me\"[^>]*>(.*?)</g>", RegexOption.DOT_MATCHES_ALL).find(text)!!.groupValues[1]
        val d = Regex("d=\"([^\"]+)\"").find(referencePaths)!!.groupValues[1]
        val read = SvgTemplateImport.import(path, draw(text, referencePaths.replace(" fill-rule=\"evenodd\"", "")))
        assertIs<SvgImport.Drawing>(read)
        assertEquals(d.count { it == 'M' }, read.outlines.size)
        // The first vertex is the file's M613,-441: x as written, y flipped to the font's y-up.
        val first = Regex("M(-?\\d+),(-?\\d+)").find(d)!!.destructured
        val start =
            read.outlines
                .first()
                .first()
                .start
        assertEquals(first.component1().toDouble(), start.x, 1e-9)
        assertEquals(-first.component2().toDouble(), start.y, 1e-9)
        assertTrue(read.outlines.all { loop -> loop.first().start == loop.last().end }, "every loop is closed")
    }

    @Test
    fun aRealKanaDrawingTracesFromFitWithTheRightContoursAndBox() {
        val (path, text) = fileFor(PrintScript.KANA, PrintSheets.glyphs(PrintScript.KANA).first().glyphName)
        val referencePaths = Regex("<g id=\"reference_delete_me\"[^>]*>(.*?)</g>", RegexOption.DOT_MATCHES_ALL).find(text)!!.groupValues[1]
        val read = assertIs<SvgImport.Drawing>(SvgTemplateImport.import(path, draw(text, referencePaths)))
        val trace = traceOutlines(read.outlines)
        assertEquals(
            listOf(TraceStage.CAPTURE, TraceStage.CORNERS, TraceStage.FIT, TraceStage.SNAP, TraceStage.REGULARISE, TraceStage.REVIEW),
            trace.stages.map { it.stage },
        )
        assertEquals(read.outlines.size, trace.contours.size)
        val captured = trace.stage(TraceStage.CAPTURE).polylines.sumOf { it.size }
        val fit = assertNotNull(trace.stage(TraceStage.FIT).nodes)
        assertTrue(fit.total < captured / 4, "${fit.total} nodes from $captured points")
        val review = assertNotNull(trace.review)
        assertTrue(review.boundsDifference <= 6, "the traced box is the drawn box: ${review.boundsDifference}")
        assertTrue(review.mismatchFraction < 0.05, "redrawn from the outline it matches the drawing: ${review.mismatchFraction}")
    }

    /** Hyle Deco's [char] as the SVG a person would put in the template: a filled path of the file's own y-down units. */
    private fun hyleAsSvg(char: Char): Pair<String, List<Contour>> {
        val contours = HyleDecoFixture.glyph(char).contours
        assertTrue(contours.all { c -> c.points.all { it.onCurve } }, "$char is a polygon")
        val d =
            contours.joinToString(" ") { c ->
                c.points.mapIndexed { i, p -> (if (i == 0) "M" else "L") + "${p.point.x},${-p.point.y}" }.joinToString(" ") + " Z"
            }
        return """<path d="$d"/>""" to contours
    }

    @Test
    fun hyleDecoDrawnIntoTheRealLatinFilesMeetsTheFittedTargetsFromFit() {
        // CLAUDE.md's fitted targets, on - off: T 8 - 0, o 16 - 16, n 14 - 8, H 12 - 0, and the T's stem foot at y=1 lands on 0.
        val targets = mapOf('T' to NodeCount(8, 0), 'o' to NodeCount(16, 16), 'n' to NodeCount(14, 8), 'H' to NodeCount(12, 0))
        val metricLines = listOf(-210, 0, 500, 700)
        for ((char, target) in targets) {
            val (path, text) = fileFor(PrintScript.LATIN, char.toString())
            val (svgPath, source) = hyleAsSvg(char)
            val read = assertIs<SvgImport.Drawing>(SvgTemplateImport.import(path, draw(text, svgPath)))
            val trace = traceOutlines(read.outlines, TraceParameters(metricLines = metricLines))
            assertEquals(target, trace.finalNodes, "$char from the SVG")
            val box =
                assertNotNull(
                    com.asoc.typewright.core.geometry
                        .Glyph("t", 0, trace.contours)
                        .outlineBounds(),
                )
            val sourceBox =
                assertNotNull(
                    com.asoc.typewright.core.geometry
                        .Glyph("s", 0, source)
                        .outlineBounds(),
                )
            assertEquals(source.size, trace.contours.size, "$char contours")
            assertTrue(boxDifference(box, sourceBox) <= 2, "$char: $box against $sourceBox")
            if (char == 'T') assertEquals(0, box.yMin, "the stem foot is on the baseline")
        }
    }

    private fun boxDifference(
        a: OutlineBounds,
        b: OutlineBounds,
    ): Int = maxOf(abs(a.xMin - b.xMin), abs(a.xMax - b.xMax), abs(a.yMin - b.yMin), abs(a.yMax - b.yMax))
}
