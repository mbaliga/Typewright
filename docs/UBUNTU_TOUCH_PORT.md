# Ubuntu Touch port: what exists, how it is proved, what is the owner's

Status as of 2026-11-17. Written in a Linux x86_64 container with no Ubuntu Touch device, no Docker daemon, no
Android SDK (disk full) and no arm64 host. Evidence labels follow `docs/PORTING_PLAN.md` section 0: **BUILT-HERE**
(run here, passed), **CI-LANE-WRITTEN** (a workflow exists, no run seen), **CODE-ONLY** (written, never executed),
**NEEDS-DEVICE-VALIDATION** (only the owner's phone can answer). Nothing in this file says Typewright runs on
Ubuntu Touch.

## The two clicks, and which one is the port

The owner ruled (2026-10-06) "native only, no substitutes", and said Typewright is needed on Linux and Ubuntu Touch
because "installed apps will always have more to offer". The plan's own Ubuntu Touch cell (a Kotlin/Wasm click) is
the substitute that ruling excludes, so the order here differs from the plan's UT-1..UT-5 table:

| Click | Name | What it is | Is it the port? |
|---|---|---|---|
| Native | `typewright.asoc` | The Compose Desktop app-image (bundled arm64 JRE, Skiko arm64 native) launched under Lomiri's X11 layer, `unconfined` template. This is plan step UT-5 ("JVM-in-click"), pulled forward because the ruling makes it the only honest port. | **Yes, as a candidate.** Unproven: whether Compose Desktop starts under Lomiri is plan spike S-UT2, not run. |
| Preview | `typewright-preview.asoc` | The `:app-web` Kotlin/Wasm distribution in `webapp-container`. Titled and described "Typewright Preview"; no compile, no layer-one check. | **No.** A labelled companion (plan R12), never described as the port. `tools/check_ubuntu_touch.py` fails the build if its title lacks "Preview" or if the native click calls itself a preview. |

Why the JVM route is still risky: it has never started on a Lomiri phone; AWT needs X11 libraries that the click
rootfs may lack; touch arrives as emulated mouse events at best (no two-finger swipe, no puck pressure); OpenGL may
be unavailable under Xwayland; and the `unconfined` template needs an OpenStore manual review that FSL-1.1-ALv2 may
not pass (OQ-4, OQ-35: the owner chose to ask OpenStore's reviewers first, see Owner actions).

## What exists

| Piece | Where | Evidence |
|---|---|---|
| Lomiri launcher (POSIX sh): display check, `GRID_UNIT_PX` to `sun.java2d.uiScale` mapping (clamped 1 to 4), `TYPEWRIGHT_UI_SCALE` override, `TYPEWRIGHT_RENDER=software` safe mode, preflight `ldd` of `libawt_xawt.so` into `~/.cache/typewright/launch.log`, dry-run mode | `packaging/ubuntu-touch/typewright-launch.sh` | Logic BUILT-HERE (`test-launcher.sh`, about a dozen checks against a fake app-image). That the scale mapping is right for Lomiri is a guess: NEEDS-DEVICE-VALIDATION |
| Staging script (app-image + manifest + apparmor + desktop + icon + launcher into one directory) | `packaging/ubuntu-touch/stage.sh` | BUILT-HERE against a fake app-image; real app-image CI-LANE-WRITTEN |
| Click metadata: `manifest.json` (placeholders `@CLICK_FRAMEWORK@`, `@CLICK_ARCH@`), `typewright.apparmor.json` (`unconfined`, no `networking` group), `typewright.desktop`, placeholder icon | `packaging/ubuntu-touch/` | JSON and cross-file references BUILT-HERE (`tools/check_ubuntu_touch.py`, 10 tests, nine of them negative). Whether click-review and a device accept them: CI-LANE-WRITTEN / NEEDS-DEVICE-VALIDATION |
| `clickable.yaml` for both clicks (framework `ubuntu-touch-24.04-1.x`, builder `precompiled` arm64 / `pure`) | `packaging/ubuntu-touch/clickable.yaml`, `preview/clickable.yaml` | BUILT-HERE: loaded and schema-validated by the real `clickable-ut` 8.10.0 loader (`check-clickable-config.py`) |
| Preview click: manifest, `webview`-only apparmor, desktop, launcher, stager and test | `packaging/ubuntu-touch/preview/` | Stager and launcher logic BUILT-HERE (`test-preview.sh`); whether `webapp-container` loads `file://`, runs WasmGC, keeps storage: NEEDS-DEVICE-VALIDATION |
| `skiko-awt-runtime-linux-arm64` catalog pin (same Skiko 0.150.1) and an arch switch in `shape-preview`'s `desktopTest` | `gradle/libs.versions.toml`, `shape-preview/build.gradle.kts` | Catalog parse BUILT-HERE (`:core-geometry:jvmTest :core-font:jvmTest` green); the `shape-preview` edit is CODE-ONLY (that module needs the Android SDK, absent here). Artifact exists on Maven Central (HTTP 200 on its POM) |
| `ubuntu-touch.yml`: `static` (ubuntu-latest), `native-click` (ubuntu-24.04-arm: SDK, `createDistributable`, arm64 ELF and Skiko-arm64 assertion, stage, Clickable build + click-review in the digest-pinned CI image), `preview-click` (wasm dist, stage, Clickable) | `.github/workflows/ubuntu-touch.yml` | CI-LANE-WRITTEN. `actionlint` clean and YAML parse BUILT-HERE. Uploads only on `v*` tags, `continue-on-error`. Actions SHA-pinned; Clickable pinned 8.10.0; CI images pinned by digest read from Docker Hub on 2026-11-17 |
| Device checklist | `docs/release/DEVICE_CHECKLIST_UT.md` | owner fills |

## Decisions and why

- **Native first, Preview second.** Reverses the plan's ordering because of the owner ruling above. Recorded in
  `docs/PORTING_PLAN.md`'s status note.
- **Build on an arm64 runner, not by cross-building.** jpackage makes the app-image for the architecture it runs on;
  a cross-built x86_64 image would be the wrong click. `ubuntu-24.04-arm` also lets `compose.desktop.currentOs`
  resolve the arm64 Skiko native. Private repositories are billed for that runner.
- **`precompiled` builder, no Clickable compile step.** The click only repackages the Gradle output.
- **No `networking` policy group.** Law 3 allows only user-asked network calls (Google Fonts fetch, GitHub push); under
  `unconfined` the sandbox grants network anyway, so the group would only make a claim the template makes moot.
  Recorded so a reviewer does not read the absence as "no network".
- **Click identifiers are placeholders.** `typewright.asoc` and `typewright-preview.asoc` live only in the two
  `manifest.json` files (plan rule R11 wants a `NAMES.md` row first; that file is outside this repo).
- **Framework 24.04-1.x only.** The bundled JRE links the runner's glibc (2.39). The owner's OnePlus 6 is read as
  20.04-only (OQ-37, "wait for the OnePlus 6 on a newer image"), so no click here can be tried on it today.
- **Known gap, not fixed here:** `app-desktop/Main.kt` flushes the project only in `onCloseRequest`. Lomiri freezes an
  unfocused app about 1.5 s after suspend, so a background-then-kill can lose the debounced autosave. The checklist
  measures it (section 3). The fix is a Kotlin change in `:app-desktop` that this container cannot compile (no SDK), so
  it was not written blind.

## Not done

- No `.click` has been built; no Docker daemon and no arm64 host here. `clickable build` and click-review are CI-only.
- `:app-desktop:createDistributable` and `:app-web:wasmJsBrowserDistribution` were not run (both modules need the
  Android SDK, absent; disk was full).
- Lomiri touch handling, HiDPI, OpenGL under Xwayland and suspend behaviour for the JVM app: all unknown.
- A real application icon (the file shipped is a placeholder SVG drawn for this port; OQ-25).
- Window sizing/maximising on a phone screen (Compose's default window is 800x600 dp).

## Owner decision: tolerated click-review finding (2026-11-18, OQ-35)

The repo owner decided on 2026-11-18 ("Option 2, apply the narrow allowance") that CI may tolerate the
click-review findings that are inherent to the app and that only OpenStore's human reviewers can clear, and
nothing else. For the native click that is exactly one finding:

- `security:template_valid:typewright.apparmor.json`, text `(NEEDS REVIEW) 'unconfined' not allowed`.

Why: the JVM app-image needs the `unconfined` template (`tools/check_ubuntu_touch.py` requires it), and
click-review cannot approve that template; a human OpenStore reviewer must. The AppArmor policy is unchanged.

How it is enforced: the CI build runs `clickable build --skip-review`, then `packaging/ubuntu-touch/review-click.py`
runs `clickable review` as its own hard-gate step. The script prints the full click-review output, tolerates only
rule ids on its exact-match `ALLOWLIST` whose text is the expected `(NEEDS REVIEW)` text, and fails on any other
error or warning, unparsable output, or a non-zero exit with no parsable findings. Its `--self-test` fixtures run in the
`static` job.

Rules: keep the allowlist minimal (add an entry only with a new owner decision recorded here), and note that OpenStore
review must still clear this finding before any store submission. The tolerance is a CI convenience, not an approval.
The Preview click is unchanged and does not use the allowance. (Fotoz records its own matching decision in its ADR-013.)

## Owner actions

1. Confirm the click names (add `NAMES.md` rows for `typewright` and the Preview) and the developer suffix on OpenStore.
2. Post the OpenStore reviewer question about FSL-1.1-ALv2 and an `unconfined` JVM app (`Personal-Tracker/porting/outbound/openstore-fsl-question.md`, OQ-35). Until answered the native click cannot be listed.
3. A phone on Ubuntu Touch 24.04 (OQ-37). Install the `.click` from the workflow's step summary/artifact, then fill `docs/release/DEVICE_CHECKLIST_UT.md`. Start with section 1; if the window is blank, set `TYPEWRIGHT_RENDER=software`.
4. Run the workflow (push a branch or `workflow_dispatch`) and report the first red step; this lane has never run.
5. Signing: OpenStore signs/accepts uploads itself; no key is needed or stored in this repo.
