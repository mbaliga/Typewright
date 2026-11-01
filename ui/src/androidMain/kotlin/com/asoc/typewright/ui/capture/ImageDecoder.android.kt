// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import android.graphics.BitmapFactory
import com.asoc.typewright.engine.trace.GrayscaleRaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Android reads pictures with `BitmapFactory`, sampled down while decoding so a 12-megapixel photograph is never held at full size.
 * Compile-verified only: nothing here has run on a device (CLAUDE.md law 4).
 */
actual fun platformImageDecoder(): ImageDecoder = AndroidImageDecoder

internal object AndroidImageDecoder : ImageDecoder {
    override suspend fun decodeGray(
        bytes: ByteArray,
        maxSide: Int,
    ): GrayscaleRaster? =
        withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
            try {
                val argb = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                ImagePixels.grayFromArgb(bitmap.width, bitmap.height, argb, maxSide)
            } finally {
                bitmap.recycle()
            }
        }
}
