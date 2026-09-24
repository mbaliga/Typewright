package dev.aarso.typewright.ui.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Background/sketch layers (P5b task item 4; brief section 10 v1-must-have item 3: "Layers:
 * background (scan or reference), sketch, and the master layer"). The explorer shows no dedicated
 * layer-panel screen for this (task's own note: only a bare "layers: 3" kv row on the Learn
 * room's overlay screen, and a "Sketch · controls" scrapbook pin caption -- neither is a layer-
 * panel UI), so this is the minimal real model and toggle affordance that supports it, not a
 * reproduction of a shown screen -- logged in `docs/OPEN_QUESTIONS.md`. The master layer (the
 * user's own approved drawing, CLAUDE.md law 1's "source of truth") is deliberately not one of
 * [LayerKind]'s entries: it is not a toggleable reference layer, it is the drawing itself.
 *
 * No real background image or sketch content is loaded or drawn by this task (there is no file
 * picker or image-import path wired anywhere in `ui` yet, and CLAUDE.md law 4 "environment
 * honesty" governs claiming device/file capability that has not been built) -- [LayerState] and
 * [LayersPanel] exist so a later task that *does* load an image only needs to plug into
 * [LayerState.visible]/[LayerState.opacity], not invent the model too.
 */
enum class LayerKind(
    val label: String,
) {
    BACKGROUND("Background"),
    SKETCH("Sketch"),
}

/** One layer's own minimal, real controls: shown or hidden, editable or not, and how strongly it shows through (0.0 invisible, 1.0 fully opaque) -- brief section 10's own "background... sketch" list names nothing more granular than this for v1. */
data class LayerState(
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Double = 1.0,
)

/** [LayerState.opacity]'s own step sequence -- a plain, discrete cycle (task's own "opacity or similar minimal control", not a full drag-slider widget) rather than continuous drag, matching this task's own explicit minimalism instruction. */
internal val OPACITY_STEPS: List<Double> = listOf(0.25, 0.5, 0.75, 1.0)

/** The next value after [current] in [OPACITY_STEPS], wrapping -- pure and unit-tested ([LayersTest]) independently of the [LayersState] Compose holder that calls it, the same split [dev.aarso.typewright.ui.puck.PuckGestureMachine]'s own pure-reducer-plus-Compose-holder pattern already establishes in this module. */
internal fun nextOpacityStep(current: Double): Double {
    val index = OPACITY_STEPS.indexOfFirst { it >= current + OPACITY_EPSILON }
    return if (index == -1) OPACITY_STEPS.first() else OPACITY_STEPS[index]
}

private const val OPACITY_EPSILON = 1e-9

/** The two layers' live Compose state -- [rememberLayersState]'s own holder, one per [TypewrightSheet]. Sketch starts at a lower default opacity (`0.6`) than background (`1.0`): a sketch is meant to show *through*, alongside the master layer being drawn over it, where a reference background is usually viewed at full strength until the user dims it. */
class LayersState {
    var background: LayerState by mutableStateOf(LayerState())
        private set
    var sketch: LayerState by mutableStateOf(LayerState(opacity = 0.6))
        private set

    private fun stateFor(kind: LayerKind): LayerState =
        when (kind) {
            LayerKind.BACKGROUND -> background
            LayerKind.SKETCH -> sketch
        }

    private fun setStateFor(
        kind: LayerKind,
        newState: LayerState,
    ) {
        when (kind) {
            LayerKind.BACKGROUND -> background = newState
            LayerKind.SKETCH -> sketch = newState
        }
    }

    fun toggleVisible(kind: LayerKind) {
        val current = stateFor(kind)
        setStateFor(kind, current.copy(visible = !current.visible))
    }

    fun toggleLocked(kind: LayerKind) {
        val current = stateFor(kind)
        setStateFor(kind, current.copy(locked = !current.locked))
    }

    fun cycleOpacity(kind: LayerKind) {
        val current = stateFor(kind)
        setStateFor(kind, current.copy(opacity = nextOpacityStep(current.opacity)))
    }
}

@Composable
fun rememberLayersState(): LayersState = remember { LayersState() }
