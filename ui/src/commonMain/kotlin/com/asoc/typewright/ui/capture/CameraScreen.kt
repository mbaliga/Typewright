// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asoc.typewright.engine.trace.capture.ImageCorner
import com.asoc.typewright.engine.trace.capture.findCornerMarks
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.deck.SystemBackHandler
import com.asoc.typewright.ui.shell.ScreenHeader
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** Shown where there is no camera to use, so the person is told why and what to do instead (law 4). */
internal const val NO_CAMERA_HERE: String =
    "Camera capture is only in the Android app. Here, add a photograph or a scan with the plus button's File choice, " +
        "or drop one on the window."

/**
 * Camera (S09): a full-bleed viewfinder, four corner marks that fill in as the camera locks each of the sheet's black squares, a
 * mono status line, and a shutter that arms when all four are locked. A long press on the shutter turns the limits off for one
 * photograph (the manual override), for a sheet the detector cannot find though it is plainly there.
 *
 * The picture itself comes from the platform's [CameraFeed]; everything else here, including finding the corner marks in the
 * preview, is shared code that runs the capture engine's detector, throttled by [CameraLockTracker]. On a device the camera
 * is owner-verified only: this container has no phone (CLAUDE.md law 4).
 */
@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onPhoto: (bytes: ByteArray, manual: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val provider = PlatformCaptureSupport.camera
    val access = provider?.rememberAccess() ?: CameraAccess.Unavailable(NO_CAMERA_HERE)
    CameraFrame(access = access, onClose = onClose, onPhoto = onPhoto, modifier = modifier)
}

/** [CameraScreen] over a given [access], so a test can give it a fake feed. */
@Composable
fun CameraFrame(
    access: CameraAccess,
    onClose: () -> Unit,
    onPhoto: (bytes: ByteArray, manual: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tracker: CameraLockTracker = remember { CameraLockTracker() },
) {
    val texture = CameraTexture
    val ink = texture.ink.toColor()
    val muted = texture.muted.toColor()
    var flash by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(true) }
    var manual by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var shooting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val feed = (access as? CameraAccess.Ready)?.feed
    SystemBackHandler(enabled = true) { onClose() }
    if (feed != null) {
        DisposableEffect(feed) {
            val clock = TimeSource.Monotonic.markNow()
            feed.analyse { frame ->
                if (tracker.dueAt(clock.elapsedNow().inWholeMilliseconds)) {
                    // The search runs here, on the camera's thread; the tracker and the overlay are only touched on the UI thread.
                    val search = findCornerMarks(frame)
                    scope.launch {
                        tracker.offer(search)
                        version += 1
                    }
                }
            }
            onDispose {
                feed.analyse(null)
                tracker.reset()
            }
        }
        DisposableEffect(feed, flash) {
            feed.setFlash(flash)
            onDispose { }
        }
    }
    // Read so the overlay and the status line follow the tracker.
    @Suppress("UNUSED_EXPRESSION")
    version
    val armed = tracker.armed || manual
    Box(modifier.fillMaxSize().background(texture.canvas.toColor())) {
        feed?.Preview(Modifier.fillMaxSize())
        if (feed != null) {
            CornerOverlay(tracker, grid, ink, texture.canvas.toColor())
        }
        Column(Modifier.align(Alignment.TopStart)) {
            ScreenHeader(
                title = "Camera",
                subtitle =
                    when (access) {
                        is CameraAccess.Ready -> tracker.status(manual)
                        CameraAccess.Asking -> "waiting for permission"
                        is CameraAccess.Denied -> "no permission"
                        is CameraAccess.Unavailable -> "no camera"
                    },
                texture = texture,
            )
        }
        val words =
            when (access) {
                is CameraAccess.Ready -> message ?: "The shutter arms when the last corner locks · hold for manual"
                CameraAccess.Asking -> "Android is asking whether Typewright may use the camera."
                is CameraAccess.Denied -> access.message
                is CameraAccess.Unavailable -> access.message
            }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 22.dp, end = 22.dp, bottom = HINT_BOTTOM)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(text = words, style = mono(muted, 9.5).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            BasicText(
                text = CAMERA_UNVERIFIED,
                style = mono(muted.copy(alpha = 0.8f), 8.5, 0.04).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = DeckSection.CAPTURE,
            left = DeckLeft.CLOSE,
            right = DeckRight.SHUTTER,
            rightDimmed = feed == null || !armed || shooting,
            rightLabel = if (manual) "Shutter, manual" else "Shutter",
            hideSections = true,
            toggles =
                listOf(
                    DeckToggle("Flash", on = flash, enabled = feed != null, why = "There is no camera to light."),
                    DeckToggle("Grid", on = grid, groupStart = true),
                ),
            onLeft = onClose,
            onRight = {
                val camera = feed
                when {
                    camera == null -> {
                        Unit
                    }

                    !armed -> {
                        message = "Four corners first. Hold the shutter down to take it anyway."
                    }

                    shooting -> {
                        Unit
                    }

                    else -> {
                        shooting = true
                        scope.launch {
                            try {
                                val bytes = camera.takePhoto()
                                if (bytes == null) message = "The camera could not take the picture. Try again." else onPhoto(bytes, manual)
                            } finally {
                                shooting = false
                            }
                        }
                    }
                }
            },
            onRightLong = {
                if (feed != null) {
                    manual = !manual
                    message = if (manual) "Manual: the shutter is armed and the blur, light and angle limits are off." else null
                }
            },
            onToggle = { index ->
                when (index) {
                    0 -> if (feed != null) flash = !flash
                    else -> grid = !grid
                }
            },
        )
    }
}

