// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.Test
import kotlin.test.assertEquals

class SandboxPathsTest {
    private val now = "/var/mobile/Containers/Data/Application/NEW-UUID/Documents"

    @Test
    fun pathAlreadyUnderCurrentDocumentsIsUnchanged() {
        val path = "$now/Typewright Projects/My Font"
        assertEquals(path, SandboxPaths.remapIntoDocuments(path, now))
        assertEquals(path, SandboxPaths.remapIntoDocuments(path, "$now/"))
    }

    @Test
    fun stalePathFromAnotherContainerIsReRooted() {
        val stale = "/var/mobile/Containers/Data/Application/OLD-UUID/Documents/Typewright Projects/My Font"
        assertEquals("$now/Typewright Projects/My Font", SandboxPaths.remapIntoDocuments(stale, now))
    }

    @Test
    fun documentsFolderItselfMapsToCurrentDocuments() {
        assertEquals(now, SandboxPaths.remapIntoDocuments("/old/Documents/", now))
    }

    @Test
    fun pathWithoutADocumentsSegmentIsLeftAlone() {
        assertEquals("/tmp/elsewhere/Font", SandboxPaths.remapIntoDocuments("/tmp/elsewhere/Font", now))
    }

    @Test
    fun displayNameIsTheLastComponent() {
        assertEquals("My Font", SandboxPaths.displayName("/a/b/My Font/"))
        assertEquals("x", SandboxPaths.displayName("x"))
    }

    @Test
    fun childNamesCannotEscapeTheirFolder() {
        assertEquals("a-b", SandboxPaths.safeChildName("a/b"))
        assertEquals("Untitled", SandboxPaths.safeChildName(".."))
        assertEquals("Untitled", SandboxPaths.safeChildName("   "))
        assertEquals("My Font", SandboxPaths.safeChildName(" My Font "))
    }
}
