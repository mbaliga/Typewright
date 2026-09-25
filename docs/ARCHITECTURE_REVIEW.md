# Architecture review (P0)

Written 23 September 2026 at the end of P0, against the scaffold on branch
`claude/build-out-feature-6i1vsl`. Inputs: `CLAUDE.md`, `TYPEWRIGHT_BUILD_BRIEF.md`,
`UI_SPEC.md`, `PROMPTS_CLAUDE_CODE.md`, `docs/TYPEWRIGHT_HANDOFF.md`, the explorer
(`ui/typewright-explorer.html`, driven in Playwright Chromium) and six research passes run in
this container. The evidence behind each claim is one of three things:

- a build or script anyone can rerun (commands in §2);
- a source URL (listed per section);
- a measurement made here. Measurements were taken on 4 shared x86 vCPUs, with Chromium running
  headless on SwiftShader, and **never on a phone**.

UNVERIFIED marks everything else. No device or emulator was available (CLAUDE.md law 4).

---

## 1. Verdict

1. **The scaffold works.** The build compiles and tests all 16 projects on the JVM, as Android
   host tests, under Node-Wasm and in headless Chrome. The ten pure modules also build with no
   Android SDK. Behaviour on a device is still unverified.
2. **The core can run on all three targets, but only if we write it ourselves** in common Kotlin:
   booleans with curve restoration, the fitter, Hobby curves, an sfnt reader and a UFO writer.
   The only libraries that do these jobs are JVM-only or native (Clipper2's Kotlin port has no
   Wasm target), so none of them can go into a pure module.
3. **The v1 compile plan cannot work on Android.** Five of fontmake's native dependencies have no
   Android wheels, and one of them runs a binary, which Android forbids. Chaquopy 17 supports AGP
   only up to 9.2 (we use 9.4.1) and cannot be applied to a KMP library module. fontc 1.0 shipped
   on 1 Sept 2026. It compiles Hyle Deco in 0.13 s natively and in 0.37 s as WASI, so it should be
   the v1 compiler on all three targets.
4. **The v1 QA plan cannot work on Android or the web.** Fontbakery needs 18 native packages.
   Fontspector, which Google now uses, runs as Wasm in a browser. The two tools disagree on Hyle
   Deco: 4 FAIL against 2.
5. **Web shaping through `FontFace` cannot work in Compose,** which paints everything into one
   canvas. Skiko's HarfBuzz shaper ships inside `skiko.wasm`. The bottleneck for shaping previews
   is compiling after every edit, not shaping.
6. **The node-economy corpus is miscounted**: closing points counted twice, composites counted as
   0, class sizes wrong. P1, the fence and the Task 4 gate are all built on it. Even with honest
   counts, Hyle Deco's properly fitted `o` is an outlier in 6 of the 10 boxes, so Task 4 can never
   pass for the reference font.
7. **The one-sheet UI works in Compose only as a camera over draw-phase passes,** not as the
   explorer's single transformed DOM layer. The explorer does not define zoom-as-depth at all, so
   P4 would have to invent it.
8. **Gestures need one arbiter.** Three [CANON] gestures collide. The explorer's radial has bugs
   that must be fixed before it is ported. A stylus is a separate hand only on Android.
9. **Kotlin/Wasm is Beta, and on the web the app breaks law 3 by default** (Compose downloads Noto
   from fonts.gstatic.com). It also goes blank under a POSIX locale, loses the stylus, has one
   thread, and can open a project directory only in Chromium. Ship the web as a labelled Beta.
10. **Law 2 should read "common-first"**, not "pure-JVM-first". Several numbers fixed in the
    CLAUDE.md fixtures, P1 and P2 are wrong or are totals where on-curve counts are meant (o 80 → 32,
    n 44 → 22, P1's box of 10 for o, the 8 + 16 circle). They need restating before P1 and P2 write
    tests against them.

### The ten biggest risks, ranked

| # | risk | why it ranks here | mitigation (section) |
|---|---|---|---|
| 1 | **The compile plan cannot run on Android or the web as written** | Blocks the compile step of M1, every shaping preview (M5) and in-app builds on 2 of 3 targets | fontc 1.0 everywhere; Chaquopy becomes a spike; the endpoint becomes optional (§4.4, §7) |
| 2 | **The corpus is miscounted** and the fence carries a minimum we invented | The app would judge users with wrong numbers labelled "measured" (law 5), and P1's tests would lock the bugs in | Fix the script, regenerate, restate the numbers before P1 (§3 `qa:corpus`, §7) |
| 3 | **QA layer one has no runtime on Android or the web**, and the choice of tool changes what blocks Ship | M0's gate is the first product | Fontspector: Wasm on web, CLI on desktop, behind an interface (§3 `qa`) |
| 4 | **Shaping previews need a compile after every edit.** `FontFace` cannot shape for Compose. TextRunShaper uses whatever HarfBuzz the OS has (2.6.4 on Android 12) and falls back to system fonts silently | M5's Devanagari preview can mislead the user or not exist at all | Two shapers, glyphs drawn from our own outlines, a separate preview font, bundled HarfBuzz for the lookup trace (§4.3) |
| 5 | **Web: Beta stack, law-3 breach by default, blank page on a POSIX locale, storage only in Chromium** | M-web's promise of "the same app" does not hold | Bundle fonts, CSP, an egress test, locale guard, OPFS + zip, Beta label (§4.5) |
| 6 | **Gaps in the one-sheet spec, and performance if the DOM is copied literally** | M2's core interaction. The explorer defines no zoom behaviour | `SheetCamera` over passes; compose only nearby rooms; a benchmark before P4 (§4.1) |
| 7 | **Gesture collisions and missing input on some platforms** | Hold, two-finger swipe and M each carry two meanings; no pinch on Linux; no stylus on the web | One root arbiter; fix the explorer first (law 6) (§4.2) |
| 8 | **Every correctness algorithm must be written in common Kotlin** | Booleans, fitter, Hobby, sfnt reader, UFO writer: large, exacting work, with nothing to borrow | Order P1a → P2 → P5a around it; port from permissively licensed references (§3, §7) |
| 9 | **Licences are unsettled** | An Apache-2.0 LICENSE sits in a repo whose licence is "undecided". 5 of the 10 lesson faces have Reserved Font Names. families.csv has no stated licence. Skiko's notices have no screen to appear on | Decisions in §6. Nothing ships until they are made |
| 10 | **The build supply chain and the missing device** | Maven Central answered 429 on 6 of 8 requests. GitHub reachability varied (Binaryen, Karma). AGP installs SDK packages silently. Each Compose upgrade raises compileSdk. CI has never run on GitHub. No phone | Fallbacks already in the build (`-Ptypewright.wasmOpt`); owner verifies on device (§2) |

---

## 2. Build facts

### Versions (all pinned in `gradle/libs.versions.toml`)

| | version | note |
|---|---|---|
| Gradle | 9.7.1 wrapper, `distributionSha256Sum` pinned | embeds Kotlin 2.4.0 for build scripts |
| JDK | 21 runs the build | all JVM and Android bytecode targets Java 17 |
| Kotlin | 2.4.20 | KMP, JVM, Compose compiler plugin |
| Compose Multiplatform | 1.12.1 | explicit `org.jetbrains.compose.{runtime,foundation,ui}` coordinates; Skiko 0.150.1 on desktop and web, androidx.compose 1.12.1 on Android. 1.13.0-alpha01 is the only newer release |
| AGP | 9.4.1 | `com.android.application`; KMP modules use `com.android.kotlin.multiplatform.library` |
| Android | compileSdk 37, targetSdk 36, minSdk 31, build-tools 36.0.0 | compileSdk 37 is forced by Compose 1.12.1's AAR metadata (`checkAarMetadata` fails at 36). targetSdk 36 is Play's requirement from 31 Aug 2026. minSdk 31 is TextRunShaper |
| ktlint | 1.8.0 via Spotless 8.10.2 | wired into `check` |
| Wasm tooling | Node.js 26.2.0 (from nodejs.org), npm instead of Yarn (`kotlin.js.yarn=false`), Binaryen version_130 | |
| CI | `actions/checkout@v7`, `setup-java@v6`, `gradle/actions/setup-gradle@v6` with `cache-provider: basic` | **never run on GitHub Actions** |

### Targets

- **Pure modules**, `typewright.kmp.pure`: `jvm()` and `wasmJs { nodejs() }`, no Android
  target, so an `android.*` import cannot compile. The ten are `core-geometry`, `core-font`,
  `engine-trace`, `engine-construct`, `qa`, `qa:corpus`, `learn`, `learn:scenes`, `campaign`
  and `scripts`.
- **Platform modules**, `typewright.kmp.platform`: `android`, `jvm("desktop")` and
  `wasmJs { browser }`. The three are `compile`, `shape-preview` and `ui`.
- **Apps**: `app-android`, `app-desktop` and `app-web`.
- `build-logic/convention` is an included build, not a module in `settings.gradle.kts`. It holds
  the conventions `typewright.lint`, `typewright.kmp.pure` and `typewright.kmp.platform`.
- `-Ptypewright.android=false` leaves every Android-bearing project out of the build.

### Verified (P0a scaffold runs, 22–23 Sept 2026; reproduce with the commands shown)

| command | result |
|---|---|
| `ANDROID_HOME=/opt/android-sdk CHROME_BIN=<chromium> ./gradlew check --rerun-tasks` | BUILD SUCCESSFUL: 443 tasks, 33 test executions, 0 failures. Covered: jvmTest in 10 pure modules; wasmJsNodeTest in 12 modules; desktopTest in compile, shape-preview and ui (the screenshot harness is in ui); Android host tests; app-android unit test; app-desktop test; app-web in headless Chrome via Karma; spotlessCheck; Android lint |
| `env -u ANDROID_HOME -u ANDROID_SDK_ROOT ./gradlew -Ptypewright.android=false jvmTest spotlessCheck --rerun-tasks` | SUCCESSFUL, 11 JVM tests. Without the property, all 16 projects still configure with no SDK (AGP 9.4.1) |
| `./gradlew -Ptypewright.android=false wasmJsNodeTest spotlessCheck --rerun-tasks` (SDK unset) | SUCCESSFUL. `kotlinWasmStorePackageLock` is skipped by design, and the committed lock is unchanged (`sha256sum -c`) |
| `./gradlew :app-android:assembleDebug` | `app-android-debug.apk`, **23,647,702 bytes** (rechecked with `ls` 23 Sept). aapt2: `dev.aarso.typewright`, SDK 37/31/36. `core-geometry` resolves to its `jvmRuntimeElements` variant |
| `./gradlew :ui:desktopTest --rerun` | `ImageComposeScene` renders `ui/build/screenshots/placeholder.png` (720×480 at density 2); the test asserts on paper and ink pixels |
| `Xvfb :99 & DISPLAY=:99 ./gradlew :app-desktop:run` | The window rendered (`app-desktop/build/screenshots/desktop-run-xvfb.png`); the run was then killed on purpose |
| `./gradlew :app-web:wasmJsBrowserDistribution` | See the size table below. Binaryen came from GitHub in this session, although the environment notes said GitHub is blocked |
| the same with `-Ptypewright.wasmOpt=<npm binaryen@130.0.0>/bin/wasm-opt` | Output byte-identical to the native wasm-opt build (sha256 `e78068cf…` app, `089052ba…` Skiko). The npm wasm-opt took 157 s against 34 s natively |
| Playwright Chromium on `python3 -m http.server` | With `navigator.language` = `en-US@posix` (LANG unset), the page is blank: `RangeError: Incorrect locale information provided` in `parseLanguageTagToIntlLocale`. With `en-GB` it renders, in Compose's bundled sans: Wasm has no system monospace |

Sizes of the web distribution. Raw and gzip -9 were measured on 23 Sept; brotli 11 comes from the
Wasm research pass.

| file | raw | gzip -9 | brotli 11 |
|---|---|---|---|
| `skiko.wasm` | 8,640,316 | 3,328,940 | 2,622,783 |
| app `.wasm` (wasm-opt) | 1,654,568 | 557,482 | 440,442 |
| app `.wasm` (no wasm-opt) | 6,259,856 | — | 1,208,385 |
| `typewright.js` | 327,889 | 57,962 | 46,460 |

Time from navigation to the first Compose frame, measured in headless Chromium on SwiftShader.
**These figures are not from a phone.**

| conditions | first frame |
|---|---|
| localhost | 0.55 s |
| 4× CPU throttle, brotli | 2.3 s |
| 4× CPU, brotli, 9 Mbps / 60 ms latency | 4.9 s |
| the same network, served uncompressed | 11.5 s |

Fixture facts, rechecked with fontTools 4.65 on 23 Sept:
- `fonts/HyleDeco-Regular.ttf` has 338 glyphs: 25,991 on-curve points and 0 off-curve.
- T has 1,763 on-curve points in **1** contour, o has 80 in 2 contours (46 + 34), n has 44 in 1,
  and H has 1,252 in 1.
- Corpus double count, Poppins-Regular: `glyf` flags against the JSON give o 8 vs 10, n 12 vs 13
  and e 12 vs 14.
- Corpus data: serif-didone holds **15** families, blackletter's o box has n = **15**, grotesque's
  o box has n = **29**, and the geometric o box runs min **0**, Q1 = median = Q3 = 10, max 100.

### Environment risks found while building

- **Maven Central rate-limits this container.** It answered Cloudflare 429 on 6 of 8 sequential
  requests. The machine-local workaround has been removed, so any prompt that adds dependencies
  will hit the 429s again.
- **GitHub reachability varies.** Binaryen's release asset answered 200 in this session. Karma
  comes from `github:Kotlin/karma#6.4.5`, so `app-web`'s browser test breaks wherever github.com
  is blocked. `-Ptypewright.wasmOpt` covers Binaryen; nothing covers Karma.
- **AGP 9.4.1 installs SDK packages silently** when licences are accepted: platforms 36, 37.0 and
  build-tools 36.0.0. A machine that is offline or has not accepted the licences fails at the
  first Android build.
- **Spotless and the configuration cache ignored edits to `.editorconfig`.** This is fixed by
  making the file a configuration input (`LintConventionPlugin`).
- **KGP writes build statistics to `~/.gradle/kotlin-profile`.** The only switch is internal
  (OPEN_QUESTIONS 15).

### Not verified here

- **On a device:** installing the APK; any gesture, haptic or stylus behaviour; TextRunShaper;
  Chaquopy; frame times.
- **On other platforms and browsers:** Firefox, Safari or any iOS browser; any phone browser; a
  Wacom pen on Linux; Devanagari, kana and Arabic IMEs on the web.
- **Tasks not run:** `packageDeb`, `packageRpm` and `packageAppImage`;
  `wasmJsBrowserDevelopmentRun`; any GitHub Actions run.
- **Toolchain claims:** that Skiko's shaper runs at runtime on Wasm (it is exported, not
  exercised); that fontc links for Android (`cargo check` passes, no NDK here); fontc's JNI size.

