# Open questions

Questions the build cannot settle alone. Each entry is tagged with the prompt that raised it,
says what was done in the meantime, and names who decides. Answered entries move to
`docs/DECISIONS.md`.

## P0: scaffold

1. **LICENSE file versus brief §15.** The repository root has an Apache-2.0 `LICENSE`, added
   when the GitHub repository was created. Brief §15 and CLAUDE.md say there is no LICENSE
   until the licence is decided (the handoff §9 recommends AGPL-3). It is left untouched:
   deleting it is not the build's call, and neither is keeping it. Separately, the generated
   Gradle wrapper scripts (`gradlew`, `gradlew.bat`) carry Gradle's own Apache-2.0 notice
   with an `SPDX-License-Identifier` line. That declares the licence of Gradle's script, not
   Typewright's, and Apache-2.0 requires it to stay, so it is kept despite the "no SPDX
   headers" convention. *Madhav.*

2. **Maven group and package `dev.aarso.typewright` are provisional.** They follow the
   constellation's group. Renaming later touches every package line and the Android
   application id (`dev.aarso.typewright`), so it is cheaper before P1 than after. *Madhav.*

3. **The explorer does not render standalone.** `ui/typewright-explorer.html` relies on its
   host for a `[hidden]{display:none!important}` rule: its `.screen{display:flex}` outranks
   the browser's own `[hidden]` rule, so every screen shows at once in a plain browser.
   `tools/explorer-shots.mjs` injects the rule. Adding it to the explorer itself would be a
   one-line fix, but the explorer is changed in the chat app first (README, step 4). *Madhav.*

4. **minSdk 31 (Android 12).** Chosen because shape-preview's Android stack is
   `TextRunShaper`, which is API 31, and brief §3 plans no fallback shaper. Going lower means
   either a HarfBuzz binding (which the brief takes off the critical path) or no shaping
   preview on older phones. Android 11 and older devices cannot install the app. *Madhav.*

