// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectText
import com.asoc.typewright.ui.project.fontVersionText
import com.asoc.typewright.ui.project.nameTableLines
import com.asoc.typewright.ui.project.nameTablePreview
import com.asoc.typewright.ui.project.threeDecimals
import com.asoc.typewright.ui.project.unitsText
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * Font info (S06): what `fontinfo.plist` of the open project says, read-only. The family, style,
 * version and units per em; the vertical metrics with the cap height picked out and the project's own
 * letters set at the line spacing those metrics give; who made it and under what licence (a name the
 * font does not state is shown as not set, with a shape, rather than filled in); and a preview of the
 * names a compiler would write. Editing these is not built, and the screen does not pretend to.
 */
@Composable
fun FontInfoScreen(
    texture: CanvasTexture,
    font: ProjectFont,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = font.ufo.fontInfo
    SectionScaffold(
        texture = texture,
        section = DeckSection.HOME,
        title = "Font info",
        subtitle = "fontinfo.plist · read-only here",
        modifier = modifier,
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Identity(texture, info, font)
            Metrics(texture, info, font)
            People(texture, info)
            NameTable(texture, info)
        }
    }
}

@Composable
private fun Identity(
    texture: CanvasTexture,
    info: UfoFontInfo,
    font: ProjectFont,
) {
    val rows =
        listOf(
            "Family" to (info.familyName ?: NOT_SET),
            "Style" to (info.styleName ?: NOT_SET),
            "Version" to (fontVersionText(info) ?: NOT_SET),
            "Units per em" to font.unitsPerEm.toString(),
        )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (pair in rows.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for ((label, value) in pair) Field(texture, label, value, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metrics(
    texture: CanvasTexture,
    info: UfoFontInfo,
    font: ProjectFont,
) {
    val ascender = info.openTypeHheaAscender ?: info.ascender
    val descender = info.openTypeHheaDescender ?: info.descender
    val lineGap = info.openTypeHheaLineGap ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(texture, "Vertical metrics")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(texture, "Ascender", ascender?.let(::unitsText) ?: NOT_SET, Modifier.weight(1f), small = true)
            Field(
                texture,
                "Cap",
                info.capHeight?.let(::unitsText) ?: NOT_SET,
                Modifier.weight(1f),
                small = true,
                ink =
                    info.capHeight != null,
            )
            Field(texture, "x-height", info.xHeight?.let(::unitsText) ?: NOT_SET, Modifier.weight(1f), small = true)
            Field(texture, "Descender", descender?.let(::unitsText) ?: NOT_SET, Modifier.weight(1f), small = true)
        }
        if (ascender != null && descender != null) {
            val total = ascender - descender + lineGap
            val em = total.toDouble() / font.unitsPerEm
            val word = font.showWord(PREVIEW_WORD)
            if (word != null) {
                ProjectText(
                    font = font,
                    text = word + "\n" + (font.showWord(PREVIEW_SECOND_LINE) ?: word),
                    sizeSp = 24f,
                    color = texture.ink.toColor(),
                    wrap = true,
                    lineHeight = em.toFloat(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            val gap = if (lineGap != 0) " + $lineGap line gap" else ""
            BasicText(
                text = "Line spacing preview at ${threeDecimals(em)} em (${unitsText(ascender)} + ${-descender}$gap).",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, color = texture.muted.toColor()),
            )
        } else {
            BasicText(
                text = "The font states no ascender or descender, so there is no line spacing to preview.",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, color = texture.muted.toColor()),
            )
        }
    }
}

@Composable
private fun People(
    texture: CanvasTexture,
    info: UfoFontInfo,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(texture, "People and licence")
        Field(texture, "Designer · ID 9", info.openTypeNameDesigner ?: "◐ Not set", small = true, valueSize = 14f)
        Field(texture, "Copyright · ID 0", info.copyright ?: "◐ Not set", small = true, valueSize = 13f)
        Field(texture, "Licence · ID 13", info.openTypeNameLicense ?: "◐ Not set", small = true, valueSize = 14f)
        info.openTypeNameLicenseURL?.let { Field(texture, "Licence URL · ID 14", it, small = true, valueSize = 13f) }
    }
}

@Composable
private fun NameTable(
    texture: CanvasTexture,
    info: UfoFontInfo,
) {
    val rows = nameTablePreview(info)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(texture, "Name table")
        if (rows.isEmpty()) {
            BasicText(
                text = "The font has no family name, so there are no names to write.",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, color = texture.muted.toColor()),
            )
        } else {
            for (line in nameTableLines(rows)) {
                BasicText(
                    text = line,
                    style = Typography.mono(sizeSp = 11.0).copy(color = texture.fg.toColor(), lineHeight = 18.7.sp, letterSpacing = 0.sp),
                )
            }
            BasicText(
                text = "What the keys above say. Typewright does not build a font file yet, so this is a preview, not a build.",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, color = texture.muted.toColor()),
            )
        }
    }
}

@Composable
private fun Label(
    texture: CanvasTexture,
    text: String,
    small: Boolean = false,
) {
    BasicText(
        text = text.uppercase(),
        style = Typography.mono(sizeSp = if (small) 8.5 else 9.5).copy(color = texture.muted.toColor(), letterSpacing = 0.14.em),
    )
}

/** A mono label above a value; [ink] sets the value in an ink block (the cap height's emphasis). */
@Composable
private fun Field(
    texture: CanvasTexture,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    ink: Boolean = false,
    valueSize: Float = 15f,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(texture, label, small)
        val weight = if (valueSize >= 15f) FontWeight.SemiBold else FontWeight.Normal
        BasicText(
            text = value,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = valueSize.sp,
                    lineHeight = (valueSize * 1.4f).sp,
                    fontWeight = weight,
                    color = if (ink) texture.canvas.toColor() else texture.fg.toColor(),
                ),
            modifier = if (ink) Modifier.background(texture.ink.toColor()).padding(horizontal = 4.dp) else Modifier,
        )
    }
}

private const val NOT_SET = "—"
private const val PREVIEW_WORD = "Hamburgefonstiv"
private const val PREVIEW_SECOND_LINE = "quartz judge vow"
