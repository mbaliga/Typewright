// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

/** Which application sandbox, if any, the desktop app is running inside. */
enum class LinuxSandbox {
    /** A plain install (tarball + install.sh, .deb, .rpm): the host's tools and files are visible. */
    None,

    /** Flatpak: host binaries (zenity, kdialog) are not visible and only granted paths are. */
    Flatpak,

    /** Snap: same shape as Flatpak for this app's purposes. */
    Snap,
    ;

    /** True when a host `zenity` / `kdialog` cannot be assumed to exist. */
    val hidesHostTools: Boolean get() = this != None

    companion object {
        /**
         * Detects the sandbox from the process environment. Pure: [env] and [fileExists] are
         * injected so it is tested on any host. Flatpak sets `FLATPAK_ID` and creates
         * `/.flatpak-info`; Snap sets `SNAP` and `SNAP_NAME`.
         */
        fun detect(
            env: Map<String, String> = System.getenv(),
            fileExists: (String) -> Boolean = { java.io.File(it).exists() },
        ): LinuxSandbox =
            when {
                !env["FLATPAK_ID"].isNullOrEmpty() || fileExists("/.flatpak-info") -> Flatpak
                !env["SNAP"].isNullOrEmpty() && !env["SNAP_NAME"].isNullOrEmpty() -> Snap
                else -> None
            }
    }
}

/** The order the folder picker tries its back-ends in. */
enum class PickerBackend { Zenity, Kdialog, Swing }

/**
 * Inside a sandbox the host's `zenity`/`kdialog` are not there to run, so only Swing is tried;
 * the manifest grants the folders it may browse (`--filesystem=home` for the Flatpak, see
 * packaging/linux/flatpak/README.md). Outside one, the long-standing order is kept.
 */
fun pickerOrder(sandbox: LinuxSandbox): List<PickerBackend> =
    if (sandbox.hidesHostTools) {
        listOf(PickerBackend.Swing)
    } else {
        listOf(PickerBackend.Zenity, PickerBackend.Kdialog, PickerBackend.Swing)
    }
