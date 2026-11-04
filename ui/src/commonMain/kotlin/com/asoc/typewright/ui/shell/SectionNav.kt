// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.StationStatus

/**
 * What the deck's section arc needs from the router, handed down once so no screen carries a
 * widened parameter list: which section is current, how to go to another, how to open the Map, and
 * each station's status shape where something knows it ([statusOf] is null for a station with no
 * known state, which draws no shape rather than an invented one).
 */
class SectionNav(
    val current: DeckSection,
    val goTo: (DeckSection) -> Unit,
    val openMap: (() -> Unit)? = null,
    val statusOf: (DeckSection) -> StationStatus? = { null },
)

val LocalSectionNav = staticCompositionLocalOf<SectionNav?> { null }

/** Goes up from Home to Projects (S01), the app's root; null where nothing provided it. */
val LocalOpenProjects = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Which glyph Draw and Space are working on, and how to change it. [current] is null until a glyph
 * is chosen; [open] makes a glyph current and goes to Draw, which is what tapping a glyph on Home does;
 * [select] only changes the glyph, for stepping to the next one without leaving the screen.
 */
class GlyphNav(
    val current: String?,
    val open: (String) -> Unit,
    val select: (String) -> Unit,
)

/** The glyph in hand; null where nothing provided it. */
val LocalGlyphNav = staticCompositionLocalOf<GlyphNav?> { null }
