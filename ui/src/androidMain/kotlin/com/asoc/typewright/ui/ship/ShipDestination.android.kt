// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

/** Android's destination: a folder in a document tree the person grants, once the app has connected its folder picker. */
actual fun platformShipDestination(): ShipDestination = AndroidShipDestination

/**
 * What the Android app connects to let Ship write to a folder the person grants through the system's document picker: [chooseFolder] asks
 * for the grant and answers a target whose writer is a `StoreTreeWriter` over the store of that tree. `MainActivity` sets it to
 * `AndroidShipFolderChooser`, over the same picker and storage the project folder uses, and clears it when the activity goes. Where nothing
 * has set it, Ship says so in words and writes nothing. The picker and the document tree themselves are owner-verified only: this container
 * has no phone (CLAUDE.md law 4).
 */
object AndroidShipSupport {
    /** Asks the person for a folder and answers where Ship writes the folder named [folderName] to, or why it cannot. Set by the Android app. */
    var chooseFolder: (suspend (folderName: String) -> ShipChoice)? = null
}

private object AndroidShipDestination : ShipDestination {
    override val exit: ShipExit get() = if (AndroidShipSupport.chooseFolder == null) ShipExit.NOWHERE else ShipExit.DOCUMENT_TREE

    override suspend fun choose(
        folderName: String,
        previous: String?,
    ): ShipChoice {
        val choose =
            AndroidShipSupport.chooseFolder
                ?: return ShipChoice.Failed(
                    "This build has not connected the phone's folder picker to Ship, so there is nowhere to write the folder.",
                )
        return try {
            choose(folderName)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ShipChoice.Failed("The folder picker could not be shown: ${e.message ?: e::class.simpleName}.")
        }
    }
}
