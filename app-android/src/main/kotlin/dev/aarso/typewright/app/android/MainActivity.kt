// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.aarso.typewright.ui.TypewrightApp

/** Hosts the shared UI. On-device behaviour is owner-verified only (CLAUDE.md law 4). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TypewrightApp() }
    }
}
