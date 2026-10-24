// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asoc.typewright.project.KernScope
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckToggle

/** The two views of the Space page: the sidebearings of each letter, or the pair values between letters. */
enum class SpaceMode { SPACING, KERNING }

/** A sub-view of Space that replaces the string: reached from the ⋯ menu, left with Back. */
enum class SpacePanel { GROUPS, FEATURES, STRINGS }

/** One line on the glass about the last edit; [unlockGlyph] is set when the project refused because that glyph is locked, and the screen offers Unlock. */
data class SpaceNotice(
    val text: String,
    val unlockGlyph: String? = null,
)

/**
 * What the page is on: the selected letter, and in Kerning the pair of letters (first, second) the selected junction
 * stands for; [index] is its place among the [cells] letters of the string, and [select] moves the selection to another.
 */
data class SpaceTarget(
    val glyph: String?,
    val pair: Pair<String, String>?,
    val index: Int = 0,
    val cells: Int = 0,
    val select: (Int) -> Unit = {},
)

/**
 * What the Space page is showing and what its deck does about it: the view ([mode]), whether the
 * string is drawn as it stands now or as it stood at the Before ([showAfter]), the selected letter
 * or junction, the number that arrows and drags change ([field]), the level a kerning value is
 * written at ([scope]), which sub-view is open ([panel]) and the ⋯ menu. Everything the deck's
 * circles and seats do is decided here, so it is the same on every screen size and a test can read it
 * without a window.
 */
@Stable
class SpaceRoomState {
    var mode: SpaceMode by mutableStateOf(SpaceMode.SPACING)
        private set

    /** True for the string as it is now; false only in Kerning with Before chosen. */
    var showAfter: Boolean by mutableStateOf(true)
        private set

    /** The selected letter's position in the string; in Kerning the junction that starts at it (or ends at it, for the last letter). */
    var selected: Int by mutableIntStateOf(0)

    /** Where the page is, published by the page as it draws so a key press edits what is on screen. */
    var target: SpaceTarget? by mutableStateOf(null)

    /** The number the arrow keys change and a drag scrubs. */
    var field: SpaceField by mutableStateOf(SpaceField.RIGHT)

    /** Where a typed or dragged kerning value is written when the letters are in groups. */
    var scope: KernScope by mutableStateOf(KernScope.GROUPS)

    var panel: SpacePanel? by mutableStateOf(null)
        private set

    var menuOpen: Boolean by mutableStateOf(false)

    /** The control string being set; null until one is chosen, which means the first. */
    var activeString: String? by mutableStateOf(null)

    var notice: SpaceNotice? by mutableStateOf(null)

    /** True while an inspector value is being typed. */
    var editingField: Boolean by mutableStateOf(false)

    /** Counts up to put away a value being typed without committing it. */
    var cancelEditTick: Int by mutableIntStateOf(0)
        private set

    /**
     * The `.fea` text the person has typed and not saved, or null while the editor shows the saved text. It lives here, not
     * in the editor, so it outlives the editor: a way out that nothing on this page can stop (the command palette, a swipe
     * to another room, the keyboard) leaves it where it was, and Space shows it again, marked as not saved ([draftKept]).
     */
    var featuresDraft: String? by mutableStateOf(null)

    /** True while the `.fea` editor holds text that is not saved. */
    var featuresDirty: Boolean by mutableStateOf(false)

    /** True when the page was left with unsaved `.fea` text and has been opened again: the editor says the text it shows was kept. */
    var draftKept: Boolean by mutableStateOf(false)
        private set

    /** True while the screen asks whether to throw the unsaved `.fea` text away. */
    var confirmingDiscard: Boolean by mutableStateOf(false)

    /** What to do once the person has agreed to throw the unsaved `.fea` text away (open a sub-view, switch a seat, leave); null for closing the editor. */
    private var pendingExit: (() -> Unit)? = null

    /** Whether the person can edit now: not while the string is shown as it stood at Before. */
    val editable: Boolean get() = showAfter

    /** The unsaved `.fea` text is put away on purpose: saved, or put back to what the project holds. */
    fun discardDraft() {
        featuresDraft = null
        draftKept = false
    }

    /** The page has been opened again: unsaved `.fea` text that is still held is said to have been kept. */
    fun reopened() {
        if (featuresDirty) draftKept = true
    }

