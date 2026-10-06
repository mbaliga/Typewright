# Typewright: multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN. Nothing in this document has been built, run on a device, signed or submitted to
> a store.** Every claim about a target platform is labelled with its evidence class (§0). Written
> 6 Oct 2026 against commit `7ff4409` (the merge of PR #3). Per `PROMPTS_V1.md` §3 only the lead agent commits, so a
> planning agent handed this file back and did not commit it. Typewright has no PROGRESS or STATE
> file (its living state is the Status paragraph of `README.md`), so a platform track updates only
> its own §4 row here and records progress in the PR that lands its step.

**Identifiers.** The repo keeps three decision registers that reuse the numbers D1 to D14. This plan
writes `DEC-Dn` for `docs/DECISIONS.md` (D1 to D17, the design sessions), `V1-Dn` for `PROMPTS_V1.md`
§2, `docs/V1_SCOPE.md` §6 and `docs/V1_TRIAGE.md` (D1 to D14); `docs/PROJECT_MODEL.md` §14 has a third D1 to D14.
The master program's `TW:D5` is `V1-D5` (the Android compile route), read there as the fontc
decision. `OQ-n` is a master-program owner question (`Personal-Tracker/PORTING_PROGRAM.md` §8), `item n`
a numbered entry in `docs/OPEN_QUESTIONS.md`, `F-n` a master shared-foundation item (§6 there), `R1` to
`R12` the master's program rules (§3 there). `WP` ids in §6 are work packages (the lead assigns prompt numbers).

## 0. Evidence labels (never dropped)

`LAB` · `CI (hosted VM) evidence` · `EMULATOR EVIDENCE` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` ·
`SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` · `SYNTHETIC` · `CI-ONLY / NOT RUN` ·
`NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV) · `PLAN` (this document) ·
`NOT-APPLICABLE (<reason>)` · `CONTAINER-BUILD-ONLY` (this container: JVM x86_64 compile and tests, nothing else) ·
`BROWSER-HEADLESS`. Labels this repo does not use today (LAB, EMULATOR EVIDENCE, SIMULATED, VIRTUALIZED, SYNTHETIC,
CI-ONLY / NOT RUN) are kept so a later entry can use them unchanged; `CI (hosted VM)` below abbreviates
`CI (hosted VM) evidence`. Law 4 extends to every new target: the build environment has no Mac, Windows PC, iPhone,
iPad or Ubuntu Touch phone, so nothing here is claimed to work on any of them.

## 1. What this repo is, in porting terms

- **Product.** Typewright (working name, pending name clearance V1-D2; studio A System of Cells;
  application id `com.asoc.typewright`) turns hand-made letterforms into fonts of Google Fonts
  submission quality and teaches what quality means (`README.md`). One Kotlin Multiplatform and
  Compose Multiplatform app. A project is a directory of UFO 3 and JSON (law 7).
