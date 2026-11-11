// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.project.FileAppConfigStore
import com.asoc.typewright.project.FileSystemProjectStore
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.SessionEnvironment
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path

/** A [ProjectWorkspace] over real directories (the desktop storage provider lives in a platform module this one cannot see). */
internal object DiskWorkspace {
    fun of(
        scope: CoroutineScope,
        configDir: Path = Files.createTempDirectory("typewright-config-test"),
    ): ProjectWorkspace =
        ProjectWorkspace(DiskStorage(), FileAppConfigStore(configDir)) {
            SessionEnvironment(scope = scope, io = Dispatchers.IO)
        }
}

private class DiskStorage : StorageProvider {
    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        val path = Path.of((location as? ProjectLocation.FileSystem)?.path ?: return StoreLookup.Missing)
        return if (Files.isDirectory(path)) StoreLookup.Available(FileSystemProjectStore(path)) else StoreLookup.Missing
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        val child = Path.of((parent as ProjectLocation.FileSystem).path).resolve(name)
        Files.createDirectories(child)
        return ProjectLocation.FileSystem(child.toString())
    }
}
