// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class PaperTokensTest {
    @Test
    fun paperMatchesTheExplorer() {
        assertEquals(Color(0xFFEFE9DC), PaperTokens.Canvas)
        assertEquals(Color(0xFF17150F), PaperTokens.Ink)
    }
}
