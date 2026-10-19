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
