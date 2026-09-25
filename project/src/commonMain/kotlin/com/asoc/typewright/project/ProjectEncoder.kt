// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.readGlyphFileNames
import com.asoc.typewright.core.font.ufo.writeGlif
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import com.asoc.typewright.project.scrapbook.ScrapbookManifestCodec

/**
 * Encoding bookkeeping that survives between saves (docs/PROJECT_MODEL.md §8.5): a glyph or
 * approved-snapshot `.glif`'s previous instance and bytes, keyed by the project path it last used
 * (so an unedited glyph keeps its original bytes — foreign `<lib>`/`<note>` included — rather than
 * losing them to a fresh, lossy [writeGlif]); each master's glyph and lock file-name hints, kept
 * stable across saves; and pinned images' bytes, which live outside [ProjectState] entirely.
 * [ProjectSession] seeds this at load with what is on disk and threads the updated copy through
 * every later encode.
 */
internal data class EncodeCaches(
    val glyphFiles: Map<String, Pair<Glyph, ByteArray>> = emptyMap(),
    val fileNameHints: Map<String, Map<String, String>> = emptyMap(),
    val lockStemHints: Map<String, Map<String, String>> = emptyMap(),
    val images: Map<String, ByteArray> = emptyMap(),
) {
    companion object {
        val EMPTY: EncodeCaches = EncodeCaches()
    }
}

/** [ProjectEncoder.encode]'s result: every file the project format owns, and the caches for next time. */
internal data class EncodedProject(
    val files: Map<String, ByteArray>,
    val caches: EncodeCaches,
)

/**
 * Turns a [ProjectState] into the exact set of files the project format owns (docs/PROJECT_MODEL.md
 * §3, §8.3–§8.5): a pure, deterministic function of the state and [EncodeCaches], except for pin
 * image bytes, which [caches] supplies since they are not part of [ProjectState]. Optional files
 * (kerning, groups, features, workbook, scrapbook, comparisons) are present only when they would
 * be non-empty, matching §3's "optional files are absent rather than empty" rule.
 */
internal object ProjectEncoder {
    /** `build/.gitignore`'s path (D12: written at creation so compiled output never enters git). */
    const val BUILD_GITIGNORE_PATH: String = "build/.gitignore"
    const val BUILD_GITIGNORE_CONTENT: String = "*\n!.gitignore\n"

    fun encode(
        state: ProjectState,
        caches: EncodeCaches,
    ): EncodedProject {
        val files = LinkedHashMap<String, ByteArray>()
        val newGlyphFiles = LinkedHashMap<String, Pair<Glyph, ByteArray>>()
        val newFileNameHints = LinkedHashMap<String, Map<String, String>>()
        val newLockStemHints = LinkedHashMap<String, Map<String, String>>()
        val lockStemsByRef = LinkedHashMap<GlyphRef, String>()

        for (master in state.font.masters) {
            encodeMaster(master, state.font.locks, caches, files, newGlyphFiles, newFileNameHints, newLockStemHints, lockStemsByRef)
        }

        files[BUILD_GITIGNORE_PATH] = BUILD_GITIGNORE_CONTENT.encodeToByteArray()

        files[ProjectManifestCodec.PATH] = ProjectManifestCodec.encode(state, lockStemsByRef).encodeToByteArray()

        if (state.meta.scrapbook.pins
                .isNotEmpty()
        ) {
            files[ScrapbookManifest.PATH] = ScrapbookManifestCodec.encode(state.meta.scrapbook).encodeToByteArray()
        }
        val usedImages = LinkedHashMap<String, ByteArray>()
        for (pin in state.meta.scrapbook.pins) {
            val path = pin.imagePath ?: continue
            val bytes = caches.images[path] ?: continue
            files[path] = bytes
            usedImages[path] = bytes
        }

        for ((key, lessons) in state.meta.lessons) {
            if (lessons.reflections.isEmpty()) continue
            files[WorkbookLessonsCodec.path(key)] = WorkbookLessonsCodec.encode(lessons).encodeToByteArray()
        }

        return EncodedProject(files, EncodeCaches(newGlyphFiles, newFileNameHints, newLockStemHints, usedImages))
    }

    private fun encodeMaster(
        master: Master,
        locks: Map<GlyphRef, GlyphLock>,
        caches: EncodeCaches,
        files: MutableMap<String, ByteArray>,
        newGlyphFiles: MutableMap<String, Pair<Glyph, ByteArray>>,
        newFileNameHints: MutableMap<String, Map<String, String>>,
        newLockStemHints: MutableMap<String, Map<String, String>>,
        lockStemsByRef: MutableMap<GlyphRef, String>,
    ) {
        val lineDefault = master.ufo.lib.lineContourFormat
        val hints = caches.fileNameHints[master.id].orEmpty()
        val ufoFiles = writeUfoProject(master.ufo, hints)
        val fileNames = readGlyphFileNames(ufoFiles)
        newFileNameHints[master.id] = fileNames
        val glyphNameByFileName = fileNames.entries.associate { (name, fileName) -> fileName to name }
        val glyphsByName = master.ufo.glyphs.associateBy { it.name }

        for ((relativePath, text) in ufoFiles) {
            val path = "${master.path}/$relativePath"
            val glyphFileName = relativePath.removePrefix("glyphs/")
            val glyph = if (relativePath.startsWith("glyphs/")) glyphNameByFileName[glyphFileName]?.let(glyphsByName::get) else null
            if (glyph != null) {
                val bytes = cachedOrEncoded(caches.glyphFiles, path, glyph) { text }
                files[path] = bytes
                newGlyphFiles[path] = glyph to bytes
            } else {
                files[path] = text.encodeToByteArray()
            }
        }

        val glyphOrder = master.ufo.glyphs.map { it.name }
        val stemHints = caches.lockStemHints[master.id].orEmpty()
        val stems = LockPaths.assignStems(master.id, locks, glyphOrder, stemHints)
        newLockStemHints[master.id] = stems.entries.associate { it.key.glyph to it.value }
        lockStemsByRef += stems

        for ((ref, stem) in stems) {
            val lock = locks.getValue(ref)
            val approvedPath = LockPaths.approvedPath(master.id, stem)
            val approvedBytes = cachedOrEncoded(caches.glyphFiles, approvedPath, lock.approved) { writeGlif(lock.approved, lineDefault) }
            files[approvedPath] = approvedBytes
            newGlyphFiles[approvedPath] = lock.approved to approvedBytes

            val diffPaths = LockPaths.diffPaths(master.id, stem, lock.episodes)
            val currentGlyph = master.ufo.glyphs.find { it.name == ref.glyph }
            for ((episode, diffPath) in lock.episodes.zip(diffPaths)) {
                val diffText =
                    if (episode.relockedAt != null) {
                        episode.frozenDiff.orEmpty()
                    } else if (currentGlyph != null) {
                        UnifiedDiff.diff(writeGlif(lock.approved, lineDefault), writeGlif(currentGlyph, lineDefault), "approved", "current")
                    } else {
                        ""
                    }
                if (diffText.isNotEmpty()) files[diffPath] = diffText.encodeToByteArray()
            }
        }
    }

    private inline fun cachedOrEncoded(
        cache: Map<String, Pair<Glyph, ByteArray>>,
        path: String,
        glyph: Glyph,
        encode: () -> String,
    ): ByteArray {
        val cached = cache[path]
        return if (cached != null && cached.first === glyph) cached.second else encode().encodeToByteArray()
    }
}
