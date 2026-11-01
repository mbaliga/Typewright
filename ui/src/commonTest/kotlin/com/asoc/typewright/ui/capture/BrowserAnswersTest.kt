// SPDX-License-Identifier: FSL-1.1-ALv2

@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.asoc.typewright.ui.capture

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BrowserAnswersTest {
    private fun record(
        name: String,
        vararg bytes: Byte,
    ) = name + "\u0000" + Base64.encode(bytes)

    @Test
    fun severalFilesComeBackInOrderWithTheirBytes() {
        val answer = listOf(record("a.png", 1, 2, 3), record("b.svg", 9)).joinToString("\u0001")
        val picked = assertIs<FilePickOutcome.Picked>(picksFromBrowserAnswer(answer))
        assertEquals(listOf("a.png", "b.svg"), picked.files.map { it.name })
        assertContentEquals(byteArrayOf(1, 2, 3), picked.files[0].bytes)
    }

    @Test
    fun noAnswerMeansTheyCancelled() {
        assertEquals(FilePickOutcome.Cancelled, picksFromBrowserAnswer(null))
        assertEquals(FilePickOutcome.Cancelled, picksFromBrowserAnswer(""))
    }

    @Test
    fun aFileWithNoNameBadBytesOrTooManyBytesAnswersInPlainWords() {
        assertTrue("which file" in assertIs<FilePickOutcome.Failed>(picksFromBrowserAnswer("\u0000AAAA")).message)
        assertTrue("could not be read" in assertIs<FilePickOutcome.Failed>(picksFromBrowserAnswer("x.png\u0000!!!!")).message)
        val big = assertIs<FilePickOutcome.Failed>(picksFromBrowserAnswer(record("big.png", 1, 2, 3, 4, 5, 6), maxBytes = 3))
        assertTrue("big.png" in big.message && "too large" in big.message, big.message)
    }
}
