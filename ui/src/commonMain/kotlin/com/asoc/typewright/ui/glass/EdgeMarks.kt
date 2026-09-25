// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * Edge marks (UI_SPEC §3 "Edge marks"): "< > at mid-height, 22 sp, ink at 30%; hidden at the ends
 * of the row and in map mode." `showPrevious`/`showNext` are the caller's own "at the ends of the
 * row" test ([com.asoc.typewright.ui.sheet.Room.ORDERED]'s first/last); "map mode" does not
 * apply here since this task does not build the map screen (see this task's `knownGaps`).
 */
@Composable
fun EdgeMarks(
    showPrevious: Boolean,
    showNext: Boolean,
    texture: CanvasTexture,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val color = texture.ink.toColor().copy(alpha = 0.30f)
    Box(modifier = modifier.fillMaxSize()) {
        if (showPrevious) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).clickable { onPrevious() },
            ) {
                BasicText(text = "‹", style = Typography.edgeMark.copy(color = color))
            }
        }
        if (showNext) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).clickable { onNext() },
            ) {
                BasicText(text = "›", style = Typography.edgeMark.copy(color = color))
            }
        }
    }
}
