// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.brief.bodyStyle
import com.asoc.typewright.ui.brief.monoStyle
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.shell.PageMenuItem
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

const val LICENCE_SENTENCE: String =
    "The app is under the Functional Source License (FSL-1.1-ALv2) and its engine is Apache-2.0. The fonts you make are yours."

const val PRIVACY_STATEMENT: String =
    "Typewright collects nothing. The only network use is what you ask for: fetching a comparison font, and builds you start."

/** The libraries and fonts About names up front; the notices below it carry every entry in THIRD_PARTY.md. */
val BUILT_WITH: List<Pair<String, String>> =
    listOf(
        "Kotlin, Compose Multiplatform" to "Apache-2.0",
        "Skia (through Skiko on desktop and web)" to "BSD-3-Clause",
        "HarfBuzz" to "Old MIT",
        "FreeType" to "FTL",
        "libpng, zlib, Expat, libjpeg-turbo, libwebp, ICU" to "their own notices below",
        "The sample font and the Brief's cue faces" to "OFL-1.1",
        "fontmake, fontTools, Fontbakery (run from your own Python, not bundled)" to "Apache-2.0, MIT",
    )

/** The version text About copies: the name, version, build number and, where the build says which it is, its channel. */
fun aboutVersionLine(channel: DistributionChannel): String =
    "Typewright $APP_VERSION (build $APP_VERSION_CODE)" +
        if (channel == DistributionChannel.UNSPECIFIED) "" else " · ${channel.label}"

const val PRIVACY_POLICY_LINK_TEXT: String = "Read the privacy policy"

/**
 * About & licences (S23): the name and version, who made it, the licence in one sentence, the
 * privacy statement and its policy, the support link where the build has one, what it is built
 * with, and every third-party notice in THIRD_PARTY.md (generated into [NOTICE_SECTIONS], and
 * checked in CI against the dependency catalogue so a dependency without an entry fails the
 * build). The same screen is in every target's bundle, so the notices ship with the web build too.
 */
@Composable
fun AboutScreen(
    texture: CanvasTexture,
    section: DeckSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    channel: DistributionChannel = BuildChannel.current,
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val accent = texture.accent.toColor()
    val versionLine = aboutVersionLine(channel)

    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    @Composable
    fun Label(text: String) = BasicText(text = text.uppercase(), style = monoStyle(muted), modifier = Modifier.padding(top = 14.dp))

    @Composable
    fun Link(
        text: String,
        url: String,
    ) = BasicText(
        text = "$text ›",
        style = bodyStyle(accent, weight = FontWeight.SemiBold),
        modifier = Modifier.clickable { open(url) }.padding(vertical = 6.dp),
    )

    SectionScaffold(
        texture = texture,
        section = section,
        title = "About Typewright",
        subtitle = "Version $APP_VERSION" + if (channel.isPreview) " · Preview" else "",
        modifier = modifier,
        onBack = onBack,
        dimSections = true,
        dimReason = "Leave About first",
        menuItems =
            listOf(
                PageMenuItem("Copy version info", versionLine) {
                    clipboard.setText(AnnotatedString(versionLine))
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(text = "A System of Cells", style = bodyStyle(fg, sizeSp = 15.0, weight = FontWeight.SemiBold))
            if (channel.isPreview) BasicText(text = WEB_PREVIEW_NOTICE, style = bodyStyle(fg))
            Label("Licence")
            BasicText(text = LICENCE_SENTENCE, style = bodyStyle(fg))
            Label("Privacy")
            BasicText(text = PRIVACY_STATEMENT, style = bodyStyle(fg))
            Link(PRIVACY_POLICY_LINK_TEXT, PRIVACY_POLICY_URL)
            supportRow(channel)?.let { row ->
                Label("Support")
                if (row.url != null) {
                    BasicText(text = "It is free for everyone. Supporting unlocks nothing.", style = bodyStyle(fg))
                    Link(row.text, row.url)
                } else {
                    BasicText(text = row.text, style = bodyStyle(fg))
                }
            }
            Label("Built with")
            for ((name, licence) in BUILT_WITH) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(text = name, style = bodyStyle(fg), modifier = Modifier.weight(1f))
                    BasicText(text = licence, style = monoStyle(muted), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Link("Source, changelog and the notices in full", SOURCE_URL)
            Label("Third-party notices")
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
