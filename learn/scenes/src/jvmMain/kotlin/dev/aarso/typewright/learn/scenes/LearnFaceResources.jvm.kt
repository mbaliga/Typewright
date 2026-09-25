// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

/** Anchors [readLearnFaceResourceBytes]'s classloader lookup to this module's own class. */
private object JvmLearnFaceResourceAnchor

internal actual fun readLearnFaceResourceBytes(resourcePath: String): ByteArray {
    val stream =
        JvmLearnFaceResourceAnchor.javaClass.getResourceAsStream("/$resourcePath")
            ?: error("Learn-face resource not found on the classpath: $resourcePath")
    return stream.use { it.readBytes() }
}
