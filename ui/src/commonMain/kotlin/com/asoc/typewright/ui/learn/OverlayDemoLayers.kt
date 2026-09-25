// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.learn.scenes.LearnFaceResources
import com.asoc.typewright.ui.tokens.MeaningColors

/**
 * The word the Overlay tab's word field shows by default (`ui/typewright-explorer.html`'s
 * `#ln-ov` `.wordf`: "Hamburg"), reproduced verbatim (CLAUDE.md law 6). Confirmed covered by
 * every one of [loadDefaultOverlayLayers]'s three real fonts' own `cmap` before picking it
 * (`python3`/fontTools, checked while building this file): Hyle Deco, EB Garamond and Libre
 * Baskerville all map every letter in "Hamburg".
 */
const val OVERLAY_DEFAULT_WORD = "Hamburg"

/**
 * The Overlay tab's three real, default layers (TYPEWRIGHT_BUILD_BRIEF.md section 9: "the
 * comparison overlay"; task P6's own instruction, "at least 2-3 real layers"): this build's own
 * project font (Hyle Deco, via [HyleDecoProjectFontBytes] -- see that file's own KDoc for why it
 * is embedded rather than read off disk) plus two of the seventeen real fetched Learn faces
 * (`data/learn-faces`, via [LearnFaceResources] -- `learn:scenes`, already built and tested by a
 * sibling P6 task), EB Garamond and Libre Baskerville, matching
 * `ui/typewright-explorer.html`'s own worked Overlay example (`#ln-ov`'s own third layer is
 * literally "Libre Baskerville"). No "device font" layer: the explorer's own third static layer
 * ("Roboto ... device") would need this container's actual device font list, which does not
 * exist here (CLAUDE.md law 4 -- this container has no phone; "device" fonts are a real gap, not
 * something to fake with a bundled substitute wearing that label).
 *
 * Wrapped in [runCatching]: every one of these reads can throw for a real, disclosed reason on
 * some target (`LearnFaceResources.fontBytes`'s own wasmJs actual needs Node's `fs`, which does
 * not exist in a browser -- the exact gap `ui.learn.LearnFaceFonts.kt`'s own KDoc already
 * discloses for [com.asoc.typewright.ui.learn.learnFaceFontsAreReal]'s own "false on wasmJs"
 * case; `ui`'s own wasmJs target runs its tests in a real browser, not Node, per this module's
 * `build.gradle.kts` own comment). [OverlayTab] shows an honest "not available on this target"
 * note instead of crashing when this fails, the same convention.
 */
fun loadDefaultOverlayLayers(): Result<List<OverlayLayer>> =
    runCatching {
        val project =
            buildOverlayLayer(
                font = readSfntFont(HyleDecoProjectFontBytes.bytes),
                id = PROJECT_LAYER_ID,
                label = "Hyle Deco",
                sourceLabel = "project",
                dashPattern = null,
                meaningColor = null,
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
        listOf(project, garalde, transitional)
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
