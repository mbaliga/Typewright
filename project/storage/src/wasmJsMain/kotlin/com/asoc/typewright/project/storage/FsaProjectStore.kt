// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.Durability
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectPath
import com.asoc.typewright.project.ProjectStore
import com.asoc.typewright.project.RecoveryPlan
import com.asoc.typewright.project.RecoveryReport
import com.asoc.typewright.project.WriteLease
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString

/**
 * [ProjectStore] over the File System Access API (docs/PROJECT_MODEL.md §5's table): a picked
 * folder ([location] a [ProjectLocation.BrowserHandle]) or, for this module's own browser tests,
 * OPFS's root ([openOpfs]), which has the same handle API with no picker or permission prompt.
 *
 * **Atomic replace is FSA's own**, not the swap protocol: `createWritable({keepExistingData:
 * false})` → write → `close()` changes the file only on a successful close (by spec), so
 * [writeAtomic] writes straight to the target path, never a `.tmp`/`.new` of its own. The only
 * thing [recover] ever has to clean up is a **stale `.crswap`** -- Chromium's own internal swap
 * file, left behind only if the browser died mid-`createWritable`, before its own close finished.
 *
 * The single-writer check is the Web Locks API ([webLockLease]), keyed by this project's own
 * [ProjectLocation.BrowserHandle.key] (or, for an OPFS-backed store in tests, a fixed key), scoped
 * to the browser origin rather than to one process the way Android's is.
 */
class FsaProjectStore(
    private val rootHandle: JsAny,
    override val location: ProjectLocation,
    override val displayName: String,
) : ProjectStore {
    override val durability: Durability = Durability.PERSISTENT

    override suspend fun list(): List<String> {
        val joined = fsaListFiles(rootHandle).await<JsString>().toString()
        return splitNulJoined(joined)
            .filter(ProjectPath::isValid)
            .filterNot(ProjectPath::isInGitDirectory)
            .distinct()
            .sorted()
    }

    override suspend fun read(path: String): ByteArray? {
        ProjectPath.validate(path)
        val base64 = fsaReadFile(rootHandle, path.toJsString()).await<JsString?>() ?: return null
        return Base64.decode(base64.toString())
    }

    override suspend fun writeAtomic(
        path: String,
        bytes: ByteArray,
    ) {
        ProjectPath.validateWritable(path)
        fsaWriteFile(rootHandle, path.toJsString(), Base64.encode(bytes).toJsString()).await<JsAny?>()
    }

    override suspend fun delete(path: String) {
        ProjectPath.validate(path)
        fsaRemoveEntry(rootHandle, path.toJsString()).await<JsString>()
        var directory = ProjectPath.parent(path)
        while (isUnderLocks(directory)) {
            val removed = fsaRemoveEntry(rootHandle, directory.toJsString()).await<JsString>().toString() == "1"
            if (!removed) break
            directory = if ('/' in directory) ProjectPath.parent(directory) else ""
        }
    }

    override suspend fun ensureDirectory(path: String) {
        ProjectPath.validate(path)
        fsaEnsureDirectory(rootHandle, path.toJsString()).await<JsAny?>()
    }

    override suspend fun recover(): RecoveryReport {
        val joined = fsaListFiles(rootHandle).await<JsString>().toString()
        val paths = splitNulJoined(joined).filter(ProjectPath::isValid).filterNot(ProjectPath::isInGitDirectory)
        val plan = RecoveryPlan.of(paths)
        for (temp in plan.discard) fsaRemoveEntry(rootHandle, temp.toJsString()).await<JsString>()
        // FsaProjectStore never creates its own `.new` (see this class's own KDoc), so this is
        // defensive rather than a path any normal run takes: roll the completed write forward by
        // copying its bytes over the target, then drop the `.new`.
        for (newPath in plan.rollForward) {
            val target = RecoveryPlan.targetOf(newPath)
            val base64 = fsaReadFile(rootHandle, newPath.toJsString()).await<JsString?>()
            if (base64 != null) fsaWriteFile(rootHandle, target.toJsString(), base64).await<JsAny?>()
            fsaRemoveEntry(rootHandle, newPath.toJsString()).await<JsString>()
        }
        return RecoveryReport(rolledForward = plan.rollForward.map(RecoveryPlan::targetOf), discarded = plan.discard)
    }

    override suspend fun acquireWriteLease(): WriteLease? = webLockLease(lockName().toJsString())

    private fun lockName(): String =
        when (val loc = location) {
            is ProjectLocation.BrowserHandle -> "typewright-project:${loc.key}"
            else -> "typewright-project:$displayName"
        }

    companion object {
        /**
         * Opens (creating if needed) the directory [name] inside origin-private storage's own
         * root as a [FsaProjectStore] -- no picker, no permission prompt (this module's own
         * browser tests, one differently named directory per test for isolation).
         */
        suspend fun openOpfs(name: String): FsaProjectStore {
            val root = fsaOpfsRoot().await<JsAny>()
            val projectHandle = fsaGetOrCreateChildDirectory(root, name.toJsString()).await<JsAny>()
            return FsaProjectStore(projectHandle, ProjectLocation.BrowserHandle("opfs:$name"), name)
        }
    }
}

private fun splitNulJoined(joined: String): List<String> = if (joined.isEmpty()) emptyList() else joined.split('\u0000')

// isUnderLocks duplicates :project's own internal isUnderLocks (SwapProtocolStore.kt): that one
// isn't visible outside the :project module, and it's a one-line rule.
private fun isUnderLocks(directory: String): Boolean = directory == "locks" || directory.startsWith("locks/")
