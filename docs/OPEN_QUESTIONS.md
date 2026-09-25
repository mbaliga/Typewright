# Open questions

Questions the build cannot settle alone. Each entry is tagged with the prompt that raised it,
says what was done in the meantime, and names who decides. Answered entries move to
`docs/DECISIONS.md`.

## P0: scaffold

1. **RESOLVED (P10, 25 Sep 2026): the licence is decided, see `docs/LICENSING.md`.** The root
   `LICENSE` is now FSL-1.1-ALv2 for the app, the Apache-2.0 text moved to
   `LICENSES/Apache-2.0.txt` for the engine, and every source file carries an SPDX line checked
   by `tools/check_licences.py`. Gradle's wrapper scripts keep their own Apache-2.0 notice.
   Original entry: **LICENSE file versus brief §15.** The repository root has an Apache-2.0 `LICENSE`, added
   when the GitHub repository was created. Brief §15 and CLAUDE.md say there is no LICENSE
   until the licence is decided (the handoff §9 recommends AGPL-3). It is left untouched:
   deleting it is not the build's call, and neither is keeping it. Separately, the generated
   Gradle wrapper scripts (`gradlew`, `gradlew.bat`) carry Gradle's own Apache-2.0 notice
   with an `SPDX-License-Identifier` line. That declares the licence of Gradle's script, not
   Typewright's, and Apache-2.0 requires it to stay, so it is kept despite the "no SPDX
   headers" convention. *Madhav.*

2. **RESOLVED (P10, 25 Sep 2026): the application id is `com.asoc.typewright`, the Kotlin
   packages are `com.asoc.typewright.*` and the Maven group is `com.asoc`** (PROMPTS_V1 D3).
   Original entry: **Maven group and package `dev.aarso.typewright` are provisional.** They
   follow the constellation's group. Renaming later touches every package line and the Android
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
    correctly yet (P1b).** `com.asoc.typewright.qa.corpus.style` (in `qa:corpus`) adds a feature
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

## P3: the pure raster-to-vector trace chain

