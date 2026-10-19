// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.asoc.typewright.brief.CueFace
import com.asoc.typewright.ui.learn.platformLearnFaceFontFamily
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The real typefaces the Brief's questions are asked in, as Compose font families. Each cue face
 * ships inside the app as base64 text ([BRIEF_CUE_FONT_BASE64]), so the specimen a person sees
 * for an answer is the font that answer was measured on, on every target, with no file to fetch.
 * The bytes go through the same per-platform loader the Learn screen's faces use.
 *
 * A face that cannot be built falls back to [FontFamily.Default], so a specimen is never missing,
 * only set in the wrong face; nothing here is cached across failures.
 */
internal object BriefCueFonts {
    private val families = HashMap<String, FontFamily>()

    /** The family for [face]. */
    fun familyFor(face: CueFace): FontFamily = familyForKey(face.key)

    /** The family for the cue key [key] (a [CueFace.key]). */
    @OptIn(ExperimentalEncodingApi::class)
    fun familyForKey(key: String): FontFamily {
        families[key]?.let { return it }
        val encoded = BRIEF_CUE_FONT_BASE64[key] ?: return FontFamily.Default
        val built =
            runCatching {
                platformLearnFaceFontFamily(
                    identity = "typewright-brief-cue:$key",
                    bytes = Base64.decode(encoded),
                    weight = FontWeight.Normal,
                    style = FontStyle.Normal,
                )
            }.getOrNull() ?: return FontFamily.Default
        families[key] = built
        return built
    }
}
