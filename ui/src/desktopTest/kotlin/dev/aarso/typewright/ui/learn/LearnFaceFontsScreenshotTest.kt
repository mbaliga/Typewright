package dev.aarso.typewright.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aarso.typewright.ui.ScreenshotHarness
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P6 (Learn UI half), task item 2: real desktop proof that [learnFaceFontFamily] renders real
 * bytes from real `data/learn-faces/` files, not just that the code compiles. Five of the ten
 * on-stage faces (`docs/LESSONS_SCAFFOLD.md` section 2's own era table), set in the explorer's
 * own identify-it test word ("Hamburgefonstiv", `ui/typewright-explorer.html`'s `.qw` element)
 * so a human looking at `ui/build/screenshots/learn-faces-gallery.png` can eyeball the same
 * comparison the app itself will make on stage. [assertRowsAreVisuallyDistinct] is this test's
 * own automated stand-in for that eyeballing: two real, differently-drawn typefaces rendering the
 * same word at the same size must not produce pixel-identical output, which would be true if
 * [learnFaceFontFamily] had silently fallen back to [androidx.compose.ui.text.font.FontFamily.Default]
 * for every face (exactly the failure this test exists to catch).
 */
class LearnFaceFontsScreenshotTest {
    private val faces =
        listOf(
            "garalde" to "EB Garamond",
            "transitional" to "Libre Baskerville",
            "didone" to "Playfair Display",
            "slab" to "Zilla Slab",
            "grotesque" to "Work Sans",
        )
    private val sampleWord = "Hamburgefonstiv"

    @Test
    fun rendersFiveRealLearnFacesAsRealText() {
        val shot =
            ScreenshotHarness.capture(name = "learn-faces-gallery", width = 900, height = 620, density = 2f) {
                LearnFacesGallery(faces = faces, word = sampleWord)
            }
        assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
        assertRowsAreVisuallyDistinct(shot.bitmap)
    }

    @Test
    fun eachIndividualFaceRendersNonEmptyInk() {
        for ((key, family) in faces) {
            val shot =
                ScreenshotHarness.capture(name = "learn-face-$key", width = 640, height = 140, density = 2f) {
                    BasicText(
                        text = sampleWord,
                        style = TextStyle(fontFamily = learnFaceFontFamily(key), fontSize = 40.sp, color = Color.Black),
                    )
                }
            assertTrue(shot.file.length() > 0, "PNG written to ${shot.file}")
            assertTrue(inkFraction(shot.bitmap) > 0.01, "$family ($key): rendered text looks blank")
        }
    }

    @Test
    fun garaldeAndDidoneRenderDifferentGlyphShapesAtTheSameSizeAndPosition() {
        // The strongest single check: same word, same size, same origin, two real,
        // independently-drawn typefaces (Garalde vs Didone -- the era table's own two most
        // visually distant styles: oblique-stress bracketed serifs vs vertical-stress hairlines).
        // If learnFaceFontFamily silently fell back to FontFamily.Default for both, these two
        // captures would be pixel-identical.
        fun render(key: String) =
            ScreenshotHarness
                .capture(name = "learn-face-diff-$key", width = 640, height = 140, density = 2f) {
                    BasicText(
                        text = sampleWord,
                        style = TextStyle(fontFamily = learnFaceFontFamily(key), fontSize = 40.sp, color = Color.Black),
                    )
                }.bitmap
                .toPixelMap()

        val garalde = render("garalde")
        val didone = render("didone")
        var differingPixels = 0
        for (x in 0 until garalde.width) {
            for (y in 0 until garalde.height) {
                if (garalde[x, y] != didone[x, y]) differingPixels++
            }
        }
        assertTrue(
            differingPixels > 500,
            "Garalde and Didone renders differ in only $differingPixels pixels -- looks like a shared fallback, not two real faces",
        )
    }

    private fun assertRowsAreVisuallyDistinct(bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        val pixels = bitmap.toPixelMap()
        val rowHeight = pixels.height / faces.size
        val rowSignatures =
            (faces.indices).map { row ->
                val y = row * rowHeight + rowHeight / 2
                (0 until pixels.width step 4).map { x -> pixels[x, y] }
            }
        for (i in rowSignatures.indices) {
            for (j in i + 1 until rowSignatures.size) {
                assertFalse(
                    rowSignatures[i] == rowSignatures[j],
                    "row $i and row $j (${faces[i].second} vs ${faces[j].second}) rendered identically",
                )
            }
        }
    }

    private fun inkFraction(bitmap: androidx.compose.ui.graphics.ImageBitmap): Double {
        val pixels = bitmap.toPixelMap()
        var ink = 0
        var total = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                total++
                if (pixels[x, y].red < 0.5f) ink++
            }
        }
        return ink.toDouble() / total
    }
}

@Composable
private fun LearnFacesGallery(
    faces: List<Pair<String, String>>,
    word: String,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        for ((key, family) in faces) {
            BasicText(
                text = "$word  —  $family",
                style = TextStyle(fontFamily = learnFaceFontFamily(key), fontSize = 34.sp, color = Color.Black),
            )
        }
    }
}
