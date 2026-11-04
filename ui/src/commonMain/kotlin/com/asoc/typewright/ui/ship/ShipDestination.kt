// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

/** What choosing a place for the repository came to. */
sealed interface ShipChoice {
    /** A place was chosen, or is fixed on this platform (a zip), and writes to it can begin. */
    class Chosen(
        val target: ShipTarget,
    ) : ShipChoice

    /** The person closed the chooser without choosing. */
    data object Cancelled : ShipChoice

    /** The chooser could not be shown; [message] says why in plain words. */
    class Failed(
        val message: String,
    ) : ShipChoice
}

/**
 * Where the repository folder can go on this platform, as the page needs it: a folder the person picks on a computer, a zip the browser
 * downloads, a folder in a document tree on a phone, or nowhere where nothing is wired. One interface so the page and the controller are the
 * same everywhere and testable with a fake; each platform supplies its own ([platformShipDestination]). Choosing asks the person; nothing is
 * written before they have chosen or a build has finished.
 */
interface ShipDestination {
    /** How the repository leaves the app here. */
    val exit: ShipExit

    /**
     * Asks for the place the folder [folderName] goes. On a computer that is a folder picker (starting near [previous], the last place
     * used, when there is one); where the place is fixed (a zip) it answers at once. Never throws.
     */
    suspend fun choose(
        folderName: String,
        previous: String?,
    ): ShipChoice
}

/** A destination with nowhere to write, and [reason] why, for a build that has not wired one. */
class UnavailableShipDestination(
    private val reason: String,
) : ShipDestination {
    override val exit: ShipExit get() = ShipExit.NOWHERE

    override suspend fun choose(
        folderName: String,
        previous: String?,
    ): ShipChoice = ShipChoice.Failed(reason)
}

/** The destination this platform has: a folder picker on the desktop, a zip download in the browser, a document tree on Android. */
expect fun platformShipDestination(): ShipDestination

/** Remembers where the last repository for each project went, for the rest of the run, so the chooser opens near it and the page names it. */
class ShipTargetMemory {
    private val last = HashMap<String, String>()

    /** The place last used for [project], or null. */
    fun placeFor(project: String): String? = last[project]

    fun remember(
        project: String,
        place: String,
    ) {
        last[project] = place
    }

    companion object {
        /** The one the app shares. */
        val shared = ShipTargetMemory()
    }
}
