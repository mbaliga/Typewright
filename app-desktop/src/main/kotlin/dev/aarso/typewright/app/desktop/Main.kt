// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.app.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.aarso.typewright.ui.TypewrightApp

/** The window title. */
const val APP_TITLE: String = "Typewright"

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = APP_TITLE) {
            TypewrightApp()
        }
    }
