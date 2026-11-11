// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.engine.trace.GrayscaleRaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** The desktop reads pictures with `javax.imageio`: JPEG, PNG, GIF and BMP. A format it does not know answers null. */
actual fun platformImageDecoder(): ImageDecoder = DesktopImageDecoder

internal object DesktopImageDecoder : ImageDecoder {
    override suspend fun decodeGray(
        bytes: ByteArray,
        maxSide: Int,
    ): GrayscaleRaster? =
        withContext(Dispatchers.IO) {
            try {
                val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@withContext null
                val w = image.width
                val h = image.height
                val argb = IntArray(w * h)
                image.getRGB(0, 0, w, h, argb, 0, w)
                ImagePixels.grayFromArgb(w, h, argb, maxSide)
            } catch (e: java.io.IOException) {
                null
            } catch (e: RuntimeException) {
                null
            }
        }
}
