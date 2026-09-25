# Third-party software

Every dependency Typewright declares or pulls in, with its licence. Recorded at P0 (scaffold),
23 September 2026. **How each licence was verified** is in the last column: *POM* means the
`<licenses>` element of the artifact's own Maven POM (or its parent POM) in the Gradle cache;
*file* means the licence file shipped with the tool or, where the artifact ships none, the
licence file in the upstream repository. Update this file in the same change that adds,
removes or upgrades a dependency (CLAUDE.md).

GPL code is not linked (CLAUDE.md). Nothing here is telemetry or analytics.

## Shipped inside the apps

Resolved from `app-desktop` `testRuntimeClasspath` (a superset of its runtime classpath),
`app-android` `debugUnitTestRuntimeClasspath` (a superset of its debug runtime classpath; the
release variant declares the same dependencies) and `app-web` `wasmJsRuntimeClasspath`: 228
artifacts, all Apache-2.0 by their POMs except the two test-only ones listed further down.

| Component | Version | Licence | Verified |
|---|---|---|---|
| Kotlin standard library (`kotlin-stdlib`, `kotlin-stdlib-wasm-js`) | 2.4.20 | Apache-2.0 | POM |
| Compose Multiplatform: `org.jetbrains.compose.runtime`, `.foundation`, `.ui`, `.animation`, `.material` (ripple, pulled in by `desktop`), `.desktop` | 1.12.1 | Apache-2.0 | POM |
| Compose Multiplatform internals: `org.jetbrains.compose.collection-internal`, `.annotation-internal` | 1.10.0 | Apache-2.0 | POM |
| JetBrains ports of AndroidX: `org.jetbrains.androidx.lifecycle` 2.9.6, `.savedstate` 1.3.6, `.navigationevent` 1.1.0 | as listed | Apache-2.0 | POM |
| AndroidX (Android app, and a few desktop/web artifacts): `activity` 1.13.0 (`activity-compose`), `compose.*` 1.12.1, `lifecycle` 2.11.0, `savedstate` 1.4.0, `navigationevent` 1.1.1, `annotation`, `arch.core`, `autofill`, `collection`, `concurrent`, `core`, `customview`, `emoji2`, `graphics`, `interpolator`, `profileinstaller`, `startup`, `tracing`, `versionedparcelable`, `window` | as resolved | Apache-2.0 | POM |
| kotlinx.coroutines | 1.9.0 | Apache-2.0 | POM. Since P9, `shape-preview` also declares `kotlinx-coroutines-core` directly (its own `wasmJsMain`/`wasmJsTest`, `gradle/libs.versions.toml`'s `kotlinx-coroutines` version), pinned to this same 1.9.0 rather than only reached transitively through `ui`'s Compose dependency as before, the same reason `skiko-awt` below already gives — `BrowserFontFaceVisualPreview.renderPng`'s one genuinely async step (`FontFace.load()`) uses its real `Promise<T>.await()` |
| kotlinx.serialization core | 1.7.3 | Apache-2.0 | POM |
| kotlinx-atomicfu | 0.28.0 | Apache-2.0 | POM |
| kotlinx-browser | 0.5.0 | Apache-2.0 | POM. Since P9, `shape-preview` also declares this directly (its own `wasmJsMain`, `gradle/libs.versions.toml`'s `kotlinx-browser` version), pinned to this same 0.5.0 for the same reason as `kotlinx-coroutines-core` above — `BrowserFontFaceVisualPreview`'s typed `Document`/`HTMLCanvasElement`/`CanvasRenderingContext2D` bindings (no Compose dependency of its own to pull them in). Its `org.w3c.dom` package has no `FontFace` binding at all (checked directly against its own 0.5.0 klib before writing that class), so `FontFace` itself is bound by hand in `BrowserFontFaceVisualPreview.kt` — see that file's own KDoc |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 | POM |
| JSpecify | 1.0.0 | Apache-2.0 | POM |
| Guava `listenablefuture` | 1.0 | Apache-2.0 | POM |
| Skiko (`skiko`, `skiko-awt`, `skiko-awt-runtime-linux-x64`, `skiko-wasm-js`, `skiko-js-wasm-runtime`) | 0.150.1 | Apache-2.0 | POM; file (JetBrains/skiko `LICENSE`). Since P8, `shape-preview` also declares `skiko-awt`/`skiko-awt-runtime-linux-x64` directly (its own `desktopMain`/`desktopTest`, `gradle/libs.versions.toml`'s `skiko` version), pinned to this same 0.150.1 rather than only reached transitively through `ui`'s Compose dependency as before |
| `org.jetbrains.runtime:jbr-api` | 1.9.0 | Apache-2.0 | POM. Not declared by any module directly; pulled in transitively by Compose Multiplatform's desktop UI artifact (`org.jetbrains.compose.ui:ui-desktop`) onto `app-desktop`'s `runtimeClasspath` only (`./gradlew :app-desktop:dependencies --configuration runtimeClasspath`; a 1.5.0 constraint is raised to 1.9.0) — `app-android` and `app-web` never resolve it |
| xmlutil core (`io.github.pdvrieze.xmlutil:core`, `core-jvm`, `core-wasm-js`), used by `core-font`'s UFO 3 / glif reader | 1.0.2 | Apache-2.0 | POM. Resolved via `:core-font:dependencies`: the `jvm`/`wasmJs` platform artifacts pull in nothing beyond `kotlin-stdlib` — `core-font` uses xmlutil's raw pull-parser API, not `xmlutil-serialization`, so `kotlinx-serialization-core` 1.11.0 (declared only on xmlutil's common-metadata variant) is never resolved into a classpath and does not collide with Compose's 1.7.3 |
| kotlinx.serialization JSON (`org.jetbrains.kotlinx:kotlinx-serialization-json`, `-json-jvm`, `-json-wasm-js`; pulls in `kotlinx-serialization-core`, `-core-jvm`, `-core-wasm-js`), used by `qa:corpus`'s node-economy data loader (P1), directly on `jvmMain` only by `qa`'s `FontspectorCliChecker` to parse a `fontspector --json` report as a generic `JsonElement` tree (P1-qa; no `@Serializable` classes of its own, so `qa` does not apply the `kotlin-serialization` compiler plugin), by `learn:scenes`'s `LearnFaceResources` to parse `data/learn-faces/manifest.json` into `@Serializable LearnFaceEntry`/`LearnFaceManifestFile` (P6, Learn UI half), and since P9 by `compile`'s `HostedBuildProtocol` to encode/decode the hosted build endpoint's request/response JSON (`docs/HOSTED_BUILD_ENDPOINT.md`) | 1.11.0 | Apache-2.0 | POM. Resolved via `:qa:corpus:dependencies`, `:qa:dependencies`, `:learn:scenes:dependencies` and `:compile:dependencies` (`jvmRuntimeClasspath` and `wasmJsRuntimeClasspath`): only `kotlinx-serialization-core`/`-core-jvm`/`-core-wasm-js` 1.11.0 beyond `kotlin-stdlib` on both platforms, the same 1.11.0 xmlutil's common-metadata variant already names above, so none of them collide. `ui` depends on `qa`, `learn:scenes` and `compile` directly, all of which reach this artifact, so this reaches the apps' runtime classpath even though no app module names it directly |

### Inside Skiko's native and Wasm binaries

Skiko's jars and klibs carry no licence or NOTICE files, but `libskiko-linux-x64.so`
statically contains Skia and Skia's third-party libraries. Presence was checked from symbols
in `libskiko-linux-x64.so` 0.150.1 (for example `SkShaper`, `hb_buffer_create`,
`FT_Init_FreeType`, `u_errorName`, `png_create_read_struct`, `inflateInit`,
`XML_ParserCreate`, `jpeg_CreateDecompress`, `WebPDecode`). The web build's `skiko.wasm` is
stripped of names, so its contents were not checked; assume the same set until they are.
The Android app does not use Skiko; it draws with the platform's own Skia. Licences were read
from each upstream repository's licence file, not from a versioned copy.

| Library | Licence | Verified |
|---|---|---|
| Skia | BSD-3-Clause | file (google/skia `LICENSE`) |
| HarfBuzz | "Old MIT" | file (harfbuzz `COPYING`) |
| ICU | Unicode License v3 | file (unicode-org/icu `LICENSE`) |
| FreeType | FreeType License (FTL) or GPL-2.0, at the user's choice; Typewright uses it under the FTL | file (freetype `LICENSE.TXT`) |
| libpng | PNG Reference Library License v2 | file (libpng `LICENSE`) |
| zlib | Zlib | file (zlib `LICENSE`) |
| Expat | MIT | file (libexpat `COPYING`) |
| libjpeg-turbo | IJG and BSD-3-Clause (plus zlib for part of the SIMD code) | file (libjpeg-turbo `LICENSE.md`) |
| libwebp | BSD-3-Clause | file (libwebp `COPYING`) |

The BSD-style licences and the FTL require their notices in the documentation of binary
distributions. The apps have no licences screen yet; see `docs/OPEN_QUESTIONS.md` (P0).

## Tests only (never shipped)

| Component | Version | Licence | Verified |
|---|---|---|---|
| `kotlin-test`, `kotlin-test-junit` | 2.4.20 | Apache-2.0 | POM |
| JUnit 4 (`app-android` host tests, via `kotlin-test-junit`) | 4.13.2 | EPL-1.0 | POM |
| Hamcrest core (via JUnit 4) | 1.3 | BSD-3-Clause ("New BSD License") | POM (parent) |
| Karma and launchers (`app-web` browser test), mocha: part of KGP's npm tooling below | — | MIT | package.json |

## Build tooling (never shipped)

| Component | Version | Licence | Verified |
|---|---|---|---|
| Gradle, including the committed wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) | 9.7.1 | Apache-2.0 | file (distribution `LICENSE`); the wrapper scripts carry Gradle's own Apache-2.0 notice and SPDX line |
| Kotlin Gradle plugin, Compose compiler Gradle plugin, Kotlin serialization compiler plugin (`org.jetbrains.kotlin.plugin.serialization`, applied by `qa:corpus` for its `@Serializable` data classes) | 2.4.20 | Apache-2.0 | POM |
| Compose Multiplatform Gradle plugin | 1.12.1 | Apache-2.0 | POM |
| Android Gradle plugin | 9.4.1 | Apache-2.0 | POM |
| Spotless Gradle plugin | 8.10.2 | Apache-2.0 | POM |
| ktlint (`ktlint-cli`, rule engine, standard rules), run by Spotless | 1.8.0 | MIT | POM |
| Node.js, downloaded by KGP from nodejs.org for Wasm builds and tests (npm comes with it) | 26.2.0 | MIT (plus the notices of its bundled components in the same file) | file (`LICENSE` in the Node distribution) |
| Binaryen (`wasm-opt`), downloaded by KGP from Binaryen's GitHub releases, or the npm package `binaryen` when `-Ptypewright.wasmOpt` points at it | version_130 | Apache-2.0 | file (WebAssembly/binaryen `LICENSE`) |
| KGP's npm tooling in `~/.kotlin/kotlin-npm-tooling`: webpack 5, webpack-cli, webpack-dev-server, Karma (JetBrains' fork, `github:Kotlin/karma#6.4.5`), mocha, TypeScript, sass and loaders, 415 packages | pinned by KGP | MIT 330, ISC 32, Apache-2.0 28, BSD-3-Clause 12, BSD-2-Clause 6, BlueOak-1.0.0 4, 0BSD 1, Python-2.0 1 (`argparse`), CC-BY-4.0 1 (`caniuse-lite`, data) | each package's `package.json` |
| Android SDK platform 37.0, build-tools 36.0.0 | — | Android Software Development Kit License Agreement | accepted in the SDK's `licenses/` |
| GitHub Actions: `actions/checkout` v7, `actions/setup-java` v6 | — | MIT | file (`LICENSE`) |
| GitHub Actions: `gradle/actions/setup-gradle` v6, used with `cache-provider: basic` | — | MIT for the action and basic caching; its default "enhanced caching" is a proprietary component, which CI does not enable | file (`LICENSE`, `DISTRIBUTION.md`) |

Webpack writes its MIT-licensed runtime bootstrap into `typewright.js`. The production bundle
carries no licence banners, which is one more reason the web app needs a licences page.

## Ported (reimplemented in Kotlin; not linked as a binary dependency)

| Component | Source | Licence | Notes |
|---|---|---|---|
| UFO glyph-name-to-file-name algorithm (`core-font`'s `dev.aarso.typewright.core.font.ufo.userNameToFileName`, `handleFileNameClash1/2`) | `fontTools.ufoLib.filenames` (`fontTools` 4.66.0), itself copied from `ufoLib` (`unified-font-object/ufoLib`, commit `8747da7`) | MIT (fontTools) and BSD-3-Clause (ufoLib) | Ported line-for-line so `core-font`'s UFO writer produces exactly the `.glif` file names a real UFO tool (RoboFont, FontForge) would; both licences' notices (fontTools' MIT notice, copyright 2017 Just van Rossum, and ufoLib's BSD-3-Clause notice, copyright 2005-2016 the RoboFab developers: Erik van Blokland, Tal Leming, Just van Rossum) are reproduced in full in `GlyphFileNames.kt`'s own header, above its `package` line. |

## Fetched for Learn lesson content

Real webfonts, not code, so they get their own section rather than a row in "Shipped inside
the apps": the seventeen faces `data/scripts/fetch_learn_faces.py` fetches from
`google/fonts` for the Learn strand's scenes (TYPEWRIGHT_BUILD_BRIEF.md §9,
docs/LESSONS_SCAFFOLD.md §1 — "every example is a real font loaded at build time", never
bundled as an image of type). Ten are the Lineages on-stage faces (one per era, crossfaded
live on the scrubber); seven are the "identify-it" exercise-bank faces
(docs/LESSONS_SCAFFOLD.md §2's own list), never shown on stage before the learner is quizzed
on them. Files live under `data/learn-faces/<slug>/`, one directory per family, each with the
Regular-weight font file (static or variable — `METADATA.pb`'s own listed file, no static
instancing needed for lesson rendering) and that family's licence text
(`OFL.txt`/`LICENSE.txt`, whichever `METADATA.pb`'s `license:` field names). The exact
filename, copyright string, designer and source URL for every face are in
`data/learn-faces/manifest.json`, generated by the same script and never hand-edited (this
repository's `data/` convention). Fetched from **google/fonts commit
`23e54b51ddffbc7713c583748e3bd86f62b1fa4a`** (pinned via `git ls-remote`, the same method
`build_node_economy_corpus.py` uses — see that script's own `resolve_source_commit`), 24
September 2026.

| Family | Copyright | Licence |
|---|---|---|
| EB Garamond | Copyright 2017 The EB Garamond Project Authors | OFL-1.1 |
| Libre Baskerville | Copyright 2012 The Libre Baskerville Project Authors | OFL-1.1 |
| Playfair Display | Copyright 2017 The Playfair Display Project Authors, RFN "Playfair Display" | OFL-1.1 |
| Zilla Slab | Copyright 2017, The Mozilla Foundation | OFL-1.1 |
| Work Sans | Copyright 2019 The Work Sans Project Authors | OFL-1.1 |
| Limelight | Copyright (c) 2010 by Sorkin Type Co, RFN "Limelight" | OFL-1.1 |
| Jost | Copyright 2020 The Jost Project Authors | OFL-1.1 |
| Source Sans 3 | © 2023 Adobe, RFN "Source" | OFL-1.1 |
| Inter | Copyright 2016 The Inter Project Authors | OFL-1.1 |
| UnifrakturMaguntia | Copyright (c) 2010 j. 'mach' wust, RFN "UnifrakturMaguntia"; Copyright (c) 2009 Peter Wiegel | OFL-1.1 |
| Libre Bodoni | Copyright 2012 The Libre Bodoni Project Authors | OFL-1.1 |
| Poppins | Copyright 2020 The Poppins Project Authors | OFL-1.1 |
| Libre Franklin | Copyright 2020 The Libre Franklin Project Authors | OFL-1.1 |
| Roboto Slab | Copyright 2018 The Roboto Slab Project Authors | **Apache-2.0** (`METADATA.pb`'s `license:` field reads `"APACHE2"`, not `"OFL"` — the one family here that is not OFL-licensed; see `docs/OPEN_QUESTIONS.md` P6 item 1) |
| Cormorant | Copyright 2015 The Cormorant Project Authors | OFL-1.1 |
| Open Sans | Copyright 2020 The Open Sans Project Authors | OFL-1.1 |
| Josefin Sans | Copyright 2010 The Josefin Sans Project Authors, RFN "Josefin Sans" | OFL-1.1 |

Verified from each family's own `METADATA.pb` at the pinned commit (`license:` field) and
its licence text file, fetched alongside the font and stored next to it — not hand-typed.
See `data/learn-faces/manifest.json` for the exact per-family source URLs, designer and
category fields, and SHA-256 of each downloaded file. A handful were spot-checked as real,
loadable sfnt fonts (not HTML error pages or truncated downloads) by
`learn/scenes/src/jvmTest/.../LearnFacesRealFontValidationTest.kt`, which in fact checks
every one of the seventeen through `core-font`'s own production `readSfntFont`.

## Embedded in the explorer

`ui/typewright-explorer.html` embeds 11 real fonts as base64 `@font-face` rules (licensing pass,
25 Sep 2026): Hyle Deco, this project's own font, and ten subsets of Google Fonts families
(20-22 glyphs each), subsetted to the explorer's own sample glyphs. Copyright lines are exactly
as each font's own name ID 0 states them; the full licence text for all 11 is
`ui/OFL-explorer-fonts.txt`, pointed to from an HTML comment at the top of the explorer file
itself. Four of the ten subsets started with a Reserved Font Name, which OFL-1.1 condition 3
forbids a Modified Version (a subset is one) from keeping — each was renamed inside its own
`name` table (only that table touched; every other table, and `head` apart from its
`checksumAdjustment`, verified byte-identical to the original subset).

| CSS alias | Family | Copyright | Licence | Notes |
|---|---|---|---|---|
| `HyleDeco` | Hyle Deco | Copyright 2026 The Hyle Deco Project Authors | OFL-1.1 | This project's own font (`fonts/OFL.txt`), not a Google Fonts subset. |
| `ex-garalde` | EB Garamond | Copyright 2017 The EB Garamond Project Authors | OFL-1.1 | |
| `ex-transitional` | Libre Baskerville | Copyright 2012 The Libre Baskerville Project Authors | OFL-1.1 | |
| `ex-didone` | Playfair Display, RFN "Playfair Display" | Copyright 2017 The Playfair Display Project Authors | OFL-1.1 | Renamed inside the font to "Explorer Didone". |
| `ex-geometric` | Jost | Copyright 2020 The Jost Project Authors | OFL-1.1 | |
| `ex-grotesque` | Work Sans | Copyright 2019 The Work Sans Project Authors | OFL-1.1 | |
| `ex-neogrotesque` | Inter | Copyright 2016 The Inter Project Authors | OFL-1.1 | |
| `ex-humanist` | Source Sans 3, RFN "Source" | © 2023 Adobe | OFL-1.1 | Renamed inside the font to "Explorer Humanist". |
| `ex-slab` | Zilla Slab | Copyright 2017, The Mozilla Foundation | OFL-1.1 | |
| `ex-artdeco` | Limelight, RFN "Limelight" | Copyright (c) 2010 by Sorkin Type Co | OFL-1.1 | Renamed inside the font to "Explorer Art Deco". |
| `ex-blackletter` | UnifrakturMaguntia, RFN "UnifrakturMaguntia" | Copyright (c) 2010 j. 'mach' wust; Copyright (c) 2009 Peter Wiegel | OFL-1.1 | Renamed inside the font to "Explorer Blackletter". |

## The template pack's Noto reference layers

`scripts/templates/hyle-all-templates.zip` (CC0-1.0, `scripts/templates/LICENSE`) is the owner's
own template pack and is never modified by this repository's tooling; its sha256 is recorded in
the `*-manifest.json` files beside it. Its non-Latin sheets each carry a `reference_delete_me`
layer, deleted before export (`HOW_TO_USE.md`), holding a faint reference glyph copied from a
Noto font — not CC0 like the rest of the sheet:

| Sheets | Source font | Copyright | Licence |
|---|---|---|---|
| Hiragana, Katakana | Noto Sans JP | (c) 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source' | OFL-1.1 |
| Devanagari | Noto Sans Devanagari | Copyright 2022 The Noto Project Authors (`github.com/notofonts/devanagari`) | OFL-1.1 |
| Naskh | Noto Naskh Arabic | Copyright 2022 The Noto Project Authors (`github.com/notofonts/arabic`) | OFL-1.1 |
| Nastaliq (12 of 49 sheets) | Noto Nastaliq Urdu | Copyright 2022 The Noto Project Authors (`github.com/notofonts/nastaliq`) | OFL-1.1 |

Latin has no reference layer. Noto Sans JP's copyright verified from its own name ID 0, fetched
from `google/fonts` (`ofl/notosansjp/NotoSansJP[wght].ttf`, main branch, 25 Sep 2026, not
committed here); the other three are the same families already in `fonts/` (see below) and
Noto Nastaliq Urdu's copyright as given by the review that found this gap. Full licence text:
`scripts/templates/OFL.txt`.

## Already in the repository before P0

| Component | Licence | Notes |
|---|---|---|
| fontTools (used by `data/scripts/build_node_economy_corpus.py` and `data/scripts/build_script_node_economy_corpus.py`) | MIT | Not installed or run by the build. |
| Playwright (used by `tools/explorer-shots.mjs`) | Apache-2.0 | Resolved from the machine's global install, not a project dependency. |
| Hyle Deco Regular and Italic in `fonts/` (Copyright 2026 The Hyle Deco Project Authors) | OFL-1.1 | Verified from the fonts' name table (IDs 0 and 13); the full licence text is now in `fonts/OFL.txt`. Regular ships inside the app itself: `ui`'s `HyleDecoProjectFontBytes.kt` embeds it in `ui`'s own main source set (Overlay tab's project layer, P6), not only as a test fixture (`core-font`'s `HyleDecoRegularTtfBase64.kt`, `commonTest`). Italic is not currently read by any module. |
| Noto Sans Devanagari Regular and Noto Naskh Arabic Regular in `fonts/` (Copyright 2022 The Noto Project Authors, `github.com/notofonts/devanagari` and `github.com/notofonts/arabic`) | OFL-1.1 | Verified from the fonts' name table (IDs 0 and 13), same method as Hyle Deco above; the full licence text is now in `fonts/OFL.txt`. Added P8 as `SkikoShaperTest`'s real conjunct/joining fixtures (`shape-preview`, `desktopTest`) — the module `docs/ARCHITECTURE_REVIEW.md` section 4.3 already named these two families by name. Test fixtures only (Devanagari also embedded as a `wasmJsTest` fixture, `shape-preview`'s `EmbeddedNotoSansDevanagariBytes.kt`). |
