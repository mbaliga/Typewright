// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * UI_SPEC.md §2 "Type", reproduced as [TextStyle]s. Two families only, both system fonts:
 * [FontFamily.Default] ("system UI sans ... Roboto on Android, system on desktop/web") for
 * sentences, and [FontFamily.Monospace] ("monospace (system mono)") for labels, numbers and
 * chips. UI_SPEC marks bundling Inter and JetBrains Mono `[CONFIRM]` (also
 * `docs/OPEN_QUESTIONS.md` item 9, "Madhav, with P4/P9") -- an open decision this task does not
 * make, so P4b ships system fonts only; no font files are bundled here. This is a real gap, not
 * a silent substitution: identity will differ between Android/desktop/web until that question is
 * answered (see this task's own `knownGaps`).
 *
 * "Labels, numbers, chips: ... uppercase, letter-spacing 0.14 em" (UI_SPEC §2) is two separate
 * things this file does not fully own: the 0.14em tracking is [letterSpacing] on every mono style
 * below, but the actual case transform is a call-site `.uppercase()` on the string, since Compose
 * has no CSS `text-transform` equivalent on [TextStyle].
 */
object Typography {
    /** UI_SPEC §2: "Sentences: system UI sans ... 12.5-13 sp, line-height 1.45". */
    val sentence: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = 13.sp,
            lineHeight = 13.sp * 1.45,
        )

    /**
     * UI_SPEC §2: "Labels, numbers, chips: monospace ... 9.5-11 sp, uppercase, letter-spacing
     * 0.14 em, tabular numerals." [fontFeatureSettings] requests the OpenType `tnum` feature;
     * whether the resolved system font actually has tabular-numeral glyphs is outside this app's
     * control (CLAUDE.md law 4 spirit: this is a request to the font, not a guarantee).
     */
    fun mono(
        sizeSp: Double = 9.5,
        weight: FontWeight = FontWeight.Normal,
        textAlign: TextAlign? = null,
    ): TextStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = sizeSp.sp,
            fontWeight = weight,
            letterSpacing = 0.14.em,
            fontFeatureSettings = "tnum",
            textAlign = textAlign ?: TextAlign.Unspecified,
        )

    /** UI_SPEC §3 "Header": "Room name as an ink block (mono, uppercase, 11 sp, padding 5x9)". */
    val headerRoomName: TextStyle = mono(sizeSp = 11.0)

    /** UI_SPEC §3 "Header": "glyph info beside it (15 sp semibold + mono small)". */
    val headerGlyphInfo: TextStyle = mono(sizeSp = 9.5, weight = FontWeight.SemiBold)

    /** UI_SPEC §3 "Puck": "name below in mono 9.5 sp uppercase". */
    val puckLabel: TextStyle = mono(sizeSp = 9.5, textAlign = TextAlign.Center)

    /** UI_SPEC §3 "Radial dial": "the current tool's name above the marker in mono". */
    val radialLabel: TextStyle = mono(sizeSp = 9.5, textAlign = TextAlign.Center)

    /** UI_SPEC §3 "Inspector": "label mono 9.5 sp uppercase muted". */
    val inspectorLabel: TextStyle = mono(sizeSp = 9.5)

    /**
     * UI_SPEC §3 "Inspector": "value 15 sp semibold tabular" -- semibold and larger than the
     * other mono tokens, but still monospace/tabular per the same paragraph.
     */
    val inspectorValue: TextStyle = mono(sizeSp = 15.0, weight = FontWeight.SemiBold)

    /** UI_SPEC §3 "Edge marks": "22 sp, ink at 30%". Not mono in the spec (a plain glyph, `<`/`>`). */
    val edgeMark: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, textAlign = TextAlign.Center)

    /** A large placeholder room label for [com.asoc.typewright.ui.sheet]'s world ink pass (P5b replaces this with real glyph content). */
    val roomPlaceholder: TextStyle = mono(sizeSp = 44.0, weight = FontWeight.Bold, textAlign = TextAlign.Center)
}
