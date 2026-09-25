# Typewright

Typewright takes hand-made letterforms (a scan, a photo, a drawing on the device) and turns
them into a font of Google Fonts submission quality, while teaching the user what quality
means. It treats the scripts the incumbents ignore (Devanagari and the other Indic scripts,
South East Asian scripts, Arabic, kana) as first class, and runs as one Kotlin Multiplatform
and Compose Multiplatform app on Android, Linux (JVM desktop) and the web (Kotlin/Wasm).
Working name, pending registry clearance. Studio: A System of Cells.

**Status: prompts P0–P9 are built.** The engine is real: geometry, font I/O, the trace chain,
fitting, construction, qa and the node-economy corpus, and scripts with shaping. Three UI
surfaces work: the sheet with Draw and Space, Learn, and the Workbook. The web build renders.
No one can make a font with it end to end yet: the UI has no import, save, capture or compile
path. The V1 prompts, P10–P19, close that gap. There is no phone or emulator in the build
environment, so on-device behaviour is verified by the owner only and never claimed here.

Read `CLAUDE.md` (the laws) before changing anything, then `TYPEWRIGHT_BUILD_BRIEF.md` and
`UI_SPEC.md`. Open questions live in `docs/OPEN_QUESTIONS.md`, licences in `THIRD_PARTY.md`.

## Licence

Typewright is licensed in parts (`docs/LICENSING.md`):

- **The app** (`ui`, `learn`, `campaign`, the three app shells, the Workbook and lesson text) is
  fair source under **FSL-1.1-ALv2** (`LICENSE`). Each release becomes Apache-2.0 two years
  after it's published.
- **The engine** (`core-*`, `engine-*`, `qa`, `scripts`, `shape-preview`, `compile`,
  `build-logic`, `tools`, `data/scripts`) is **Apache-2.0** (`LICENSES/Apache-2.0.txt`).
- **Template sheets and data packs** are **CC0-1.0**.
- **Fonts you make with Typewright are yours.**

Contributions come in under Apache-2.0 with a DCO sign-off (`CONTRIBUTING.md`). The name and
icon are covered by `TRADEMARKS.md`.

## Toolchain

Latest stable releases on 22 September 2026, checked against repository metadata and proven
by the builds below. All pins are in `gradle/libs.versions.toml`.

| | version | note |
|---|---|---|
| Gradle | 9.7.1 (wrapper) | |
| JDK to run the build | 21 | bytecode targets Java 17 |
| Kotlin | 2.4.20 | |
| Compose Multiplatform | 1.12.1 | explicit library coordinates; the plugin's `compose.*` accessors are deprecated |
| Android Gradle plugin | 9.4.1 | KMP Android targets use `com.android.kotlin.multiplatform.library` (`kotlin { android { } }`); the app uses AGP's built-in Kotlin |
| Android levels | compileSdk 37, targetSdk 36, minSdk 31 | 37 is required by Compose 1.12.1's androidx artifacts; 36 is Google Play's requirement from 31 Aug 2026; 31 is `TextRunShaper` |
| ktlint | 1.8.0 through Spotless 8.10.2 | rules in `.editorconfig`; wired into `check` |

## Build, run, test

Results are from runs on 22–23 September 2026 in the build container (Linux, JDK 21,
4 CPUs); rows marked "not run" were not. Android tasks need an SDK: set `ANDROID_HOME` or
put `sdk.dir` in `local.properties` (never commit it). AGP installs the platform and
build-tools it needs if the SDK licences are accepted.

