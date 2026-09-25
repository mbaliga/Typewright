// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * App configuration files (recent projects) in [dir]: `$XDG_CONFIG_HOME/typewright/` on the
 * desktop, `filesDir/config/` on Android. Writes are atomic like a project's. Methods block on
 * file I/O.
 */
class FileAppConfigStore(
    dir: Path,
) : AppConfigStore {
    private val dir: Path = dir.toAbsolutePath().normalize()

    override suspend fun read(name: String): ByteArray? {
        val file = dir.resolve(ProjectPath.validate(name))
        return try {
            if (Files.isRegularFile(file)) Files.readAllBytes(file) else null
        } catch (_: NoSuchFileException) {
            null
        }
    }

    override suspend fun writeAtomic(
        name: String,
        bytes: ByteArray,
    ) {
        AtomicFiles.write(dir.resolve(ProjectPath.validateWritable(name)), bytes)
    }
}
