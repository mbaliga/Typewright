// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.core.font.sfnt.readSfntFont
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [buildOverlayRenderPlan] against real layers built from `fonts/HyleDeco-Regular.ttf` (JVM-only,
 * same reason as this package's other Hyle Deco tests). Builds two *independent* [OverlayLayer]s
 * from the exact same font bytes (not one layer reused twice) to prove the redline/scale logic
 * genuinely reads each layer's own real metrics rather than assuming identity.
 */
class OverlayRenderPlanHyleDecoTest {
    private val fontBytes by lazy { File("../fonts/HyleDeco-Regular.ttf").readBytes() }

    private fun layer(
        id: String,
        dash: FloatArray?,
    ) = buildOverlayLayer(
        font = readSfntFont(fontBytes),
        id = id,
        label = "Hyle Deco",
        sourceLabel = if (id == PROJECT_LAYER_ID) "project" else "Google Fonts",
        dashPattern = dash,
        meaningColor = null,
        chars = OVERLAY_DEFAULT_WORD,
    )

    @Test
    fun aLayerComparedAgainstAnIdenticalCopyOfItselfNeverDivergesUnderRedline() {
        val project = layer(PROJECT_LAYER_ID, dash = null)
        val copy = layer("copy", dash = floatArrayOf(9f, 5f))
        val plan =
            buildOverlayRenderPlan(
                layers = listOf(project, copy),
                alignment = OverlayAlignment.CAP_HEIGHT,
                redlineOn = true,
                hiddenLayerIds = emptySet(),
                canvasWidthPx = 960f,
                canvasHeightPx = 300f,
            )
        val divergence = plan.divergence
        assertTrue(divergence != null, "expected a divergence check to run (both layers real and visible)")
        assertEquals(0, divergence!!.tickPointsPx.size, "an identical layer must never diverge from itself")
        assertEquals(0.0, divergence.maxDivergenceFraction)
    }

    @Test
    fun redlineIsNullWhenRedlineModeIsOff() {
        val project = layer(PROJECT_LAYER_ID, dash = null)
        val copy = layer("copy", dash = floatArrayOf(9f, 5f))
        val plan =
            buildOverlayRenderPlan(
                layers = listOf(project, copy),
                alignment = OverlayAlignment.CAP_HEIGHT,
                redlineOn = false,
                hiddenLayerIds = emptySet(),
                canvasWidthPx = 960f,
                canvasHeightPx = 300f,
            )
        assertEquals(null, plan.divergence)
    }

    @Test
    fun redlineIsNullWhenTheOnlyOtherLayerIsHidden() {
        val project = layer(PROJECT_LAYER_ID, dash = null)
        val copy = layer("copy", dash = floatArrayOf(9f, 5f))
        val plan =
            buildOverlayRenderPlan(
                layers = listOf(project, copy),
                alignment = OverlayAlignment.CAP_HEIGHT,
                redlineOn = true,
                hiddenLayerIds = setOf("copy"),
                canvasWidthPx = 960f,
                canvasHeightPx = 300f,
            )
        assertEquals(null, plan.divergence)
    }

    @Test
    fun capHeightAlignmentGivesEveryVisibleLayerTheSameTargetHeight() {
        val project = layer(PROJECT_LAYER_ID, dash = null)
        val copy = layer("copy", dash = floatArrayOf(9f, 5f))
        val plan =
            buildOverlayRenderPlan(
                layers = listOf(project, copy),
                alignment = OverlayAlignment.CAP_HEIGHT,
                redlineOn = false,
                hiddenLayerIds = emptySet(),
                canvasWidthPx = 960f,
                canvasHeightPx = 300f,
            )
        assertEquals(2, plan.visibleLayers.size)
        assertEquals(0, plan.unavailableLayerIds.size)
        // Nominally 300 * 0.62 = 186 (this file's own documented constant), but "Hamburg" set at
        // Hyle Deco's own real advance widths at that height is a little wider than a 960px-wide
        // canvas leaves room for once the two side margins come out, so buildOverlayRenderPlan's
        // own fit-to-width step shrinks it slightly -- real geometry, not a hand-tuned number.
        assertTrue(plan.targetHeightPx in 150f..186f, "expected a real, slightly-fitted height near 186, was ${plan.targetHeightPx}")
    }

    @Test
    fun theProbeReadingMatchesStrokeProbeCalledDirectlyOnTheProjectLayer() {
        val project = layer(PROJECT_LAYER_ID, dash = null)
        val plan =
            buildOverlayRenderPlan(
                layers = listOf(project),
                alignment = OverlayAlignment.CAP_HEIGHT,
                redlineOn = false,
                hiddenLayerIds = emptySet(),
                canvasWidthPx = 960f,
                canvasHeightPx = 300f,
            )
        val direct = strokeProbe(project.glyphs['b'], project.glyphs['H'], project.glyphs['o'])
        assertEquals(direct, plan.probe)
        assertEquals(44.0, plan.probe.stemUnits)
        assertEquals(43.0, plan.probe.barUnits)
    }
}
