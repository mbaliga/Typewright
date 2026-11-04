// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.shell.LocalOpenProjects
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor

/**
 * What a view of "your font" shows when no project is open: it says to open or start one, names what
 * would be here ([what], e.g. `the Anatomy Lens`), and offers Projects. It never draws a stand-in
 * font in its place.
 */
@Composable
fun NoProjectState(
    texture: CanvasTexture,
    what: String,
    modifier: Modifier = Modifier,
) {
    val openProjects = LocalOpenProjects.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            text = "Open or start a project to see $what.",
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = texture.fg.toColor(),
                ),
        )
        BasicText(
            text = "This view is about your own letters, so it has nothing to show until a font is open.",
            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, lineHeight = 18.sp, color = texture.muted.toColor()),
        )
        if (openProjects != null) {
            BasicText(
                text = "Projects ›",
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = texture.accent.toColor(),
                    ),
                modifier = Modifier.clickable(onClick = openProjects).padding(vertical = 6.dp),
            )
        }
    }
}
