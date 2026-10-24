// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.report.ECONOMY_FENCE_SENTENCE
import com.asoc.typewright.qa.report.EconomyRow
import com.asoc.typewright.qa.report.EconomySort
import com.asoc.typewright.qa.report.economyLegend
import com.asoc.typewright.qa.report.faceSpread
import com.asoc.typewright.qa.report.shape
import com.asoc.typewright.qa.report.withThousands
import com.asoc.typewright.qa.report.word
import com.asoc.typewright.ui.project.GlyphPathCache
import com.asoc.typewright.ui.project.NoProjectState
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor

private const val GLYPH_COLUMN_DP = 28
private const val TAG_COLUMN_DP = 64
private const val ROW_HEIGHT_DP = 36
private const val OPEN_PLOT_HEIGHT_DP = 66
private const val OPEN_CENTER_DP = 13
private const val FACES_TOP = 26.0
private const val FACES_BOTTOM = 56.0
private const val DECK_CLEARANCE_DP = 200
private const val CHIP_FADE_DP = 28
private const val CHIP_FADE_HEIGHT_DP = 30

/** What the Economy page needs, all of it read from the project and the corpus by the screen and handed down. */
internal class EconomyPageModel(
    val font: ProjectFont,
    val corpus: NodeEconomyCorpus,
    val chips: List<StyleChip>,
    /** The class being compared against, or null when none is chosen yet. */
    val selectedKey: String?,
    /** One sentence saying which class the glyphs are judged against and where it came from, or null for none. */
    val classNote: String?,
    val rows: List<EconomyRow>,
    val sort: EconomySort,
    /** The glyph whose row is open, or null. */
    val open: String?,
    val faceCount: Int,
)

/**
 * Economy (S15): every glyph of the open project as a box plot against the faces of one style class, on a log
 * axis that stays at the top while the rows scroll. Each row is the glyph drawn from the project's own outlines, the
 * box (middle half, median, whiskers over the full range), the person's count as the accent point, and the count
 * and the verdict as a shape and a word. A tap opens a row to show the faces behind its box, the fewest and the
 * most named. The class chips and the sort order are above; the legend and the fence sentence say what the marks mean.
 */
