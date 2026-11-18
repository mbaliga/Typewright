# Linux desktop port: what exists, how it is proved, what is the owner's

Status as of 2026-11-17. Written in a Linux x86_64 container with no Linux desktop to look at, no Steam Deck,
no Termux:X11 and no Android SDK (the disk could not hold it), so none of the Gradle platform modules
(`:ui`, `:app-desktop`, `:project:storage`) could be built here. Evidence labels follow `docs/PORTING_PLAN.md`
section 0: **BUILT-HERE**, **CI-LANE-WRITTEN**, **CODE-ONLY**, **NEEDS-DEVICE-VALIDATION**. Nothing here says the app
runs on a Linux desktop.

Linux was already a target (`app-desktop`, Compose Desktop). This pass finishes the plan's LX-2 to LX-6.

## What exists

| Piece | Where | Evidence |
|---|---|---|
| `LinuxSandbox.detect` (Flatpak, Snap, none) and `pickerOrder`: inside a sandbox the folder picker skips `zenity` and `kdialog` (the runtime has neither) and uses Swing | `project/storage/src/desktopMain/.../LinuxSandbox.kt`, `DesktopFolderPicker.kt` | BUILT-HERE: the two files plus 6 tests compiled and run in a scratch JVM module (Kotlin 2.4.20, same sources): 6 tests, 0 failures. The module's own `:project:storage:desktopTest` is CI-LANE-WRITTEN |
| Shutdown flush: a JVM shutdown hook flushes the workspace (3 s bound), so SIGTERM, logout and `kill` no longer lose the last debounced edit; only the window close did before | `app-desktop/.../Main.kt` | CODE-ONLY (not compiled here) |
| `install.sh` now installs the SVG icon and a `.desktop` entry with `Icon` and `StartupWMClass`; `test-install.sh` extended | `packaging/linux/` | BUILT-HERE (`test-install: ok`, `desktop-file-validate` clean on the installed entry). Never run against a real app-image |
| `check-glibc.sh`: highest `GLIBC_x.y` the app-image's ELF files need, fails above `MAX_GLIBC` (2.35) | `packaging/linux/check-glibc.sh`, `test-check-glibc.sh` | BUILT-HERE against this host's binaries (logic only). Never run on the real image |
| Flatpak manifest, `.desktop`, AppStream metainfo, app id `com.asoc.Typewright` (placeholder), `test-metadata.sh` | `packaging/linux/flatpak/` | BUILT-HERE: `desktop-file-validate` clean, `appstreamcli validate --no-net` passes (1 pedantic note), `flatpak-builder --show-manifest` parses. NOT built: the runtime download does not fit here |
| `desktop-linux.yml`: `static`, `x64` (tests under xvfb, `createDistributable`, glibc check, install.sh, launcher smoke under xvfb, `.deb`, `.rpm`), `arm64` (`ubuntu-24.04-arm`, tests plus arm64 app-image checks), `flatpak` (bundle), `release` (version tags: draft release only) | `.github/workflows/` | CI-LANE-WRITTEN: YAML parses, every step's shell logic that can run here was run; no run seen |
| Skiko arm64 pin for the test classpath | `gradle/libs.versions.toml`, `shape-preview/build.gradle.kts` (from the Ubuntu Touch pass) | CODE-ONLY |

## Not done, and why

- **A true `.AppImage` file.** `TargetFormat.AppImage` yields an app-image directory (LX-1); `appimagetool` is not
  available here and the plan recommends the tarball + `install.sh` + Flatpak instead. Not built.
- **Icon in the `.deb` and `.rpm`.** jpackage wants a PNG and there is no SVG rasteriser here (ImageMagick has no
  `rsvg-convert`). The packages carry no icon; `install.sh` and the Flatpak do. Adding `linux { iconFile }` with a PNG
  is a small follow-up once the PNG exists.
- **Compose `linux { ... }` package settings** (maintainer, menu group, RPM licence). Left alone: they could not be
  compiled here and `debMaintainer` needs an email that is the owner's to choose.
- **Portal-aware folder picker.** AWT and Swing are not portal-aware; the Flatpak grants `--filesystem=home`
  instead. Narrowing it is new code and an owner decision.
- **Bundling `fontc`, `fontmake`, Fontspector** in the Flatpak. The V1 compile backend is still a stub.
- **Desktop project storage** is still the in-memory workspace in parts (P11 UI half); that is product work, not
  packaging.
- **Flathub, Snap, OpenStore listings.** Blocked by the FSL-1.1-ALv2 question (plan OQ-4).

## Owner steps

1. Let `desktop-linux.yml` run once (push to `main` or `workflow_dispatch`) and read the step summaries. Expect
   first-run failures: the SDK install, the glibc ceiling and the launcher smoke step are the likeliest.
2. Give the Flatpak a `NAMES.md` row, then rename the three `com.asoc.Typewright.*` files together.
3. Run `docs/release/DEVICE_CHECKLIST_LINUX.md` on a real desktop and the Steam Deck.
4. Choose the sandbox filesystem grant, and whether Flathub / Snap is worth the FSL question.
5. Add a PNG icon if the `.deb` and `.rpm` should carry one.
