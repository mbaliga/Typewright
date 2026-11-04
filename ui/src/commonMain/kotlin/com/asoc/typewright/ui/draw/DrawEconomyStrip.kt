// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.project.ProjectManifest
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.qa.corpus.verdictFor
import com.asoc.typewright.ui.check.BoxPlot
import com.asoc.typewright.ui.check.BoxPlotSpec
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectStyle
import com.asoc.typewright.ui.project.StyleOrigin
import com.asoc.typewright.ui.project.projectStyle
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * What the thin strip under the Draw header says about the open glyph's node count: the glyph judged against the
 * corpus's box for its style class, or the plain reason it cannot be. The class and the number of faces behind the box
 * are always named; a class the app read off the letters rather than being told is called our heuristic (law 5).
 */
sealed interface EconomyStrip {
    /** The glyph's count sits against a measured box. */
    data class Judged(
        val spec: BoxPlotSpec,
        val verdict: NodeEconomyVerdict,
        val line: String,
        val classLine: String,
    ) : EconomyStrip

    /** There is nothing to judge, and [line] says why. */
    data class Unjudged(
        val line: String,
    ) : EconomyStrip

    companion object {
        /**
         * The strip for [glyph] given the project's [style] and the [corpus] (null on a target that cannot read it). The
         * count is the one the Check screen judges: on-curve points as the file is written.
         */
        fun of(
            glyph: Glyph,
            style: ProjectStyle?,
            corpus: NodeEconomyCorpus?,
        ): EconomyStrip {
            if (glyph.contours.isEmpty()) return Unjudged("${glyph.name} · nothing drawn yet")
            if (corpus == null) return Unjudged("Node economy: this build cannot read the measured faces")
            if (style == null) return Unjudged("Node economy: no style class yet. Declare one in the brief, or draw more letters.")
            val box =
                corpus.onCurveBox(style.key, glyph.name)
                    ?: return Unjudged("No measured box for ${glyph.name} in ${style.name}")
            val count = glyph.count().onCurveEquivalent
            val verdict = verdictFor(count, box)
            val word =
                when (verdict) {
                    NodeEconomyVerdict.OUTLIER -> "● outlier"
                    NodeEconomyVerdict.ABOVE -> "◐ above the middle half"
                    NodeEconomyVerdict.IN_RANGE -> "✓ in range"
                }
            val origin = if (style.isHeuristic) " · class read from your letters: our heuristic" else " · ${style.provenance}"
            return Judged(
                spec = BoxPlotSpec.of(box, count),
                verdict = verdict,
                line = "${glyph.name} · $count on-curve · $word",
                classLine = "${style.name}, n = ${box.n} faces$origin",
            )
        }

        /** The style the manifest states outright (confirmed, then declared), or null; cheap, so it can be read on every edit. */
        fun statedStyle(manifest: ProjectManifest): ProjectStyle? {
            val brief = manifest.brief.styleClass
            brief.confirmed?.let { return ProjectStyle(it, StyleOrigin.CONFIRMED) }
            brief.declared?.let { return ProjectStyle(it, StyleOrigin.DECLARED) }
            return null
        }
    }
}

/** How long the project must stay still before the style class is read off its letters: that reading measures many glyphs and is not done on each frame of a drag. */
private const val STYLE_SETTLE_MILLIS = 300L

/**
 * The style class the open project is judged against: the stated one at once, else the one read from its letters once
 * the project has been still for a moment (computed off the UI thread). Null until there is one.
 */
@OptIn(FlowPreview::class)
@Composable
private fun rememberDrawStyle(session: ProjectSession): ProjectStyle? {
    val state by session.state.collectAsState()
    val stated = EconomyStrip.statedStyle(state.meta.manifest)
    val read by produceState<ProjectStyle?>(null, session) {
        session.state
            .debounce(STYLE_SETTLE_MILLIS)
            .map { settled ->
                EconomyStrip.statedStyle(settled.meta.manifest) ?: settled.font.let { font ->
                    val master = font.masters.firstOrNull { it.isDefault } ?: font.masters.firstOrNull()
                    master?.let { projectStyle(settled.meta.manifest, ProjectFont.of(it)) }
                }
            }.flowOn(Dispatchers.Default)
            .collect { value = it }
    }
    return stated ?: read
}

/** The strip's height on the sheet, in dp. */
const val ECONOMY_STRIP_DP: Double = com.asoc.typewright.ui.sheet.SheetLayout.ECONOMY_STRIP_DP

/**
 * The glyph's live box plot, thin, under the header (S11): its count against the style class's measured box on the
 * shared log axis, with the class and the number of faces named beside it. It follows every edit, a drag included.
 */
@Composable
fun DrawEconomyStrip(
    room: DrawRoom,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val controller = room.controller ?: return
    val session = room.session ?: return
    val state by session.state.collectAsState()
    val glyph = state.font.glyph(controller.editor.ref) ?: return
    val style = rememberDrawStyle(session)
    val strip = remember(glyph, style) { EconomyStrip.of(glyph, style, ProjectDataPacks.corpus) }
    val muted = texture.muted.toColor()
    val fg = texture.fg.toColor()
    Row(
        modifier = modifier.fillMaxWidth().height(ECONOMY_STRIP_DP.dp).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (strip) {
            is EconomyStrip.Judged -> {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(text = strip.line, style = Typography.mono(sizeSp = 9.0).copy(color = fg, letterSpacing = 0.04.em))
                    BasicText(text = strip.classLine, style = Typography.mono(sizeSp = 8.0).copy(color = muted, letterSpacing = 0.02.em))
                }
                BoxPlot(
                    spec = strip.spec,
                    glyph = glyph.name,
                    texture = texture,
                    modifier = Modifier.width(STRIP_PLOT_DP.dp),
                    height = ECONOMY_STRIP_DP.dp,
                    boxHeight = 8.dp,
                )
            }

            is EconomyStrip.Unjudged -> {
                BasicText(text = strip.line, style = Typography.mono(sizeSp = 9.0).copy(color = muted))
            }
        }
    }
}

private const val STRIP_PLOT_DP = 150
