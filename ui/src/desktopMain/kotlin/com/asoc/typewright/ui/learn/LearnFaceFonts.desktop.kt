// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Desktop is Skiko-backed directly: real. */
public actual fun learnFaceFontsAreReal(): Boolean = true

/**
 * `androidx.compose.ui.text.platform.Font(identity: String, data: ByteArray, weight:
 * FontWeight, style: FontStyle): Font` — confirmed against `compose-multiplatform 1.12.1`'s real
 * `ui-text-desktop-1.12.1.jar` (`javap` on `androidx/compose/ui/text/platform/PlatformFont_skikoKt.class`
 * shows the `byte[]`-taking overload of `Font-wCLgNak`/`Font-MuC2MFs`, i.e. this exact call,
 * alongside the `Function0<byte[]>`-taking one Compose's own lazy-loading resource path uses),
 * not from memory or a changelog. [LearnFaceFonts.kt]'s own KDoc has the full platform-status
 * writeup.
 */
internal actual fun platformLearnFaceFontFamily(
    identity: String,
    bytes: ByteArray,
    weight: FontWeight,
    style: FontStyle,
): FontFamily = FontFamily(Font(identity, bytes, weight, style))
