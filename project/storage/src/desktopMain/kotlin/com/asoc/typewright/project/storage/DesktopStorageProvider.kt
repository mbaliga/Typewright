// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.FileSystemProjectStore
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * [StorageProvider] for the desktop (Linux) app: every [ProjectLocation] is a
 * [ProjectLocation.FileSystem], and its store is [FileSystemProjectStore] (`:project` jvmMain),
 * which already implements the atomic writes and the `build/.session-lock` write lease
 * (docs/PROJECT_MODEL.md §5's table).
 */
class DesktopStorageProvider : StorageProvider {
    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        val fileSystem = location as? ProjectLocation.FileSystem ?: return StoreLookup.Missing
        val path = Path.of(fileSystem.path)
        val isDirectory = withContext(Dispatchers.IO) { Files.isDirectory(path) }
        return if (isDirectory) StoreLookup.Available(FileSystemProjectStore(path)) else StoreLookup.Missing
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        val fileSystem =
            parent as? ProjectLocation.FileSystem
                ?: throw IllegalArgumentException("DesktopStorageProvider.createChild needs a FileSystem location, got $parent")
        val child = Path.of(fileSystem.path).resolve(name)
        // Never throws, matching AndroidStorageProvider.createChild: a parent that turns out not
        // writable is caught by ProjectSession.create's own store.list()/acquireWriteLease()
        // checks (CreateResult.NotWritable), not by this call.
        withContext(Dispatchers.IO) { runCatching { Files.createDirectories(child) } }
        return ProjectLocation.FileSystem(child.toString())
    }
}
