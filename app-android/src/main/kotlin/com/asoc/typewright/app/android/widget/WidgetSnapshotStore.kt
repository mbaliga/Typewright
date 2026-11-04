// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * The open-project snapshot on disk, under `noBackupFilesDir` so it never reaches a cloud backup
 * or a device transfer. Written atomically (write, then rename) and removed by [WidgetPublisher];
 * read by the widget providers.
 */
class WidgetSnapshotStore(
    context: Context,
) {
    private val dir = File(context.noBackupFilesDir, "widgets")
    private val file = File(dir, "open-project.properties")

    /** The snapshot, or [SnapshotRead.Absent] before the first write. */
    fun read(): SnapshotRead {
        val bytes =
            try {
                if (file.isFile) file.readBytes() else null
            } catch (_: java.io.IOException) {
                return SnapshotRead.Unreadable
            }
        return ProjectWidgetSnapshots.decode(bytes)
    }

    /** Replaces the snapshot with [snapshot]. */
    fun write(snapshot: ProjectWidgetSnapshot) {
        synchronized(LOCK) {
            dir.mkdirs()
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeBytes(ProjectWidgetSnapshots.encode(snapshot))
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "could not write the widget snapshot" }
            }
        }
    }

    /** Removes the snapshot, so no widget placed later shows a project from before it was placed. */
    fun clear() {
        synchronized(LOCK) {
            if (file.exists()) file.delete()
        }
    }

    companion object {
        private val LOCK = Any()

        /** Names this process, so a snapshot can tell whether the process that wrote it still runs. */
        val PROCESS_TOKEN: String = UUID.randomUUID().toString()
    }
}
