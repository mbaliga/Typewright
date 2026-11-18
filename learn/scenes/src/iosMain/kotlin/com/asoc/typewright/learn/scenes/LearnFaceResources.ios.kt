// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * Where the iOS app keeps the data packs (docs/IOS_PORT.md "Resources"): a folder reference named
 * `typewright-resources` copied into the app bundle by the Xcode project, holding the same
 * relative paths the JVM classpath and the Wasm build use ("typewright/corpus/...", "scenes/...").
 * Tests, which have no app bundle, point `TYPEWRIGHT_RESOURCES` at that folder instead
 * (app-ios/build.gradle.kts passes it through SIMCTL_CHILD_). Written without a Mac: CODE-ONLY.
 */
private fun iosResourceFile(
    resourcePath: String,
    label: String,
): String {
    val root =
        NSProcessInfo.processInfo.environment["TYPEWRIGHT_RESOURCES"] as? String
            ?: NSBundle.mainBundle.resourcePath?.let { "$it/typewright-resources" }
            ?: error("$label resource root unavailable: no app bundle and no TYPEWRIGHT_RESOURCES")
    val file = "$root/$resourcePath"
    check(NSFileManager.defaultManager.fileExistsAtPath(file)) { "$label resource not found in the app bundle: $resourcePath" }
    return file
}

@OptIn(ExperimentalForeignApi::class)
private fun iosResourceBytes(
    resourcePath: String,
    label: String,
): ByteArray {
    val data =
        NSData.dataWithContentsOfFile(iosResourceFile(resourcePath, label))
            ?: error("$label resource unreadable: $resourcePath")
    val size = data.length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}

internal actual fun readLearnFaceResourceBytes(resourcePath: String): ByteArray = iosResourceBytes(resourcePath, "LearnFace")
