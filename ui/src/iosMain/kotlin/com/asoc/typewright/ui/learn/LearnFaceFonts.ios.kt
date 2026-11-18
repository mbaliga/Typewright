// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * `true`: the face bytes come from the app bundle (`readLearnFaceResourceBytes`'s iOS actual) and
 * [platformLearnFaceFontFamily] hands them to Compose. Not yet seen on a device or simulator
 * (docs/IOS_PORT.md, evidence CODE-ONLY); [learnFaceFontFamily] already falls back to
 * [FontFamily.Default] if loading throws, so a wrong `true` degrades rather than crashes.
 */
public actual fun learnFaceFontsAreReal(): Boolean = true

/**
 * The same `androidx.compose.ui.text.platform.Font(identity, data, weight, style)` call desktop
 * and wasmJs make: it lives in Compose Multiplatform's shared Skiko source set, which iOS also
 * compiles. Believed present in the iOS klib, not inspected here (no Kotlin/Native toolchain
 * target for Apple on this host); the macOS CI lane's compile is the first check.
 */
internal actual fun platformLearnFaceFontFamily(
    identity: String,
    bytes: ByteArray,
    weight: FontWeight,
    style: FontStyle,
): FontFamily = FontFamily(Font(identity, bytes, weight, style))