---

## 3. Per module

Every project in `settings.gradle.kts` is covered below. Each entry gives three things:
- **Risks**: the three riskiest technical decisions.
- **Pick**: the FOSS dependency we would choose, with its licence, and where that licence was read.
- **Will not work**: where the plan fails on Android, JVM desktop on Linux, or Kotlin/Wasm.

The rule behind every pick is the same. Pure modules build for the JVM and Wasm, so every
dependency they take must publish a `wasmJs` variant. JVM-only jars (Clipper2-java, FontBox,
SnakeYAML) and native code (OpenCV, VTracer, HarfBuzz, Python) can only go in platform modules,
behind an interface.

### `:core-geometry` (pure)

**Risks**
1. **Booleans on cubic contours without flattening.**
   - Clipper2 (BSL-1.0) works on polygons only.
   - Its Kotlin port, `urbanistic/clipper2-kotlin`, targets JVM, JS and native, has no Wasm
     target, and is "NOT available as Maven/Gradle artifact".
   - Without curve restoration, it brings back the polygon glyphs (the 1,763-point T) that
     Typewright exists to remove.
   - booleanOperations (MIT), fontmake's default, restores curves:
     - it flattens segments into steps of about 5.3 units;
     - scales by 2^17 and clips with pyclipper;
     - maps each output point back to its source segment by t and re-splits the original cubics.
2. **Offsetting and stroking cubics, and the fitter itself (P2).**
   - Offsets must handle cusps and loops.
   - Schneider fitting must be written from the article: the Graphics Gems code is under a
     non-OSI EULA.
   - Extrema must be forced onto the curve.
3. **Determinism across the JVM and Wasm, and where cleanup lives.**
   - Rounding to integers can break tangent continuity, and float results can drift between
     targets.
   - Overlap removal and point cleanup belong here, visible to the user, and never in the
     compiler. fontmake's default overlap removal turns T 1,763 into 10 and H 1,252 into 20, so a
     fixture can pass by accident.

**Pick**
- Build it ourselves.
- Port Clipper2's core (BSL-1.0, file) and add booleanOperations-style curve restoration (MIT,
  file).
- Port offsets and fitting from kurbo (Apache-2.0 OR MIT, file): `offset_cubic`, `stroke`,
  `fit_to_bezpath_opt`.
- Use Compose's `Path.op` (Skia PathOps; skia-pathops is BSD-3-Clause) **only as a test
  oracle**. It works in Float32, and Android uses its own Skia, so results can differ by target.

**Will not work:** nothing on any target, provided the module stays pure common Kotlin. Golden
tests should compare rounded integers on both the JVM and Wasm.

### `:core-font` (pure; api core-geometry)

**Risks**
1. **A lossless UFO 3 round-trip** (lib keys, identifiers, glif file-name rules). Law 7 says
   "FontForge, Glyphs or RoboFont", but Glyphs and RoboFont are macOS-only. On Linux, only ufoLib
   and FontForge can check our files.
2. **A binary font reader in common Kotlin, with no `java.*`** (no `javax.xml`, no
   `java.util.zip`).
   - M0 needs it for fonts the user brings. It must read glyf, loca, cmap 4/12, hmtx, name, OS/2
     and post, plus GDEF/GPOS mark and pair lookups for the checks.
   - The shaping plan (§4.3) later needs a *writer* for a preview font: cmap, hmtx, GDEF, GSUB and
     GPOS.
3. **`.fea` that nothing in-process can compile on Android or the web in v1.**
   - The feature model must be structured, not text, so that Indic feature generation, the
     preview writer and `.fea` export share one model.
   - `gftools` now treats `upstream.yaml` as legacy: it merges the file into `METADATA.pb` and
     deletes it.

**Pick**
- `io.github.pdvrieze.xmlutil:core` 1.0.2 (Apache-2.0, POM) for reading, with a hand-written,
  byte-stable writer. It forces kotlinx-serialization-core 1.11.0; Compose currently resolves
  1.7.3.
- Build the sfnt reader ourselves, with read-fonts (MIT OR Apache-2.0) as the reference.
- No CFF in v1: google/fonts ships only TTF.

**Will not work**
- **Web:** a project folder opens only in Chromium (`showDirectoryPicker`: Chrome 86, Chrome
  Android 132; none in Firefox or Safari). Elsewhere the project lives in OPFS and moves as a zip.
  kotlinx-io's and okio's file systems need Node, not a browser.
- **Android:** per-file speed through the Storage Access Framework is UNVERIFIED.

### `:engine-trace` (pure; api core-geometry)

**Risks**
1. **OpenCV against law 2.** P3 puts OpenCV "in engine-trace on Android".
   - The `org.opencv:opencv` 4.12.0 AAR is 117.7 MB. Its arm64 `libopencv_java4.so` is 23.5 MB
     (9.3 MB compressed); in 5.0.0 it is 33.5 MB.
   - There is no official desktop jar and no Wasm build.
2. **Sub-pixel contour extraction and corner detection.** These are the product. Their speed on a
   12 MP image is UNVERIFIED on ART, and on single-threaded Wasm (see §4.5) the work must run in a
   Worker.
3. **P3 depends on template fiducials that do not exist.** `scripts/templates/hyle-all-templates.zip`
   holds 406 entries, all per-glyph SVGs: no multi-glyph sheets and no fiducials. 49 of them are
   Nastaliq, which is out of scope for v1.

**Pick**
- Build it ourselves in common Kotlin: integral-image adaptive threshold, connected components,
  distance transform, marching squares, homography.
- Use VTracer (MIT, file) only as a benchmark.
- Put camera code in a separate Android-only module that feeds rasters into the pure chain
  (OPEN_QUESTIONS 6).

**Will not work**
- **Android:** camera or OpenCV code inside this module.
- **Wasm:** OpenCV; camera capture has not been designed (UNVERIFIED).
- **Linux:** OpenCV has no official artifact; the third-party `org.bytedeco:opencv` jar for
  linux-x86_64 is 31.9 MB.

### `:engine-construct` (pure; api core-geometry)

**Risks**
1. **Where live parameters are stored.** They go in UFO `lib` keys and must survive a round-trip
   through other editors (law 7) until the user bakes them.
2. **Curve-level booleans.** This module depends on core-geometry's port (above).
3. **The spline choice.**
   - Spiro splits any segment that bends 1 rad or more (`if (depth > 5 || bend < 1.)`), so a
     quarter-circle becomes two cubics.
   - Neither Spiro nor Hobby puts extrema on-curve unless knots are placed there with a direction.
   - Hobby's method (Hobby 1986; Jackowski, TUGboat 34:2) needs one tridiagonal solve and gives
     one cubic per pair of points.

