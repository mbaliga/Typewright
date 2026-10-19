// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** [referenceScrapbookPin] (WP5's "Add to scrapbook" under a chosen reference), without needing a project or a Compose click. */
class BriefScreenTest {
    @Test
    fun namesTheFaceAndItsDescribeFaceLine() {
        val pin = referenceScrapbookPin(family = "Futura", detail = "monoline, circular rounds", id = "pin-7")
        assertEquals("pin-7", pin.id)
        assertEquals(ScrapbookPinKind.NOTE, pin.kind)
        assertEquals("Futura", pin.captionTitle)
        assertEquals("reference", pin.captionSource)
        assertEquals("Futura: monoline, circular rounds", pin.noteText)
    }

    @Test
    fun fallsBackToJustTheNameWithNoDetail() {
        val pin = referenceScrapbookPin(family = "Futura", detail = "", id = "pin-8")
        assertEquals("Futura", pin.noteText)
    }
}
