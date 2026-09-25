// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * This build's own real Hyle Deco reference font ([HyleDecoProjectFontBytes]) as a real Compose
 * [FontFamily], for a screen that wants to render actual text set in it
 * (`ui/typewright-explorer.html`'s own `.hy` class -- "the user's font is the only display type
 * on screen", UI_SPEC section 2) rather than only its geometry ([com.asoc.typewright.ui.
 * GeometryInterop]'s path bridge, which every existing `.hy` consumer in this codebase so far has
 * used instead: the Overlay tab's outlines, the Anatomy Lens diagram). Built the first time a
 * screen needs real `.hy` **text**, not geometry -- [com.asoc.typewright.ui.workbook.
 * WorkbookScreen]'s own Demonstration and Gate sections (`#s-workbook`'s `.demo .dl`,
 * `.gate div .g`).
 *
 * Calls the exact same real, already-verified [platformLearnFaceFontFamily] [learnFaceFontFamily]
 * itself calls (see that function's own KDoc, "Platform status", for the full desktop/android/
 * wasmJs verification) -- but **never needs that function's own wasmJs fallback**:
 * [HyleDecoProjectFontBytes.bytes] is plain, already-in-memory common-code bytes (no
 * [com.asoc.typewright.learn.scenes.LearnFaceResources] file read, no Node `fs`), so this loads
 * for real on every KMP target `ui` builds for, including the one target [learnFaceFontFamily]
 * itself cannot reach today (that function's own KDoc: "wasmJs (browser): ... falls back to
 * FontFamily.Default").
 */
fun hyleDecoFontFamily(): FontFamily =
    runCatching {
        platformLearnFaceFontFamily(
            identity = "typewright-project:hyle-deco",
            bytes = HyleDecoProjectFontBytes.bytes,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
        )
    }.getOrDefault(FontFamily.Default)
