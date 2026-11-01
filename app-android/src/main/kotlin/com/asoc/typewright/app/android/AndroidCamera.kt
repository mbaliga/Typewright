// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.ui.capture.CameraAccess
import com.asoc.typewright.ui.capture.CameraFeed
import com.asoc.typewright.ui.capture.CameraProvider
import com.asoc.typewright.ui.capture.LumaFrames
import com.asoc.typewright.ui.capture.PhotoRotation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * The phone's camera for Capture's Camera screen, with CameraX: a preview, an analysis stream of the brightness plane (which the
 * shared code reads for the four corner marks), and a still capture. Asks for the camera permission when the screen opens, and
 * says in words if it is refused.
 *
 * Compile-verified only. There is no phone here (CLAUDE.md law 4), so none of this has run: the owner is asked to photograph a
 * printed A4 sheet in room light, and to check the four corners lock, the shutter arms, the flash toggle lights the sheet, and
 * the picture reads as a sheet in Capture.
 */
class AndroidCameraProvider : CameraProvider {
    @Composable
    override fun rememberAccess(): CameraAccess {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        var refused by remember { mutableStateOf(false) }
        val ask =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                granted = ok
                refused = !ok
            }
        LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
        val owner = context.findActivity()
        if (!granted) {
            return if (refused) {
                CameraAccess.Denied(
                    "Android did not allow Typewright to use the camera. Allow it in the app's settings, or add a picture from the gallery or a file.",
                )
            } else {
                CameraAccess.Asking
            }
        }
        if (owner == null) return CameraAccess.Unavailable("The camera could not find the screen it belongs to.")
        val feed = remember { CameraXFeed(context, owner) }
        DisposableEffect(feed) { onDispose { feed.release() } }
        return CameraAccess.Ready(feed)
    }
}

/**
 * [jpeg] turned clockwise by [degrees] (the camera's `rotationDegrees`) and saved again, because the picture decoder ignores EXIF
 * and a sideways file would read as a sheet with its corners in the wrong order. A zero turn keeps the camera's bytes as they are;
 * a picture that cannot be decoded gives null rather than a sideways file.
 */
internal fun uprightJpeg(
    jpeg: ByteArray,
    degrees: Int,
): ByteArray? {
    val turn = PhotoRotation.normalised(degrees)
    if (turn == 0) return jpeg
    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
    val matrix = Matrix().apply { postRotate(turn.toFloat()) }
    val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    return try {
        ByteArrayOutputStream().also { turned.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
    } finally {
        if (turned !== bitmap) turned.recycle()
        bitmap.recycle()
    }
}

private const val JPEG_QUALITY = 95

private tailrec fun Context.findActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

internal class CameraXFeed(
    private val context: Context,
    private val owner: ComponentActivity,
) : CameraFeed {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    private val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var torch = false
    private var lastAnalysedAt = 0L

    @Composable
    override fun Preview(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    bind(view)
                }
            },
        )
    }

    private fun bind(view: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val cameras = future.get()
                provider = cameras
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                cameras.unbindAll()
                camera = cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
                camera?.cameraControl?.enableTorch(torch)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun analyse(onFrame: ((GrayscaleRaster) -> Unit)?) {
        if (onFrame == null) {
            analysis.clearAnalyzer()
            return
        }
        analysis.setAnalyzer(executor) { image: ImageProxy ->
            try {
                // Frames that would be thrown away are dropped before the costly conversion, not after it.
                val now = System.nanoTime()
                if (now - lastAnalysedAt < MIN_FRAME_GAP_NANOS) return@setAnalyzer
                lastAnalysedAt = now
                val plane = image.planes[0]
                val buffer = plane.buffer
                val luma = ByteArray(buffer.remaining()).also { buffer.get(it) }
                onFrame(
                    LumaFrames.toRaster(
                        luma = luma,
                        width = image.width,
                        height = image.height,
                        rowStride = plane.rowStride,
                        pixelStride = plane.pixelStride,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        maxSide = ANALYSIS_SIDE,
                    ),
                )
            } finally {
                image.close()
            }
        }
    }

    override fun setFlash(on: Boolean) {
        torch = on
        camera?.cameraControl?.enableTorch(on)
    }

    override suspend fun takePhoto(): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val degrees = image.imageInfo.rotationDegrees
                        val bytes =
                            try {
                                val buffer = image.planes[0].buffer
                                ByteArray(buffer.remaining()).also { buffer.get(it) }
                            } finally {
                                image.close()
                            }
                        if (continuation.isActive) continuation.resume(uprightJpeg(bytes, degrees))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }

    fun release() {
        analysis.clearAnalyzer()
        provider?.unbindAll()
        camera?.cameraControl?.enableTorch(false)
        executor.shutdown()
    }

    private companion object {
        /** About 640 pixels on the long side: enough to find an 8 mm square on an A4 sheet, and cheap enough to do several times a second. */
        const val ANALYSIS_SIDE = 640

        /** A little under the tracker's own 250 ms, so a frame is ready each time the tracker wants one. */
        const val MIN_FRAME_GAP_NANOS = 200_000_000L
    }
}
