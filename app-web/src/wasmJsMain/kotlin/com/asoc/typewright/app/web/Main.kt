// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.asoc.typewright.ui.TypewrightApp

/** The id of the element in index.html that the app mounts into. */
const val ROOT_ELEMENT_ID: String = "typewright"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(ROOT_ELEMENT_ID) {
        TypewrightApp()
    }
}
