// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.capturetestkit

import java.io.File

/**
 * Finds a file of the repository from a test, whatever module the test runs in: under the folder the `typewright.repoRoot` system
 * property names when a build sets it, and otherwise under the first folder above the working directory that holds it.
 */
object RepoFiles {
    fun file(relativePath: String): File {
        System.getProperty("typewright.repoRoot")?.let { root -> File(root, relativePath).takeIf { it.isFile }?.let { return it } }
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, relativePath).takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        error("$relativePath was not found above ${System.getProperty("user.dir")}; set typewright.repoRoot to the repository folder")
    }
}