- **State** (`README.md` Status, `PROMPTS_V1.md` §0, `docs/V1_SCOPE.md`, `golden-path/passing-steps.txt`).
  P0 to P9 are built; P10 and P11 (engine half) are merged (PRs #2 and #3); P12 to P19 are not built.
  Only golden-path steps 3 and 7 of 7 are listed as passing. All three compile backends are stubs
  (`compile/src/{desktopMain,androidMain,wasmJsMain}`). `app-desktop` and `app-web` still build their
  workspace with `createProjectWorkspace(scope)` defaults, which is the in-memory storage; only
  `app-android/TypewrightApplication.kt` wires real storage. The `ui/v1-screens/` mockups are absent
  (item 123). There is no release tag.
- **Targets today.** Android (`app-android`; debug APK in CI; minSdk 31; owner-verified only). Linux JVM
  desktop (`app-desktop`; CI tars `createDistributable` on every push to `main`; deb, rpm and
  "AppImage" formats are declared and have never been run). Web Kotlin/Wasm (`app-web`; a labelled
  preview with no compile per V1-D4 and no layer-one QA). Windows and macOS are "later, on the same
  desktop target" and iOS/iPadOS "later, behind the compile interface with fontc" (DEC-D1, brief §3).
  `docs/V1_SCOPE.md` §4 puts iOS, Windows and macOS out of V1; `docs/SCREENS_V1.md` §7 names iOS.
  Ubuntu Touch appears nowhere in the repo (a grep of tracked `.md .kt .kts .yml .toml .html .py .json .yaml` for
  "ubuntu touch", "lomiri", "ubports", "openstore", "webapp-container" and "click package" finds nothing).
- **Stack.** Kotlin 2.4.20, Compose Multiplatform 1.12.1 (Skiko 0.150.1), Gradle 9.7.1 on JDK 21
  (bytecode 17), AGP 9.4.1 with `com.android.kotlin.multiplatform.library`, kotlinx-serialization 1.11.0,
  coroutines 1.9.0, xmlutil 1.0.2. Own design language (`UI_SPEC.md`, `docs/SCREENS_V1.md`,
  `ui/typewright-explorer.html`); **no Hyle dependency** (no `hyle` in any build file; `HyleDeco` is a font
  fixture). Python 3 only in build-time tools and data generators. No JNI, NDK, OpenCV or Rust is linked.
  Planned, not linked: fontc and Fontspector (Rust), Chaquopy (spike).
- **Size** (measured 6 Oct 2026 at `7ff4409`). `git ls-files '*.kt' '*.kts' | wc -l` gives 567 files and
  84,455 lines. Main sources (excluding `commonTest jvmTest desktopTest androidTest androidHostTest
  wasmJsTest test` directories): 290 files, 41,165 lines. Tests: 254 files, 42,408 lines, 1,690 `@Test`
  annotations (`git ls-files '*.kt' | xargs grep -h '@Test' | wc -l`). Python: 9 files, 2,473 lines.
  726 tracked files in all.

## 2. Portable core vs platform-bound layers

Lines are main-source Kotlin (command above, per module directory). "Common" means no `java.*`, `javax.*` or
`android.*` import in `commonMain`: `git ls-files '*/src/commonMain/*.kt' | xargs grep -l -E
'^import (java|javax|android)\.'` prints nothing, which is what makes Kotlin/Native targets possible
without engine rewrites (`docs/ARCHITECTURE_REVIEW.md` §5 item 8).

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `core-geometry`, `core-font`, `engine-trace`, `engine-construct` (Apache-2.0) | geometry, fitting, UFO 3 and sfnt I/O, trace chain, construction | common (jvm + wasmJs/node today) | 12,144 | iOS native targets are a convention-plugin change only. `core-font` needs xmlutil 1.0.2: iOS artifact **unverified**. UFO writer is cubic-only (item 125). |
| `qa`, `qa:corpus` (Apache-2.0) | layer-two checks, Fontspector result model, node-economy corpus, style detector | common + 2 expects | 2,879 | `platformLayerOneChecker()`: jvm shells out to a `fontspector` binary with `ProcessBuilder` (`LayerOneChecker.jvm.kt`), wasm is an honest Unavailable stub. `readCorpusResourceText`: classloader on jvm, Node `fs` on wasm (fails in a browser, V1_TRIAGE gap 3). |
| `learn`, `learn:scenes`, `campaign` (FSL) | lesson scene model, bundled OFL learn faces, workbook engine | common + 3 expects | 2,757 | Resource readers (`readSceneResourceText`, `readLearnFaceResourceBytes`, `readCampaignResourceText`) have jvm and wasm actuals only. |
| `scripts` (Apache-2.0) | per-script metrics, inventories, features, capture templates (CC0) | common | 2,582 | Not yet reached by any screen (V1_TRIAGE gap 4). |
| `project` (Apache-2.0) | `ProjectSession`, history, locks and diffs (law 1), autosave, `ProjectZip`, `SwapProtocolStore`, store interfaces | common + jvmMain | 6,268 | `jvmMain` (`AtomicFiles`, `FileSystemProjectStore`, `FileAppConfigStore`) uses `java.nio` (`FileChannel.force`, `Files.move` with ATOMIC_MOVE and REPLACE_EXISTING, `tryLock`). `SwapProtocolStore` over the `DocumentOps` interface is common code: it is Android's answer to "no atomic rename-over". |
| `project:storage` (Apache-2.0, platform) | storage actuals | android + desktop + wasmJs | 1,292 | desktop: zenity, then kdialog, then Swing `JFileChooser`; config at `$XDG_CONFIG_HOME/typewright`. android: SAF. wasm: File System Access, OPFS, IndexedDB, Web Locks, zip fallback. |
| `compile`, `shape-preview` (Apache-2.0, platform) | `CompileBackend`; `Shaper` | android + desktop + wasmJs | 601 + 709 | All three compile backends are stubs. Shapers: Skiko (desktop, clusters), TextRunShaper (Android, no clusters), browser stub. `desktopTest` pins only `skiko-awt-runtime-linux-x64`. |
| `ui` (FSL) | the one Compose tree: sheet, rooms, puck, deck, Learn, Workbook, palette | common | 11,227 | 568 `androidx.compose` imports in main sources (all Compose MPP), zero `android.*` in `commonMain`. Platform actuals: `LearnFaceFonts.{android,desktop,wasmJs}.kt` only. Palette handles Ctrl and Meta (`CommandPalette.kt`). |
| `app-android`, `app-desktop`, `app-web` (FSL) | thin shells | android-bound / JVM / web | 57 + 39 + 55 | `app-desktop` is plain `kotlin-jvm` plus `compose.desktop.currentOs`: the same module builds on macOS and Windows hosts. |
| `golden-path` (FSL, test only) | acceptance harness, one headless desktop-JVM test per step, ratchet | JVM | 5 test files | Depends on `:compile`, so it sits in the `withAndroid` block of `settings.gradle.kts`. |
| `build-logic` (Apache-2.0) | convention plugins `typewright.kmp.pure`, `.kmp.platform`, `.jvm`, `.lint` | Gradle | 555 | The one place that declares targets for all 15 KMP modules: no `iosArm64`, `macosArm64`, `mingwX64` or `linuxX64` anywhere. |

**Platform-bound seams that matter.** The repo has 9 `expect` declarations and 22 `actual`s (grep
`^\s*(internal |public )?(expect|actual) ` over tracked `.kt`): 4 resource readers, `platformShaper`,
`platformLayerOneChecker`, `platformCompileBackend`, and two learn-face font functions.

| API | Where | Porting impact |
|---|---|---|
| KMP target declarations | `build-logic/.../KmpPureConventionPlugin.kt`, `KmpPlatformConventionPlugin.kt`; `app-desktop/build.gradle.kts` (`TargetFormat.Deb, Rpm, AppImage`) | macOS and Windows: no target change, a `TargetFormat` change and a build on that OS. iOS: new targets in both plugins plus an `app-ios` module. |
| `java.nio`, `ProcessBuilder` | `project/src/jvmMain`, `qa/src/jvmMain`, `project/storage/src/desktopMain` | Fine on macOS and Windows JVM in principle; NTFS replace-over-open-file and lock behaviour are **unverified**. Unavailable on Kotlin/Native: iOS needs a new store. |
| Desktop picker and config dir | `DesktopFolderPicker.kt`, `XdgConfigDir.kt`, `docs/PROJECT_MODEL.md` §5 | macOS and Windows fall through to Swing and still read `~/.config/typewright`. Neither zenity nor kdialog exists in a Flatpak or a Click sandbox. |
| Skiko shaper | `shape-preview/.../SkikoShaper.kt`, `libs.versions.toml` | `compose.desktop.currentOs` resolves macOS and Windows natives; the test-only pin is Linux x64. Linux arm64 needs `skiko-awt-runtime-linux-arm64`, not pinned. Whether Skiko's iOS artifact exposes `SkShaper` is **unverified**. |
| External binaries on `PATH` | `LayerOneChecker.jvm.kt`, `SystemPythonFontmakeBackend.kt` | `python3` and `fontspector` are detected, never bundled (`PROMPTS_V1.md` P18). Per `docs/ARCHITECTURE_REVIEW.md` §3 a Flatpak sandbox would not see host Python (UNVERIFIED). On Windows `python3` is rarely the name. This, not the UI, is the real per-OS work. |
| Web platform APIs | `project/storage/src/wasmJsMain/*`, `app-web/.../Main.kt` | A webapp container needs WasmGC (Chrome 119 or later floor, `docs/ARCHITECTURE_REVIEW.md` §3) plus these APIs: availability unknown. Compose fetches Noto from fonts.gstatic.com for uncovered code points (ARCHITECTURE_REVIEW §5 item 7): a law 3 exposure unless guarded. |
| CI | `.github/workflows/ci.yml` | ubuntu-latest only, tag-pinned actions, artifacts `continue-on-error` because the Actions storage is full. |

## 3. Binding rules this port must not break

- **Law 1** (`CLAUDE.md`): the user's drawing is the source of truth; approved glyphs stay locked
  (`docs/PROJECT_MODEL.md` §7). Any new store must keep the lock and diff files byte-compatible.
- **Law 2** (`CLAUDE.md`): `core-*`, `engine-*`, `qa`, `learn`, `campaign`, `scripts`, `project` carry no
  `android.*` import and CI's SDK-free `core` job proves it. Read as "common-first" (ARCHITECTURE_REVIEW §5
  item 8). `iosMain` source sets are allowed only in modules that already have platform actuals; the four
  geometry and font cores get none.
