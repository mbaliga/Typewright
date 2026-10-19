// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.brief.Chip
import com.asoc.typewright.ui.brief.Chips
import com.asoc.typewright.ui.brief.bodyStyle
import com.asoc.typewright.ui.brief.fieldFocus
import com.asoc.typewright.ui.brief.monoStyle
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.ownsDeckKeys
import com.asoc.typewright.ui.shell.ConfirmBlock
import com.asoc.typewright.ui.shell.PageMenuItem
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.theme.Theme
import com.asoc.typewright.ui.theme.ThemeCodec
import com.asoc.typewright.ui.theme.ThemeGuardrails
import com.asoc.typewright.ui.theme.ThemeProblem
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.ThemeGroup
import com.asoc.typewright.ui.tokens.ThemeRole
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

/**
 * The custom theme editor (S22). A live [ThemePreview] at the top, then every role from SCREENS_V1
 * §3.1 as a row: a swatch, its name, its hex, and the shape that always goes with it. The guardrail
 * messages sit under the row they are about, in words and with a shape (never red); the ✓ circle
 * stays dimmed, and says why on a tap, until every collision is resolved. The left circle is Close
 * (with an ink-block confirmation) while there are unsaved changes, otherwise Back. The editor itself
 * is drawn in the theme being edited, so the preview and the page agree.
 *
 * ⋯ copies the theme as JSON and pastes one in, which is how a theme is exported, imported and
 * shared (law 7).
 */
@Composable
fun ThemeEditorScreen(
    state: ThemeEditorState,
    section: DeckSection,
    onSave: (Theme) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texture = state.draft.toTexture()
    var confirmDiscard by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val blocked = state.blockedBecause
    Box(modifier = modifier.fillMaxSize()) {
        SectionScaffold(
            texture = texture,
            section = section,
            title = "Theme editor",
            subtitle = if (state.original.builtIn) "A copy of ${state.original.name}" else state.original.name,
            left = if (state.dirty) DeckLeft.CLOSE else DeckLeft.BACK,
            onBack = { if (state.dirty) confirmDiscard = true else onLeave() },
            right = DeckRight.CHECK,
            rightDimmed = blocked != null,
            rightLabel = "Save theme",
            rightExplanation = blocked,
            onRight = { onSave(state.draft) },
            dimSections = true,
            dimReason = "Save or close the theme editor first",
            menuItems =
                listOf(
                    PageMenuItem("Copy theme", "As JSON, to share") {
                        clipboard.setText(AnnotatedString(state.export()))
                        message = "Copied."
                    },
                    PageMenuItem("Paste theme", "From JSON on the clipboard") {
                        message = clipboard
                            .getText()
                            ?.text
                            ?.let(state::import)
                            ?.let { "Not imported: $it" } ?: "Pasted."
                    },
                ),
        ) {
            EditorBody(state = state, texture = texture, message = message)
        }
        if (confirmDiscard) {
            ConfirmBlock(
                texture = texture,
                message = "Discard your changes to this theme?",
                confirmLabel = "Discard",
                cancelLabel = "Keep editing",
                onConfirm = onLeave,
                onCancel = { confirmDiscard = false },
            )
        }
    }
}

@Composable
private fun EditorBody(
    state: ThemeEditorState,
    texture: CanvasTexture,
    message: String?,
) {
    val problems = state.problems
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemePreview(texture = texture)
        Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (message != null) BasicText(text = message, style = bodyStyle(texture.muted.toColor()))
            NameField(state = state, texture = texture)
            for (group in ThemeGroup.entries) {
                GroupLabel(text = group.title, texture = texture)
                if (group == ThemeGroup.GROUND) GroundControls(state = state, texture = texture)
                for (role in ThemeRole.entries.filter { it.group == group }) {
                    RoleRow(state = state, role = role, texture = texture, problems = ThemeGuardrails.forRole(problems, role))
                }
            }
            Text12Link(label = "Reset theme", texture = texture, enabled = state.dirty) { state.resetTheme() }
        }
    }
}

