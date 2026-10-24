// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

/**
 * Which seats the deck's toggle arc shows and what its two circles are, for each page and state:
 * the one place that says so (SCREENS_V1 §2.4). A seat does one of three things: it switches a view
 * the page already has ([SeatKind.VIEW]), scrolls to a section the page already has and moves focus
 * to its heading ([SeatKind.REVEAL]), or opens another page ([SeatKind.OPENS], drawn with a ›).
 * Nothing here draws or navigates; the screen reads a [DeckLayout] and acts on it.
 */
enum class SeatKind { VIEW, REVEAL, OPENS }

/** One seat on the toggle arc: a [label] of at most [MAX_SEAT_LABEL] characters, and whether it can be chosen now ([why] says why not). */
data class DeckSeat(
    val id: String,
    val label: String,
    val kind: SeatKind = SeatKind.VIEW,
    val enabled: Boolean = true,
    val why: String? = null,
)

/** The page's one dominant action, which the right circle carries and the page itself does not. */
enum class DeckAction(
    val label: String,
    val circle: DeckRight,
) {
    DONE("Done", DeckRight.CHECK),
    SET_WORDMARK("Set", DeckRight.CHECK),
    USE_ANSWERS("Use these answers", DeckRight.CHECK),
    START_DRAWING("Start drawing", DeckRight.NEXT),
    DRAW_FIRST("Draw first", DeckRight.NEXT),
    NEXT("Next", DeckRight.NEXT),
    CREATE("Create", DeckRight.CHECK),
}

/**
 * What the deck shows on one page in one state. [pressed] is the seat that is on, or null when the
 * page is in a state no seat names (nothing reads as pressed: that is the truth). [action] is null
 * when the right circle is ⋯, and [actionWhyNot] is set when the action cannot run yet: the circle
 * stays visible at 35% and a tap says why.
 */
data class DeckLayout(
    val seats: List<DeckSeat>,
    val pressed: String?,
    val left: DeckLeft,
    val action: DeckAction?,
    val actionWhyNot: String? = null,
) {
    val right: DeckRight get() = action?.circle ?: DeckRight.MENU

    /** True when this page itself should draw [candidate] as a button: only when the right circle is not already carrying it. */
    fun pageCarries(candidate: DeckAction): Boolean = action != candidate
}

/** Where the Brief is, as far as the deck cares. */
enum class BriefDeckPage {
    DOORS,
    SINGLE_CHOICE,
    MULTI_CHOICE,
    WORDMARK,
    MIRROR_READING,
    MIRROR_EMPTY,
    FOCUS,
    WRAP_UP,
    BRIEF_TAB,
    GUIDE_TAB,
}

/** The three steps of S02, new project. */
enum class NewProjectStage(
    val label: String,
) {
    SOURCE("Source"),
    BRIEF("Brief"),
    METRICS("Metrics"),
}

object DeckSeats {
    const val MAX_SEAT_LABEL = 10
    const val MAX_SEATS_PER_GROUP = 4
    const val MIN_SEATS_PER_GROUP = 2
    const val MAX_GROUPS = 2

    const val SEAT_ASK = "ask"
    const val SEAT_BRIEF = "brief"
    const val SEAT_GUIDE = "guide"

    /** The Brief's views, always, at the doors too: Ask · Brief · Guide, with [pressed] on. */
    private fun briefSeats() = listOf(DeckSeat(SEAT_ASK, "Ask"), DeckSeat(SEAT_BRIEF, "Brief"), DeckSeat(SEAT_GUIDE, "Guide"))

    /**
     * The Brief's deck for [page]: Back on the left (leaving loses nothing, the answers autosave),
     * and on the right the step's dominant action, or ⋯. [canRunAction] is false while the action
     * has nothing to act on, with [whyNot] saying so.
     */
    fun brief(
        page: BriefDeckPage,
        canRunAction: Boolean = true,
        whyNot: String? = null,
    ): DeckLayout {
        val pressed =
            when (page) {
                BriefDeckPage.BRIEF_TAB -> SEAT_BRIEF
                BriefDeckPage.GUIDE_TAB -> SEAT_GUIDE
                else -> SEAT_ASK
            }
        val action =
            when (page) {
                BriefDeckPage.MULTI_CHOICE -> DeckAction.DONE
                BriefDeckPage.WORDMARK -> DeckAction.SET_WORDMARK
                BriefDeckPage.MIRROR_READING -> DeckAction.USE_ANSWERS
                BriefDeckPage.MIRROR_EMPTY, BriefDeckPage.GUIDE_TAB -> DeckAction.START_DRAWING
                BriefDeckPage.WRAP_UP -> DeckAction.DRAW_FIRST
                BriefDeckPage.DOORS, BriefDeckPage.SINGLE_CHOICE, BriefDeckPage.FOCUS, BriefDeckPage.BRIEF_TAB -> null
            }
        return DeckLayout(
            seats = briefSeats(),
            pressed = pressed,
            left = DeckLeft.BACK,
            action = action,
            actionWhyNot = if (action != null && !canRunAction) whyNot else null,
        )
    }

