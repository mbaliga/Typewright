package dev.aarso.typewright.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Placeholder from P0: the paper canvas with the app's name in monospace ink. */
@Composable
fun TypewrightApp(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(PaperTokens.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Typewright",
            style =
                TextStyle(
                    color = PaperTokens.Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                ),
        )
    }
}
