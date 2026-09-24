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
