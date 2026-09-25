// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneRendererTest {
    @Test
    fun crossfadeAtZeroIsAllFrom() {
        val fade = SceneRenderer.crossfadeAt(0.0)
        assertEquals(1.0, fade.fromOpacity)
        assertEquals(0.0, fade.toOpacity)
    }

    @Test
    fun crossfadeAtOneIsAllTo() {
        val fade = SceneRenderer.crossfadeAt(1.0)
        assertEquals(0.0, fade.fromOpacity)
        assertEquals(1.0, fade.toOpacity)
    }

    @Test
    fun crossfadeAtTheMidpointIsAnEvenMix() {
        val fade = SceneRenderer.crossfadeAt(0.5)
        assertEquals(0.5, fade.fromOpacity)
        assertEquals(0.5, fade.toOpacity)
    }

    @Test
    fun crossfadeOpacitiesAlwaysSumToOne() {
        for (t in listOf(-1.0, 0.0, 0.1, 0.37, 0.6, 0.9, 1.0, 2.0)) {
            val fade = SceneRenderer.crossfadeAt(t)
            assertEquals(1.0, fade.fromOpacity + fade.toOpacity, absoluteTolerance = 1e-9)
        }
    }

    @Test
    fun crossfadeClampsOutOfRangeScrubPositions() {
        assertEquals(SceneRenderer.crossfadeAt(0.0), SceneRenderer.crossfadeAt(-0.5))
        assertEquals(SceneRenderer.crossfadeAt(1.0), SceneRenderer.crossfadeAt(1.5))
    }

    @Test
    fun stressAngleIsNullWithNoStressAxis() {
        assertNull(SceneRenderer.stressAngleAt(null, 0.5))
    }

    @Test
    fun stressAngleInterpolatesLinearlyFromTheWorkedExample() {
        val stress = 30.0 to 12.0
        assertEquals(30.0, SceneRenderer.stressAngleAt(stress, 0.0))
        assertEquals(12.0, SceneRenderer.stressAngleAt(stress, 1.0))
        assertEquals(21.0, SceneRenderer.stressAngleAt(stress, 0.5))
        assertEquals(25.5, SceneRenderer.stressAngleAt(stress, 0.25))
    }

    @Test
    fun stressAngleInterpolatesWhenToIsLargerThanFrom() {
        val stress = 0.0 to 30.0
        assertEquals(15.0, SceneRenderer.stressAngleAt(stress, 0.5))
    }

    @Test
    fun calloutsAreHiddenBeforeSixTenths() {
        assertFalse(SceneRenderer.calloutsVisibleAt(0.0))
        assertFalse(SceneRenderer.calloutsVisibleAt(0.59))
    }

    @Test
    fun calloutsBecomeVisibleAtAndAfterSixTenths() {
        assertTrue(SceneRenderer.calloutsVisibleAt(0.6))
        assertTrue(SceneRenderer.calloutsVisibleAt(0.61))
        assertTrue(SceneRenderer.calloutsVisibleAt(1.0))
    }

    @Test
    fun normalizedScrubConvertsSecondsAgainstDuration() {
        assertEquals(0.0, SceneRenderer.normalizedScrub(0.0, 40))
        assertEquals(0.5, SceneRenderer.normalizedScrub(20.0, 40))
        assertEquals(1.0, SceneRenderer.normalizedScrub(40.0, 40))
    }

    @Test
    fun normalizedScrubClampsPastTheSceneEnd() {
        assertEquals(1.0, SceneRenderer.normalizedScrub(999.0, 40))
    }

    @Test
    fun frameAtBundlesAllThreeFactsForOneScrubPosition() {
        val scene =
            Scene(
                id = "lineages.transitional",
                strand = Strand.LINEAGES,
                title = "Transitional",
                era = "1757",
                duration = 40,
                faces = listOf(FaceRef("garalde", "EB Garamond"), FaceRef("transitional", "Libre Baskerville")),
                stage = Stage("ago", Align.XHEIGHT, "garalde", "transitional", stress = 30.0 to 12.0),
                caption = Caption("tool", "text"),
            )

        val early = SceneRenderer.frameAt(scene, 0.2)
        assertEquals(0.2, early.t)
        assertFalse(early.calloutsVisible)
        assertEquals(1.0 - 0.2, early.crossfade.fromOpacity, absoluteTolerance = 1e-9)

        val late = SceneRenderer.frameAt(scene, 0.8)
        assertTrue(late.calloutsVisible)
        assertEquals(30.0 + (12.0 - 30.0) * 0.8, late.stressAngle!!, absoluteTolerance = 1e-9)
    }

    @Test
    fun frameAtHasANullStressAngleWhenTheSceneHasNoStressAxis() {
        val scene =
            Scene(
                id = "anatomy.stem",
                strand = Strand.ANATOMY,
                title = "Stem",
                era = null,
                duration = 20,
                faces = listOf(FaceRef("subject", "Inter")),
                stage = Stage("n", Align.BASELINE, "subject", "subject"),
                caption = Caption("a broad nib", "the vertical stroke."),
            )
        assertNull(SceneRenderer.frameAt(scene, 0.5).stressAngle)
    }
}
