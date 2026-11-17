# iOS and iPadOS port: what exists, how it is proved, what is the owner's

Status as of 2026-11-17. Written in a Linux container with no Mac, no Xcode, no simulator and no iPad.
Evidence labels follow `docs/PORTING_PLAN.md` section 0: **BUILT-HERE** (run here, passed), **CI-LANE-WRITTEN**
(a workflow exists, no run seen), **CODE-ONLY** (written, never executed), **NEEDS-DEVICE-VALIDATION** (only the
owner's iPad can answer). Nothing in this file says the app runs on an iPad.

The owner's request of 2026-11-17 overrides the plan's "wait for V1" gate (OQ-10) for the question *which
platforms to port*: iOS is in. Everything is additive and off by default (`-Ptypewright.ios=true`), so the V1 path
and the existing gate are unchanged. No new screen is designed (CLAUDE.md law 6): the iPad shows the existing phone
frame.

## What exists

| Piece | Where | Evidence |
|---|---|---|
| Flag-gated `iosArm64` and `iosSimulatorArm64` targets in both convention plugins | `build-logic/convention/.../KmpPureConventionPlugin.kt`, `KmpPlatformConventionPlugin.kt`, `ProjectExtensions.kt` | BUILT-HERE: configures and compiles on Linux with the flag; default build unchanged (jvmTest and spotlessCheck green, below) |
| iOS actuals for the four resource readers (corpus, campaign, scenes, learn faces) | `qa/corpus`, `campaign`, `learn/scenes` `src/iosMain` | BUILT-HERE as klibs (compile only); CODE-ONLY at run time |
| Honest stubs: compile backend, layer-one checker, shaper (each says so in the UI text) | `compile`, `qa`, `shape-preview` `src/iosMain` | BUILT-HERE as klibs; stubs by design |
| Learn-face font actuals | `ui/src/iosMain` | BUILT-HERE as a klib (compiled with all of `ui` commonMain); CODE-ONLY at run time |
| Sandbox storage: `IosDocumentOps` (NSFileManager plus stdio `fsync`), `IosStorageProvider`, `IosAppConfigStore`, all over the shared `SwapProtocolStore` | `project/storage/src/iosMain` | BUILT-HERE as klibs; CODE-ONLY at run time |
| Pure path logic for a container that moves between launches (`SandboxPaths`) with 6 tests | `project/.../SandboxPaths.kt`, `SandboxPathsTest.kt` | BUILT-HERE (JVM) |
| `:app-ios` Gradle module: static framework `TypewrightKit`, `mainViewController()` and `flushWorkspace()` | `app-ios/` | BUILT-HERE as a klib (compile only, in a scratch module); framework link needs macOS: CI-LANE-WRITTEN |
| SwiftUI shell, XcodeGen spec, privacy manifest, identity and signing xcconfigs | `app-ios/iosApp/` | CODE-ONLY (no Swift compiler here; YAML and plist parse checked) |
| Data-pack staging script and its test | `app-ios/scripts/` | BUILT-HERE |
| `ios.yml`: simulator tests of the pure modules, then an unsigned simulator and device build | `.github/workflows/ios.yml` | CI-LANE-WRITTEN (actionlint clean) |
| `ios-release.yml`: signing and TestFlight, every job switched off | `.github/workflows/ios-release.yml` | template, deliberately disabled |
| iPad checklist | `docs/release/DEVICE_CHECKLIST_IOS.md` | owner fills |

## Decisions and why

- **Compose Multiplatform on iOS, SwiftUI only as the host.** The plan's CMP-iOS route; the app's UI is one Compose
  codebase and law 6 forbids a second one. The Swift is about 40 lines.
- **iPad only (`TARGETED_DEVICE_FAMILY = 2`).** The plan lists iPad first and iPhone as no-port. Changing it is one line
  in `app-ios/iosApp/project.yml`.
- **Storage is the app's own `Documents/Typewright Projects`**, visible in Files through `UIFileSharingEnabled`. It
  reuses `SwapProtocolStore`, whose crash-injection tests already run on the JVM, instead of porting
  `FileSystemProjectStore`. Not built: a folder picker for locations outside the sandbox and the security-scoped
  bookmark `ProjectLocation` it needs (plan section 8 item 7, owner decision). A saved path is re-rooted onto the
  current `Documents` folder (`SandboxPaths`) because the container UUID can change.
- **Compile and layer-one checks are unavailable on iOS** and the actuals say so. fontmake and Fontspector are
  processes; an iOS app cannot start one. The plan's real route (fontc and Fontspector as Rust static libraries behind a
  C ABI, IOS-6) is a separate, later step gated on decision V1-D5 and is not started here.
