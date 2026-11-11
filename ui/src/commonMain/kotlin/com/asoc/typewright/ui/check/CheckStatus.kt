// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.StationStatus
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.ship.ShipMemory
import com.asoc.typewright.ui.ship.shipStationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** How long the drawing must stay still before the deck re-reads Check: fourteen checks over every glyph is not work to do on each point of a drag. */
private const val CHECK_SETTLE_MILLIS = 400L

/**
 * The shapes the deck's Check and Ship labels carry, from Typewright's own checks (layer two) on the open
 * project: ● while a check fails, ✓ on Check when none does, and on Ship ✓ once this exact drawing has been
 * written. No shape while there is no project or the first report has not been worked out. The work is
 * done on a background thread after the project has been still for a moment.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberCheckShipStatus(inWorkspace: ProjectWorkspace? = LocalProjectWorkspace.current): (DeckSection) -> StationStatus? {
    val session = inWorkspace?.current?.collectAsState()?.value ?: return NO_CHECK_SHIP_STATUS
    val failCount: State<Int?> =
        produceState<Int?>(null, session) {
            session.state
                .debounce(CHECK_SETTLE_MILLIS)
                .map { state ->
                    checkSubjectOf(OpenProject(session, state))?.takeIf { it.hasGlyphs }?.let { buildCheckReport(it.inputs).counts.fail }
                }.flowOn(Dispatchers.Default)
                .collect { value = it }
        }
    val fails = failCount.value
    val state = session.state.collectAsState().value
    val shipped = ShipMemory.shared.resultFor(state.font) != null
    return remember(session, fails, shipped) {
        { section ->
            when (section) {
                DeckSection.CHECK -> fails?.let { if (it > 0) StationStatus.TO_REFIT else StationStatus.DONE }
                DeckSection.SHIP -> shipStationStatus(fails, shipped)
                else -> null
            }
        }
    }
}

private val NO_CHECK_SHIP_STATUS: (DeckSection) -> StationStatus? = { null }
