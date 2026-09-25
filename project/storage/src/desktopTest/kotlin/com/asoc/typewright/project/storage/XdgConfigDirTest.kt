// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class XdgConfigDirTest {
    @Test
    fun usesXdgConfigHomeWhenSet() {
        val resolved =
            resolveXdgConfigDir(getenv = { name ->
                if (name ==
                    "XDG_CONFIG_HOME"
                ) {
                    "/srv/config"
                } else {
                    null
                }
            }, home = "/home/owner")
        assertEquals(Path.of("/srv/config/typewright"), resolved)
    }

    @Test
    fun fallsBackToDotConfigWhenUnset() {
        val resolved = resolveXdgConfigDir(getenv = { null }, home = "/home/owner")
        assertEquals(Path.of("/home/owner/.config/typewright"), resolved)
    }

    @Test
    fun fallsBackToDotConfigWhenBlank() {
        val resolved = resolveXdgConfigDir(getenv = { name -> if (name == "XDG_CONFIG_HOME") "   " else null }, home = "/home/owner")
        assertEquals(Path.of("/home/owner/.config/typewright"), resolved)
    }

    @Test
    fun realEnvironmentResolvesUnderTypewright() {
        assertEquals("typewright", xdgConfigDir().fileName.toString())
    }
}
