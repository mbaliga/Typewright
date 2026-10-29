// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.learn.scenes.LearnFaceResources
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.tokens.MeaningColors

/**
 * The word the Overlay tab's word field shows by default (`ui/typewright-explorer.html`'s
 * `#ln-ov` `.wordf`: "Hamburg"), reproduced verbatim (CLAUDE.md law 6).
 */
const val OVERLAY_DEFAULT_WORD = "Hamburg"

/**
 * The Overlay tab's layers (TYPEWRIGHT_BUILD_BRIEF.md section 9: "the comparison overlay"): the open
 * project's own font ([project], the "ink" layer, labelled with its family name) plus two of the
 * real fetched Learn faces (`data/learn-faces`, via [LearnFaceResources]), EB Garamond and Libre
 * Baskerville, matching `ui/typewright-explorer.html`'s own worked Overlay example (`#ln-ov`'s own
 * third layer is literally "Libre Baskerville"). No "device font" layer: the explorer's third static
 * layer ("Roboto ... device") would need the device's font list, which this build has no way to read
 * (CLAUDE.md law 4: a device font is a real gap, not something to fake with a bundled substitute
 * wearing that label).
 *
 * Wrapped in [runCatching]: reading the Learn faces can throw for a real reason on some target
 * (`LearnFaceResources.fontBytes`'s wasmJs actual needs Node's `fs`, which a browser lacks; see
 * `LearnFaceFonts.kt`). [OverlayTab] then shows an honest "not available on this target" note
 * instead of crashing.
 */
fun loadDefaultOverlayLayers(project: ProjectFont): Result<List<OverlayLayer>> =
    runCatching {
        val own =
            buildProjectOverlayLayer(
                font = project,
                id = PROJECT_LAYER_ID,
                label = project.ufo.fontInfo.familyName ?: "Your font",
                sourceLabel = "project",
                chars = OVERLAY_DEFAULT_WORD,
            )
        val garalde =
            buildOverlayLayer(
                font = readSfntFont(LearnFaceResources.fontBytes("garalde")),
                id = "garalde",
                label = "EB Garamond",
                sourceLabel = "Google Fonts",
                dashPattern = OverlayDashPatterns.AMBER,
                meaningColor = MeaningColors.AMBER,
                chars = OVERLAY_DEFAULT_WORD,
            )
        val transitional =
            buildOverlayLayer(
                font = readSfntFont(LearnFaceResources.fontBytes("transitional")),
                id = "transitional",
                label = "Libre Baskerville",
                sourceLabel = "Google Fonts",
                dashPattern = OverlayDashPatterns.MAGENTA,
                meaningColor = MeaningColors.MAGENTA,
                chars = OVERLAY_DEFAULT_WORD,
            )
        listOf(own, garalde, transitional)
    }

/**
 * Dash intervals (dp; [androidx.compose.ui.graphics.PathEffect.dashPathEffect] units), one per
 * comparison layer -- `ui/typewright-explorer.html`'s own `.lay.l2`/`.lay.l3` values reproduced
 * exactly (CLAUDE.md law 6), even though this file's own layers no longer line up 1:1 with the
 * explorer's `l2`/`l3` ids (see [loadDefaultOverlayLayers]'s own KDoc for why there is no device
 * layer here): `9f, 5f` for the AMBER-family layer, `2f, 4f` for the MAGENTA-family one.
 */
object OverlayDashPatterns {
    val AMBER = floatArrayOf(9f, 5f)
    val MAGENTA = floatArrayOf(2f, 4f)
}
