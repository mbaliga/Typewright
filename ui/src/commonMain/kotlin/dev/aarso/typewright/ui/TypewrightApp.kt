package dev.aarso.typewright.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aarso.typewright.ui.sheet.TypewrightSheet

/**
 * The app's entry point on every platform (`app-android`/`app-desktop`/`app-web` each call only
 * this). From P0 through P4a this was a paper-canvas placeholder with the app's name in mono ink;
 * task P4b replaces that placeholder with the real one-sheet UI ([TypewrightSheet]), opened on
 * the Draw room with the paper texture, per UI_SPEC §2 ("Default: paper [CONFIRM]" -- still the
 * default pending that confirmation) and UI_SPEC §9's own screen list (Draw is the sheet's first
 * room). [PaperTokens] (the old placeholder's own two colours) is unused by this function now but
 * left in place: `PlaceholderScreenshotTest`'s pixel-level assertions still reference it directly
 * against `CanvasTextures.PAPER`'s own values, which it also is a subset of.
 */
@Composable
fun TypewrightApp(modifier: Modifier = Modifier) {
    TypewrightSheet(modifier = modifier)
}
