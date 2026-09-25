// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.compile.ProjectDirectory
import java.io.File

/**
 * [ProjectDirectory] over a real `java.io.File` directory: step 5's own, real seed project root
 * (`compile/src/commonMain/.../CompileRequest.kt`'s own KDoc: "a platform module, or a JVM test
 * using `java.io`" is exactly what writes a [ProjectDirectory]'s files to disk -- this is that
 * JVM test). [listFiles] walks [root] recursively and returns every regular file's path relative
 * to [root], `/`-separated regardless of the host OS, matching [ProjectDirectory.listFiles]'s own
 * contract ("so the same project can live on a filesystem, in Android storage or in browser
 * memory").
 */
class FileProjectDirectory(
    private val root: File,
    override val displayName: String = root.name,
) : ProjectDirectory {
    override fun listFiles(): List<String> =
        root
            .walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()

    override fun readBytes(path: String): ByteArray = File(root, path).readBytes()
}