**Pick**
- Hobby first.
- Spiro ported from raphlinus/spiro (Apache-2.0 OR MIT, file; the patents "passed into the
  public domain").
- **Never** libspiro, which is GPL-3.0.

**Will not work:** nothing specific to any target.

### `:qa` (pure; api core-font, qa:corpus)

**Risks**
1. **Where layer one runs.**
   - `fontbakery[googlefonts]` 1.1.0 installs 70 packages into a 178 MB venv. 18 of them carry
     native code, plus two bundled executables (`ots-sanitize`, `tx`). None has an Android wheel.
   - Fontspector 1.8.0 (Apache-2.0) ships an official Wasm build: 14.4 MB raw, 4.4 MB gzip. It
     checked Hyle Deco in 0.8 s after a 1.0 s start in Chromium. Fontbakery with
     `--skip-network` took 6 s.
   - **The two disagree.** Fontbakery offline gives 2 FAIL, 9 WARN and 116 SKIP; it skips
     `shape_languages` and `soft_dotted` for lack of network. Fontspector gives 4 FAIL, including
     `googlefonts/name/mandatory_entries`.
2. **One geometry for quadratic TTF and cubic UFO.** Checks must measure the UFO and never the
   compiled binary, because the compiler's overlap pass changes the counts.
3. **Plain-language text keyed to check IDs that change** between tools and versions.
   Fontbakery hides the ID inside a repr string (`"<FontBakeryCheck:…>"`); Fontspector gives
   `check_id`. Fontspector's CLI JSON shape is UNVERIFIED.

**Pick**
- Fontspector (Apache-2.0, file): Wasm on the web, CLI on desktop.
- Fontbakery (Apache-2.0) as an optional desktop extra.
- The bridge is an interface in `qa`, with actuals in a platform module (OPEN_QUESTIONS 7).

**Will not work**
- **Android:** no binary of either tool. Fontspector's Wasm build inside a WebView, or a
  cargo-ndk build, is UNVERIFIED.
- **Linux:** Fontspector is not on PyPI. It comes from GitHub releases (blocked here) or
  `cargo install fontspector`, which needs `protoc`.
- **Wasm:** Fontbakery; Fontspector's Wasm build does work.

### `:qa:corpus` (pure; resources from `data/`)

**Risks**
1. **Counting bugs in `data/scripts/build_node_economy_corpus.py`.**
   - `count_points` counts the `moveTo` point, then counts it again as the end of a closing
     `qCurveTo`, adding +1 to every contour that closes with a curve. The geometric o box is
     really 8, not 10.
   - The script ignores `addComponent`, so composites count as 0: 261 of 15,354 entries are 0,
     and the geometric o box has min 0.
2. **Class sizes and ranking.**
   - The real sizes are didone 15, blackletter 15 and grotesque 29.
   - At the top-30 cut, ties are broken by reverse-alphabetical family name.
   - "Superfamily" means the first word of the name, which kept Playfair Display SC and dropped
     Playfair Display.
   - Display faces set the whiskers: Fredericka the Great, filed under Didone, has T 680.
   - The fonts are fetched from unpinned `main`, and `families.csv` has no stated licence.
3. **The fence and the loader.**
   - The minimum of 0.25·Q3 is ours. It is the deciding term in 27 of the 62 geometric boxes.
   - `verdict()` in the explorer returns `'fail'`.
   - Wasm has no classpath, so the pack must be embedded or fetched (OPEN_QUESTIONS 8).

**Pick**
- fontTools (MIT) as the generator.
- Ship only derived statistics, and record the source commit.
- Load with kotlinx-serialization-json 1.11.0 (Apache-2.0; `wasmJs` since 1.6.1).

**Will not work:** Wasm resource loading, as above. Otherwise nothing target-specific.

### `:learn` (pure; api core-font)

**Risks**
1. **"Fonts from the device".** `queryLocalFonts` exists only in desktop Chrome, and Wasm pages
   have no system fonts.
2. **Fetching Google Fonts for comparison**, a network call the user asks for (law 3). The
   webfonts API needs a key we do not have. Raw files from the google/fonts repository need none.
3. **Where the scrapbook lives.** On the web it has to be OPFS or a zip, not a directory
   (law 7).

**Pick:** draw `core-font` outlines as Compose Paths, never as `Text`.

**Will not work:** on the web, device fonts and a real directory.

### `:learn:scenes` (pure; api learn)

**Risks**
1. **YAML at runtime.** kaml's README calls its Wasm support "highly experimental … may be
   removed".
2. **Reserved Font Names on subset fonts.**
   - 5 of the 10 exemplars have RFNs: Libre Baskerville, Playfair Display, Limelight, Source Sans 3
     ("Source") and UnifrakturMaguntia.
   - OFL FAQ 2.6 counts subsetting as modification, so the subsets must be renamed or the whole
     fonts shipped.
   - The corpus also includes Ubuntu Mono, which is UFL-1.0.
3. **The web bundle, and faces outside the corpus.** Each lesson face adds to the web download.
   6 of the 10 lesson faces are not in their class corpus, contradicting brief §9.

**Pick:** write scenes in YAML and convert them to JSON at build time. Load them with
kotlinx-serialization-json (Apache-2.0).

**Will not work:** nothing target-specific once the data is JSON.

### `:campaign` (pure; api qa, learn:scenes)

**Risks**
1. **Gates on boxes that are small or cannot be passed.**
   - display-artdeco has n = 8.
   - The Task 4 gate can never pass for Hyle Deco's properly fitted o: 16 on-curve against a
     geometric Q3 of 10 and fence of 12.5, which drops to 10 once the counts are fixed.
2. **SCAFFOLD content** until the Domestika material arrives.
3. **Migrating `typewright.json`** as its schema changes.

**Pick:** build it ourselves, with kotlinx-serialization-json.

**Will not work:** nothing target-specific.

### `:scripts` (pure; api core-font)

**Risks**
1. **Generated Indic and Arabic features, with no in-process shaper to check them.**
   - HarfBuzz reorders by the old spec unless the font declares `dev2` (`is_old_spec` in
     `hb-ot-shaper-indic.cc`), so we must always emit `languagesystem dev2`.
   - HarfBuzz's fallback mark positioning and Arabic fallback shaping can make an incomplete font
     look right in the preview while it breaks in CoreText.
2. **Glyph naming.** fontmake renames glyphs to production names, so the map from glyph ID to
   outline must come from the build.
3. **Capture templates.** They have no fiducials and no sheet layout, and include Nastaliq (see
   `engine-trace`).

**Pick:** googlefonts/glyphsets and gflanguages (Apache-2.0, file).

**Will not work:** on the web, a preview has to wait for a compile until fontc-WASI lands
(§4.3).

### `:compile` (platform: android, desktop, wasmJs)

**Risks**
1. **Python on Android.** fontmake cannot be installed under Chaquopy 17.0.0 (2025-12-01):
   - **None of pyclipper, unicodedata2, openstep-plist, compreffor or cffsubr** has an Android
     wheel, in Chaquopy's index or on PyPI.
   - cffsubr runs its bundled ELF `tx` via `subprocess`, and apps targeting API 29+ "cannot invoke
     `execve()` … within the app's home directory".
   - Chaquopy 17 supports **AGP 7.3–9.2**; we use 9.4.1.
   - Chaquopy applies only to `com.android.application` or `com.android.library` (docs, read
     23 Sept). This module uses `com.android.kotlin.multiplatform.library`.
   - The runtime costs about 12–15 MB per ABI.
2. **System Python on Linux.** fontmake 3.12.1, ufo2ft 3.9 and glyphsLib 6.14 need Python
   ≥ 3.10, which excludes Ubuntu 20.04, Debian 11 and RHEL 8's default Python.
   - `python3 -m venv` needs the separate `python3-venv` package, and PEP 668 blocks pip from
     installing into the system Python.
   - Bootstrapping a private venv is a download the user must start (law 3).
3. **A hosted endpoint against "no backend of ours"** (law 3; OPEN_QUESTIONS 10).
   - Related: `CompileOptions.autohint = true` assumes ttfautohint, and `ttfautohint-py` has no
     Android wheel.
   - Whether fontc autohints is UNVERIFIED.
   - fontc 1.0 has no overlap-removal option.

**Pick:** fontc 1.0.0 (released 2026-09-01; the repository LICENSE is Apache-2.0, the crate
metadata says "MIT/Apache-2.0").
- **Measured compile times:**
  - Hyle Deco: native 0.13 s, against fontmake's 2.4 s for TTF and 3.2 s for OTF.
  - Noto Sans Devanagari: 0.16 s, against fontmake's 8.7 s.
  - As `wasm32-wasip1` under Node's WASI: 0.37 s, with every table identical to the native
    output except the `head` timestamps.
- **Coverage:** fontc_crater (2026-09-22) found 2,227 of 3,450 GF targets identical to fontmake
  (64.6%).
- **Routes per target:**
  - Android: JNI via cargo-ndk. `cargo check` passes for `aarch64-linux-android`; linking is
    UNVERIFIED.
  - Desktop: a bundled binary (21.6 MB).
  - Web: WASI in a Web Worker, with an in-memory filesystem shim. The library API reads UFOs
    from paths (fontc issue #1215).
- **fontmake 3.12.1** (Apache-2.0) stays as a desktop option when Python is present, and as the
  reference build in the generated repository's CI via `gftools builder`.
- Pin `SOURCE_DATE_EPOCH` so builds are reproducible.

**Will not work**
- **Android:** the Chaquopy/fontmake route as written, and OTF output via cffsubr.
- **Linux:** without Python ≥ 3.10 and `python3-venv`. A Flatpak sandbox would not see the host
  Python (UNVERIFIED).
- **Wasm:** Python of any kind.

### `:shape-preview` (platform)

**Risks**
1. **The stacks.**
   - **`FontFace` cannot work:** Compose paints into one canvas, and `FontFace` affects only DOM
     text.
   - **TextRunShaper (API 31)** gives no clusters and uses the OS's HarfBuzz: 2.6.4 on Android
     12, 3.1.1 on 13, 6.0.0 on 14, 8.2.2 on 15, 10.2.0 on 16.
   - `CustomFallbackBuilder` cannot switch off system fallback, so a syllable the user has not
     drawn can render in Noto without warning.
2. **Every preview needs a compiled font** with the user's GSUB and GPOS.
   - fontmake takes 4.2–8.7 s per compile; fontc takes 0.16 s.
   - The web v1 has no local compiler.
3. **Teaching a conjunct needs HarfBuzz's lookup trace** (`hb_buffer_set_message_func`), which
   no platform API exposes.
   - Clusters do not help: after reordering, one Indic cluster is the whole syllable (र्कि gives
     3 glyphs, all in cluster 0).

**Pick**
- Skiko's `org.jetbrains.skia.shaper.Shaper` + `FontMgr.makeFromData` in a `skikoMain` source
  set shared by desktop and wasmJs (HarfBuzz is "Old MIT", Skia BSD-3-Clause). Its `RunHandler`
  gives clusters.
- TextRunShaper on Android.
- For the trace, bundled HarfBuzz:
  - harfbuzzjs 1.6.2 on the web (MIT; HarfBuzz 14.5.0; 434 KB wasm, 174 KB gzip);
  - uharfbuzz 0.56.2 on desktop (Apache-2.0);
  - libharfbuzz via the NDK and JNI on Android.

**Will not work**
- **Android below 12:** no glyph-level API.
- **Wasm:** `FontFace`. Skiko's shaper at runtime on Wasm is UNVERIFIED; it is exported
  (`Shaper__1nShape`, `RunHandler_nGetClusters`) but has not been run.

### `:ui` (platform; Compose)

**Risks**
1. **The sheet built as one transformed layer.** §4.1 explains why it has to be a camera over
   draw passes instead.
2. **Gesture arbitration and three input models,** where a stylus exists only on Android (§4.2).
3. **Fonts.**
   - Wasm has no system fonts.
   - Since CMP 1.12.0, Compose on the web downloads Noto from `https://fonts.gstatic.com/s/` for
     any code point that no loaded font covers. `defaultFallbackFontDownloader` is `internal`,
     with no public switch, and when blocked it retries forever with a back-off of 5 s × n.
   - The user's letters must be drawn as Paths, not `Text`. Compose desktop caches typefaces by
     identity string, so reusing an identity after a recompile shows the old font.

**Pick**
- Compose Multiplatform 1.12.1 (Apache-2.0).
- Inter (OFL-1.1, no RFN, 876,576 B) and JetBrains Mono (OFL-1.1, no RFN, 187 KB).
- **Not** IBM Plex Mono: it has the RFN "Plex".

**Will not work**
- **Linux:** no multi-touch, pinch or stylus identity. Without OpenGL, Skiko falls back to
  software rendering; one report measured 5–10 fps at 1080p (Windows, CMP 1.6). Reduced motion
  needs app code.
- **Wasm:** a pen arrives as `PointerType.Touch` with empty buttons. There is no `aria-live`,
  and IMEs are UNVERIFIED.
- **Android:** the barrel button is folded into `isPrimaryPressed`, and Compose never reads
  `FLAG_CANCELED`, so palm rejection is our code.

### `:app-android`

**Risks**
1. **APK size.**
   - The debug placeholder is already 23.6 MB, with no R8.
   - OpenCV would add 9.3 MB compressed on arm64, and Chaquopy 12–15 MB per ABI.
   - The size of a fontc JNI library is UNVERIFIED.
   - Every native library must support 16 KB pages. Play blocks updates without it from
     1 Feb 2027 for apps targeting API 35+ (checked on developer.android.com). Chaquopy 17's own
     notes say many wheels still fail on 16 KB devices.
2. **No device (law 4).** Every gesture, haptic, stylus and performance claim needs the owner to
   verify it on a device.
3. **SDK levels.**
   - minSdk 31 is needed only for glyph-level data. Plain `Text` rendering is shaped by HarfBuzz
     on older APIs too (OPEN_QUESTIONS 4).
   - compileSdk 37 moves with every Compose upgrade (OPEN_QUESTIONS 12).

**Pick:** leave out OpenCV and Chaquopy.

**Will not work:** anything on a device can be verified here.

### `:app-desktop`

**Risks**
1. **Finding the toolchain.** Prefer a bundled fontc; finding a system Python is fragile.
2. **Linux packaging.** Deb, Rpm and AppImage are declared but have never been run.
3. **Licence notices and QA tooling.** Skiko's `libskiko-linux-x64.so` (29.6 MB) embeds Skia,
   HarfBuzz, ICU, FreeType, libpng, zlib, Expat, libjpeg-turbo and libwebp. Their notices need a
   licences screen that the explorer does not have (OPEN_QUESTIONS 14). Fontspector binaries come
   from GitHub releases.

**Pick:** fontc (Apache-2.0) and Fontspector (Apache-2.0) as bundled binaries per OS.

**Will not work:** hardware rendering without OpenGL (the software fallback), and pinch.

### `:app-web`

**Risks**
1. **Beta foundations.**
   - Kotlin/Wasm and Compose for web are both Beta.
   - The browser floor is set by WasmGC: Chrome/Edge 119, Firefox 120 and Safari **18.2**, which
     covers every iOS browser.
   - The first load is about 3.1 MB brotli.
   - The page is blank on `en-US@posix`.
2. **Storage.** Project folders work only in Chromium. `createWritable` for OPFS arrived in
   Safari 26. Safari's ITP deletes script-writable storage after 7 days without interaction;
   whether that covers OPFS is UNVERIFIED.
3. **Law 3.** The hosted endpoint has no owner, and Compose falls back to fetching fonts from
   fonts.gstatic.com.

**Pick**
- fontspector-web (Apache-2.0).
- fontc as `wasm32-wasip1` in a Worker (Apache-2.0).
- `binaryen@130.0.0` from npm (Apache-2.0) as the `wasm-opt` fallback.

**Will not work:** stylus semantics, a disk directory outside Chromium, Python, OpenCV,
screen-reader announcements, haptics on iOS, and iOS below 18.2.

**Sources for §3:**
- **Libraries and ports:**
  - https://raw.githubusercontent.com/pdvrieze/xmlutil/master/README.md
  - https://maven-central.storage-download.googleapis.com/maven2/io/github/pdvrieze/xmlutil/core/1.0.2/core-1.0.2.pom
  - https://maven-central.storage-download.googleapis.com/maven2/com/charleskorn/kaml/kaml/0.104.0/kaml-0.104.0.module
  - https://github.com/urbanistic/clipper2-kotlin
  - https://raw.githubusercontent.com/AngusJohnson/Clipper2/main/LICENSE
- **Booleans, curves and fitting:**
  - https://raw.githubusercontent.com/typemytype/booleanOperations/master/Lib/booleanOperations/flatten.py
  - https://raw.githubusercontent.com/fonttools/skia-pathops/main/LICENSE
  - https://raw.githubusercontent.com/JetBrains/compose-multiplatform-core/jb-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/Path.kt
  - https://raw.githubusercontent.com/raphlinus/spiro/master/ppedit/spiro.c
  - https://raw.githubusercontent.com/fontforge/libspiro/master/COPYING
  - https://tug.org/TUGboat/tb34-2/tb107jackowski.pdf
  - https://raw.githubusercontent.com/erich666/GraphicsGems/master/LICENSE.md
  - https://raw.githubusercontent.com/linebender/kurbo/main/kurbo/src/offset.rs
- **Capture:**
  - https://maven-central.storage-download.googleapis.com/maven2/org/opencv/opencv/4.12.0/opencv-4.12.0.aar
  - https://raw.githubusercontent.com/visioncortex/vtracer/master/LICENSE
- **Chaquopy and Android:**
  - https://chaquo.com/pypi-13.1/
  - https://chaquo.com/chaquopy/doc/current/versions.html
  - https://chaquo.com/chaquopy/doc/current/android.html
  - https://developer.android.com/about/versions/10/behavior-changes-10
  - https://developer.android.com/guide/practices/page-sizes
- **Compilers and QA tools:**
  - https://pypi.org/pypi/fontbakery/json
  - https://raw.githubusercontent.com/fonttools/fontspector/main/INSTALLATION.md
  - https://pypi.org/pypi/fontc/json
  - https://crates.io/api/v1/crates/fontc
  - https://github.com/googlefonts/fontc/issues/1215
  - https://googlefonts.github.io/fontc_crater/
  - https://peps.python.org/pep-0668/
- **Shaping and fonts:**
  - https://raw.githubusercontent.com/harfbuzz/harfbuzz/main/COPYING
  - https://raw.githubusercontent.com/harfbuzz/harfbuzzjs/main/LICENSE
  - https://raw.githubusercontent.com/rsms/inter/master/LICENSE.txt
  - https://raw.githubusercontent.com/google/fonts/main/README.md
  - https://openfontlicense.org/ofl-faq/
  - https://raw.githubusercontent.com/googlefonts/glyphsets/main/LICENSE
- **Pipeline:**
  - https://github.com/googlefonts/gftools/blob/main/Lib/gftools/packager/__init__.py
- **Browser support:**
  - https://raw.githubusercontent.com/mdn/browser-compat-data/main/api/Window.json

---

## 4. The five reviews

### 4.1 The one-sheet UI as a single transformed layer

**What the explorer actually does**
- `.world` is a DOM strip 300% wide holding **three** rooms (Draw, Space, Learn). CSS
  `translateX()` moves it, with a 0.6 s `cubic-bezier(.2,.8,.2,1)` transition.
- Each room stacks an SVG lines layer (z 0) and an SVG ink layer (z 2).
- `.bloom` (z 1) sits *inside* the strip, counter-translated by the same transition.
- `.glass` (z 5) is `pointer-events:none`, with `auto` on its children.
- The map is `translate(..) scale(.3)`, with the bloom's opacity set to 0.
- **It has no** pinch, wheel zoom, two-finger pan, node drag, or glyph → word → specimen depth.
  Capture, Trace, Economy, Check, Ship and Home are separate screens, not regions of the sheet.

**What carries over to Compose, and what does not**
- **The counter-translation is a CSS artefact.** CSS can interleave z-indexes only inside the
  transformed stacking context, which is why the bloom has to move with the strip.
  `Modifier.zIndex` orders siblings of one parent. The Compose shape is:
  - world lines pass (camera) → bloom (screen space, never transformed) → world ink pass
    (camera) → glass.
  - The bloom needs no animation and cannot drift from the glass.
- **Read the camera late, during layer update or draw, not in composition.**
  - `graphicsLayer { translationX; scaleX; transformOrigin = TransformOrigin(0f, 0f) }` updates
    "without triggering recomposition and relayout". The default origin is `Center`; the
    explorer's is `0 0`.
  - `Modifier.offset { }` moves in whole pixels and cannot scale.
  - A `Canvas`/`drawBehind` that reads the camera skips both composition and layout.
- **One scaled layer is wrong for an editor.**
  - Node squares, handles, hairlines and labels would grow with the glyph.
  - Draw outlines under `withTransform { translate(); scale() }` over cached `Path`s, rebuilt only
    when a glyph's revision changes.
  - Draw nodes, handles, metric lines and labels in screen space.
  - `Stroke.HairlineWidth` is one *pixel*; the spec's 1 dp line is `1.dp.toPx()`.
- **One world-sized layout node throws.** `Constraints` packs width and height into 18/13 or
  16/15 bits, so a node that large fails with "Can't represent a width … in Constraints" (7 ×
  5,120 by 3 × 2,880 px on a 2× 5K window). Move the camera, not the layout. The pan is
  `tween(600, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))`.
- **A `Box` of seven rooms composes, measures and records all seven, even off screen.**
  - Compose only the current room and its neighbours.
  - Draw the map from thumbnails (`rememberGraphicsLayer()` + `toImageBitmap()`), not by
    re-rasterising the ~100-row Economy and Check lists at a new scale every frame of a 600 ms
    fly.
- **The map's "room contents at 55%".**
  - Through `graphicsLayer(alpha=…)` with `CompositingStrategy.Auto`, this allocates an offscreen
    buffer several screens wide, clipped to the layer's bounds.
  - Use `ModulateAlpha` per room instead.
- **Text under scale.** All three targets draw text through Skia. Text drawn under a scale stays
  sharp at 0.3, but every animation frame fills the glyph cache again. The user's letters are
  uncompiled UFO and must be drawn as Paths.
- **Gradients.**
  - `Brush.radialGradient` is circular, so the inspector's `ellipse closest-side` needs a
    `scale()`.
  - Skia interpolates unpremultiplied, while CSS interpolates premultiplied, so a stop at
    `Color.Transparent` gives a grey halo. End on `canvas.copy(alpha = 0f)` instead.
  - The four zones cover about 0.8 of a 360×780 dp screen. That is nothing for a GPU but costly
    on Skiko's software fallback, so record it once and redraw it only when the puck or the
    texture changes.
  - An imported texture needs a `BlendMode.DstOut` mask on the grid-and-lines pass, not a wash.

**Hit testing on the glass**
- Compose hit-tests siblings from the top down and stops at the first one with a hit (unless a
  node overrides `sharePointerInputWithSiblings()`).
- A layout with no pointer modifier is transparent. So the glass container must carry none,
  which is the `pointer-events:none` equivalent.
- A glass element that does not consume an event still blocks the world beneath it.
- Composables inside a `graphicsLayer` are hit-tested through its inverse matrix automatically.
  Geometry drawn on a Canvas is not, so the camera needs `screenToFont()`.

**What will not work, by target**
- **Android:**
  - The sheet works.
  - Trackpad pinch arrives as mouse scale events (`isTrackpadPinchReinterpretationEnabled`, on
    since Compose UI 1.12.0-beta02).
  - The system animator scale reaches `MotionDurationScale`, so reduced motion is automatic.
- **Linux desktop:**
  - No multi-touch. JetBrains: "pinch-to-zoom cannot be implemented".
  - AWT delivers no trackpad pinch, and a two-finger swipe arrives as scroll.
  - The software rendering fallback applies.
  - Reduced motion needs app code.
- **Wasm:**
  - Skia draws one WebGL2 `<canvas>`, and the app cannot start without WebGL2.
  - `detectTransformGestures` has worked since CMP 1.8.0.
  - The fix that lets unconsumed pinches reach the browser is unreleased (merged 22 Sept 2026).
  - `prefers-reduced-motion` needs JS interop.

**Risk:** medium-high for M2. Performance is fine if the camera is read during draw, over cached
paths with culling. It is poor if P4 copies the DOM literally: seven live rooms in one scaled
layer with alpha. The bigger risk is the gap in the spec.

**Recommendation**
1. **A camera in common code.** `SheetCamera(offset, zoom)` in commonMain, with `worldToScreen`
   and `screenToFont`.
2. **One pass order.** `Box { WorldPass(lines); Bloom(); WorldPass(ink); Glass() }`, composing
   only the current room and its neighbours.
3. **Overlays in screen space, and a map made of thumbnails.**
4. **A benchmark scene before P4.** All of Hyle Deco (338 glyphs, 25,991 points) at map zoom
   through a 600 ms fly, measured on desktop software rendering and on the web. Device numbers
   are for the owner to verify.
5. **Settle the open design questions in the explorer first (law 6):** depth, the two-finger
   rule, and where the specimen sits.

**Sources**
- **Compose performance and graphics layers:**
  - https://developer.android.com/develop/ui/compose/performance/bestpractices
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/graphics/GraphicsLayerModifier.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/layer/CompositingStrategy.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/drawscope/DrawScope.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-unit/src/commonMain/kotlin/androidx/compose/ui/unit/Constraints.kt
- **Hit testing and pointer input:**
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/node/InnerNodeCoordinator.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/node/PointerInputModifierNode.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/input/pointer/PointerInteropFilter.android.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/input/pointer/PointerEvent.android.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/WindowRecomposer.android.kt
- **Releases and platform notes:**
  - https://developer.android.com/jetpack/androidx/releases/compose-ui
  - https://raw.githubusercontent.com/JetBrains/compose-multiplatform/master/CHANGELOG.md
  - https://kotlinlang.org/docs/multiplatform/compose-platform-specifics.html
  - https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html
  - https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta/
  - https://kotlinlang.org/docs/wasm-configuration.html
- **Pull requests and issues:**
  - https://github.com/JetBrains/compose-multiplatform-core/pull/3218
  - https://github.com/JetBrains/compose-multiplatform-core/pull/3436
  - https://github.com/JetBrains/compose-multiplatform/issues/4459
- **Gradients:**
  - https://api.skia.org/classSkGradientShader.html
  - https://www.w3.org/TR/css-images-3/#coloring-gradient-line

### 4.2 The puck's gesture arbitration against canvas gestures

**Verdict:** this can be built in Compose only with one hand-written arbiter. The brief treats each
gesture as standalone; they are not.
- Three [CANON] gestures collide: hold on a node, the two-finger swipe, and the M key.
- The explorer omits the hub cancel, two-finger room switching, both object radials and the
  letter shortcuts, and its radial misbehaves at the hub.
- A stylus is a separate hand only on Android.

**Method**
- **Driving the explorer:** the explorer's puck (l.1089–1110) was driven in Playwright Chromium
  with `hasTouch` via CDP `Input.dispatchTouchEvent`, logging `navigator.vibrate`.
- **Reading sources:** of the pinned versions (CMP `ui-desktop`/`ui-wasm-js` 1.12.1, androidx
  `ui-android`/`foundation-android` 1.12.1, `core` 1.19.0).

**The explorer, measured** (the puck is 68 px, so 1 px = 1 dp)
- **Tap and hold** match the spec: ≤ 5 px of jitter still opens the radial; 7 px becomes a
  swipe.
- **Swipe.**
  - The pixels spent crossing the slop are discarded, so tool changes land at 45 and 80 px, not
    at 34 and 68.
  - A 100 px move in one event cycles **one** tool, because the accumulator resets to 0.
  - Compose batches moves per frame, so a literal port drops steps on a flick.
- **Radial.**
  - The reference angle is taken at touch-down, on the hub, where the angle is undefined.
    Moving straight out to r = 80 turned the ring −90° and changed the tool by two with no
    rotation.
  - A 6 px wobble across the centre jumped 4 sectors with one 5 ms tick, and releasing
    **committed** it.
  - "Release within r < 24 dp cancels" is not implemented.
  - Opening fires an unspecified 12 ms vibration.
- **Grip and ring size.**
  - The grip's hit area is 18×12 px, against UI_SPEC §6's 44 dp floor.
  - Re-pinning lands 12 px from the edge, not 18 dp.
  - The r = 120 dp ring, centred 52 dp from the edge, runs 68 dp off screen.
- **Header.** dx −45 / dy +100 switched rooms. There is no direction test, and the switch is
  decided on pointer-up.

**Platform facts (from source)**
- **Android:**
  - Tool types map to `PointerType.Touch/Stylus/Mouse/Eraser`, and stylus hover arrives as
    Enter/Move/Exit.
  - The barrel button is folded into `isPrimaryPressed` (`BUTTON_PRIMARY or
    BUTTON_STYLUS_PRIMARY`); isolating it needs `PointerEvent.motionEvent` in androidMain.
  - Compose never reads `FLAG_CANCELED` (API 33).
  - `longPressTimeoutMillis` is 400 ms by default and user-adjustable; touch slop is 8 dp.
  - The gestural-back inset is 30 dp. `systemGestureExclusion` is Android-only and capped at
    200 dp of height per edge.
- **JVM desktop:**
  - Every AWT event becomes `PointerType.Mouse`, and there is no touch or pinch code.
  - `DefaultHapticFeedback` is an empty `TODO`.
  - The defaults are 500 ms and 18 dp.
  - `Window(onKeyEvent=…)` catches keys regardless of focus.
- **Wasm:**
  - All touch pointers are forwarded, so multi-touch works.
  - Every non-mouse pointer, pens included, becomes `PointerType.Touch` with **empty buttons**,
    and is reported as `pressed` on any move. A pen hovering therefore looks like a drag (from
    reading the source, not run on hardware).
  - A detector that waits out the slop without consuming anything can lose the gesture to the
    browser (UNVERIFIED).
  - Keys arrive only while the canvas has focus.
  - `navigator.vibrate` works in Chrome Android. It has been a no-op in Firefox Android since 79,
    was removed from desktop Firefox in 129, and has never existed in Safari.

**Recommendation**
1. **One sheet-level arbiter.** It is a `pointerInput` on the common parent of the canvas and the
   glass, in `PointerEventPass.Initial`, and it owns multi-touch:
   - two-finger tap = undo (all fingers up within about 250 ms, under the slop);
   - pinch against pan: whichever passes its slop first wins;
   - a room change only as an overscroll past the room's edge, or as a fling.

   Not `detectTransformGestures`, which pans with one finger.
2. **Puck.**
   - Detect with `awaitEachGesture` + `awaitFirstDown` + `withTimeoutOrNull(max(380,
     longPressTimeoutMillis))`.
   - Slop = `max(6.dp, touchSlop)`, and consume every move from the down onwards.
   - Swipe steps = `floor(acc / 34 dp)`, keeping the remainder.
   - Radial: accumulate angle only at r ≥ 24 dp, re-anchoring when the finger leaves the hub.
     Tick once per detent crossed. Cancel on a release inside the hub and on pointer cancel.
   - A 44 dp grip, and `systemGestureExclusion` on the puck.
3. **Hold on a node.** Hold then move grabs the node, keeping its offset. Hold then release opens
   the object radial, where the user taps a sector rather than spinning.
4. **Haptics.** Use `expect fun haptic(kind)`.
   - The Android actual goes through `LocalHapticFeedback`: `SegmentTick` for a swipe step,
     `SegmentFrequentTick` for a detent, `Confirm` for a commit. These are API 34; on API 31–33,
     `core` falls back to `CONTEXT_CLICK`/`CLOCK_TICK`.
   - This route needs no permission and respects the user's Touch-feedback setting.
   - Exact millisecond durations only if the owner insists: they need the `VIBRATE` permission.
   - Desktop and web are no-ops. Common code calling `LocalHapticFeedback` *does* vibrate on
     Chrome Android.
5. **Shortcuts.** Put them in a root `onKeyEvent`, not `onPreviewKeyEvent`, so a focused `=` field
   receives its letters. Resolve M: map versus measure.

**Sources**
- **Compose and AndroidX source jars:**
  - https://repo1.maven.org/maven2/org/jetbrains/compose/ui/ui-wasm-js/1.12.1/ui-wasm-js-1.12.1-sources.jar
  - https://repo1.maven.org/maven2/org/jetbrains/compose/ui/ui-desktop/1.12.1/ui-desktop-1.12.1-sources.jar
  - https://dl.google.com/android/maven2/androidx/compose/ui/ui-android/1.12.1/ui-android-1.12.1-sources.jar
  - https://dl.google.com/android/maven2/androidx/compose/foundation/foundation-android/1.12.1/foundation-android-1.12.1-sources.jar
  - https://dl.google.com/android/maven2/androidx/core/core/1.19.0/core-1.19.0-sources.jar
- **Android haptics:**
  - https://developer.android.com/develop/ui/views/haptics/haptics-apis
  - https://developer.android.com/reference/android/view/HapticFeedbackConstants
  - https://developer.android.com/reference/android/os/VibrationEffect
  - https://developer.android.com/reference/android/os/VibrationEffect.Composition
  - https://developer.android.com/reference/android/os/Vibrator
- **Android stylus, gesture exclusion and system defaults:**
  - https://developer.android.com/develop/ui/compose/touch-input/stylus-input/advanced-stylus-features
  - https://developer.android.com/reference/android/view/View#setSystemGestureExclusionRects(java.util.List%3Candroid.graphics.Rect%3E)
  - https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewConfiguration.java
  - https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/overlays/NavigationBarModeGesturalOverlay/res/values/config.xml
- **Browsers:**
  - https://raw.githubusercontent.com/mdn/browser-compat-data/main/api/Navigator.json
- **Editor shortcuts:**
  - https://handbook.glyphsapp.com/single-page/
  - https://robofont.com/documentation/reference/workspace/glyph-editor/tools/

### 4.3 TextRunShaper and Skiko shaping for Indic conjunct previews (and the truth on the web)

**Findings**
- **Android: TextRunShaper.**
  - `TextRunShaper.shapeTextRun(…)` (API 31) returns `PositionedGlyphs`: IDs, x/y, font and
    advance. It gives no per-glyph cluster, no glyph name and no trace.
  - Font bytes need `Font.Builder(ByteBuffer)` (API 29, direct buffers only) plus
    `Typeface.CustomFallbackBuilder`, whose system fallback "is processed only when no matching
    font is found" and cannot be switched off. Check `getFont(i)` every time.
  - Minikin uses the OS's HarfBuzz: **2.6.4** on Android 12, and 10.2.0 by Android 16.
- **Desktop: Skiko's shaper.**
  - Skiko 0.150.1 exposes `org.jetbrains.skia.shaper.Shaper`.
  - `RunHandler.commitRun(info, glyphs, positions, clusters: IntArray)` returns UTF-16 clusters.
  - SkShaper uses `HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS` and the HarfBuzz pinned in Skia
    m144 DEPS. It has no trace.
- **Web: the brief is wrong.**
  - `skiko.wasm` (8.6 MB) contains `SkShaper_harfbuzz.cpp`. The Wasm klib exports the same
    `Shaper`/`RunHandler` (including `RunHandler_nGetClusters`), `FontMgr.makeFromData` and
    `Canvas.drawTextBlob`.
  - Compose paints everything into one canvas, and `FontFace` affects only DOM text.
  - Using the browser's engine would need an `HtmlElementView`, which "overlays the canvas" and
    "intercepts input events". It cannot sit under the sheet and returns no glyph data.
  - Safari, and therefore every iOS browser, shapes with CoreText, not HarfBuzz.
- **Clusters are not what is missing.** HarfBuzz 14.5.0 on Noto Sans Devanagari:
  - र्कि gives 3 glyphs, all in cluster 0; क्षि gives 2, both in cluster 0.
  - A teaching preview must say something like "this half form came from lookup 34 in `half`".
    Only the lookup trace can say that: harfbuzzjs `shapeWithTrace` (248 entries for क्ष) or
    uharfbuzz `set_message_func`.
- **The engine versus the font.**
  - HarfBuzz's Indic shaper finds syllables and the base consonant, reorders the i-matra and the
    reph, sets the rphf/half/blwf/pstf masks, and handles ZWJ/ZWNJ and dotted circles.
  - The font supplies every form and rule: half forms, akhn/pres conjuncts, rphf/rkrf/blwf/vatu/
    cjct, the i-matra length choice, and the abvm/blwm anchors.
  - Correct conjuncts are therefore the job of Typewright's **feature generator** (`scripts`).
  - Always emit `languagesystem dev2`.
  - A clean preview proves only that HarfBuzz renders the font: its fallbacks can hide missing
    tables.
- **Compile latency is the real constraint.** Measured on Noto Sans Devanagari Regular (854
  glyphs, 311 KB `features.fea`):

  | path | time |
  |---|---|
  | fontmake 3.12.1 CLI → TTF | 8.7 s (6.9 s with `--keep-overlaps`) |
  | `ufo2ft.compileTTF`, warm, in-process | 4.2–4.4 s |
  | patching one `glyf` entry and saving | 0.11 s |
  | fontc 1.0.0 | 0.16 s (0.26–0.33 s on one thread) |
  | HarfBuzz shape of one syllable | about 4 µs |

  The fontmake and fontc builds shaped identically on 4,625 Devanagari strings. Phone timings
  are UNVERIFIED.

**What will not work**
- **Android:** clusters, a trace, a pinned HarfBuzz version, or switching off fallback.
- **Linux:**
  - There is no trace.
  - Compose `Text` caches typefaces by identity (`FontCache`, `ExpireAfterAccessCache<String,
    Typeface>`) and falls back to system fonts. Use `Shaper` with `FontMgr.empty` instead.
- **Wasm:** the `FontFace` path. Showing the user's own GSUB/GPOS in v1 is impossible without a
  local compiler, because P9 sends the project only on an explicit build.

**Recommendation**
1. **Two shapers, not three.**
   - Skiko `Shaper` in a `skikoMain` source set for desktop and wasmJs; TextRunShaper in
     androidMain.
   - Remove `ShapingStack.BROWSER_FONT_FACE` and `BrowserFontFaceShaperStub`.
2. **The shaper returns glyph IDs and positions only.**
   - Draw each glyph from our own `core-geometry` outlines, mapped through the build's glyph
     order.
   - Outline edits then need no recompile, and zoom and selection ink behave the same on every
     target.
3. **Keep the preview font separate from the shipped font.**
   - Rebuild it only when cmap, advances, anchors or features change, and debounce the rebuild.
   - On desktop, call fontc now.
   - Next, write a pure-Kotlin cmap/hmtx/GDEF/GSUB/GPOS writer in `core-font` (GSUB types 1/2/4/6,
     GPOS types 2–6). A CI test should check that HarfBuzz shapes its output the same as fontc's
     on a shared corpus.
   - Until then, Android and the web show a stub saying "preview stale" or "not on this target"
     (law 4).
4. **"Explain this conjunct" needs direct HarfBuzz on every target:** harfbuzzjs 1.6.2 on the
   web, uharfbuzz 0.56.2 on Linux, libharfbuzz via the NDK and JNI on Android.
   - Pair it with fontc's `--emit-lookup-debug-info`, whose `Debg` table maps each lookup to
     `features.fea:line:col` (verified).
   - Any number that judges the user (law 5) uses the same bundled HarfBuzz.

**Sources**
- **Android text APIs:**
  - https://developer.android.com/reference/android/graphics/text/TextRunShaper
  - https://developer.android.com/reference/android/graphics/text/PositionedGlyphs
  - https://developer.android.com/reference/android/text/TextShaper.GlyphsConsumer
  - https://developer.android.com/reference/android/graphics/Canvas
  - https://developer.android.com/reference/android/graphics/Typeface.CustomFallbackBuilder
  - https://developer.android.com/reference/android/graphics/fonts/Font.Builder
- **Android's HarfBuzz and Minikin:**
  - https://android.googlesource.com/platform/external/harfbuzz_ng/+/refs/heads/android12-release/src/hb-version.h
  - https://android.googlesource.com/platform/frameworks/minikin/+/refs/heads/android12-release/libs/minikin/LayoutCore.cpp
- **Skiko and Skia:**
  - https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt/0.150.1/
  - https://raw.githubusercontent.com/google/skia/chrome/m144/modules/skshaper/src/SkShaper_harfbuzz.cpp
  - https://raw.githubusercontent.com/google/skia/chrome/m144/DEPS
- **Browsers:**
  - https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html
  - https://developer.mozilla.org/en-US/docs/Web/API/FontFace/FontFace
  - https://raw.githubusercontent.com/WebKit/WebKit/main/Source/WebCore/platform/graphics/coretext/ComplexTextControllerCoreText.mm
- **HarfBuzz and its bindings:**
  - https://raw.githubusercontent.com/harfbuzz/harfbuzz/main/src/hb-ot-shaper-indic.cc
  - https://raw.githubusercontent.com/harfbuzz/harfbuzz/main/src/hb-ot-shape.cc
  - https://www.npmjs.com/package/harfbuzzjs
  - https://pypi.org/project/uharfbuzz/
- **Compilers:**
  - https://pypi.org/project/fontc/1.0.0/
  - https://github.com/googlefonts/fontc
  - https://pypi.org/project/fontmake/
- **Devanagari:**
  - https://github.com/notofonts/devanagari
  - https://learn.microsoft.com/en-us/typography/script-development/devanagari

### 4.4 Chaquopy packaging size (and the compile backend)

**What Chaquopy 17 is.**
- Chaquopy 17.0.0 was released on 2025-12-01. It supports Python 3.10–3.14, minSdk 24 and
  **AGP 7.3–9.2**.
- It can be applied to one `com.android.application` or `com.android.library` module only.
- From Python 3.12 on, it ships arm64-v8a and x86_64 only.
- It has been MIT-licensed open source since **12.0.1 (2022-07-24)**.
- It installs with `pip --only-binary`, so it never builds an sdist.

**Native dependencies of the fontmake chain**

| package | native | Chaquopy index | PyPI Android wheel |
|---|---|---|---|
| fontTools 4.65 | optional Cython | pure wheel | – |
| lxml | yes | 5.3.0, cp311–cp313 | no |
| pyclipper (booleanOperations → ufo2ft) | C++ | **none** | no |
| unicodedata2 | C | **none** | no |
| openstep-plist (glyphsLib → fontmake) | Cython | **none** | no |
| compreffor | C++ | **none** | no |
| cffsubr | ships an ELF `tx` **executable** | **none** | no |
| skia-pathops (optional) | C++ | **none** | no |
| brotli (woff2) | C | 1.1.0 | no |

`pip { install("fontmake") }` therefore fails. The smallest set that could work gives TTF output
only:
- ufo2ft, ufoLib2, fontMath, booleanOperations and attrs;
- pure-Python fontTools;
- a pyclipper wheel we build ourselves (cibuildwheel ≥ 3.1 can build Android wheels).

Installing ufo2ft without cffsubr needs `--no-deps` inside Chaquopy's `install()`, which is
UNVERIFIED.

**Size per ABI** (arm64-v8a, Python 3.13):

| component | size |
|---|---|
| Chaquopy runtime | 6.6 MB compressed (`libpython3.13.so` is 5.4 MB, libcrypto 3.7 MB) |
| stdlib-pyc | 4.3 MB |
| pure-Python toolchain | about 2.5 MB zipped |
| lxml | 1.4 MB |
| **total** | **about 12–15 MB of download per ABI** |

Doubling for x86_64, and the installed size, are UNVERIFIED. Chaquopy's own notes warn that "many
existing Android wheels will still fail to load on 16 KB devices".

**QA under Chaquopy.** Fontbakery needs 18 native packages. It cannot run on Android.

**Recommendation: swap the v1 and v2 plans.**
1. **The compiler (`CompileBackend`) is fontc 1.0 on all three targets:**
   - Android: JNI via cargo-ndk.
   - Desktop: a bundled binary.
   - Web: WASI in a Worker.
2. **QA is Fontspector everywhere.**
3. **Overlap removal and point cleanup move into `core-geometry`,** and Economy and Check always
   measure the UFO.
4. **fontmake via `gftools builder`** becomes the reference build in the generated repository's
   CI, and an option on desktop when Python is present.
5. **Chaquopy becomes a spike, not v1.**
6. **The hosted endpoint becomes optional and self-hostable** (contract below), which removes the
   conflict with law 3.
7. **Pin `SOURCE_DATE_EPOCH`** so builds are reproducible.

**A minimal endpoint contract, if one is ever run**
- **Request:** `POST /v1/build` with an `application/zip` body: at most 10 MB zipped, 50 MB
  unzipped and 2,000 entries. It holds the UFO(s) plus `build.json` (`formats`, `toolchain`,
  `sourceDateEpoch`).
- **Success:** 200, with a zip of the fonts, `build.log` and `manifest.json`.
- **Errors:** 413, 422 (with the log), 429.
- **Headers:** `Cache-Control: no-store`, no cookies, CORS limited to the app's origin.
- **Zero retention:**
  - a single-use sandbox with a tmpfs and no outbound network;
  - no object store and no database;
  - no logging of request bodies;
  - platform logs stripped of IPs;
  - reject FEA `include()`;
  - publish the source and the image digest.

**Sources**
- **Chaquopy:**
  - https://raw.githubusercontent.com/chaquo/chaquopy/master/LICENSE.txt
  - https://chaquo.com/chaquopy/license/
  - https://chaquo.com/chaquopy/doc/current/changelog.html
  - https://chaquo.com/chaquopy/doc/current/versions.html
  - https://chaquo.com/chaquopy/doc/current/android.html
  - https://chaquo.com/pypi-13.1/
  - https://repo1.maven.org/maven2/com/chaquo/python/target/3.13.9-0/
  - https://raw.githubusercontent.com/chaquo/chaquopy/master/server/pypi/README.md
- **Android wheels and platform rules:**
  - https://raw.githubusercontent.com/pypa/cibuildwheel/main/docs/changelog.md
  - https://developer.android.com/about/versions/10/behavior-changes-10
  - https://developer.android.com/guide/practices/page-sizes
  - https://developer.android.com/google/play/requirements/target-sdk
- **fontc:**
  - https://pypi.org/pypi/fontc/json
  - https://raw.githubusercontent.com/googlefonts/fontc/main/README.md
  - https://raw.githubusercontent.com/googlefonts/fontc/main/fontc/Cargo.toml
  - https://github.com/googlefonts/fontc/issues/1215
  - https://googlefonts.github.io/fontc_crater/
- **Fontspector:**
  - https://raw.githubusercontent.com/fonttools/fontspector/main/INSTALLATION.md
  - https://raw.githubusercontent.com/fonttools/fontspector/main/USING.md
  - https://fonttools.github.io/fontspector/
  - https://crates.io/api/v1/crates/fontspector
- **Google Fonts guides:**
  - https://googlefonts.github.io/gf-guide/qa.html
  - https://googlefonts.github.io/gf-guide/build.html
- **Fontbakery and Python packaging:**
  - https://pypi.org/pypi/fontbakery/json
  - https://peps.python.org/pep-0668/

### 4.5 Kotlin/Wasm readiness for the canvas

**Verdict:** the web target can show the specimen, the proofs, Learn and Check. On the current
stack it cannot match the phone and desktop apps as a drawing instrument. Several of its default
behaviours break laws 3 and 7 unless the design allows for them from P1.

**Findings**
- **Status.**
  - Compose for web has been Beta since 1.9.0 (Sept 2025), and Kotlin/Wasm is Beta too.
  - CMP ≥ 1.11 needs Kotlin ≥ 2.3.20; we use 2.4.20.
- **Browser floor.**
  - WasmGC plus legacy exception handling: Chrome, Edge, Chrome Android and WebView 119+,
    Firefox 120+, Safari **18.2+**.
  - `composeCompatibilityBrowserDistribution` falls back to Kotlin/JS, but only if every module
    also has a `js()` target (inferred).
- **Size and startup:** see §2. The host **must** serve brotli or gzip.
- **Threads.** In a browser, `Dispatchers.Default` is `WindowDispatcher`, which runs on the main
  thread. Trace, fit and QA work will take frames away from drawing unless it moves to a Worker,
  which is a second Wasm instance that shares no memory.
- **Pointer input.**
  - Multi-touch transform gestures have worked since 1.8.0, and 1.12 fixed `pointercancel`.
  - **The stylus does not survive**: a pen arrives as `PointerType.Touch` with empty buttons, and
    Enter/Exit are dropped. Pressure and `getCoalescedEvents` do pass through.
- **Fonts.**
  - `Font(identity, data: ByteArray, …)` exists in ui-text-wasm-js and is Skiko-only; Android
    needs a different route.
  - Call `FontFamily.Resolver.preload()` before first use.
  - **Since 1.12.0, Compose downloads Noto from fonts.gstatic.com** for any code point no loaded
    font covers, and there is no public off switch. The placeholder made no such request, because
    everything it draws is Latin.
- **Files.**
  - `showOpenFilePicker`/`showDirectoryPicker` exist in Chrome 86 and Chrome Android 132
    (experimental), and in no version of Firefox or Safari.
  - OPFS: Chrome 86, Firefox 111, Safari 15.2; `createWritable` arrived in Safari 26.
  - `<input webkitdirectory>`: iOS Safari 18.4, Firefox Android 142.
- **Clipboard.** The async Clipboard API needs HTTPS; plain text works.
- **Accessibility.**
  - On by default, as a DOM mirror under `#cmp_a11y_root`.
  - 1.12.1 maps only these roles: button, checkbox, switch, radio, tab, img, menu, heading,
    textbox, list, grid.
  - `Role.ValuePicker` gets no role, and there is **no `aria-live`**.
- **Libraries with a wasmJs variant:**

  | library | version | caveat |
  |---|---|---|
  | kotlinx-serialization-json | 1.11.0 | none |
  | kotlinx-coroutines-core | 1.11.0 | main thread only |
  | kotlinx-io-core | 0.9.1 | its file system needs Node |
  | okio | 3.18.2 | its file system needs Node |
  | xmlutil | 1.0.2.1 | none |
  | kaml | 0.104.0 | "highly experimental" |
  | snakeyaml-engine-kmp | 4.0.1 | none |

- **Binaryen.**
  - KGP 2.4.20 runs `--closed-world --type-ssa -O3 -O3 --gufa -O3 --type-merging -O3 -Oz`.
  - Its defaults are `version="130"` and download from GitHub.
  - Skipping wasm-opt costs 3.8× the raw size and +768 KB brotli on the app module.
  - Binaryen assets have disappeared upstream before (KT-77076).
- **Test trap.** Pin `LANG=en_US.UTF-8` or `--lang=en-US` in browser tests (see §2, the blank
  page).

**Recommendation**
1. **Keep the web in v1, labelled Beta.**
   - Feature-detect WasmGC before loading, and show an "unsupported browser" page below Chrome
     119, Firefox 120 or Safari 18.2.
   - Do not build the Kotlin/JS fallback.
2. **Law 3.**
   - Bundle fonts that cover every code point the UI and the previews render.
   - Host with `Content-Security-Policy: connect-src 'self' <build endpoint>`.
   - Add a Playwright check that no request leaves the origin.
   - Ask JetBrains for a public switch.
3. **Shaping** through Skiko (§4.3).
4. **Files.**
   - The working copy lives in OPFS, with `navigator.storage.persist()`.
   - Import and export as zip, and prompt the user to export.
   - Offer `showDirectoryPicker` only where it exists.
5. **Compute.** Design `engine-trace`, `engine-construct` and `qa` now as pure suspend functions
   with serializable inputs and outputs, so they can run in a Worker.
6. **Stylus and accessibility.**
   - Read `pointerType`, `buttons` and hover from our own DOM listeners, or stub those features
     with a note saying so (law 4).
   - Add a hidden `aria-live` element for the radial's announcements.
7. **Data.** JSON on the web path. Convert YAML at build time.
8. **Build.**
   - Keep `-Ptypewright.wasmOpt`, and never ship without wasm-opt.
   - Add a locale guard in `index.html` (OPEN_QUESTIONS 13).
   - Frame times on a phone are for the owner to measure.

**Sources**
- **Kotlin/Wasm and Compose for web:**
  - https://kotlinlang.org/docs/multiplatform/supported-platforms.html
  - https://kotlinlang.org/docs/wasm-overview.html
  - https://kotlinlang.org/docs/wasm-configuration.html
  - https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta/
  - https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html
  - https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html
  - https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html
  - https://raw.githubusercontent.com/JetBrains/compose-multiplatform/master/CHANGELOG.md
  - https://blog.jetbrains.com/kotlin/2025/05/present-and-future-kotlin-for-web/
- **Compose web source:**
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/FallbackFontDownloader.web.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/window/ComposeWindowInternal.web.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/accessibility/ComposeWebSemanticsListener.kt
  - https://github.com/JetBrains/compose-multiplatform-core/blob/v1.12.1/compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/PlatformClipboard.web.kt
  - https://github.com/JetBrains/compose-multiplatform-core/pull/3010
- **Libraries:**
  - https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/wasmJs/src/CoroutineContext.kt
  - https://github.com/Kotlin/kotlinx-io/blob/master/core/nodeFilesystemShared/src/files/FileSystemNodeJs.kt
  - https://square.github.io/okio/multiplatform/
  - https://github.com/charleskorn/kaml
  - https://repo1.maven.org/maven2/com/charleskorn/kaml/kaml-wasm-js/
  - https://repo1.maven.org/maven2/io/github/pdvrieze/xmlutil/core-wasm-js/
  - https://repo1.maven.org/maven2/com/squareup/okio/okio-wasm-js/
  - https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-io-core-wasm-js/
  - https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-wasm-js/
  - https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json-wasm-js/
- **Browser support and storage:**
  - https://github.com/mdn/browser-compat-data (api/Window.json, StorageManager.json, Clipboard.json, PointerEvent.json, Navigator.json, webassembly/garbage-collection.json)
  - https://webkit.org/tracking-prevention/
- **Binaryen:**
  - https://youtrack.jetbrains.com/issue/KT-77076
  - https://www.npmjs.com/package/binaryen

---

## 5. Where the brief overclaims

Each entry gives the quote, where it comes from, and the correction.

### Toolchain and runtime

1. "Chaquopy is free of charge for all use since v14" (brief §3). Chaquopy has been MIT-licensed
   open source since **12.0.1 (2022-07-24)**. The current release is 17.0.0: AGP 7.3–9.2, minSdk
   24.
2. "v1 backend: fontmake + fontTools in a Python runtime (Chaquopy on Android, system Python on
   Linux" (brief §3); "Android and Linux call bundled fontmake" (handoff §3). fontmake cannot be
   installed under Chaquopy 17: five packages have no Android wheel, and cffsubr runs `tx`. On
   top of that, Chaquopy 17 does not support our AGP 9.4.1 and cannot be applied to a KMP library
   module. On Linux, fontmake needs Python ≥ 3.10 plus `python3-venv`.
3. "Planned v2 backend: fontc (Rust, Apache-2.0) as a native library, which is what makes iOS
   possible and web offline" (brief §3); "a Wasm fontc port as the v2 goal" (handoff §3).
   - fontc 1.0.0 shipped on 2026-09-01.
   - A native library cannot run in a browser.
   - A `wasm32-wasip1` build already compiles Hyle Deco in 0.37 s under Node's WASI, so no port
     is needed, only an in-memory filesystem.
   - fontc is ready for v1.
4. "Layer one: Fontbakery or Fontspector" (brief §12); "qa: Fontbakery in the same Python
   runtime" (handoff §3). The two are not interchangeable: on Hyle Deco they give 2 FAIL against
   4, and offline Fontbakery skips the shaping checks. Fontbakery cannot run on Android, and
   Google now uses Fontspector.
5. "bridge to fontspector or fontbakery (whichever installs cleanly)" (P1). Only Fontbakery
   installs with pip, so this rule picks the tool Google replaced.
6. "No backend of ours" (CLAUDE.md law 3), against "a small hosted endpoint (fontmake in a
   container)" (P9) and the handoff §9's paid hosted service. That endpoint *is* our backend.
   Make it optional and self-hostable, with fontc-WASI in the browser as the default.
7. "The only network calls are the ones the user asks for" (CLAUDE.md law 3). On the web with
   CMP ≥ 1.12 this is false by default: Compose fetches Noto from fonts.gstatic.com for any
   uncovered code point.
8. "Pure-JVM-first" (CLAUDE.md law 2). Kotlin/Wasm needs the core in `commonMain`, which cannot
   use `java.*` (no `javax.xml`, no `java.util.zip`). The rule is "common-first". The pure
   modules can hold neither the Chaquopy bridge (`qa`) nor OpenCV (`engine-trace`).

### Shaping

9. "web uses the browser text engine through `FontFace`" (brief §3). Compose draws text with
   Skia inside `skiko.wasm`, so `FontFace` fonts never reach it and it gets no glyph data from
   them. Use Skiko's shaper, as on desktop. The DOM route would also put Safari users on
   CoreText. OPEN_QUESTIONS 5's "Only desktop … gives everything" needs the same correction.
10. "Direct HarfBuzz bindings only if a script's preview needs cluster data the platform API
    withholds" and "the binding work leaves the critical path" (brief §3). That is the wrong
    trigger:
    - Skiko already gives clusters, and an Indic cluster is the whole syllable anyway.
    - The real triggers are the lookup trace and the need for one pinned HarfBuzz version.
    - The critical path is the compiler.
11. "Android 12+ `android.graphics.text.TextRunShaper` yields positioned glyphs shaped by
    HarfBuzz" (brief §3). True, but Android 12's HarfBuzz is 2.6.4, glyphs may come from a system
    fallback font without warning, and there are no clusters.
12. "Renders the user's font with real shaping so conjuncts and joins are seen correctly during
    design" (handoff §3). Only after a compile, and only as HarfBuzz renders them, fallbacks
    included. Fontmake takes 4–9 s per compile, and the web v1 has no compiler.

### Corpus, fence and fixtures

13. "serif-didone 19 … blackletter 18", and "the top 30" as the class size (brief §8.1). The data
    has didone **15**. Blackletter boxes hold 15, because Content, Khmer and Siemreap have no
    Latin glyphs. Grotesque boxes hold 29.
14. "Glyphs with zero contours are excluded from a box, and n drops accordingly" (brief §8.1).
    False in the script and in the JSON:
    - composites count as 0 (261 of 15,354 entries);
    - the geometric o box has min 0;
    - the compact pack keeps the zeros, which a log axis cannot plot.
15. "On-curve counts are comparable across cubic sources and TrueType binaries because cu2qu keeps
    the source's on-curve points" (brief §8.1). Three corrections:
    - The script double-counts the closing point of every contour that ends in a curve (Poppins:
      o 8 → 10, n 12 → 13, e 12 → 14).
    - The claim breaks under `--drop-implied-oncurves`.
    - 89 of the 251 corpus faces are variable fonts, which normally keep their overlaps. Compare
      the user's glyphs only after the same overlap removal.
16. "(`data/families.csv`, from `google/fonts/tags/all/families.csv`" (brief §8.1). The
    repository sets licences per directory, and `tags/` has none, so the file's licence is
    unstated.
17. "the standard fence. Not a threshold we invented." (explorer, Check). Brief §8.2 and the code
    use Q3 + max(1.5·IQR, **0.25·Q3**). That minimum is ours, and it is the deciding term in 27 of
    the 62 geometric boxes. By law 5 the footer must say so.
18. "never 'fail / warn / pass' for this check" (brief §8.2). The explorer's `verdict()` returns
    `'fail'`, and fails block Ship (§12).
19. "The gate compares against the style box, which already reflects construction" (brief §8.5).
    Hyle Deco's properly fitted o (16 on-curve) is an outlier against 6 of the 10 boxes. In the
    geometric box, Q3 is 10 and the fence 12.5, and the fence drops to 10 once the counts are
    fixed. The Task 4 gate can never pass for the reference font.
20. "T 1,763 → 8, o 80 → 32, n 44 → 22" (CLAUDE.md; D15). 32 and 22 are on-curve + off-curve
    totals, but the box compares on-curve counts: o 16 (+16 off) and n 14 (+8 off). List on-curve,
    off-curve and total separately.
21. "o an outlier (80 against 10)" (P1). The 10 comes from the double count; the true geometric
    o box is 8.
22. "A rasterised circle must fit to 8 on-curve + 16 off-curve within 1 unit" (P2).
    - A disc needs **4 + 8**: four quarter-arc cubics deviate by about 2.7×10⁻⁴·r, which is
      0.07 units at r = 250.
    - 8 + 16 is a ring of two contours.
    - As written, the test rewards a fitter that doubles its segments, and "1 unit" has no stated
      raster scale.
23. "A T is 8 points" (handoff §2) and "Node count per glyph against a per class threshold"
    (KNOWLEDGE A1; handoff M7). These contradict D2, which calls for a box, not a threshold. The
    garalde T median is 24.
24. "Reference faces for lessons come from the same corpus as the boxes (OFL/Apache, fetched at
    build time, subset for the lesson's glyphs)" (brief §9).
    - 6 of the 10 lesson faces are not in their class corpus.
    - The corpus includes Ubuntu Mono, which is UFL-1.0.
    - 5 exemplars have Reserved Font Names, and OFL FAQ 2.6 counts subsetting as modification.

### Interaction and the sheet

25. "The pipeline is a row of rooms on one sheet" (brief §4.1; seven rooms). The explorer's sheet
    has three: Draw, Space and Learn. The rest are separate screens with back buttons.
26. "Economy (§8) is a view inside Check" (brief §4.4). In the explorer, Economy is a top-level
    screen.
27. "the grid, metric lines, letters and proofs travel together as one plane" (brief §4.1). True
    for pans. Under zoom, lines, nodes and labels must not scale, so it is not one layer.
28. "a layer in the sheet's coordinate space counter-translated so it stays fixed to the glass;
    radial gradients from canvas colour to transparent" (brief §5.3; UI_SPEC §1). This is a CSS
    workaround. In Compose:
    - the bloom is a screen-space pass between the two world passes;
    - the gradient ends on *canvas colour at alpha 0*, because Skia interpolates unpremultiplied;
    - imported textures need a `DstOut` mask.
29. "Pinch out from a glyph → the word proof → the specimen (the project's home) → the map of
    rooms" (brief §4.2). The explorer implements none of this. The specimen has no place on the
    sheet, and §4.3 puts the proof *below* the room rather than at a zoom level.
30. "This is the one navigation model that works identically for finger, stylus and trackpad"
    (brief §4.2).
    - On Linux there is no pinch and no multi-touch.
    - Android trackpads send scale events.
    - A trackpad two-finger tap is usually a right-click.
31. "a two-finger horizontal swipe anywhere" switches rooms (brief §4.1), against "pan / zoom
    canvas | two fingers" (brief §5.5). The same gesture has two meanings, and the explorer lists
    it as an open Fork. Rule: switch rooms only on an overscroll past the edge, or on a fling.
32. "hold grabs a node" (brief §5.2), against "Contextual actions on an object … are a hold on
    the object itself" (brief §5.1). One finger gesture again has two meanings. Rule: hold then
    move grabs; hold then release opens the radial.
33. "Shortcuts follow Glyphs and RoboFont where sensible: V select, P pen, S primitives,
    B boolean, K stroke, M measure, A anchors, T metrics" (brief §5.1).
    - In Glyphs, S is Scale, B is Pencil, F is Primitives, L is Measurement and T is Text. Only V
      and P match.
    - RoboFont's tools page lists no such letters.
    - M is also "map" (brief §4.2, UI_SPEC §4, the explorer).
34. "The ring rotates with the finger … Release inside the hub cancels" (brief §5.1). The
    explorer implements neither safely:
    - the reference angle is taken on the hub;
    - a 6 px wobble jumped 4 sectors;
    - a release on the hub re-commits.

    Fix the explorer before porting it.
35. "Hold (380 ms)" (brief §5.1) and "Movement > 6 dp" (UI_SPEC §3). Both are below Android's
    400 ms and 8 dp defaults, and they ignore the accessibility setting "Touch & hold delay". Use
    `max(380, longPressTimeout)` and `max(6 dp, touchSlop)`.
36. "haptic tick (6 ms)", "(5 ms)", "(10 ms)" (brief §5.1). Compose's `HapticFeedback` takes no
    duration, and Android advises against on/off vibration "except as a last resort". Read these
    numbers as intent, not as spec.
37. "Haptics: Android only in v1" (brief §6). Under CMP 1.12.1, a `LocalHapticFeedback` call in
    common code also vibrates on Chrome Android.
38. "Stylus: … hover shows snap candidates before the touch commits; barrel button constrains;
    palm rejection on" (brief §5.2). This holds only on Android with an active pen:
    - on desktop the stylus is a mouse;
    - on the web a pen is a touch without buttons, and its hover arrives as pressed;
    - palm rejection is code we write, because Compose ignores `FLAG_CANCELED`.
39. "Reduced-motion honoured" (brief §6). It happens by itself only on Android. Desktop needs app
    code, and the web needs JS interop.
40. "the radial announces the current tool on each detent" (UI_SPEC §6). On the web, CMP 1.12.1
    has no `aria-live`. Names work; announcements do not.
41. "Default view shows three stages — Clean · Fit · Review", yet "Corner detection, fit tolerance
    and the snap band are the only parameters visible by default" (brief §7). The default hides
    the Corners and Snap stages whose parameters it says are visible. The explorer shows all
    eight, and P3 builds the unconfirmed three.

### Platforms, files and process

42. "the same app on Linux and the web" (brief §1) and "Web (Kotlin/Wasm)" as a [CANON] v1 target
    (brief §3). Both Compose for web and Kotlin/Wasm are Beta, and the web has no stylus
    semantics, no disk directory, no local Python QA and no announcements.
43. "Project = one directory" (brief §11; law 7). Only Chromium browsers can open a directory.
    Everywhere else the project lives in OPFS and moves as a zip, and Safari may evict it after
    7 days (UNVERIFIED for OPFS).
44. "Every file we write must open in FontForge, Glyphs or RoboFont" (law 7). Glyphs and RoboFont
    run only on macOS. Here, only ufoLib and FontForge can check our files.
45. "Web … renders the same tree; the compile step calls the hosted endpoint" (UI_SPEC §4). The
    compile step is not the only gap: QA layer one, capture, file I/O, stylus and announcements
    all differ too.
46. "Measure canvas frame times on a mid-range phone browser and report them" (P9). This cannot
    be done here (law 4). Throttled desktop emulation is possible; the owner has to measure on a
    phone.
47. "M-web … can run alongside M1–M2" (brief §13), and "P9 (web) can run any time after P4"
    (prompts). This holds only if M1's engine APIs are designed from the start to run off the main
    thread in a Worker, and the fonts and storage are designed for the web.
48. "Spiro / Hobby curves … produces minimal nodes by construction" (brief §10). Spiro splits any
    segment bending 1 rad or more. Neither method puts extrema on-curve unless knots are placed
    there with a direction.
49. "a robust polygon boolean (Clipper2 has a Kotlin path)" (handoff M2). The port is unpublished,
    has no Wasm target and flattens curves. Without curve restoration it brings back polygon
    glyphs.
50. "OpenCV for capture and cleanup on Android and Linux" (handoff M1), and "Implement
    engine-trace capture and cleanup on Android with OpenCV" (P3). OpenCV adds 23.5 MB on arm64,
    has no official Linux artifact and no Wasm build, and breaks law 2.
51. "perspective correction from the fiducials on the template sheets in scripts/templates" (P3).
    The zip holds 406 per-glyph SVG templates. There are no sheets and no fiducials.
52. "upstream.yaml" (handoff M7; P1). gftools treats it as legacy: it merges the file into
    `METADATA.pb` and deletes it.
53. "Violet = selected" (law 8). UI_SPEC also uses violet for the scrubber thumb, the user's
    point on the box plot, the stress needle and the feature tags.
54. Numbering.
    - CLAUDE.md says "P0–P8", but there are twelve prompts, up to P9 (including P1b, P5a and
      P5b). Brief §14 calls one of them "P5-geometry".
    - "M0–M6" leaves out M-web.
    - The handoff uses M-numbers for modules ("handoff M3" is input, not the Learn milestone), and
      P7 cites "§4 M5" for the campaign.
    - M6 has no prompt, and it includes interpolation, which §10 puts in v2.

**Sources for §5** are those of §3 and §4, plus:
- https://github.com/googlefonts/gftools/blob/main/Lib/gftools/builder/recipeproviders/googlefonts.py
- https://github.com/googlefonts/fontmake/blob/main/Lib/fontmake/__main__.py
- https://github.com/fonttools/fontspector/tree/main/fontspector-web
- https://github.com/google/fonts/blob/main/tags/all/families.csv
- https://github.com/google/fonts/tree/main/ofl/poppins
- https://github.com/JetBrains/skia-pack/blob/main/script/build.py
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/text/PositionedGlyphs.java

---

## 6. Decisions Madhav must make

Each item below is also in `docs/OPEN_QUESTIONS.md` under "P0: architecture review". The
entries there start at 17, so they do not repeat items 1–16. Existing items that these touch are
named.

1. **Compile backend (item 17).** Should fontc 1.0 be the v1 compiler on all three targets, with
   Chaquopy demoted to a spike and fontmake kept only as the desktop option and the reference CI
   build? This touches item 10, the endpoint owner, which becomes "optional, self-hostable".
2. **QA layer one (item 18).** Fontspector everywhere, and Fontbakery as a desktop extra? This
   changes P1's "whichever installs cleanly". It also decides which of the two verdicts on Hyle
   Deco (4 FAIL or 2) blocks Ship. It builds on item 7.
3. **Where cleanup lives (item 19).** Overlap removal and point cleanup in `core-geometry`, with
   Economy and Check measuring the UFO and never the binary? This also decides whether
   `CompileOptions.removeOverlaps` keeps defaulting to true, and where autohinting runs.
4. **Corpus regeneration (item 20).** The fixes are:
   - count from `glyf` flags;
   - decompose components;
   - drop families with no Latin glyphs;
   - store a per-glyph n;
   - use a documented tie-break and superfamily rule;
   - add a generator for the compact pack;
   - pin the source commit.

   Then regenerate, and update brief §8.1, the explorer's copy and P1 together. Plus: what is
   `families.csv`'s licence?
5. **Fence and verdict words (item 21).** Keep the 0.25·Q3 minimum and label it ours ("our
   heuristic", law 5), or use plain Tukey? And should an economy outlier block Ship at all, given
   that §8.2 forbids the word "fail"?
6. **Fixtures and the Task 4 gate (item 22).**
   - Restate CLAUDE.md and D15 as on-curve + off-curve counts (o 16 + 16, n 14 + 8).
   - Change P2's circle test to disc 4 + 8 and ring 8 + 16 at a stated raster scale.
   - Decide what Task 4 compares Hyle Deco's o against.
7. **Shaping architecture (item 23).**
   - Two shapers (Skiko for desktop and web, TextRunShaper for Android), dropping `FontFace`.
   - Glyphs drawn from our own outlines, and a separate preview font.
   - Bundled HarfBuzz for "explain this conjunct".

   This corrects item 5.
8. **Law 2 wording (item 24).** Replace "pure-JVM-first" with "common-first", meaning no `java.*`
   in the core modules. CLAUDE.md is the owner's document.
9. **Law 3 on the web (item 25).** Bundle fonts that cover every code point rendered, add a CSP
   and an egress test, and ask JetBrains for a switch. Accept the extra download size. This
   builds on item 9, the monospace licence.
10. **Web v1 scope (item 26).**
    - A Beta label, and a WasmGC gate (Chrome 119, Firefox 120, Safari 18.2) with an
      "unsupported browser" page.
    - No Kotlin/JS fallback.
    - OPFS + zip as project storage.
    - The stylus stubbed with a note.
    - Engine work in Workers.
11. **Gesture rules (item 27), explorer first (law 6).**
    - Two-finger swipe against pan.
    - Hold on a node: grab or radial.
    - M: map or measure.
    - The shortcut letters.
    - Haptic durations as intent.
    - Timings that follow the platform.
    - The radial's hub bugs and the 44 dp grip.
12. **Sheet model (item 28), explorer first.**
    - Three rooms on the sheet or seven?
    - Where does the specimen sit on the zoom axis?
    - Economy as a top-level screen or inside Check?
    - What does pinch do at each depth?
13. **Trace stages (item 29).** Three by default (D12, [CONFIRM]) or the explorer's eight? The
    three-stage default hides the Corners and Snap stages whose parameters §7 says are visible.
14. **Lesson-face licensing (item 30).**
    - Rename the RFN subsets or ship whole fonts.
    - Exclude Ubuntu Mono (UFL).
    - Should the lesson faces come from the corpus, as §9 says?
15. **Capture templates (item 31).** There are no multi-glyph sheets and no fiducials. Their
    design, and an explorer design for camera capture, are needed before P3.
16. **Numbering and M6 (item 32).** Adopt one prompt and milestone table. Decide what M6 contains
    (interpolation is v2 in §10), and whether M6 gets a prompt.
17. **Violet (item 33).** Amend law 8 so violet may also mark "the user's own value" (the box-plot
    point, the scrubber thumb, the needle, the tags), or change the explorer.

---

## 7. Recommended changes to the build order and to the prompts

### What P1 and P2 need first

The prompts assume that `core-font` and `core-geometry` exist, and they do not. So add a
**P1a: foundations** step, run by Opus, before P1:

1. **`core-geometry` types.**
   - Integer points in font units, y up.
   - Contour and glyph types, with on-curve and off-curve points and implied on-curves.
   - Direction, extrema detection and segment metrics.
   - Counting rules **identical** to the fixed corpus generator.
   - Every public function tested on the JVM *and* Wasm.
2. **`core-font`.**
   - An sfnt reader in common Kotlin (head, hhea, maxp, loca, glyf, cmap 4/12, hmtx, name,
     OS/2, post; GDEF and GPOS marks and pairs next).
   - UFO 3 read and write via xmlutil, with a byte-stable writer.
3. **A cross-check test.** The Kotlin counts for `fonts/HyleDeco-Regular.ttf` must equal
   fontTools' counts: 338 glyphs, 25,991 on-curve points; T 1,763 in 1 contour, o 80 in 2, n 44,
   H 1,252.
4. **Dependencies.** Add kotlinx-serialization-json 1.11.0 and xmlutil 1.0.2 to
   `libs.versions.toml` and `THIRD_PARTY.md`. Check that serialization-core 1.11.0 does not break
   Compose, which resolves 1.7.3 today. Expect Maven Central 429s when adding them.

Before P1a, run a **P0c: corpus fix**: fix the script, regenerate the pack, restate the numbers
in brief §8.1, and update P1's expected values (the geometric o box becomes 8). The explorer's
copy changes in the chat app first. Without this, P1 writes tests against counting artefacts.

### Changes per prompt

- **P1.**
  - Layer one is Fontspector behind a `LayerOneChecker` interface in `qa`, with actuals in a
    platform module: the CLI on desktop, Wasm on the web, and "not on this device" on Android.
  - The corpus is loaded with kotlinx-serialization-json and embedded on Wasm.
  - The pipeline writes `METADATA.pb`, not `upstream.yaml`.
  - The fence wording follows decision 5.
- **P1b.** Unchanged, but after P0c. The detector must be validated against the corrected class
  membership.
- **P2.**
  - Restate the fixtures (on-curve, off-curve, total).
  - Circle: disc 4 + 8 and ring 8 + 16 at a stated raster scale.
  - Tests run on `core-geometry` directly, never through a compiler, because fontmake's overlap
    pass yields T = 10 by accident.
  - Add JVM/Wasm determinism tests.
- **New P2b: compile via fontc** (Opus, because it spans Rust, JNI and WASI).
  - Build a desktop backend on the fontc binary first.
  - Then run a spike with fontc as WASI in a Worker for the web.
  - Then a cargo-ndk JNI spike for Android, with 16 KB pages and the result verified by the
    owner on a device.
  - Chaquopy only if all of that fails.
  - `CompileBackend` stays; `SystemPythonFontmakeBackend` becomes the optional desktop route.
  - Put booleans (at least union) in `core-geometry` before the app ships binaries it built
    itself, because fontc 1.0 has no overlap removal.
- **P3.**
  - Split the work. The raster chain goes in `engine-trace`, in common Kotlin with no OpenCV. A
    new Android-only capture module handles the camera.
  - Template sheets with fiducials must be designed first.
  - Build the stages as the explorer shows them (eight) until decision 13.
- **P4.** Precede it with:
  - explorer updates for decisions 11 and 12;
  - the Hyle Deco benchmark scene (§4.1).

  Then replace "the bloom as a counter-translated quiet layer" with the pass model, and "three
  separate input models" with one arbiter plus per-target `expect`/`actual` input and haptics.
- **P5a.** Hobby first; Spiro from raphlinus/spiro, never libspiro. Curve-restoring booleans live
  in `core-geometry` so that the trace, compile and construct steps share them.
- **P6.** Settle the lesson-face licensing (decision 14) before fetching or subsetting anything.
  Convert YAML to JSON at build time.
- **P7.** Blocked on P0c and decision 6. The margin is [CONFIRM] and not in the explorer, so by
  law 6 it needs an explorer design first.
- **P8.**
  - Shaping per §4.3.
  - Feature generation always emits `dev2`.
  - The preview-font writer goes in `core-font`, with a CI parity test against fontc.
  - The Devanagari preview screen does not exist in the explorer, so stop and ask (law 6).
- **P9.**
  - Not "any time after P4": the web constraints (Workers, serializable engine APIs, bundled
    fonts, OPFS) shape P1 onwards.
  - P9 itself covers fontc-WASI plus fontspector-web; the hosted endpoint as optional; CSP; the
    egress test; the locale guard; the WasmGC gate.
  - Phone frame times are for the owner to measure.
- **CI.**
  - Set `LANG=en_US.UTF-8` for browser tests.
  - Keep `-Ptypewright.wasmOpt`.
  - Run the workflow once on GitHub before P1 lands, because it has never run.

### Proposed order

P0c (corpus) → P1a (foundations) → P1 → P1b → P2 → P2b (fontc) → P3 → [explorer update +
benchmark] → P4 → P5a → P5b → P6 → P7 → P8 → P9. Web constraints apply from P1a on.
