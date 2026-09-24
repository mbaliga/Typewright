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