| what | command | result |
|---|---|---|
| Everything: all tests on every target, ktlint, Android lint | `./gradlew check` | passes; `app-web`'s browser test needs Chrome or Chromium (set `CHROME_BIN` if it is not on the path) |
| Pure modules only, **no Android SDK** | `./gradlew -Ptypewright.android=false jvmTest spotlessCheck` | passes with `ANDROID_HOME` unset and no `local.properties` |
| Format the code | `./gradlew spotlessApply` | rewrites files in place |
| Android debug APK | `./gradlew :app-android:assembleDebug` | `app-android/build/outputs/apk/debug/app-android-debug.apk` (installs on API 31+; on-device behaviour is owner-verified) |
| Desktop app | `./gradlew :app-desktop:run` | opens the window (seen under Xvfb) |
| Desktop packages | `./gradlew :app-desktop:packageDeb` (or `packageRpm`, `packageAppImage`) | declared; not run at P0 (they need `jpackage` and the distro's packaging tools) |
| Screenshot harness | `./gradlew :ui:desktopTest --rerun` | renders composables headlessly to `ui/build/screenshots/*.png` (e.g. `draw-paper-rest.png`, `app-learn-open.png`) |
| Web, production | `./gradlew :app-web:wasmJsBrowserDistribution` | `app-web/build/dist/wasmJs/productionExecutable/`; serve it with `python3 -m http.server -d app-web/build/dist/wasmJs/productionExecutable 8080` |
| Web, development server | `./gradlew :app-web:wasmJsBrowserDevelopmentRun` | not run at P0 |
| Wasm tests under Node | `./gradlew wasmJsNodeTest` | the pure modules, `compile` and `shape-preview` |

Notes:

- **Without an Android SDK.** AGP 9.4.1 does configure the Android projects with no SDK
  present, and `jvmTest spotlessCheck` never touches them. `-Ptypewright.android=false`
  additionally leaves `compile`, `shape-preview`, `ui` and the three apps out of the build,
  so CI's `core` job does not depend on AGP's behaviour staying that way. The pure modules'
  Wasm tests also run in that reduced build (`wasmJsNodeTest`); it skips storing the npm
  lock, which describes the full build.
- **Binaryen and GitHub.** Production Wasm builds run Binaryen's `wasm-opt`, which KGP
  downloads from Binaryen's GitHub releases. Where github.com is unreachable, install the
  npm package (`npm install binaryen@130.0.0`, the version KGP 2.4.20 expects) and pass
  `-Ptypewright.wasmOpt=$PWD/node_modules/binaryen/bin/wasm-opt`; its output was
  byte-identical to the native build's. Yarn is not used (`kotlin.js.yarn=false`), so the
  web build needs no other download from GitHub; KGP's npm tooling does fetch its Karma
  fork from GitHub for `app-web`'s browser test.
- **The npm lock file** in `kotlin-js-store/` is committed so the Wasm build's JS
  dependencies stay pinned. After changing npm dependencies, refresh it with
  `./gradlew kotlinUpgradePackageLock` (not needed at P0).
- **Web locale.** A Chromium on Linux with no `LANG` reports the locale `en-US@posix`, and
  Compose 1.12.1 then renders nothing (`docs/OPEN_QUESTIONS.md`, P0 item 13).

## Modules

| module | Gradle path | targets | purpose |
|---|---|---|---|
| `core-geometry/` | `:core-geometry` | JVM, Wasm | curves, contours, fitting, metrics, node counts |
| `core-font/` | `:core-font` | JVM, Wasm | UFO 3 and designspace, naming, features, anchors, kerning |
| `engine-trace/` | `:engine-trace` | JVM, Wasm | the raster-to-outline chain (M1) |
| `engine-construct/` | `:engine-construct` | JVM, Wasm | construction geometry, booleans, spiro (M2) |
| `qa/` | `:qa` | JVM, Wasm | the quality gate's own checks and the Fontbakery result model |
| `qa/corpus/` | `:qa:corpus` | JVM, Wasm | node-economy distributions and the style detector; `data/node-economy-*.json` is copied into its generated resources |
| `learn/` | `:learn` | JVM, Wasm | still a P0 placeholder; the overlay, anatomy lens and scrapbook logic lives in `ui` (`ui/src/commonMain/.../ui/learn/`) |
| `learn/scenes/` | `:learn:scenes` | JVM, Wasm | lesson scene format and model |
| `campaign/` | `:campaign` | JVM, Wasm | the workbook engine |
| `scripts/` | `:scripts` | JVM, Wasm | per-script metrics, inventories, features; `scripts/templates/` holds the capture sheets |
| `compile/` | `:compile` | Android, desktop JVM, Wasm | `CompileBackend`: fontmake via system Python (desktop) and Chaquopy (Android) are both stubs; the hosted-endpoint backend (web) makes a real `fetch()` call but stays a stub result until an endpoint is deployed (`compile/README.md`) |
| `shape-preview/` | `:shape-preview` | Android, desktop JVM, Wasm | `Shaper`: TextRunShaper and Skiko real (P8), browser `FontFace` still a stub (P9) |
| `ui/` | `:ui` | Android, desktop JVM, Wasm | Compose UI; `TypewrightApp()`; the screenshot harness |
| `app-android/` | `:app-android` | Android | application shell |
| `app-desktop/` | `:app-desktop` | desktop JVM | application shell, Linux packaging |
| `app-web/` | `:app-web` | Wasm (browser) | application shell and `index.html` |

The first ten are **pure** (CLAUDE.md law 2): no Android target and no Android SDK on their
classpath, so an `android.*` import cannot compile there. Android code reaches them through
their JVM variant (`app-android` resolves `core-geometry`'s `jvmRuntimeElements`).

Conventions:

- Kotlin packages and Android namespaces come from the Gradle path (the convention plugins
  derive the namespaces; packages follow by hand): `com.asoc.typewright.` plus
  the path with `:` and `-` turned into `.` (`:qa:corpus` is `com.asoc.typewright.qa.corpus`,
  `:shape-preview` is `com.asoc.typewright.shape.preview`). The group is provisional.
- A new pure module needs only `plugins { id("typewright.kmp.pure") }`; a module with
  platform actuals uses `typewright.kmp.platform`. Both live in `build-logic/`. Pure modules
  name their JVM target `jvm`; platform modules name it `desktop`, because their JVM
  actuals are desktop-specific and Android has its own.
- Wasm tests run under Node except where Compose is involved (Skiko loads only in a
  browser); see `docs/OPEN_QUESTIONS.md`.

## CI

`.github/workflows/ci.yml`, three jobs on every push to `main` and every pull request; a
newer run cancels the one it supersedes, and no job uploads artifacts.

- `core`: JDK 21, `jvmTest` and ktlint for the pure modules with the runner's Android SDK
  unset.
- `android`: `:app-android:assembleDebug`, the platform modules' desktop and Android
  host tests including the screenshot harness, and a whole-project `spotlessCheck` (the only
  job with the Android SDK on PATH, so the only one that lints the Android-bearing modules too).
- `web`: the production Wasm distribution, the Wasm tests under Node, and `app-web`'s,
  `shape-preview`'s and `ui`'s tests in headless Chrome.

`gradle/actions/setup-gradle` runs with `cache-provider: basic`, its MIT-licensed cache,
rather than the default proprietary one.

## The build pack

The repository started as the Typewright build pack: everything a Claude Code session needs
to build the app, unzipped into the repository root.

```
README.md                      this file
CLAUDE.md                      repo-level instructions for Claude Code (laws, conventions)
TYPEWRIGHT_BUILD_BRIEF.md      the consolidated brief; supersedes the handoff where they differ
UI_SPEC.md                     layers, tokens, components, states, motion, derived from the explorer
PROMPTS_CLAUDE_CODE.md         P0–P9 with Sonnet/Opus routing
ui/
  typewright-explorer.html     the UI source of truth (open in a browser; phone-first; try Draw)
docs/
  TYPEWRIGHT_HANDOFF.md        original handoff (still canon for trace chain, geometry, scripts, pipeline)
  KNOWLEDGE.md                 what went wrong with Hyle Deco, and what the app teaches
  RESEARCH_font_quality.md     the research corpus with sources
  COMPETITOR_ANALYSIS.md       why this and not another tracer
  LESSONS_SCAFFOLD.md          scene format + Lineages / Craft / Scripts / Reading scaffolds
  DECISIONS.md                 the decision register from the design sessions
  OPEN_QUESTIONS.md            questions raised during the build, by prompt
  HOSTED_BUILD_ENDPOINT.md     the web compile backend's wire contract and zero-retention policy (not deployed yet)
  hyle-outline-explainer.html  the explainer that started this
data/
  node-economy-latin.json      per-style, per-glyph distributions + per-family counts (10 classes)
  node-economy-latin.compact.json  the app-side pack the explorer embeds
  node-economy-devanagari.json per-style, per-glyph distributions for Devanagari (3 classes: sans/serif/display)
  node-economy-kana.json       per-style, per-glyph distributions for Hiragana+Katakana (4 classes)
  exemplars.json               the ten lesson faces: family, file, cap and x-height ratios
  scripts/build_node_economy_corpus.py   regenerates the Latin corpus from the repository
  scripts/build_script_node_economy_corpus.py   regenerates the Devanagari/kana corpora
fonts/
  HyleDeco-Regular.ttf, HyleDeco-Italic.ttf   the fixtures
scripts/templates/hyle-all-templates.zip     capture template sheets, six scripts
tools/
  explorer-shots.mjs           renders every explorer screen to PNG for comparison
```

### How to use it

1. Open `ui/typewright-explorer.html` on a phone and on a desktop. Use the Draw sheet: swipe
   the puck, tap it, hold it and spin, drag its grip, swipe the header, open the map. Read
   the Notes for each screen. This is the app. In a plain browser the explorer needs a
   `[hidden]{display:none!important}` rule its usual host supplies;
   `NODE_PATH=<global node_modules> node tools/explorer-shots.mjs` injects it and renders
   every screen to `build/explorer-shots/`.
2. Run the prompts from `PROMPTS_CLAUDE_CODE.md` in order: P0 → P1 → P1b → P2 → P3 → P4 →
   P5a → P5b → P6 → P7 → P8 → P9 (P9, web, can run any time after P4), on the models the
   file names. Each prompt reads the files it needs.
3. Keep the explorer and the brief in sync: a UI change is made in the explorer first (in the
   chat app), then copied here, then implemented.

### Regenerating the corpus

Each run fetches `tags/all/families.csv` fresh from the pinned `google/fonts` commit (it is not
committed here — `google/fonts` states no licence for `tags/`, `docs/OPEN_QUESTIONS.md` item
121). Pass `--tags path/to/families.csv` instead for an offline run against a local copy.

```
cd data/scripts
python3 build_node_economy_corpus.py --top 30 --out ../node-economy-latin.json
python3 build_script_node_economy_corpus.py --script devanagari --out ../node-economy-devanagari.json
python3 build_script_node_economy_corpus.py --script kana --out ../node-economy-kana.json
```

Needs `fontTools` and network access to raw.githubusercontent.com (both scripts) and
fonts.google.com (the second script only, for real subset/category metadata; no API key
needed). Popularity ranking is not built; the corpus ranks by Google's /Quality/Drawing tag
score only.

### Status markers

[CANON] decided · [CONFIRM] needs Madhav · [SCAFFOLD] placeholder content shipped marked as
such. The list of open items is `TYPEWRIGHT_BUILD_BRIEF.md` §15.
