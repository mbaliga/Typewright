// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StoreLookup
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopStorageProviderTest {
    private val temp = Files.createTempDirectory("desktop-storage-provider")
    private val provider = DesktopStorageProvider()

    @AfterTest
    fun cleanUp() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun storeForAMissingFolderIsMissing() =
        runTest {
            val lookup = provider.storeFor(ProjectLocation.FileSystem(temp.resolve("never-created").toString()))
            assertIs<StoreLookup.Missing>(lookup)
        }

    @Test
    fun storeForAnExistingFolderIsAvailable() =
        runTest {
            val project = Files.createDirectory(temp.resolve("Hyle Deco"))
            val lookup = provider.storeFor(ProjectLocation.FileSystem(project.toString()))
            assertIs<StoreLookup.Available>(lookup)
        }

    @Test
    fun createChildMakesTheNamedDirectoryEvenInsideAMissingParent() =
        runTest {
            val parent = temp.resolve("does-not-exist-either")
            val location = provider.createChild(ProjectLocation.FileSystem(parent.toString()), "Untitled")
            val fileSystemLocation = assertIs<ProjectLocation.FileSystem>(location)
            assertTrue(
                java.nio.file.Path
                    .of(fileSystemLocation.path)
                    .exists(),
            )
        }
}
