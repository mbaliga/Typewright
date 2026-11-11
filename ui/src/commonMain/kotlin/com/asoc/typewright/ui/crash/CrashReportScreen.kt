// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor

/**
 * Shown on the launch after a crash: what stopped, with Copy so the person can send it, and Continue to
 * go on. The report is theirs to share; the app sends nothing (law 3).
 */
@Composable
fun CrashReportScreen(
    report: String,
    texture: CanvasTexture,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier.fillMaxSize().background(texture.canvas.toColor()).padding(horizontal = 22.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("Typewright stopped last time", style = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
        BasicText(
            "This report is kept on this device only. Copy it to send it to whoever is helping you; nothing is sent from here.",
            style = TextStyle(color = muted, fontSize = 13.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Action("Copy the report ›", fg) { clipboard.setText(AnnotatedString(report)) }
            Action("Continue ›", fg, onContinue)
        }
        BasicText(
            report,
            style = TextStyle(color = fg, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun Action(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        style = TextStyle(color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}
