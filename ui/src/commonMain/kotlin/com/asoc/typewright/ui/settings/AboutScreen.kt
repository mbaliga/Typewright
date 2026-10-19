// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.brief.bodyStyle
import com.asoc.typewright.ui.brief.monoStyle
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.shell.PageMenuItem
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor

/** The app's version: the one place it is written in the UI, kept equal to the Android `versionName`. */
const val APP_VERSION: String = "0.0.1"

const val LICENCE_SENTENCE: String =
    "The app is under the Functional Source License (FSL-1.1-ALv2) and its engine is Apache-2.0. The fonts you make are yours."

const val PRIVACY_STATEMENT: String =
    "Typewright collects nothing. The only network use is what you ask for: fetching a comparison font, and builds you start."

/**
 * About & licences (S23): the name and version, who made it, the licence in one sentence, the
 * privacy statement, and every third-party notice in THIRD_PARTY.md (generated into [NOTICE_SECTIONS],
 * and checked in CI against the dependency catalogue so a dependency without an entry fails the build).
 * The support link is not shown: there is no address for it yet.
 */
@Composable
fun AboutScreen(
    texture: CanvasTexture,
    section: DeckSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    SectionScaffold(
        texture = texture,
        section = section,
        title = "About Typewright",
        subtitle = "Version $APP_VERSION",
        modifier = modifier,
        onBack = onBack,
        dimSections = true,
        dimReason = "Leave About first",
        menuItems =
            listOf(
                PageMenuItem("Copy version info", "Typewright $APP_VERSION") {
                    clipboard.setText(AnnotatedString("Typewright $APP_VERSION"))
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(text = "A System of Cells", style = bodyStyle(fg, sizeSp = 15.0, weight = FontWeight.SemiBold))
            BasicText(text = LICENCE_SENTENCE, style = bodyStyle(fg))
            BasicText(text = PRIVACY_STATEMENT, style = bodyStyle(fg))
            BasicText(text = "THIRD-PARTY NOTICES", style = monoStyle(muted), modifier = Modifier.padding(top = 18.dp))
            for (notice in NOTICE_SECTIONS) {
                BasicText(
                    text = notice.title,
                    style = bodyStyle(fg, weight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 10.dp),
                )
                for (line in notice.lines) BasicText(text = line, style = bodyStyle(muted, sizeSp = 11.0))
            }
        }
    }
}
