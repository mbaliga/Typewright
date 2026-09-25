// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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
}

/** A written PNG and its pixels. */
class Screenshot(
    val file: File,
    val bitmap: ImageBitmap,
)