    /** The label the right circle announces for [action] on a Brief page that has already got letters: Draw next, not Draw first. */
    fun actionLabel(
        action: DeckAction,
        lettersExist: Boolean,
    ): String = if (action == DeckAction.DRAW_FIRST && lettersExist) "Draw next" else action.label

    /**
     * S02's deck: the steps are the seats, Close on the left (the one place leaving discards
     * something), and → Next, then ✓ Create on the last built step. A later step can be chosen once
     * the earlier ones are valid; [built] lists the steps that have a screen.
     */
    fun newProject(
        stage: NewProjectStage,
        built: Set<NewProjectStage>,
        valid: Set<NewProjectStage>,
    ): DeckLayout {
        val seats =
            NewProjectStage.entries.map { step ->
                val earlier = NewProjectStage.entries.filter { it.ordinal < step.ordinal }
                val reachable = step in built && earlier.all { it in valid || it == stage }
                DeckSeat(
                    id = step.name.lowercase(),
                    label = step.label,
                    enabled = reachable || step == stage,
                    why =
                        when {
                            step == stage || reachable -> null
                            step !in built && step in valid -> "${step.label} was chosen when the project was made"
                            step !in built -> "${step.label} isn't built yet"
                            else -> "Finish the earlier steps first"
                        },
                )
            }
        val lastBuilt = NewProjectStage.entries.last { it in built }
        return DeckLayout(
            seats = seats,
            pressed = stage.name.lowercase(),
            left = DeckLeft.CLOSE,
            action = if (stage == lastBuilt) DeckAction.CREATE else DeckAction.NEXT,
        )
    }

    /** What the right circle announces on the import audit (S03). */
    const val IMPORT_LABEL = "Import"

    /**
     * S03's deck, the import licence audit: Close on the left (leaving discards the import), no seats,
     * and ✓ on the right as the one action, Import. It stays visible but dimmed, with [whyNot] as the
     * reason a tap gives, while the audit refuses the font or a new name is still needed.
     */
    fun importAudit(
        canImport: Boolean,
        whyNot: String?,
    ): DeckLayout =
        DeckLayout(
            seats = emptyList(),
            pressed = null,
            left = DeckLeft.CLOSE,
            action = DeckAction.DONE,
            actionWhyNot = if (canImport) null else whyNot,
        )

    /** What is wrong with [layout] against the rules for seats, or empty. */
    fun problems(layout: DeckLayout): List<String> =
        buildList {
            if (layout.seats.size !in MIN_SEATS_PER_GROUP..MAX_SEATS_PER_GROUP) add("a group has ${layout.seats.size} seats")
            layout.seats.filter { it.label.length > MAX_SEAT_LABEL }.forEach { add("'${it.label}' is longer than $MAX_SEAT_LABEL") }
            if (layout.seats
                    .map { it.id }
                    .toSet()
                    .size != layout.seats.size
            ) {
                add("seat ids repeat")
            }
            if (layout.pressed != null &&
                layout.seats.none { it.id == layout.pressed }
            ) {
                add("pressed seat '${layout.pressed}' is not a seat")
            }
            layout.seats.filter { !it.enabled && it.why == null }.forEach { add("'${it.label}' is off with no reason") }
        }

    /** A seat remembered from last time, dropped when the page no longer has it (stored data is never trusted). */
    fun sanitise(
        stored: String?,
        layout: DeckLayout,
    ): String? = stored?.takeIf { id -> layout.seats.any { it.id == id && it.enabled } }

    /** The seat to show pressed for a page state named [state]: the seat with that id, or none. */
    fun resolve(
        state: String?,
        layout: DeckLayout,
    ): String? = layout.seats.firstOrNull { it.id == state }?.id
}
