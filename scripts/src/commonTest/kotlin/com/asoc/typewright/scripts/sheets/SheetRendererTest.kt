// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.sheets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SheetRendererTest {
    private val latin = PrintSheets.forScript(PrintScript.LATIN)
    private val layout = SheetLayout.of(PaperSize.A4, PrintScript.LATIN)

    @Test
    fun aSheetDrawsFourMarksTheCodeModulesAndOneBoxPerGlyph() {
        val page = SheetRenderer.render(latin.sheets[0], layout, GuideStyle.LIGHT)
        val blackRects = page.ops.filterIsInstance<DrawOp.Rect>().filter { it.fillGray == 0.0 }
        val bits = SheetIdentity(PrintScript.LATIN, 0, 5, PaperSize.A4).encode()
        assertEquals(4 + bits.count { it }, blackRects.size)
        val marks = blackRects.filter { it.width == SheetLayout.MARK_SIDE_MM }
        assertEquals(4, marks.size)
        for ((mark, centre) in marks.zip(layout.markCentres)) {
            assertEquals(centre.x, mark.x + mark.width / 2.0, 1e-9)
            assertEquals(centre.y, mark.y + mark.height / 2.0, 1e-9)
        }
        val boxes = page.ops.filterIsInstance<DrawOp.Rect>().filter { it.fillGray == null }
        assertEquals(24, boxes.size)
        assertEquals(PaperSize.A4.widthMm, page.widthMm)
        assertEquals(PaperSize.A4.heightMm, page.heightMm)
    }

    @Test
    fun theIdentityModulesAreBlackWhereTheCodeHasA1() {
        val page = SheetRenderer.render(latin.sheets[3], layout, GuideStyle.LIGHT)
        val bits = SheetIdentity(PrintScript.LATIN, 3, 5, PaperSize.A4).encode()
        val modules = page.ops.filterIsInstance<DrawOp.Rect>().filter { it.fillGray == 0.0 && it.width == SheetLayout.CODE_MODULE_MM }
        val expected = layout.codeCentres.filterIndexed { i, _ -> bits[i] }
        assertEquals(expected.size, modules.size)
        for ((module, centre) in modules.zip(expected)) assertEquals(centre.x, module.x + module.width / 2.0, 1e-9)
    }

    @Test
    fun guidesAreDrawnWhenAskedAndNotWhenNot() {
        val withGuides = SheetRenderer.render(latin.sheets[0], layout, GuideStyle.LIGHT)
        val without = SheetRenderer.render(latin.sheets[0], layout, GuideStyle.NONE)
        val guideCount = layout.guides.lines.size + 2
        assertEquals(24 * guideCount, withGuides.ops.filterIsInstance<DrawOp.Line>().size)
        assertEquals(0, without.ops.filterIsInstance<DrawOp.Line>().size)
        assertTrue(withGuides.ops.size > without.ops.size)
    }

    @Test
    fun nothingInsideABoxIsDarkerThanTheGuideGreyBecauseNoGuideMayBecomeInk() {
        for (script in PrintScript.entries) {
            val l = SheetLayout.of(PaperSize.A4, script)
            for (spec in PrintSheets.forScript(script).sheets) {
                val page = SheetRenderer.render(spec, l, GuideStyle.LIGHT)
                for (op in page.ops) {
                    val inside =
                        l.cellBoxes.take(spec.glyphs.size).any { box ->
                            when (op) {
                                is DrawOp.Line -> box.contains(MmPoint(op.x1, op.y1)) || box.contains(MmPoint(op.x2, op.y2))
                                is DrawOp.Text -> box.contains(MmPoint(op.x, op.y))
                                is DrawOp.Rect -> op.fillGray == null && op.x == box.left && op.y == box.top
                            }
                        }
                    if (!inside) continue
                    val gray =
                        when (op) {
                            is DrawOp.Line -> op.gray
                            is DrawOp.Text -> op.gray
                            is DrawOp.Rect -> op.strokeGray ?: 1.0
                        }
                    assertTrue(
                        gray >= SheetRenderer.GUIDE_GRAY - 1e-9,
                        "${script.label} sheet ${spec.index}: $op is darker than the guides",
                    )
                }
            }
        }
    }

    @Test
    fun everyCellIsLabelledWithItsGlyphNameAndCodePoint() {
        val page = SheetRenderer.render(latin.sheets[0], layout, GuideStyle.NONE)
        val texts = page.ops.filterIsInstance<DrawOp.Text>().map { it.text }
        for (glyph in latin.sheets[0].glyphs) {
            assertTrue(glyph.glyphName in texts || glyph.character in texts, "the name ${glyph.glyphName} is printed")
            assertTrue(
                "U+" +
                    glyph.codePoint!!
                        .toString(16)
                        .uppercase()
                        .padStart(4, '0') in texts,
            )
        }
        assertTrue(texts.any { it.startsWith("Latin sheet 1 of 5") })
        assertTrue(texts.any { "CC0" in it })
    }

    @Test
    fun everyPrintedCharacterIsOneTheBase14FontsCanShow() {
        for (script in PrintScript.entries) {
            val l = SheetLayout.of(PaperSize.LETTER, script)
            for (page in SheetRenderer.renderAll(PrintSheets.forScript(script), PaperSize.LETTER, GuideStyle.LIGHT)) {
                for (text in page.ops.filterIsInstance<DrawOp.Text>()) {
                    assertTrue(text.text.all { winAnsiByte(it.code) != null }, "${script.label}: '${text.text}'")
                }
            }
            assertEquals(l.paper, PaperSize.LETTER)
        }
    }

    @Test
    fun aSheetCannotBeDrawnWithAnotherScriptsLayoutOrTooManyGlyphs() {
        val kana = SheetLayout.of(PaperSize.A4, PrintScript.KANA)
        assertFailsWith<IllegalArgumentException> { SheetRenderer.render(latin.sheets[0], kana, GuideStyle.LIGHT) }
        val tooMany = latin.sheets[0].copy(glyphs = latin.sheets[0].glyphs + latin.sheets[1].glyphs)
        assertFailsWith<IllegalArgumentException> { SheetRenderer.render(tooMany, layout, GuideStyle.LIGHT) }
    }

    @Test
    fun aShortLastSheetDrawsOnlyItsOwnBoxes() {
        val naskh = PrintSheets.forScript(PrintScript.NASKH)
        val page = SheetRenderer.render(naskh.sheets.last(), SheetLayout.of(PaperSize.A4, PrintScript.NASKH), GuideStyle.LIGHT)
        assertEquals(1, page.ops.filterIsInstance<DrawOp.Rect>().count { it.fillGray == null })
    }

    @Test
    fun pdfSafeKeepsWhatWinAnsiHasAndMarksTheRest() {
        assertEquals("A-z", pdfSafe("A-z"))
        assertEquals("–", pdfSafe("–"))
        assertEquals("?", pdfSafe("ı"))
        assertEquals("a?b", pdfSafe("aあb"))
        assertEquals(0x80, winAnsiByte(0x20AC))
        assertEquals(null, winAnsiByte(0x2260))
    }
}
