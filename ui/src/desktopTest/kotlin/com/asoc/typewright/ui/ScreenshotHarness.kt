// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Headless screenshot harness: renders a composable off-screen with Skia (no display, no
 * window) and writes a PNG under `ui/build/screenshots/`, for side-by-side comparison with the
 * explorer's renders. Run: `./gradlew :ui:desktopTest --rerun`.
 */
object ScreenshotHarness {
    /** Where PNGs go; the Gradle test task sets this, and a plain IDE run falls back to build/. */
    val outputDir: File =
        File(System.getProperty("typewright.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    /** Renders [content] at [width] x [height] pixels, writes `<name>.png`, returns the pixels. */
    fun capture(
        name: String,
        width: Int,
        height: Int,
        density: Float = 2f,
        content: @Composable () -> Unit,
    ): Screenshot {
        val image =
            ImageComposeScene(width = width, height = height, density = Density(density), content = content)
                .use { it.render() }
        val file = File(outputDir, "$name.png")
        file.writeBytes(checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes)
        return Screenshot(file, image.toComposeImageBitmap())
    }

    /**
     * [capture], and every piece of text the page holds, read from the semantics tree the frame was drawn from, so a test can
     * say that a sentence is on the page and how wide the box it is set in came out, not only that some pixels changed.
     */
    fun captureWithText(
        name: String,
        width: Int,
        height: Int,
        density: Float = 2f,
        content: @Composable () -> Unit,
    ): TextCapture {
        var texts = emptyList<DrawnText>()
        val image =
            ImageComposeScene(width = width, height = height, density = Density(density), content = content).use { scene ->
                val rendered = scene.render()
                val all = mutableListOf<DrawnText>()

                fun walk(node: SemanticsNode) {
                    node.config.getOrNull(SemanticsProperties.Text)?.let { lines ->
                        all += DrawnText(lines.joinToString("") { it.text }, node.boundsInRoot)
                    }
                    node.children.forEach(::walk)
                }
                scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
                texts = all
                rendered
            }
        val file = File(outputDir, "$name.png")
        file.writeBytes(checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes)
        return TextCapture(Screenshot(file, image.toComposeImageBitmap()), texts, width, height)
    }
}

/** A written PNG and its pixels. */
class Screenshot(
    val file: File,
    val bitmap: ImageBitmap,
)

/** A piece of text on a page and the box, in pixels, it was laid out in. */
class DrawnText(
    val text: String,
    val bounds: Rect,
)

/** A page's [screenshot] and the [texts] it holds, of a scene [width] by [height] pixels. */
class TextCapture(
    val screenshot: Screenshot,
    val texts: List<DrawnText>,
    private val width: Int,
    private val height: Int,
) {
    /** The texts that contain [part]. */
    fun containing(part: String): List<DrawnText> = texts.filter { part in it.text }

    /** Whether some text on the page contains [part], whether or not it is scrolled into view. */
    fun has(part: String): Boolean = containing(part).isNotEmpty()

    /** Whether some text on the page contains [part] and part of the box it is in is inside the picture. */
    fun isVisible(part: String): Boolean = containing(part).any { it.bounds.overlaps(Rect(0f, 0f, width.toFloat(), height.toFloat())) }
}
