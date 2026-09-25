// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/**
 * The write order autosave uses (docs/PROJECT_MODEL.md §8.3d): leaf files first (everything under
 * `locks/`, scrapbook and lessons JSON, a glyph's own `.glif`), then each UFO's index files
 * (`contents.plist`, `layercontents.plist`, `fontinfo.plist`, `groups.plist`, `kerning.plist`,
 * `features.fea`, `lib.plist`, `metainfo.plist`), then `typewright.json` last. A crash between
 * any two writes leaves every reference pointing at a file that exists, old or new.
 */
internal object SavePhases {
    private val INDEX_NAMES =
        setOf(
            "contents.plist",
            "layercontents.plist",
            "fontinfo.plist",
            "groups.plist",
            "kerning.plist",
            "features.fea",
            "lib.plist",
            "metainfo.plist",
        )

    /** [paths], grouped into the three phases and sorted within each, manifest last. */
    fun order(paths: Collection<String>): List<String> {
        val leaf = mutableListOf<String>()
        val index = mutableListOf<String>()
        var manifest: String? = null
        for (path in paths) {
            when {
                path == ProjectManifestCodec.PATH -> manifest = path
                isUfoIndexFile(path) -> index += path
                else -> leaf += path
            }
        }
        return leaf.sorted() + index.sorted() + listOfNotNull(manifest)
    }

    private fun isUfoIndexFile(path: String): Boolean {
        val segments = path.split('/')
        if (segments.size < 2 || !segments[0].endsWith(".ufo")) return false
        return segments.last() in INDEX_NAMES
    }
}
