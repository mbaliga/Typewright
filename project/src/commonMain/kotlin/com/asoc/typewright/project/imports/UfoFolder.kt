// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

/**
 * A UFO folder as a platform read it, ready for [reviewUfoFolder]: its text files by path relative
 * to the folder ([files]), the paths of the entries that are not read ([otherEntries]: `images/`,
 * `data/`), and what lies beside it: the folder it sits in ([parentPath], where adoption would write
 * `typewright.json`), the other `.ufo` folders there, and the texts of any licence files such as
 * `OFL.txt`.
 */
data class UfoFolder(
    val name: String,
    val parentPath: String,
    val files: Map<String, String>,
    val otherEntries: List<String>,
    val otherUfos: List<String>,
    val licenceFiles: List<String>,
) {
    /** [reviewUfoFolder] over what was read. */
    fun review(): ImportReview = reviewUfoFolder(name, files, otherEntries, UfoSiblings(parentPath, otherUfos), licenceFiles)
}

/** The outcome of reading a UFO folder off a disk. */
sealed interface UfoFolderRead {
    data class Read(
        val folder: UfoFolder,
    ) : UfoFolderRead

    /** The folder could not be read as a UFO; [message] says why in plain words. */
    data class Failed(
        val message: String,
    ) : UfoFolderRead
}