    /**
     * Runs [exit] now, unless unsaved `.fea` text would be lost by it: then it asks first, and runs [exit] only if the
     * person agrees. Every way out of the editor that this page can see goes through here: the left circle, a seat on the toggle
     * arc, a section on the section arc, the Map, and the other entries of the ⋯ menu.
     */
    fun guardExit(exit: () -> Unit) {
        if (panel == SpacePanel.FEATURES && featuresDirty) {
            pendingExit = exit
            menuOpen = false
            confirmingDiscard = true
        } else {
            exit()
        }
    }

    /** The person agreed to throw the unsaved `.fea` text away: it goes, and what was asked for happens. */
    fun confirmDiscard() {
        val exit = pendingExit
        closePanel()
        exit?.invoke()
    }

    /** The person kept editing. */
    fun cancelDiscard() {
        confirmingDiscard = false
        pendingExit = null
    }

    fun showMode(next: SpaceMode) {
        if (mode == next) return
        mode = next
        // Before and After exist only in Kerning: leaving it shows the string as it is, so edits land on what the person sees.
        if (next == SpaceMode.SPACING) showAfter = true
        field =
            when {
                next == SpaceMode.KERNING -> SpaceField.KERN
                field == SpaceField.KERN -> SpaceField.RIGHT
                else -> field
            }
        notice = null
    }

    fun showAfter(after: Boolean) {
        showAfter = after || mode != SpaceMode.KERNING
    }

    fun open(panel: SpacePanel) {
        // Text that is the same as what the project holds is not a draft, and must not outlive a change to what the project holds.
        if (!featuresDirty) featuresDraft = null
        this.panel = panel
        menuOpen = false
        notice = null
    }

    /** Opens a sub-view from the ⋯ menu, asking first if that would throw away unsaved `.fea` text. Asking for the editor already open changes nothing. */
    fun requestPanel(next: SpacePanel) {
        if (next == panel) {
            menuOpen = false
            return
        }
        guardExit { open(next) }
    }

    /** Leaves the sub-view and drops what it held: the unsaved `.fea` text and the last notice. */
    fun closePanel() {
        panel = null
        featuresDirty = false
        featuresDraft = null
        draftKept = false
        confirmingDiscard = false
        pendingExit = null
        notice = null
    }

    /** The left circle: Back, or Close while a value is being typed or `.fea` text would be lost. */
    fun left(): DeckLeft = if (editingField || (panel == SpacePanel.FEATURES && featuresDirty)) DeckLeft.CLOSE else DeckLeft.BACK

    /**
     * What the left circle (and Esc, and the system back) does: put away a value being typed, else shut
     * the menu, else ask before losing `.fea` text, else leave a sub-view, else [leave] the page.
     */
    fun onLeft(leave: () -> Unit) {
        when {
            editingField -> cancelEditTick++
            menuOpen -> menuOpen = false
            confirmingDiscard -> cancelDiscard()
            panel == SpacePanel.FEATURES && featuresDirty -> guardExit(::closePanel)
            panel != null -> closePanel()
            else -> leave()
        }
    }

    /** The deck's toggles: Spacing · Kerning, and in Kerning a second group, Before · After. */
    fun toggles(): List<DeckToggle> =
        buildList {
            add(DeckToggle("Spacing", on = mode == SpaceMode.SPACING))
            add(DeckToggle("Kerning", on = mode == SpaceMode.KERNING))
            if (mode == SpaceMode.KERNING) {
                add(DeckToggle("Before", on = !showAfter, groupStart = true))
                add(DeckToggle("After", on = showAfter))
            }
        }

    /** A tap on seat [index] of [toggles]. */
    fun onToggle(index: Int) {
        guardExit {
            closePanel()
            when (index) {
                SEAT_SPACING -> showMode(SpaceMode.SPACING)
                SEAT_KERNING -> showMode(SpaceMode.KERNING)
                SEAT_BEFORE -> showAfter(false)
                SEAT_AFTER -> showAfter(true)
            }
        }
    }

    companion object {
        const val SEAT_SPACING = 0
        const val SEAT_KERNING = 1
        const val SEAT_BEFORE = 2
        const val SEAT_AFTER = 3
    }
}
