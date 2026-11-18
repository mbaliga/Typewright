// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.DocumentOps
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fsync
import platform.posix.fwrite
import platform.posix.memcpy

/**
 * [DocumentOps] over a directory in the app's sandbox, through `NSFileManager` and stdio
 * (docs/PORTING_PLAN.md IOS-4). The project store on top of it is the shared
 * [com.asoc.typewright.project.SwapProtocolStore], so the crash-safety argument (a crash between
 * any two steps leaves a file wholly old or wholly new once `recover()` has run) is the one the
 * crash-injection tests already prove on the JVM; what this file adds is only the five file
 * operations, which have not run on a Mac, simulator or iPad (evidence: CODE-ONLY).
 *
 * `rename` uses `moveItemAtPath`, which fails when the destination exists, as the contract
 * requires. `writeTruncate` flushes and `fsync`s the file descriptor before returning.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDocumentOps(
    private val rootPath: String,
) : DocumentOps {
    private val files = NSFileManager.defaultManager

    override suspend fun list(): List<String> {
        val subpaths = files.subpathsOfDirectoryAtPath(rootPath, error = null) ?: return emptyList()
        return subpaths.mapNotNull { it as? String }.filter { !isDirectory("$rootPath/$it") }
    }

    override suspend fun read(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile("$rootPath/$path") ?: return null
        val size = data.length.toInt()
        val out = ByteArray(size)
        if (size > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return out
    }

    override suspend fun writeTruncate(
        path: String,
        bytes: ByteArray,
    ) {
        val target = "$rootPath/$path"
        val handle = fopen(target, "wb") ?: error("Could not open $path for writing")
        try {
            if (bytes.isNotEmpty()) {
                val written = bytes.usePinned { fwrite(it.addressOf(0), 1.toULong(), bytes.size.toULong(), handle) }
                check(written == bytes.size.toULong()) { "Short write to $path" }
            }
            check(fflush(handle) == 0) { "Could not flush $path" }
            check(fsync(fileno(handle)) == 0) { "Could not sync $path" }
        } finally {
            fclose(handle)
        }
    }

    override suspend fun rename(
        path: String,
        newName: String,
    ) {
        val from = "$rootPath/$path"
        val parent = from.substringBeforeLast('/')
        attempt("rename $path to $newName") { error -> files.moveItemAtPath(from, "$parent/$newName", error) }
    }

    override suspend fun delete(path: String) {
        val target = "$rootPath/$path"
        if (!files.fileExistsAtPath(target)) return
        attempt("delete $path") { error -> files.removeItemAtPath(target, error) }
    }

    override suspend fun ensureDirectory(path: String) {
        attempt("create directory $path") { error ->
            files.createDirectoryAtPath("$rootPath/$path", withIntermediateDirectories = true, attributes = null, error = error)
        }
    }

    private fun isDirectory(absolutePath: String): Boolean =
        memScoped {
            val flag = alloc<BooleanVar>()
            files.fileExistsAtPath(absolutePath, isDirectory = flag.ptr) && flag.value
        }

    private fun attempt(
        what: String,
        action: (kotlinx.cinterop.CPointer<ObjCObjectVar<NSError?>>) -> Boolean,
    ) {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            check(action(error.ptr)) { "Could not $what: ${error.value?.localizedDescription ?: "unknown error"}" }
        }
    }
}