22. **Two real limitations measured directly while building `engine-trace`'s pure "Clean"/"Contour"
    chain (P3-core), both documented honestly in the code rather than tuned around.**
    `com.asoc.typewright.engine.trace` adds a raster type (`GrayscaleRaster`/`BinaryRaster`, a
    plain `IntArray`/`BooleanArray` wrapper, no platform bitmap type), `adaptiveThreshold`
    (integral-image local-mean threshold), `despeckle`/`fillHoles` (a general connected-component
    labeler, `labelComponents`, written here, 4- and 8-connectivity used per the standard
    complementary-colour pairing), `traceContours` (marching squares, derived from first principles,
    general over any `ScalarField`), `distanceTransform`/`estimateStrokeWidth`, and
    `traceBinaryToContours`/`traceGrayscaleToContours`, which wire the whole chain into
    `core-geometry`'s P2 `fitClosedContourToCubics`. `./gradlew :engine-trace:check` passes on both
    `jvm` and `wasmJs`, 60 tests each.
    - **`adaptiveThreshold`'s local-mean method assumes the ink is thin relative to its window.** A
      filled shape wider than about `2 * windowRadius` (the default is 15) has an interior whose own
      local window is entirely ink, so "darker than the local mean by at least `bias`" can never
      fire there — not a boundary artefact but a real, measured failure (a test disc of radius 25
      against the default window misclassified a large connected interior region as background,
      confirmed by inspecting the resulting background component directly, not guessed at). A real
      hand-drawn letter stroke is thin, so this rarely matters for this module's actual use case;
      `AdaptiveThreshold.kt`'s own KDoc documents the limitation, and every test in this file and
      `TraceChainTest` was sized accordingly (a disc of radius 12, comfortably under the window)
      rather than hidden behind a larger default window that would make the common thin-stroke case
      less sensitive for no benefit.
    - **Marching squares chamfers every genuine 90-degree corner by a small, fixed, honestly-measured
      amount** (a right triangle of legs `0.5`, area `0.125`, per corner, at isovalue `0.5` on a
      binary field) — an inherent property of linear interpolation between four samples, not a
      defect of this implementation; `MarchingSquares.kt`'s KDoc and `MarchingSquaresTest`'s
      rectangle fixtures measure and account for it explicitly (`chamferedRectangleArea`) rather than
      asserting a right angle the method cannot produce. Sub-pixel accuracy against a true circle was
      measured, not merely asserted: a binary mask's extracted contour stays within exactly `0.5`
      pixels of the true circle (the theoretical bound for linear interpolation between two 0/1
      samples, confirmed empirically at radius 15 and radius 40 in `MarchingSquaresSubpixelAccuracyTest`),
      while the same general function handed a genuinely anti-aliased coverage field for the same
      circle gets far closer (about `0.10` pixels max, `0.03` mean, an order of magnitude tighter),
      demonstrating `ScalarField`'s generality rather than only asserting it.
    - **`estimateStrokeWidth` switched from "twice the median" to "twice the maximum" distance-
      transform value, honestly, after measuring the median version's own real bias.** This task's
      own instructions name both as acceptable ("roughly twice the local maximum... along the medial
      axis, or more simply, twice the median/mode... over all foreground pixels"). The median version
      was implemented first and measured directly against a synthetic bar of known width `W`: for a
      straight, hard-edged stroke, the distance-transform profile across its cross-section is a
      triangle from `1` up to `ceil(W/2)` and back down, with *every* level occurring about equally
      often — so the median approximates the triangle's *average* height (about `W/4`), not its peak,
      and "twice the median" converges to roughly `W/2` as `W` grows: half the true width, a real,
      structural bias, not a rare edge case (measured directly on this module's own bar fixtures
      before the switch). "Twice the maximum" is exact for a single, roughly-uniform-width stroke (the
      medial axis is by definition where the distance field peaks) and measured exact or within 1
      pixel on this module's own known-width bar fixtures (`W=12` to exactly `12.0`; `W=7` to `8.0`)
      — at the cost of being less robust than a median to one stray large-interior blob elsewhere in
      the same raster (a filled counter, undespeckled noise); `DistanceTransform.kt`'s KDoc records
      both sides of that trade-off and the measurements behind the switch.

    *Data points for whoever next builds capture/cleanup calibration on top of this chain (a future
    per-glyph-cell or per-stroke capture task) or revisits `estimateStrokeWidth`'s robustness on a
    multi-component raster; not product decisions, so no owner tag.*

## P5a-foundations: offset, stroke-to-outline, transformations and Hobby splines

23. **Hobby splines use a verified, published control-point formula but a documented substitute for
    Hobby's own linearized tangent-angle system, reported honestly rather than claimed as a literal
    reproduction of `mp_make_choices` (P5a-foundations).** `engine-construct` adds `Transform.kt`
    (an affine-transform primitive plus align/distribute, exhaustively tested including composition
    and round-trips), `Offset.kt` (cubic-to-polyline flattening, a documented miter/bevel corner
    rule, and `offsetContour`, refitting through `core-geometry`'s own `fitClosedContourToCubics`
    rather than a second fitter), `Stroke.kt` (`strokeOpenPolyline`/`strokeClosedContour`, all three
    cap styles and all three join styles, built on `Offset.kt`'s own primitives), and
    `HobbySpline.kt` (`hobbySplineOpen`/`hobbySplineClosed`). `./gradlew :engine-construct:check`
    passes on both `jvm` and `wasmJs`, 56 tests each.
    - **The velocity (control-point-distance) formula is Hobby's own published one, independently
      re-derived and verified to machine precision** against the standard cubic-Bezier curvature
      formula (`kappa(0) = (2/3) * cross(P1-P0, P2-P1) / |P1-P0|^3`, confirmed via direct
      differentiation), not transliterated from any single source.
    - **The tangent-angle solve is not Hobby's own linearized tridiagonal system.** Reconstructing
      Hobby's/Knuth's exact `mp_make_choices` mock-curvature-linearization coefficients from a
      general description alone, without the primary source, turned out on direct testing to be
      unreliable to trust blindly: an initial from-scratch reconstruction, using the *true* nonlinear
      Bezier curvature at the placed control points as the matching criterion, was verified to be
      *degenerate* for a symmetric circle (any constant tangent angle trivially satisfies it there,
      so it does not by itself pin down the right answer — measured directly with a Newton solve
      that converged to different, wrong, non-circular roots depending on the initial guess, before
      being abandoned). This module solves the standard chord-length-weighted tangent-vector system
      instead (the classical parametric-cubic-spline "Bessel tangent" equations, cyclic for a closed
      curve, non-cyclic with a PCHIP-style boundary estimate for an open one), combined with the
      verified velocity formula. **Why this is a sound substitute, verified rather than assumed:**
      for a circle (this task's own named test), the result reproduces the textbook
      kappa ~= 0.5522847498307936 constant to full double precision internally
      (`HobbySplineTest.circleKnotsMatchKnownKappaRatioAtExactPrecisionInternally`) and the correct
      general `(4/3) * tan(pi/(2n))` formula for other knot counts
      (`denserCircleKnotsMatchTheGeneralNGonKappaFormula`); through the public, integer-rounding
      `Point`-based API at radius 200 the same ratio reads `0.55` rather than `0.5523` — an honest,
      measured, small (about 0.4%) integer-rounding effect at that one radius, not a formula error
      (see `HobbySplineTest`'s own `println` output, `circleKnotsMatchKnownKappaRatioThroughThePublicApi`).
    - **`curl` is accepted per `HobbyKnotStyle` (task's own "optional per-knot curl/tension" ask) but
      only `tension`'s effect is a literal reproduction of Hobby's own formula** (it divides the
      handle length directly, exactly as METAFONT documents, verified exactly in
      `higherTensionShortensTheHandleLength`). `curl` — meaningful only at an open path's two ends —
      is this module's own **documented, simplified reading** of its published qualitative role
      ("the additional curling at the endpoint relative to a straight continuation"): `curl = 0`
      forces the boundary tangent exactly along the endpoint's own chord; `curl = 1` (the tested
      default) uses the standard one-sided (PCHIP) boundary estimate; other values scale linearly
      between. This is not Knuth's own curl formula (`boundaryTangentDirection`'s KDoc says so).
    - **Spiro is out of this task's scope** (P5a-hard, the next task, per this task's own
      instructions) and is not attempted here.
    - **`offsetContour`'s "sensible" degenerate-offset rule (a square offset inward past half its
      side) uses a per-edge direction-reversal test, not a whole-polygon signed-area sign flip** —
      the latter was tried first and found, by direct construction, to miss exactly the case it
      needed to catch: a fully symmetric shape (a square's four corners in particular) offsets
      *through* its own centre and out the other side as a still-simple, still-same-signed-area
      polygon (verified directly in this task's own development notes; the boundary itself is
      covered by `OffsetTest.squareOffsetInwardByExactlyHalfSideAlsoCollapses` and
      `squareOffsetInwardPastHalfSideCollapsesToAPoint`), so a global area check alone is not
      general-purpose enough; the per-edge test (`offsetContour`'s own KDoc) is.

    *Data points for whoever next builds Spiro (P5a-hard, sharing this module) or revisits Hobby's
    own tangent system against the primary METAFONT source; not product decisions, so no owner tag.*

## P5a-hard: curve-restoring booleans and oblique derivation

24. **Booleans are a general planar-arrangement-and-winding-number algorithm, not a literal port
    of Vatti's own sweep-line bookkeeping — union is solid; subtract/intersect/exclude are
    verified only on this task's own named fixtures — and curve restoration measurably prevents
    the 1,763-point-T problem from coming back (P5a-hard).** `engine-construct` adds `Booleans.kt`
    (`union`/`subtract`/`intersect`/`exclude`/`booleanOp`) and `Oblique.kt`
    (`strokeShearedCenterline`/`strokeThenShearNaively`). `./gradlew :engine-construct:check`
    passes on both `jvm` and `wasmJs`, 79 tests each (up from 56 at P5a-foundations).
    - **Method, stated once here rather than re-derived:** flatten both sides to dense polylines
      with a breadcrumb per vertex (which original cubic segment, and at what parameter `t`);
      find every crossing between a subject edge and a clip edge and split both there; classify
      every resulting edge by sampling a point to each side and asking which side is inside the
      requested op under the standard nonzero-winding rule (an edge whose two sides disagree is
      kept, oriented so the result's interior is on its left); trace the kept edges into closed
      loops by vertex adjacency; then **curve-restore**: a loop's consecutive edges from the same
      original segment are merged back into one run, a run covering a whole original segment is
      emitted **verbatim** (the untouched original control points, not refit), and a run covering
      only part of one is extracted as an **exact** de Casteljau sub-arc (two applications of the
      already-tested `subdivide`) — so away from an actual crossing, the output boundary is
      byte-identical to the input.
    - **This is deliberately not Vatti's own sweep-line active-edge-table algorithm** (Vatti
      1992) — reconstructing that data structure correctly from a description alone, with no
      primary source to check a from-scratch attempt against, is exactly the kind of risk this
      build's own conventions (`HobbySpline.kt`'s `mp_make_choices` precedent) treat as a reason
      to build and document a verified substitute rather than force a literal reproduction. What
      is built instead is the same operation every scan-line boolean computes — the overlay of
      two polygons' edges classified by winding number, the general technique the computational-
      geometry literature describes for Boolean operations via map overlay — verified against
      closed-form area, not merely asserted. Cost, stated plainly: this is `O(n*m)` in the two
      inputs' edge counts (pairwise intersection search), not a sweep line's near-linear cost;
      fine at a flattened letterform contour's realistic size (tens to a few hundred points), not
      benchmarked past it. It is original code against that general structure, not a port of
      Clipper2 or any other library (architecture review item 49's own warning).
    - **Verified, with actual numbers, against the closed-form circle-circle intersection area**
      on two overlapping 200-unit-radius circles 250 units apart (a real, non-trivial crossing —
      two intersection points, not a containment or disjoint degenerate case): union 218,515
      against an expected 218,705 (0.09% off), intersect 32,612 against 32,622 (0.03%), subtract
      92,951 against 93,042 (0.10%), exclude 185,903 against 186,084 (0.10%) — all four ops, not
      union alone, though union is the one this task asked to be made solid first and the one
      exercised by every degenerate case below too. Also verified: a circle unioned with a
      smaller circle fully inside it returns the outer circle **byte-for-byte** (not just
      matching area); two disjoint circles union to **two** separate contours, never one; a
      circle unioned with itself returns **exactly** its own 12 points back (idempotence, exact,
      not approximate); a circle subtracted from a square leaves a two-contour annulus (outer
      square byte-for-byte, inner hole correctly clockwise) at 179,253 against an expected
      179,314 (0.03%).
    - **The curve-restoration point count, the actual number this task asked for:** the union of
      the two overlapping circles above restores to **8 on-curve, 16 off-curve — 24 points
      total** for the whole merged shape, not the hundreds a polygon-only boolean (or a boolean
      with no curve-restoration step at all — architecture review item 49) would leave behind.
    - **Honesty on scope, stated plainly rather than left implicit.** All four ops pass on the
      one fixture above (the task's own suggested cases: overlapping, nested, disjoint, self,
      square-minus-circle) and nowhere else has this task tested them — no glyph-shaped input, no
      near-tangency, no touching-at-a-vertex configuration, no more-than-two-crossing-point case.
      A configuration `traceLoops` cannot close into simple loops throws `IllegalStateException`
      rather than returning wrong geometry (this task's own "honesty rule applied to a failure
      mode"), so a real gap surfaces loudly in a later task rather than silently shipping bad
      outlines; it has not been triggered by anything built here.
    - **Two `Offset.kt` helpers changed from `private` to `internal`** (`MAX_FLATTEN_DEPTH`,
      `isFlatEnough`, `distanceToLine`) so `Booleans.kt`'s own parameter-tracking flattener
      reuses the exact same recursion guard and flatness test rather than a second copy —
      a pure visibility widening, no behaviour change, `OffsetTest.kt` untouched and still green.
    - **Oblique derivation matches `docs/KNOWLEDGE.md` B2 once the measurement method's own noise
      is accounted for, reported honestly rather than only asserted.** A synthetic 44-unit-stroke
      monolinear circle sheared 9 degrees: shearing the centreline then re-stroking measures about
      3.7 units of width variation against the naive shear-the-outline approach's about 9.7 — a
      real, large improvement, but not B2's own ~0.2 on its own. A control (an *unsheared* ring,
      exactly constant-width by construction) measures about 2.4 units of "variation" under this
      same nearest-point-to-polyline measurement method — its own noise floor, not a shear
      artifact. Tightening the fit tolerance (a normal, general `CubicFitParameters` knob, not a
      per-shape hack) to 0.25 brings the sheared measurement to about 2.6, which is close to the
      unsheared floor of 2.4 — so the shear-attributable residual is about `2.6 - 2.4 ≈ 0.2`
      units, matching B2's own figure once the measurement method's own noise is subtracted out.
    - **Spiro was not attempted.** Brief section 10 item 1 asks to "pick one [Spiro or Hobby] and
      document why"; docs/ARCHITECTURE_REVIEW.md section 3 `:engine-construct` already picks
      "Hobby first," with Spiro named as a possible follow-up, not a same-task requirement. Given
      this task's own effort went first to booleans (its own explicit priority: "the hardest
      remaining piece... at least union"), Hobby was already built and verified at
      P5a-foundations, and this task's own instructions say plainly to prefer no Spiro at all
      over a half-working one, the call made here is: Hobby alone for v1, Spiro left for a future
      task if the product owner ever wants it. The raphlinus/spiro licence (Apache-2.0 OR MIT,
      never libspiro's GPL-3.0) was not re-verified here, since nothing from it was used.

    *Data points for whoever next hardens the boolean tracer against a real glyph corpus, revisits
    the `O(n*m)` intersection search's cost at scale, or picks up Spiro; not product decisions
    (the Hobby-for-v1 call is this task's own technical pick, per brief section 10's own "pick one,
    document why," not one CLAUDE.md's "when unsure" rule reserves for the product owner), so no
    owner tag.*

## P4a/P4b: the one-sheet UI's camera, puck gestures and Compose composables

25. **The one-sheet UI is built and its chrome verified against the explorer, but several
    interactions the brief and UI_SPEC name for this surface are not yet wired — disclosed here
    rather than left implicit.** P4a built the pure logic (`SheetCamera`/`SheetDepth`, room
    layout, motion tokens, `PuckGestureEvents`/`PuckGestureConfig`/`reducePuckGesture`) and P4b
    built the Compose composables on top of it (`TypewrightSheet`, `Puck`/`RadialDial`/
    `UnfoldedToolList`, `Bloom`, `GridAndMetrics`, `Header`/`Inspector`/`EdgeMarks`). Screenshot
    comparison against the explorer's own reference screenshots (`ui/src/desktopTest/.../
    SheetScreenshotTest.kt`) covers the chrome only — grid, puck, header, inspector, bloom and
    camera framing — not room *content*, which is a placeholder rectangle and wordmark, not yet a
    reproduction of anything the explorer draws inside a room.
    - **`SheetDepth` (brief §4.2: "zoom is depth... pinch out from a glyph → the word proof → the
      specimen") is computed but never read.** `SheetCamera.depth` derives the right enum value
      from `zoom` and is unit-tested in isolation, but nothing in P4b's composables reads it —
      `TypewrightSheet` renders one room's content the same way at every zoom level; there is no
      map/specimen/proof rendering keyed to depth yet.
    - **`PointerType` (stylus vs. mouse vs. finger) has zero occurrences in `ui`.** Brief §5.2
      ("Three hands over one geometry") asks for different behaviour per input kind (stylus
      hover shows snap candidates before touch commits, barrel button constrains, palm rejection;
      mouse gets modifier-key constrain/snap and 1/10-unit arrow nudge). `PuckGestureEvents`'
      alphabet is already pointer-kind-agnostic by design (its KDoc: "abstract pointer-lifecycle
      events"), and nothing built in P4a/P4b reads `androidx.compose.ui.input.pointer.PointerType`
      anywhere to branch on it — every pointer is currently handled identically.
    - **`=` numeric expression entry (UI_SPEC §5.2: `=xheight`, `=o.rsb+4`) does not exist.** No
      numeric-entry field, expression parser or evaluator was built in P4a/P4b; there is nothing
      yet to type a value into (that belongs to a later inspector/measurement task).
    - **The command palette (UI_SPEC §4, brief §5.2 and §10 item 6: Ctrl/⌘ K, 520 dp wide, palette
      commands like "add extremes", "harmonise curvature", "tidy/simplify with a live count") does
      not exist.** No palette composable, no command registry, no keyboard shortcut wiring for
      `Ctrl/⌘ K` was built.
    - **The contextual radial on holding a node or contour (brief §5.1: "Contextual actions on an
      object... are a hold on the object itself: the same radial, different contents, centred on
      the object") does not exist.** Only the puck's own tool-cycling radial (hold the puck itself)
      is built; `PuckGestureEvents`/`reducePuckGesture` model the puck's hold gesture specifically,
      not a general hold-on-any-object gesture, and there is no node/contour hit-testing yet for a
      hold to target.
    - **Haptics (UI_SPEC §3: "haptic 6 ms" per radial detent) are an unwired hook point, not a
      gap in the gesture machine itself.** `PuckGestureEvents.ToolCycled`'s KDoc previously
      overclaimed this as "P4b's `LocalHapticFeedback` call"; `Puck.kt`'s handler for the
      analogous `RadialDetentTicked` output event is in fact a no-op with a comment marking where
      the call would go (Android is the only P4 target with a vibrator to call it on). Corrected
      in the same change that adds this entry.

    *Data points for P5b (which wires `engine-construct`'s geometry into this sheet: primitives
    tool, Construct inspector, background/sketch layers, guides) or a future task that adds
    numeric/expression entry, the command palette, and the contextual object radial; not product
    decisions, so no owner tag.*

## P5b: Palette commands (core-geometry)

26. **The eight Palette commands (`TYPEWRIGHT_BUILD_BRIEF.md` line 372-373) are built as pure
    functions in `core-geometry`, with two honest scope cuts disclosed here rather than forced into
    a false symmetry or a silent guess.** New: `Curvature.kt` (`curvatureAt`, `harmoniseCurvatureAtJoin`
    — two overloads, segment-pair and contour-level — `HarmoniseFixedSide`), `ContourSimplify.kt`
    (`simplifyContour`, `SimplifyResult`), `ContourKnife.kt` (`ContourLocation`, `knifeContour`),
    `Palette.kt` (`PaletteCommand`/`PaletteTarget` registry, `reverseContour`,
    `roundContourCoordinates`, `closePolylineToContour`, `straightLineCubic`). Widened `internal` to
    `public` in `TypeConstraints.kt`: `insertExtremaOnCurvePoints` (add extremes) and
    `enforceContourDirections` (correct direction) — reused directly, not reimplemented, a pure
    visibility widening with no behaviour change, in the same spirit as `Offset.kt`'s own P4b
    precedent (item in this file's P5a-hard entry). `./gradlew :core-geometry:check` passes on both
    `jvm` (177 tests) and `wasmJs` (168 — the 9-test gap is `CubicFittingHyleDecoValidationTest`/
    `FitPipelineHyleDecoValidationTest`, already `jvmTest`-only before this task, for reading the
    real `fonts/HyleDeco-Regular.ttf` off disk).
    - **"Close/open contour": "open" does not apply to an already-valid `Contour`, and this is not
      a gap silently papered over.** `Contour.kt`'s own KDoc is explicit that this type is *always*
      closed and cyclic — there is no "open `Contour`" representation to toggle at all
      (`HobbySpline.kt`'s `hobbySplineOpen` hit the identical wall: "`core-geometry` has no `open
      Contour` type, so the result is the segment list directly"). An "open path" is a Pen tool's
      own *authoring-time* idea — a drawing in progress that has not yet looped back to its start —
      not a property a finished, already-valid `Contour` can carry or query. Inventing a fake
      `isOpen` flag on `Contour` to make "close" and "open" look like a symmetric pair of commands
      would mean either quietly breaking `Contour`'s own documented invariant or building a second,
      parallel meaning of "open" nothing else in this module recognises. What is built instead is
      the one half that genuinely applies: `closePolylineToContour(points: List<Point>): Contour`
      closes an open polyline — a plain, not-yet-looped-back point list, exactly what a pen tool has
      placed so far — into a valid, closed `CurveFormat.CUBIC` contour by connecting the last point
      to the first with one more straight segment (`straightLineCubic`, the same degenerate-cubic
      convention `CubicFitting.kt`'s own `buildCubicContour` KDoc already documents for a straight
      run). There is no `openContour` command; `PaletteCommand.CLOSE_CONTOUR` is the only entry the
      registry carries for this brief line.
    - **"Cut/knife"'s natural primitive, de Casteljau subdivision, already lives on both sides of a
      dependency edge that only runs one way — resolved by reusing `core-geometry`'s own existing
      copy, not `engine-construct`'s.** `engine-construct/build.gradle.kts` depends on
      `core-geometry` (`api(project(":core-geometry"))`); `core-geometry/build.gradle.kts` has no
      dependency back (confirmed directly, not assumed, before writing `ContourKnife.kt`), so
      `Booleans.kt`'s own `CurveSegment.Cubic.subdivide` is unreachable from here. Rather than add a
      second, near-identical de Casteljau implementation to `core-geometry`, `knifeContour` reuses
      `TypeConstraints.kt`'s own `splitCubicAt` — the identical standard construction, already
      written, tested and in production use one file over (P2b's extrema-insertion stage) — so
      there are exactly two de Casteljau splits in this codebase, one native to each module that
      independently needs one, never a third copy invented for this task.
    - **"Round coordinates" is an honest identity, stated plainly rather than dressed up as doing
      something it cannot.** `Point.x`/`Point.y` are Kotlin `Int`, never a float (`Point.kt`: CLAUDE.md's
      "integers at rest ... floats only inside algorithms") — every `Contour` this app can hold is
      already rounded to the nearest font unit by construction, so there is no sub-integer
      coordinate anywhere in the type for a rounding pass to find. `roundContourCoordinates` still
      exists, and still rebuilds a genuinely new `Contour` from its own point list rather than
      short-circuiting to `return contour`, so `PaletteCommand.ROUND_COORDINATES` is a real,
      testable entry point (`PaletteTest`'s own round-trip test) rather than a command a UI has to
      know is secretly a no-op.
    - **"Harmonise curvature" needs one more convention than the brief states, because matching
      curvature at a join is one equation in two unknowns.** Given a smooth join's two *fixed* far
      control points and its shared tangent direction, each side's own curvature reduces to a clean
      closed form in that side's own near-handle length alone (`Curvature.kt`'s own KDoc has the
      full derivation): `kappa = (2/3) * K / h^2`. Matching the two sides' `kappa` is one equation;
      "the two handle lengths" is two unknowns — solvable only by holding one side fixed and solving
      the other, so `harmoniseCurvatureAtJoin` takes an explicit `HarmoniseFixedSide` (default
      `INCOMING`) rather than guessing which side the brief's one-line "harmonise curvature" meant.
      A later UI task decides how a person expresses this (the side they did *not* just drag,
      most likely) when it wires `PaletteCommand.HARMONISE_CURVATURE` to a gesture.
    - **`simplifyContour`'s greedy, restart-after-each-removal design is `O(n)` removals times an
      `O(n)` rescan each — `O(n^2)` worst case, not the fastest possible single-pass algorithm** —
      a deliberate simplicity choice (this file's own KDoc), fine at a glyph's real point counts
      (tens to low hundreds) and never asked to run at a scale where it would matter.

    *Data points for the later UI task that lists these eight commands generically via
    `PaletteCommand` and wires each to a gesture (P5b's own remaining sheet-wiring half, or a
    follow-up); not product decisions, so no owner tag.*

## P5b-construction-grammar: the construction grammar's primitives

27. **Every primitive and entry method the handoff's M2 list asks for is built except Serif and
    Terminal, deferred because nothing in this codebase defines the "font-wide serif style" they
    would derive from — a real, disclosed scope gap, not a shortcut.** `engine-construct` adds the
    construction grammar itself (brief section 10 M2; `docs/TYPEWRIGHT_HANDOFF.md`'s own M2 list),
    each primitive a data type holding its own entry method's parameters plus a pure `realize()`
    recomputed fresh from them on every call — no caching anywhere, so "stays parametric until
    baked" is exactly "call `realize()` again after changing a field"; "baked" is a caller keeping
    one `realize()` result as plain `Contour` geometry, which needs no separate wrapper type since
    `Contour` already is that. New files: `PrimitiveGeometry.kt` (shared circumcenter/line-
    intersection/fillet/rounding/tangent helpers every primitive below builds on), `Arc.kt`
    (`ArcPrimitive`, `CircularArc`), `LinePrimitive.kt`, `Circle.kt` (`CirclePrimitive`),
    `Ellipse.kt` (`EllipsePrimitive`, `SuperellipsePrimitive`), `Rectangle.kt`
    (`RectanglePrimitive`, `RoundedRectanglePrimitive`), `StemPrimitive.kt`, `BowlPrimitive.kt`.
    `./gradlew :engine-construct:check` passes on both `jvm` and `wasmJs`, 148 tests each (up from
    79 at P5a-hard — 69 new tests, one test class per primitive file plus `PrimitiveGeometryTest`).
    - **Arc — all six entry methods built, none deferred** (three points; start/centre/end;
      start/end/radius; start/end/bulge; tangent-tangent-radius fillet; centre/radius/start/sweep),
      each reduced to one canonical `CircularArc` and checked against a closed-form fact, not
      merely "it compiled": the swept-angle-to-chord-length relationship
      (`chordLength = 2 * radius * sin(|sweep| / 2)`), exact circumcenter equidistance, exact
      fillet tangency (distance from the fillet's own centre to each original line equals the
      radius), and the arc-to-cubic conversion's own deviation from the true circle (`ArcTest`,
      `PrimitiveGeometryTest`). `start/end/radius`'s four largeArc/direction combinations are
      solved by generating all four actual candidate arcs directly (two circle centres times two
      sweep directions) and filtering, deliberately **not** by transcribing the W3C SVG path
      spec's own endpoint-to-centre sign algebra (`F.6.5`) — that derivation's signs are tied to
      SVG's y-down convention, and reproducing it blind in this module's y-up convention from a
      general description risked exactly the silent sign error `HobbySpline.kt`'s own honesty note
      warns about (`Arc.kt`, `arcThroughChordAndRadius`'s own KDoc). The arc-to-cubic conversion
      itself is Riškus's published closed form (A. Riškus, "Approximation of a Cubic Bezier Curve
      by Circular Arcs and Vice Versa", *Information Technology and Control* 35(4), 2006) — the
      same kappa constant `TestShapes.circleContour` already uses independently as a fixture,
      cross-checked against it directly in `ArcTest.fullCircleCubicSegmentsMatchTheIndependentFourArcKappaFixture`.
    - **Circle/Ellipse/Superellipse — centre+radius and three-point built for all three; tangent-
      tangent-radius also built for Circle; fit-to-points also built for Circle.** A circle and an
      ellipse are exact (four/eight cubic arcs via `CircularArc.toCubicSegments`, an ellipse as an
      affine image of the unit circle through the existing `AffineTransform`); a superellipse has
      no closed-form Bezier approximation for a general Lame exponent, so it samples the exact
      parametric equation (360 points) and refits through `core-geometry`'s own
      `fitClosedContourToCubics` — the one general fitter, never a second one written here. Circle's
      fit-to-points is the Kåsa algebraic least-squares fit (I. Kåsa, "A circle fitting procedure
      and its error analysis", *IEEE Transactions on Instrumentation and Measurement*, 1976; exact
      for points that truly lie on a circle, checked directly in `CircleTest`). Ellipse/
      superellipse's own three-point method is AutoCAD's `ELLIPSE` command's three-point axis-
      endpoint construction (two points fix one axis, a third point's perpendicular distance fixes
      the other) — a true three-point fit to a *general* (freely rotated) ellipse is mathematically
      under-determined (an ellipse has 5 degrees of freedom; even an axis-aligned one has 4), so no
      literal three-points-pick-an-ellipse method exists to build; ellipse/superellipse
      fit-to-points (least-squares, not just three points) was left for a future task if the
      product owner wants it — not attempted here.
    - **Rectangle/RoundedRectangle — both of Rectangle's own entry methods (two corners, centre
      plus size) and all three of RoundedRectangle's (two corners, centre plus size, each with a
      uniform radius; per-corner radius) are built.** A corner whose combined radii would overlap
      its neighbour's is resolved by the CSS Backgrounds and Borders Module Level 3 section 5.5
      "Corner Overflow" algorithm (scale every radius down by one shared factor so adjacent radii
      exactly meet), reused here as a general, well-defined answer to an over-constrained input
      rather than an arbitrary per-shape clamp (`RectangleTest.cornerRadiiThatOverflowAreScaledDownProportionally`).
    - **Stem is a parameter, not a font.** `StemPrimitive` accepts `stemValue` as a plain `Double`
      (this task's own scope: wiring it to a real font's UFO `fontinfo` is a later task's job).
    - **Bowl's contrast is a genuinely new technique in this codebase, built and cited rather than
      stubbed, honestly scoped to what it does not attempt.** `strokeClosedContour` (`Stroke.kt`)
      has no notion of a direction-dependent width, so a monolinear bowl (`contrast = 1.0`, the
      default) literally delegates to it, but a contrasted one needs a real extension: this file
      models the pen as an ellipse and offsets the flattened superellipse skeleton, at each vertex,
      by that ellipse's own **support function** evaluated at the vertex's local outward-normal
      angle (`h(phi) = sqrt((rx*cos(phi-alpha))^2 + (ry*sin(phi-alpha))^2)`, the closed-form
      support function of an ellipse of semi-axes `rx`/`ry` rotated by `alpha` — independently
      re-derived from the ellipse's own implicit equation and gradient, checked in `BowlTest`
      directly against a hand-evaluated point and against the constant-circle-pen `rx == ry`
      reduction) — the standard "elliptical pen" idea best known from Knuth's METAFONT. Like
      `Offset.kt`'s own offset primitives, this does not attempt self-intersection cleanup: an
      extreme `contrast`/`strokeWidth` combination on a very small, sharply-curved bowl is this
      primitive's own out-of-scope input, not silently corrected (`BowlPrimitive`'s own KDoc says
      so plainly).
    - **Serif and Terminal primitives are not built, and nothing fakes a "font-wide serif style"
      for them to consume.** The handoff is explicit that these are "derived from font wide
      parameters" (a serif style setting) — the brief's own project-declaration layer (section
      9.2: "serif or sans... serif presence and bracket... terminal style") names this as a
      *user-declared/classifier-detected* property elsewhere in the app, but no type anywhere in
      this codebase (`core-font`'s `fontinfo`, or any other module) currently represents "the
      font's serif style" as a construction parameter (fillet radii, bracket curvature, terminal
      shape) a primitive could read. Inventing one here would be exactly the kind of guess CLAUDE.md's
      "when unsure, stop and report" rule exists to prevent — this is a real, disclosed scope gap,
      not a shortcut.
    - **Nothing here is wired into the sheet yet.** Like P4a/P4b's own primitives-tool/Construct-
      inspector entry above, this task built only the geometry (`engine-construct`, no UI); the
      puck's primitives tool, the "parametric · bake" inspector toggle and per-entry-method input
      fields are P5b's own remaining UI work.

    *Data points for whoever wires this grammar into the sheet's primitives tool and Construct
    inspector, defines a font-wide serif style for a future Serif/Terminal primitive task, or adds
    ellipse/superellipse least-squares fit-to-points; not product decisions, so no owner tag,
    except the serif-style definition itself, which is a design decision. Madhav, when the serif
    style parameter set is designed.*

## P5b-ufo-guides-kerning: guides, kerning groups and the .fea escape hatch (core-font)

28. **UFO 3's four data-model pieces this task asked for — font-wide and per-glyph guidelines at
    any angle, `groups.plist` kerning groups, `kerning.plist` kerning pairs, and `features.fea`'s
    raw FEA text — are all built as a faithful, spec-conformant `read(write(x)) == x` round trip in
    `core-font`/`core-geometry`, with every field name and structure checked against the fetched
    UFO 3 spec (unifiedfontobject.org/versions/ufo3) rather than trusted from memory.** New:
    `core-geometry`'s `Glyph.kt` gains `Guideline` (`x`/`y`/`angle: Double?`,
    `name`/`color`/`identifier: String?`, an `init` block enforcing the spec's
    vertical/horizontal/angled shape rule — at least one of `x`/`y`; `angle` only when both are
    set; `angle` in 0–360) and `Glyph.guidelines: List<Guideline> = emptyList()`, following the
    `Anchor`/`Glyph.anchors` precedent already in this file (a UFO-specific concept `Glyph` needs)
    rather than inventing a separate UFO-only wrapper type. `core-font`'s `UfoFontInfo` gains
    `guidelines: List<Guideline>? = null` (font-wide, `fontinfo.plist`'s own `guidelines` key, `null`
    meaning "no key" versus an explicit empty list meaning "key present, no guidelines" — both round-
    trip distinctly). `UfoProject` gains `kerningInfo: UfoKerning = UfoKerning()`; new file
    `UfoKerning.kt` (`UfoKerning(groups: Map<String, List<String>>, kerning: Map<String, Map<String,
    Double>>, features: String?)`, `writeGroupsPlist`/`readGroupsPlist`, `writeKerningPlist`/
    `readKerningPlist`) — one small model rather than three flat `UfoProject` fields, since the
    spec's own `features.fea` page describes these three files as one related cluster with allowed
    but unsynchronised overlap. `GlifCodec.kt` reads/writes per-glyph `<guideline .../>` elements.
    `PlistValue.kt` gains two small helpers both the kerning and guideline-coordinate paths share:
    `numericPlistValue`/`asDoubleOrNull` (the int-vs-real choice for a field the spec types
    "integer or float") and `elementName()` (replacing `PlistCodec.kt`'s near-duplicate
    `rootElementName` — a drive-by dedup, not new scope). `./gradlew :core-font:check` passes on
    both `jvm` (151 tests, up from 130 — 21 new, across `GlifCodecTest`/`UfoProjectTest`/the new
    `UfoKerningTest`) and `wasmJs` (150, up from 129); `:core-geometry:check` passes on `jvm` (188,
    up from 177 — 11 new, `GuidelineTest` plus two `GlyphTest` additions) and `wasmJs` (179, up from
    168) — the jvm/wasmJs gaps in both modules are pre-existing `jvmTest`-only disk/fixture tests,
    unrelated to this task. `spotlessCheck` passes on both modules; `:qa:check` and
    `:engine-construct:check` (the two other modules that construct `Glyph`/`UfoFontInfo`/
    `UfoProject` directly) still pass unchanged, confirming the new defaulted fields are additive.
    - **Every UFO 3 fact this task depended on was fetched from the live spec, not recalled**: the
      guideline vertical/horizontal/angled rule and its exact "optional if `y` is provided and
      `angle` is not provided" wording (`fontinfo.plist` and `.glif` pages, which state the rule
      identically); `groups.plist`'s `public.kern1.`/`public.kern2.` prefixes and its own
      side-uniqueness rules (Rule 4/5, see the next bullet); `kerning.plist`'s dict-of-dict-of-number
      shape and its "integer or float" value typing; `features.fea`'s "plain text... AFDKO
      syntax... self-contained... `include()` relative to the UFO path... synchronization [with
      kerning.plist/groups.plist/fontinfo.plist] is not a requirement" wording. Three of the fetched
      pages' own example documents (`groups.plist`'s, `kerning.plist`'s, and the `.glif` page's
      `period` guideline example) are used verbatim as test input in `UfoKerningTest`/
      `GlifCodecTest`, not just paraphrased into KDoc — `matchesTheUfo3SpecsOwnGroupsPlistExample`,
      `matchesTheUfo3SpecsOwnKerningPlistExample`, `matchesTheUfo3SpecsOwnGlifGuidelineExample`.
    - **`groups.plist`'s own Rule 4/5 ("glyphs must not appear in more than one kerning group per
      side"; "should not appear more than once in a single group") are deliberately not enforced**
      by `readGroupsPlist`/`writeGroupsPlist` — this codec is a faithful carrier of whatever groups
      map it is given or reads, the same "plain files, faithful round-trip, not interpretation"
      scope `features.fea`'s own spec page states explicitly for the three-file cluster. Enforcing
      cross-group membership uniqueness means checking a *combination* of groups against each
      other, which is a kerning-groups-UI's job (catching it as the person edits), not a
      single-file codec's.
    - **`Guideline.name`'s own spec rule (at least one character, no control characters) and
      `color`'s own comma-separated-component syntax are not validated**; both round-trip as plain
      strings verbatim — the same choice this codebase already made for anchor names (`GlifCodec.kt`
      only requires an anchor's `name` to be present, never validates its content).
    - **Non-default UFO layers (background/sketch — the other half of P5b's "background and sketch
      layers" ask) remain out of `UfoProject`'s scope**, unchanged from before this task;
      `core-font/README.md` still says so plainly rather than implying layers are covered.

    *Data points for whoever builds the kerning-groups UI (side-uniqueness checking belongs there,
    at edit time, not in this codec) or wires guides/kerning/`.fea` into the sheet and Space; not
    product decisions, so no owner tag.*
## P5b: wiring the primitives tool, Construct inspector and layers into the sheet

29. **The real 8-tool puck, the Primitives tool's own two-level unfolded menu, the Construct
    inspector and a minimal background/sketch layer toggle are wired in, each with an honest scope
    cut disclosed here rather than forced or hidden.** New: `ui/puck/Tool.kt` (replaces P4b's own
    `PlaceholderTool`), `ui/puck/ToolIcons.kt` (the eight `ic-*` icon paths, traced from the
    explorer, shared by [Puck]'s own icon, [RadialDial]'s sector glyphs and
    [UnfoldedToolList]'s row icon), `ui/puck/PrimitiveKind.kt`, `ui/puck/PrimitivesConstruction.kt`
    (`ActiveConstruction`, `defaultPrimitiveInstance`, `constructValuesFor`,
    `viewCentreFontUnits`), `ui/puck/PrimitivesMenu.kt` (`PrimitiveKindMenu`/
    `PrimitiveEntryMethodMenu`), `ui/glass/ConstructInspector.kt` (`constructInspectorFields`),
    `ui/sheet/Layers.kt`/`LayersPanel.kt`. `UnfoldedToolList.kt` is generalized into a shared
    `GlassList`/`GlassListRow` (the primitives menu's own two lists reuse it, not a second
    layout). `./gradlew :ui:spotlessCheck :ui:desktopTest` passes (every existing test plus two new
    test files, `PrimitivesConstructionTest` and `LayersTest`); `:ui:compileKotlinWasmJs`,
    `:ui:compileTestKotlinWasmJs`, `:ui:compileAndroidMain` and `:ui:testAndroidHostTest` all pass
    too (all three platforms, not desktop alone). Screenshots under `ui/build/screenshots/` were
    viewed directly (not just "it compiled") and compared against the explorer's own tool-rail and
    icon defs.
    - **Icon paths are traced, not invented, including the three P4b already had.** `ic-select`/
      `ic-pen`/`ic-shape`'s exact path data (grep `id="ic-select"` etc., explorer lines 552-559)
      replaces P4b's own documented "minimal geometric stand-in" for those three, and the same
      path-drawing function adds `ic-bool`/`ic-stroke`/`ic-measure`/`ic-anchor`/`ic-metrics`. One
      icon (`ic-stroke`) keeps the explorer's own `stroke-width="3"` override rather than the
      shared 1.6-unit default -- read directly off the SVG, not guessed.
    - **`GlassList`'s row arrangement changed from `Arrangement.SpaceBetween` across exactly three
      always-present children to `icon, label (weighted), shortcut`, disclosed as a small,
      deliberate layout change, not a silent one.** `SpaceBetween` only reads as "icon and name
      together, shortcut alone on the right" (UI_SPEC's own words) when all three children are
      always present, which stops being true the moment a row has no icon or no shortcut (every
      primitive-kind/entry-method row). The new arrangement matches UI_SPEC's literal wording in
      both cases; tool rows render the same way as before by inspection (screenshots compared).
    - **The primitives menu reuses the puck's existing tap gesture, not a new one, and the
      explorer shows no screen for this specific two-level content to check that choice
      against.** When the current tool is Primitives, the puck's own single tap-to-unfold
      (`PuckOutputEvent.ToolListToggled`) opens the primitive-kind list instead of the plain tool
      list; picking a kind with more than one entry method opens that kind's entry-method list;
      picking an entry method (or a one-method kind, Stem/Bowl, directly) creates a default
      instance and closes the menu. This is this task's own reasonable reading of "the primitives
      tool with entry methods as an unfolded list from the puck", not a reproduction of a shown
      screen -- a different, equally defensible wiring (e.g. a dedicated third puck gesture) was
      not built. *Madhav, if this reading should change before the on-canvas flow is built.*
    - **Deferred, as the task itself allowed: on-canvas point-picking.** Selecting an entry method
      creates a default-parameterized instance at the sheet's current view centre
      (`defaultPrimitiveInstance`, sizes grounded in this codebase's own numbers -- 44-unit stroke
      width matching Hyle Deco's own shipped stem, 2.5 exponent matching `BowlPrimitive`'s own
      cited Piet Hein value) in parametric, not-yet-baked form; it does not place points by
      clicking on the sheet, drag them, or feed a real curve into `LinePrimitive.TangentToCurve`'s
      own "tangent to a curve" entry method (that one tangents to a synthetic default reference
      line through the view centre instead, since nothing on the sheet exposes a real curve to
      snap to yet). The created instance is also not rendered as ink on the canvas -- `RoomInk.kt`'s
      own placeholder pass is untouched, per this task's own scope (wiring the puck/inspector, not
      real glyph rendering). A full multi-step point-picking/dragging gesture flow, and rendering
      the live construction as ink, are both later work.
    - **The Construct inspector shows five fields (stem, contrast, exponent, width, state),
      replacing rather than joining the camera placeholder when Primitives is active and
      something has been created.** `InspectorRow`'s own "never more than six pairs" rule cannot
      fit both sets (5 + 4 = 9), and camera X/Y/zoom reads as far less useful while
      parameterizing a primitive -- a judged call, not a coin flip, stated in
      `TypewrightSheet.kt`'s own `inspectorFields` KDoc. `contrast`/`exponent` render as "--" for a
      kind that genuinely has neither (a plain line, arc, circle, rectangle); `width` is read as
      directly as each kind allows (a stroke/stem width for Bowl/Stem, a line's own length, an
      arc's own diameter, or a shape's own realized bounding-box width for Circle/Ellipse/
      Superellipse/Rectangle/Rounded rectangle -- one general bounding-box reader, not nine
      per-variant field reads). `stem` is always a placeholder font-wide constant (44, Hyle Deco's
      own value) -- there is still no UFO `fontinfo` this app reads a real font-wide stem from
      (the same gap `StemPrimitive`'s own P5b-construction-grammar entry already names).
    - **"Parametric · bake" has no explorer look to reproduce, stated plainly rather than
      invented.** The phrase is `TYPEWRIGHT_BUILD_BRIEF.md`/`docs/TYPEWRIGHT_HANDOFF.md`'s own
      wording; the explorer's own CSS carries a dead, never-instantiated `.bake` rule
      (`ui/typewright-explorer.html` line 224) with no markup anywhere using it. The inspector's
      own STATE field (`constructInspectorFields`) is this task's own reasonable reading of
      `InspectorField`'s existing label/value/emphasize pattern applied to that phrase. Nothing in
      this task's own flow ever sets `ActiveConstruction.baked = true` -- there is no bake action
      wired to a gesture yet (needs the same later on-canvas editing task), so the field always
      reads "PARAMETRIC" today, honestly, not for show.
    - **Layers: a minimal model and toggle only, no real image loading, and a likely scope
      overlap with the sibling agent's own stated scope, flagged for the orchestrator rather than
      silently resolved either way.** `Layers.kt`/`LayersPanel.kt` add a `LayerKind`
      (Background/Sketch)/`LayerState` (visible, locked, opacity) model and a plain toggle panel
      (tap the label to show/hide, tap the percentage to cycle opacity in quarters, tap "L" to
      lock), reusing this module's own established canvas-background/ink-block/mono-uppercase
      vocabulary rather than inventing chrome -- the explorer shows no layer-panel screen anywhere
      to reproduce (only a bare "layers: 3" kv row on Learn's overlay screen and an unrelated
      "Sketch · controls" scrapbook pin caption, neither a real panel). No background image or
      sketch content is actually loaded or drawn -- there is no file-picker/image-import path in
      `ui` yet, and CLAUDE.md law 4 governs claiming that capability before it exists; the model
      exists so a later task that adds real image loading only has to plug into
      `LayerState.visible`/`.opacity`, not invent the model too. **The orchestrator's own task
      text for this agent named "a background/sketch layer toggle" as explicitly the *sibling*
      agent's scope (guide rendering / Space kerning-`.fea` / command palette), while this same
      task's own numbered item 4 explicitly asked *this* agent to build exactly that -- an
      apparent contradiction in the computed task text, not something this agent could resolve by
      re-reading it more carefully.** Built here, in new, small, isolated files
      (`ui/sheet/Layers.kt`/`LayersPanel.kt`, one `rememberLayersState()` call and one
      `LayersPanel(...)` composable added to `TypewrightSheet.kt`) specifically so the orchestrator
      can drop this half cleanly if the sibling's own diff already covers it, rather than a change
      threaded through shared files that would be costly to unpick. *Orchestrator, to deduplicate
      against the sibling agent's own diff before merging.*
    - **`TypewrightSheet.kt`'s own exact diff, for the orchestrator's merge against the sibling
      agent's diff:** 5 import lines added (`padding`, `dp`, `constructInspectorFields`, `Tool`,
      `viewCentreFontUnits`, alphabetised into the existing block); one `val layers =
      rememberLayersState()` line (plus a one-line comment) after `val pin =
      rememberPuckPinState(canvasSizeDp)`; one new `LayersPanel(...)` call (5 lines) inserted
      before the `Puck(...)` call; one new `viewCentreFontUnits = ...` line inside the existing
      `Puck(...)` call; the `InspectorRow(...)` call's `fields = inspectorFields(cameraState)`
      changed to `fields = inspectorFields(cameraState, puckState)`; and the private
      `inspectorFields` function's own signature (`cameraState: SheetCameraState` ->
      `cameraState: SheetCameraState, puckState: PuckUiState`), KDoc and body (one new `if` branch
      at the top) changed. 38 insertions, 7 deletions total (`git diff --stat`). No other line in
      the file was touched.

    *Data points for whoever builds the on-canvas point-picking/dragging flow this task's own
    scope stopped short of, wires a bake gesture, adds real background/sketch image loading, or
    reconciles this entry's own layers-scope-overlap flag against the sibling agent's diff; not
    product decisions except the primitives-menu-gesture reading (Madhav, tagged above) and the
    layers-scope conflict (orchestrator, tagged above).*


## P5b: guides, Space room content and the command palette (ui)

30. **The UI-wiring half of P5b — guides at any angle, Space's kerning demo/groups/`.fea`, and the
    desktop command palette — is built, on top of the sibling core-geometry Palette-commands and
    core-font guides/kerning reports, with several honest scope cuts logged here rather than
    invented.** New: `ui/.../sheet/GuidesLayer.kt` (`WorldGuidesPass`, `sampleGuidelines`),
    `ui/.../sheet/SpaceRoomContent.kt` (`SpaceRoomGlass`, `sampleKerning`), `ui/.../glass/
    CommandPalette.kt` (`CommandPalette`, `commandPaletteShortcut`, `PaletteEntry`). `TypewrightSheet.kt`
    itself changed by 19 lines across 7 small locations (two new imports, a `paletteOpen` state
    var, one modifier chained onto the existing gesture chain, and three new composable calls) —
    merged by hand against the sibling puck/primitives/Construct-inspector piece's own
    TypewrightSheet.kt changes (a separate parallel worktree touching the same file) -- both
    landed cleanly since they touch disjoint lines. `SpaceRoomGlass` itself was moved from this
    task's own `Alignment.Center` to `Alignment.TopCenter` (matching `LayersPanel`'s own "just
    below the header" offset) during that merge: centred, it visibly collided with `RoomInk.kt`'s
    room wordmark, which also sits near screen-centre by default -- caught by viewing the
    screenshot directly, not assumed from the diff.
    - **Guides.** `WorldGuidesPass` reproduces `GridAndMetrics.kt`'s own metric-line approach and
      `CanvasTexture.line` token exactly (UI_SPEC §1 layer 3, "1 dp hairline at ink 20%") rather than
      inventing new styling, extended to an arbitrary `(x, y, angle)` line via `Guideline`'s own
      three shapes. Screenshot-verified (`ui/build/screenshots/draw-paper-rest.png` and
      `draw-blueprint.png`): the sample overshoot/origin/italic guides render correctly on both a
      light and a dark texture, at the right line colour and weight, with no regression to the
      existing puck/radial/grid chrome.
    - **The explorer shows no guide precedent at all, not only for creation.** `grep -ni guide
      ui/typewright-explorer.html` returns zero matches anywhere in the file — no guide markup, no
      guide CSS, no mention of the word (an orchestrator verification pass re-grepped this directly
      and found the same; the original wording here, "no guide-creation interaction anywhere,"
      undersold it — the entire rendered *look* of a guide, not only the affordance to create one,
      is this task's own invention, reusing `GridAndMetrics.kt`'s existing metric-line token as a
      reasonable, disclosed basis for it). `WorldGuidesPass` renders a `List<Guideline>` and nothing
      else; no creation gesture (drag-from-edge, a palette "Add guide" command, or otherwise) was
      built. Guide creation is left for a follow-up task.
    - **No real project/font data flows into `ui` at this layer yet**, confirmed by reading
      `TypewrightSheet.kt`'s own existing scope before writing anything (only a camera and placeholder
      ink exist). `sampleGuidelines()` and `sampleKerning()` are small, honestly-labelled in-memory
      `Guideline`/`UfoKerning` values, not a loaded `.ufo` project — stated plainly in both files' own
      KDoc and in each function's name (`sample*`), not disguised as real data.
    - **Space room: the kerning-pair demo word ("To AV Ty") now shifts by a real kerning value**
      (`kerningOffsetDp`, `(value / unitsPerEm) * fontSizeSp` — the plain definition of "1 em of
      kerning" at that type size) read from `sampleKerning()`'s `UfoKerning.kerning` map, not the
      explorer's own fixed, hand-tuned CSS margins (`.kern.after .k1{margin-left:-.14em}` etc.) — the
      before/after toggle itself reuses the explorer's own selection-language ink-block button style
      (UI_SPEC §5.4). Screenshot-verified (`space-panned.png`): the word, toggle, group list and
      `.fea` text all render and read correctly.
    - **The explorer shows no kerning-groups panel and no `.fea` editor anywhere** — Space's own
      design notes mention only "classes link (=n × 0.65, min) so one value moves a family" as
      intent, never a worked screen (checked directly against `ui/typewright-explorer.html`'s own
      `#s-space` markup). What is built is the plainest possible functional surface per this task's
      own instructions: a list of group-name/members rows in the same mono label-plus-value row
      language `InspectorRow`/`UnfoldedToolList` already use (not a new list-row style), and a
      **read-only** `BasicText` block for `UfoKerning.features` — no tabs, no syntax highlighting, no
      panel chrome, no `BasicTextField` editing (a straightforward future upgrade if the product
      owner wants live editing; not built here to avoid inventing panel chrome around it).
    - **The command palette is real, not a mock** — `Ctrl`/`⌘`+`K` toggles it (`commandPaletteShortcut`,
      a plain `Modifier.onKeyEvent` checking `isCtrlPressed`/`isMetaPressed`, the same "commonMain,
      no desktop-only gate" precedent `SheetGestures.kt`'s own `roomKeyboardNavigation` already set —
      **`ui` has no desktop-only Kotlin source set yet** (only `commonMain`/`commonTest`/
      `desktopTest`, confirmed by listing `ui/src` before writing anything), so this compiles
      everywhere and is only ever meaningfully reachable where a physical keyboard exists, exactly
      like the existing arrow-key room navigation). 520 dp wide, centred 70 dp from the top, square
      corners (the explorer's later "brutalist pass" stylesheet resets `.palette`'s border-radius to
      0 — read past the base `.palette{border-radius:12px}` rule to confirm which one actually
      renders, per this task's own instruction to check for exactly that). Filtering, arrow-key/
      click highlight, Enter-to-run and Escape-to-close are all real (`onPreviewKeyEvent` on the
      palette's own outer `Box`, ahead of its inner `BasicTextField`'s focus).
    - **Palette copy: three rows reuse the explorer's own exact copy and shortcuts** ("Add extremes"
      ⇧E, "Correct path direction" ⇧R, "Round coordinates" — `ui/typewright-explorer.html` lines
      929-931, grepped directly) plus its own two non-Palette rows ("Run the gate on this glyph" ⌘⏎,
      "Compare with…" — lines 932-933), reused verbatim for visual completeness. **The five remaining
      brief commands the explorer's own worked example does not show** — harmonise curvature, tidy/
      simplify, reverse contour, cut/knife, close contour — are new rows in the same label-plus-"·
      description" copy style, with **no shortcut invented for any of them** (this task's own choice,
      not the explorer's, per its own instruction not to invent bindings without good reason).
    - **All eight `PaletteCommand`s are wired to their real `core-geometry` functions, not stubs** —
      `insertExtremaOnCurvePoints`, `enforceContourDirections`, `roundContourCoordinates`,
      `harmoniseCurvatureAtJoin`, `simplifyContour`, `reverseContour`, `knifeContour`,
      `closePolylineToContour` are all called for real when a row runs, each against a small, fixed
      demo shape (`CommandPalette.kt`'s own `demoSquare`/`demoSquareWithRedundantPoint`/`demoCircle`/
      `demoTriangleForClose`) rather than a live selection — **`ui` has no contour-selection model
      yet** (the sibling puck/primitives/Construct-inspector task is a parallel, separate piece of
      work, not this one), so there is nothing live to run a command against. Each row's own result
      (a real, computed point count, direction or "already matched"/"adjusted" outcome) is shown
      under the list after running — genuine output, not a canned string. **"Run the gate on this
      glyph" and "Compare with…" are left unwired** (`run = null`, shown but inert): `qa`'s Ship/gate
      pipeline and the Google Fonts comparison flow are both out of this task's own reach in the
      time available, stated here rather than claimed to work.
    - **No drop shadow on the palette panel** (the explorer's own `.palette` rule:
      `box-shadow:0 30px 60px rgba(0,0,0,.28)`) — this codebase's token system has no shadow token
      anywhere yet (`UI_SPEC §1`'s own default is "nothing on the glass has a... shadow"; the palette
      is one of a small number of documented exceptions for background only), so one was not invented
      ad hoc for this single component. A future shadow token, if the product owner wants one, applies
      here too.
    - **Verified**, per this task's own instructions: `./gradlew :ui:spotlessCheck :ui:desktopTest`
      — clean, 0 failures (`SheetScreenshotTest`'s own 4 cases included). Also compiled
      `:ui:compileKotlinDesktop` and `:ui:compileKotlinWasmJs` directly (both clean) as a stronger
      check that the new `commonMain` code — the keyboard-modifier `isCtrlPressed`/`isMetaPressed`
      calls in particular — is genuinely cross-platform, not desktop-only code that happens to compile
      once. `SheetScreenshotTest`'s own PNGs under `ui/build/screenshots/` were opened and read
      directly (`draw-paper-rest.png`, `draw-blueprint.png`, `draw-radial-open.png`,
      `space-panned.png`) rather than trusted from compilation alone — the guides, the Space kerning
      demo/groups/`.fea` block and the unaffected puck/radial chrome all render as intended.

    *Data points for whoever builds live contour selection (to wire the palette against a real
    glyph instead of a demo shape), a guide-creation gesture, an editable `.fea` field, or `qa`'s
    gate/compare wiring into the palette's two remaining rows; not product decisions, so no owner
    tag.*

## P6: Learn faces fetch (data/logic half)

31. **Roboto Slab is Apache-2.0, not OFL.** Every other family this task fetched has
   `METADATA.pb`'s `license:` field reading `"OFL"`; Roboto Slab's reads `"APACHE2"` (its
   licence text is `apache/robotoslab/LICENSE.txt`, not an `OFL.txt` — there is none at that
   path). Recorded correctly throughout (`data/learn-faces/manifest.json`'s `licence`/
   `licence_field_raw` fields, `data/learn-faces/robotoslab/LICENSE.txt`, `THIRD_PARTY.md`'s
   new table) rather than assumed OFL like its sixteen siblings. Apache-2.0 is not GPL
   (CLAUDE.md law "GPL code ... is not linked" is about code, not font assets, and does not
   block this), so this is not a build blocker — flagged only because a reviewer skimming
   "OFL fonts for Learn" should know one of the seventeen is not. *Madhav, if the Learn UI's
   licence/attribution screen (open question 14, "a licences page") should call this out
   per-face rather than blanket-labelling the set "OFL".*

32. **Real font binaries are committed under `data/learn-faces/`, unlike the node-economy
   corpus.** `data/scripts/build_node_economy_corpus.py` deliberately does **not** commit the
   TTFs it downloads (`qa/corpus`'s own `StyleDetectorRealFontValidationTest` KDoc: "this
   repository does not commit [them] — they are copyrighted, several megabytes each, and not
   'generated data' this module owns") — only the derived per-glyph statistics
   (`data/node-economy-latin.json`) are checked in. This task instead commits the seventeen
   actual font files (5.8 MB total; `data/learn-faces/manifest.json` has each one's SHA-256),
   because docs/LESSONS_SCAFFOLD.md section 1 is explicit that Lineages scenes render "a real
   font loaded at build time" live on the canvas — a statistics pack cannot crossfade a
   glyph. Both are OFL/Apache-2.0 and both are attributed, so this is not a licence problem;
   it is a repository-size and precedent question (every future `learn/scenes` addition that
   wants a new on-stage face grows this directory further, unlike the corpus's build-once,
   commit-nothing pattern). *Madhav: confirm committing lesson font binaries is the intended
   long-term pattern (versus, say, a `.gitattributes` LFS rule, or fetching at app-build time
   into a gitignored directory the way `qa/corpus`'s validation test expects its own fonts).*

33. **wasmJs loading of `data/learn-faces/` is out of this task's scope.** This task is data/
   logic only (no UI); the sibling P6 task that builds the actual Lineages/Overlay/Lens/
   Scrapbook screens (`ui/typewright-explorer.html`'s `#s-learn`) is the one that reads these
   files at runtime. `qa/corpus` already hit the same fork in the road for
   `data/node-economy-*.json` and left it open (open question 8, "Corpus data on Wasm":
   "Kotlin/Wasm has no classpath: the web loader will need to fetch the pack from the app's
   distribution or embed it"). `data/learn-faces/manifest.json`'s `path` field for every face
   is repo-root-relative exactly like `data/exemplars.json`'s existing scheme, so whichever
   approach that task picks (a `Sync`-into-resources Gradle task the way `qa/corpus/
   build.gradle.kts`'s `syncCorpusData` already does it, versus a wasmJs-only fetch of the
   app's own bundled assets — never a *build-time* GitHub fetch repeated at runtime, which
   CLAUDE.md law 3 would not permit) has a manifest to read paths from. *Decide in the P6 UI
   task, per open question 8's own precedent.*

   **Partially resolved by the P6 UI task's Lineages font-rendering half** (`ui/src/commonMain/
   kotlin/com/asoc/typewright/ui/learn/LearnFaceFonts.kt`): the `Sync`-into-resources option was
   taken, in `learn:scenes` (`LearnFaceResources.kt`, following `SceneResources.kt`'s own
   convention) rather than `ui`, with a jvm-classpath actual and a wasmJs actual that reads via
   Node's `fs` — real and tested (`LearnFaceResourcesJvmTest`, `LearnFaceResourcesWasmJsTest`),
   but only for `learn:scenes`'s own `wasmJs { nodejs() }` target, not `ui`'s `wasmJs { browser()
   }` one. See item 47 below for exactly what that leaves open.

34. **A future re-fetch onto a newer `google/fonts` commit must re-check every family's
   `license:` field, not assume today's is permanent.** Google Fonts has migrated some
   families between Apache-2.0 and OFL-1.1 over time (Open Sans, fetched here, reads `"OFL"`
   at this pinned commit; various public trackers put its licence history as Apache-2.0
   originally). `data/scripts/fetch_learn_faces.py` re-derives `licence`/`licence_field_raw`
   from `METADATA.pb` on every run rather than hard-coding a licence per family, so a re-run
   on a later commit will catch a future change automatically — but whoever re-runs it should
   diff `THIRD_PARTY.md`'s table against the new `manifest.json` by hand before committing,
   since that file is prose, not generated. *Not a decision; a standing instruction for
   whoever re-runs this script, recorded here since there is nowhere else for it to live.*


    *Data points for whoever builds the Learn UI (which reads this manifest and decides the
    wasmJs loading path), whoever re-runs the fetch script on a later commit, or the product
    owner deciding the long-term pattern for committing lesson font binaries; not a single
    owner tag, per the individual items above.*

## P6: Learn scene format and renderer logic (data/logic half)

35. **`docs/LESSONS_SCAFFOLD.md` section 2's identify-it bank names a face that is also on
   stage.** The bank's own framing is "faces not on stage", and its first entry is "Libre
   Baskerville → Transitional". But section 2's own era table (row 3) has Libre Baskerville as
   the on-stage face for the Transitional era, and section 1's own worked example (the
   `lineages.transitional` scene) shows this concretely: that scene's `stage.to` is the
   `transitional` key, which resolves to family "Libre Baskerville", and that same scene's own
   inline `exercise.face` is *also* "Libre Baskerville" — the exact face the learner just
   watched crossfade onto stage. `StrandSequencer.plan` (`learn/scenes`) enforces the CANON
   rule ("never shows a face used on stage in that block") structurally, so this collision is
   filtered out automatically wherever real content is loaded — it will not surface as a bug
   in the app, only as a missing exercise question. But whoever authors the real Lineages YAML
   (the content-authoring stage of this same P6 task) should swap that bank entry for a face
   genuinely absent from all ten on-stage faces, since as written it can never actually be
   shown. *Whoever authors the real `scenes/lineages/*.yaml` content.*

36. **"Used on stage in that block" was read as the whole block, not "earlier than this
   scene".** `docs/LESSONS_SCAFFOLD.md` section 1's own CANON text says only "never shows a
   face used on stage in that block" — no "earlier" qualifier. `StrandSequencer` (`learn/
   scenes`) implements exactly that: a candidate exercise face is ineligible if it was used as
   `stage.from`/`stage.to` by *any* scene in the given block, including the exercise's own
   scene, not just scenes before it. This reading is what makes open question 35's collision
   detectable at all (a strictly-"earlier-than-this-scene" reading would let era 3's own
   self-referential exercise through, since nothing *before* era 3 puts Libre Baskerville on
   stage) and matches section 2's "faces not on stage" framing for the bank as a whole. If the
   Learn UI task (the second half of this P6 prompt) needs a different reading — for example,
   surfacing a scene's own exercise immediately after that scene rather than pooling every
   block's exercises after its last scene — `StrandSequencer.plan` takes the scene list as an
   ordinary ordered `List<Scene>`, so a caller wanting a narrower "strictly earlier" collision
   check can already get it by calling `plan` once per prefix of the block; nothing needs to
   change in this file for that, but it is worth confirming the whole-block reading is what the
   UI actually wants before scene content is authored against it. *Madhav, or whoever builds
   the Learn UI half of P6.*

## P6: Lineages/vocabulary/identify-it content (data/logic half)

Real YAML content for the ten Lineages era scenes, the vocabulary scene, and the identify-it
bank, all under `learn/scenes/src/commonMain/resources/scenes/lineages/`, parsed through the
sibling-built `parseScene`/`StrandSequencer` with a real round-trip test per file
(`LineagesSceneContentTest`, `ScaffoldFieldTest`) — `./gradlew :learn:scenes:check`: jvm 79
tests / 0 failures, wasmJs 77 tests / 0 failures (both counts include a concurrently-landed
sibling addition, item 42 below, not this task's own tests).

37. **The bank's own item 1 (Libre Baskerville → Transitional) can never actually surface,
   confirmed against the real content, not just the synthetic fixtures open questions 35-36
   already flagged this against.** `LineagesSceneContentTest.realContentReproducesThe
   DocumentedCollisions` proves it directly: `StrandSequencer.plan` run on the real eleven-scene
   block and the real eight-entry bank excludes exactly this one entry, leaving 7 of 8 bank
   entries eligible. Kept verbatim in `identify-it-bank.yaml` rather than silently swapped for
   a different face, per this task's own brief ("already-written content, not something you
   invent; your job is encoding it faithfully") — and because there is no real substitute to
   swap it for: `data/learn-faces/manifest.json` fetched exactly one exercise-bank face per
   answer class *other than* Transitional (Transitional's only fetched face is Libre
   Baskerville itself, already on stage as era 3). *Madhav or the product owner: either pick a
   real OFL Transitional-class face for `fetch_learn_faces.py` to fetch and swap the bank entry
   to it, or accept that this strand's identify-it bank effectively has 7 live questions, not 8.*

38. **The Roboto Slab → Slab bank entry has no give-away phrase in `docs/LESSONS_SCAFFOLD.md`
   section 2.** Every other of the eight bank entries has a parenthetical give-away
   (`"(bracketed serifs, near-vertical stress)"` and so on); this one reads only "Roboto Slab →
   Slab" with nothing after it. `identify-it-bank.yaml`'s giveaway for this entry ("square
   serifs as thick as the stems, low contrast, even colour — made for the poster wall") is
   drawn from this same strand's own `05-slab.yaml` (section 2's era table row 5's "note" and
   "tags" columns for the Slab era), not invented from nothing, but it is this task's own
   phrasing, not a copy of scaffold text the way the other seven entries are. *Whoever owns
   `docs/LESSONS_SCAFFOLD.md` content: confirm this phrasing or supply the intended one.*

39. **`StrandSequencer.ExerciseBankEntry` has no `word` or `options` field, unlike a scene's own
   inline `exercise:` block.** This task's brief described the bank as needing "a give-away text
   and options list per the scene format's exercise shape," but the real type `StrandSequencer.kt`
   already ships (`ExerciseBankEntry(face, answer, giveaway)`) has neither `word` nor `options` —
   only `Exercise` (a *scene's own* inline candidate) has those. `identify-it-bank.yaml` and
   `ExerciseBankParser.kt` were written to match the real type exactly, per this task's own
   practice (established by the sibling reports this task read) of reading the real built code
   over a paraphrase of it. This leaves a real gap for whoever builds the actual identify-it
   exercise card: it will need a sample word to set each bank face in (this task suggests
   reusing "Hamburgefonstiv", the same word CANON's own worked example and section 6 both use)
   and a multiple-choice options list per entry, neither of which exists on `ExerciseBankEntry`
   today. *Whoever builds the Learn UI half of P6 (or extends `ExerciseBankEntry` first).*

40. **The vocabulary scene's own mechanics (sample, faces, duration) are this task's own
   construction, not scaffold-given.** `docs/LESSONS_SCAFFOLD.md` section 2 gives the vocabulary
   note only as a floating paragraph ("Vocabulary scene (after era 6): ..."), not a described
   scene — CANON's `Scene` schema still requires a `stage` and `faces`, so encoding it as one
   meant choosing what goes on stage. `07-vocabulary.yaml` crossfades the sample word "gothic"
   itself from this strand's own Grotesque face (Work Sans, era 6 — the American "sans" sense)
   to its own Blackletter face (UnifrakturMaguntia, era 1 — the European sense), both already on
   stage elsewhere in the block, over 20 seconds, with no era and no stress axis. `caption.text`
   is section 2's own paragraph verbatim; the rest (the stage design, `caption.tool`,
   `caption.tags`, the duration) is authored, documented inline in the file's own header
   comment. *Madhav or whoever builds the Learn UI: confirm this staging reads the ambiguity
   correctly before it is what the learner actually sees.*

41. **`stage.stress` is `null` for era 1 and era 2, not just era 1.** Section 2's stress column
   reads "none" for Blackletter (era 1) and "30°" for Garalde (era 2). `01-blackletter.yaml`'s
   null is a direct reading of "none". `02-garalde.yaml`'s null is this task's own judgement
   call, not a second literal "none" in the table: `stage.stress` describes the dial's motion
   from the *from* face's own angle to the *to* face's, and era 2's `from` face is Blackletter,
   which has no angle to start the dial from — inventing one (or showing Garalde's real 30°
   twice, as a static value) would either fabricate a number Blackletter doesn't have or claim
   motion that isn't there. The dial's first real motion is era 3 onward (`03-transitional.yaml`,
   CANON's own `[30, 12]` worked example), chained forward with the table's own degrees through
   era 10. *Whoever builds the Learn UI: confirm this reading — versus, say, having era 2
   introduce the dial at a static 30° — before deciding how the dial's on-screen debut looks.*

42. **A concurrent sibling agent extended `Scene.kt`/`SceneParser.kt` in the same module while
   this task ran** (a `FaceSource` enum and `FaceRef.source`/`FaceRef.path`, and `Stage.pipeline`
   — a Craft-strand schema extension, additive and backward-compatible the same way this task's
   own `Scene.scaffold` is). Left entirely untouched; the two extensions compose cleanly
   (`./gradlew :learn:scenes:check` green with both present — see this section's own header for
   the counts). Noted here only as a heads-up for whoever integrates both halves of P6, since
   neither agent coordinated the change directly; not a problem this task needed to resolve.
   *Whoever reviews P6 as a whole.*

    *Data points for whoever builds the Learn UI half of P6 (the actual identify-it exercise
    card, the vocabulary scene's staging, the stress dial's on-screen behaviour), whoever owns
    `docs/LESSONS_SCAFFOLD.md`'s real content (the Slab give-away, the Transitional bank-entry
    swap), and whoever reviews P6 as a whole; not a single owner tag, per the individual items
    above.*

## P6: Craft scenes content (data/logic half)

The four Craft before/after scenes P6's own content-authoring prompt names explicitly
(`docs/LESSONS_SCAFFOLD.md` section 4, rows A1/B1/B2/C1), all under
`learn/scenes/src/commonMain/resources/scenes/craft/`, parsed through the sibling-built
`parseScene` with a real round-trip test per file (`CraftSceneContentTest`) —
`./gradlew :learn:scenes:check`: jvm 85 tests / 0 failures, wasmJs 83 tests / 0 failures (the
2-test gap is the two pre-existing jvm-only tests, `LearnFacesRealFontValidationTest` and
`SceneResourcesJvmTest`; every test this task added runs on both platforms).

43. **`FaceRef.source`/`FaceRef.path` and `Stage.pipeline` are this task's own schema extension,
   additive and backward-compatible, needed because a Craft scene's `from`/`to` are this
   project's own glyph before/after a construction-pipeline stage, not two independently-drawn
   typefaces the way every Lineages scene's `from`/`to` are.** `Scene.kt`'s own KDoc on `Scene`,
   `FaceSource` and `Stage.pipeline` has the full "why"; in short: forcing this shape through
   `FaceRef.family` alone would mean either fetching a corpus family that does not exist, or
   quietly overloading `family` (CANON's "a real, fetchable typeface name") to mean something
   else, both of which CLAUDE.md's "measured, not invented" cuts against. `FaceSource` has two
   values (`CORPUS`, the unchanged default; `PROJECT`, this project's own material, told apart by
   whether `FaceRef.path` is set — a real checked-in file, e.g. `fonts/HyleDeco-Regular.ttf`, or
   nothing, meaning either live-pipeline output named by `Stage.pipeline` or a fixed illustration
   with no live mechanism yet). Every existing two-argument `FaceRef(key, family)` call and every
   existing `Stage(...)` call keeps compiling and keeps its old meaning unchanged
   (`CraftSchemaExtensionTest` checks this directly, including the sibling's own worked-example
   call shape). **This was written independently of, and lands the same session as, a sibling
   agent's own concurrent note about it** (this file's own item 42, filed from the Lineages-
   content task's side after noticing the modification mid-session): both sides confirm the two
   extensions (this one and `Scene.scaffold`, the sibling's own addition, found already landed
   when this task started and reused unchanged rather than re-added) compose cleanly with a green
   `:learn:scenes:check`. *Whoever reviews P6 as a whole; whoever eventually builds the Craft
   half of the Learn UI, since `Stage.pipeline`'s named functions are not called by anything yet
   — this task's own scope is data and parsing, not wiring a live renderer to them.*

44. **A1's honest live-measure numbers are more nuanced than a clean "outlier to in range" story
   on both axes, and the scene says so rather than only reporting the flattering half.** Re-run
   directly for this task (`:core-geometry:jvmTest --tests FitPipelineHyleDecoValidationTest`,
   system-out captured 2026-09-24): the real fit pipeline's own current output for the shipped T
   is **8 on-curve / 16 off-curve**, not CLAUDE.md's fixture-table target of 8/0. On-curve hits
   the target exactly, and against `qa/corpus`'s real `sans-geometric` T box
   (`Quartiles(min=8, q1=8, med=8, q3=12.5, max=28, n=30)`, fence 19.25) the count genuinely goes
   OUTLIER (1,763) → IN_RANGE (8) — the product's own thesis, real. But the off-curve axis, told
   honestly against that same corpus's real off-curve box (`Quartiles(0, 0, 0, 0, 16, n=30)`,
   fence 0): the shipped T's 0 is trivially IN_RANGE, and the *fitted* T's real 16 is itself an
   OUTLIER by this box's own fence — a known representational limit (item 20 above: `Contour`'s
   `CUBIC` format has no line-only point kind, so a straight run is always an (on, off, off)
   triple with two redundant, on-line control points), not a drawing defect, but also not the
   clean story a less careful scene would tell. `a1-tracing-destroys-the-drawing.yaml`'s caption
   and callouts state both axes; `CraftSceneContentTest` checks both numbers and both verdict
   words are present in the real parsed text. *Whoever revisits item 20's own "type gap" (a
   `Contour` line-point kind, or a post-fit collinear-control-point cleanup pass) should know this
   Craft scene is now a second, user-facing place that gap surfaces, beyond the P2 validation
   test's own KDoc.*

45. **B1 and C1 each name a real aspirational gap plainly, per this task's own instructions, rather
   than inventing a fixture or a mechanism to fill it.** Two separate, unrelated gaps:
   - **B1 ("Inflating a bold"): no "properly drawn bold" fixture exists anywhere in this
     codebase.** `fonts/` holds only `HyleDeco-Regular.ttf` and `HyleDeco-Italic.ttf` — no bold.
     The scene is built entirely around what *is* real: `engine-construct`'s `offsetContour` and
     its own `OffsetTest` fixture (a 100-unit square stem/counter pair; +20 outward grows every
     side by exactly 20 with corners preserved; -50 inward — only half that distance — collapses
     the counter completely to a single degenerate point). The caption states the drawn-bold
     comparison is aspirational in so many words and the scene's own `tags` carry an
     `"aspirational: ..."` entry (`CraftSceneContentTest` checks both). *Whoever eventually draws
     or interpolates a real Hyle Deco bold master: that fixture, once it exists, is what would let
     this scene's `to` face become the real drawn bold instead of the naive-offset stand-in.*
   - **C1 ("Baked composites"): no anchor-driven composite-rebuild mechanism, and no bounding-box
     accent-placement function either, exist anywhere in this codebase yet.** Confirmed by grep
     before writing this scene, not assumed: `core-font`'s `GlifCodec` reads/writes outlines,
     anchors (`Anchor`, losslessly — `GlifCodecTest.writesAndParsesAnchorsLosslessly`) and
     guidelines only; there is no UFO `<component>` support at all (brief section 10's own v1.5
     tier, item 8, "anchors-driven composites" — not yet built), and no function anywhere computes
     a mark's position from a base's bounding box either. `Stage.pipeline` is therefore left
     `null` on this scene (`CraftSceneContentTest.c1WiresTheRealAnchorRoundTripAndNamesNoInventedPipeline`
     asserts this directly) and both faces are illustrative (`FaceSource.PROJECT`, no `path`) —
     the scene shows the two real, static states (unattached, anchored) and states the composite-
     rebuild gap in its own caption text rather than staging a live demo that does not exist.
     *Whoever builds anchors-driven composites (brief section 10 v1.5, item 8): this scene's own
     `to` face is exactly where a live rebuild-on-anchor-move demo belongs once that mechanism is
     real.*

    *Data points for whoever builds the Craft half of the Learn UI (which reads
    `Stage.pipeline`/`FaceRef.source` and must decide how to actually render a `PROJECT`/pipeline
    face live), whoever revisits the CUBIC line-point "type gap" (item 20), and whoever draws a
    real Hyle Deco bold or builds anchors-driven composites; not a single owner tag, per the
    individual items above.*

## P6: content-foundations verification pass

46. **B2's stated ~0.2-unit shear-attributable residual is a rounded-input subtraction, not the
    raw figure.** `scenes/craft/b2-shearing-an-italic.yaml`'s caption states "about 0.2 once the
    ~2.4-unit measurement floor is subtracted" from the tight-tolerance figure of "about 2.6".
    Re-running `engine-construct`'s `ObliqueTest` live gives the unrounded values
    `2.5844494163740563` (tight-tolerance correct approach) and `2.4393519052570056` (unsheared
    noise floor) — a raw residual of `0.145`, not `0.2`; the stated `0.2` comes from subtracting
    the already-rounded display figures (`2.6 − 2.4`). This rounding-before-subtracting
    convention is inherited from `ObliqueTest`'s own pre-existing KDoc (P5a-hard, written before
    this scene existed), and both the raw (`0.145`) and rounded (`0.2`) values are consistent with
    `docs/KNOWLEDGE.md` B2's own hedged "~0.2" figure, so this is not a fabrication — just worth a
    note for whoever next revisits B2 that the precise residual reads closer to 0.15 than 0.2.
    *Whoever next revisits B2's own measurement, not a decision.*

    *A data point, not a decision; found by this task's own adversarial verification pass, which
    otherwise confirmed every build/test count, licence, file hash, scene-content number and face
    resolution in items 31–45 independently and found nothing else to fix.*

## P6: Learn UI — Lineages real font rendering (Compose text pipeline)

47. **wasmJs (browser) can genuinely render real font bytes; it just cannot fetch them from
   `data/learn-faces/` yet.** Two separate questions this task told apart by testing each
   directly rather than assuming (item 33's own fork): (a) does Compose Multiplatform's wasmJs
   *text-rendering* target support `androidx.compose.ui.text.platform.Font(identity, data,
   weight, style)` at all, in this pinned `compose-multiplatform 1.12.1` — **yes**, confirmed by
   a real `wasmJsBrowserTest` (`LearnFaceFontsWasmJsBrowserTest`, headless Chrome, real embedded
   font bytes, real `Paragraph` measurement, not just a compile check) that this task's own brief
   left genuinely open; (b) can `ui`'s wasmJs target *get* real bytes from `data/learn-faces/` at
   runtime in a browser — **no**, `learn:scenes`'s own resource reader is Node-`fs`-based
   (`wasmJs { nodejs() }`, `KmpPureConventionPlugin`), which throws `process is not defined` in a
   real browser; `ui`'s wasmJs target is `wasmJs { browser() }` (`KmpPlatformConventionPlugin`).
   `learnFaceFontFamily` catches that failure and falls back to `FontFamily.Default`
   (`learnFaceFontsAreReal()` reports `false` on wasmJs so a caller can show an honest note
   instead of silently substituting a face), rather than crashing, per this task's own brief.
   Building the actual browser fetch (either a plain `fetch()` of a build-time-copied static
   asset, or Compose Multiplatform's own `org.jetbrains.compose.resources` library — not used
   anywhere in this repo yet, and would need its own `composeResources/font/` convention decided
   against this task's `Sync`-task-into-`learn:scenes` one) is real, scoped work this task did not
   do. *Whoever wires the Lineages tab's actual on-stage rendering for the web target; the two
   candidate mechanisms above are both real options, not yet compared.*

48. **Android's real byte-loading path (`LearnFaceFonts.android.kt`) compiles but is not verified
   on a device or emulator.** This container has neither (CLAUDE.md law 4). The implementation is
   real code, not a stub — a custom `AndroidFont` + `AndroidFont.TypefaceLoader` built from
   `android.graphics.fonts.Font.Builder(ByteBuffer)` and `Typeface.CustomFallbackBuilder`, the
   real mechanism Jetpack Compose's own public API exists for (verified by extracting and
   `javap`-ing the real `androidx.compose.ui:ui-text-android:1.12.1` `.aar`: it does **not** carry
   `androidx.compose.ui.text.platform.Font(identity, data, weight, style)` at all, so this task's
   own brief's assumption that Android "follows the same Skia-backed path as desktop" is false)
   — but nothing in this container can confirm it actually renders correctly on a real device.
   `:ui:compileAndroidMain` succeeds offline against `/opt/android-sdk`'s `android-37` platform.
   *Madhav, or whoever next has a device/emulator: run the Learn screen's Lineages tab on Android
   and look at it.*

49. **Requesting a non-default weight from a variable font's `Font(identity, data, weight,
   style)` call is unverified.** Several of the seventeen faces are variable fonts (filenames
   carry axis tags: `EBGaramond[wght].ttf`, `LibreBaskerville[wght].ttf`, `Inter[opsz,wght].ttf`,
   …). `learnFaceFontFamily`'s `weight`/`style` parameters are passed straight through to
   `Font(...)`, and Skia is documented to match a variable font's `wght` axis to a requested
   [FontWeight] automatically — but this task only ever requested [FontWeight.Normal] in its own
   tests and screenshots (`LearnFaceFontsScreenshotTest`, `LearnFaceFontsWasmJsBrowserTest`), so
   that matching is asserted here, not verified by looking at a rendered bold/italic instance.
   *Whoever wires a Lineages/Craft scene that actually requests a non-default weight or style
   from one of these faces — check the rendered result, not just that it compiles.*

## P6: Anatomy Lens data API (`ui/src/commonMain/kotlin/com/asoc/typewright/ui/learn/AnatomyLensData.kt`)

50. **`qa:corpus`'s style package (P1b) had zero consumers anywhere outside `:qa:corpus` and was
    entirely `internal`, which is a Kotlin *module* boundary, not a file one — `:ui` could not
    call any of it regardless of its Gradle dependency graph.** Confirmed by grepping the whole
    repository (`grep -rl "qa.corpus.style"` outside `qa/corpus/` finds only two KDoc/comment
    references, never an import) before writing anything, per this task's own instruction to
    verify rather than guess. Fixed by widening exactly the declarations `AnatomyLensData.kt`
    calls (and the result types their signatures return) from `internal` to public, in
    `:qa:corpus` itself: `contrastRatio`, `stressAngleDegrees`, `serifMetrics`/`SerifMetrics`,
    `storeysFromA`/`storeysFromG`/`Storeys`, `terminalStyle`/`TerminalStyle`, `apertureOpenness`,
    `superellipseExponent`, `xHeightToCapHeightRatio`, plus `Glyph.outerContour` and
    `Glyph.inkBounds`/`Bounds` (two small navigational helpers their signatures need). Every other
    internal declaration (the probe geometry in `Geometry2D.kt`, `StyleGlyphSet`,
    `extractFeatures`, `StyleScorer.kt`, `widthClass`) is untouched — no behaviour changed,
    confirmed by `./gradlew :qa:corpus:check` staying green (jvm + wasmJs/Node tests, spotless)
    before and after. This is a real architectural gap task P1b left behind (a measurement module
    built with no public API for anything else to consume it), not something this task invented
    to work around — future modules wiring `qa/corpus`'s other packages (e.g. the Check screen's
    own node-economy checks, task M0/`campaign`) should check for the same problem rather than
    assume `internal` there is already open where it is needed.
    *Whoever next wires a `qa/corpus` package into `ui` or another consuming module — check
    visibility first, the way this task did, rather than discovering it mid-implementation.*

51. **"The user's own letter" is `fonts/HyleDeco-Regular.ttf`, this build's own test-fixture font,
    not a real user project — there is still no real "current project" concept wired into `ui`.**
    Every prior P4/P5b task already disclosed this same gap; this task inherits it rather than
    solving it. `AnatomyLensGlyphSet.fromSfntFont(SfntFont)` and every function in
    `AnatomyLensData.kt` are general over any `SfntFont`/`Glyph`, real or drawn — nothing is
    Hyle-Deco-specific — so wiring a real project's own glyphs through this same API, once a
    project-loading flow exists, needs no changes here.
    *Whoever builds real project loading into `ui` — this file's own API is already the shape it
    needs to be; only the caller supplying `SfntFont`/`Glyph` changes.*

52. **Three lens terms ([AnatomyTerm.STOREYS], [AnatomyTerm.ROUNDNESS],
    [AnatomyTerm.X_HEIGHT_TO_CAP_HEIGHT_RATIO]) are wired but are not in
    docs/LESSONS_SCAFFOLD.md section 3's own 26-term Anatomy Lens vocabulary.** This task's own
    instructions explicitly named `Storeys.kt`, `Roundness.kt` and `Proportions.kt`'s
    `xHeightToCapHeightRatio` as features to wire, and `qa/corpus` measures all three for real, so
    they were included as three extra lens terms rather than left unwired — but section 3's own
    list (and `TYPEWRIGHT_BUILD_BRIEF.md` section 9's strand description) never names them as
    Anatomy Lens content; storeys and roundness read instead as Lineages-strand classification
    features that happen to share `qa/corpus`'s style package with the lens. `widthClass` (the
    ninth style feature) was *not* wired for the same reason, deliberately, and section 3 never
    names it either.
    *Product owner: should these three actually ship in the Anatomy Lens tab, stay Lineages/
    identify-it-only classification features, or (for storeys/roundness specifically) ship as
    lens content under different framing than a raw ratio/enum? Not decided here.*

53. **`SerifKind` can only ever return [SerifKind.BRACKETED]/[SerifKind.UNBRACKETED]/
    [SerifKind.NONE] — it never returns a "hairline" kind, even though
    docs/LESSONS_SCAFFOLD.md section 3 names "hairline" as one of serif's three kinds.**
    `qa/corpus`'s `SerifMetrics` measures flare ratio and bracket smoothness only, never serif
    *weight* (thin vs. thick), which is what actually distinguishes a hairline serif (a Didone's)
    from a bracketed one (a Garalde's) at similar bracket smoothness. This is not an oversight
    this task introduced: docs/RESEARCH_font_quality.md's own anatomy section already flags the
    identical gap in its own fetched glossaries ("'aperture' and 'hairline serif' are not defined
    in the fetched glossaries"). `serifEntry`'s own threshold splitting BRACKETED/UNBRACKETED at
    `bracketScore >= 0.5` is this file's own unpublished cut point too (no published rule sets
    one), logged rather than presented as settled.
    *Whoever next extends the style detector or the lens: a "hairline" signal would need a new
    measured feature (likely: serif stroke width relative to the stem, or relative to the o's own
    contrast ratio) — not a reclassification of the existing bracketScore.*

54. **RESOLVED (P6, Overlay tab) — was misdiagnosed as a Hyle Deco fixture data-quality issue;
    it was actually a `core-font` `Os2Table.kt` parser bug, now fixed.** Originally found while
    building `xHeightEntry`/`capHeightEntry`: `OS/2 sxHeight = 0`, `sCapHeight = 500`, while
    `x`/`H`'s own drawn ink heights (`Glyph.inkBounds().maxY`) are `500`/`700`. P6's own Overlay
    tab needed real cap-height/x-height for every one of its layers, cross-checked `Os2Table.kt`
    byte-for-byte against a real `OS/2` table with an independent parser (fontTools) before
    trusting it, and found the real bug: `readOs2Table` never skipped `xAvgCharWidth` (the
    `int16` field right after `version`), so *every* field it read after `version` — weight
    class, fs selection, typo ascender/descender, `sxHeight`, `sCapHeight`, all of it, for every
    font this reader has ever parsed, not just Hyle Deco — came from 2 bytes early. Fixed
    (`Os2Table.kt`, `core-font`); Hyle Deco's real `OS/2` table reads `sxHeight = 500`,
    `sCapHeight = 700` — it agrees with the drawing exactly. There never was a data-quality gap in
    the fixture font. Regression tests: `core-font`'s `HyleDecoCrossCheckTest.os2XHeightAndCapHeightAreReadFromTheRealSpecOffsets`
    (byte-level proof against the real font) and `Os2TableTest` (synthetic-fixture proof, its own
    `version0Bytes()` builder was missing the same field and has been corrected too).
    `AnatomyLensData.kt`'s `xHeightEntry`/`capHeightEntry` still read the drawn glyph's own ink
    first and `OS/2` only as a fallback (CLAUDE.md law 1) — the right general rule regardless, now
    just not load-bearing for this one fixture. *Madhav: no action needed on
    `fonts/HyleDeco-Regular.ttf` itself — it was never wrong.*

55. **This task's own instructions describe cross-checking against an existing `qa/corpus` test
    that already asserts Hyle Deco's measured values ("e.g. if `ContrastStressTest` already
    asserts Hyle Deco's own contrast is some value") — no such test exists.** Checked before
    writing anything: `grep -rl HyleDeco qa/corpus/src` finds only `HyleDecoNodeEconomyTest.kt`
    (node economy, an unrelated check) and `SyntheticGlyphs.kt` (one unrelated comment); every one
    of `qa/corpus`'s own `ContrastStressTest`/`SerifBracketTest`/`StoreysTest`/`TerminalTest`/
    `ApertureTest`/`RoundnessTest`/`ProportionsTest` tests these functions on synthetic glyphs with
    geometrically-obvious expected values (P1b's own instruction), never on the real fixture font.
    `AnatomyLensDataHyleDecoTest` (`ui`'s `desktopTest`) still delivers the real regression check
    the instructions asked for, just against a direct call to the same `qa/corpus` function in the
    same test rather than against a pre-existing fixture — if this file's wiring ever diverged from
    the real function (wrong glyph, stale cache, an off-by-one in the dispatcher), the direct-call
    side of each assertion would still report the true value and the test would fail. The real,
    reproducible numbers this task read once and pinned (contrast ≈1.2513, stress ≈52.5°, roundness
    ≈4.2, x-height 500, cap-height 700, ascender 984, descender −292) are not in any prior
    `qa/corpus` fixture to match against; they are new, in `AnatomyLensDataHyleDecoTest` itself.
    *A data point about this task's own instructions, not a decision — logged per the instructions'
    own "if a check has no measurement behind it... stop and report" spirit; not a gap to fix.*

56. **RESOLVED (P6, LearnScreen shell) — the standalone Learn screen and its minimal entry point
    are now built.** Originally: no composable consumed `AnatomyLensData` — the Learn screen's
    Anatomy Lens tab had no rendered UI, only the data API behind it. This task's own scope, read
    plainly: "wiring...
    into a small, real API," with the deliverable list at the end naming the API, its tests and
    `:ui:spotlessCheck`/`:ui:check` — never a composable. The standalone Learn screen itself
    (matching `ui/typewright-explorer.html`'s `#s-learn` station, law 6) and its own entry point
    are explicitly later work ("the 'Shell' phase," per this task's own instructions), so this is
    the correct, disclosed boundary for this slice rather than an accidental gap — but at the time
    it meant the Anatomy Lens was not yet visible or usable in the app itself (see the Update
    below for how that changed).
    *Whoever builds the Learn screen's Lens tab composable: `anatomyLensEntry(term, glyphSet)` is
    the one function to call per term; `AnatomyLensValue`'s sealed variants are what there is to
    format and draw, following `ui/typewright-explorer.html`'s own `#ln-lens` look (the `.lens` SVG
    diagram with leader lines, the `.defs` definition list below it) per law 6.*
    **Update:** `LearnScreen` (`ui/src/commonMain/kotlin/com/asoc/typewright/ui/learn/
    LearnScreen.kt`) now composes all four already-built tabs — including `LensTab` — behind a
    real `#s-learn`-matching header and `#lnTabs` tab row, and `TypewrightApp` now has a real,
    minimal, always-visible entry point to it. See items 79–84 below for the full account
    (header/tabs shell, the entry-point design and its reasoning, a real bug found and fixed, and
    what this task did and did not test).

## P6: Learn UI — Lineages tab composable (`ui/src/commonMain/kotlin/com/asoc/typewright/ui/learn/LineagesTab.kt`)

The real, self-contained `LineagesTab(texture: CanvasTexture, modifier: Modifier = Modifier)`
composable: the eras strip, crossfade stage, stress dial, scrub bar, caption and identify-it
quiz, wired to `LineagesResources.loadFullBlockInPlayOrder()`/`loadIdentifyItBank()`,
`StrandSequencer.plan` and `SceneRenderer`'s pure `frameAt`/`crossfadeAt`/`stressAngleAt`/
`calloutsVisibleAt`. `./gradlew :ui:spotlessCheck :ui:desktopTest` both run clean for every file
this task touched (`:ui:desktopTest`: 161 tests, 0 failures across `:ui` except one pre-existing,
unrelated failure — see item 63); `:ui:compileKotlinWasmJs`/`:ui:compileTestKotlinWasmJs` also
succeed.

57. **The explorer's own Lineages scrubber (`ui/typewright-explorer.html`'s `#scrub`, `min=0
    max=900`) is one continuous range walking all ten eras at once; the real `SceneRenderer`
    contract (its own KDoc, read plainly) is a normalised `t` in `0.0..1.0` for *one scene*, never
    a block-wide position.** The two are genuinely different shapes, not a rendering detail — the
    explorer's mock has no equivalent of "which scene is this `t` relative to" at all. This task's
    own reading: the eras strip switches *which* scene is on stage (`LineagesTab`'s own
    `sceneIndex` state, reset to `t = 0.0` on every switch, mirroring the mock's own click handler
    jumping its scrubber to that era's start position), and the `[0,1]` scrub bar drives
    `SceneRenderer.frameAt` within whichever scene is currently selected. `docs/LESSONS_SCAFFOLD.md`
    section 1 does not describe a block-wide scrub at all ("Every scene is pausable and scrubbable"
    reads as per-scene too), so this is read as the mock simplifying for a one-screen demo, not as
    a spec this task's own multi-scene composable should reproduce literally.
    *Product owner / whoever next touches the Lineages tab: confirm the eras-strip-switches-scene
    reading is right, versus (for example) a single scrubber that walks the whole eleven-scene
    block continuously the way the mock's own `#scrub` does.*

58. **The identify-it quiz section's own visibility gate, and whether its cards page one at a
    time, are both this task's own reading, not something the explorer specifies.**
    `docs/LESSONS_SCAFFOLD.md` section 1: "the exercise appears after the last scene of a strand
    block" — read here as: the eras strip's own selection has reached the block's last scene
    (`sceneIndex == scenes.lastIndex`), not "has finished scrubbing/autoplaying through it" (this
    task built no autoplay clock at all — see item 61). Once visible, every one of
    `StrandSequencer.plan`'s eligible cards renders at once, stacked in a scrollable column — on
    the real, checked-in Lineages content that is 7 real bank cards (`LineagesRealContentTest`
    pins this exact count; the strand's own single inline scene exercise collides and is excluded,
    per `StrandSequencer`'s own already-correct block-wide rule). The explorer's own `.quiz`
    markup is a single static example and never designed paging chrome for more than one card, so
    "show them all at once" was the smallest reading that needed no invented UI the explorer never
    drew.
    *Whoever next touches the Lineages tab: confirm stacking every eligible card is right, versus
    one-at-a-time behind a "next" affordance — and whether the gate should require the *scrubber*
    to reach the end of the last scene, not just the strip selection landing on it.*

59. **A bank entry's own three-option identify-it list is synthesised, not real content —
    `ExerciseBankEntry` (docs/OPEN_QUESTIONS.md item 39, already logged) has no `options` field at
    all, only `face`/`answer`/`giveaway`.** `optionsFor` (`LineagesQuizItems.kt`) builds one from
    this block's own real era class names (`classNames`, the ten on-stage scene titles, era
    order): the answer plus its two cyclic successors in that list (e.g. Didone's distractors are
    whichever two classes follow Didone in era order), with the answer's own slot in the
    three-option list decided by `index % 3` so a learner scanning several cards does not always
    see it land in the same place. Deterministic on purpose (no `Random`), so the same content
    always renders the same options — real class names this strand actually taught, never
    invented — but the exact scheme (cyclic-successor, not e.g. a curated per-entry distractor
    list, or distractors drawn from the *same broad style family* as the answer) is this task's
    own choice, not specified anywhere.
    *Whoever authors real identify-it content next: consider giving each bank entry its own
    curated `options` list (the schema change item 39 already flags) rather than relying on this
    synthesis, if some distractor pairings read as too easy or too obscure.*

60. **`Stage.align` (`xheight`/`capheight`/`baseline`) is read but not acted on — the crossfading
    `from`/`to` renderings share one fixed font size and one Compose `Box`'s natural text
    baseline, not the explorer's own per-face size compensation.** The explorer's own mock
    (`ui/typewright-explorer.html`'s `EXX` object) hand-carries a measured x-height-to-em ratio per
    face and scales each face's own `font-size` so the two renderings' x-heights visually line up
    despite the faces having different real metrics — this task's own instructions describe only
    "two overlapping `BasicText`s..., each its own font, each its own alpha" and do not ask for
    that compensation, so it was not built. Building it for real (rather than transcribing the
    mock's own hardcoded table, which would not be "measured" by this task, per CLAUDE.md law 5)
    would mean reading each of the ten on-stage faces' real x-height/cap-height metrics at
    runtime — `core-font`'s `SfntFont`/`Os2Table` can do this and is already a dependency of `ui`,
    but parsing all ten (several variable fonts) for this one purpose is real, unscoped work.
    *Whoever wants the crossfade to visually match at x-height/cap-height the way the explorer's
    own mock does: this is the gap, and `core-font`'s `Os2Table`/`HeadTable` are the real,
    already-built pieces to read the metrics from — not a fabricated per-face ratio table.*

61. **No autoplay: the scrub bar and eras strip are entirely manual (drag/tap), matching the
    explorer's own mock (no play button anywhere in `#ln-lin`), but `Scene.duration` and
    `SceneRenderer.normalizedScrub` (an autoplay-clock-to-`t` converter that already exists and is
    already tested) go entirely unused by this composable.** `docs/LESSONS_SCAFFOLD.md`'s own
    worked example calls `duration` "seconds at the default pace; scrubbing ignores it," implying
    an autoplay pace exists conceptually even though the explorer never builds a control for it.
    *Whoever wants autoplay ("play" affordance, `LaunchedEffect` ticking `normalizedScrub` forward
    until the learner touches the scrub bar): the pure conversion is already there in
    `SceneRenderer`; only the driving clock and its pause-on-touch interaction are missing.*

62. **`Scene.callouts` (era 3/Transitional's own `a`/terminal label) has no visual design in the
    explorer at all — `ui/typewright-explorer.html` never renders a callout anywhere.** This
    task's own instructions still name `calloutsVisibleAt` among the four `SceneRenderer`
    functions to drive, so `StageArea` renders each visible callout as a small violet mono chip
    (`"${glyph} · ${part}: ${label}"`) stacked at the stage's bottom-start once
    `frame.calloutsVisible` is true, rather than skipping the data entirely. This is this task's
    own minimal, honestly-improvised design (law 6 has nothing to reproduce here), not a
    transcription of anything drawn.
    *Whoever owns the explorer/UI_SPEC: a real `.callout` look (the scaffold's own text: "optional
    labels on the stage, resolved on the `to` face") belongs in `ui/typewright-explorer.html`
    first, per law 6, with this composable then reproducing it exactly instead of the placeholder
    chip built here.*

63. **A real bug in the P6 Foundations stage's `learnFaceFontFamily` (`LearnFaceFonts.kt`), found
    and fixed while building this tab, not merely worked around.** Its own KDoc already claimed
    wasmJs (browser) "catches that failure and falls back to `FontFamily.Default` ... rather than
    crashing," but the function's first line, `LearnFaceResources.entry(key)`, was not actually
    wrapped in `runCatching` — only the `fontBytes(key)` call below it was. `LearnFaceResources`
    reads `data/learn-faces/manifest.json` through the same Node-`fs`-based
    `readSceneResourceText` `fontBytes` already needed guarding against (item 47), so on `ui`'s
    real `wasmJs { browser() }` target (unlike every existing test of this function, which calls
    `platformLearnFaceFontFamily` directly and never exercises `LearnFaceResources` at all) the
    very first real call from a composable — this one — would have thrown uncaught. Fixed with the
    same one-line `runCatching` the very next line already used; `LineagesQuizItems.kt`'s own new
    `learnFaceKeyForFamily` (which also reads `LearnFaceResources.allEntries()`, for a bank
    entry's family name) is written the same defensive way from the start. Neither this fix nor
    the new helper could be exercised against a real browser here — no Chrome binary in this
    container (CLAUDE.md law 4) — but `:ui:compileKotlinWasmJs`/`:ui:compileTestKotlinWasmJs` both
    still succeed, and `LineagesQuizItemsTest` (commonTest, runs on `:ui:wasmJsBrowserTest` too)
    was deliberately written against hand-built fixtures rather than real `LineagesResources`
    calls for exactly this reason — see that file's own KDoc.
    *Whoever next has a real browser to verify against: confirm the Lineages tab's real on-stage
    faces actually fall back to the system font cleanly there (`learnFaceFontsAreReal()` already
    reports `false` on wasmJs), rather than crashing the whole tab, the way this fix intends.*

64. **RESOLVED (P6, Overlay tab piece) — the concurrent `qa/corpus`/`Geometry2D.kt` edit this item
    originally flagged was the Overlay tab's own `lineCrossings`/`inkIntervals` visibility widen,
    and the failing test it named was a real, load-bearing correctness bug, not a flake.**
    `AnatomyLensDataHyleDecoTest.hyleDecosOwnOs2TableDoesNotMatchItsDrawnInkHeights` failed because
    `core-font`'s `Os2Table.kt` had a real parser bug (missing `xAvgCharWidth` skip, misreading
    every `OS/2` field after `version` by 2 bytes for every font ever read through it) — fixed,
    with the test renamed and corrected to match
    (`hyleDecosOwnOs2TableActuallyMatchesItsDrawnInkHeights`); see item 54 (also corrected there)
    and `Os2Table.kt`'s own KDoc for the full story. `:ui:spotlessCheck`/`:ui:desktopTest` both
    pass clean post-fix (221 tests, 0 failures, re-verified after this item's own resolution).
    *No action needed — kept here, marked resolved, rather than deleted, so the record of what
    happened during P6's concurrent-agent window stays intact.*

65. **The Overlay tab's comparison layers use `MeaningColors.AMBER`/`.MAGENTA`, not
    `ui/typewright-explorer.html`'s own literal violet/cyan swatches for `#ln-ov`'s `l2`/`l3`
    layers.** `ui/tokens/CanvasTokens.kt`'s own `MeaningColors` object (built before this task)
    already documents `VIOLET` as "selected node or contour" and `CYAN` as "the thing you are
    snapping to" — both real, distinct meanings this app uses elsewhere (the Draw/Space rooms'
    own puck/selection chrome) — while `AMBER`/`MAGENTA` are the two the object's own KDoc already
    calls out as "comparison layers, paired with a line pattern". Using violet/cyan here, as the
    explorer's own static mockup does, would put one colour on two different meanings at once
    (a selected node and a comparison face) — exactly what CLAUDE.md law 8 ("colour is meaning
    only") rules out. Dash patterns (`9 5`/`2 4`) are still reproduced from the explorer exactly;
    only the two colours differ. See `OverlayLayer.kt`'s own KDoc for the same reasoning inline.
    *Madhav (or whoever owns the explorer's own source): either the explorer's `#ln-ov` CSS
    (`.lay.l2`/`.lay.l3`, `.sw.l2`/`.sw.l3`) should be updated to amber/magenta to match the real
    token system, or `MeaningColors` should grow a violet/cyan variant that is defined to also
    mean "comparison layer 2/3" — right now the two disagree and this task picked the real token
    system's own stated meaning over the mockup's literal hex values.*

66. **Hyle Deco's real font bytes reach the Overlay tab as a base64 string embedded in
    `ui`'s own commonMain source (`HyleDecoProjectFontBytes.kt`), not through a Gradle resource
    sync + classpath/fetch read the way `data/learn-faces`'s seventeen faces do
    (`learn:scenes`'s `LearnFaceResources.kt`, `syncLearnFaceData`).** Checked before building this
    way: no existing module syncs `fonts/HyleDeco-Regular.ttf` (or `-Italic.ttf`) into any KMP
    module's runtime resources today — `core-geometry`'s and `ui`'s own tests that read it do so
    with `java.io.File` off disk, JVM-only, which a real Compose page cannot do on every target
    (no filesystem on Android or in a browser). The embedded-base64 approach mirrors `core-font`'s
    own `HyleDecoRegularTtfBase64.kt` (built for exactly the same "every target, no resource
    pipeline" reason, for its own commonTest), so it is a real, precedented pattern in this
    codebase, not invented for this task — but it does mean Hyle Deco's bytes now live in three
    places (`fonts/`, `core-font`'s commonTest fixture, `ui`'s own commonMain), and a future redraw
    of Hyle Deco needs all three regenerated by hand (the file's own KDoc gives the exact
    regenerate command). A proper `syncProjectFontData`-style Gradle task (mirroring
    `syncLearnFaceData`) into a module every KMP target can read from would remove the duplication
    and the manual-regeneration risk, at the cost of solving cross-target resource reading for real
    (`ui`'s own `learnFaceFontsAreReal()`/`LearnFaceFonts.wasmJs.kt` KDoc already discloses that
    `learn:scenes`' own resource-reading approach does not reach a real browser — the same problem
    would apply to a `ui`-owned Hyle Deco resource sync).
    *Whoever next needs "the project's own font" available at runtime from more than one `ui`
    file (this task's own Overlay tab is the first caller): consider building the real resource
    pipeline once, generally, rather than a second embedded-bytes file per caller.*

67. **The Overlay tab's redline/divergence check and stroke probe are scoped to single glyphs
    (`'H'` for redline, `'b'`/`'H'`/`'o'` for the probe), not the whole rendered word or an
    arbitrary user-selected glyph, and the word field's own "per glyph ›" label
    (`ui/typewright-explorer.html`'s own `#ln-ov` markup, reproduced verbatim) does nothing when
    tapped.** Comparing a whole multi-glyph word's flattened outlines directly would need each
    layer's own advance widths reconciled first (two real fonts rarely agree on `b`'s advance
    width, so `"Hamburg"`'s fourth glyph does not sit at the same x-cursor in every layer past the
    first letter) — solvable, but a materially bigger feature (per-glyph alignment/selection UI,
    not just a redline check) than this task's own scope covered. `'H'` (the word's own first
    letter, so every layer's cursor starts at the same shared origin with no reconciliation needed)
    was chosen as the one real, always-position-matched glyph to redline-check by default.
    *Whoever builds the explorer's own `"per glyph ›"` destination for real: the redline/probe
    glyph choice below should probably become whatever glyph the user is currently focused on
    there, not a fixed 'H'/'b' pair.*

68. **`OverlayTab`'s word field wraps `"Hamburg"` mid-word ("Hambu"/"rg") on a narrow phone-width
    screenshot (`OverlayTabScreenshotTest`'s own 210dp-logical viewport, matching this codebase's
    established Learn-tab screenshot convention — `LineagesTabScreenshotTest`'s own same
    `width = 420, density = 2f`).** `OVERLAY_DEFAULT_WORD` is one word with no internal space, so
    Compose's own default wrap has no natural break point at that width; every other row in this
    tab was fixed to wrap or ellipsize cleanly (this task's own real screenshot-driven find), but
    the word field itself was left to wrap mid-word rather than truncate it (CLAUDE.md law 5 spirit
    extended here: the word is content, not chrome, so hiding letters behind an ellipsis felt like
    the worse trade of the two, even though the mid-word split is visually rougher).
    *Whoever next tunes this tab's own layout: a smaller word-field font size on narrow widths, or
    a shorter default word, would remove the mid-word wrap without hiding any letters.*

## P6: Learn UI — Anatomy Lens tab composable (`ui/src/commonMain/kotlin/com/asoc/typewright/ui/learn/LensTab.kt`, `LensScene.kt`, `LensRenderPlan.kt`)

69. **RESOLVED (P6, Anatomy Lens tab) — `GeometryInterop.kt`'s `Glyph.toComposePath` rendered a
    real closed counter as solid ink, not a hole, under Compose's own default `PathFillType`.**
    Found while building this tab's own real diagram (a real screenshot of Hyle Deco's `o`
    rendered as a solid filled stadium shape, no counter visible) — not a guess: Compose's
    `Path.fillType` defaults to `PathFillType.NonZero`, which only punches a hole when the outer
    contour and its counter wind in genuinely opposite directions, and this codebase's own two
    conventions for that (TrueType's on-disk rule, and this module's own cubic-source "outer
    counter-clockwise, inner clockwise") are not guaranteed to agree once a caller's own transform
    (a y-flip, in this tab's case) is applied on top. Fixed by setting `path.fillType =
    PathFillType.EvenOdd` in `Glyph.toComposePath` itself (`GeometryInterop.kt`, the one shared
    outline-to-Compose-path bridge every `ui.learn` tab uses) — even-odd never depends on winding
    direction at all, only crossing parity, so it is correct regardless of source convention. This
    is a shared-file fix, not scoped to this tab alone: the Overlay tab's own `OverlayTabScreenshotTest`
    and every other `ui:desktopTest` that renders a glyph outline through this bridge were re-run
    after the change and stayed green (223 tests, 0 failures, `./gradlew :ui:spotlessCheck
    :ui:desktopTest`), so this was very likely an undiscovered bug in the Overlay tab's own ink
    rendering too (a closed counter in `"Hamburg"`'s `a`/`b`/`g` would have had the same solid-fill
    problem), now fixed for both. *No action needed — kept here, marked resolved, as the record of
    a real cross-tab bug this task found and fixed centrally, per the pattern item 64 already set.*

70. **The Lens diagram shows one real glyph at a time, switching to a different real glyph when a
    tapped term needs one, rather than `ui/typewright-explorer.html`'s own `#ln-lens` static scene
    (one hand-drawn "a" carrying all four of its own worked-example labels at once).** This task's
    own instructions require wiring real measurements, and `AnatomyLensData.kt`'s own KDoc is
    explicit that different terms are measured on different real glyphs (contrast/stress/roundness
    on `o`, serif on `T`, terminal/aperture on `c`/`e`/`s`, storeys on `a`/`g`) — always drawing one
    fixed glyph regardless of the selected term would point a leader line at ink that was never
    actually the subject of the measurement on screen, which CLAUDE.md laws 1 and 5 rule out. The
    compromise built here: `termsSharingHeroChar` groups every curated term by its own real hero
    glyph, so the four `o`-measured terms (bowl, counter, contrast, roundness) *do* still show as
    simultaneous leader lines on one glyph, reproducing the explorer's own multi-label look for
    that group (a real screenshot, `ui/build/screenshots/lens-tab-paper.png`) — but stem (`n`),
    serif (`T`), storeys (`a`) and terminal/aperture (`c`) each get their own, separate diagram,
    switched to on tap. *Product owner: confirm this per-glyph-group switching is the right reading
    of "the lens" for a real font, versus (for example) a small glyph-picker strip always visible
    so a learner can jump straight to any of the five real diagrams without going through the
    definitions list.*

71. **The storeys diagram always draws `a`, even on a font (like this build's own Hyle Deco
    fixture) where the real measured value actually came from `g`'s own topological answer, not
    `a`'s heuristic one.** `storeysEntry` (`AnatomyLensData.kt`) prefers `g`'s answer when it is
    available and non-`UNKNOWN` — the more reliable, non-heuristic signal, per that function's own
    KDoc — so the *value* shown next to "storeys" in the definitions list can come from `g` while
    the *diagram* above it is drawing `a`. `LensScene.kt`'s own `HERO_CHAR_CANDIDATES` chose `a`
    over `g` for the diagram deliberately (a single-storey-vs-double-storey distinction is usually
    easier to read on `a`'s own bowl-plus-arm shape than on `g`'s descender-plus-loop one, and
    Hyle Deco's own real `a` turned out to be an unusual, tall-stemmed construction once actually
    rendered — see this task's own `lens-diagram-storeys-a.png` screenshot), but this means the
    diagram is not always drawing *the specific glyph the number on screen was measured from*.
    *Whoever next revisits this: showing `g` instead when `storeysEntry` actually used it (i.e.
    picking the diagram's own hero glyph the same way the measurement itself picked its source,
    rather than a fixed preference) would close this gap.*

72. **Leader-line target points are a fixed fraction of each hero glyph's own real ink bounds
    (`LensScene.kt`'s own `LEADER_ANCHOR`), not the exact point the underlying `qa/corpus` probe
    actually touched.** Checked before building this way: `AnatomyLensValue` carries only the real
    aggregate number or enum each measured term's own function returns (a ratio, a degree, a
    style) — never a location — for every one of `contrastRatio`/`stressAngleDegrees`/
    `superellipseExponent`/`apertureOpenness`/`terminalStyle`'s own real signatures. The one place
    a located point exists internally, `qa/corpus`'s own `narrowestThroat` (`Geometry2D.kt`), is
    `internal` and was not widened for this task (a second AnatomyLensData-style visibility change
    this task was not asked to make). The fractions themselves were chosen by eye per term and
    checked against this task's own real rendered screenshots (`ui/build/screenshots/lens-tab-*.png`,
    `lens-diagram-*.png`), so every leader line does visibly land on real ink for this build's own
    Hyle Deco fixture — but a differently proportioned real project font could, in principle, place
    one of these fractional points off the actual anatomical part on a glyph shaped unusually
    enough (item 71's own Hyle Deco `a` is exactly this kind of surprise). *Whoever next wants
    pixel-exact leader lines: widen `narrowestThroat`'s own result type to expose the point it
    already finds internally, the same way this task's own Foundations stage widened `qa/corpus`'s
    other internals for the Lens's own real values.*

73. **A leader line's own connecting stroke has no separate tap target — only its two ends (the dot,
    and the label text) are tappable.** CLAUDE.md's own instructions for this task say tapping "a
    leader line/label" selects a term; this build reads that as "the two things a leader line
    visually terminates in", both wired (`LensDiagram`'s own dot hit-box and label `clickable`), but
    a tap on the thin connecting line itself, between those two points, currently does nothing.
    Given the line is 1.2dp wide, a real hit target along its whole length would need either a
    wider invisible stroke or a small custom hit-test, neither built here. *Minor — flagging rather
    than fixing, since the dot and label together already cover the two ends a reader's eye and
    finger would naturally reach for.*

## P6: Learn UI — Scrapbook tab composable (`ui/src/commonMain/kotlin/com/asoc/typewright/ui/learn/ScrapbookTab.kt`)

The real, self-contained `ScrapbookTab(texture: CanvasTexture, modifier: Modifier = Modifier)`
composable (`ui/typewright-explorer.html`'s `#ln-scrap`): a real `ScrapbookManifest`/`ScrapbookPin`
data model with a real JSON codec (`ScrapbookManifestCodec`, snake_case fields matching
`data/learn-faces/manifest.json`'s own convention — full reasoning in `ScrapbookManifest.kt`'s own
KDoc, including why this is one flat JSON file rather than `core-font`'s `UfoProject`-style
many-files-as-a-map), a fixed four-pin `SampleScrapbook.MANIFEST` (three photo pins carrying the
explorer's own `.pinmark`, one note pin that does not — the same 3-of-4-driving proportion the
explorer's own real 14-pin board has), and the pin grid itself (pattern-block/note-text image
block, caption row, `.pinmark`). `ui/build.gradle.kts` gained the `kotlin.serialization` plugin and
`kotlinx-serialization-json` for this (the same dependency `qa/corpus`'s and `learn/scenes`'s own
`build.gradle.kts` files already carry). `./gradlew :ui:spotlessCheck :ui:desktopTest`: desktopTest
242 tests, 0 failures; spotlessCheck fails only on one pre-existing, unrelated file this task never
touched (`src/desktopTest/kotlin/com/asoc/typewright/ui/GeometryInteropPathTest.kt`, a real
dangling-top-level-KDoc ktlint violation, untracked in git at the time this task ran — apparently a
concurrent sibling agent's in-progress file — confirmed unrelated by running `spotlessCheck` before
touching anything: `:ui:spotlessKotlin` itself reports `UP-TO-DATE`/clean for every file this task
wrote once its own two real formatting bugs, items 72–73 below, were fixed).

74. **A real, screenshot-caught Compose `Row` bug, found and fixed twice in this one file, not
    merely worked around — worth recording generally, since the next Learn tab (or any tab) can
    walk into the exact same trap.** `Row`'s own measurement (confirmed by disassembling
    `foundation-layout`'s own `RowScope.class`, not guessed) measures every *unweighted* child
    first, each against the **entire** remaining main-axis budget in declaration order — not a
    fresh full-width budget per child, and not a fair share. A short *label* text can still eat
    almost the whole row if its own content happens to need it (e.g. `"Devanagari"` at 11sp easily
    fills a narrow pin column on its own), starving a sibling that has real content to show. Two
    real instances this task hit and fixed: `ScrapbookHeader`'s own `"4 pins · 3 driving the
    design"` label originally crushed the sibling `"+ photo · + note"` row into unreadable
    one-character-per-line wrapping (fixed: give the *label*, not the affordance row, a real
    `Modifier.weight(1f, fill = false)`, so the short affordance row is measured first, at its
    own natural width, and the label absorbs whatever is left); `PinCaption`'s own `captionTitle`
    versus `captionSource` had the mirror problem (`"Devanagari"` — plausible-looking as "the short
    one" — still greedily consumed most of a narrow pin column before the *actually* long
    `captionTitle` was measured at all, e.g. `"1912 primer · scan"` collapsing into single letters
    per line), fixed with fixed *proportional* weights on both sides (`weight(3f)` /
    `weight(2f)`, both `fill = true`) rather than "weight the one that looks long", since neither
    side's true rendered width is knowable from its string alone. Every published screenshot in
    `ui/build/screenshots/scrapbook-tab-*.png` was regenerated and eyeballed after both fixes.
    *No action needed elsewhere in this codebase today — no other `Row`/`Column` here currently
    puts two variable-length real-content texts side by side without a weight on each — but
    whoever next builds a tab with two adjacent text elements should give both a real weight from
    the start, not just the one that looks like it will need protecting.*

75. **"+ note" is a real, working affordance this task built from nothing — `#ln-scrap`'s own
    markup never draws an input for it at all** (`<span class="mut">+ photo · + note</span>` is a
    single static label; the explorer never shows what tapping either half does). "+ photo" reads
    straightforwardly (append a placeholder pin — there is no image picker to open, CLAUDE.md law
    4), but "+ note" needed an actual text-entry surface this task invented: a single-line
    `BasicTextField` plus "add"/"cancel" (`NoteDraftRow`), styled off `ui.glass`'s own
    `CommandPalette` input row (the one other place in `ui` that already builds a real text field)
    rather than off anything in `#ln-scrap`, since there is nothing there to reproduce. Both
    affordances only ever append to this composable's own `remember`ed, in-memory pin list — never
    written to any file — the same "no real project to persist into yet" gap
    `HyleDecoProjectFontBytes`'s own KDoc and this section's own item 56 already disclose for their
    own pieces of Learn.
    *Whoever owns `ui/typewright-explorer.html`: a real `#ln-scrap` note-composer design (so a
    future rebuild has something concrete to reproduce, per law 6) would replace `NoteDraftRow`'s
    own improvised look. Whoever wires a real current-project flow into `ui`: `ScrapbookManifest`/
    `ScrapbookManifestCodec` are ready to read/write a real `scrapbook/manifest.json` the moment
    there is a real project directory to point them at — nothing about the data model or codec is
    sample-specific.*

76. **The two-column pin board is a small greedy shortest-column heuristic
    (`splitIntoBalancedColumns`, keyed off a fixed per-kind `estimatedPinHeightDp`), not a real
    measured layout and not CSS multi-column.** `.scrap{columns:2}` is browser-native balanced
    masonry over each pin's own *real* rendered height; Compose has no direct equivalent short of
    an experimental staggered-grid API this codebase uses nowhere else, so this task built the
    smaller, plain thing instead — good enough for a 4-pin sample plus a handful of appended ones,
    but it will drift from a true height-balanced board once note pins carry very different
    amounts of text (every note pin is estimated at the same fixed height today, regardless of
    what was actually typed into `NoteDraftRow`).
    *Whoever wants real masonry parity with the explorer's own CSS: swap `splitIntoBalancedColumns`
    for `androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid` (real per-item
    measured heights, shortest-column placement) once a board with more than a handful of pins
    makes the difference visible.*

77. **A deliberate visual departure from `#ln-scrap`'s own literal CSS: pins are tilted.**
    `ui/typewright-explorer.html`'s own `.pin`/`.im` rules carry no rotation at all — its board is
    flat. This task's own instructions still ask for "a rotation degrees value for the pinned look"
    in the data model and a stable per-pin tilt in the rendering, so `PinCard` applies
    `ScrapbookPin.rotationDegrees` as a whole-card `graphicsLayer` rotation — mirroring a
    *different* real part of this app's own house style (the review-grid cells' own small `--rot`
    tilt elsewhere in the explorer, `.cell .cg{transform:rotate(var(--rot,0deg))}`) rather than
    inventing the idea from nothing, but still not what `#ln-scrap` itself draws today.
    *Whoever owns `ui/typewright-explorer.html`: either add the tilt to `#ln-scrap`'s own CSS (so
    the mock and the real composable agree, per law 6), or say the board should in fact stay flat
    and this composable should drop `graphicsLayer` and read `rotationDegrees` for nothing but
    round-trip fidelity.*

78. **`SampleScrapbook`'s three photo pins' own ids were chosen, among otherwise-equivalent
    spellings, so `patternVariantForPinId` lands on a different pattern for each.** The first
    spelling tried (`"signage-charminar"`, `"1912-primer-scan"`, `"sketch-controls"`) hashed all
    three to the same `DOT` variant by real coincidence — caught in this task's own screenshot step,
    not a hypothetical — and `patternVariantForPinId`/`stableHashCode` themselves were *not*
    tuned to fix it (they stay general-purpose, tested functions); only the three ids' own
    spellings changed. This is an honest, disclosed cosmetic choice about *sample data*, not a
    correctness fix — a real manifest's own pin ids (a timestamp, a UUID, whatever a real "+ photo"
    flow generates) will keep landing on whichever of the three patterns their own hash lands on,
    collisions included, and nothing about this composable treats that as a bug.
    *No action needed — noted for the record so a future reader of `SampleScrapbook.kt`'s own ids
    does not mistake the specific spellings for meaningful data.*

## P6: Learn UI — LearnScreen shell and its entry point (`ui/src/commonMain/kotlin/com/asoc/typewright/ui/learn/LearnScreen.kt`, `LearnScreenState.kt`, `ui/src/commonMain/kotlin/com/asoc/typewright/ui/TypewrightApp.kt`)

79. **`LearnScreen`'s own header bar is a purpose-built composable (`LearnHeaderBar`), not
    `com.asoc.typewright.ui.glass.Header`.** [Header] is UI_SPEC §3's *one-sheet* header — a
    room-name ink block plus a MAP toggle, built around horizontal-swipe room navigation
    (`onSwipePrevious`/`onSwipeNext`) that has no equivalent meaning on `#s-learn`'s own bar (back
    button, title block, commands button — a different shape). Bending `#s-learn`'s bar through
    [Header]'s own room/swipe-shaped API would mean adding parameters its one real caller does not
    need, just to reach a different design; this task's own instructions left the choice open
    ("your call, document it"), so `LearnHeaderBar` reuses the same tokens (`Typography`,
    `CanvasTexture`) directly instead, per the task's own "reuse tokens, not a second token
    system" instruction.
    *No action needed — a documented judgment call, not a gap.*

80. **The commands button (`›_`, `.ib.pal`, aria-label "Commands") is visually present and
    deliberately unwired — no `clickable` modifier at all.** It reuses the exact glyph
    `CommandPalette.kt`'s own input row already prints for its prompt (line 158), the established
    visual language for that affordance in this codebase, but tapping it does nothing: wiring a
    real command palette onto the Learn screen is out of this task's own scope, whose own
    instructions are explicit that an unwired button here is fine, disclosed. Leaving off
    `clickable` entirely (rather than attaching a no-op handler) was a deliberate choice so it
    reads, correctly, as inert chrome rather than as a button that silently swallows a tap.
    *Whoever wires a real command palette onto the Learn screen: `CommandPalette` (`ui.glass`) is
    already built and already used by `TypewrightSheet`; the same `visible`/`onDismiss` shape would
    drop in here.*

81. **A second real instance of item 74's own predicted trap — Compose `Row` measuring unweighted
    children sequentially against the remaining budget, not a fair or fresh share — found in
    `LearnScreen`'s own `#lnTabs` tab row itself, not just a Scrapbook-tab-specific bug.** At this
    task's own narrow screenshot width (420 px / density 2 = 210 dp, the same width every sibling
    tab's own screenshot test already uses), a plain `Row` of four tab buttons left "Lens" and
    "Scrapbook" almost no budget, wrapping their labels one character per line — caught by looking
    at `learn-screen-lin.png`/`learn-screen-lens.png` before reporting, not assumed. Fixed by
    reusing this same package's own established precedent for exactly this shape of overflow:
    `LineagesTab.kt`'s `ErasStrip` already wraps its own multi-item row in
    `Modifier.horizontalScroll(rememberScrollState())`; `LearnTabsRow` now does the same, so a tab
    label that does not fit scrolls instead of vertically wrapping. Re-screenshotted after the fix
    (`learn-screen-lin.png`/`-ov.png`/`-lens.png`/`-scrap.png`, all five textures) and confirmed by
    eye: no more per-character wrapping at 210 dp, and the wider `app-learn-open.png` render
    (360 dp, `TypewrightAppEntryPointScreenshotTest`) shows all four labels fitting on one line
    with no scroll needed there.
    *No action needed elsewhere — item 74's own advice ("give real content real room, or let it
    scroll") held again; recorded so a *third* occurrence is recognized just as fast.*

82. **The entry point (`TypewrightApp`'s own "Shell" phase): a `TypewrightAppNavState` (real,
    testable state, not a debug flag) plus a small, always-visible "Learn" corner tab.** Chosen
    over a debug-only flag or a no-op placeholder because this task's own instructions rule both
    out by name. The tab is pinned to the bottom-end corner specifically — not guessed: reading
    `TypewrightSheet.kt` in full first, every *other* corner/edge of the phone is already claimed
    by the sheet's own glass ([Header] top-start, `LayersPanel` top-end, `SpaceRoomGlass`/
    `CommandPalette` top-centre, `InspectorRow` bottom-start, `EdgeMarks` centre-start/-end) —
    bottom-end is the one corner nothing else in the sheet draws into, confirmed by screenshot
    (`app-learn-closed.png`) rather than assumed clear. `LearnScreen`'s own real back button is the
    return path (`onBack = navState::closeLearn`); no second affordance was added for that
    direction. This is still only the entry point this task's own instructions scope it to — the
    *full* cross-station navigation shell (Home/Capture/Trace/Economy/Draw-Space-Learn/Check/Ship/
    Workbook/Desktop) remains separate, later, undesigned-by-the-explorer work, per this task's own
    instructions; that larger gap was already disclosed above (item 56) and stays disclosed, not
    re-logged as new.
    *Whoever designs the real cross-station shell: `TypewrightAppNavState` is a small, single-
    purpose state class (today, only `showLearn`); it is not intended to grow into that shell's own
    state and should probably be replaced, not extended, once that design exists.*

83. **Tab-switching and the entry point are tested by asserting directly on the real state classes
    (`LearnScreenUiState.select`, `TypewrightAppNavState.openLearn`/`closeLearn`), not by
    simulating a pointer click against the rendered composable.** No test anywhere in this
    codebase simulates a real pointer event against a Compose scene (confirmed by reading every
    existing screenshot test before writing new ones, not assumed) — the established equivalent,
    set by `SheetScreenshotTest`'s own `puckState.gestureState = PuckGestureState.RadialOpen(...)`,
    is to pre-seed or mutate the same plain state class the real `onClick` calls and assert on it
    directly, then separately confirm-by-screenshot that each state renders correctly. Both new
    state classes ([LearnScreenUiState], `TypewrightAppNavState`) are built the same shape as
    `PuckUiState` specifically so this equivalence holds, and each tab button's/entry button's own
    `onClick` calls the exact method under test (`select`/`openLearn`/`closeLearn`) — not a
    parallel stand-in for it. This is disclosed as the honest boundary of what "tested" means for
    this piece: it proves the state mechanism and every rendered state are each correct, not that a
    real on-device or mouse tap reaches that mechanism (CLAUDE.md law 4's own device-honesty spirit,
    applied here to a plain click rather than a gesture).
    *No action needed — matches this codebase's own established testing convention exactly; noted
    so it reads as a deliberate choice, not an oversight, if a future reviewer goes looking for a
    `sendPointerEvent`-style test and does not find one anywhere in this module.*

84. **`LearnTabButton`'s selected state is a plain ink-block background plus ordinary padding, not
    `#lnTabs button.on`'s own literal CSS (`background:var(--ink);...;padding:2px 6px;margin:-2px
    -6px`).** The explorer's negative-margin trick keeps the ink block's own padding from pushing
    neighbouring tabs apart, letting it visually bleed slightly into the row's own gap instead.
    Compose has no direct negative-margin equivalent; `LearnTabButton` uses the same simplification
    `OverlayTab.kt`'s own `SegmentedButton` already established for this exact shape of control
    (conditional `.background(ink)` plus fixed padding, no margin compensation) — consistent with
    an already-precedented choice in this same package, not a new one.
    *No action needed — the visual difference is a few dp of tab spacing, not a meaning or
    legibility change; noted for completeness only.*

## P6: Learn UI — verification pass (LearnScreen tab-scroll bug)

85. **`LearnTabsRow`'s selected-tab ink block could render entirely off-screen, invisible, when a
    non-drag-selected tab was current.** Found by screenshot
    (`ui/build/screenshots/learn-screen-scrap.png`,
    `LearnScreenScreenshotTest.rendersEachOfTheFourTabsWithoutCrashing`, Scrapbook state): the
    row's `Modifier.horizontalScroll(rememberScrollState())` (`LearnScreen.kt`, `LearnTabsRow`)
    starts at scroll offset 0 and has nothing to move it, so a tab selected any way other than the
    user's own drag (the initial tab, a restored/caller-supplied `LearnScreenUiState`) shows only a
    one-pixel sliver of its own ink block clipped at the row's right edge, its label fully
    off-canvas. Fixed with a `BringIntoViewRequester` per tab button (`LearnTabButton`,
    `LearnScreen.kt`), the standard Compose mechanism for this exact problem, triggered from a
    `LaunchedEffect(selected)`. Could not be confirmed by this codebase's own screenshot harness:
    `ScreenshotHarness.capture` (`ui/src/desktopTest/kotlin/com/asoc/typewright/ui/ScreenshotHarness.kt`)
    calls `ImageComposeScene(...).use { it.render() }` exactly once, and this was confirmed
    empirically (a throwaway probe pumping 90 synthetic `render(t)` frames, 1.44s of simulated
    time, through a raw `ImageComposeScene`) that a `LaunchedEffect`'s coroutine never gets
    dispatched at all in that harness — the scroll offset stayed at exactly 0 for all 90 frames.
    This is a real, general limitation of the harness (no coroutine dispatcher/frame clock backs
    it), not specific to this fix, and it means any future effect-driven behavior in this codebase
    is similarly unverifiable by screenshot alone.
    *For whoever next touches `ScreenshotHarness`: it would need to pump several `render()` calls
    with advancing `timeNanos` under a real `MonotonicFrameClock`/coroutine dispatcher (not just
    one) before this class of fix becomes screenshot-checkable; not attempted here since it changes
    every existing screenshot test's own capture semantics, out of scope for a single-tab fix.*

## P7: Campaign engine (`:campaign` module — `WorkbookTask.kt`, `WorkbookGates.kt`, `WorkbookLatinContent.kt`, `WorkbookYamlParser.kt`, `workbook-latin.yaml`)

86. **`core-font` still has no generic SFNT-to-`UfoProject` bridge, and this task is now the
    second place that needed one and hand-rolled a small, local version instead of building the
    real thing.** `HyleDecoTask4GateTest.kt` (`campaign/src/jvmTest`) reads
    `fonts/HyleDeco-Regular.ttf` with `core-font`'s real `readSfntFont`, pulls out four named
    glyphs (`n`/`o`/`H`/`O`) by hand, and wraps them in a `UfoProject` built inline, just enough
    for `WorkbookGates.task4ControlCharacters` to run against for real — this task's own brief
    said plainly there is no such bridge and pointed at the closest existing precedent
    (`core-geometry`'s `FitPipelineHyleDecoValidationTest`, `ui`'s
    `HyleDecoProjectFontBytes.kt`), none of which is a real, reusable, whole-font
    `SfntFont -> UfoProject` conversion either — each reads a handful of glyphs by name for its
    own narrow purpose. As `qa`'s gates (this task's own `WorkbookGates`, and presumably a future
    Check-room UI) increasingly want to run real `UfoProject`-shaped checks against a *compiled*
    font (a device's own font file, a Google Fonts comparison download, or — per M5's own
    workbook shape — the learner's own font at whatever stage it is in), a real, whole-font,
    tested `core-font` bridge (every glyph, real advance widths, real `UfoFontInfo` from the
    `head`/`hhea`/`OS/2` tables already parsed) would replace every one of these ad hoc,
    partial, per-task versions with one correctness-critical, `core-font`-owned implementation
    (CLAUDE.md law 2's own module boundary — this belongs in `core-font`, not repeated in every
    caller). *Whoever next needs a `UfoProject` from a compiled font for a fourth time: this is
    the signal to build the real bridge in `core-font` rather than writing a fifth inline
    version.*

    P10: `SfntFont.toUfoProject()` added in core-font for the golden-path seeds; it carries no
    unicodes yet (item 124).

87. **A real, empirically-verified wasmJs limitation, distinct from item 47's browser/Node split:
    `learn:scenes`' `readSceneResourceText` wasmJs actual cannot be called from a *different
    module's own compiled* `wasmJs { nodejs() }` test bundle, even though both are the identical
    Node target.** `WorkbookLatinContentTest` (`campaign/src/commonTest`) only checks the exact
    `sceneId` string task 1's/9's YAML carries (`"lineages.transitional"` /
    `"craft.c1-baked-composites"`); proving those strings are real, *loadable* `learn:scenes`
    scenes needed calling `LineagesResources.loadEraScenes()`/`CraftResources.loadScenes()` for
    real, which was written as `WorkbookSceneDemonstrationsJvmTest` (`campaign/src/jvmTest`)
    instead of a `commonTest`, because doing it from `commonTest` throws a `node:fs`
    `JsException` under `:campaign:wasmJsNodeTest` specifically — `readSceneResourceText`'s
    wasmJs actual resolves its file path from `import.meta.url` of *the module whose compiled
    output is currently executing* (its own KDoc says as much), which under `campaign`'s own
    compiled wasmJs test bundle points at `campaign`'s own output directory, not
    `learn:scenes`', where `workbook-latin.yaml`'s sibling scene YAML actually landed at build
    time. `ui`'s own `LineagesQuizItemsTest` (item 47, `docs/OPEN_QUESTIONS.md`) worked around a
    related but different cause (browser vs. Node target) the identical way — hand-built
    fixtures in `commonTest`, the real loader call moved to a JVM-only test — so this task
    followed that same precedent rather than inventing a third pattern. *Whoever next wants any
    cross-module real-resource-loading call to work uniformly on `wasmJs` regardless of which
    module's test bundle calls it: this and item 47 are now two independent, real data points
    that `learn:scenes`' (and by extension this module's own `CampaignResources.kt`) resource-
    reading convention is fundamentally single-module-only on that target, not a one-off bug in
    either call site.*

88. **TYPEWRIGHT_BUILD_BRIEF.md §4.3 "Margin and proof" `[CONFIRM]`'s pull-down-per-room
    interaction was deliberately not built by this task, and the underlying design question it
    raises is still genuinely open, not resolved by that omission.** This task's own brief quoted
    §4.3 in full and pointed out, correctly, that its own last sentence ("Proposed; the explorer
    does not yet show the vertical axis") means `ui/typewright-explorer.html` has no markup at
    all for pulling down to see a room's margin (its workbook task) or up to see its proof — so
    CLAUDE.md law 6 ("if a screen is missing from the explorer, stop and say so rather than
    inventing") forbids building it, and this task built only what the explorer does show for the
    workbook instead (`#s-home`'s `.taskcard`, `#s-workbook`'s own full single-task screen) via
    the real `WorkbookTask`/`WorkbookGateResult` data this module now provides. That leaves the
    real design question §4.3 raises — should a construction room ever surface its own workbook
    task inline, pulled down over the room itself, rather than only reachable through the
    separate `#s-workbook` screen this task's data now backs — entirely undecided; nothing in
    this task's own data model forecloses it (a room could look up its own task by matching
    `WorkbookTask.title`/index against the room it is showing), but nothing was designed for it
    either. *Whoever next designs a room's own screen and wants §4.3's pulled-down-margin
    interaction: it needs its own explorer mockup first (per law 6) before any code, `:campaign`'s
    data included — this task deliberately left that mockup unmade rather than inventing one.*

## P7: WorkbookScreen (`:ui` — `WorkbookScreen.kt`, `WorkbookCampaignSnapshot.kt`,
`WorkbookGateDisplay.kt`, `SeverityMark.kt`, `WorkbookReferenceProject.kt`,
`WorkbookScreenState.kt`; `:campaign` — `WorkbookProgress.kt`; `TypewrightApp.kt`)

89. **The corner taskcard is this build's own disclosed substitute for brief section 9's "a
    one-line next-task on the specimen," not an attempt to build the specimen screen itself — and
    the substitute's first render needed a width cap found and fixed by screenshot.** Per this
    task's own instructions, item 82 above (and item 56 it points back to) already discloses that
    the *full* cross-station navigation shell — a real Home/specimen screen included — is
    separate, later, undesigned-by-the-explorer work; with no specimen screen to put a taskcard
    on, `TypewrightApp`'s own bottom-end corner (the one corner P6's own `LearnEntryButton` left
    free, that task's own KDoc) now stacks a second small ink block above it,
    `WorkbookEntryButton`, showing the real current/next task's own label (`nextTaskLabel`,
    `#s-home`'s own `.taskcard` copy shape) rather than a generic "Workbook" word. Confirmed by
    screenshot, not by eye alone (this task's own instruction): the first unconstrained render
    (`nextTaskLabel` returning a real, longer title like `"Task 10 · Extend a script (optional)"`)
    spanned nearly the full phone width, reading as a banner rather than a corner tab —
    `WorkbookEntryButton`'s own `widthIn(max = 190.dp)` plus `TextOverflow.Ellipsis` is the fix,
    and `TypewrightAppEntryPointScreenshotTest.closedStateShowsBothEntryButtonsStackedWithoutOverlapping`
    now samples real pixels (measured with a throwaway vertical pixel-transition probe against a
    real render, not guessed) proving the two stacked blocks do not collide. `TypewrightAppNavState`
    grew `showWorkbook`/`openWorkbook`/`closeWorkbook`, mutually exclusive with `showLearn` (both
    are full-screen, texture-occluding overlays over the same `TypewrightSheet`) — the same plain,
    directly-testable state shape `showLearn` already established, extended rather than a second
    sibling class, since both flags are genuinely one "which full-screen overlay, if any" concern.

90. **A real, screenshot-caught layout bug in the Demonstration section, found and fixed the same
    way this build's own history keeps finding these: render the real data, not a hand-picked
    example, and look.** `WorkbookDemonstrationSection`'s first version rendered every
    `Demonstration.StatDemonstration.label` at `#s-workbook`'s own `.demo .dl` treatment — 40sp, in
    the user's own font, next to the stats in an unweighted `Row` — because task 4's own real label
    is exactly that shape (`"noHO"`, the explorer's only worked example). Every other real label in
    `workbook-latin.yaml` is a descriptive phrase, not a short sample — task 6's own real
    `"on-curve range across 13 open fonts (docs/RESEARCH_font_quality.md)"`, 68 characters — and
    forcing one of those into the same unconstrained 40sp hero slot starved its own sibling stats
    column of width, wrapping every character of every stat line onto its own line (confirmed by
    screenshot, `ui/build/screenshots/workbook-screen-task6-pass-warn-mix.png` before the fix; the
    same `Row`-sibling-width failure class items 74/81 above already name, a third real instance of
    it in this codebase now). Fixed two ways together, not one: `isHeroStatLabel(label)`
    (`WorkbookGateDisplay.kt`, a length/shape heuristic — `<= 10` characters and no space — not a
    task-index special case, so it keeps working if the YAML content changes) decides whether a
    label reads as a real hero sample at all, and the stats column now always carries
    `Modifier.weight(1f)` regardless, so it can never again collapse to near-zero even if a future
    label sits right at that heuristic's own boundary. A second, smaller instance of the same class
    of bug (bottom-aligning a wrapped multi-line caption against a single-line hero in a `Row`, for
    `Demonstration.SceneDemonstration`) was found and fixed the same session, before it ever reached
    a committed screenshot — that section is a plain top-aligned `Column` now, not a `Row`.

91. **`campaign`'s own real progress logic makes task 1 — not the mockup's "task 4" — this build's
    real current/next task on the Hyle Deco reference project, and that is a genuine finding about
    the data model, not a workaround.** `WorkbookProgress.kt`'s `campaignProgress` marks a task
    [DONE] only when its real gate `isFullyPassing()` (implemented, at least one check, every check
    PASS); the first non-DONE task in index order is CURRENT, and everything after it is TODO
    regardless of its own gate (a later task's gate happening to pass alone, e.g. task 12's own
    pure string generation almost always does, must never let it jump the queue — this exact bug
    was caught by `WorkbookProgressTest.firstNonDoneTaskIsCurrentAndEveryTaskAfterItIsTodoRegardlessOfItsOwnGate`
    on the first implementation, which checked `isFullyPassing()` before the "already blocked" flag
    instead of after, and was fixed before this task was reported done). Run for real
    (`loadWorkbookCampaignSnapshot`, JVM/`desktopTest`, a throwaway probe first, per this task's own
    instruction not to guess): task 1's gate is NOT_IMPLEMENTED and stays task 1 by definition —
    the campaign module has **no persisted "the learner manually confirmed this judgment-call task
    is done" flag anywhere** (`WorkbookProgress.kt`'s own KDoc says so plainly), so on *any* project,
    not just this one, tasks 1/2/7/8/10 can never read DONE and task 1 is always CURRENT until that
    gap is closed — a real, disclosed limitation of the data model this task built on top of, not
    fixed here (out of this task's own scope: no new state was added to `WorkbookTask`/
    `WorkbookGateSpec`). Task 4's own gate needs a style-class key `WorkbookGateSpec` itself does not
    carry (item 18's own closing line: "that remains `campaign`'s call"); `runWorkbookGate` decides
    it once, `DEFAULT_TASK4_STYLE_KEY = "sans-geometric"`, matching `HyleDecoTask4GateTest`'s own
    already-established choice, not a new one. Task 11's real gate (`WorkbookGates.task11Test`,
    `suspend`, needs a real `LayerOneChecker`) is deliberately *not* called by `runWorkbookGate` —
    there is no compiled-font pipeline reachable from a `UfoProject` alone (item 86's own gap, the
    same shape the other direction), so every real call would return the identical "no compiled
    font to check yet" result a `null` compiled input already gives; `runWorkbookGate` returns that
    exact honest result directly and synchronously instead, documented as a deliberate
    simplification in `WorkbookProgress.kt`'s own KDoc, not a silent shortcut.

92. **Loading the real campaign snapshot in `ui` inherits item 18's already-disclosed wasmJs-browser
    gap, one target wider than P1-corpus-loader's own prompt covered, and is honestly disclosed
    rather than worked around.** `WorkbookCampaignSnapshot.Loaded` needs `qa:corpus`'s
    `NodeEconomyCorpus.load()`, whose `wasmJs` actual reads its JSON pack with Node's own `fs`
    (`CorpusResources.wasmJs.kt`) — real and tested under `wasmJsNodeTest`, but `ui`'s own `wasmJs`
    target is a real **browser** (`ui/build.gradle.kts`'s own comment, confirmed again while
    writing this task), where `process` is undefined. `loadWorkbookCampaignSnapshot` therefore
    `runCatching`s the whole load and returns an honest `WorkbookCampaignSnapshot.Unavailable`
    instead of crashing — the identical "not available on this target" convention
    `com.asoc.typewright.ui.learn.loadDefaultOverlayLayers` already established for a *different*
    real gap on this exact same target (missing Learn-face bytes) — and `WorkbookScreen`/
    `WorkbookEntryButton` both render an honest fallback for it (`WorkbookUnavailableBody`;
    `nextTaskLabel`'s own plain `"Workbook"` word), screenshot-tested
    (`rendersAnHonestUnavailableBodyWhenTheSnapshotFailedToLoad`). Not fixed here (this task's own
    scope is the screen, not `qa:corpus`'s wasmJs resource loader) — a real, disclosed gap for
    whoever next builds `app-web`'s own real Kotlin/Wasm browser target (P9), the same "browser
    half is not [solved]" item 18 already named, now confirmed to reach `ui` itself, not only a
    hypothetical future caller. **A second, smaller, disclosed inefficiency, not a correctness
    gap:** `TypewrightApp`'s own `WorkbookEntryButton` and `WorkbookScreen`'s own
    `rememberWorkbookScreenUiState` each independently call `loadWorkbookCampaignSnapshot()` —
    two real font parses and corpus loads per app session, not a shared instance — because
    `WorkbookScreen`'s own signature is fixed to `(modifier, texture, onBack, uiState)` (this
    task's own instruction, matching `LearnScreen`'s established shape) with no fifth parameter to
    thread a pre-loaded snapshot through; both loads are deterministic and always agree, so this
    is a real, small perf cost, not a data-consistency risk (`TypewrightApp.kt`'s own KDoc on
    `WorkbookEntryButton`).

93. **Three more deliberate, disclosed calls made building this screen, none of them shown by the
    explorer's own single worked example (task 4) and none of them guessed at silently.**
    - **The Reflection section's text entry is real and reuses `com.asoc.typewright.ui.learn.
      ScrapbookPin`/`ScrapbookPinKind.NOTE`/`stablePinRotationDegrees` — the real scrapbook data
      model and its real stable-rotation function — but appends to `WorkbookScreen`'s own
      `remember`ed list, not `ScrapbookTab`'s.** There is no current-project flow or shared
      scrapbook state anywhere in `ui` yet (the same gap `SampleScrapbook`'s own KDoc already
      discloses); a reflection saved on this screen and a "+ note" pin added on the Scrapbook tab
      are two different `remember` scopes today, both real, both in-memory-only, neither one
      shared with the other. Whoever next gives this app a real current-project/scrapbook
      repository is the one who can make these the same list.
    - **`WorkbookGateSection`'s "Trace again with Fit ›" action is reproduced on task 4's own
      screen only, and is unwired.** `#s-workbook`'s own markup shows exactly one task and exactly
      one Gate action button; nothing in the explorer says whether that action is task-4-specific
      (a re-trace makes sense for a node-economy miss) or a generic per-gate action, so per law 6
      this file does not guess past the one real, literal case — and it does nothing when tapped,
      since no real Trace screen composable exists anywhere in `ui` yet (confirmed by reading
      `ui/src/commonMain` in full before writing this file), the same disclosed-not-fake convention
      `LearnScreen`'s own commands button already established.
    - **Task 12's real ship gate needs a `designer` name `campaign`'s data model has no source for**
      (`ShipMetadata` carries `family`/`year`/`gitUrl` real values already quoted by task 12's own
      YAML demonstration, `"Hyle Deco"`/`2026`/`"https://github.com/mbaliga/hyle-deco"`, but no
      task or brief text ever names a designer). `WorkbookCampaignSnapshot.kt` uses `"Madhav"` —
      the one real person named throughout this build's own docs as its product owner
      (`TYPEWRIGHT_BUILD_BRIEF.md` section 16) — a reasonable, disclosed stand-in for a demo
      project's own placeholder field, not a measured fact CLAUDE.md law 5 binds.

94. **`LearnScreenScreenshotTest`'s own `width = 420, density = 2f` (210 dp) screenshot convention
    is narrower than `ui/typewright-explorer.html`'s own real phone frame
    (`.tw.phone{width:400px}`, line 70) — a real discrepancy found while choosing this screen's own
    screenshot width, not fixed on `LearnScreen`'s own tests (out of this task's scope).**
    `WorkbookScreenScreenshotTest` captures at `width = 800, density = 2f` (400 dp) instead, once a
    210 dp canvas was confirmed by screenshot to over-truncate this screen's own longer real header
    text (`"Workbook · Latin"`) and task titles far more than the real explorer frame would ever
    force. `TypewrightAppEntryPointScreenshotTest`'s own existing `phoneWidthPx = 720, density = 2f`
    (360 dp, P6's own choice, left unchanged here) sits between the two and was not the cause of
    any truncation this task found. *Whoever next revisits `LearnScreenScreenshotTest`/
    `ScrapbookTabScreenshotTest`/`OverlayTabScreenshotTest`/`LensTabHyleDecoTest`'s own shared
    210 dp convention: this file's own finding is a data point that it reads narrower than the
    explorer's own real frame, not a claim that those specific screens' own content is broken by
    it (their own content was not re-audited here).*

## P7: independent verification (campaign engine + WorkbookScreen UI)

95. **A real, previously-uncaught bug: `ui`'s own commonTest fixture `WorkbookScreenStateTest.kt`'s
    `fixtureSnapshot()` claimed to be "portable across every target" while actually calling
    `WorkbookLatinContent.load()`, which is not -- caught only by running `:ui:wasmJsBrowserTest`
    fresh, which neither prior P7 stage ran (it is not in `.github/workflows/ci.yml`; the "web" job
    there runs `:app-web:wasmJsBrowserTest`, never `:ui:`'s own) and item 89-94's own report never
    mentions.** `WorkbookLatinContent.load()` calls `campaign`'s `readCampaignResourceText`, whose
    wasmJs actual (`CampaignResources.wasmJs.kt`) is, by its own KDoc, "Written for `wasmJs {
    nodejs() }` only... not a browser" -- the same class of gap item 92 already discloses for
    `qa:corpus`'s loader on this exact target, and structurally the same cross-module-wasmJs finding
    item 87 already names, one level up (`ui` calling `campaign`'s resource reader from `ui`'s own
    compiled wasmJs *browser* bundle, not `campaign`'s own Node one). `:ui:wasmJsBrowserTest
    --rerun-tasks`, run fresh for this verify pass, failed 5 of 199 tests --
    `WorkbookScreenUiStateTest` (3) and `NextTaskLabelTest` (2) -- every one the identical
    `ReferenceError: process is not defined` inside `readNodeFileNextToThisModule`, reached through
    `WorkbookLatinContent.load()` called from this test file's own `fixtureSnapshot()`. Fixed by
    making `fixtureSnapshot()` genuinely synthetic (`syntheticTask(index)`, a hand-built
    `WorkbookTask` per task, no resource read at all) rather than touching `campaign`'s own loader
    (out of scope, and an honest, already-disclosed gap, not a bug in the loader itself). Confirmed:
    `:ui:wasmJsBrowserTest --rerun-tasks` now reports 199/199, and a full combined repo-wide re-run
    afterward (`spotlessCheck jvmTest wasmJsNodeTest :ui:desktopTest :ui:testAndroidHostTest
    :ui:wasmJsBrowserTest --rerun-tasks`) is green: 820/820 jvmTest, 794/794 wasmJsNodeTest, 290/290
    `:ui:desktopTest`, 198/198 `:ui:testAndroidHostTest`, 199/199 `:ui:wasmJsBrowserTest`. A second,
    smaller inaccuracy fixed the same pass: `campaign/README.md`'s own "Tests" section named only
    four `commonTest` classes and claimed "29 tests," omitting `WorkbookProgressTest` (15 tests)
    from the list entirely -- the real, measured total is 44 `commonTest` tests on both `jvm` and
    `wasmJs` (Node); corrected in place, no test content changed. Task 4's own real per-glyph counts
    were independently re-derived in this pass too (a throwaway `jvmTest` probe reading
    `fonts/HyleDeco-Regular.ttf` via `core-font`'s real `readSfntFont`, deleted after use, the
    established pattern): n 44 / o 80 / H 1,252 / O 83 on-curve, 0 off-curve each, matching
    `HyleDecoTask4GateTest`'s own assertions exactly and confirming `WorkbookScreen`'s real rendered
    box ranges (`box 17 – 22` / `23 – 24` / `12 – 18` / `24 – 29`, from `data/node-economy-latin.
    json`'s real `sans-geometric` quartiles) are genuinely computed, not the explorer's own
    `#s-workbook` mockup box text (`12 – 14` / `10 – 10` / `12 – 13` / `10 – 12`) -- the two box sets
    disagree everywhere despite the four on-curve *value* numbers coincidentally matching the
    mockup's own (both being the same real shipped-font measurement to begin with).

## P8: Devanagari script data

96. **`scripts/templates/hyle-all-templates.zip`'s own `svg/Devanagari/` folder genuinely holds 68
    files (matching its own `HOW_TO_USE.md` count) but only 66 distinct glyphs -- two files are
    byte-identical duplicates of an earlier file in the same folder, confirmed by content hash, not
    guessed from filenames alone.** `011_DEVANAGARI_LETTER_A.svg` is an exact md5 duplicate of
    `000_DEVANAGARI_LETTER_A.svg` (both `3cff3c40703916671d9bd5e9abfa82cd`, 2,552 bytes), and
    `056_DEVANAGARI_SIGN_ANUSVARA.svg` is an exact md5 duplicate of `012_DEVANAGARI_SIGN_ANUSVARA.
    svg` (both `c2dd7a0f426a203d8a82980ec81e7f08`, 1,834 bytes). Not a defect in the read-only
    `ScriptProfile.kt` shared model (its own `GlyphInventory`/`TemplateSheet` split already has room
    for this: an inventory of distinct glyphs plus a sheet of every real template file), so nothing
    there needed changing -- logged here instead per this task's own "when unsure ... put open
    questions in docs/OPEN_QUESTIONS.md" instruction, since a future template-pack maintainer should
    know this is real and confirmed, not a transcription slip. Handled honestly rather than
    silently: `com.asoc.typewright.scripts.devanagari.DevanagariGlyphInventory` lists 66 distinct
    `GlyphSpec`s (one per real Unicode name the folder covers -- never two for the same glyph), while
    `com.asoc.typewright.scripts.devanagari.DevanagariTemplateSheet` lists all 68 real
    `TemplateSheetEntry`s, with both duplicate files pointed at the one glyph they actually draw
    (`a-deva` twice, `anusvara-deva` twice). Reproducible: `python3
    data/scripts/build_devanagari_template_manifest.py` regenerates the checked-in
    `scripts/templates/devanagari-manifest.json` from the zip directly (`template_count: 68`,
    `distinct_glyph_count: 66`, a `duplicate_pairs` list naming both pairs by index) -- both Kotlin
    files were cross-checked against that generated manifest programmatically before being written
    up, not hand-typed from memory. Whether the zip's own `svg/Devanagari/` folder should eventually
    be corrected upstream (e.g. `011` and `056` replaced with two of the real letters the folder is
    currently missing, or left as deliberate spare/practice capture slots) is outside this task's
    scope and is the open question proper.

97. **`:scripts:compileKotlinJvm`, `:scripts:compileKotlinWasmJs` and `:scripts:spotlessKotlinCheck`
    are all currently failing on the real, shared working tree -- entirely because of a concurrent
    sibling script agent's own `scripts/src/commonMain/kotlin/com/asoc/typewright/scripts/kana/*`
    files, not because of anything added by this Devanagari task.** The real compiler error, run
    fresh via the exact commands this task was given (`:scripts:spotlessCheck :scripts:jvmTest
    :scripts:wasmJsNodeTest`, env `ANDROID_HOME=/opt/android-sdk
    CHROME_BIN=/opt/pw-browsers/chromium-1194/chrome-linux/chrome LANG=en_US.UTF-8`): `'public'
    property exposes its 'internal' type argument 'KanaTemplate'.` at
    `scripts/.../kana/HiraganaGlyphs.kt:23` and `KatakanaGlyphs.kt:24` (Kotlin's `EXPOSED_PROPERTY_
    TYPE`, both targets); `:scripts:spotlessKotlinCheck` separately reports real ktlint format
    violations, all inside `scripts/.../kana/*` (`KanaControlCharacters.kt`, `KanaFeaturePlan.kt`,
    `KanaGlyphNaming.kt`, `KanaScriptProfiles.kt`, `HiraganaGlyphs.kt`, `KatakanaGlyphs.kt`,
    `KanaMetrics.kt` and two `commonTest/.../kana/*` files). This task's own instructions are
    explicit that per-script work must live in its own package "so your files never overlap another
    script agent's files running in parallel with you right now" -- correctly read as: never edit
    another script's files to fix them either, even to unblock a shared-module build, since that
    agent may be mid-edit on them right now. Not fixed here; not worked around by touching
    `kana/*`. Instead, verified in isolation, honestly: the whole repository tree was copied to a
    scratch directory (`tar`, excluding `build/` and `.gradle/`), the `kana` package was moved aside
    within that scratch copy only (never in the real tree), and `:scripts:spotlessCheck
    :scripts:jvmTest :scripts:wasmJsNodeTest` was run fresh there -- `BUILD SUCCESSFUL`, zero
    ktlint violations outside `kana/*`, and all 51 real Devanagari `commonTest` tests (9
    `DevanagariControlCharactersTest` + 9 `DevanagariFeaturePlanTest` + 11
    `DevanagariGlyphInventoryTest` + 9 `DevanagariMetricsTest` + 4 `DevanagariScriptProfileTest` + 9
    `DevanagariTemplateSheetTest`) passing on both `jvm` and `wasmJs` (Node). Two of this task's own
    `spotlessKotlinCheck` violations, found in that same run, were real and were fixed directly on
    the real tree (not worked around): a joinable one-line `fun build()` in
    `DevanagariControlCharacters.kt`, and two ktlint-preferred multi-line method-chain reformats in
    `DevanagariMetricsTest.kt` and `DevanagariTemplateSheetTest.kt` -- confirmed against the scratch
    copy's own `spotlessApply` output, never guessed. Whoever next touches `scripts/.../kana/*`
    (or the orchestrator, before it commits) needs to fix `HiraganaGlyphs.kt`'s and
    `KatakanaGlyphs.kt`'s own `KanaTemplate` visibility (make the type `internal`-exposing property
    itself non-public, or make `KanaTemplate` public) and run `spotlessApply` scoped to `kana/*`
    only before `:scripts:jvmTest`/`:scripts:wasmJsNodeTest` will pass on the real, combined tree.

## P8: Kana (Hiragana + Katakana) script data

98. **Item 97's own finding is now fixed, on the real tree, by this task (the `kana/*` author it
    named).** `HIRAGANA_TEMPLATES`/`KATAKANA_TEMPLATES` (in `HiraganaGlyphs.kt`/`KatakanaGlyphs.kt`)
    are now `internal` rather than exposing the `internal` `KanaTemplate` type through a public
    property (Kotlin's `EXPOSED_PROPERTY_TYPE`); the public surface of the `kana` package is
    `GlyphInventory`/`TemplateSheet`/`ScriptMetricSystem`/`ControlCharacterSet`/
    `FeatureGenerationPlan`/`ScriptProfile` instances, per the shared `ScriptProfile.kt` model, not
    the intermediate `KanaTemplate` staging type. The real ktlint violations item 97 also named
    (`KanaControlCharacters.kt`, `KanaFeaturePlan.kt`, `KanaGlyphNaming.kt`, `KanaScriptProfiles.kt`,
    `HiraganaGlyphs.kt`, `KatakanaGlyphs.kt`, `KanaMetrics.kt` and two `commonTest/.../kana/*` files)
    are fixed too, applied with `./gradlew :scripts:spotlessApply -PspotlessFiles="scripts/src/.*/
    kana/.*"` -- the Spotless Gradle plugin's own file-scoping property -- specifically so this
    fix never touched `devanagari/*` (item 97's own scratch-copy verification method was more
    conservative than necessary; `-PspotlessFiles` reformats only matching real files in place, and
    is used here in both directions: never applied to a sibling's package, confirmed by this
    session's own diffs touching only `kana/*`). Run fresh, on the real combined tree, after this
    fix: `./gradlew :scripts:spotlessCheck :scripts:jvmTest :scripts:wasmJsNodeTest --rerun-tasks`
    (env `ANDROID_HOME=/opt/android-sdk CHROME_BIN=/opt/pw-browsers/chromium-1194/chrome-linux/
    chrome LANG=en_US.UTF-8`) -- `BUILD SUCCESSFUL`, 114/114 on both `jvmTest` and `wasmJsNodeTest`
    (Devanagari's 51 + this task's own 59 + the pre-existing `ScriptProfileTest`/`ScriptsModuleTest`
    4), zero ktlint violations anywhere in `:scripts`.

99. **The real template counts this task confirmed itself, not assumed from the prompt: 55
    Hiragana, 57 Katakana** (`scripts/templates/hyle-all-templates.zip`'s `svg/Hiragana`/
    `svg/Katakana` folders, matching the zip's own `HOW_TO_USE.md` table exactly), unlike
    Devanagari's folder (item 96), neither kana folder has a single duplicate file -- 112 distinct
    real templates, 112 distinct codepoints, confirmed by `scripts/templates/
    generate_kana_manifest.py`, which refuses to write a manifest at all if any folder's count
    disagrees with `HOW_TO_USE.md` or if a duplicate codepoint or glyph name appears. That generator
    is scoped to kana only (`kana-manifest.json`, not the shared `manifest.json` name the original
    prompt suggested) specifically to avoid the collision risk of several concurrent script agents
    each writing the same shared manifest file at once -- item 96's `devanagari-manifest.json`
    independently made the identical per-script naming call, confirming this was the right read of
    "your own judgement on the exact mechanism."

100. **The real, measured template guides give kana a genuine "virtual body" / optical-centre metric
    system, not a Latin baseline+x-height one, and it is honestly NOT modelled as script-wide
    fixed constants.** Every one of the 112 real templates draws identical guides: body top 880,
    body bottom -120 (a 1000-unit-tall body, matching this build's own 1000 UPM default), virtual-
    body centre 380 (the exact midpoint, standing in for Latin's x-height/cap-height pair), baseline
    0, and a uniform 720-unit advance width -- matching the zip's own `HOW_TO_USE.md` prose exactly
    ("a near-square body from about -120 to 880"). Only `baseline` is marked `fixed = true`
    (`ScriptMetricLine`'s own structural-constant sense) in `KanaMetrics.kt`: `docs/
    RESEARCH_font_quality.md`'s kana subsection states plainly that how far a kana letter face sits
    inside its virtual body "varies by design and for which no percentage is published", so the body
    top/bottom/centre values are recorded as this one template pack's own real, measured design
    choice, not invented as a universal kana constant the way Devanagari's headline and baseline
    (item's own two genuinely fixed levels) are.

101. **Neither real source gives kana a starting-sequence control-character set, and none was
    invented to match Latin's shape -- `HIRAGANA_CONTROL_CHARACTERS`/`KATAKANA_CONTROL_CHARACTERS`
    are honestly empty `ControlCharacterSet`s, not placeholders.** Both `docs/
    RESEARCH_font_quality.md`'s kana subsection ("No 'draw these kana first' list was found") and
    `docs/LESSONS_SCAFFOLD.md` section 5's kana bullet were read in full for this; neither names one,
    unlike Latin (n/o then H/O, "every source agrees") or Devanagari (the real पाव / किमीनुफू /
    भरसगदह progression from Design With FontForge, already in `docs/DevanagariControlCharacters.kt`
    per item 96's sibling task). `KanaControlCharactersTest.bothControlCharacterSetsAreHonestlyEmpty
    BecauseNoRealSourceGivesAKanaStartingSequence` pins this at empty so a future edit that quietly
    adds an invented sequence fails a test rather than shipping unnoticed. Whether the product owner
    wants a first-glyph sequence chosen by design judgement (not by a written source) for the app's
    own kana workbook is a real product decision this task does not make. *Madhav, if a kana
    workbook path is scheduled before a real source turns up.*

102. **The real feature-generation plan is deliberately thin (one GSUB stage, one GPOS feature), and
    two gaps are disclosed rather than smoothed over.** `HIRAGANA_FEATURE_PLAN`/
    `KATAKANA_FEATURE_PLAN` carry `gsubStagesInOrder = ["ccmp"]` (composing a base kana +
    combining U+3099/U+309A dakuten/handakuten sequence into its precomposed glyph) and
    `gposFeatures = ["mark"]` (mark-to-base anchor positioning for a standalone mark at the base's
    upper right, the research's own "Dakuten ... and handakuten ... sit at the upper right" using
    the identical anchor mechanism the research's Diacritics section already gives Latin accents) --
    no joining, no conjuncts, contrasting item 96/97's Devanagari (nine GSUB stages) and the
    Arabic Naskh profile (eight GSUB stages, per this task's own briefing). Two things disclosed
    honestly rather than invented or silently dropped: **(a)** the real zip has no dedicated
    dakuten/handakuten glyph template in `svg/Hiragana` or `svg/Katakana` (confirmed by
    `generate_kana_manifest.py`'s full listing), so this plan's own one real stage has no glyph to
    compose with yet -- a real gap in the template asset, not this task's data; **(b)** `vert`
    (vertical writing) is real and sourced (research: "a planned `vert` set") but is NOT included as
    an active stage, since the real 112 templates carry only horizontal guides -- named in the
    plan's own `notes` field as a real, future item, the same "name it, don't silently omit it,
    don't claim it's built" treatment this task's briefing asks for Nastaliq in the Arabic profile.
    `kern` is also excluded, as a reasoned inference (not a cited fact) from the templates' own
    uniform 720-unit advance width implying a monospaced grid, where kerning is not the norm.

103. **`scripts/templates/kana-manifest.json`'s own generator (`generate_kana_manifest.py`) fails
    loudly rather than silently on three real conditions, verified by actually running it against
    the real zip, not merely reading the code:** a folder's real template count disagreeing with the
    zip's own `HOW_TO_USE.md`; any two templates in the same folder having non-identical guide
    metrics (would break the "one shared metric system per script" assumption `KanaMetrics.kt` and
    this task's own briefing both make); and a duplicate codepoint or duplicate derived glyph name
    within a folder. None of the three fired on the real asset (confirmed: guide metrics are
    byte-identical across all 112 files, no duplicate codepoints, no duplicate derived names), so
    this generator's defensive paths are themselves untested against a real failing input -- a real,
    disclosed gap in the generator's own test coverage (it is a one-time build-time script, not a
    `core-*` module, so it carries no `commonTest` unit tests of its own per CLAUDE.md's convention,
    which binds `core-*` public functions specifically); anyone editing it should hand-verify a
    deliberately-broken copy of the zip still trips each check before trusting a future edit to it.

## P8: Arabic Naskh script data

104. **Which of Arabic Naskh's 39 real base letters are Unicode dual-joining versus right-joining
    versus non-joining -- the fact this task's own instruction turns on ("for each dual-joining
    letter, also generate its three positional variants") -- has no local source to check it
    against: this repository carries no copy of Unicode's own `ArabicShaping.txt` (the file that
    defines `Joining_Type`), and Python's stdlib `unicodedata` module (already used, and re-used
    here, to verify every letter's and digit's real codepoint against
    `scripts/templates/hyle-all-templates.zip`'s own embedded labels) does not expose that
    property either.** `scripts/templates/generate_naskh_manifest.py`'s own
    `JOINING_TYPE_BY_CODEPOINT` table is therefore a hand-entered classification, not a mechanical
    derivation -- disclosed as such in that generator's own docstring, and cross-checked once, at
    the time this task was done, against the real `ArabicShaping.txt` (Unicode 18.0.0,
    https://www.unicode.org/Public/UCD/latest/ucd/ArabicShaping.txt, fetched live via this
    session's own web-fetch tool) for exactly these 39 codepoints -- not guessed from memory alone,
    but also not something a future re-run of the generator re-verifies on its own. The result: 28
    dual-joining letters (all four positional forms generated), 10 right-joining letters (alef,
    dal, ddal, thal, reh, rreh, zain, jeh, waw, yehBarree -- isolated form only, per this task's own
    literal instruction, which scopes positional-form generation to dual-joining letters and says
    nothing about adding a bare `.fina` for right-joining ones), and 1 non-joining letter (hamza,
    isolated form only). Anyone revisiting `ArabicJoiningType`'s own per-letter assignments in
    `ArabicLetters.kt` or `generate_naskh_manifest.py`'s own table should re-fetch the real
    `ArabicShaping.txt` and diff it against the 39-entry table directly, rather than trusting this
    write-up's restatement of it.

105. **`scripts/templates/hyle-all-templates.zip`'s own `svg/Naskh/` folder genuinely holds 49
    files with four identical real vertical-metric guide values baked into every one of them
    (`ascender 720`, `tooth height 300`, `baseline 0`, `descender -360`) -- confirmed by
    `generate_naskh_manifest.py` actually parsing all 49 SVGs' own embedded guide `<text>` labels
    and raising if any of the 49 disagree (none did) -- but the template SVGs' own raw guide labels
    read the generic, Latin-shaped words "ascender"/"descender", not the research's own real
    Arabic-vocabulary terms "sky"/"earth".** Not a defect in the read-only `ScriptProfile.kt`
    shared model (nothing there needed changing), and not treated as license to smuggle a Latin
    metric name into `ArabicMetrics.kt` under a different label, which this task's own briefing
    explicitly warns against: `ArabicMetrics`'s two matching [`ScriptMetricLine`]s are named
    `"sky"` and `"earth"` (the research's own quoted terms), each with its own note stating plainly
    that the template pack's own raw label text differs and why this build does not reuse it.
    `eyeHeight` and `loopHeight`, the other two of Arabic's real four x-height replacements named
    in `docs/RESEARCH_font_quality.md` ("tooth-, loop- and eye-heights"), have no numeric value in
    either the real template pack or the general research at all (the same section's own closing
    sentence: "No source gave numeric tooth/loop/descender ratios in dot units") and are recorded
    as this build's own placeholder `[CONFIRM]` values (260 and 180), following the identical
    disclosure pattern item 96's sibling Devanagari task used for its own five unsourced levels.
    Separately: Nastaliq's own real template folder (`svg/Nastaliq`, 49 files, confirmed by direct
    zip listing) was not opened or read beyond confirming its file count for
    `NastaliqOutOfScope.REAL_TEMPLATE_COUNT_IN_ZIP` -- this task's own scope is Naskh only, and
    `WritingScript` (read-only, shared) carries no `NASTALIQ` entry to build a profile against;
    `NastaliqOutOfScope.kt`'s own disclosure states the concrete OpenType-sloping-baseline reason
    from `docs/RESEARCH_font_quality.md` directly, per this task's own instruction.

## P8: Shaping preview — desktop and Android real implementations (`shape-preview`)

106. **Cluster data is genuinely available on desktop, genuinely not on Android — checked
    empirically, not assumed from the brief, and the v1 no-JNI-HarfBuzz decision (`Shaper.kt`'s
    own top KDoc: "not through JNI, in v1"; `docs/DECISIONS.md` D14) is not overridden for
    either.** Desktop: `org.jetbrains.skia.shaper.RunHandler.commitRun`'s `clusters: IntArray`
    parameter is real per-glyph UTF-16 cluster data (Skiko 0.150.1's own KDoc on that parameter:
    "clusters[i] is an utf-16 offset starting run which produced glyphs[i]"), and `SkikoShaper`
    now wires it into `ShapedRun.clusters` for real — confirmed against real output, not just the
    doc comment: `SkikoShaperTest.shapesRealDevanagariConjunctAndFindsRealClusterData` shapes a
    real three-codepoint Devanagari conjunct (क् + ष, U+0915 U+094D U+0937) with
    `fonts/NotoSansDevanagari-Regular.ttf` and gets exactly one glyph (id 90) and one cluster
    spanning the whole `[0, 3)` text range — genuine ligation, genuinely captured. Android:
    `android.graphics.text.PositionedGlyphs`' full API surface (`getGlyphId`, `getGlyphX`/
    `getGlyphY`, `getFont`, `getAdvance`, and API 35's `getFakeBold`/`getFakeItalic`/
    `getWeightOverride`/`getItalicOverride`) has no `getCluster`/`getClusterStart`/text-range
    method at all, on any API level through `android-37` (`/opt/android-sdk`, this task's own
    compile target) — verified by listing the class's real members with `javap`, not by reading
    only the one method (`shapeTextRun`) the brief named. `AndroidTextRunShaper.shape()` therefore
    always returns `clusters = null`, exactly as `Shaper.kt`'s own KDoc says a stack should when
    it withholds the data, and matching `docs/ARCHITECTURE_REVIEW.md` section 4.3's earlier
    finding. Given this, and that the real gap is narrow (`TextRunShaper` still gives real glyph
    ids and real positions; only the text-range-per-glyph map is missing, and the interface
    already models that as nullable), a new JNI HarfBuzz binding was not added: it would need
    NDK-built native code and per-ABI packaging this build does not already pay for anywhere
    (`docs/ARCHITECTURE_REVIEW.md` section 4.3's own "Risk"/"Recommendation" already name direct
    HarfBuzz-via-JNI as future, "explain this conjunct"-tier work, not a v1 requirement), and no
    existing pure-Kotlin-Multiplatform HarfBuzz binding with no native-compile step was found to
    propose instead (a search would need network access to a Maven/npm registry this task did not
    spend on a negative result). Recorded rather than silently left null: whoever builds Android's
    glyph-level conjunct explanation later (the "lookup trace" work `docs/ARCHITECTURE_REVIEW.md`
    section 4.3 already scopes past v1) will need that binding then, not a cluster map from this
    API, because this API genuinely has none to give.

107. **Two more Android gaps disclosed rather than smoothed over, both unverifiable on-device here
    (CLAUDE.md law 4) so recorded as known, not fixed.** (a) `ShapeRequest.script` cannot be
    forced through `android.graphics.text.TextRunShaper.shapeTextRun` — it takes no script
    parameter, unlike Skiko's desktop path (`SkikoShaper` uses
    `org.jetbrains.skia.shaper.TrivialScriptRunIterator` when `request.script` is non-null); on
    Android, Minikin's own script detection on the text is all there is, so `AndroidTextRunShaper`
    silently drops that field rather than pretending to honour it, and `AndroidTextRunShaper`'s
    own KDoc says so. (b) `Typeface.CustomFallbackBuilder`'s system fallback (`ui/src/androidMain/
    .../LearnFaceFonts.android.kt`'s own KDoc already names this: "processed only when no matching
    font is found" and "cannot be switched off") means a character the compiled preview font does
    not cover could render silently from a system font on-device, unlike desktop's `SkikoShaper`
    (which is deliberately given a `null` `FontMgr`, `SkFontMgr::RefEmpty()` inside Skia, so a
    missing glyph renders `.notdef` there instead) — `docs/ARCHITECTURE_REVIEW.md` section 4.3
    already found this ("Check `getFont(i)` every time"); this task did not add per-glyph
    `getFont(i)` fallback-detection logic on top of it, because that logic cannot be exercised or
    checked without a device or emulator here, and untested logic guarding exactly the thing law 4
    says must not be faked is worse than an honest, named gap. Both belong to whoever next works
    on the Android half of this module with a real device in hand.

## P8: Node-economy corpus — Devanagari and kana (`data/scripts/build_script_node_economy_corpus.py`, `qa:corpus`)

108. **Extended the node-economy corpus to Devanagari and kana with real google/fonts data, and
    deliberately used a different, coarser style-class taxonomy than Latin's ten classes,
    because google/fonts' own catalog does not support Latin's taxonomy for these scripts.**
    `data/scripts/build_script_node_economy_corpus.py --script {devanagari,kana}` is a sibling
    to `build_node_economy_corpus.py`, reusing its counting rule (`_count_simple_contours`,
    `count_points`, `process`) and its family-selection rules (rank by `/Quality/Drawing` from
    `data/families.csv`, tie-break reverse-alphabetical family name, dedupe to one face per
    superfamily) verbatim. What differs: (a) the glyph set is extracted at run time from this
    build's own already-committed inventories — `scripts/.../devanagari/
    DevanagariGlyphInventory.kt` (66 glyphs) and `.../kana/{Hiragana,Katakana}Glyphs.kt` (55 +
    57 = 112 glyphs, one pack covering both kana scripts since no candidate family in either
    corpus supports one without the other) — via a regex over the real `.kt` source, not
    retyped by hand; (b) style classes are Google Fonts' own `category` field (Sans Serif /
    Serif / Display / Handwriting / Monospace, from the public `fonts.google.com/metadata/fonts`
    endpoint, no key needed), not google/fonts' volunteer style-tag project's specific
    sub-style tags (`/Sans/Geometric` and the like), because that tag project's coverage is far
    thinner outside Latin: checked directly before writing the script, only 34/62 (55%) of
    families that genuinely cover Devanagari, and 24/68 (35%) of those that cover Japanese
    (kana), carry any of Latin's specific sub-style tags at a score ≥ 50, versus 100% coverage
    for `/Quality/Drawing` (used for in-class ranking) and 100% for Google's own `category`
    field. This is the "small handful of style classes, not Latin's ten" this task's own
    instruction anticipated, arrived at from the real, checked catalog, not assumed.

109. **Real family counts per class, and the classes that were too thin to build at all (law 5:
    a box needs a real distribution behind it, not a fabrication).** Regenerated fresh
    (`git ls-remote` pinned `google/fonts@23e54b51ddffbc7713c583748e3bd86f62b1fa4a`,
    2026-09-24): `devanagari-sans` 25 families, `devanagari-serif` 19, `devanagari-display` 9;
    `kana-sans` 18, `kana-serif` 11, `kana-display` 11, `kana-handwriting` 6. Two classes'
    real candidate pools ran under this script's own `MIN_FAMILIES = 5` and were **not**
    written into the pack: `devanagari-handwriting` (4 real candidates: Playpen Sans Deva,
    Dekko, Kalam, Amita) and `devanagari-mono` (0). For kana, `kana-mono` also fell under the
    line (1 real candidate: M PLUS 1 Code) and was skipped. `data/node-economy-devanagari.json`
    therefore has 3 style classes, not the 5 categories the taxonomy defines; kana has 4, not 5.
    Family counts for the thinner classes are sensitive to google/fonts' live network
    (transient per-file fetch failures) in a way Latin's 30-wide classes are not buffered
    against, because a thin category's full real candidate pool is already inside the "a few
    spares for failed downloads" window (`cands[: top + 8]` already covers every candidate when
    the pool is under ~38) — a re-run of `devanagari-serif`/`devanagari-display` during this
    same task varied by one family each (19→17, 9→8) on a transient download failure, while
    `devanagari-sans` and every kana class reproduced exactly. The **committed** files
    (`data/node-economy-devanagari.json`, `data/node-economy-kana.json`) are from the run whose
    console summary is quoted above and match the numbers this section and the new
    `qa:corpus` tests pin; they were not overwritten by the flakier verification re-run.

110. **Loader wiring: `NodeEconomyCorpus.load()`/`loadNodeEconomyPack()` took no parameters at
    all before this — extended, not replaced, so every existing call site
    (`ui/.../WorkbookCampaignSnapshot.kt`, `campaign/.../HyleDecoTask4GateTest.kt`, and every
    test that called `NodeEconomyCorpus.load()` or `loadNodeEconomyPack()` bare) keeps
    compiling and behaving identically.** `loadNodeEconomyPack(resourcePath: String =
    FULL_PACK_RESOURCE_PATH)` and `NodeEconomyCorpus.load(resourcePath: String =
    FULL_PACK_RESOURCE_PATH)` gained a defaulted parameter; two new named, zero-argument
    companion functions, `NodeEconomyCorpus.loadDevanagari()` and `NodeEconomyCorpus.loadKana()`
    (`qa/corpus/.../NodeEconomyCorpus.kt`), are the intended call sites for the new packs so a
    caller never has to know the literal classpath resource string. `CorpusResources.kt` gained
    `DEVANAGARI_PACK_RESOURCE_PATH`/`KANA_PACK_RESOURCE_PATH` alongside the existing
    `FULL_PACK_RESOURCE_PATH`/`COMPACT_PACK_RESOURCE_PATH`; `qa/corpus/build.gradle.kts`'s
    existing `syncCorpusData` task already globs `node-economy-*.json`, so both new files reach
    the classpath/Wasm-Node resource bundle with no build-script change. New tests:
    `qa/corpus/src/commonTest/.../ScriptNodeEconomyCorpusTest.kt` (5 tests: both packs' real
    source/glyph-count/style-class shape, a handful of real glyph boxes checked against the
    committed JSON's own numbers, every style class's box for 4 glyphs each checked min ≤ q1 ≤
    med ≤ q3 ≤ max with max > 0 and n ≥ 5, and family-row/box agreement) plus two
    `CorpusDataResourceTest.kt` classpath checks — 61/61 `qa:corpus` tests pass (`jvmTest` and
    `wasmJsNodeTest` both green; `:qa:corpus:spotlessCheck :qa:corpus:check` clean). **Left
    open, not decided here:** no compact (on-curve-only) derivative was built for either new
    pack — `build_compact_corpus.py` is Latin-specific (hardcodes `node-economy-latin.json` as
    its default source) and item 8's own "which pack the app ships" question is still
    unresolved for Latin, so extending compaction to two more packs before that question is
    answered would be speculative; and no UI/campaign code was wired to consume
    `loadDevanagari()`/`loadKana()` yet — this task's own scope was the corpus and its loader,
    not a Devanagari/kana workbook gate, which does not exist yet either.

## P8: independent verification (Devanagari/kana/Arabic script data, real shapers, corpus extension)

111. **Every build/test claim in items 96-110 independently reproduced from a fresh, isolated
    re-run, plus one real, previously-uncaught KDoc inaccuracy found and fixed — full method
    below rather than just the totals, per this build's own verify-pass convention.** Fresh
    `--rerun-tasks` runs, env `ANDROID_HOME=/opt/android-sdk
    CHROME_BIN=/opt/pw-browsers/chromium-1194/chrome-linux/chrome LANG=en_US.UTF-8`:
    `:scripts:spotlessCheck :scripts:jvmTest :scripts:wasmJsNodeTest` — BUILD SUCCESSFUL,
    181/181 both targets (JUnit XML, not console text), both before and after the fix below.
    **`:shape-preview:jvmTest`, exactly as this task's own instructions named it, does not
    exist** — this module's JVM/Compose target is named `desktop`, not `jvm` (it has no
    `jvmMain`/`jvmTest` source set at all), so the real equivalent is `desktopTest`; run as
    `:shape-preview:spotlessCheck :shape-preview:desktopTest :shape-preview:wasmJsNodeTest` —
    BUILD SUCCESSFUL, 16/16 `desktopTest` (the real `SkikoShaperTest`/`SkikoClustersTest`, not
    stubs), 1/1 `wasmJsNodeTest` (the shared `ShaperStubTest`, correctly still a stub check on
    this target). The real Android compile task, run separately:
    `:shape-preview:compileAndroidMain :shape-preview:testAndroidHostTest` — BUILD SUCCESSFUL;
    `testAndroidHostTest` is 1/1 because this module has no `androidHostTest`/`androidUnitTest`
    source file for `AndroidTextRunShaper` at all (confirmed by directory listing, not
    assumed) — consistent with, not contradicting, item 107's own "unverifiable on-device here"
    disclosure. `:qa:corpus:spotlessCheck :qa:corpus:check` — BUILD SUCCESSFUL, 89/89 `jvmTest`,
    85/85 `wasmJsNodeTest`. Combined repo-wide re-run (`spotlessCheck jvmTest wasmJsNodeTest
    :ui:desktopTest :ui:testAndroidHostTest`, root, plus `:ui:wasmJsBrowserTest` separately) —
    BUILD SUCCESSFUL both before and after the fix: `jvmTest` 1,007/1,007 (sum across all ten
    pure modules' own XML), `wasmJsNodeTest` 977/977, `:ui:desktopTest` 290/290,
    `:ui:testAndroidHostTest` 198/198, `:ui:wasmJsBrowserTest` 199/199 — the last three
    byte-identical to P7's own item 95 totals, confirming P8 changed nothing `:ui:` depends on
    in a way that broke it, even though `:ui:` genuinely depends on all three P8-touched modules
    (`:scripts:`, `:shape-preview:`, and `:qa:corpus:` transitively via `:qa:`/`:campaign:` —
    checked directly in `build.gradle.kts` files, not assumed). Zero failures, errors or
    skips anywhere, in any run.
    - **`ScriptProfile.kt` reuse, confirmed genuine, not forked:** all three of
      `DevanagariScriptProfile.kt`/`ArabicScriptProfile.kt`/`KanaScriptProfiles.kt` import and
      assemble the real shared `ScriptProfile`/`WritingScript` types (not a per-script
      reimplementation); every file in `devanagari/` (6/6) and all but three files in `arabic/`
      and one in `kana/` import the shared model directly — the exceptions
      (`ArabicJoiningType.kt`, `ArabicLetters.kt`, `NastaliqOutOfScope.kt`, `KanaGlyphNaming.kt`)
      are raw data/naming helpers with no need to, confirmed by reading each; no file anywhere
      declares a second, competing `ScriptProfile`/`GlyphInventory`/etc.
    - **Template-zip fidelity, independently recounted from a fresh `unzip`, not trusted from
      the manifests:** `svg/Devanagari` 68 files (66 distinct by md5 — the same two duplicate
      pairs item 96 names, confirmed by hash and filename), `svg/Hiragana` 55, `svg/Katakana`
      57 (no duplicates), `svg/Naskh` 49, `svg/Nastaliq` 49 — all six exactly matching the zip's
      own `HOW_TO_USE.md` table. Re-running all three generator scripts fresh
      (`build_devanagari_template_manifest.py`, `generate_kana_manifest.py`,
      `generate_naskh_manifest.py`) reproduces the checked-in manifests byte-for-byte (aside
      from `generated_at`).
    - **Naming convention, spot-checked and cross-verified against a live source, not just
      read:** real glyph names sampled from each script's own manifest/test file all match —
      `a-deva`/`aaMatra-deva`/`anusvara-deva` (Devanagari), `a-hira`/`a-kata` through the full
      112-glyph list (kana), `beh-ar` → `beh-ar.init`/`.medi`/`.fina` (Arabic, `beh` being a
      real dual-joining letter). The 39-letter `ArabicJoiningType` table was independently
      re-derived by fetching the live `ArabicShaping.txt` (Unicode 18.0.0,
      unicode.org/Public/UCD/latest/ucd/, this session's own fetch, not item 104's) and diffing
      all 39 real codepoints programmatically against this build's table: **zero mismatches.**
    - **Law 5 numeric cross-check, with real, independently-obtained numbers:** downloaded
      Khula-Regular.ttf fresh from `raw.githubusercontent.com/google/fonts` at the pack's own
      pinned commit (`23e54b51ddffbc7713c583748e3bd86f62b1fa4a`) and re-derived अ
      (DEVANAGARI LETTER A)'s point count with a fresh, independent script (not calling the
      build script's own function) — **on=38, off=24, contours=1**, exactly matching
      `data/node-economy-devanagari.json`'s own committed `Khula`/`अ` entry. Independently
      recomputed the `devanagari-sans`/`अ` quartiles from that same file's 25 raw per-family
      counts by linear interpolation — min 34, q1 40.0, med 46.0, q3 54.0, max 70, matching the
      committed `dist` block exactly; repeated for `kana-sans`/`あ` (min 36, q1 47.25, med 55.5,
      q3 76.5, max 112, n 18) with the same exact match. Fetched `fonts.google.com/metadata/fonts`
      live and independently confirmed the disclosed thin/excluded classes by name: Devanagari's
      `devanagari` subset has exactly 62 families (34/62 in item 108's own ratio), its four real
      Handwriting-category members are exactly `Amita`, `Dekko`, `Kalam`, `Playpen Sans Deva`
      (item 109's own list), and its Monospace count is 0; the `japanese` subset has exactly 68
      families (24/68), and its one real Monospace member is exactly `M PLUS 1 Code` — all five
      numbers matching items 108/109 with nothing invented. The counting-rule function
      (`_count_simple_contours`) in `build_script_node_economy_corpus.py` is algorithmically
      byte-identical to Latin's own already-reviewed `build_node_economy_corpus.py` (diffed
      directly; only docstrings differ), not a re-derived or drifted copy.
    - **Shape-preview audit:** `SkikoShaperTest` genuinely shapes real text with real fonts
      (`fonts/HyleDeco-Regular.ttf`, `fonts/NotoSansDevanagari-Regular.ttf`,
      `fonts/NotoNaskhArabic-Regular.ttf` — both Noto files' real `name` table IDs 0/13
      independently re-read here and confirmed OFL-1.1, matching `THIRD_PARTY.md`'s own claim)
      through the real `org.jetbrains.skia.shaper.Shaper`, not a mock. `AndroidTextRunShaper` is
      real code against the real Android SDK (`javap`, run directly against
      `/opt/android-sdk/platforms/android-37.0/android.jar`, confirms
      `android.graphics.text.PositionedGlyphs` genuinely has no `getCluster`/clusterrange method
      on any level through API 37 — item 106's claim, now independently reproduced rather than
      trusted) and its on-device behaviour is honestly disclosed as unverified, not claimed to
      work. The no-JNI-HarfBuzz call is a real, reasoned decision, not a silent skip: it is
      documented at length in the pre-existing (untouched by P8 — confirmed by `git log`)
      `Shaper.kt` top KDoc, restated in `AndroidTextRunShaper`'s own KDoc, and the real diff
      shows only stub deletions, two new real implementations, and a `README.md`/
      `build.gradle.kts` update naming the decision — no HarfBuzz-JNI code was added or removed.
      wasmJs's `BrowserFontFaceShaperStub` has zero diff (`git status` confirms) and is
      correctly still a stub, named in the same `README.md` diff as "P9's own scope."
    - **Anti-gaming audit: clean.** Grepped every new/changed main-source file (not test files)
      across `scripts/`, `shape-preview/`, `qa/corpus/` for glyph-name/family/script literal
      branching (`if (glyphName == "..."`, `when (family)`, etc.) and fixture-tied magic-number
      equality checks — none found. `NodeEconomyCorpus`'s new `loadDevanagari()`/`loadKana()`
      are a plain, generic default-parameter extension, not fixture-specific code.
    - **One real bug found and fixed: `KanaFeaturePlan.kt`'s own KDoc miscounted Devanagari's
      real GSUB stage count as "nine"; it is 15** (`DevanagariFeaturePlan.kt`'s real
      `gsubStagesInOrder` list, pinned by `DevanagariFeaturePlanTest`'s own
      `assertEquals(15, ...)`) — the "nine" only ever matched the first of Devanagari's own two
      documented stage-groups (the nine pre-base/reordering-adjacent tags, `locl` through
      `cjct`), not the real 15-entry total; Arabic's "eight" was already correct. The same
      undercount was independently written into this log's own item 102 ("Devanagari (nine
      GSUB stages)") — that entry is left as-is per this log's append-only rule; this entry is
      the correction. Fixed by rewording `KanaFeaturePlan.kt`'s KDoc to state both real counts
      (8 and 15) precisely; no code, data or test changed. `:scripts:spotlessCheck
      :scripts:jvmTest :scripts:wasmJsNodeTest` re-run fresh after the fix: BUILD SUCCESSFUL,
      181/181 both targets, unchanged from before the fix (doc-only change). The full combined
      repo-wide re-run above was also re-run fresh after the fix and is the "after" set of
      numbers quoted in this entry; before and after are numerically identical everywhere except
      that this one KDoc's text changed, confirming the fix altered no behaviour.
    - **Not independently reproduced, disclosed rather than silently skipped:** re-running the
      full corpus-extension pack build from scratch (cloning `google/fonts`, downloading ~130
      font files for both scripts) was judged too expensive for this pass; the Law 5 audit above
      instead independently re-derives one real glyph's count from one freshly-downloaded real
      font (the established throwaway-probe pattern) and independently recomputes quartiles from
      the committed raw per-family data, which is the same standard `docs/OPEN_QUESTIONS.md`'s
      own P6/P7 verify passes used.

    *Independent verification pass; not a product decision, so no owner tag.*

## P9: Browser shaping preview — re-verified for real, and `BrowserFontFaceVisualPreview`

112. **Item 5's "no glyph ids or clusters on the web" finding (restated by `BrowserFontFaceShaperStub`'s
    own KDoc since P0) re-checked live, today, in this sandbox's own real, stable, non-flagged
    Chromium 141.0.7390.37 (`/opt/pw-browsers/chromium-1194`, driven by the `playwright` package at
    `/opt/node22/lib/node_modules/playwright`), not trusted from the earlier entry — finding
    CONFIRMED, with more surfaces checked than before.** A real `FontFace` was built from
    `fonts/NotoSansDevanagari-Regular.ttf`'s real bytes, added to `document.fonts`, loaded for
    real, and used to draw real Devanagari conjunct text (क्ष, U+0915 U+094D U+0937 — the same
    fixture `SkikoShaperTest.shapesRealDevanagariConjunctAndFindsRealClusterData` shapes on
    desktop). What each real, standard, non-experimental API actually returned, dumped directly
    from the live page, not read off a spec:
    - `CanvasRenderingContext2D.measureText(text)`'s `TextMetrics`: exactly `width,
      actualBoundingBoxLeft, actualBoundingBoxRight, fontBoundingBoxAscent, fontBoundingBoxDescent,
      actualBoundingBoxAscent, actualBoundingBoxDescent, hangingBaseline, alphabeticBaseline,
      ideographicBaseline` — whole-string metrics only (dumped every own+inherited property on a
      real instance). No glyph count, no glyph ids, no per-character or per-cluster boxes.
    - `TextMetrics.getSelectionRects` (the one experimental candidate that sometimes ships
      partially flagged): `typeof tm.getSelectionRects === 'function'` is `false` — does not exist
      on this real, stable build.
    - The "CSS Font Metrics API": `FontFace.prototype`'s real, complete property list is `family,
      style, weight, stretch, unicodeRange, variant, featureSettings, display, ascentOverride,
      descentOverride, lineGapOverride, sizeAdjust, status, loaded, load, variationSettings` — the
      `*Override` entries are `@font-face` metric-*override* descriptors, not a read API for an
      already-loaded face's real metrics; there is no readable `ascent`/`descent`/`lineGap` getter
      on a real `FontFace` at all (accessing `face.ascent` on a real, loaded instance returns
      `undefined`).
    - `OffscreenCanvas`'s `CanvasRenderingContext2D`: the identical restricted `TextMetrics`
      surface, no extra glyph method (`getGlyphs`/`getGlyphIds` do not exist).
    - `CanvasRenderingContext2D.prototype`'s own real property names, filtered for
      `/glyph|shape|cluster|run/i`: zero matches.
    - The Local Font Access API (`navigator.fonts.query`): not present in this build (`'fonts' in
      navigator` is `false`); and even where it does ship, it reads a *locally installed* system
      font's raw table bytes by postscript name, not shaped output from an arbitrary `FontFace` —
      a "read the font and shape it yourself" route, not "the browser's own text engine hands back
      structure," so it would not answer this question differently even if present.
    - `window` has no `GlyphRun` (or similarly named) primitive.
    - Real shaping is genuinely still happening underneath all of this, confirmed by width alone
      (`font: 100px` on this same real font): the conjunct measured 71.7 CSS px wide, a single
      unligated "क" measured 76.8 px, and three separate base consonants ("ककक", no virama, no
      ligation) measured 230.4 px — the conjunct is *narrower than one single consonant*, real
      evidence of real ligation into one compact glyph, not three glyphs painted in sequence. The
      browser's text engine does real, correct HarfBuzz-backed work; it simply never publishes the
      intermediate structure through any API checked above, on this real, current, stable build.

    Given the finding still holds, empirically, `BrowserFontFaceShaperStub` stays a real, honest
    stub (this interface's own top KDoc: "no shaper fakes glyphs") rather than being forced to
    return a fabricated `ShapeResult.Shaped` — its own KDoc and `NotImplemented.planned` message
    were rewritten with this exact re-verified finding, keeping `platformShaper()` and the class's
    name/shape unchanged (P8's own established disclosure style: `AndroidTextRunShaper`'s KDoc
    names a real gap plainly rather than routing around it).

113. **Built instead, honestly not a `Shaper`: `BrowserFontFaceVisualPreview.renderPng`
    (`shape-preview/src/wasmJsMain/.../BrowserFontFaceVisualPreview.kt`) — a real `FontFace`, real
    browser paint, real PNG bytes.** Registers the caller's font bytes as a real `FontFace`, awaits
    its real `.load()` (`Promise<T>.await()` from `kotlinx-coroutines-core`, not a guessed delay),
    paints the requested text on an off-screen `<canvas>` (never inserted into the visible page,
    `direction`/`textAlign` set from `ShapeRequest.rightToLeft` so RTL text really reorders), and
    returns `canvas.toDataURL("image/png")` decoded to real PNG bytes. Deliberately not named or
    shaped like `Shaper` — its own KDoc states why at length, mirroring the reasoning style item
    106/`AndroidTextRunShaper` already established for a different real gap. Two real, empirical
    findings behind its implementation, both found by trying, not guessed:
    - `kotlinx-browser` 0.5.0 (already resolved transitively into this build's own dependency
      graph via Compose; `THIRD_PARTY.md` already listed it before this task) has typed `Canvas`/
      `Document` bindings (`measureText`, `fillText`, `toDataURL`, `TextMetrics`,
      `CanvasRenderingContext2D`, `HTMLCanvasElement`, `getContext`, `createElement` — confirmed
      present by searching its own klib's string table directly) but **no `FontFace` binding at
      all** — `FontFace` itself is hand-bound with one `js("""...""")` block, the same pattern
      `learn/scenes`'s own `LearnFaceResources.wasmJs.kt` already established for the one thing
      this codebase's other JS dependencies don't cover.
    - `kotlinx-browser` 0.5.0's `CanvasRenderingContext2D.direction`/`.textAlign`/`.textBaseline`
      setters take its own `CanvasDirection`/`CanvasTextAlign`/`CanvasTextBaseline` types, not
      `String` (a real `compileKotlinWasmJs` error, not assumed), and this klib's public API
      surface exports no usable constructor or constant for them (every constant-access spelling
      tried was rejected too) — three one-line raw-JS property setters direct on the real `ctx`
      object were used instead of guessing that type's real API shape.
    - `kotlinx-coroutines-core` 1.9.0's real wasmJs `CoroutineScope.promise { ... }` builder always
      returns `Promise<JsAny?>`, whatever `T` the block produces (checked by trying `Promise<T>`
      first — real compiler error) — a real, wasmJs-specific detail of that library, not a defect
      in this task's own code.

    Both libraries are pinned in `gradle/libs.versions.toml` (`kotlinx-coroutines`,
    `kotlinx-browser`) to the exact versions `THIRD_PARTY.md` already listed as transitively
    shipped (1.9.0, 0.5.0) and declared directly on `shape-preview`'s own `wasmJsMain` — the same
    "declare directly what was previously only transitive" move item 33's `skiko-awt` entry
    already made for the identical reason (`THIRD_PARTY.md` updated in this same change).

114. **A real Kotlin/Wasm test-framework constraint found by trying, and a real bug the resulting
    test infrastructure genuinely caught (not staged) — both disclosed rather than smoothed over.**
    (a) Kotlin's wasmJs test framework rejects a `suspend fun` directly annotated `@kotlin.test.Test`
    (`compileTestKotlinWasmJs`'s real error: `'suspend' functions annotated with
    '@kotlin.test.Test' are unsupported`) — `BrowserFontFaceVisualPreviewBrowserTest`'s three real
    tests instead each return a `Promise<JsAny?>` built with `GlobalScope.promise { ... }`, which
    Kotlin/Wasm's Mocha-backed browser test runner does genuinely await (see (b) for the real proof,
    not an assumption). (b) The first real run of
    `realDevanagariConjunctRendersVisuallyNarrowerThanThreeSeparateConsonants` against real headless
    Chrome genuinely FAILED: `conjunct PNG width (163 px) is not meaningfully narrower than half of
    three separate consonants' width (319 px)` — a real assertion inside the awaited coroutine,
    surfaced correctly as a JUnit-XML test failure by `:shape-preview:wasmJsBrowserTest`
    (`4 tests completed, 1 failed`), proving the Promise-await bridge genuinely works rather than
    reporting the test passed the instant its synchronous part returned. The cause was a real test
    bug, not a bug in `renderPng`: `renderPng`'s own fixed padding (`pixelSize` CSS px total, both
    sides) dilutes a raw PNG-width *ratio* toward 1 at a small `pixelSize`, so `conjunctWidth <
    threeSeparateWidth / 2` on the raw widths was the wrong comparison. Fixed by subtracting the
    known, fixed padding (both calls use the same explicit `pixelSize = 96.0`) before comparing —
    the padding-adjusted text widths are 67 px (conjunct) vs. 223 px (three separate), a real,
    comfortable margin under half. Also this module runs both `wasmJsNodeTest` and (newly, for this
    task) `wasmJsBrowserTest` off one shared `wasmJsTest` compilation, so every test in
    `BrowserFontFaceVisualPreviewBrowserTest` calls a real `typeof document !== 'undefined'` check
    first and no-ops under Node, the same "no-op there by construction" shape `ShaperStubTest`'s
    own KDoc already uses for a different reason.

    **Real, fresh (`--rerun-tasks`) build/test results, env `ANDROID_HOME=/opt/android-sdk
    CHROME_BIN=/opt/pw-browsers/chromium-1194/chrome-linux/chrome LANG=en_US.UTF-8`:**
    `:shape-preview:spotlessCheck :shape-preview:wasmJsNodeTest :shape-preview:wasmJsBrowserTest
    :shape-preview:desktopTest` — BUILD SUCCESSFUL. `wasmJsNodeTest`: 4/4 (`ShaperStubTest` 1,
    `BrowserFontFaceVisualPreviewBrowserTest` 3, all three no-op there by the `document` check, 0
    failures — confirms this module's existing Node-run tests stay green). `wasmJsBrowserTest`: 4/4
    for real in headless Chrome (same two classes, this time genuinely exercising `renderPng`, 0
    failures). `desktopTest`: 16/16 unchanged (`ShaperStubTest` 1, `SkikoClustersTest` 8,
    `SkikoShaperTest` 7) — confirms the two new wasmJs-only dependencies did not disturb the P8
    desktop shaper at all. `Shaper.kt`'s public API (the interface, `ShapeRequest`/`ShapeFont`/
    `ShapedRun`/`PositionedGlyph`/`GlyphCluster`/`ShapeResult`/`ShapingStack`) was not touched —
    `BrowserFontFaceVisualPreview` reuses `ShapeRequest`/`ShapeFont` as a plain parameter bag, not
    as a `Shaper` implementation, and `BrowserFontFaceShaperStub`'s only change is its own KDoc and
    `planned` message text.

## P9: hosted build endpoint (`compile` module — `HostedBuildProtocol.kt`,
`HostedEndpointBackend.kt`, `HostedEndpointTransport.kt`, `docs/HOSTED_BUILD_ENDPOINT.md`)

115. **The wire contract in `docs/HOSTED_BUILD_ENDPOINT.md` is new and unreviewed.** No hosted
     endpoint existed before this pass (`HostedEndpointBackend.compile()` was a stub, brief §3,
     prompt P9), so there was no existing contract to match: the request/response JSON shape
     (`POST {endpoint}/v1/compile`, files base64-whole in one JSON body, success/failure both as
     HTTP 200 distinguished by a `status` field, non-2xx and transport errors folded into
     `CompileResult.Failure`) is this pass's own design, made to be simple, testable and honest
     rather than derived from any spec. It is exercised for real against a local mock server
     (`HostedEndpointBackendRealHttpTest`, `:compile:wasmJsNodeTest`) and the encode/decode logic
     is unit-tested on every target (`HostedBuildProtocolTest`), but nothing has checked it
     against whatever the real fontmake-in-a-container service (once someone builds it) will
     actually expect — that service does not exist yet either. Whoever builds the real endpoint
     should treat this doc as the starting contract to implement against, not as settled the way
     `docs/DECISIONS.md`'s entries are. `[CONFIRM]`. *Madhav, once a real deployment is being
     built.*

116. **`HostedEndpointBackend.availability()` now reports `Available` once an endpoint is
     configured**, rather than always `Unavailable` as it did when the whole backend was a stub.
     The originating task for this pass explicitly said not to change the `endpoint == null`
     branch (unchanged: still `Unavailable(PLANNED)`, exact same value) but did not ask for the
     non-null branch to change either way; reporting `Available` once there is a real `compile()`
     to try was judged the honest reading — an endpoint the UI could otherwise never attempt
     would be a worse kind of dishonesty than the stub it replaces — but it is a real behavioural
     decision made without a request for it, not one this log's other entries can point to a
     brief section for. `[CONFIRM]`. *Madhav.*

117. **Zero-retention is written as a policy, not proven.** `docs/HOSTED_BUILD_ENDPOINT.md`'s own
     "Zero-retention policy" section says plainly that nothing about a promise like "the endpoint
     keeps nothing" can be verified by the client from outside the container — no response header
     defined here would be trustworthy evidence even if one existed. The document recommends that
     a real deployment publish its container image and infrastructure-as-code so the policy is
     checkable rather than only promised, but building or publishing either of those is outside
     this pass's own scope (no real endpoint is deployed, per `docs/DECISIONS.md`'s "hosted
     endpoint's home" still being open). Whoever deploys the real endpoint should treat that
     recommendation as a requirement, not a suggestion. *Madhav, at deployment time.*

118. **`:compile:wasmJsBrowserTest` is registered but disabled** (`KmpPlatformConventionPlugin`'s
     own default `testTask { enabled = false }`; confirmed by actually running it —
     `BUILD SUCCESSFUL`, task `SKIPPED`, not merely believed from reading the plugin), so no
     module in this repository proves `compile`'s wasmJs code inside a real browser; the real
     proof this pass has is `wasmJsNodeTest` against a real local HTTP server, using Node's own
     global `fetch()` (Node ≥ 18), which implements the same Fetch API a browser does but is not
     a browser. If a later pass wants `compile`'s own wasmJs tests to also run under headless
     Chrome the way `:ui`'s does (`ui/build.gradle.kts`'s own `testTask { enabled = true }`
     override, with its CMP-4906 reasoning), that override was judged out of scope here since it
     was not asked for and this module has no Compose dependency forcing the switch the way `ui`
     did. Noted rather than done. *Not a product decision — implementation choice, flagged for
     visibility.*

## P9: web canvas frame-time measurement — a headless-targeting limitation, disclosed

119. **The puck's grip-drag (`PuckGestureMachine.GripDragging`, repositioning the puck via its
    top-of-disc handle) could not be reliably engaged from this task's headless Playwright
    session, despite systematic testing.** `docs/WEB_PERFORMANCE.md` needed a real, sustained
    single-pointer drag against the Draw sheet's canvas to measure frame times. The plan was the
    puck's grip-drag specifically. Candidate press points were computed directly from
    `PuckGeometry.gripVisualCenter`'s own formula (`center.y - radiusDp + GRIP_INSET_FROM_EDGE_DP`,
    `PUCK_DIAMETER_DP = 68`, `GRIP_MIN_HIT_DP = 44`) using the puck's actual on-screen centre,
    itself measured two ways that agreed with each other and with `PuckPinState.defaultFor`'s own
    x formula (`canvasSizeDp.x - 18 - 34` = 308 CSS px at a 360 px-wide viewport, matching the
    rendered circle's measured horizontal centre exactly): a dark-pixel bounding box of the
    rendered disc, and a direct per-pixel scan for the grey grip bar itself (found at CSS y
    ≈ 287, inside the theoretically correct ±22 dp hit square either way). Nine candidate points
    spanning the full theoretical 44×44 dp hit box (CSS x 300–316, y 270–300) were each pressed
    and dragged 60 CSS px in a real `page.mouse` sequence; every one engaged `Swiping` (the
    tool-cycle drag) instead of `GripDragging`, confirmed visually each time by the puck's tool
    label changing (SELECT → PEN → BOOLEAN → MEASURE → …) rather than the puck's position
    changing (confirmed by a row-based pixel scan of the rendered disc's vertical position,
    identical — 935.5 px, i.e. unmoved — across all nine trials). The canvas element's own
    `getBoundingClientRect()` was also checked and is exactly `(0, 0, 360, 640)` at
    `devicePixelRatio: 3`, ruling out a canvas-offset explanation. **This is logged as a real
    limitation encountered, not a claimed bug**: the coordinate math checks out against the
    source on paper, this task could not identify why the live hit-test disagrees within the time
    available, and it did not modify `Puck.kt`/`PuckGestureMachine.kt`/`PuckGestureConfig.kt` to
    investigate further, since debugging a gesture state machine was out of this task's own scope
    (frame-time measurement). `docs/WEB_PERFORMANCE.md`'s own numbers instead use the `Swiping`
    drag, which is still a real, single-pointer, continuously-repainting gesture recognised by the
    same state machine — a worked-around limitation, not a fabricated result. Worth a look by
    whichever task next touches `ui/puck/`: either a genuine coordinate-space mismatch between
    `PointerInputChange.position` (as delivered to `puckGestureArbiter`) and `PuckGeometry.center`
    on the wasmJs target specifically, or a gap in this task's own reasoning about Compose's
    local-vs-global pointer coordinate contract — worth a real on-device or real-browser
    (non-headless, human-observed) check either way, since this task's evidence is consistent but
    entirely headless.

## P9: independent verification (hosted build endpoint, browser shaping preview, frame times)

120. **Every P9 build/test/audit claim (items 112-119) independently reproduced from fresh,
    isolated re-runs, plus one real numeric inconsistency found in `docs/WEB_PERFORMANCE.md` and
    disclosed with a re-measurement rather than silently corrected.** Fresh `--rerun-tasks` runs,
    env `ANDROID_HOME=/opt/android-sdk CHROME_BIN=/opt/pw-browsers/chromium-1194/chrome-linux/chrome
    LANG=en_US.UTF-8`: `:compile:spotlessCheck :compile:desktopTest :compile:testAndroidHostTest
    :compile:wasmJsNodeTest` — BUILD SUCCESSFUL, 16/16 `desktopTest`, 16/16 `testAndroidHostTest`,
    21/21 `wasmJsNodeTest` (16 `HostedBuildProtocolTest` + 5 `HostedEndpointBackendRealHttpTest`,
    JUnit XML, not console text) — matching item 115's own "Tested" section exactly.
    `:compile:wasmJsBrowserTest` re-run separately: task genuinely `SKIPPED` (disabled), confirming
    item 118 by running it, not by reading the plugin. `:shape-preview:spotlessCheck
    :shape-preview:wasmJsNodeTest :shape-preview:wasmJsBrowserTest :shape-preview:desktopTest` —
    BUILD SUCCESSFUL, 4/4 `wasmJsNodeTest`, 4/4 `wasmJsBrowserTest` (real headless Chrome, 3 real
    `BrowserFontFaceVisualPreviewBrowserTest` cases), 16/16 `desktopTest` — exactly item 114's own
    numbers. Combined repo-wide re-run (`spotlessCheck jvmTest wasmJsNodeTest :ui:desktopTest
    :ui:testAndroidHostTest`, root, plus `:ui:wasmJsBrowserTest` separately) — BUILD SUCCESSFUL:
    `jvmTest` 829/829, `wasmJsNodeTest` 832/832 (both sums across every pure module's own XML,
    covering `:scripts:`/`:qa:corpus:`/`:campaign:` in full), `:ui:desktopTest` 290/290,
    `:ui:testAndroidHostTest` 198/198, `:ui:wasmJsBrowserTest` 199/199 — the last three
    byte-identical to P7/P8's own item 95/111 totals, confirming zero regression anywhere `:ui:`
    depends on. Zero failures, errors or skips anywhere, in any run.
    - **`:app-web:wasmJsBrowserDistribution --rerun-tasks` rebuilt fresh independently** — BUILD
      SUCCESSFUL (109 actionable tasks, all executed; the same pre-existing webpack asset-size
      warnings item 119's own predecessor doc already names, not new errors). Served locally and
      loaded in this session's own real headless Chromium (`/opt/pw-browsers/chromium-1194`, at a
      360x640/DPR-3 mobile viewport), with a real screenshot taken and looked at directly (not
      trusted from any other stage's own description): the Draw sheet renders correctly — header
      ("DRAW -- pts", "MAP"), BACKGROUND/SKETCH layer rows, the glyph bounding box with its guide
      lines, the "DRAW" watermark, the puck at "SELECT", and the Workbook/Learn corner buttons all
      present, matching `docs/WEB_PERFORMANCE.md`'s own description. Console/network capture during
      load: zero failed requests, zero HTTP >= 400 in the `response` listener, one `[error] Failed
      to load resource: 404` console line consistent with the same cosmetic `favicon.ico` 404
      `docs/WEB_PERFORMANCE.md` already names (not a new problem).
    - **Browser-shaper audit: the glyph-id/cluster-availability finding independently re-run live,
      today, in this session's own real Chromium 141.0.7390.37, not trusted from item 112's own
      write-up.** A real `FontFace` built from `fonts/NotoSansDevanagari-Regular.ttf`'s real bytes,
      loaded for real, used to measure the same real Devanagari conjunct (क्ष) against a single क
      and three unligated ककक on a real `<canvas>`: **71.70 px / 76.80 px / 230.40 px — exact,
      byte-for-byte matches of item 112's own three widths**, on the identical Chrome build.
      `CanvasRenderingContext2D.prototype`'s own real property list, independently re-filtered for
      `/glyph|shape|cluster|run/i`, is empty here too; `TextMetrics.getSelectionRects` is still
      absent; `FontFace.prototype`'s own real property list is still the same fifteen names with no
      readable `ascent`/`descent` getter (`face.ascent` reads `undefined`); `'fonts' in navigator`
      and `'GlyphRun' in window` are still both `false`. Finding: **independently CONFIRMED, not
      merely re-read.** Grepped every P9-touched main-source file (`compile/`, `shape-preview/`)
      for a fake `ShapeResult.Shaped(` or a literal `"Shaped"`/`status = "success"` construction
      outside real response-parsing logic: **zero matches** — the only two real
      `ShapeResult.Shaped(` call sites in the whole module are `SkikoShaper.kt` (desktop) and
      `AndroidTextRunShaper.kt` (Android), both pre-existing from P8 and untouched here (`git diff`
      confirms). `Shaper.kt` itself: `git log` shows its last real change is P0's own scaffold
      commit — its public API is genuinely untouched by P9, not merely claimed to be.
    - **Hosted-endpoint audit: `HostedEndpointBackend.compile()` genuinely builds a real request
      from a real `CompileRequest` and performs a real HTTP call, confirmed by reading
      `HostedEndpointBackend.kt`/`HostedBuildProtocol.kt`/`HostedEndpointTransport.kt`/
      `MockHttpServer.kt` directly, not assumed from the doc.** `encodeRequest` walks
      `request.project.listFiles()`/`readBytes()` and base64-encodes every byte; the one `fetch(`
      call site in the module's entire main source is `HostedEndpointTransport.kt`'s
      `fetchOutcomeJson`, reached only from `HostedEndpointBackend.compile()`, reached only when a
      caller explicitly invokes it with a request — `availability()` and construction touch no
      network, confirmed by reading both, not merely the KDoc's claim. `endpoint == null` still
      returns `Unavailable(PLANNED)` with the exact same `PLANNED` string, byte for byte, against
      the pre-P9 version (`git diff` on `HostedEndpointBackend.kt` shows the `PLANNED` constant
      itself outside the diff hunks entirely) — item 116's own claim, independently confirmed by
      diff rather than by reading the class's own KDoc. `HostedEndpointBackendRealHttpTest` genuinely
      starts a real Node `http` server on a real loopback socket (`MockHttpServer.kt`'s own
      `process.getBuiltinModule('http')` call, read directly) and `HostedEndpointBackend("http://
      127.0.0.1:$port").compile(request)` genuinely POSTs to it — not a Kotlin fake standing in for
      either side, confirmed by reading the raw JS interop in both files rather than trusting the
      class-level KDoc's own claim. `docs/HOSTED_BUILD_ENDPOINT.md`'s request/response shapes were
      cross-checked line by line against `HostedBuildProtocol.kt`'s real `RequestPayload`/
      `ResponsePayload`/`BinaryPayload`/`LogPayload` classes and `interpretTransportOutcome`/
      `decodeResponseBody`'s real branching (unrecognised log level -> INFO, not dropped;
      unrecognised binary format -> dropped with a WARNING, not mislabelled; any non-2xx or
      transport failure -> `CompileResult.Failure` naming the endpoint, up to 2,000 echoed body
      characters) — **every claim in the doc matches the real code**, no discrepancy found. The
      doc's own "what the client can verify, and what it must simply trust" section on zero-
      retention is genuinely present (confirmed by reading it, not by its own table of contents)
      and is internally honest about being a policy statement, not proof — it does not claim more
      than the code (there is no real deployment) can back up.
    - **Frame-times audit: re-measured independently, one run per rate, same technique
      (`Emulation.setCPUThrottlingRate`, real `page.mouse` drag against the puck body — `Swiping`,
      the same worked-around gesture item 119 already discloses, not `GripDragging`), full numbers
      and method in a new section appended directly to `docs/WEB_PERFORMANCE.md` (not reproduced
      here in full).** Headline finding **reproduced**: median frame time pinned at vsync (~16.7 ms,
      60 fps) at 1x/4x/6x alike, and the tail visibly worsens under throttling (max 49.9 -> 100.0 ->
      150.0 ms here, against the original's 41.1 -> 91.6 -> 90.5 ms) — same real ballpark, same
      qualitative shape, in an independent run. **One real thing flagged, not smoothed over:**
      `docs/WEB_PERFORMANCE.md`'s own three frame counts (510/510/520) do not arithmetically fit its
      own stated "4 real seconds" gesture window against its own stated median deltas — `(frames -
      1) x median` lands at roughly 8.3-8.6 real seconds at every rate, not 4; this session's own
      re-run, using a `Date.now()`-bounded drive loop rather than a fixed step count, produced frame
      counts consistent with an actual 4-second window (250/240/237) at every rate. Flagged as an
      unresolved inconsistency (a plausible but unconfirmed explanation is offered) rather than
      silently corrected, since the original `frame_times.mjs` script lives in a different, prior
      session's scratchpad and was not available here to confirm which side of the mismatch is
      wrong. `docs/WEB_PERFORMANCE.md` still states plainly, at its top and now again in the
      appended section, that no physical phone was used.
    - **Law 3 audit (no backend of ours, no phoning home): clean.** The only `fetch(`/network call
      anywhere in the diff is the one already covered by the hosted-endpoint audit above, gated
      behind an explicit `compile()` call with no automatic or speculative caller anywhere in this
      session's own grep of `ui/src` and `app-web/src` for `HostedEndpointBackend`/`CompileBackend`
      usage (there is none yet — no Ship room UI screen exists in `ui/` at all, matching every other
      not-yet-built brief-§4.1 screen (Capture/Trace/Economy/Check) at this point in the build, per
      `docs/ARCHITECTURE_REVIEW.md` §4.1's own finding that only Draw/Space/Learn are wired into the
      one-sheet UI so far — not a P9-specific gap, and not a law 3 violation, since the network call
      is unreachable from the shipped app until a future task wires a Ship room UI to it).
    - **Anti-gaming audit: clean.** Grepped every P9 main-source file (`HostedEndpointBackend.kt`,
      `HostedBuildProtocol.kt`, `HostedEndpointTransport.kt`, `BrowserFontFaceShaperStub.kt`,
      `BrowserFontFaceVisualPreview.kt`) for glyph-name/endpoint-literal branching (`if (... ==
      "..."`, `when (...)` on a name/family/endpoint) and fixture-tied magic-number checks: zero
      matches anywhere.
    - **Not independently reproduced, disclosed rather than silently skipped:** a real fontmake-
      backed server deployment was not stood up to test `HostedEndpointBackend` end to end (none
      exists, per item 115's own disclosure and `docs/DECISIONS.md`'s still-open "hosted endpoint's
      home"), so this pass reproduces the same narrower proof item 115/117 already describe honestly
      (the client speaks the contract correctly against a real local server) rather than a claim
      neither this pass nor P9 itself can make yet.

    *Independent verification pass; not a product decision, so no owner tag.*

## P10.1: review of PR #1

121. **google/fonts states no licence for `tags/`** (`tags/all/families.csv`, the style-tag data
    the corpus generators rank by). google/fonts sets a licence per top-level font directory
    (`ofl/`, `apache/`, `ufl/`), and `tags/` is not one.
    P10.1 (25 Sep 2026): the file is no longer committed; the generators fetch it at the pinned
    commit on each run. What's left is only whether using it at build time needs permission.

    *Madhav; not blocking.*

122. **App code published before the licence split is Apache-2.0 for anyone who copied it.**
    Every commit on `claude/build-out-feature-6i1vsl` before bb321f5 (P0–P9, the whole app) was
    pushed to this public repository with the root LICENSE reading Apache-2.0. FSL-1.1-ALv2
    applies from bb321f5 on; copies taken earlier keep Apache-2.0. `docs/LICENSING.md` §1's
    "already published under Apache-2.0" reasoning covers the engine, and for these commits it
    covers the app too, so the FSL protection starts with the code written from bb321f5 on. For
    the lawyer review LICENSING.md already asks for.

    *Madhav (D1).*

## P10: V1 harness

123. **The V1 mockup sources are not in the repository.** `ui/v1-screens/*.dc.html`, named by
    `PROMPTS_V1.md` and `docs/SCREENS_V1.md` as the mockup sources (one board per screen ID,
    `Deck.dc.html` the shared control deck) — CLAUDE.md law 6 makes them, together with
    `docs/SCREENS_V1.md` and `docs/INTERACTION_V1.md`, the look source for every V1 screen. The
    upload this prompt worked from had the two documents but not the mockups. P11's S01/S02/S06
    UI, and every later screen, needs them first.

    *Madhav.*

124. **The glyph model has no Unicode code points.** `core-geometry`'s `Glyph` data class
    (`core-geometry/src/commonMain/kotlin/com/asoc/typewright/core/geometry/Glyph.kt:83-89`)
    carries a name, advance width, contours, anchors and guidelines — no `unicode` field of any
    kind. `core-font`'s `GlifCodec` confirms this on both sides of the round trip: its own KDoc
    states `<unicode>` "is skipped on read (nothing in `Glyph` has anywhere to put them) and
    never written" (`core-font/src/commonMain/kotlin/com/asoc/typewright/core/font/ufo/GlifCodec.kt:35-36`),
    and `parseGlif`'s element loop actually does this — a `<unicode>` element falls into the
    `else -> reader.skipElement()` branch, commented `// unicode, image, lib, note: not modelled,
    see KDoc` (same file, lines 91-93); `writeGlif` (lines 245-271) never emits a `<unicode>`
    element either. So importing a font loses its cmap on the way into a project, and a font
    compiled back out could not map characters to glyphs. No V1 prompt names this gap. It
    belongs to P11's project model (deciding where a code point lives) and P12's import (reading
    it off the source font).

    *build.*

125. **The UFO writer is cubic-only, so a font opened from a TTF can't be saved as a project.**
    `writeGlif` refuses QUADRATIC contours
    (`core-font/src/commonMain/kotlin/com/asoc/typewright/core/font/ufo/GlifCodec.kt:247`), and
    `readSfntFont` yields TrueType's quadratic contours, which the in-memory `UfoProject` holds
    as they are (for example `ui`'s `hyleDecoReferenceProject()`). The golden-path harness hit
    it in P10: its step-5 seed is degree-elevated to cubic in test code so that step measures
    compile, not import. P11's save and P12's import must choose between two fixes: write
    `qcurve` points (UFO 3 allows them, and the outline stays exactly the user's) or elevate to
    cubic on import (exact geometry, but the point structure changes, which moves the node
    counts CLAUDE.md's fixtures state). Law 1 favours keeping the source outline.

    *build (P11 + P12).*

## P11: projects are real files

126. **Owner decisions in `docs/PROJECT_MODEL.md` §14 (D1–D14 there).** P11 proceeds on each
    recommended default, and each can be changed later, except where noted:
    - **D1:** an imported font's glyphs are approved and locked on import (P12).
    - **D2:** spacing, kerning, unicodes and renaming stay allowed on a locked glyph.
    - **D3:** saving a reflection confirms a judgment-call Workbook task.
    - **D4:** navigation state lives in app config, not in the project.
    - **D5:** undo history is not kept across reopen.
    - **D6:** no OPFS crash mirror for the web's tab-only projects in V1.
    - **D7:** P16 may add a versioned `capture/` folder.
    - **D9:** the desktop folder picker runs `zenity` or `kdialog` as a subprocess, falling back
      to Swing.
    - **D10:** a locked glyph edited outside Typewright is unlocked with an "external-edit" diff
      and never reverted.
    - **D11:** UFO names drop spaces (`HyleDeco-Regular.ufo`).
    - **D12:** `build/.gitignore` is written when a project is created.
    - **D13:** lessons are stored as JSON.
    - **D14:** a SAF folder whose storage app can't rename is refused.
    - **D8 is the one that can't be undone:** the scrapbook data model, about 60 lines, moves from
      `ui` (FSL-1.1-ALv2) to `:project` (Apache-2.0). Code published under Apache stays Apache.

    *Madhav.*
