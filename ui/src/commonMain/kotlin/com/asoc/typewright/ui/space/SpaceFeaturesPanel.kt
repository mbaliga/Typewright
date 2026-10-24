// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor

/**
 * The editor for the project's `features.fea`: plain monospaced text with line numbers, nothing
 * highlighted, saved only when the person says so ([onSave]; an empty text removes the file). It says
 * what it has checked and what it has not: [FeaCheck] looks at braces, brackets, strings and block
 * names, and nothing here parses the language, so the page says the full check happens when the font
 * is built. Whether the text is unsaved is reported to [state] so the deck's left circle becomes Close.
 */
@Composable
internal fun SpaceFeaturesPanel(
    saved: String?,
    state: SpaceRoomState,
    texture: CanvasTexture,
    editable: Boolean,
    onSave: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf<String?>(null) }
    val text = draft ?: saved.orEmpty()
    val dirty = draft != null && draft != saved.orEmpty()
    LaunchedEffect(dirty) { state.featuresDirty = dirty }
    val issues = remember(text) { FeaCheck.check(text) }
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = texture.fg.toColor())
    val lineCount = text.count { it == '\n' } + 1

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpaceSentence(
            text =
                if (saved ==
                    null
                ) {
                    "This project has no features.fea yet. What you type and save here becomes it."
                } else {
                    "features.fea, as the project holds it."
                },
            texture = texture,
            muted = true,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            BasicText(
                text = (1..lineCount).joinToString("\n"),
                style = mono.copy(color = texture.muted.toColor(), textAlign = TextAlign.End),
                modifier = Modifier.width(32.dp).padding(end = 8.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = { if (editable) draft = it },
                readOnly = !editable,
                textStyle = mono,
                cursorBrush = SolidColor(texture.fg.toColor()),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaceAction("Save", texture, enabled = editable && dirty) {
                onSave(draft?.takeIf { it.isNotEmpty() })
                draft = null
            }
            SpaceAction("Put back what is saved", texture, enabled = dirty) { draft = null }
        }
        if (dirty) SpaceLabel("Not saved yet", texture)
        if (text.isEmpty() &&
            saved != null
        ) {
            SpaceSentence("Saving an empty text removes features.fea from the project.", texture, muted = true)
        }
        if (FEA_OWN_KERN.containsMatchIn(text)) {
            SpaceSentence(
                text =
                    "This file writes its own kern feature. The build then drops the pairs you set on the Kerning page " +
                        "(ufo2ft: \"Dropping the former\"). Put # Automatic Code inside the feature where the pairs should go to use both.",
                texture = texture,
            )
        }
        SpaceLabel("Check · ${FeaCheck.COVERS}", texture)
        if (issues.isEmpty()) {
            SpaceSentence("Nothing wrong with those.", texture)
        } else {
            for (issue in issues) SpaceSentence("Line ${issue.line}: ${issue.message}", texture, bold = true)
        }
        SpaceSentence(
            "The language itself is not parsed here: the full check happens when you build, so a text that passes can still fail then.",
            texture,
            muted = true,
        )
    }
}

private val FEA_OWN_KERN = Regex("""(?m)^\s*feature\s+kern\b""")