- **Shaping is a stub** until a Mac run answers whether Skiko-iOS exposes `SkShaper` (a one-day spike, plan IOS-3).
  Devanagari and Naskh previews wait on it.
- **Bundle id `com.asoc.typewright`** lives in exactly one file, `app-ios/iosApp/Config/Identity.xcconfig`, marked
  OWNER-FOLLOW-UP: plan rule R11 wants a `NAMES.md` row first and `NAMES.md` is outside this repository.
- **Data packs travel as a folder in the app bundle** (`typewright-resources/`), filled by `app-ios/scripts/stage-resources.sh`
  with the same relative paths the JVM and Wasm builds use. Simulator tests have no bundle, so `TYPEWRIGHT_RESOURCES`
  points the readers at the same folder.
- **Privacy (law 3).** The app has no network code of its own on iOS. TestFlight and the App Store collect crash reports
  and tester usage on Apple's side; that is the one approved exception and is not Typewright code. It must be named in
  the README and the app's about text, and P18's "collects nothing" copy reconciled. Neither is edited here (the about
  text is UI; the README note below is).

## How it is proved here, and how to repeat it

```
export LANG=C.UTF-8 LC_ALL=C.UTF-8
# Default gate, flag off: must be unchanged.
./gradlew spotlessCheck jvmTest -Ptypewright.android=false
# iOS klibs for every pure module, from Linux (compile only; no link, no run).
./gradlew compileKotlinIosSimulatorArm64 compileKotlinIosArm64 \
  -Ptypewright.android=false -Ptypewright.ios=true -Pkotlin.native.enableKlibsCrossCompilation=true
bash app-ios/scripts/test-stage-resources.sh
python3 tools/check_licences.py
```

`kotlin.native.enableKlibsCrossCompilation=true` lets Kotlin/Native produce klibs for Apple targets on Linux. It
checks types and Apple framework signatures against the real `platform.Foundation` bindings, which is why a typo in
`NSFileManager` calls fails here. It does not link or run anything. The Android SDK could not be installed in this
container (disk), so the platform modules (`project/storage`, `compile`, `shape-preview`, `ui`, `app-ios`) were
compiled for iOS through a throwaway scratch module that includes the same source directories; that module is not
committed.

## Owner steps

1. Run the `iOS` workflow (push, or `workflow_dispatch`). Expect to fix first-run problems; none of it has run.
   Hosted macOS minutes are billed on a private repository.
2. Add the `NAMES.md` row for `com.asoc.typewright` (plan R11), or change `Identity.xcconfig`.
3. Join the Apple Developer Program (plan OQ-2), then create the GitHub environment `apple-release` with reviewers and
   the secrets named in `.github/workflows/ios-release.yml`; only then enable that template.
4. Decide TestFlight use: plan OQ-2's note says it waits on the approved I-1 exception; this task's brief names Apple's
   own crash reports as that exception. Name it in the README and the app's about text.
5. Run `docs/release/DEVICE_CHECKLIST_IOS.md` on the iPad and write down what you saw.
6. Rulings still open: the Skiko-iOS `SkShaper` spike, the fontc native lane (V1-D5), the folder-picker and bookmark
   design, iCloud Backup behaviour for `Documents`, and iPad screen design (law 6).
