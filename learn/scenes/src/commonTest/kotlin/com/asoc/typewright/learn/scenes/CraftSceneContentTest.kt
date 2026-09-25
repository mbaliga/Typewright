// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trips every real Craft YAML file this P6 content-authoring task wrote
 * (`learn/scenes/src/commonMain/resources/scenes/craft/`) through the sibling-built
 * [parseScene], and checks the parsed content against this task's own real, already-measured
 * source numbers — not just "it parses without throwing":
 * - A1 against `qa/corpus`'s real `NodeEconomyCorpus`/`outlierFence`/`verdictFor` (the
 *   sans-geometric T box) and the exact figures `core-geometry`'s
 *   `FitPipelineHyleDecoValidationTest` measured for real (8 on-curve / 16 off-curve, not the
 *   CANON target's 8/0 — the documented "type gap", docs/OPEN_QUESTIONS.md item 20).
 * - B1/B2 against the literal numbers named in `engine-construct`'s own `OffsetTest`/
 *   `ObliqueTest` (both re-read directly, not re-run here — this module does not depend on
 *   `engine-construct`, so the exact figures are checked as text this scene's own caption must
 *   state, the same way [LineagesSceneContentTest] checks era-table prose).
 * - C1 against `core-geometry`'s `Anchor`/`core-font`'s `GlifCodecTest.writesAndParsesAnchorsLosslessly`
 *   fixture points, and that no `stage.pipeline` is invented for a mechanism that does not exist.
 *
 * [CraftResources] is what wires the file paths to these loaders.
 */
class CraftSceneContentTest {
    @Test
    fun allFourCraftScenesParseInTheScaffoldTablesOwnRowOrder() {
        val scenes = CraftResources.loadScenes()
        assertEquals(4, scenes.size)
        assertEquals(
            listOf(
                "craft.a1-tracing-destroys-the-drawing",
                "craft.b1-inflating-a-bold",
                "craft.b2-shearing-an-italic",
                "craft.c1-baked-composites",
            ),
            scenes.map { it.id },
        )
        assertTrue(scenes.all { it.strand == Strand.CRAFT })
        // Every Craft scene this task authored is marked SCAFFOLD (Scene.scaffold's KDoc).
        assertTrue(scenes.all { it.scaffold })
        // Craft is not organised by era (Scene.era's own KDoc names Craft explicitly as an example).
        assertTrue(scenes.all { it.era == null })
        // None of the four carries an inline identify-it exercise (CraftResources' own KDoc).
        assertTrue(scenes.all { it.exercise == null })
    }

    @Test
    fun a1WiresTheRealShippedAndFittedTCountsAndTheSansGeometricNodeEconomyVerdicts() {
        val scene = CraftResources.loadScenes()[0]
        assertEquals("Tracing destroys the drawing", scene.title)

        // The Craft-strand extension in use: both faces are this project's own T, not a second
        // typeface (Scene.kt's KDoc on FaceRef.source/Stage.pipeline).
        assertEquals(2, scene.faces.size)
        val shipped = scene.faces.single { it.key == "shipped" }
        val fitted = scene.faces.single { it.key == "fitted" }
        assertEquals(FaceSource.PROJECT, shipped.source)
        assertEquals("fonts/HyleDeco-Regular.ttf", shipped.path)
        assertEquals(FaceSource.PROJECT, fitted.source)
        assertNull(fitted.path)
        assertEquals("core-geometry.fitPolylineToFinishedContour", scene.stage.pipeline)
        assertEquals("T", scene.stage.sample)
        assertEquals(Align.BASELINE, scene.stage.align)

        // CLAUDE.md's own canon T fixture: shipped 1,763 on-curve / 0 off-curve.
        assertTrue(scene.caption.text.contains("1,763"))
        // The real fit pipeline's own honest output (core-geometry's FitPipelineHyleDecoValidationTest
        // system-out, 2026-09-24: "fitted 8 on-curve / 16 off-curve (target 8/0)") — on-curve hits
        // the CANON target (8) exactly; off-curve does not reach the target's 0.
        assertTrue(scene.caption.text.contains("8 on-curve"))
        assertTrue(scene.caption.text.contains("16 off-curve"))
        // The sans-geometric T on-curve box's own real fence, computed from qa/corpus's data pack:
        // Quartiles(min=8, q1=8, med=8, q3=12.5, max=28, n=30) -> fence = 12.5 + max(6.75, 3.125) = 19.25.
        // verdictFor(1763, box) = OUTLIER; verdictFor(8, box) = IN_RANGE.
        assertTrue(scene.caption.text.contains("outlier"))
        assertTrue(scene.caption.text.contains("in range"))
        assertTrue(scene.callouts.any { it.label.contains("OUTLIER -> IN_RANGE") })
        // The off-curve axis told honestly too (16 is itself an outlier against that box's own
        // fence of 0 -- not glossed over as "both axes clean").
        assertTrue(scene.callouts.any { it.label.contains("still OUTLIER") })
    }

    @Test
    fun a1SansGeometricTBoxArithmeticMatchesTheRealCorpusDataPackDirectly() {
        // Independent of the scene's own prose: recomputes the fence this scene's caption/callouts
        // describe, directly from qa/corpus's real, checked-in data/node-economy-latin.json shape
        // (this module has no dependency on qa/corpus, so the box is restated here as a literal
        // fixture rather than loaded -- see this test class's own KDoc). Values read from
        // data/node-economy-latin.json's styles.sans-geometric.dist.T entry.
        val onCurveBoxQ3 = 12.5
        val onCurveBoxQ1 = 8.0
        val iqr = onCurveBoxQ3 - onCurveBoxQ1
        val fence = onCurveBoxQ3 + maxOf(1.5 * iqr, 0.25 * onCurveBoxQ3)
        assertEquals(19.25, fence)
        assertTrue(1763 > fence, "shipped T (1,763) must be an outlier against the real fence")
        assertTrue(8 <= onCurveBoxQ3, "fitted T (8) must be in range: at or under the box's own Q3")

        val offCurveBoxQ3 = 0.0
        val offCurveFence = offCurveBoxQ3 + maxOf(1.5 * 0.0, 0.25 * offCurveBoxQ3)
        assertEquals(0.0, offCurveFence)
        assertTrue(0 <= offCurveFence, "shipped T's 0 off-curve is in range against the real fence")
        assertTrue(16 > offCurveFence, "the fitted T's real 16 off-curve is honestly an outlier against this same box")
    }

    @Test
    fun b1WiresTheRealOffsetContourFixtureAndFlagsTheDrawnBoldGapAsAspirational() {
        val scene = CraftResources.loadScenes()[1]
        assertEquals("Inflating a bold", scene.title)
        assertEquals(2, scene.faces.size)
        assertTrue(scene.faces.all { it.source == FaceSource.PROJECT })
        assertTrue(scene.faces.all { it.path == null }, "B1's faces are a construction primitive, not a checked-in file")
        assertEquals("engine-construct.offsetContour", scene.stage.pipeline)

        // docs/KNOWLEDGE.md B1's own real account.
        assertTrue(scene.caption.text.contains("46 units"))
        // engine-construct's OffsetTest own literal numbers (100-unit square, +20 outward, -50 inward).
        assertTrue(scene.caption.text.contains("100-unit"))
        assertTrue(scene.caption.text.contains("20"))
        assertTrue(scene.caption.text.contains("50"))
        // The aspirational gap, stated plainly per this task's own instructions.
        assertTrue(scene.caption.text.contains("aspirational"))
        assertTrue(scene.caption.tags.any { it.contains("aspirational") })
    }

    @Test
    fun b2WiresTheRealObliqueTestVariationNumbers() {
        val scene = CraftResources.loadScenes()[2]
        assertEquals("Shearing an italic", scene.title)
        assertEquals(2, scene.faces.size)
        assertTrue(scene.faces.all { it.source == FaceSource.PROJECT })
        assertEquals(
            "engine-construct.strokeThenShearNaively (from) / engine-construct.strokeShearedCenterline (to)",
            scene.stage.pipeline,
        )

        // docs/KNOWLEDGE.md B2's own real account: 44-unit stroke, 40 to 49, 9 degrees.
        assertTrue(scene.caption.text.contains("44-unit"))
        assertTrue(scene.caption.text.contains("40 to 49"))
        assertTrue(scene.caption.text.contains("9 degrees"))
        // engine-construct's ObliqueTest own literal, already-measured figures.
        assertTrue(scene.caption.text.contains("9.7"))
        assertTrue(scene.caption.text.contains("3.7"))
        assertTrue(scene.caption.text.contains("2.6"))
        assertTrue(scene.caption.text.contains("2.4"))
        assertTrue(scene.caption.text.contains("0.2"))
    }

    @Test
    fun c1WiresTheRealAnchorRoundTripAndNamesNoInventedPipeline() {
        val scene = CraftResources.loadScenes()[3]
        assertEquals("Baked composites", scene.title)
        assertEquals(2, scene.faces.size)
        assertTrue(scene.faces.all { it.source == FaceSource.PROJECT })
        assertTrue(scene.faces.all { it.path == null })
        // No live composite-rebuild mechanism exists (this task's own honesty check): the schema's
        // pipeline field must not be filled in with a function that does not exist.
        assertNull(scene.stage.pipeline)

        // core-font's GlifCodecTest.writesAndParsesAnchorsLosslessly own literal fixture points.
        assertTrue(scene.caption.text.contains("(300, 700)"))
        assertTrue(scene.caption.text.contains("(300, 0)"))
        assertTrue(scene.caption.text.contains("52 base Latin letters"))
        // The aspirational gap named explicitly, not glossed over.
        assertTrue(scene.caption.text.contains("does not exist in this codebase yet"))
        assertTrue(scene.caption.tags.any { it.contains("aspirational") })
    }
}
