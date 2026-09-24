package dev.aarso.typewright.app.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.aarso.typewright.ui.TypewrightApp

/** The id of the element in index.html that the app mounts into. */
const val ROOT_ELEMENT_ID: String = "typewright"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(ROOT_ELEMENT_ID) {
        TypewrightApp()
    }
}
