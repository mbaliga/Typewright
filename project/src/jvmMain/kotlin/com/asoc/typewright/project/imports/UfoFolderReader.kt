// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

private val TEXT_EXTENSIONS = setOf("plist", "glif", "fea", "txt", "md", "json")
private val LICENCE_FILE_NAMES = setOf("ofl.txt", "ofl", "license", "license.txt", "licence", "licence.txt", "license.md", "copying")
private const val MAX_LICENCE_FILE_BYTES = 200_000L

/**
 * Reads the UFO folder [ufoDir]. Only the files a UFO is made of (`.plist`, `.glif`, `.fea`) are
 * read as text; `images/` and `data/` are listed and left alone. Never throws: a path that is not a
 * folder, one that holds no `metainfo.plist`, or one the operating system will not let us read is
 * [UfoFolderRead.Failed].
 */
fun readUfoFolder(ufoDir: Path): UfoFolderRead {
    return try {
        val dir = ufoDir.toAbsolutePath().normalize()
        if (!Files.isDirectory(dir)) return UfoFolderRead.Failed("That isn't a folder.")
        if (!Files.isRegularFile(dir.resolve("metainfo.plist"), LinkOption.NOFOLLOW_LINKS)) {
            return UfoFolderRead.Failed("That folder isn't a UFO: it has no metainfo.plist.")
        }
        val files = LinkedHashMap<String, String>()
        val others = mutableListOf<String>()
        Files.walk(dir).use { stream ->
            for (path in stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.sorted().toList()) {
                val relative = dir.relativize(path).joinToString("/") { it.toString() }
                val extension = relative.substringAfterLast('.', "").lowercase()
                if (extension in TEXT_EXTENSIONS && !relative.startsWith("images/") && !relative.startsWith("data/")) {
                    files[relative] = Files.readString(path)
                } else {
                    others += relative
                }
            }
        }
        val parent = dir.parent ?: return UfoFolderRead.Failed("That folder has nowhere to hold a project; choose a UFO inside a folder.")
        val siblings = Files.list(parent).use { it.toList() }
        val otherUfos =
            siblings
                .filter { Files.isDirectory(it) && it.fileName.toString().endsWith(".ufo", ignoreCase = true) && it != dir }
                .map { it.fileName.toString() }
                .sorted()
        val licenceFiles =
            siblings
                .filter {
                    Files.isRegularFile(it) && it.fileName.toString().lowercase() in LICENCE_FILE_NAMES &&
                        Files.size(it) <= MAX_LICENCE_FILE_BYTES
                }.sorted()
                .mapNotNull { runCatching { Files.readString(it) }.getOrNull() }
        UfoFolderRead.Read(UfoFolder(dir.fileName.toString(), parent.toString(), files, others, otherUfos, licenceFiles))
    } catch (e: IOException) {
        UfoFolderRead.Failed("That folder couldn't be read: ${e.message ?: e::class.simpleName}.")
    } catch (e: SecurityException) {
        UfoFolderRead.Failed("That folder couldn't be read: access was refused.")
    } catch (e: java.nio.file.InvalidPathException) {
        UfoFolderRead.Failed("That folder's path isn't one this computer can open.")
    }
}
