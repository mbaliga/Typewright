// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject

/** The longest family name accepted: a PostScript name is at most 63 characters, and it is built from the family and the style. */
const val MAX_FAMILY_NAME_LENGTH = 40

/**
 * What is wrong with [name] as the new family name of a font that reserves [reserved] names, in one
 * plain sentence, or null when it will do. It must not be blank, must not be the old family name,
 * and must not use a reserved name: not as a word of its own (`Josefin Sans Redraw`), and not run
 * together (`JosefinSans`). OFL condition 3 forbids a Modified Version using a Reserved Font Name.
 */
fun validateFamilyName(
    name: String,
    reserved: List<String>,
    oldFamily: String? = null,
): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "Give the font a new name."
    if (trimmed.length > MAX_FAMILY_NAME_LENGTH) return "Keep the name to $MAX_FAMILY_NAME_LENGTH characters or fewer."
    if (trimmed.any { it < ' ' }) return "The name can't hold control characters."
    if (oldFamily != null && words(trimmed) == words(oldFamily)) return "That is the font's own name. Give your version a different one."
    val clash = reserved.firstOrNull { usesName(trimmed, it) }
    if (clash != null) return "The name can't use the reserved name “$clash”. Choose a name that doesn't contain it."
    return null
}

/** [text] lower-cased and split into runs of letters and digits. */
private fun words(text: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    for (c in text.lowercase()) {
        if (c.isLetterOrDigit()) {
            current.append(c)
        } else if (current.isNotEmpty()) {
            words += current.toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) words += current.toString()
    return words
}

private fun usesName(
    candidate: String,
    reserved: String,
): Boolean {
    val have = words(candidate)
    val want = words(reserved)
    if (want.isEmpty()) return false
    val joinedHave = have.joinToString("")
    val joinedWant = want.joinToString("")
    if (joinedHave == joinedWant) return true
    if (joinedWant.length >= LONG_RESERVED_NAME && joinedWant in joinedHave) return true
    return have.windowed(want.size).any { it == want }
}

private const val LONG_RESERVED_NAME = 8

/** The keys of `fontinfo.plist` a compiler turns into the font's name table, apart from the family and style names themselves. */
private val DERIVED_NAME_KEYS =
    listOf(
        "styleMapFamilyName",
        "postscriptFullName",
        "openTypeNameUniqueID",
        "openTypeNameCompatibleFullName",
        "openTypeNameWWSFamilyName",
        "openTypeNameWWSSubfamilyName",
    )

/** The name IDs of the names a compiler builds from the family: family, unique id, full name, PostScript name, preferred family, compatible full, WWS family. */
private val FAMILY_NAME_IDS = setOf(1L, 3L, 4L, 6L, 16L, 18L, 21L)

/**
 * [ufo] with its family renamed to [newFamily], so that no name a compiler would emit still uses an
 * old one. The family, the PostScript name and the preferred family are set from the new name; the
 * keys a compiler derives from them (`styleMapFamilyName`, the unique id, the full and compatible
 * full names, the WWS names, and any `openTypeNameRecords` entry for those name IDs) are removed,
 * so the compiler builds fresh ones. The copyright, trademark, licence, description and designer
 * lines are left exactly as they are: the copyright line must be kept, and it is where a reserved
 * name is declared.
 *
 * The project is created with this font info; renaming is not an edit in the undo history.
 * [reserved] is what [nameTableKeysMentioning] checks the result against; the call throws if a name
 * key (the style name, say) still uses one, because the new project would not be safe to compile.
 */
fun renameFamily(
    ufo: UfoProject,
    newFamily: String,
    reserved: List<String>,
): UfoProject {
    val name = newFamily.trim()
    val info = ufo.fontInfo
    val style = info.styleName ?: "Regular"
    val renamed =
        info.copy(
            familyName = name,
            openTypeNamePreferredFamilyName = if (info.openTypeNamePreferredFamilyName != null) name else null,
            postscriptFontName = if (info.postscriptFontName != null) postScriptName(name, style) else null,
            other = info.other.filter { it.first !in DERIVED_NAME_KEYS }.map { (key, value) -> key to withoutFamilyRecords(key, value) },
        )
    val leftover = nameTableKeysMentioning(renamed, reserved)
    require(leftover.isEmpty()) {
        "after renaming, ${leftover.joinToString(", ")} still uses a reserved name; change it before importing"
    }
    return ufo.copy(fontInfo = renamed)
}

private fun withoutFamilyRecords(
    key: String,
    value: PlistValue,
): PlistValue {
    if (key != "openTypeNameRecords" || value !is PlistValue.PArray) return value
    return PlistValue.PArray(
        value.items.filterNot { item ->
            val id = ((item as? PlistValue.PDict)?.entries?.firstOrNull { it.first == "nameID" }?.second as? PlistValue.PInteger)?.value
            id != null && id in FAMILY_NAME_IDS
        },
    )
}

/**
 * The `fontinfo.plist` keys in [info] that feed the name table and still use a name in [reserved]
 * (as a word of its own, or run together with the spaces and punctuation left out; case is ignored). Empty when the font is safe to compile under its
 * current names. The copyright, trademark, licence and description keys are not checked: they are
 * meant to mention the original.
 */
fun nameTableKeysMentioning(
    info: UfoFontInfo,
    reserved: List<String>,
): List<String> {
    if (reserved.isEmpty()) return emptyList()

    fun uses(text: String?): Boolean = text != null && reserved.any { usesName(text, it) }
    val named =
        listOf(
            "familyName" to info.familyName,
            "styleName" to info.styleName,
            "openTypeNamePreferredFamilyName" to info.openTypeNamePreferredFamilyName,
            "openTypeNamePreferredSubfamilyName" to info.openTypeNamePreferredSubfamilyName,
            "postscriptFontName" to info.postscriptFontName,
        )
    val keys = named.filter { uses(it.second) }.map { it.first }.toMutableList()
    for (key in DERIVED_NAME_KEYS) {
        val value = (info.other.lastOrNull { it.first == key }?.second as? PlistValue.PString)?.value
        if (uses(value)) keys += key
    }
    val records = info.other.lastOrNull { it.first == "openTypeNameRecords" }?.second as? PlistValue.PArray
    for (item in records?.items.orEmpty()) {
        val entries = (item as? PlistValue.PDict)?.entries ?: continue
        val id = (entries.firstOrNull { it.first == "nameID" }?.second as? PlistValue.PInteger)?.value
        val text = (entries.firstOrNull { it.first == "string" }?.second as? PlistValue.PString)?.value
        if (id != null && id in FAMILY_NAME_IDS && uses(text)) keys += "openTypeNameRecords (name ID $id)"
    }
    return keys
}

/** A PostScript name for [family] and [style]: ASCII letters and digits only, `Family-Style`, at most 63 characters; null when [family] has none. */
fun postScriptName(
    family: String,
    style: String,
): String? {
    val f = family.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
    if (f.isEmpty()) return null
    val s = style.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
    return (if (s.isEmpty()) f else "$f-$s").take(MAX_POSTSCRIPT_NAME)
}

private const val MAX_POSTSCRIPT_NAME = 63

/** [name] as a folder or file name: the characters `/ \ : * ? " < > |`, controls and a leading dot become `_`; blank becomes `Untitled`. */
fun projectFolderName(name: String): String {
    val cleaned =
        name
            .trim()
            .map { c -> if (c in FORBIDDEN_IN_FOLDER_NAMES || c < ' ') '_' else c }
            .joinToString("")
            .trim()
            .let { if (it.startsWith(".")) "_" + it.drop(1) else it }
    return cleaned.ifEmpty { "Untitled" }
}

private val FORBIDDEN_IN_FOLDER_NAMES = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