- **Law 3** (`CLAUDE.md`, amended by `docs/LICENSING.md` §4): no telemetry, analytics or phoning home, ever;
  the only network calls are the ones the user asks for. No crash-reporting SaaS on any target; crashes are
  local and user-shared (P18). CI lanes fetch build dependencies, which is build time, not the app. Guard the Noto
  fetch on any web container. TestFlight's automatic tester crash reports are an OS-level egress the
  privacy copy must disclose (OQ-2).
- **Laws 4 and 5, D15**: stubs say so in the UI and nothing is claimed on a device it was not run on; any judging
  number comes from `qa/corpus`, otherwise "our heuristic"; the Hyle Deco fixture counts do not move without a
  written reason, and new lanes re-run them rather than restate them.
- **Law 6** (`CLAUDE.md`, `docs/SCREENS_V1.md`, `docs/INTERACTION_V1.md`, `ui/typewright-explorer.html`): the
  design is the UI; no improvised components; "if a screen is missing from all of them, stop and say so".
  Ports get no native redesign and no native navigation chrome ("no screen adds its own back button, tab row or
  title bar"). **Ubuntu Touch is the wasm head in a webapp click, never a QML rewrite** (brief §3: "Rejected
  again: Flutter; Rust core plus per-platform native UI").
- **Law 7**: a project is UFO 3 and JSON; every file must open in FontForge, Glyphs or RoboFont.
- **Law 8 and C3, C5**: colour is meaning only (violet selected, cyan snapping); every role has a shape;
  never green as meaning; one red, one use (the deck's Close circle). This binds every target equally.
- **Licence split** (`docs/LICENSING.md`, `tools/check_licences.py`, CI "Licence split"): app modules FSL-1.1-ALv2,
  engine modules Apache-2.0, data CC0; every source file carries an SPDX line; an Apache module may never
  depend on an FSL one; **a new Gradle module, or a new directory holding a checked source file (`.kt .kts .mjs
  .js .py .sh`), must be added to `LICENCE_BY_DIR` or CI fails**. New native fontc and Fontspector tooling lands
  only in the Apache modules `compile` and `qa`. F-Droid and IzzyOnDroid
  cannot list the FSL app; other stores are an owner question (OQ-4).
- **No GPL or AGPL linked, ever**; record every dependency in `THIRD_PARTY.md` as it is added, including Rust
  crates; Skia, FreeType and libpng notices ship in About on every target (item 14, P18).
- **Standing rules for P10 to P19** (`PROMPTS_V1.md` §3): only the lead commits; one PR per prompt into `main`;
  every code prompt ends by running `./gradlew :golden-path:goldenPath`; passing steps are ratcheted in
  `golden-path/passing-steps.txt` and never regress; KDoc says what and why, not build history; "your font"
  means the open project.
- **Scope gates** (`docs/V1_SCOPE.md` §1, §4): V1 is Linux desktop and Android with web as a preview; iOS,
  Windows and macOS are out. `docs/V1_SCOPE.md` §4 stays untouched until the owner moves a platform in.

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe | "Typewright Preview": the Kotlin/Wasm head (`:app-web`) in a webapp-container click, labelled a preview (R12). JVM-in-click is recorded, not scheduled. A QML rewrite is excluded by law 6. | WasmGC in the 24.04-2.x container engine (unknown); loading bundled content without a network call (unknown); storage APIs (unknown); preview limits (no compile, no layer one, data loaders until P14); no UT device on record (OQ-1); OQ-10 gate | 4 (2 to 3 if WasmGC is present; the JVM spike, 6 to 10 with high failure risk, is outside the 4) | PLAN |
| Linux desktop | already-done (build and tarball exist; packaging, storage wiring and per-arch pins are not) | Finish P18's packaging: app-image tarball plus `install.sh`, Flatpak for the Deck, no true `.AppImage` unless the owner wants one; pin `skiko-awt-runtime-linux-arm64`; picker under a sandbox; correct the "AppImage" naming (LX-1). | P11 UI half (desktop still in-memory); compile backend stub (V1-D5); Flathub under FSL unknown (OQ-4); no application icon in the repo; packaging never run | 2 | PLAN |
| iOS / iPadOS | moderate | Compose Multiplatform iOS on the existing common code: flag-gated `iosArm64` and `iosSimulatorArm64` targets, `iosMain` actuals for 9 expects, a store over `SwapProtocolStore` plus a document picker, an `app-ios` SwiftUI shell, and fontc and Fontspector as Rust static libraries in `compile` and `qa`. iPadOS first (master §4.3). | OQ-10; fontc decision (V1-D5, proposed DEC-D19); `SkShaper` on iOS unverified; xmlutil iOS artifact unverified; no iOS screens designed (law 6); Apple account and delivery route (OQ-2); no device | 12 (10 to 14) | PLAN |
| macOS | native-fit | Same JVM desktop build on `macos-latest`, `TargetFormat.Dmg` and `Pkg`, per-OS Skiko pin, config-dir function, `⌘` trigger chips, signing and notarisation as disabled templates. | OQ-10; Developer ID (OQ-3); no Mac on record (OQ-5), so device gates stay NOV; `packageVersion = "0.0.1"` likely rejected by macOS formats (unverified) | 3 | PLAN |
| Windows | native-fit | Same JVM desktop build on `windows-2025`, `TargetFormat.Msi`, NTFS spike for atomic replace and locks, `%APPDATA%` config dir, unsigned until OQ-3. | OQ-10; NTFS semantics unverified; signing route (OQ-3); the Dell only while it is Windows (OQ-5); `python3` and bundled-tool story | 3 | PLAN |

Effort figures are the master program's §5 cells (estimates, one engineer who knows the stack, spikes passing
first time; no owner time, accounts, store review or device sessions) and sum to 24. The 3-week desktop cells
assume fontc, Fontspector and Python stay "detected, not bundled" (the P18 posture); bundling them is about one
more week per OS (estimate) and rides the Rust lane iOS pays for once (IOS-6). **Ubuntu Touch is a labelled
reframe, not a native port**; Waydroid (OQ-21) is not Typewright's answer because a web head exists, and an APK
run under it would be owner-device evidence only.

## 5. Tier and sequencing

**Tier B (port in sequence), per the master program's §5 row.** The stack already is the port: one Compose
tree over a common core, three shipping targets, and the repo's own register pre-plans Windows and macOS on the
JVM desktop target and iOS on Compose iOS plus fontc. Desktop ports are packaging, CI and signing work.
iOS is a bounded job (9 expects, a native store, native compile libraries). It is not tier A because the
owner's V1 ordering forbids starting; it is not tier D because the preparation is low-risk and can sit behind
flags. The Ubuntu Touch cell is a reframe (R12) and does not change the row's tier.

**Waves** (master §7; same ids). Build-entry is what repo CI must show; device-entry is what the owner supplies.

| Wave | Typewright's part | Repo-local build-entry | Device-entry |
|---|---|---|---|
| P-0 Foundation (pre-wave proof, no F-dependency) | Linux packaging pilot (LX-2 to LX-4): compile-only lanes, nothing released | `ci.yml` green; format chosen by P18 (OQ-4, OQ-10) | none (build proof only) |
| P-UT a | Kotlin/Wasm click, **only after** the engine and WasmGC spike (UT-1), with a Morph PWA bookmark as the fallback | V1 P14 (browser data loader) and P18 (static web build, locale and Noto guards) merged; OQ-10 | a UT device (OQ-1) or an explicit CI-only waiver |
| P-LX | Typewright pilot first in the Linux order; macOS and Windows lanes **wait for OQ-10** | P11 UI half merged (real desktop storage); V1 Linux golden path as V1_SCOPE §5 requires | Deck Desktop and Gaming Mode, Redmagic-Edge under Termux:X11, HiDPI (all NDV) |
| P-iOS | after Clavis, nooz, csapp and hnm; **after OQ-10 and the fontc decision (V1-D5)** | the CMP-iOS recipe proven on Clavis; `macos-latest` lanes; V1 shipped or the owner's amendment (§5.1) | Apple Developer Program and delivery route (OQ-2); the iPad Pro M4; iPhone items NDV |
| P-mac | every CMP app's DMG; **after OQ-10** | P-LX binaries; Developer ID and notarisation secrets (OQ-3) | no Mac on record: NOV until OQ-5 changes |
| P-win | every CMP app's MSI and winget; **after OQ-10** | P-LX binaries; a signing route or accepted unsigned (OQ-3) | the Dell while it is Windows (OQ-5); afterwards `CI (hosted VM)` only |

**Gate before any wave** (master §5): `docs/V1_SCOPE.md` §4 forbids iOS, macOS and Windows until V1 (OQ-10);
every code prompt runs `:golden-path:goldenPath`; FSL-1.1-ALv2 store compatibility is open (OQ-4); only the lead
commits. Typewright is public (`PROMPTS_V1.md` P10.0), so hosted minutes are not the OQ-20 blocker here; the full
artifact storage is, so new lanes upload nothing (R6).

### 5.1 Proposed amendment for OQ-10 (not applied; the owner rules)

Proposed wording, to be added to `docs/V1_SCOPE.md` §4 and `docs/SCREENS_V1.md` §7 only if the owner agrees
(proposed **DEC-D18**): *"Not in V1 means no UI design for that platform and no change to the V1 path. Build-system
preparation that is a no-op unless a flag is set, `iosMain` stubs that say so in the UI, new CI workflow files
that never gate V1, packaging directories and documents may proceed as a parallel track (P20 and later), one PR
per prompt, golden path run on each."* Allowed under it: `-Ptypewright.ios` flag-gated targets and module,
actuals and honest stubs in non-UI modules, `packaging/` and `app-ios/` directories, new workflows, device
checklists. Not allowed until the owner opens it: any SwiftUI, iPad or iPhone screen design, any change to the
look or layout in `ui`, store submission, signing, or a change to `ci.yml`.

## 6. Work breakdown

**How the plan respects R1 to R4.** New work lives in new directories or source sets: `packaging/<os>/`,
`app-ios/`, `iosMain`, `compile/native/`, `qa/native/`, `docs/release/DEVICE_CHECKLIST_*.md` (P18 already creates
`docs/release/`). `ci.yml` is never edited; each lane is a new workflow file with SHA-pinned actions, `contents:
read`, and no `upload-artifact` (R3, R6). The only existing build files touched are `build-logic` (two convention
plugins), `settings.gradle.kts` (one gated include), `shape-preview/build.gradle.kts` and `libs.versions.toml`
(per-OS Skiko runtime), `app-desktop/build.gradle.kts` (`targetFormats`) and `tools/check_licences.py`
(directories and `.rs`/`.swift` suffixes), each in a step that names it and each meant to leave the existing gate
unchanged (R2). Cores come first (R4). This container has `jpackage` 21.0.12.1, `cargo` 1.97 and a `docker` CLI
(daemon not checked) and no Swift, Xcode, Clickable, flatpak-builder or Chrome; steps say what it cannot verify.

### 6.1 Ubuntu Touch (reframe)

| WP | Step | Where | Workflow | Done when | In this container? |
|---|---|---|---|---|---|
| UT-1 | Spike page: WasmGC (`WebAssembly.validate` on a minimal GC module), `showDirectoryPicker`, OPFS, IndexedDB, Web Locks, `CompressionStream("deflate-raw")`, `navigator.language` shape (item 13's `en-US@posix` blank page), `visibilitychange` on backgrounding, engine user-agent, how local content loads | `packaging/ubuntu-touch/spike/` (static, no network) | `ubuntu-touch.yml` builds the click only | owner's filled report in `docs/release/UT_SPIKE_REPORT.md` with go or no-go | No. Click build in CI only (`CI-APPROX — NOT DEVICE EVIDENCE`); verdict NDV |
| UT-2 | Web-preview prerequisites **owned by V1, consumed here**: browser data loader (V1_TRIAGE gap 3, P14), locale guard (item 13), Noto and gstatic guard (ARCHITECTURE_REVIEW §5 item 7), static build (P18) | existing modules | existing `ci.yml` web job | merged on `main` | Wasm tests: `BROWSER-HEADLESS` in CI |
| UT-3 | Click: manifest, apparmor with **no `networking` group unless** a loopback static server is chosen (OQ-15), launcher, copy of `:app-web:wasmJsBrowserDistribution`; label "Typewright Preview". Identifier is a placeholder until a NAMES.md row exists (R11) | `packaging/ubuntu-touch/` | `ubuntu-touch.yml`: Gradle web distribution, then Clickable 8.10.0 in a digest-pinned `clickable/ci-ut24.04-1.x-arm64`, click-review lint | click builds, lint output pasted, no artifact upload | No. `CI (hosted VM)` |
| UT-4 | `docs/release/DEVICE_CHECKLIST_UT.md`: launch, WasmGC, touch gestures (puck, hold, two-finger swipe), and **edit, background, kill, reopen** (golden-path step 7 analogue). Lomiri freezes an unfocused app about 1.5 s after suspend (master §4.1), so `app-web`'s `visibilitychange` flush is the hook to test | `docs/release/` | none | owner fills; unknown until then | No. NDV |
| UT-5 | JVM-in-click (Linux arm64 jpackage image under XMir and `unconfined`): **not scheduled.** Needs `skiko-awt-runtime-linux-arm64`, the S-UT1 verdict, and an open-source-only manual review that FSL-1.1-ALv2 may not satisfy (unknown, OQ-4) | none yet | none | owner asks | No |

### 6.2 Linux desktop (already-done: finish)

| WP | Step | Where | Workflow | Done when | In this container? |
|---|---|---|---|---|---|
| LX-1 | **Documentation fix proposal for P18: the "AppImage" misnomer.** Compose's `TargetFormat.AppImage` maps to jpackage `--type app-image` (upstream Compose docs, not re-verified here; this container's `jpackage --help` shows `--type app-image` and `--app-image <directory path>`). An app-image is a **directory** with a launcher and a bundled runtime, like the `createDistributable` output `ci.yml` already tars; it is not an `.AppImage` file. Proposed edits, none made here: `README.md:67` and `app-desktop/README.md:3` say "app-image directory (`createDistributable`)" instead of `packageAppImage` as a package; `PROMPTS_V1.md:331` (P18) offers "app-image tarball, Flatpak, or a real AppImage wrapped with appimagetool"; `docs/ARCHITECTURE_REVIEW.md:166,558` get a dated erratum line, not a rewrite; `TargetFormat.AppImage` in `app-desktop/build.gradle.kts:38` keeps its name (JetBrains' enum) with a one-line comment | docs only | none | lead applies in P18 | doc facts only |
| LX-2 | Packaging pilot (compile-only on PRs, `main` and tags only to package; sizes and sha256 in the step summary, no upload): `createDistributable` tarball plus `install.sh` (universal fallback, and the only route on Redmagic-Edge); `packageDeb` on `ubuntu-24.04` (fakeroot preinstalled), `packageRpm` with `rpm` apt-installed; glibc baseline check on the launcher and Skiko `.so` (unknown) | `packaging/linux/` (Apache-2.0, add to `LICENCE_BY_DIR`) | `desktop-linux.yml` | lanes green on `CI (hosted VM)`; format choice recorded by P18 | `createDistributable` is runnable here (`CONTAINER-BUILD-ONLY`; not run for this plan). `fakeroot`, `rpmbuild` and `appimagetool` are absent |
| LX-3 | Flatpak (the program recommends it for the Deck): manifest consumes a prebuilt app-image from a draft Release (Flathub builds offline); app id placeholder until R11; **fontc and Fontspector must be bundled** because a sandbox does not see host tools, which couples this step to proposed DEC-D19 | `packaging/linux/flatpak/` | `desktop-linux.yml` | manifest builds in CI; Flathub submission is separate (OQ-4) | No (flatpak-builder absent) |
| LX-4 | Folder access under a sandbox: today's picker order is zenity, kdialog, Swing. Choose `--filesystem=home` or a portal-aware picker (decision in §8); the single-writer lease on `build/.session-lock` is unaffected | `project/storage/src/desktopMain` (existing, Apache-2.0) | `desktop-linux.yml` | picker opens and saves inside the Flatpak (NDV) | No |
| LX-5 | arm64: add `skiko-awt-runtime-linux-arm64` and an `ubuntu-24.04-arm` lane for `:shape-preview:desktopTest :ui:desktopTest`; confirm `currentOs` resolves arm64 natives | `gradle/libs.versions.toml`, `shape-preview/build.gradle.kts` | `desktop-linux.yml` | tests pass on the arm runner | No |
| LX-6 | `docs/release/DEVICE_CHECKLIST_LINUX.md`: Deck Desktop Mode via Discover, Gaming Mode under gamescope, Redmagic-Edge under Termux:X11 (software GL, no Vulkan; whether the arm64 JVM app starts there is unknown), HiDPI on Plasma and GNOME, save, kill, reopen with a Linux analogue of `tools/device/p11-save-kill-restore.sh` | `docs/release/`, `tools/device/` | none | owner fills | No. NDV |

Not ours: wiring `DesktopStorageProvider`, `DesktopFolderPicker` and `FileAppConfigStore` into `app-desktop`
(the P11 UI half), the compile backend (P14, V1-D5) and the "detected, not bundled" first-run check (P18).

### 6.3 iOS and iPadOS (moderate; after OQ-10 and the fontc decision)

| WP | Step | Where | Workflow | Done when | In this container? |
|---|---|---|---|---|---|
| IOS-0 | Gate: OQ-10 ruled; V1-D5 and proposed DEC-D19 ruled; shaper and screen questions answered or the step stops at a shell (§8) | none | none | owner answers | n/a |
| IOS-1 | Cores first (R4): `-Ptypewright.ios=true` (default off, like `typewright.android`) adds `iosArm64` and `iosSimulatorArm64` to `KmpPureConventionPlugin`; `commonTest` runs as `iosSimulatorArm64Test`. No `iosMain` in the four geometry and font cores. Verify the xmlutil 1.0.2, kotlinx-serialization 1.11.0 and coroutines 1.9.0 iOS artifacts first | `build-logic`, `settings.gradle.kts` (gated), pure modules | `ios.yml` on `macos-latest` | pure-module simulator tests pass, `CI (hosted VM)` plus `SIMULATOR` | No (Kotlin/Native iOS needs Apple tooling) |
| IOS-2 | Resource readers: 4 expects get iOS actuals. The bundling mechanism (NSBundle copy phase or generated constants) is a spike | `qa/corpus`, `campaign`, `learn/scenes` `iosMain` | `ios.yml` | readers return the packs in a simulator test | No |
| IOS-3 | Platform modules get the targets in `KmpPlatformConventionPlugin`. Actuals: `platformCompileBackend` and `platformLayerOneChecker` as honest **Unavailable** stubs saying so in the UI (law 4; V1_SCOPE §5's Android row already allows "layer one needs the desktop app"); `platformShaper` via Skiko-iOS if it exposes `SkShaper` (likely, because Compose text on iOS runs on Skia's paragraph stack, **unverified**, one-day spike), else CoreText only by owner ruling (DEC-D14 says HarfBuzz via platform stacks); learn-face fonts from bytes | `compile`, `qa`, `shape-preview`, `ui` `iosMain` | `ios.yml` | simulator tests pass; shaper spike verdict written down | No |
| IOS-4 | Storage: reuse `SwapProtocolStore` with an iOS `DocumentOps` over `NSFileManager`, rather than porting `FileSystemProjectStore`; a new `ProjectLocation` subtype for a security-scoped bookmark (additive in `project`, recents `format_version` to be checked); `AppConfigStore` in Application Support; `UIDocumentPickerViewController`; single-writer lease (`flock` or `NSFileCoordinator`, unknown); iCloud Drive and Backup behaviour (§8) | `project`, `project/storage` `iosMain` | `ios.yml` | crash-injection tests of the swap protocol pass on the simulator | No |
| IOS-5 | `app-ios/` (FSL, add to `LICENCE_BY_DIR`): `ComposeUIViewController` host in a thin SwiftUI shell, XcodeGen `project.yml`, `PrivacyInfo.xcprivacy`, Info.plist keys for Files visibility, `CODE_SIGNING_ALLOWED=NO`. Shows the existing phone frame; **no new screen is designed** (law 6) | `app-ios/` | `ios.yml` | unsigned simulator app builds | No |
| IOS-6 | Rust lane (the real cost): `compile/native/fontc-ffi/` and `qa/native/fontspector-ffi/` (Apache-2.0; `.rs` and `.swift` added to the checker's suffix map) wrap fontc 1.0 and Fontspector behind a small C ABI (fontc's library API is believed to read UFOs from paths rather than memory — not re-verified here; confirm against the fontc 1.0 crate docs in the IOS-6 spike before designing the C ABI); cinterop `.def` in `compile` and `qa`; static libs for iOS device and simulator; `cargo deny` licence gate; crates recorded in `THIRD_PARTY.md`; also yields the Linux, macOS and Windows binaries used by the bundled option | `compile/native/`, `qa/native/` | `native-fonttools.yml` | a simulator test compiles the Hyle Deco seed and `core-font` reads the TTF back; **memory and time on an iPad unknown** | `cargo` exists here; iOS targets and cinterop do not |
| IOS-7 | Input: Apple Pencil through `PointerType` (shared with Android's P17 gap, item 25; no iOS design), hardware-keyboard `⌘` chips (shared with MAC-5), Core Haptics stays unwired as on Android | `ui` (existing) | `ios.yml` | simulator UI tests; Pencil behaviour NDV | No |
| IOS-8 | Signing, TestFlight and App Store lanes as **disabled templates** until OQ-2 and OQ-3; every artefact "UNSIGNED, not for release" (R6) | `packaging/ios/` | disabled | none | No |
| IOS-9 | `docs/release/DEVICE_CHECKLIST_IOS.md` for the iPad Pro M4: all golden-path steps, Files round trip, kill during autosave, background grace; iPhone items NDV | `docs/release/` | none | owner fills | No. NDV |

Golden path on iOS: the headless JVM harness cannot drive iOS, so steps become simulator `commonTest` where the
real modules run, plus the checklist for the rest. Steps 4 to 6 degrade honestly until IOS-6 lands.

### 6.4 macOS (native-fit; after OQ-10)

| WP | Step | Where | Workflow | Done when | In this container? |
|---|---|---|---|---|---|
| MAC-1 | Lane on `macos-latest` (arm64): pure `jvmTest`, then `:shape-preview:desktopTest :ui:desktopTest :app-desktop:test` and `:golden-path:goldenPath` (not the ratchet until proven). Add `skiko-awt-runtime-macos-arm64` (Intel best-effort until Sept 2027); install the Android SDK packages as `ci.yml` does, or confirm AGP 9.4.1 configures without one (README says it does) | `libs.versions.toml`, `shape-preview/build.gradle.kts` | `desktop-macos.yml` | `CI (hosted VM)` green | No (Linux x86_64 only) |
| MAC-2 | `targetFormats(... Dmg, Pkg)`; fix `packageVersion = "0.0.1"` (macOS formats likely need a major version above 0, unverified; P18's single version file is the natural home); `UNSIGNED, not for release` DMG | `app-desktop/build.gradle.kts` | `desktop-macos.yml` | unsigned DMG builds on `main` or tag | No |
| MAC-3 | Config dir: an `appConfigDir()` per OS (`~/Library/Application Support/typewright`) beside `xdgConfigDir()`, testable like `resolveXdgConfigDir`; amend `docs/PROJECT_MODEL.md` §5 only after the owner rules (§8) | `project/storage/src/desktopMain` | `desktop-macos.yml` | unit tests of the resolver | JVM unit tests can run here |
| MAC-4 | Picker: the Swing fallback is expected to run but looks non-native; an AWT `FileDialog` branch for directories is a small follow-up (unverified) | `project/storage/src/desktopMain` | same | NOV | No |
| MAC-5 | `⌘` trigger chips: `docs/INTERACTION_V1.md` §6 already specifies "⌘ on macOS later"; the palette already accepts Meta | `ui` (existing) | same | `ui:desktopTest` screenshot | the screenshot harness already runs in CI on Linux |
| MAC-6 | Developer ID signing, hardened runtime, `notarytool`: **disabled template**; entitlement set is `allow-jit` only if asom's S-M3 verdict allows, else recorded as unknown | `packaging/macos/` | `desktop-macos-release.yml`, manual dispatch, disabled | none until OQ-3 | No |
| MAC-7 | `docs/release/DEVICE_CHECKLIST_MACOS.md`, including the one check only a Mac allows: open a Typewright-written UFO in **Glyphs and RoboFont** (law 7; ARCHITECTURE_REVIEW §5 item 44; both are paid, owner's licences unknown) | `docs/release/` | none | NOV | No |

### 6.5 Windows (native-fit; after OQ-10)

| WP | Step | Where | Workflow | Done when | In this container? |
|---|---|---|---|---|---|
| WIN-1 | Path lint before the first Windows lane (R3). **Measured today**: 726 tracked files, none with `: < > \| ? * "`, no trailing dot or space, no reserved device names, no case collisions, longest path 112 characters. There is **no `.gitattributes`**; the lane sets `core.autocrlf=false` explicitly. Adding a `.gitattributes` (`-text` on byte-exact fixtures) is a lead decision (§8) | `.github/workflows` | `desktop-windows.yml` | lint passes | Lint commands run here; the lane does not |
| WIN-2 | Lane on `windows-2025`: first the SDK-free `-Ptypewright.android=false jvmTest` (cores first, R4), then platform desktop tests and `goldenPath`; `skiko-awt-runtime-windows-x64` pin; `gradlew.bat` is already committed | `libs.versions.toml`, `shape-preview/build.gradle.kts` | `desktop-windows.yml` | `CI (hosted VM)` green | No |
| WIN-3 | NTFS spike: run the existing two-process lock race test and `AtomicFiles` replace-over-open-file cases on Windows; if `ATOMIC_MOVE` with `REPLACE_EXISTING` fails under scanners or sync clients, fall back to `SwapProtocolStore` over a `java.nio` `DocumentOps` (its protocol is crash-safe by construction, `docs/PROJECT_MODEL.md` §5) | `project` tests (existing) | `desktop-windows.yml` | verdict written in this plan's §4 row | No |
| WIN-4 | `%APPDATA%\typewright` in the same `appConfigDir()` as MAC-3 | `project/storage/src/desktopMain` | same | resolver tests | JVM unit tests can run here |
| WIN-5 | `TargetFormat.Msi` via WiX on `windows-2025`; no upgrade GUID until a NAMES.md row exists (R11), which blocks shipping upgrades, not the lane | `app-desktop/build.gradle.kts`, `packaging/windows/` | `desktop-windows.yml` | unsigned MSI builds | No |
| WIN-6 | Signing (SignPath Foundation, OV on HSM, or the Store's MSIX re-signing; Azure Artifact Signing is unavailable to the owner, master §4.5) and winget manifest as **disabled templates** | `packaging/windows/` | disabled | none until OQ-3 | No |
| WIN-7 | `docs/release/DEVICE_CHECKLIST_WINDOWS.md`; first-run tool story: `python3` is rarely the Windows name, so "detected" is weak and pushes toward bundling (DEC-D19) | `docs/release/` | none | owner fills (the Dell while Windows) | No. NDV |

## 7. Shared foundation this repo consumes or provides

| Item | Typewright's relation | Why |
|---|---|---|
| F1 hyle-kmp, F3 cell-shell | **none** | No Hyle dependency; own design language. Law 8 and the shape-plus-colour rule are enforced by this repo's own tests and review, not by F1's commonTest. |
| F2 crash-recovery KMP | none | P18's crash handling is local and user-shared and not built on `dev.aarso:crash-recovery`. A desktop adoption is possible later (OQ-27). |
| F4 asom-client, F8 native engines | none | No model inference; nothing from asystemofmodels. |
| F5 kmp-conventions | **provides evidence, consumes nothing yet** | `build-logic` already is a convention set. Its pin (Kotlin 2.4.20, CMP 1.12.1, AGP 9.4.1) is OQ-17's Option A, proven in one repo with no composite consumers (`CI (hosted VM)`). This plan assumes the pins do not move for the program; Option B would be a downgrade here and would need §6 re-checked. No `includeBuild` of another repo is added (PT:D-Q lockstep). |
| F6 platform-ports | none now; **later** | GitHub push (out of V1, law 3 allows it when the user asks) would need token storage and OQ-22. |
| F7 ubuntu-touch-shell | consumes only the **webapp-container template**; contributes a Kotlin/Wasm case to the single engine spike | Typewright, Clavis and hnm share the WasmGC question. The QML shell and jlink recipe are not used (law 6). Until OQ-24 rules, `packaging/ubuntu-touch/` is local. |
| F9 CI matrix, F10 packaging templates | **provides the Linux pilot, consumes later** | LX-2 is the pre-wave proof F10's Linux template is seeded from; macOS, Windows and iOS lanes are written locally and replaced by SHA-pinned `uses:` after OQ-24. No copy-vendoring from F10. |
| F11 evidence and checklists | consumes templates | Precedent here: `tools/device/p11-save-kill-restore.sh` and P19's RC_AUDIT checklist. |
| Rust font tooling (fontc, Fontspector) | **no master F-item; Typewright is the only consumer** | Master §6 has no row for it (F8 is llama.cpp and stable-diffusion.cpp). It stays local in `compile/native/` and `qa/native/`, Apache-2.0, until the owner wants it elsewhere. |

Other repos need nothing from Typewright today. Its Apache-2.0 engine modules are reusable by licence
(`docs/LICENSING.md` §1) and unused elsewhere.

## 8. Open questions for the owner

In the format of `docs/OPEN_QUESTIONS.md`, for a proposed `## Porting plan` heading; the lead moves them (numbers
here are local, the next free ones after item 130; this plan does not edit that file). Proposed decisions are
DEC-D18 to DEC-D20, the next free ids in `docs/DECISIONS.md`.

1. **Porting gate (OQ-10).** May porting start before V1 ships as a parallel track limited to "prepare behind
   flags, no UI design" (proposed DEC-D18, text in §5.1), or only after P19's RC audit? And may the Linux
   packaging pilot (LX-2 to LX-5) run ahead of P18 as compile-only lanes? **Blocks** every non-Linux step and
   all Ubuntu Touch steps (V1 names no Ubuntu Touch target).

    *Madhav.*

2. **Compile and QA tooling (V1-D5, OQ-10).** Adopt fontc as the `CompileBackend` and Fontspector as layer one on
   every target (proposed DEC-D19), keeping Chaquopy only as V1-D5's Android default until its spike decides and
   fontmake as a detected desktop option? `docs/ARCHITECTURE_REVIEW.md` §4.4 and §6 decision 1 recommend it; fontc
   1.0 has no overlap-removal option, so §6 decision 3 (cleanup in `core-geometry`) comes with it. **Blocks**
   iOS entirely, bundling on Linux, macOS and Windows, and a Flatpak that can run anything.

    *Madhav.*

3. **Ubuntu Touch shape (OQ-1, OQ-21, OQ-15).** Web-container preview (recommended, degraded by design), a
   JVM-in-click spike (not planned), or skip? Which device and 24.04-2.x image does the owner hold, and does its
   webapp-container engine support WasmGC (Chrome 119 or later)? The master program lists Morph as Chromium 134
   but calls the container's engine unverified, and a Qt 5.15 QtWebEngine would not qualify (general knowledge).
   How does the click serve bundled content: `file://` (unknown), a loopback 127.0.0.1 static server (needs the
   `networking` group), or a hosted URL fetched at every launch (law 3 allows that on the web only when opt-in
   and named)? With no device, is `CI-APPROX` the accepted ceiling? **Blocks** UT-1 to UT-4.

    *Madhav.*

4. **Stores under FSL-1.1-ALv2 (OQ-4).** Flathub, Mac App Store, Microsoft Store, App Store, Snap, OpenStore (its
   reserved policy groups require open source and FSL is source-available, not OSI: unknown), or GitHub Releases
   and the website only (`docs/LICENSING.md` §1 already accepts losing F-Droid)? Each may need the one-commit
   Apache relicence that document keeps open, and name clearance (V1-D2) comes first. **Blocks** any listing and
   the Flatpak app id; not the CI lanes.

    *Madhav, with the lawyer LICENSING.md names.*

5. **Accounts, signing, cost (OQ-2, OQ-3, OQ-20).** Apple Developer Program for iOS and notarisation; a Windows
   signing route (Azure Artifact Signing is closed to the owner, master §4.5); TestFlight's automatic crash
   reports against P18's "collects nothing" privacy copy. Hosted minutes are free for this public repo; artifact
   storage is full. **Blocks** IOS-8, MAC-6, WIN-6 and any release artefact.

    *Madhav.*

6. **Validation hardware (OQ-1, OQ-2, OQ-5).** Which of a Mac, a Windows PC (the Dell while Windows), the iPad Pro
   M4, an iPhone and a Ubuntu Touch phone can the owner use? Targets without a device stay stubbed and
   unclaimed (law 4). **Blocks** each wave's device-entry.

    *Madhav.*

7. **iOS screens nobody designed.** `docs/SCREENS_V1.md` §2.6 defines the desktop frame at 900 dp and above; iPad
   portrait widths (about 744 to 1032 pt, general knowledge) straddle it, so which frame an iPad gets is a design
   call. S09's camera is Android-only and import on desktop and web is "File only". Law 6 says stop. Also:
   iPad first (master §4.3) or iPhone first, where projects live (Documents with Files visibility or a picked
   folder), and whether iCloud Drive and iCloud Backup are acceptable for projects. **Blocks** IOS-5 beyond a
   shell on the existing phone frame, IOS-4's location choice and IOS-7.

    *Madhav (design).*

8. **iOS shaping under DEC-D14.** Accept Skiko-iOS's shaper if the spike shows it exposes `SkShaper`; if not,
   accept CoreText (not HarfBuzz, so it departs from brief §3's "shaping is HarfBuzz" and changes cluster
   semantics), or require a HarfBuzz binding (proposed DEC-D20)? **Blocks** IOS-3's shaper and the Devanagari and
   Naskh previews on iOS.

    *Madhav.*

9. **Linux format, the "AppImage" name and sandbox folder access (P18, OQ-4, OQ-10).** Choose among an app-image
   tarball with `install.sh`, Flatpak, a true AppImage and deb or rpm (recommended: tarball and Flatpak, no true
   AppImage); approve the naming correction in LX-1; say whether arm64 Linux (Raspberry Pi, Redmagic-Edge) is
   supported; and for a Flatpak choose `--filesystem=home` (simple, weak sandbox) or a portal-aware picker
   (per-folder grants, new code; AWT and Swing are not portal-aware, unknown). **Blocks** LX-2 to LX-5.

    *Madhav.*

10. **Config directory per OS (`docs/PROJECT_MODEL.md` §5).** Amend the row to `~/Library/Application Support/
    typewright` and `%APPDATA%\typewright`, or keep `~/.config/typewright` everywhere? **Blocks** MAC-3, WIN-4.

    *Madhav.*

11. **Application icon and identifiers (OQ-25).** No icon asset exists in the repo (no `.png .svg .ico .icns`
    tracked), and every packaged target needs one; the Flatpak id, bundle id, MSI upgrade GUID and click name each
    need a NAMES.md row first (R11). Law 6 forbids improvising the mark. **Blocks** any release package, not the
    lanes.

    *Madhav.*

12. **`.gitattributes` and the Windows lane (R3).** Add a root `.gitattributes` (`-text` on byte-exact fixtures)
    before WIN-2, or set `core.autocrlf=false` in the lane only? **Blocks** WIN-2 only.

    *lead.*

13. **Already open elsewhere, relied on and not re-raised.** Toolchain pins (OQ-17: this plan assumes Kotlin 2.4.20,
    CMP 1.12.1 and AGP 9.4.1 stay fixed here); the missing `ui/v1-screens/` mockups (item 123: every target's UI work
    waits on them); the hosted build endpoint's home (`docs/DECISIONS.md` "Still open"), which
    would be the only compile path for a Ubuntu Touch preview; V1-D2 name clearance; V1-D4 web compile.

    *Madhav.*

## 9. Sources read

Repo: `CLAUDE.md`, `README.md`, `TYPEWRIGHT_BUILD_BRIEF.md` (§1 to §5, §13, §15), `PROMPTS_V1.md` (§0 to §3, P10,
P11, P18, P19), `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`,
`build-logic/convention/` (the three convention plugins), the `build.gradle.kts` of `app-desktop`, `app-android`,
`app-web`, `ui`, `compile`, `shape-preview`, `project`, `project/storage` and `golden-path`,
`golden-path/{passing-steps.txt,README.md}`, `.github/workflows/ci.yml`, `tools/check_licences.py`,
`tools/device/p11-save-kill-restore.sh`, `docs/{LICENSING,V1_SCOPE,V1_TRIAGE,DECISIONS,ARCHITECTURE_REVIEW,
PROJECT_MODEL,SCREENS_V1,INTERACTION_V1,OPEN_QUESTIONS}.md`, `THIRD_PARTY.md`, `CONTRIBUTING.md`, the READMEs of
`compile`, `shape-preview`, `project`, `project/storage`, `ui` and `app-desktop`, the three app shells' `Main.kt`
and `TypewrightApplication.kt`, `ui/.../project/ProjectUi.kt`, `ui/.../glass/CommandPalette.kt`,
`compile/.../{SystemPythonFontmakeBackend,ChaquopyBackend}.kt`, `qa/.../LayerOneChecker*.kt`, `shape-preview/.../`
shaper sources, `project/.../{ProjectStore,SwapProtocolStore}.kt`, `project/storage/.../Desktop*.kt` and
`XdgConfigDir.kt`, the `SceneResources*.kt` readers. Measurements: `git ls-files`, `wc -l`, `grep` for `expect`,
`actual`, `@Test`, `java.*` and `android.*` imports, `ProcessBuilder`, Windows-invalid path characters and icon
assets; `jpackage --help`.

Program: `Personal-Tracker/PORTING_PROGRAM.md` §0 to §3, §4.1 to §4.6, the Typewright row of §5, §6, §7, §8, §9.
