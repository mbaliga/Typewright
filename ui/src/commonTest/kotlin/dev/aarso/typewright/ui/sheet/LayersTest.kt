package dev.aarso.typewright.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals

/** [nextOpacityStep]'s own pure cycle -- Compose-free, matching [dev.aarso.typewright.ui.puck.PuckGestureMachineTest]'s own pattern of testing a state holder's pure logic apart from its Compose wrapper. */
class LayersTest {
    @Test
    fun cyclesUpThroughEachStepThenWrapsToTheFirst() {
        assertEquals(0.5, nextOpacityStep(0.25))
        assertEquals(0.75, nextOpacityStep(0.5))
        assertEquals(1.0, nextOpacityStep(0.75))
        assertEquals(0.25, nextOpacityStep(1.0))
    }

    @Test
    fun aValueBetweenTwoStepsAdvancesToTheNextStepUp() {
        assertEquals(0.5, nextOpacityStep(0.3))
        assertEquals(1.0, nextOpacityStep(0.9))
    }

    @Test
    fun defaultLayerStatesMatchTheDocumentedStartingValues() {
        val state = LayersState()
        assertEquals(LayerState(visible = true, locked = false, opacity = 1.0), state.background)
        assertEquals(LayerState(visible = true, locked = false, opacity = 0.6), state.sketch)
    }

    @Test
    fun togglesAndCyclesEachLayerIndependently() {
        val state = LayersState()

        state.toggleVisible(LayerKind.SKETCH)
        assertEquals(false, state.sketch.visible)
        assertEquals(true, state.background.visible)

        state.toggleLocked(LayerKind.BACKGROUND)
        assertEquals(true, state.background.locked)
        assertEquals(false, state.sketch.locked)

        state.cycleOpacity(LayerKind.BACKGROUND)
        assertEquals(0.25, state.background.opacity)
        assertEquals(0.6, state.sketch.opacity)
    }
}
