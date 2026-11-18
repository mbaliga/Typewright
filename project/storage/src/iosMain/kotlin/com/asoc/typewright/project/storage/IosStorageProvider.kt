// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.AppConfigStore
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.SandboxPaths
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.project.SwapProtocolStore
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * [StorageProvider] for iOS and iPadOS: projects are plain directories inside the app's own
 * `Documents/Typewright Projects` folder, held as [ProjectLocation.FileSystem] paths, with every
 * file written through [IosDocumentOps] and the shared swap protocol (docs/PORTING_PLAN.md
 * IOS-4). Because Info.plist turns on file sharing, that folder shows in the Files app under
 * "On My iPad", which is how a user takes a project out as UFO 3 and JSON.
 *
 * Not built: a document picker for folders outside the sandbox, and the security-scoped
 * bookmark location it needs (an additive [ProjectLocation] subtype, owner question in
 * docs/PORTING_PLAN.md section 8 item 7). Not decided: whether the folder is excluded from
 * iCloud backup. Nothing here has run on a device (evidence: CODE-ONLY).
 */
@OptIn(ExperimentalForeignApi::class)
class IosStorageProvider(
    private val documentsDir: String = defaultDocumentsDirectory(),
) : StorageProvider {
    /** The folder new projects are created in; the first location the UI offers. */
    val projectsRoot: ProjectLocation.FileSystem =
        ProjectLocation.FileSystem("$documentsDir/${SandboxPaths.PROJECTS_FOLDER}")

    override suspend fun storeFor(location: ProjectLocation): StoreLookup {
        val saved = location as? ProjectLocation.FileSystem ?: return StoreLookup.Missing
        val path = SandboxPaths.remapIntoDocuments(saved.path, documentsDir)
        if (!isDirectory(path)) return StoreLookup.Missing
        val here = ProjectLocation.FileSystem(path)
        return StoreLookup.Available(SwapProtocolStore(IosDocumentOps(path), here, SandboxPaths.displayName(path)))
    }

    override suspend fun createChild(
        parent: ProjectLocation,
        name: String,
    ): ProjectLocation {
        val parentPath =
            (parent as? ProjectLocation.FileSystem)?.let { SandboxPaths.remapIntoDocuments(it.path, documentsDir) }
                ?: projectsRoot.path
        val child = "$parentPath/${SandboxPaths.safeChildName(name)}"
        // Never throws, matching the other providers: a failure shows up as StoreLookup.Missing on
        // the caller's next storeFor, which ProjectWorkspace.create turns into NotWritable.
        runCatching {
            NSFileManager.defaultManager.createDirectoryAtPath(child, withIntermediateDirectories = true, attributes = null, error = null)
        }
        return ProjectLocation.FileSystem(child)
    }

    private fun isDirectory(path: String): Boolean =
        memScoped {
            val flag = alloc<BooleanVar>()
            NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory = flag.ptr) && flag.value
        }

    companion object {
        /** The app's `Documents` directory, the root `Info.plist` file sharing exposes in Files. */
        fun defaultDocumentsDirectory(): String =
            (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
                ?: error("This app has no Documents directory")
    }
}

/**
 * Recent-projects and other app settings under `Application Support/Typewright/config/`, written
 * through the same [IosDocumentOps] swap steps as projects. A file here is never a user's
 * drawing, so it is not in the Files-visible `Documents` folder.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppConfigStore(
    configDir: String = defaultConfigDirectory(),
) : AppConfigStore {
    private val ops = IosDocumentOps(configDir)
    private val dir = configDir

    override suspend fun read(name: String): ByteArray? = ops.read(name)

    override suspend fun writeAtomic(
        name: String,
        bytes: ByteArray,
    ) {
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val tmp = "$name.tmp"
        val staged = "$name.new"
        ops.writeTruncate(tmp, bytes)
        ops.delete(staged)
        ops.rename(tmp, staged)
        ops.delete(name)
        ops.rename(staged, name)
    }

    companion object {
        fun defaultConfigDirectory(): String {
            val support =
                (
                    NSSearchPathForDirectoriesInDomains(
                        platform.Foundation.NSApplicationSupportDirectory,
                        NSUserDomainMask,
                        true,
                    ).firstOrNull() as? String
                )
                    ?: error("This app has no Application Support directory")
            return "$support/Typewright/config"
        }
    }
}
