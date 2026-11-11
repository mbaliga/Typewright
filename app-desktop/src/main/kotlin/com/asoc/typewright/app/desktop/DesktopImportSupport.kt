// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.project.storage.DesktopStorageProvider
import com.asoc.typewright.ui.imports.ImportDestination
import com.asoc.typewright.ui.project.InMemoryStorageProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a project made from an imported font is kept on the desktop: the person is asked for a
 * folder when they press Import, and `<name>/` is made inside it. The audit says so before they
 * decide, in those words, and names the folder once it is known.
 */
class DesktopImportDestination(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ImportDestination {
    override val persists: Boolean get() = true

    override suspend fun parentFolder(): ProjectLocation? = null

    override suspend fun chooseParentFolder(): ProjectLocation? =
        withContext(io) {
            try {
                chooseFolder("Choose the folder to keep the project in")?.let { ProjectLocation.FileSystem(it.absolutePath) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // No display, or the dialog failed: the import then says no folder was chosen.
                null
            }
        }

    override fun describe(
        parent: ProjectLocation?,
        folderName: String,
    ): String =
        when (parent) {
            is ProjectLocation.FileSystem -> {
                "Typewright will make the folder $folderName inside ${parent.path} and write the project there."
            }

            else -> {
                "When you press Import, Typewright asks which folder to keep the project in, " +
                    "then makes a folder named $folderName there and writes the project in it."
            }
        }
}

/**
 * The desktop app's storage: a [ProjectLocation.FileSystem] is a real folder on disk
 * ([DesktopStorageProvider]), which is where an imported font's project goes and where an adopted
 * UFO already is; anything else (the in-memory workspace "New project…" still uses) stays in memory.
 */
class DesktopImportStorage(
    private val disk: StorageProvider = DesktopStorageProvider(),
    private val memory: StorageProvider = InMemoryStorageProvider(),
) : StorageProvider {
    override suspend fun storeFor(location: ProjectLocation): StoreLookup =
        if (location is ProjectLocation.FileSystem) disk.storeFor(location) else memory.storeFor(location)

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation = if (parent is ProjectLocation.FileSystem) disk.createChild(parent, name) else memory.createChild(parent, name)
}
