package dev.aarso.typewright.shape.preview

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ShaperStubTest {
    @Test
    fun stubNamesItsStackAndShapesNothing() {
        val shaper = platformShaper()
        val request = ShapeRequest(text = "क्ष", font = ShapeFont(ByteArray(0), unitsPerEm = 1000), script = "Deva")

        var outcome: Result<ShapeResult>? = null
        suspend { shaper.shape(request) }.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        val result = checkNotNull(outcome).getOrThrow()

        assertIs<ShapeResult.NotImplemented>(result)
        assertEquals(shaper.stack, result.stack)
    }
}
