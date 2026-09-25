// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

/** Anchors [readSceneResourceText]'s classloader lookup to this module's own class. */
private object JvmSceneResourceAnchor

internal actual fun readSceneResourceText(resourcePath: String): String {
    val stream =
        JvmSceneResourceAnchor.javaClass.getResourceAsStream("/$resourcePath")
            ?: error("Scene resource not found on the classpath: $resourcePath")
    return stream.use { it.readBytes() }.decodeToString()
}
