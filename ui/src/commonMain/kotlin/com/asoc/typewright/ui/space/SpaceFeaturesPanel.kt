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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.compile.FeaCheckResult
import com.asoc.typewright.compile.FeaChecker
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.delay

/**
 * The editor for the project's `features.fea`: plain monospaced text with line numbers, nothing highlighted, saved only
 * when the person says so ([onSave] answers how that went; an empty text removes the file). The text being typed lives in
 * [state], so it outlives the editor and is shown again, marked as kept and not saved, if the page was left without
 * saving. Nothing is thrown away unless the person says so, and a save the project refuses keeps what was typed.
 *
 * It says what has been checked and by what ([FeaReport]): the font's own parser, fontTools' feaLib in the Python the build
 * uses, reads the text with the font's glyph names ([glyphNames]) and its findings are marked in the margin by line; where
 * [checker] has none to run, the quick shape check of [FeaCheck] is shown instead, labelled as that, and the page says the
 * language itself was not parsed.
 */
@Composable
internal fun SpaceFeaturesPanel(
    saved: String?,
    state: SpaceRoomState,
    texture: CanvasTexture,
    editable: Boolean,
    glyphNames: List<String>,
    checker: FeaChecker,
    onSave: (String?) -> SpaceOutcome,
    modifier: Modifier = Modifier,
) {
    val draft = state.featuresDraft
    val text = draft ?: saved.orEmpty()
    val dirty = draft != null && draft != saved.orEmpty()
    LaunchedEffect(dirty) { state.featuresDirty = dirty }
    // The parser is asked once typing pauses, not at every key, and its answer replaces the quick check's when it arrives. An
    // answer is kept with the text it is about, so it is never shown against text that has changed since.
    var answer by remember { mutableStateOf<Pair<String, FeaCheckResult>?>(null) }
    val never = checker.neverReads
    LaunchedEffect(text, glyphNames, checker) {
        if (never == null) {
            delay(CHECK_PAUSE_MS)
            answer = text to checker.check(text, glyphNames)
        }
    }
    // Where no parser will ever run there is nothing to wait for, so the page never says one is reading.
    val parsed = if (never != null) FeaCheckResult.Unavailable(never) else answer?.takeIf { (about, _) -> about == text }?.second
    val report = remember(text, parsed) { FeaReport.of(text, parsed) }
    val fg = texture.fg.toColor()
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = fg)
    val lineCount = text.count { it == '\n' } + 1
    val marked = report.markedLines
    val margin =
        buildAnnotatedString {
            for (line in 1..lineCount) {
                if (line > 1) append("\n")
                if (line in marked) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = fg)) { append("● $line") }
                } else {
                    append(line.toString())
                }
            }
        }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpaceSentence(
            text =
                if (saved == null) {
                    "This project has no features.fea yet. What you type and save here becomes it."
                } else {
                    "features.fea, as the project holds it."
                },
            texture = texture,
            muted = true,
        )
        if (state.draftKept && dirty) {
            SpaceSentence(
                text =
                    "This is the text you had typed when you left. It was kept while this project is open. " +
                        "It is not in the project until you press Save.",
                texture = texture,
                bold = true,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            BasicText(
                text = margin,
                style = mono.copy(color = texture.muted.toColor(), textAlign = TextAlign.End),
                modifier = Modifier.width(MARGIN_DP.dp).padding(end = 8.dp),
            )
            BasicTextField(
                value = text,
                onValueChange = { if (editable) state.featuresDraft = it },
                readOnly = !editable,
                textStyle = mono,
                cursorBrush = SolidColor(fg),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            SpaceAction("Save", texture, enabled = editable && dirty) {
                // Only what the project took leaves the editor: a refused save keeps every character that was typed.
                if (onSave(draft?.takeIf { it.isNotEmpty() }).went) state.discardDraft()
            }
            SpaceAction("Put back what is saved", texture, enabled = dirty) { state.discardDraft() }
        }
        if (dirty) SpaceLabel("Not saved yet", texture)
        if (text.isEmpty() && saved != null) {
            SpaceSentence("Saving an empty text removes features.fea from the project.", texture, muted = true)
        }
        if (FEA_OWN_KERN.containsMatchIn(text)) {
            SpaceSentence(
                text =
                    "This file writes its own kern feature. fontmake, which builds the font, then leaves out the pairs you set on " +
                        "the Kerning page. Put # Automatic Code inside the feature where they should go to keep both.",
                texture = texture,
            )
        }
        SpaceLabel(report.label, texture)
        if (report.findings.isEmpty()) {
            SpaceSentence("✓ pass · nothing wrong with what was checked.", texture)
        } else {
            for (finding in report.findings) SpaceSentence("● fail · ${finding.text()}", texture, bold = true)
        }
        SpaceSentence(report.limits, texture, muted = true)
    }
}

private val FEA_OWN_KERN = Regex("""(?m)^\s*feature\s+kern\b""")
private const val CHECK_PAUSE_MS = 400L
private const val MARGIN_DP = 44
