// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.saveStatusWord
import com.asoc.typewright.ui.sheet.Room
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.SpacingTokens
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The sheet's header (UI_SPEC §3 "Header"): "Room name as an ink block (mono, uppercase, 11 sp,
 * padding 5x9); glyph info beside it (15 sp semibold + mono small). Tap the room name -> map.
 * Horizontal swipe on the header (>= 40 dp) -> previous/next room. MAP text button top-right
 * becomes an ink block when the map is open."
 *
 * The swipe threshold is exactly UI_SPEC's own 40 dp, using `Modifier.draggable` -- a *local*,
 * single-finger gesture scoped to this one glass element, which is fine per `docs/
 * ARCHITECTURE_REVIEW.md` §4.2: the review's "not `detectTransformGestures`" warning is about the
 * *root sheet* arbiter (where a literal one-finger pan would collide with future node/letter
 * editing), not a small, dedicated header region with no other gesture meaning.
 *
 * `glyphInfoPlaceholder` and the MAP button's actual map screen are placeholders/no-ops (task
 * P4b's own scope: "Header ... Placeholder content for this task"; the map screen itself is not
 * part of the sheet per `docs/ARCHITECTURE_REVIEW.md` §5 finding 25 and is out of scope -- see
 * this task's `knownGaps`) -- except that [glyphInfoPlaceholder]'s own default is no longer always
 * the literal placeholder text: P11 WP5 (docs/PROJECT_MODEL.md §13) makes it real once a project
 * is open ([projectStatusText]), and only falls back to the placeholder when there is none, so no
 * caller needs to change to get the real status once one exists.
 */
@Composable
fun Header(
    room: Room,
    texture: CanvasTexture,
    glyphInfoPlaceholder: String = projectStatusText(),
    onSwipePrevious: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var mapOpen by remember { mutableStateOf(false) }
    var dragAccumPx by remember { mutableStateOf(0f) }
    val thresholdPx = with(density) { HEADER_SWIPE_THRESHOLD_DP.dp.toPx() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = 14.dp)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> dragAccumPx += delta },
                    onDragStopped = {
                        when {
                            dragAccumPx <= -thresholdPx -> onSwipeNext()
                            dragAccumPx >= thresholdPx -> onSwipePrevious()
                        }
                        dragAccumPx = 0f
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .background(texture.ink.toColor())
                        .clickable { mapOpen = !mapOpen }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                BasicText(text = room.name, style = Typography.headerRoomName.copy(color = texture.canvas.toColor()))
            }
            Spacer(Modifier.width(12.dp))
            BasicText(text = glyphInfoPlaceholder, style = Typography.headerGlyphInfo.copy(color = texture.fg.toColor()))
        }
        Box(
            modifier =
                Modifier
                    .let { if (mapOpen) it.background(texture.ink.toColor()) else it }
                    .clickable { mapOpen = !mapOpen }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            BasicText(
                text = "MAP",
                style = Typography.mono(sizeSp = 11.0).copy(color = if (mapOpen) texture.canvas.toColor() else texture.fg.toColor()),
            )
        }
    }
}

/**
 * [Header]'s own mono status slot: `"<name> · saved/saving/in this tab only"` for the open
 * project (docs/PROJECT_MODEL.md §13's palette table), read from [LocalProjectWorkspace] --
 * [HEADER_NO_PROJECT_PLACEHOLDER] when there is none (no [LocalProjectWorkspace] provided at all,
 * or one provided with no project open, which is every existing screenshot test and every render
 * before P11 WP5). Draw and Space still show sample data regardless (P12/P17's own job); only this
 * one text slot is real today.
 */
@Composable
private fun projectStatusText(): String {
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value ?: return HEADER_NO_PROJECT_PLACEHOLDER
    val name =
        session.state
            .collectAsState()
            .value.meta.manifest.name
    val status = session.saveStatus.collectAsState().value
    return "$name · ${saveStatusWord(status)}"
}

/** [Header]'s own text before P11 WP5, and still its text with no project open. */
private const val HEADER_NO_PROJECT_PLACEHOLDER = "-- pts"

private const val HEADER_SWIPE_THRESHOLD_DP = 40.0
