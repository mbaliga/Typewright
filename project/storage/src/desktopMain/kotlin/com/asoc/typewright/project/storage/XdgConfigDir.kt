// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import java.nio.file.Path

/**
 * `$XDG_CONFIG_HOME/typewright/`, or `~/.config/typewright/` when `XDG_CONFIG_HOME` is unset or
 * blank (docs/PROJECT_MODEL.md §5's app-config row). [xdgConfigDir] reads the real environment;
 * [resolveXdgConfigDir] takes the lookup as a parameter so the rule itself is testable without an
 * environment to mutate.
 */
fun xdgConfigDir(): Path = resolveXdgConfigDir(getenv = System::getenv, home = System.getProperty("user.home"))

/** The pure rule behind [xdgConfigDir]: [getenv] stands in for `System.getenv`, [home] for the user's home directory. */
internal fun resolveXdgConfigDir(
    getenv: (String) -> String?,
    home: String,
): Path {
    val xdgConfigHome = getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
    val base = if (xdgConfigHome != null) Path.of(xdgConfigHome) else Path.of(home).resolve(".config")
    return base.resolve("typewright")
}