@Composable
internal fun EconomyPage(
    texture: CanvasTexture,
    model: EconomyPageModel,
    onStyle: (String) -> Unit,
    onSort: (EconomySort) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = texture.muted.toColor()
    val paths = remember(model.font) { GlyphPathCache() }
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // One line that scrolls sideways, fading out at the right edge as the explorer's does, so ten classes never push the rows down.
            Box {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(end = CHIP_FADE_DP.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (chip in model.chips) {
                        SegmentChip(chip.text, chip.selected, texture, onClick = { onStyle(chip.key) })
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(CHIP_FADE_DP.dp)
                            .height(CHIP_FADE_HEIGHT_DP.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, texture.canvas.toColor()))),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (sort in EconomySort.entries) {
                    SegmentChip(sort.label, model.sort == sort, texture, onClick = { onSort(sort) })
                }
            }
            model.classNote?.let { BasicText(text = it, style = sentenceStyle(muted, size = 11.5)) }
            if (model.selectedKey !=
                null
            ) {
                BasicText(text = economyLegend(model.faceCount), style = labelStyle(muted, size = 8.5, tracking = 0.06))
            }
        }
        if (model.selectedKey != null) {
            Row(modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp)) {
                Spacer(Modifier.width(GLYPH_COLUMN_DP.dp))
                BoxPlotAxis(texture, Modifier.weight(1f))
                Spacer(Modifier.width(TAG_COLUMN_DP.dp))
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (model.selectedKey == null) {
                BasicText(
                    text = "Choose a style above to compare your glyphs against its faces.",
                    style = sentenceStyle(texture.fg.toColor(), size = 13.0),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            if (model.selectedKey != null && model.rows.isEmpty()) {
                BasicText(
                    text = "None of your glyphs has a box in this style to be compared with.",
                    style = sentenceStyle(texture.fg.toColor(), size = 13.0),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            for (row in model.rows) {
                EconomyRowView(texture, model, paths, row, open = row.glyph == model.open, onTap = { onOpen(row.glyph) })
            }
            if (model.selectedKey != null) {
                BasicText(
                    text = ECONOMY_FENCE_SENTENCE,
                    style = sentenceStyle(muted, size = 11.5),
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp),
                )
            }
            Spacer(Modifier.height(DECK_CLEARANCE_DP.dp))
        }
    }
}

@Composable
private fun EconomyRowView(
    texture: CanvasTexture,
    model: EconomyPageModel,
    paths: GlyphPathCache,
    row: EconomyRow,
    open: Boolean,
    onTap: () -> Unit,
) {
    val ink = texture.ink.toColor()
    val paper = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    val spec = BoxPlotSpec.of(row.box, row.count)
    val spread = if (open) faceSpread(model.corpus, model.selectedKey.orEmpty(), row.glyph) else null
    val dots = if (spread != null) faceDots(spread, FACES_TOP, FACES_BOTTOM) else emptyList()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (open) ink.copy(alpha = OPEN_ROW_ALPHA) else Color.Transparent)
                .semantics {
                    role = Role.Button
                    stateDescription = if (open) "Showing the faces" else "Tap to show the faces"
                }.clickable(onClick = onTap)
                .padding(start = 14.dp, end = 10.dp, top = if (open) 6.dp else 0.dp, bottom = if (open) 6.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (open) Modifier else Modifier.height(ROW_HEIGHT_DP.dp)),
            verticalAlignment = if (open) Alignment.Top else Alignment.CenterVertically,
        ) {
            GlyphMark(model.font.glyph(row.glyph), model.font.unitsPerEm, paths, texture.fg.toColor())
            if (open) {
                BoxPlot(
                    spec,
                    row.glyph,
                    texture,
                    modifier = Modifier.weight(1f),
                    height = OPEN_PLOT_HEIGHT_DP.dp,
                    centerY = OPEN_CENTER_DP.dp,
                    boxHeight = 12.dp,
                    faces = dots,
                )
            } else {
                BoxPlot(spec, row.glyph, texture, modifier = Modifier.weight(1f))
            }
            Column(
                modifier = Modifier.width(TAG_COLUMN_DP.dp).padding(top = if (open) 4.dp else 0.dp),
                horizontalAlignment = Alignment.End,
            ) {
                BasicText(
                    text = row.count.withThousands(),
                    style = labelStyle(paper, size = 9.5, tracking = 0.02),
                    modifier = Modifier.background(ink).padding(horizontal = 4.dp, vertical = 1.dp),
                )
                BasicText(text = "${row.verdict.shape} ${row.verdict.word}", style = labelStyle(muted, size = 8.0, tracking = 0.02))
            }
        }
        if (spread != null) {
            Box(modifier = Modifier.padding(start = GLYPH_COLUMN_DP.dp, top = 2.dp)) {
                BasicText(text = facesCaption(spread), style = labelStyle(muted, size = 8.0, tracking = 0.04))
            }
        }
    }
}

private const val OPEN_ROW_ALPHA = 0.035f

/** What the Economy page shows when there is nothing to draw: no project, or a project with no glyphs. */
@Composable
internal fun EconomyEmpty(
    texture: CanvasTexture,
    hasProject: Boolean,
    reason: String?,
    modifier: Modifier = Modifier,
) {
    if (!hasProject) {
        NoProjectState(texture = texture, what = "your glyphs against real faces", modifier = modifier.padding(horizontal = 18.dp))
    } else {
        BasicText(
            text = reason.orEmpty(),
            style = sentenceStyle(texture.fg.toColor(), size = 13.0),
            modifier = modifier.padding(horizontal = 18.dp),
        )
    }
}