/** Said on the Camera screen wherever it shows, because nobody has yet used it on a phone. */
internal const val CAMERA_UNVERIFIED: String =
    "The camera has not been tried on a real phone yet. If it will not lock, or the picture comes out sideways, use Gallery or File."

private val HINT_BOTTOM = 146.dp

/** The guide frame, the four corner marks (open until locked, then an ink block) and the thirds grid, over the viewfinder. */
@Composable
private fun CornerOverlay(
    tracker: CameraLockTracker,
    grid: Boolean,
    ink: androidx.compose.ui.graphics.Color,
    ground: androidx.compose.ui.graphics.Color,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameW = maxWidth - FRAME_INSET * 2
        val frameH = minOf(maxHeight - FRAME_TOP - FRAME_BOTTOM, frameW * A4_ASPECT)
        val left = FRAME_INSET + (maxWidth - FRAME_INSET * 2 - frameW) / 2
        val top = FRAME_TOP + (maxHeight - FRAME_TOP - FRAME_BOTTOM - frameH) / 2
        if (grid) {
            Box(
                Modifier
                    .offset(left, top)
                    .size(frameW, frameH)
                    .drawBehind {
                        val line = ink.copy(alpha = GRID_ALPHA)
                        for (i in 1..2) {
                            drawLine(line, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1f)
                            drawLine(line, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
                        }
                    },
            )
        }
        val corners =
            listOf(
                ImageCorner.TOP_LEFT to Offset(0f, 0f),
                ImageCorner.TOP_RIGHT to Offset(1f, 0f),
                ImageCorner.BOTTOM_LEFT to Offset(0f, 1f),
                ImageCorner.BOTTOM_RIGHT to Offset(1f, 1f),
            )
        for ((corner, at) in corners) {
            val x = left + frameW * at.x - MARK / 2
            val y = top + frameH * at.y - MARK / 2
            if (tracker.isLocked(corner)) {
                Box(Modifier.offset(x, y).size(MARK).background(ink)) {
                    Box(Modifier.align(Alignment.Center).size(MARK_CORE).background(ground))
                }
            } else {
                Box(Modifier.offset(x + 1.dp, y + 1.dp).size(MARK - 2.dp).border(1.5.dp, ink))
            }
        }
    }
}

private val FRAME_INSET: Dp = 36.dp
private val FRAME_TOP: Dp = 130.dp
private val FRAME_BOTTOM: Dp = 256.dp
private val MARK: Dp = 22.dp
private val MARK_CORE: Dp = 10.dp
private const val A4_ASPECT = 297f / 210f
private const val GRID_ALPHA = 0.28f
