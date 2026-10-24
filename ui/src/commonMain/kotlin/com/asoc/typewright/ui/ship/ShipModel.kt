// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.ship.GfCategory
import com.asoc.typewright.qa.ship.ShipBuilt
import com.asoc.typewright.qa.ship.ShipCapability
import com.asoc.typewright.qa.ship.ShipGate
import com.asoc.typewright.qa.ship.ShipInputs
import com.asoc.typewright.qa.ship.ShipItem
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.qa.ship.ShipPlan
import com.asoc.typewright.qa.ship.ShipSituation
import com.asoc.typewright.qa.ship.buildShipPlan
import com.asoc.typewright.qa.ship.shipChecklist
import com.asoc.typewright.qa.ship.shipGate
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.shell.FontInfoField

/** The views of Ship: the checklist, and Font info, which a line's link opens over it on the value to set. */
enum class ShipView {
    READY,
    FONT_INFO,
}

/** What the person has chosen on the page so far, held where a tap and a screenshot test can both set it. */
@Stable
class ShipPageState(
    mode: ShipMode = ShipMode.GOOGLE_FONTS,
    view: ShipView = ShipView.READY,
    fontInfoField: FontInfoField? = null,
    chosenCategory: GfCategory? = null,
    target: ShipTarget? = null,
    showOutput: Boolean = false,
    showRequest: Boolean = false,
    message: String? = null,
) {
    var mode by mutableStateOf(mode)
    var view by mutableStateOf(view)

    /** The part of Font info a line's link asked for. */
    var fontInfoField by mutableStateOf(fontInfoField)

    /** The category the person chose, which beats the one read from their style class. */
    var chosenCategory by mutableStateOf(chosenCategory)

    /** Where the folder goes, once the person has chosen. */
    var target by mutableStateOf(target)
    var showOutput by mutableStateOf(showOutput)

    /** Whether the finished page shows the request text. */
    var showRequest by mutableStateOf(showRequest)

    /** A line the page shows at its head: a cancelled build, a chooser that could not be shown. */
    var message by mutableStateOf(message)
}

/**
 * What this platform can do for Ship: how the folder leaves ([exit]), whether it can compile ([compile] is the backend's own sentence either
 * way) and where the compiling happens ([location], null when it does not).
 */
data class ShipPlatform(
    val exit: ShipExit,
    val compile: ShipCapability,
    val location: CompileLocation?,
) {
    /** Whether a build can run here: a compiler, or a place that takes a folder without a font. */
    val canBuild: Boolean get() = compile.available || exit == ShipExit.ZIP || exit == ShipExit.DOCUMENT_TREE

    /** Whether the font is compiled here. */
    val compiles: Boolean get() = compile.available
}

/**
 * Everything the Ready page shows, worked out once from the project, Check and the platform: the gate, the category, the preview of the
 * folder (names and the description draft's size; no file is read), the checklist, and what the deck's right circle is. No Compose here.
 */
class ShipSheet(
    val subject: ShipSubject,
    val mode: ShipMode,
    val report: CheckReport?,
    chosenCategory: GfCategory?,
    val platform: ShipPlatform,
) {
    val gate: ShipGate = shipGate(report)

    /** The category METADATA will carry: the person's choice, else the reading of their style class, else none. */
    val category: GfCategory? = chosenCategory ?: subject.categoryReading

    /** What the folder would hold, by name. Nothing here is a file's contents but the description draft and the text files, which are the real text. */
    val preview: ShipPlan =
        buildShipPlan(
            ShipInputs(
                mode = mode,
                facts = subject.facts,
                font = subject.font,
                brief = subject.brief,
                ufoFolderName = subject.ufoFolderName,
                ufo = mapOf(PREVIEW_ENTRY to ByteArray(0)),
                binary = if (platform.compiles) ByteArray(0) else null,
                category = category,
                layerOne = null,
                today = PREVIEW_DATE,
            ),
        )

    private val categoryClass: String? = if (chosenCategory == null && category != null) subject.categoryFromClass else null

    private fun situation(built: ShipBuilt?) =
        ShipSituation(
            mode = mode,
            facts = subject.facts,
            gate = gate,
            checkTally = report?.counts?.line()?.let { "${report.items.size} checks · $it" },
            names = namesSeverity(report),
            category = category,
            categoryClass = categoryClass,
            descriptionBytes = preview.files["DESCRIPTION.en_us.html"]?.size ?: 0,
            compile = platform.compile,
            built = built,
        )

    /** The lines, before anything is built. */
    val checklist: List<ShipItem> = shipChecklist(situation(null))

    /** The lines after [result] was written: what the write did is done, and what the project still does not state still needs the person. */
    fun checklistAfter(result: ShipResult): List<ShipItem> =
        shipChecklist(
            situation(ShipBuilt(result.compileMillis?.let { (it / MILLIS_PER_SECOND).toInt() }, result.checkLine, result.withFont)),
        )

    /** The deck's right circle on this page. */
    val primary: ShipPrimary = shipPrimary(gate, platform)

    private companion object {
        const val PREVIEW_ENTRY = "metainfo.plist"
        const val PREVIEW_DATE = "0000-00-00"
        const val MILLIS_PER_SECOND = 1000L
    }
}

