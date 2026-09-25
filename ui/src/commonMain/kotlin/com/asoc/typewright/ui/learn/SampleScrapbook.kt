// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import com.asoc.typewright.project.scrapbook.stablePinRotationDegrees

/**
 * A small, honestly-labelled **sample** scrapbook -- not a real project's own scrapbook. Shown by
 * [ScrapbookTab] whenever no [com.asoc.typewright.project.ProjectSession] is open (`docs/
 * PROJECT_MODEL.md` §10.1); with one open, that tab reads
 * [com.asoc.typewright.project.scrapbook.ScrapbookManifest]'s own real pins plus reflection pins
 * instead ([com.asoc.typewright.project.scrapbookBoard]).
 *
 * The data model itself (`ScrapbookPin`, `ScrapbookPinKind`, `stablePinRotationDegrees`) moved to
 * `:project` in P11 WP2 stage A (docs/PROJECT_MODEL.md §10.1: "SampleScrapbook and the look
 * helpers stay in ui"); this object is the one piece of that migration that stays here, since it
 * is sample UI data, not project data.
 *
 * Reuses `ui/typewright-explorer.html`'s own `#ln-scrap` worked-example captions, kinds and
 * `.pinmark`-vs-not pattern (CLAUDE.md law 6's spirit -- this is data, not look, so law 6 does not
 * strictly bind it, but matching it keeps this sample recognisably "the explorer's own board").
 * The explorer's own board has 14 pins and 3 pinmarks; this sample keeps 4 of its actual entries
 * (three photo pins that carry the explorer's own `.pinmark`, one note pin that does not) rather
 * than inventing ten more just to hit "14" -- so the header reads `"4 pins · 3 driving the
 * design"`, the same real proportion, honestly sized down to a sample. The three photo pins' own
 * ids are chosen (among otherwise-equivalent readable spellings) so [patternVariantForPinId]
 * lands on a different [PinPatternVariant] for each -- a real screenshot showing all three the
 * *same* pattern, purely by hash coincidence on the first spelling tried, is what prompted
 * picking these; nothing about [stablePinRotationDegrees] or [patternVariantForPinId] themselves
 * changed to make this true (see `docs/OPEN_QUESTIONS.md`'s Scrapbook entry).
 */
object SampleScrapbook {
    val MANIFEST: ScrapbookManifest =
        ScrapbookManifest(
            pins =
                listOf(
                    ScrapbookPin(
                        id = "signage-charminar",
                        kind = ScrapbookPinKind.PHOTO,
                        captionTitle = "Signage · Charminar",
                        captionSource = "photo",
                        drivesDesign = true,
                        rotationDegrees = stablePinRotationDegrees("signage-charminar"),
                    ),
                    ScrapbookPin(
                        id = "primer-1912-scan",
                        kind = ScrapbookPinKind.PHOTO,
                        captionTitle = "1912 primer · scan",
                        captionSource = "Devanagari",
                        drivesDesign = true,
                        rotationDegrees = stablePinRotationDegrees("primer-1912-scan"),
                    ),
                    ScrapbookPin(
                        id = "controls-sketch",
                        kind = ScrapbookPinKind.PHOTO,
                        captionTitle = "Sketch · controls",
                        captionSource = "drawn",
                        drivesDesign = true,
                        rotationDegrees = stablePinRotationDegrees("controls-sketch"),
                    ),
                    ScrapbookPin(
                        id = "reflection-task-3",
                        kind = ScrapbookPinKind.NOTE,
                        captionTitle = "Reflection · Task 3",
                        captionSource = "note",
                        noteText = "Round ends everywhere, or nowhere. Decide before the s.",
                        drivesDesign = false,
                        rotationDegrees = stablePinRotationDegrees("reflection-task-3"),
                    ),
                ),
        )
}
