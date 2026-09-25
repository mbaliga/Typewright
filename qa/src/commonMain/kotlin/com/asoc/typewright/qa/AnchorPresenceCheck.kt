// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import com.asoc.typewright.core.font.ufo.UfoProject

/**
 * Anchors present (this task's item 7; brief §12's "anchors" line item). **Best-effort and
 * minimal, said plainly**: a real check needs a script/glyph-inventory model (which glyphs a
 * project is expected to have, and which of those are meant to carry which named anchor -- base
 * letters carry `top`/`bottom`, combining marks carry `_top`/`_bottom`, and neither exists yet,
 * see docs/OPEN_QUESTIONS.md and this task's own item 9 on deferred checks). This function stands
 * in with a small hard-coded allow-list, the 52 base Latin letters (`A`-`Z`, `a`-`z`), and reports
 * a gap as informational (a [AnchorPresenceFinding] with [AnchorPresenceFinding.hasAppropriateAnchor]
 * `false`), never a hard fail -- exactly as asked.
 *
 * "Named appropriately" means at least one anchor whose name does **not** start with `_`: an
 * underscore-prefixed name (`_top`, `_bottom`) is a *mark*'s attachment point, meant for a
 * combining glyph, not a base letter -- a base letter carrying only underscore-prefixed anchors is
 * itself worth flagging, not treated as having anchors.
 */
data class AnchorPresenceFinding(
    val glyphName: String,
    val hasAppropriateAnchor: Boolean,
    val anchorNames: List<String>,
    val message: String,
)

/** The base Latin letters this best-effort check expects to carry at least one mark-attachment anchor. */
val BASE_LATIN_LETTERS_EXPECTING_ANCHORS: Set<String> =
    (('A'..'Z') + ('a'..'z')).map { it.toString() }.toSet()

/**
 * Checks every glyph in [project] whose name is in [BASE_LATIN_LETTERS_EXPECTING_ANCHORS] for at
 * least one appropriately-named anchor. A glyph in [project] but not in that allow-list is left
 * out of the result entirely (no opinion either way -- this check does not know what it should
 * expect from anything else yet).
 */
fun checkAnchorsPresent(project: UfoProject): List<AnchorPresenceFinding> =
    project.glyphs
        .filter { it.name in BASE_LATIN_LETTERS_EXPECTING_ANCHORS }
        .map { glyph ->
            val appropriate = glyph.anchors.filterNot { it.name.startsWith("_") }
            val has = appropriate.isNotEmpty()
            AnchorPresenceFinding(
                glyphName = glyph.name,
                hasAppropriateAnchor = has,
                anchorNames = glyph.anchors.map { it.name },
                message =
                    if (has) {
                        "'${glyph.name}' carries ${appropriate.size} mark-attachment anchor(s): " +
                            appropriate.joinToString { it.name } + "."
                    } else {
                        "'${glyph.name}' has no mark-attachment anchor. Base Latin letters usually carry at least " +
                            "one (commonly \"top\") so accented composites and diacritics can attach to it."
                    },
            )
        }
