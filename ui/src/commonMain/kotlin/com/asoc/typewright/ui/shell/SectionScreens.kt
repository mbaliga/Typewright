// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/** Opens Settings (S21) over the current section; null where nothing provided it. */
val LocalOpenSettings = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * A station that is not built yet, shown inside the real deck (law 4): what it will do, and that it
 * is not built. Never hidden, never faked.
 */
@Composable
fun StubSection(
    texture: CanvasTexture,
    section: DeckSection,
    modifier: Modifier = Modifier,
) {
    val nav = LocalSectionNav.current
    SectionScaffold(
        texture = texture,
        section = section,
        title = section.label,
        subtitle = "Not built yet",
        modifier = modifier,
        onBack = { nav?.goTo?.invoke(DeckSection.HOME) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                text = STUB_TEXT.getValue(section),
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, color = texture.fg.toColor()),
            )
            BasicText(
                text = "NOT BUILT YET · NOTHING HERE RUNS",
                style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor()),
            )
        }
    }
}

private val STUB_TEXT: Map<DeckSection, String> =
    mapOf(
        DeckSection.CAPTURE to
            "Photograph your printed template sheets. Typewright will find the four corner marks, " +
            "straighten each sheet and cut it into one cell per letter.",
        DeckSection.TRACE to
            "Turn a cell's ink into outlines you can edit, with a few points where a type designer " +
            "would put them, and show every stage so you can see what it did.",
        DeckSection.CHECK to
            "Measure your letters against what Google Fonts accepts and against real fonts of your style, " +
            "as a report and as boxes with your own letters inside them.",
        DeckSection.SHIP to
            "Build the font and write the repository folder Google Fonts expects, and stop with a reason if anything fails.",
    )

/**
 * The Map (S24): every section as an ink block, a tap flies there. The deck shows only the left
 * Close, which leaves without going anywhere.
 */
@Composable
fun MapScreen(
    texture: CanvasTexture,
    current: DeckSection,
    onGo: (DeckSection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(texture.canvas.toColor())) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Map", subtitle = "Tap a section to go there", texture = texture)
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in DeckSection.entries.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (section in row) {
                            val on = section == current
                            BasicText(
                                text = section.label.uppercase(),
                                style =
                                    Typography.mono(sizeSp = 12.0).copy(
                                        color = if (on) texture.canvas.toColor() else texture.ink.toColor(),
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .background(if (on) texture.ink.toColor() else texture.ink.toColor().copy(alpha = 0.12f))
                                        .clickable { onGo(section) }
                                        .padding(horizontal = 10.dp, vertical = 28.dp),
                            )
                        }
                    }
                }
            }
        }
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = current,
            left = DeckLeft.CLOSE,
            right = DeckRight.NONE,
            dimSections = true,
            onLeft = onClose,
        )
    }
}
