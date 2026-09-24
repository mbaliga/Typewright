package dev.aarso.typewright.shape.preview

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Desktop and Android are no longer stubs as of P8 (`SkikoShaper`, `AndroidTextRunShaper`), so
 * this file no longer asserts stub behaviour for every target the way it did before them —
 * their real behaviour is covered where it can actually be run for real:
 * `SkikoShaperTest`/`SkikoClustersTest` (`desktopTest`). Android's real shape() output cannot be
 * run here at all (CLAUDE.md law 4; see `AndroidTextRunShaper`'s own KDoc).
 *
 * `ShapingStack.BROWSER_FONT_FACE` (the web actual) is still a stub — building it is P9's own
 * scope (`BrowserFontFaceShaperStub`'s own KDoc) — so this test keeps its original assertion for
 * exactly that stack, and is a real, meaningful check on `wasmJsNodeTest`. On every other
 * target `platformShaper().stack` will never equal `BROWSER_FONT_FACE`, so the body below is a
 * no-op there by construction, not by a target-specific skip.
 */
class ShaperStubTest {
    @Test
    fun stubStacksShapeNothingAndNameThemselves() {
        val shaper = platformShaper()
        if (shaper.stack != ShapingStack.BROWSER_FONT_FACE) return

        val request = ShapeRequest(text = "क्ष", font = ShapeFont(ByteArray(0), unitsPerEm = 1000), script = "Deva")

        var outcome: Result<ShapeResult>? = null
        suspend { shaper.shape(request) }.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        val result = checkNotNull(outcome).getOrThrow()

        assertIs<ShapeResult.NotImplemented>(result)
        assertEquals(shaper.stack, result.stack)
    }
}
