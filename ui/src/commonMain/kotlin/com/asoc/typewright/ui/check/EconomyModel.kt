// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

/** One style chip of the Economy page: the class's [label] and how many faces its box is built from. */
data class StyleChip(
    val key: String,
    val label: String,
    val faces: Int,
    val selected: Boolean,
    /** True for the class the project is judged against, which comes first. */
    val isProjectsClass: Boolean,
) {
    /** The chip's text: `Geometric sans · 30`. */
    val text: String get() = "$label · $faces"
}

/** The order the explorer lists the classes in, after the project's own. */
private val STYLE_ORDER =
    listOf(
        "sans-geometric",
        "display-artdeco",
        "sans-grotesque",
        "sans-neogrotesque",
        "sans-humanist",
        "serif-transitional",
        "serif-garalde",
        "serif-didone",
        "slab",
        "blackletter",
    )

/**
 * The chips for the classes [keys] the corpus has boxes for: the project's class ([projectKey]) first, then
 * the explorer's order, then any other class by name. [faces] is how many faces a class's box is built from
 * and [label] how a class is named; [selectedKey] is the one being compared against.
 */
fun styleChips(
    keys: Collection<String>,
    faces: (String) -> Int,
    label: (String) -> String,
    projectKey: String?,
    selectedKey: String?,
): List<StyleChip> {
    val rank = { key: String ->
        when {
            key == projectKey -> -1
            key in STYLE_ORDER -> STYLE_ORDER.indexOf(key)
            else -> STYLE_ORDER.size
        }
    }
    return keys
        .sortedWith(compareBy<String> { rank(it) }.thenBy { label(it).lowercase() })
        .map { key -> StyleChip(key, label(key), faces(key), key == selectedKey, key == projectKey) }
}

/** What the Economy page opens a row to: the person's choice once they have made one, else the worst glyph. */
class OpenRow(
    private val touched: Boolean = false,
    private val chosen: String? = null,
) {
    /** The glyph whose row is open given the page's [rows] in their current order: the first until a row is tapped. */
    fun resolve(rows: List<String>): String? = if (touched) chosen?.takeIf { it in rows } else rows.firstOrNull()

    /** The state after [glyph] is tapped while [current] is open: it closes if it was open, otherwise it opens. */
    fun tapped(
        glyph: String,
        current: String?,
    ): OpenRow = if (glyph == current) OpenRow(touched = true, chosen = null) else OpenRow(touched = true, chosen = glyph)
}