5. **The `Shaper` contract is more than two platform stacks give.** The interface returns
   positioned glyphs and clusters. Android's `TextRunShaper` returns `PositionedGlyphs` (glyph
   ids and positions) with **no cluster map**, so `clusters` is null there. The browser's
   `FontFace` route renders shaped text but exposes **neither glyph ids nor positions nor
   clusters**, so on the web the `Shaper` cannot return glyphs at all. Only desktop (Skiko's
   shaper run handler) gives everything. Options for the web: harfbuzzjs (MIT; the
   handoff's original plan) or a render-only preview. For Android, a HarfBuzz binding only
   where a script's preview needs clusters (brief §3 already allows this). *Decide in P8/P9.*

6. **P3 puts Android capture inside a pure module.** P3 asks for OpenCV capture and cleanup
   "in engine-trace on Android", but CLAUDE.md law 2 makes `engine-trace` pure (no
   `android.*`, and the scaffold gives it no Android target). Proposal: keep the chain in
   `engine-trace` and put camera and OpenCV code in a new Android-only module (for example
   `capture-android`) or in `ui`'s `androidMain`, feeding rasters into the pure chain.
   *Decide at P3.*

7. **The Fontbakery or Fontspector bridge needs a runtime `qa` cannot have.** `qa` is pure
   (law 2), but layer one of the gate (brief §12) runs Fontbakery in Python or the
   Fontspector binary. The bridge belongs behind an interface in `qa` with platform actuals
   elsewhere, the way `compile` does it, and the web has no local runtime for it at all
   (hosted endpoint or no layer one on the web). *Decide at P1.*

8. **Corpus data on Wasm.** `qa:corpus` gets `data/node-economy-*.json` copied into its
   generated resources, which the JVM and Android read from the classpath. Kotlin/Wasm has
   no classpath: the web loader will need to fetch the pack from the app's distribution or
   embed it. Both packs (full 317 KB and compact 87 KB) are copied; P1 decides which one the
   app ships. *Decide at P1.*

9. **Monospace and UI text on the web.** Compose on Wasm cannot reach system fonts. The P0
   web render draws the placeholder's `FontFamily.Monospace` text in Compose's bundled sans,
   while desktop draws it in the system monospace (DejaVu Sans Mono on Linux). Matching the
   explorer's mono labels everywhere needs a bundled monospace face, which is a licence
   choice like Inter (brief §15 item 6). *Madhav, with P4/P9.*

10. **Who runs the hosted build endpoint.** CLAUDE.md law 3 says "no backend of ours", while
    the web's v1 compile path is a hosted endpoint (brief §3, P9) and the handoff §9 treats
    a hosted build service as the paid product. `HostedEndpointBackend` carries a null
    endpoint and says "not chosen yet; nothing is sent" until this is answered. *Madhav.*

11. **Composite builds with Shared-Libraries-asoc.** That repository pins AGP 8.9.1 and
    Kotlin 2.1.0 for composite builds ("AGP lockstep"). Typewright is on AGP 9.4.1 and
    Kotlin 2.4.20 and does not depend on it. An `includeBuild` of it into Typewright (or the
    reverse) would force one side to move: Gradle loads one AGP and one KGP per build, and
    AGP 9 replaced `com.android.library` in KMP modules with
    `com.android.kotlin.multiplatform.library`. Consume it as published artifacts instead,
    or move it to AGP 9 first. *Madhav, if Typewright ever needs it.*

12. **compileSdk 37.** Compose Multiplatform 1.12.1 pulls androidx Compose 1.12.1, whose AAR
    metadata requires compileSdk 37 or later; the build uses 37 and keeps targetSdk at 36
    (Google Play's current requirement) so Android 17 behaviour changes are not opted into
    without a device to test them on. Raise targetSdk when the owner can verify on a phone.
    *Owner, on device.*

13. **The web app goes blank when the browser reports a non-standard locale.** Chromium on
    Linux with no `LANG` set reports `navigator.language` as `en-US@posix`. Compose
    Multiplatform 1.12.1 passes it to `Intl.Locale`, which throws
    `RangeError: Incorrect locale information provided`; the first composition fails and
    only the page background shows. The same build renders correctly with `en-GB`. Options:
    report it upstream and wait, or add a small guard in `index.html` that normalises
    `navigator.languages` before Compose starts. *Decide at P9.*

14. **A licences page.** Skiko's desktop and web binaries contain Skia, FreeType, libpng,
    libjpeg-turbo, libwebp and others (`THIRD_PARTY.md`); their BSD-style licences and
    the FreeType License require notices in binary distributions, and the web bundle carries
    no licence banners. The explorer has no licences or about screen. Per law 6, this needs
    an explorer design before it is built. *Madhav, before the first release.*

15. **Kotlin build statistics.** The Kotlin Gradle plugin writes build statistics (plugin
    versions, task counts, durations, core count) to `~/.gradle/kotlin-profile/` for
    IntelliJ-based IDEs' usage statistics to pick up. The Gradle build itself was not seen
    sending them anywhere. The only switch is the internal property
    `kotlin.internal.collectFUSMetrics`, which the scaffold leaves unset rather than depend
    on an internal flag. *Madhav, if law 3 should extend to build tools.*

16. **Wasm tests for Compose code.** Skiko's Wasm runtime loads only in a browser, so
    `ui` runs its common tests on the desktop JVM and as Android host tests but not on Wasm.
    `app-web` runs its one test in headless Chrome through Karma, which KGP's npm tooling
    fetches from GitHub (`github:Kotlin/karma`). When P4 adds UI logic worth testing on the
    web, `ui` needs browser tests with a Wasm executable (Compose issue CMP-4906). *P4/P9.*

## P0: architecture review

17. **The explorer's node-economy copy is now stale (P0c corpus fix; corresponds to
    docs/ARCHITECTURE_REVIEW.md section 6 decision 4, "Corpus regeneration (item 20)").**
    `data/scripts/build_node_economy_corpus.py` was fixed 2026-09-24 (closing-point double
    count, composites counted as zero, families with no Latin letters silently shrinking a
    box's n — docs/ARCHITECTURE_REVIEW.md section 5 items 13–15) and `data/node-economy-latin.json`
    / `.compact.json` were regenerated from it, pinned to google/fonts commit
    `b5efa9c32e8f9b63005f5cdb1ad5527a77d2cd04`. CLAUDE.md, TYPEWRIGHT_BUILD_BRIEF.md §7/§8.1 and
    docs/DECISIONS.md D15 were updated to match in the same commit. Per law 6,
    `ui/typewright-explorer.html` was **not** touched here — it changes in the chat app first —
    so its worked examples are now wrong in specific places:
    - the geometric o box's worked example number, "10", is a leftover of the old double-count
      bug; the corrected sans-geometric 'o' box is min 20 · Q1 23.25 · med 24 · Q3 24 · max 100
      on-curve (30 families; off-curve is min 0 · Q1 23 · med 24 · Q3 24 · max 100 — the floor of
      0 is a real polygon-outline family, Black Ops One, whose 'o' has 0 off-curve points, not a
      leftover of the composite-as-zero bug: every family's counts in this box are now nonzero
      on-curve);
    - the T range worked as "8-9" is close but not exact; the corrected sans-geometric T box is
      min 8 · Q1 8 · med 8 · Q3 12.5 · max 28 on-curve;
    - any copy giving class sizes as serif-didone 19 or blackletter 18 (the brief's old §8.1
      numbers) should read 15 and 15 — both are genuinely below 30 (their candidate pools run
      out), not a bug to hide;
    - any copy repeating the old CLAUDE.md/D15 fixture totals as single numbers (o 80 → 32,
      n 44 → 22) should read the on-curve/off-curve/total split now in CLAUDE.md and
      TYPEWRIGHT_BUILD_BRIEF.md §7 (o 80·0·80 → 16·16·32, n 44·0·44 → 14·8·22).

    *Madhav, in the chat app, per law 6.*

18. **P1's corpus loader confirms two things item 8 and item 22 left open, and narrows a third
    (P1-corpus-loader).** `qa:corpus`'s data loader, box-statistics API (`NodeEconomyCorpus`,
    `CompactNodeEconomyCorpus`) and fence/verdict logic (`outlierFence`, `verdictFor`) are built
    and tested on both `jvmTest` and `wasmJsNodeTest` (29 and 28 tests respectively, all green).
    - **Item 8, "Corpus data on Wasm": the JVM/Node half is solved, the browser half is not.**
      The `wasmJs { nodejs() }` actual reads the resource with Node's `process.getBuiltinModule`
      (needs Node >= 22.3.0; this container's Kotlin-tooling Node is 26.2.0, comfortably above
      that) resolved relative to the compiled module's own `import.meta.url`, verified empirically
      by probing `process.cwd()` and the module's resource layout under
      `build/wasm/packages/typewright-qa-corpus-test/kotlin/typewright/corpus/` before writing
      the real code. This only works because `qa:corpus` is a pure module whose Wasm target is
      Node-only (`KmpPureConventionPlugin`); it uses the `process` global, which does not exist in
      a browser, so a future `app-web` (a `wasmJs { browser {} }` target) cannot reuse this actual
      as-is and needs its own strategy — most likely `fetch()` against the app's own distribution,
      as this item already said. Which pack the app ships (the product question this item also
      raises) is still undecided; both `loadNodeEconomyPack()` (317 KB) and
      `loadCompactNodeEconomyPack()` (87 KB) are exposed so that decision is not forced here.
    - **Item 22's third bullet, "decide what Task 4 compares Hyle Deco's o against", is still
      undecided** — `verdictFor(count, box)` takes any style class's box as a parameter, so it
      does not itself pick one; that choice belongs to whichever of `campaign` (Task 4's gate) or
      P1b (the style detector) is built next. `docs/ARCHITECTURE_REVIEW.md` §5 item 19's claim
      that Hyle Deco's fitted o is "an outlier against 6 of the 10 boxes" is now **superseded** by
      the P0c-regenerated corpus, which item 17 above already restates: against the real,
      regenerated sans-geometric box (Q1 23.25 · Q3 24 · n 30), Hyle Deco's fitted o (16 on-curve
      + 16 off-curve, CLAUDE.md's fixture) is **in range** on both axes — its on-curve fence is
      24 + max(1.5 × 0.75, 0.25 × 24) = 30 (the 0.25·Q3 minimum decides it: 1.5·IQR alone would
      give 25.125), and 16 is well under Q3 itself, let alone the fence. This is one box of ten,
      chosen because P1-corpus-loader's own prompt named it, not a claim that Hyle Deco is in
      range against every class — that is exactly the decision this bullet still leaves open.

    *Data point for P1b and the `qa` checks task; not a product decision, so no owner tag.*

19. **P1b's style detector is built, and honestly does not classify most sans sub-styles
    correctly yet (P1b).** `dev.aarso.typewright.qa.corpus.style` (in `qa:corpus`) adds a feature
    extractor (contrast ratio, stress angle, serif presence/bracket, a/g storeys, terminal style,
    aperture openness, o roundness as a superellipse exponent, x-height/cap-height ratio, width
    class — brief 8.4's nine) and a hand-written, explainable scorer (`rankStyleClasses`) over the
    same ten corpus taxonomy keys, both pure common Kotlin with real unit tests on synthetic
    glyphs (86 tests total between `qa:corpus` and `learn:scenes`, green on `jvmTest` and
    `wasmJsNodeTest`).
    - **Dependency change:** `qa:corpus`'s `build.gradle.kts` now depends on `:core-font` (not
      only `:core-geometry`), since the extractor's `StyleGlyphSet.fromSfntFont` builds its glyph
      set from `SfntFont`. No third-party dependency was added (`core-font` is an internal module),
      so `THIRD_PARTY.md` is unchanged. `core-font` itself was untouched — no merge conflict with
      whatever the `qa` checks task added there in parallel; its actual current API (`SfntFont`,
      `readSfntFont`, `glyphForCodePoint`, `head.unitsPerEm`, and `core-geometry`'s `Contour`/
      `Glyph`/`CurveSegment`/`extrema`/`signedArea`) is what P1b was built and read against.
    - **Validation sample and method:** the ten corpus-#1-ranked families (`data/node-economy-
      latin.json`'s `families[0]` per style class) plus the ten Lineages exemplars
      (`data/exemplars.json`), fetched from `raw.githubusercontent.com/google/fonts` at the
      corpus's own pinned commit `b5efa9c32e8f9b63005f5cdb1ad5527a77d2cd04`, by the same
      `dir_name`/`regular_filename`/licence-directory method `data/scripts/
      build_node_economy_corpus.py` uses. 20 fonts targeted; 19 classified, 1 failed
      (`Chiron Sung HK`, a ~50 MB CJK variable font — `core-font`'s `readSfntFont` decodes every
      glyph's `glyf` record eagerly regardless of which are ever read, which is fine for a
      Latin-only font but ran the JVM test out of heap on that file; flagged here rather than
      fixed, since changing `core-font`'s eagerness is out of `qa:corpus`'s scope and risks the
      same file the `qa` checks task may be editing). The harness
      (`StyleDetectorRealFontValidationTest`, `qa:corpus`'s `jvmTest`) is opt-in — it needs the
      fonts fetched locally into `qa/corpus/build/validation-fonts/`, which is not committed
      (copyrighted binaries, and not this module's generated data), so it no-ops on a normal
      checkout or in CI rather than depending on network access CLAUDE.md law 4 does not promise.
    - **Result: 8/19 correct top-1 (42%).** Full per-font ranking and confidences are in that
      test's own `validation-report.txt` output (reproduced in the P1b prompt's response). By
      class: `sans-grotesque` 2/2, `serif-didone` 2/2, `slab` 2/2, `serif-transitional` 1/1 all
      correct; `sans-geometric` 1/2; `sans-humanist`, `sans-neogrotesque`, `serif-garalde`,
      `display-artdeco`, `blackletter` 0/2 each. The four closely-related sans classes (geometric,
      grotesque, neo-grotesque, humanist) are frequently confused with each other — genuinely hard
      to separate from only these nine features, and not unlike the ambiguity brief 8.4 itself
      names ("gothic" meaning sans versus blackletter by region). `display-artdeco` and
      `blackletter` are the two classes the scorer's own KDoc already flags as weak (neither
      "inline stripes, extreme widths" nor a genuinely broken/diamond stroke has a dedicated
      feature among the nine); both empirically top-ranked as `serif-didone`/other classes
      instead in this sample. No threshold was re-tuned against this validation sample after
      seeing its results, to avoid overfitting a hand-set scorer to 19 fonts; a larger validation
      pass is future work, not done here.
    - **Item 22's third bullet ("decide what Task 4 compares Hyle Deco's o against") is still not
      decided by this task either.** `rankStyleClasses` infers a class from a font's own outlines;
      it does not assign Hyle Deco (or any font) a fixed class for the node-economy gate to use —
      that remains `campaign`'s call (or the product owner's, per decision 6).
    - The style-identification feature vocabulary (brief 8.4's closing sentence: "the inference
      uses the same feature vocabulary the Lineages lesson teaches") is now in
      `learn:scenes` as data (`StyleFeatureVocabulary.kt`, `STYLE_FEATURE_VOCABULARY`, 9 entries,
      marked SCAFFOLD per docs/LESSONS_SCAFFOLD.md's convention) — plain-language definitions
      only, not the scene-YAML engine itself, which is P6's job.

    *Data point for `campaign` (Task 4) and P6 (Lineages); not a product decision, so no owner tag.*

## P2: corner detection and cubic fitting

20. **`Contour`'s `CUBIC` format has no "line" point kind, so a pure-polygon glyph (`T`, `H`) can
    never reach CLAUDE.md's "0 off-curve" fitted target through this type alone (P2a).**
    `CornerDetection.kt`/`CubicFitting.kt` (`core-geometry`, this task) implement corner detection
    (curvature-based, with non-maximum suppression for a raster trace's own pixel-quantised
    "staircase" corners) and Schneider (1990) cubic fitting, general-purpose and applied uniformly
    to any dense polyline — verified against a dense-polyline square, a synthetic disc/ring built
    from a mathematically exact circle (radius 250, 1-degree sampling), and the real
    `fonts/HyleDeco-Regular.ttf` `T`/`o`/`n`/`H` (read via `core-font`'s `SfntFont`, per the P2a
    prompt's own instructions for these pure-polygon glyphs). `./gradlew :core-geometry:check`
    passes on both `jvm` and `wasmJs`, 80 and 76 tests respectively.
    - **The type gap.** `Contour`'s own invariant (`Contour.kt`) requires every `CUBIC` segment to
      be an (on, off, off) triple — there is no shorter, control-point-free "line" representation.
      A straight side this fitter correctly detects as needing no interior split (`T`'s 8 real
      corners, `H`'s 12) is therefore still emitted as a cubic with two **on-line, degenerate**
      control points, never as a 2-point line: `T` fits to exactly 8 on-curve (matching
      `TYPEWRIGHT_BUILD_BRIEF.md` section 7's target on the nose) but 16 off-curve, not the
      target's 0; `H` similarly reaches 16 on-curve (target 12) with 32 off-curve (target 0).
      Closing this gap needs either a new `Contour`/point-kind capable of a true line segment, or a
      later cleanup pass (plausibly the M1 "Snap" stage, `docs/TYPEWRIGHT_HANDOFF.md` section 4)
      that detects an all-collinear cubic and collapses it to a control-point-free line on write.
      Neither exists yet; P2a's own scope (corner-detect + fit, DATA and LOGIC only in
      `core-geometry`) does not include adding one, and doing so was judged out of scope for this
      task rather than decided against.
    - **Honest fixture results (fitter tuned once, globally, against every fixture together —
      `DEFAULT_FIT_ERROR_TOLERANCE`'s KDoc in `CubicFitting.kt` has the full comparison table).**
      Against `TYPEWRIGHT_BUILD_BRIEF.md` section 7's on-curve targets: `T` 8/8 exact, `o` 28
      against 16, `n` 17 against 14, `H` 16 against 12. Against `docs/ARCHITECTURE_REVIEW.md`
      section 5 item 22's circle targets: the disc and ring hit their named counts exactly (4+8,
      8+16), at a max deviation from the true circle of about 1.3-1.5 units — over the "about 1
      unit" those shared instructions describe, because the fixture's own points are rounded to
      the nearest integer font unit before fitting, which alone puts roughly 0.7-1.0 units of
      quantisation noise into the input; a tolerance tight enough to hold every point within 1 unit
      of the true circle exists (`1.5`) but only by spending far more segments chasing that
      rounding noise (13 for the disc, not 4). No tolerance found hits every fixture's count and
      the circles' own accuracy bound at once — reported honestly per this task's own "Honesty
      rule" rather than forced.

    *Data point for a later cleanup/Snap-stage task and for whoever next tunes
    `DEFAULT_FIT_ERROR_TOLERANCE`; not a product decision, so no owner tag.*

21. **The Snap stage's "insert on-curve points at extrema" makes `H`, `n` and `o` honestly worse,
    not better, against `TYPEWRIGHT_BUILD_BRIEF.md` section 7's targets, and a global tuning knob
    only partly closes that gap (P2b).** `TypeConstraints.kt` (`core-geometry`, this task) adds the
    "Snap" stage brief section 7 stage 6 describes — insert on-curve points at extrema
    (`insertExtremaOnCurvePoints`), snap near-axis tangents (`snapNearAxisTangents`), snap points
    near a metric line except a genuinely curved approach (`snapPointsToMetricLines`, adapted from
    `qa`'s `OvershootFinding.kt` "flat" idea per this task's own pointer), and enforce contour
    direction by even-odd nesting (`enforceContourDirections`, a general point-in-polygon
    containment test, not a "first contour is outer" assumption). `ConstructionClassifier.kt` adds
    the four-way classifier (brief section 8.5), porting `qa/corpus`'s `Roundness.kt` grid-search
    math for the superellipse-exponent fit (reused: the coarse-then-fine grid search and the
    sum-of-squared-error objective and its constants; written fresh: the bounds/sample computation,
    since `Roundness.kt` is `Glyph`-and-`qa/corpus`-shaped and core-geometry cannot depend on it).
    `FittedContourResult.kt`/`NodeEconomyReport.kt` wire the full pipeline (dense polyline ->
    corner-detect + Schneider fit -> Snap -> classify -> report) and add the before/after count
    report. `./gradlew :core-geometry:check` passes on both `jvm` (131 tests) and `wasmJs` (122
    tests; the 9-test gap is `FitPipelineHyleDecoValidationTest` and
    `CubicFittingHyleDecoValidationTest`, both JVM-only for the same file-reading reason P2a's own
    validation test already documents).
    - **The honest finding.** Naively applying `insertExtremaOnCurvePoints` exactly as specified
      (using `core-geometry`'s existing, purely algebraic `CurveSegment.extremaT`, with no
      magnitude floor) against the real, noisy `fonts/HyleDeco-Regular.ttf` fit *increased* every
      curved-or-nearly-straight glyph's on-curve count rather than helping: `H` 16 (P2a's raw fit)
      to 21, `n` 17 to 24, `o` 28 to 39. The cause: the Schneider fitter's own least-squares solve
      leaves plenty of segments that are `isEffectivelyStraight` within their own 1.5-unit
      control-point tolerance yet still carry a tiny, non-zero, purely numerical wobble — enough
      for the algebraically-exact `extremaT` to report a technically-real interior root with a
      bulge nowhere near a font's actual design intent. A global (never per-glyph)
      `minimumExtremumBulgeUnits` floor, tuned honestly against this same real data rather than a
      synthetic fixture alone, helps: at its final default (`0.5`, `DEFAULT_MINIMUM_EXTREMUM_BULGE_UNITS`)
      `o` drops to 37 and `n` to 23, both still over target but closer, and `T` is unaffected (it
      never had any extrema to begin with — 8 on-curve, unchanged, hitting brief section 7's target
      on the nose). `H` barely moves (21, since its own bulge exceeds even `1.0`) — raising the
      floor further (`1.0` gives 18, `1.5` gives 16, matching P2a's raw fit exactly with zero
      insertions) was tried and is reported rather than hidden: at `1.5` it also starts making the
      construction classifier misclassify `H` as ROUNDED_RECTANGLE instead of POLYGONAL, because
      the same real segment's ~1.5-2 unit residual curvature (plausibly an imperfectly-merged
      corner "staircase" — `DEFAULT_CORNER_MERGE_DISTANCE`'s own P2a KDoc already names this exact
      `H` corner near `(105, 193)`) then also clears the classifier's own straightness tolerance.
      `0.5` is kept: a conservative floor that trims sub-visual-significance noise without reaching
      past this stage's own scope to compensate for an upstream (P2a) fitter's residual error —
      full numbers, honestly, in `TypeConstraints.kt`'s `DEFAULT_MINIMUM_EXTREMUM_BULGE_UNITS` KDoc
      and `FitPipelineHyleDecoValidationTest`'s own KDoc and live `println` output.
    - **Final honest numbers** (on-curve/off-curve, this tuning), against brief section 7's fitted
      targets: `T` 8/16 against 8/0 (on-curve exact; off-curve still blocked by the type-gap item
      20 already names); `H` 21/42 against 12/0; `n` 23/46 against 14/8 (the arch top does land
      exactly at x-height 500 after snapping); `o` 37/74 against 16/16 (both contours; the outer
      contour correctly classifies ROUNDED_RECTANGLE, and its flat top/bottom correctly *snap*
      rather than falsely preserving an "overshoot" — both verified mechanically, not assumed).
      `H`'s two stems measure 44.9-45.0 and 42.3-45.0 units across two probes (spread about 2.7
      units — close to, not inside, brief section 7's "monolinear within 2 units", reported as
      measured rather than forced). The off-centre stem survives verified against the real source
      data's own (measured, not brief-quoted) foot positions, not a hard-coded approximation, and
      the foot snaps to exactly y=0.
    - **Genericity check, `L`** (never named in this task's fixtures): 11 on-curve / 22 off-curve,
      correctly classified POLYGONAL, using the identical global parameters as `T`/`H`/`n`/`o`. More
      than the roughly 6 true corners a plain L-shape has, for the same reason as `H`, `n` and `o`
      above — small real quantisation noise along the nominally-straight left stem and base
      producing a few extra, technically-real corners.
    - **`n`'s construction classifies as ROUNDED_RECTANGLE, not a fifth "arch" category** — brief
      section 8.5 names only four classes, and an arch's own shape (two long straight stems meeting
      one curved top) is a genuine, non-gamed instance of "straight sides meeting sharply-curved
      short corners", the same signal the classifier uses for a rounded rectangle. Whether the
      taxonomy should grow a fifth class for this is a product question this task does not decide.

    *Data point for whoever next revisits the Snap stage or the Schneider fitter's own corner
    handling; not a product decision, so no owner tag.*
