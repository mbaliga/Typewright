// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.capture.CaptureManifest
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.StationStatus
import com.asoc.typewright.ui.project.LocalProjectWorkspace

/**
 * Where Capture stands, for the deck's section arc: ○ when nothing is captured, ✓ when every printed sheet of the script has a
 * picture (or SVG drawings have been brought in and no sheet is expected), and ● while some sheets are still to come. Read from what
 * the project stores, so it needs no picture to be read first.
 */
fun captureStationStatus(captures: CaptureManifest): StationStatus {
    if (captures.records.isEmpty()) return StationStatus.NOT_STARTED
    val sheets = captures.records.filter { it.sheet != null && it.sheetCount != null }
    if (sheets.isEmpty()) return StationStatus.DONE
    val missing =
        sheets
            .groupBy { it.script }
            .any { (_, held) -> held.map { it.sheet }.toSet().size < held.maxOf { it.sheetCount ?: 0 } }
    return if (missing) StationStatus.TO_REFIT else StationStatus.DONE
}

private val NO_STATUS: (DeckSection) -> StationStatus? = { null }

/** The function the deck reads Capture's status shape from, kept current as pictures are added and removed; no shape when no project is open. */
@Composable
fun rememberCaptureStatus(inWorkspace: ProjectWorkspace? = LocalProjectWorkspace.current): (DeckSection) -> StationStatus? {
    val session = inWorkspace?.current?.collectAsState()?.value ?: return NO_STATUS
    val captures =
        session.state
            .collectAsState()
            .value.meta.captures
    val status = remember(captures) { captureStationStatus(captures) }
    return remember(status) { { section -> if (section == DeckSection.CAPTURE) status else null } }
}
