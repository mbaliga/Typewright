// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheetSpec
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.launch

/** What the person has chosen on Templates: the script, the paper and whether the boxes print their guides. */
@Stable
class TemplatesState(
    script: PrintScript = PrintScript.LATIN,
    paper: PaperSize = PaperSize.A4,
    guides: GuideStyle = GuideStyle.LIGHT,
) {
    var script by mutableStateOf(script)
    var paper by mutableStateOf(paper)
    var guides by mutableStateOf(guides)

    /** What the last export did, in a line, until the next. */
    var result by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
}

/**
 * Templates (S07): the sheets to print and draw on, and the SVG pack for vector tools. Thumbnails are drawn from the same sheet
 * model the camera reads, so what is shown is what prints. The deck's right circle exports the sheets as a PDF; where that goes
 * is the platform's: a save dialog, the system's create-document screen, or a download.
 */
@Composable
fun TemplatesScreen(
    texture: CanvasTexture,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    state: TemplatesState = remember { TemplatesState() },
) {
    val exporter = LocalFileExporter.current
    val scope = rememberCoroutineScope()

    fun export(
        name: String,
        mime: String,
        make: suspend () -> ByteArray,
    ) {
        if (state.busy) return
        state.busy = true
        state.result = null
        scope.launch {
            try {
                val bytes = make()
                state.result =
                    when (val outcome = exporter.export(name, mime, bytes)) {
                        is ExportOutcome.Saved -> "Saved ${outcome.where}."
                        is ExportOutcome.Handed -> outcome.sentence
                        ExportOutcome.Cancelled -> null
                        is ExportOutcome.Failed -> "Not saved: ${outcome.message}"
                    }
            } finally {
                state.busy = false
            }
        }
    }
    TemplatesFrame(
        texture = texture,
        state = state,
        exporterNote = exporter.describe,
        onBack = onBack,
        onExportPdf = {
            export(
                TemplatePack.pdfName(state.script, state.paper),
                "application/pdf",
            ) { TemplatePack.pdf(state.script, state.paper, state.guides) }
        },
        onExportPack = { export(TemplatePack.packName(state.script), "application/zip") { TemplatePack.packZip(state.script) } },
        modifier = modifier,
    )
}

/** The page drawn from its [state], with no coroutines of its own. */
@Composable
fun TemplatesFrame(
    texture: CanvasTexture,
    state: TemplatesState,
    exporterNote: String,
    onBack: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheets = remember(state.script) { TemplatePack.sheets(state.script) }
    val glyphCount = sheets.sumOf { it.glyphs.size }
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val scripts = PrintScript.entries
    SectionScaffold(
        texture = texture,
        section = DeckSection.CAPTURE,
        title = "Templates",
        subtitle =
            "${state.script.label} · $glyphCount glyphs · " +
                (if (state.guides == GuideStyle.LIGHT) "metric guides drawn in" else "no guides printed"),
        modifier = modifier,
        toggles = scripts.map { DeckToggle(it.label, on = it == state.script) },
        onToggle = { state.script = scripts[it] },
        onBack = onBack,
        right = DeckRight.DOWNLOAD,
        onRight = onExportPdf,
        rightLabel = "Export the sheets as a PDF",
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText("Print sheets · for pen and paper", style = sentence(fg, 14.0, FontWeight.SemiBold))
                    BasicText(
                        "${SheetLayout.COLUMNS * SheetLayout.ROWS} cells a sheet. The four corner marks let the camera square the page.",
                        style = sentence(muted),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (sheet in sheets) SheetThumbnail(texture, sheet, state.paper, state.guides)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (paper in PaperSize.entries) ChoiceChip(paper.label, state.paper == paper, texture, { state.paper = paper })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (guides in GuideStyle.entries) {
                            ChoiceChip(
                                guides.label,
                                state.guides == guides,
                                texture,
                                { state.guides = guides },
                            )
                        }
                    }
                }
                BasicText("↓ exports these sheets as a PDF · $exporterNote".uppercase(), style = mono(muted, 9.0))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("SVG templates · for Illustrator, Figma or Inkscape", style = sentence(fg, 14.0, FontWeight.SemiBold))
                BasicText(
                    "One file per glyph, with the metric guides drawn in. Draw filled shapes, then bring them back through " +
                        "Capture › + › File. Vector drawings skip straight to the Fit stage.",
                    style = sentence(muted),
                )
                LinkText(if (state.busy) "Making the pack…" else "Export the SVG pack", texture, onExportPack)
            }
            state.result?.let { BasicText(it, style = sentence(fg)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                BasicText("○", style = sentence(fg, 13.0))
                BasicText("Templates are CC0. What you draw on them is yours.", style = sentence(muted))
            }
        }
    }
}

private const val THUMB_W = 58
private const val THUMB_H = 82

/** One sheet, drawn from the model that prints it: the paper, the four corner marks, the boxes, and each box's glyph faint inside it. */
@Composable
private fun SheetThumbnail(
    texture: CanvasTexture,
    sheet: PrintSheetSpec,
    paper: PaperSize,
    guides: GuideStyle,
) {
    val layout = remember(paper, sheet.script) { SheetLayout.of(paper, sheet.script) }
    val measurer = rememberTextMeasurer()
    val ink = texture.ink.toColor()
    val muted = texture.muted.toColor()
    val sheetColor = texture.canvas.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(width = THUMB_W.dp, height = THUMB_H.dp).background(sheetColor)) {
            val k = size.width / layout.pageWidthMm.toFloat()
            drawRect(
                ink.copy(alpha = HAIRLINE),
                Offset.Zero,
                size,
                style =
                    androidx.compose.ui.graphics.drawscope
                        .Stroke(1f),
            )
            val half = SheetLayout.MARK_SIDE_MM.toFloat() / 2f
            for (c in layout.markCentres) {
                drawRect(ink, Offset((c.x.toFloat() - half) * k, (c.y.toFloat() - half) * k), Size(half * 2 * k, half * 2 * k))
            }
            val style =
                TextStyle(
                    fontSize = 6.sp,
                    color =
                        ink.copy(
                            alpha =
                                if (guides ==
                                    GuideStyle.LIGHT
                                ) {
                                    GLYPH_ALPHA
                                } else {
                                    GLYPH_ALPHA / 2
                                },
                        ),
                )
            for ((i, glyph) in sheet.glyphs.withIndex()) {
                val box = layout.cellBoxes[i]
                drawRect(
                    ink.copy(alpha = BOX_ALPHA),
                    Offset(box.left.toFloat() * k, box.top.toFloat() * k),
                    Size(box.width.toFloat() * k, box.height.toFloat() * k),
                    style =
                        androidx.compose.ui.graphics.drawscope
                            .Stroke(0.6f),
                )
                val mark = glyph.character?.takeIf { it.isNotBlank() } ?: continue
                val laid = measurer.measure(mark, style)
                drawText(
                    laid,
                    topLeft =
                        Offset(
                            (box.left.toFloat() + box.width.toFloat() / 2f) * k - laid.size.width / 2f,
                            (box.top.toFloat() + box.height.toFloat() / 2f) * k - laid.size.height / 2f,
                        ),
                )
            }
        }
        BasicText("${sheet.index + 1} · ${sheet.range}".uppercase(), style = mono(muted, 9.0))
    }
}

private const val HAIRLINE = 0.25f
private const val BOX_ALPHA = 0.18f
private const val GLYPH_ALPHA = 0.6f
