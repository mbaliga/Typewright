// SPDX-License-Identifier: FSL-1.1-ALv2

@file:OptIn(ExperimentalEncodingApi::class)

package com.asoc.typewright.ui.capture

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The largest picture a browser answer is decoded for. */
const val MAX_PICTURE_BYTES: Long = 96L * 1024 * 1024

private const val RECORD = '\u0001'
private const val FIELD = '\u0000'

/**
 * Turns the browser's answer for several files, `"<name>\u0000<base64>"` joined by U+0001 (null or empty when the person
 * cancelled), into files. A file that is not base64, or is over [maxBytes], answers [FilePickOutcome.Failed] by name; nothing
 * here throws. The browser side only reads the bytes; everything after that is here, so it is tested on the JVM.
 */
fun picksFromBrowserAnswer(
    answer: String?,
    maxBytes: Long = MAX_PICTURE_BYTES,
): FilePickOutcome {
    if (answer.isNullOrEmpty()) return FilePickOutcome.Cancelled
    val files = ArrayList<PickedFile>()
    for (record in answer.split(RECORD)) {
        if (record.isEmpty()) continue
        val split = record.indexOf(FIELD)
        if (split <= 0) return FilePickOutcome.Failed("The browser did not say which file was chosen.")
        val name = record.substring(0, split)
        val encoded = record.substring(split + 1)
        // Base64 holds three bytes in four characters.
        if (encoded.length.toLong() / 4 * 3 > maxBytes) {
            return FilePickOutcome.Failed(
                "“$name” is larger than ${maxBytes / (1024 * 1024)} MB, which is too large to be a picture of a sheet.",
            )
        }
        val bytes =
            try {
                Base64.decode(encoded)
            } catch (e: IllegalArgumentException) {
                return FilePickOutcome.Failed("“$name” could not be read from the browser.")
            }
        files += PickedFile(name, bytes)
    }
    return if (files.isEmpty()) FilePickOutcome.Cancelled else FilePickOutcome.Picked(files)
}