@Composable
private fun GroupLabel(
    text: String,
    texture: CanvasTexture,
) {
    BasicText(text = text.uppercase(), style = monoStyle(texture.muted.toColor()), modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun NameField(
    state: ThemeEditorState,
    texture: CanvasTexture,
) {
    val fg = texture.fg.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(text = "NAME", style = monoStyle(texture.muted.toColor()))
        Box(modifier = Modifier.fillMaxWidth().fieldFocus(texture.accent.toColor()).padding(vertical = 4.dp)) {
            BasicTextField(
                value = state.draft.name,
                onValueChange = state::rename,
                textStyle = bodyStyle(fg, sizeSp = 15.0),
                singleLine = true,
                cursorBrush = SolidColor(fg),
                modifier = Modifier.fillMaxWidth().ownsDeckKeys(),
            )
        }
    }
}

/** Grain, grid opacity and whether the chrome is light or dark. */
@Composable
private fun GroundControls(
    state: ThemeEditorState,
    texture: CanvasTexture,
) {
    val draft = state.draft
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text = "CHROME", style = monoStyle(texture.muted.toColor()), modifier = Modifier.width(LABEL_WIDTH.dp))
        Chips {
            Chip(label = "Light", on = !draft.dark, texture = texture) { state.setDark(false) }
            Chip(label = "Dark", on = draft.dark, texture = texture) { state.setDark(true) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text = "GRAIN", style = monoStyle(texture.muted.toColor()), modifier = Modifier.width(LABEL_WIDTH.dp))
        Chips {
            for (percent in GRAIN_STEPS) {
                Chip(
                    label = "$percent%",
                    on = (draft.grain * 100).toInt() == percent,
                    texture = texture,
                ) { state.setGrain(percent / 100.0) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text = "GRID", style = monoStyle(texture.muted.toColor()), modifier = Modifier.width(LABEL_WIDTH.dp))
        Chips {
            for (percent in GRID_STEPS) {
                Chip(label = "$percent%", on = (draft.gridOpacity * 100 + 0.5).toInt() == percent, texture = texture) {
                    state.setGridOpacity(percent / 100.0)
                }
            }
        }
    }
}

private val GRAIN_STEPS = listOf(0, 10, 20, 30, 40)
private val GRID_STEPS = listOf(5, 9, 15, 20, 30)
private const val LABEL_WIDTH = 56

/** One role: a swatch, its name, its hex, the shape that goes with it, and under it anything the guardrails say. */
@Composable
private fun RoleRow(
    state: ThemeEditorState,
    role: ThemeRole,
    texture: CanvasTexture,
    problems: List<ThemeProblem>,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(20.dp).background(state.draft.color(role).toColor()))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(text = role.label, style = bodyStyle(fg, weight = FontWeight.SemiBold))
                if (role.shape != "—") BasicText(text = role.shape, style = bodyStyle(muted, sizeSp = 11.0))
            }
            Box(modifier = Modifier.width(HEX_WIDTH.dp).fieldFocus(texture.accent.toColor()).padding(vertical = 4.dp, horizontal = 2.dp)) {
                BasicTextField(
                    value = state.hexText(role),
                    onValueChange = { state.typeHex(role, it) },
                    textStyle = monoStyle(fg, sizeSp = 12.0).copy(letterSpacing = 0.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(fg),
                    modifier = Modifier.fillMaxWidth().ownsDeckKeys(),
                )
            }
        }
        for (problem in problems) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 30.dp)) {
                BasicText(text = "◐", style = bodyStyle(fg, sizeSp = 11.0))
                BasicText(text = problem.message, style = bodyStyle(fg, sizeSp = 12.0), modifier = Modifier.weight(1f))
            }
            val suggestion = problem.suggestion
            if (suggestion != null) {
                Text12Link(label = "Use ${ThemeCodec.hex(suggestion)} instead ›", texture = texture) {
                    state.setColor(role, suggestion)
                }
            }
        }
        if (state.differs(role)) Text12Link(label = "Reset role", texture = texture) { state.resetRole(role) }
    }
}

private const val HEX_WIDTH = 84

@Composable
private fun Text12Link(
    label: String,
    texture: CanvasTexture,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    BasicText(
        text = label,
        style = bodyStyle(if (enabled) texture.accent.toColor() else texture.muted.toColor(), weight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 30.dp).let { if (enabled) it.clickable(onClick = onClick) else it }.padding(vertical = 4.dp),
    )
}
