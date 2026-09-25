// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import kotlin.test.Test

/**
 * The golden path (`docs/V1_SCOPE.md`, `PROMPTS_V1.md` §1), driven headlessly against the real
 * modules on the desktop JVM -- never the UI. One test per golden-path step, sharing its real logic
 * with [GoldenPathSteps]; a step not built yet fails with a named [StepNotBuilt] reason, never
 * `@Ignore` and never an assumption (a skipped test hides a gap this harness exists to surface).
 * [chainOpen]/[chainScan] additionally run several steps back to back with real output carried
 * forward, stopping at the first failure; they don't count toward "N/7 steps pass" (only step ids
 * do -- see `golden-path/README.md`).
 *
 * Every still-unbuilt step's real assertions -- the bar the prompt that builds it must clear -- are
 * sketched as comments on that step's own test method here, `⚑` marking the one(s) that must be
 * anti-fake (real measured output, not a value that could pass while the feature is faked).
 */
class GoldenPathTest {
    // --- Step 1: New project or open an existing font -------------------------------------------
    // Step 1 counts as passing only when BOTH 1a and 1b pass (golden-path/README.md).

    /**
     * Seed: S-ttf (`fonts/HyleDeco-Regular.ttf`) and S-ttf2 (`data/learn-faces/poppins/Poppins-Regular.ttf`).
     *
     * Real assertions this step must clear (P11 + P12):
     * - The UFO read back from disk equals the session project.
     * - Glyph order, advances and contours equal the SfntFont's.
     * - Metrics equal head/hhea/OS2.
     * - Every cmap entry is present as a glyph unicode.
     * - The licence audit names OFL-1.1 for Poppins.
     * - ⚑ The Poppins and Hyle Deco projects differ in glyph count and family.
     */
    @Test
    fun step1aOpenExistingFont() = GoldenPathSteps.step1aOpenExistingFont()

    /**
     * Seed: none yet -- a photo/scan fixture (P16 adds one).
     *
     * Real assertions this step must clear (P16):
     * - Four fiducials are found.
     * - At least 95% of cells map to the manifest glyph name (list every miss).
     * - The new project holds zero accepted glyphs.
     */
    @Test
    fun step1bNewProjectFromScan() = GoldenPathSteps.step1bNewProjectFromScan()

    // --- Step 2: Capture -> Trace -----------------------------------------------------------------

    /**
     * Seed: a captured cell from step 1b's own fixture (P16 adds one).
     *
     * Real assertions this step must clear (P16):
     * - At least 3 stages, and node counts don't increase from Contour to Fit.
     * - Contour counts match S-ttf (o 2; n, H, T 1).
     * - ⚑ Each ink bbox is within 2% of UPM of S-ttf's.
     * - Accepted glyphs are locked and the lock persists.
     * - ⚑ An edit to a locked glyph is refused, and re-tracing doesn't overwrite it.
     */
    @Test
    fun step2CaptureTraceAccept() = GoldenPathSteps.step2CaptureTraceAccept()

    // --- Step 3: Draw / Space ----------------------------------------------------------------------

    /**
     * Seed: S-ufo ([Seeds.newSUfoDirectory]).
     *
     * Real assertions this step must clear (P11 history + P17):
     * - A point move changes only that point.
     * - ⚑ The live economy equals `Glyph.count()` without a manual recompute.
     * - kerning T/o = -40.
     * - Undo-all leaves the project equal and the UFO byte-identical; redo works.
     * - The locked glyph is refused, and unlocking gives a diff.
     * - ⚑ `kerning.plist` on disk holds -40.
     */
    @Test
    fun step3DrawSpaceEdit() = GoldenPathSteps.step3DrawSpaceEdit()

    // --- Step 4: Check -------------------------------------------------------------------------

    /**
     * Seed: S-ufo ([Seeds.newSUfoDirectory]), plus a deliberately failing variant for the anti-fake
     * assertion below.
     *
     * Real assertions this step must clear (P14):
     * - Layer two covers every qa check with a severity.
     * - An alignment miss is found.
     * - ⚑ The failing seed has fails > 0.
     * - On desktop, layer one is Available and not SKIP.
     * - The report's economy equals `checkNodeEconomy`.
     * - The report is deterministic.
     */
    @Test
    fun step4Check() = GoldenPathSteps.step4Check()

    // --- Step 5: Compile -----------------------------------------------------------------------

    /**
     * Seed: S-project ([Seeds.newSProject]), written to a fresh temp project directory. Fully
     * built now, real code all the way to whatever
     * [com.asoc.typewright.compile.platformCompileBackend] actually answers -- see
     * [GoldenPathSteps.step5CompileTtf] for the arrange/act/assert.
     */
    @Test
    fun step5CompileTtf() = GoldenPathSteps.step5CompileTtf()

    // --- Step 6: Ship --------------------------------------------------------------------------

    /**
     * Seed: a compiled S-project TTF (once step 5 is real) plus a deliberately failing Check seed.
     *
     * Real assertions this step must clear (P15):
     * - ⚑ The failing seed is Blocked, naming every fail, and the target directory stays empty.
     * - The clean seed writes OFL.txt, DESCRIPTION/ARTICLE, METADATA.pb, `sources/<F>-Regular.ufo`
     *   (equal to the project) and `fonts/ttf/<F>-Regular.ttf` (parses), plus the issue body.
     */
    @Test
    fun step6ShipFolder() = GoldenPathSteps.step6ShipFolder()

    // --- Step 7: Close and reopen ----------------------------------------------------------------

    /**
     * Seed: S-ufo ([Seeds.newSUfoDirectory]) opened as session A, closed, reopened as session B.
     *
     * Real assertions this step must clear (P11):
     * - B.project equals A.project; locks, diffs, typewright.json, scrapbook and lessons are equal.
     * - ⚑ `readUfoProject` over the files on disk equals A.project.
     * - No `*.tmp` files.
     * - ⚑ Saving with no changes leaves the files byte-identical.
     */
    @Test
    fun step7CloseAndReopen() = GoldenPathSteps.step7CloseAndReopen()

    // --- Chains: real output carried forward; don't count toward N/7 -----------------------------

    /** 1a -> 3 -> 4 -> 5 -> 6 -> 7, from S-ttf. Stops at the first failure (today: step 1a). */
    @Test
    fun chainOpen() =
        runChain(
            "open",
            listOf(
                "1a" to GoldenPathSteps::step1aOpenExistingFont,
                "3" to GoldenPathSteps::step3DrawSpaceEdit,
                "4" to GoldenPathSteps::step4Check,
                "5" to GoldenPathSteps::step5CompileTtf,
                "6" to GoldenPathSteps::step6ShipFolder,
                "7" to GoldenPathSteps::step7CloseAndReopen,
            ),
        )

    /** 1b -> 2 -> 3 -> 4 -> 5 -> 6 -> 7, from a scan. Stops at the first failure (today: step 1b). */
    @Test
    fun chainScan() =
        runChain(
            "scan",
            listOf(
                "1b" to GoldenPathSteps::step1bNewProjectFromScan,
                "2" to GoldenPathSteps::step2CaptureTraceAccept,
                "3" to GoldenPathSteps::step3DrawSpaceEdit,
                "4" to GoldenPathSteps::step4Check,
                "5" to GoldenPathSteps::step5CompileTtf,
                "6" to GoldenPathSteps::step6ShipFolder,
                "7" to GoldenPathSteps::step7CloseAndReopen,
            ),
        )
}
