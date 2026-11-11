// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.project.StoreTreeWriter
import com.asoc.typewright.ui.ship.ShipChoice
import com.asoc.typewright.ui.ship.ShipTarget

/**
 * Ship's folder on Android: the person picks a folder through the system's document tree ([pick], the same picker the project folder uses),
 * and the repository is written into that folder through the store [storage] makes for it, so every file goes through the document tree and
 * the whole write is undone if one fails ([StoreTreeWriter]). The folder is the repository itself: a folder the person made for it, or one
 * that already holds it. A phone cannot compile the font, so the target takes a folder without one.
 *
 * The pieces that need a device (the system picker, the grant it keeps, and the document tree itself) are owner-verified only; what is
 * decided here, and the writes through a store, are covered by tests that run without one.
 */
class AndroidShipFolderChooser(
    private val pick: suspend () -> ProjectLocation.SafTree?,
    private val storage: StorageProvider,
) {
    /** Asks for the folder and answers the target, or why there is none. [folderName] is the name the repository would have. */
    suspend fun choose(folderName: String): ShipChoice {
        val tree = pick() ?: return ShipChoice.Cancelled
        return when (val found = storage.storeFor(tree)) {
            is StoreLookup.Available -> {
                val label = found.store.displayName
                ShipChoice.Chosen(ShipTarget(label, StoreTreeWriter(found.store, label), withoutFontOk = true))
            }

            StoreLookup.PermissionNeeded -> {
                ShipChoice.Failed(
                    "Android did not keep the permission to write to that folder. Choose it again and allow Typewright to edit it " +
                        "(the folder for $folderName).",
                )
            }

            StoreLookup.Missing -> {
                ShipChoice.Failed("Typewright could not open that folder. It may have been moved or deleted; choose another.")
            }
        }
    }
}