/** The deck's right circle on Ready: which glyph, what it is called, whether it is at 35%, and what a tap says then. */
data class ShipPrimary(
    val right: DeckRight,
    val label: String,
    val dimmed: Boolean,
    val why: String?,
)

/** The primary action for [gate] on [platform]: dimmed while Check has fails, and where nothing can be built or written. */
fun shipPrimary(
    gate: ShipGate,
    platform: ShipPlatform,
): ShipPrimary {
    val right = if (platform.exit == ShipExit.ZIP) DeckRight.DOWNLOAD else DeckRight.BUILD
    val label = ShipText.actionLabel(platform.exit, platform.compiles)
    return when {
        gate.blocked -> ShipPrimary(right, label, dimmed = true, why = gate.explanation())
        platform.exit == ShipExit.NOWHERE -> ShipPrimary(right, label, dimmed = true, why = ShipText.notChosen(ShipExit.NOWHERE) + ".")
        !platform.canBuild -> ShipPrimary(right, label, dimmed = true, why = platform.compile.words)
        else -> ShipPrimary(right, label, dimmed = false, why = null)
    }
}

/** What the page puts on the glass: its title and subtitle, the deck's toggle arc, and the two circles. */
data class ShipChrome(
    val title: String,
    val subtitle: String,
    val toggles: List<DeckToggle>,
    val left: DeckLeft,
    val right: DeckRight,
    val rightDimmed: Boolean,
    val rightLabel: String,
    val rightWhy: String?,
)

/** Chrome for the checklist page: the mode toggles, Back, and the primary circle. */
fun readyChrome(
    title: String,
    mode: ShipMode,
    platform: ShipPlatform,
    primary: ShipPrimary,
): ShipChrome =
    ShipChrome(
        title = ShipText.title(title),
        subtitle = ShipText.subtitle(mode, platform.exit),
        toggles = ShipMode.entries.map { DeckToggle(it.label, on = it == mode) },
        left = DeckLeft.BACK,
        right = primary.right,
        rightDimmed = primary.dimmed,
        rightLabel = primary.label,
        rightWhy = primary.why,
    )

/** Chrome for the building page (S17): Close cancels, no toggles, no right circle. */
fun buildingChrome(
    title: String,
    platform: ShipPlatform,
): ShipChrome =
    ShipChrome(
        title = ShipText.buildingTitle(title),
        subtitle = ShipText.buildingSubtitle(platform.location.takeIf { platform.compiles }, platform.exit),
        toggles = emptyList(),
        left = DeckLeft.CLOSE,
        right = DeckRight.NONE,
        rightDimmed = false,
        rightLabel = "",
        rightWhy = null,
    )

/** Chrome for a finished, stopped or failed page: Back returns to the checklist, and the right circle builds again. */
fun afterChrome(
    title: String,
    subtitle: String,
    platform: ShipPlatform,
    primary: ShipPrimary,
    again: String,
): ShipChrome =
    ShipChrome(
        title = ShipText.title(title),
        subtitle = subtitle,
        toggles = emptyList(),
        left = DeckLeft.BACK,
        right = primary.right,
        rightDimmed = primary.dimmed,
        rightLabel = again,
        rightWhy = primary.why,
    )
