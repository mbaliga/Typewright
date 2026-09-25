// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.AppConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * App configuration files (recent projects) under `filesDir/config/` on Android
 * (docs/PROJECT_MODEL.md §5.4). Mirrors `:project`'s jvmMain `FileAppConfigStore`, which this
 * module cannot see directly (a different Kotlin target, `jvm` versus `android`, even though
 * both run on a JVM-like runtime), so the same small atomic-write contract is reimplemented here
 * against plain [java.io.File] rather than `java.nio.file`.
 */
class AndroidAppConfigStore(
    filesDir: File,
) : AppConfigStore {
    private val dir: File = File(filesDir, "config")

    override suspend fun read(name: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = File(dir, name)
            if (file.isFile) file.readBytes() else null
        }

    override suspend fun writeAtomic(
        name: String,
        bytes: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val target = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                // Same-filesystem rename can fail to overwrite on some devices; fall back to
                // delete-then-rename rather than leaving the write half-done.
                target.delete()
                check(tmp.renameTo(target)) { "could not write $target" }
            }
        }
    }
}
