// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.shape.preview

/**
 * iOS shaper: an honest stub (this interface's own KDoc: "no shaper fakes glyphs"). The plan
 * expects Skiko's `Shaper` (Skia's SkShaper over HarfBuzz) to be present in Skiko-iOS because
 * Compose text on iOS runs on Skia's paragraph stack, but that has not been checked; the
 * one-day spike (docs/PORTING_PLAN.md IOS-3, section 8 item 8) decides it. Until a Mac run
 * answers it this returns [ShapeResult.NotImplemented] and the UI keeps saying so.
 */
class IosShaperStub : Shaper {
    override val stack: ShapingStack = ShapingStack.SKIKO_SHAPER

    override suspend fun shape(request: ShapeRequest): ShapeResult =
        ShapeResult.NotImplemented(
            stack = stack,
            planned =
                "Not built on iOS. Planned: Skiko's Shaper (SkShaper over HarfBuzz), if the iOS " +
                    "build of Skiko exposes it; that is unverified and needs a spike on a Mac. " +
                    "Conjunct and joining previews (Devanagari, Naskh) wait on it.",
        )
}

actual fun platformShaper(): Shaper = IosShaperStub()
