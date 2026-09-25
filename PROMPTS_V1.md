# Typewright: from P9 to a V1 release (prompts P10–P19)

Put this file in the repo root next to `PROMPTS_CLAUDE_CODE.md`. Run the prompts in order.
Each one stands alone and starts by reading files. Model routing follows brief §14: **Opus**
for architecture, spikes and the release audit, **Sonnet** for everything that reproduces
the explorer.

Prepared 25 Sep 2026 from the build docket at `af371a8` and a read of the branch
`claude/build-out-feature-6i1vsl`. Revised the same day with Madhav's answers: the app id,
the licence and money plan, and the V1 screen designs.

**This pack contains:** `PROMPTS_V1.md` (this file), `docs/SCREENS_V1.md` (features,
journeys and per-screen requirements), `ui/v1-screens/` (the mockup sources, one per
screen), `docs/LICENSING.md`, `LICENSE`, `LICENSES/Apache-2.0.txt`, `CONTRIBUTING.md` and
`TRADEMARKS.md`. Copy them into the repo root, keeping these paths.

---

## 0. Where the build actually is

P0–P9 built a strong engine: geometry, font I/O, the trace chain, fitting, construction,
qa and the corpus, campaign, scripts and shaping. It also built three working UI surfaces
(the sheet with Draw and Space, Learn, Workbook) and a Wasm build that renders. **But no one
can use it yet to make a font.** Checked in the code:

| The brief's M0/M1 path | State at af371a8 |
|---|---|
| Open a TTF/UFO the user brings (M0) | **No import path.** Draw, Overlay, Lens and the Workbook gates all read the bundled `fonts/HyleDeco-Regular.ttf`. When the UI says "your font", it means Hyle Deco. |
| Save a project as plain files (law 7) | `UfoProject` writes to disk, but only in `jvmTest`. The UI never saves or opens a project, and nothing survives the process ending. |
| Capture (photo/scan → cells) | Not built. There's no camera or gallery import, no fiducial or perspective step, no per-cell detection. `engine-trace` begins at a clean raster. P3 asked for this. |
| Trace screen with scrubber | Not built. The chain works but nothing in the UI drives it. P3 asked for this. |
| Check report + Economy view | Not built as screens. The data and checks are real. P1 item 4 asked for these. |
| Compile | **Stubbed on all three targets.** `ChaquopyBackend` and `SystemPythonFontmakeBackend` both return `Unavailable`. `HostedEndpointBackend` has a null endpoint. The app can't produce a font file. |
| Ship (repo folder) | The generators exist in `qa`. There's no Ship screen. P1 item 5 asked for one. |
| Navigation | No router. Two corner buttons stand in for it (OQ 56/82/89). |

So "10/10 prompts" means every prompt was attempted. It doesn't mean every milestone is met.
M0 and M1 are the V1 gap, and P10–P19 close it.

---

## 1. What V1 is

**V1 = M0 + M1 for Latin, end to end, on Linux desktop and Android. The web build ships as
a labelled preview.**

The golden path is the acceptance test for the release. It must run on each V1 target.

1. **New project** from a photo or scan of a filled Typewright template sheet, **or open**
   an existing TTF/OTF/UFO.
2. **Capture → Trace**: detect the cells, trace and fit them, review on the scrubber, and
   accept. An accepted glyph is locked (law 1).
3. **Draw / Space**: hand-edit with undo, set the kerning, and see node economy live.
4. **Check**: layer two always. Layer one wherever a runtime exists. The Economy view opens
   from Check.
5. **Compile** to TTF on the device or computer.
6. **Ship**: write the Google Fonts repository folder. Ship stays blocked while Check has
   any fails.
7. Close the app and reopen it. The project is exactly as it was.

Dogfood: the **Hyle Deco redraw** (brief §16) is the first real font through this path. The
Workbook's first playthrough runs on it too.

**In V1, labelled:** Learn (real data). Workbook, marked "Early — lessons in progress" until
the Domestika material lands. Kana, Devanagari and Naskh in Draw/Space with shaping preview,
marked "Preview".
**Not in V1:** M6 Derive, nib tools, interpolation, GitHub push, iOS, Windows and macOS.

---

## 2. Decisions Madhav owes before or during V1

| # | Decision | Needed by | Recommendation |
|---|---|---|---|
| D1 | **Licence** | — | **Decided:** FSL-1.1-ALv2 for the app, Apache-2.0 for the engine, CC0 for templates and data, and users' fonts are theirs. See `docs/LICENSING.md`. Applied in P10. The lawyer reads it before v1.0.0. |
| D2 | **Name clearance** for "Typewright" (the same TM-A route as the other marks) | before P18 | Start the search now. The Android application id carries the name. |
| D3 | **Application id / package** | — | **Decided:** `com.asoc.typewright`, applied in P10. |
| D4 | **Web V1 compile.** The hosted endpoint conflicts with law 3's "no backend of ours" (OQ 10). | before P14 | V1 web = preview with no compile. It exports UFO plus the Ship folder, and the user builds on desktop. Revisit the hosted endpoint as the paid product later. |
| D5 | **Android compile route** | after the P14 spike | Chaquopy is MIT since 12.0.1 (Jul 2022), which closes brief §15 item 7. The real risk is native wheels (skia-pathops, cffsubr, booleanOperations) on Android. If the spike fails, fall back to fontc (Rust, Apache-2.0) over JNI. fontc is still maturing. |
| D6 | **Workbook in V1:** "Early", or hidden until Domestika | before P18 | Ship it as Early. The gates are real and they set the app apart. |
| D7 | **Screen designs** | — | **Delivered:** `docs/SCREENS_V1.md` plus `ui/v1-screens/`, the new look source for law 6 on every V1 screen. Madhav still confirms the four small points in SCREENS_V1 §8. |
| D8 | The brief's open CONFIRMs: 3 vs 8 trace stages, default texture, the bundled UI face (OQ 9), margin/proof axis | before P13/P16 | 3 stages by default (the mockups use this). Paper. IBM Plex Sans and IBM Plex Mono, bundled; one OFL family covers text and labels and fixes the web's missing mono. Leave margin/proof for v1.1. |
| D9 | **minSdk 31** (OQ 4) | before P18 | Keep it. |

---

## 3. Standing rules for P10–P19 (add to CLAUDE.md in P10)

- **Only the lead agent commits.** Sub-agents work in worktrees and hand back diffs. (P6/P7
  sub-agents committed against instructions.)
- **One PR per prompt, into `main`.** No more 584-file draft branches.
- **Every prompt ends by running the golden-path test** (P10 creates it) and reporting which
  steps pass. Once a step passes, it may not regress.
- **KDoc says what and why, not build history.** Put prompt numbers, OQ cross-references and
  "Task P7:" narratives in commit messages and `docs/OPEN_QUESTIONS.md`. About 31% of the
  lines in main source sets are comments, and much of that is build archaeology that will
  rot.
- **"Your font" means the open project.** A fixture is allowed only in tests.
- **Every screen follows the control deck** (SCREENS_V1 §2). No screen adds its own back
  button, tab row or title bar.

---

## P10. Land the engine, triage, and set up the V1 harness [Opus]

```
Read CLAUDE.md, TYPEWRIGHT_BUILD_BRIEF.md §1–4, §12–16, PROMPTS_V1.md in full, and
docs/OPEN_QUESTIONS.md in full.

0. FIRST, before anything else is pushed (the repo is public): apply docs/LICENSING.md.
   Replace the root LICENSE with the pack's, add LICENSES/ (and fetch the official CC0-1.0
   text), CONTRIBUTING.md and TRADEMARKS.md, and a LICENSE pointer in each engine module.
   Add SPDX headers per LICENSING.md §4. Add a CI check that fails if an Apache-2.0 module
   depends on an FSL module. Amend CLAUDE.md law 3 and the licence convention exactly as
   LICENSING.md §4 says. Close OQ 1.
1. Review PR #1 as a reviewer, not its author. Fix anything that blocks merging, then merge
   it to main. From now on, work in one branch and PR per prompt.
2. Rename the application id to com.asoc.typewright, the Kotlin packages to
   com.asoc.typewright.*, and the Maven group to com.asoc. One commit, every module. Close
   OQ 2.
3. Add PROMPTS_V1.md §3's standing rules to CLAUDE.md. Record SCREENS_V1 §1's canon changes
   C1–C7 in TYPEWRIGHT_BUILD_BRIEF.md, UI_SPEC.md and CLAUDE.md. Law 8 gains the one-red
   exception (C3), and law 6 names docs/SCREENS_V1.md and ui/v1-screens/ as the look source
   for V1 screens.
4. Triage every item in docs/OPEN_QUESTIONS.md into exactly one of: V1-BLOCKING (name the
   prompt P11–P19 that closes it), MADHAV (map it to D1–D9 or add a D10+), DEFERRED
   (v1.1+), RESOLVED (close it with the commit). Write the result to docs/V1_TRIAGE.md.
   Close brief §15 item 7: Chaquopy is MIT since 12.0.1, and the brief's "since v14" is
   wrong.
5. Write docs/V1_SCOPE.md from PROMPTS_V1.md §1: the golden path, what is labelled Early or
   Preview, and what is out.
6. Create a headless desktop-JVM integration test, GoldenPathTest, with one test per
   golden-path step. Each test drives the real modules, not the UI. Steps that aren't built
   yet fail with a named reason, not @Ignore. Wire it into CI as a non-blocking job that
   prints the pass count.
7. CI: on every push to main, upload a debug APK, a Linux desktop distributable and the Wasm
   bundle as artifacts, so the owner can sideload and verify on his phone (law 4).

Report: the triage counts, the golden-path pass count, and anything in the brief that
V1_SCOPE contradicts.
```

## P11. Projects are real files: open, new, save, reopen [Opus → Sonnet for UI]

```
Read brief §11, CLAUDE.md laws 1 and 7, core-font's UfoProject, and docs/SCREENS_V1.md
S01, S02 and S06, with ui/v1-screens/Main, NewProject and FontInfo.

Design (Opus) and write docs/PROJECT_MODEL.md: a single ProjectSession that owns the open
project directory (one UFO 3 per master, typewright.json, scrapbook/, comparisons/,
lessons/, build/). Cover its undo/redo history (every edit in Draw, Space and Trace goes
through it), autosave (debounced, atomic write-then-rename), and how locks on approved
glyphs are recorded (law 1: an edit to a locked glyph needs an unlock and produces a
stored diff).

Platform actuals:
- desktop: directory chooser; recent projects in the app's config dir
- Android: Storage Access Framework tree URI with a persisted permission; survives process
  death and configuration change
- web: File System Access API where available, otherwise an in-memory project with explicit
  "download project (.zip)" and "open .zip". Say so in the UI.

Move the Scrapbook notes and Workbook reflections from memory into the project
(lessons/, scrapbook/manifest). Build Projects (S01), New project (S02) and Font info (S06)
exactly as the mockups show them, using the control deck from P13. If P13 hasn't run yet,
build the deck first as its own commit.

Acceptance: golden-path step 7 passes on desktop. An Android instrumented test covers
save → kill → restore (owner runs it on device). The written UFO opens in fontTools and
passes ufoLib validation.
```

## P12. Open the font the user brings; remove the Hyle Deco hard-wiring [Sonnet]

```
Read brief §4.5 (licence attribution audit on import) and §12, and PROJECT_MODEL.md.

1. Import TTF, OTF and UFO into a new project: TTF/OTF → UFO through core-font, with quad
   curves kept as quadratic and labelled. Show the import licence audit as the modal ink
   block in S03 (ui/v1-screens/ImportAudit). Read the name table, OFL, copyright and "Reserved Font Name", and
   refuse or warn exactly as the brief says.
2. Replace every non-test read of fonts/HyleDeco-Regular.ttf in ui/, campaign/ and learn/
   with the open project: Draw's glyphs, Overlay's "your font" layer, the Anatomy Lens, the
   Workbook gates and the style detector. With no project open, these views show an empty
   state that says "open or start a project". They never show a silent fixture.
3. A grep test in CI fails if any main source set references HyleDeco.

Acceptance: open Hyle Deco from disk as a user would. The Workbook Task 4 gate result
matches what it showed before, and a second font (any OFL family from the corpus) gives its
own real numbers.
```

## P13. The control deck, the specimen home, and themes [Sonnet]

```
Read docs/SCREENS_V1.md §1–3 and S04, S20–S24, then ui/v1-screens/Deck.dc.html,
DeckStates, Home, Glyphs, DrawMenu, Map, Settings, ThemeEditor and About.

1. The control deck as one Compose component, reproducing Deck.dc.html exactly: concentric
   arcs (section arc and toggle arc), both circles and every state (Back, Close in the one
   red, the ⋯ ring, filled primary actions, dimmed, hidden), labels set on the arc, and the
   bloom under it. Gestures: swipe along the section arc to rotate it and settle; tap a
   label; long-press for the Map. System back = the left circle. Keys as SCREENS_V1 §2.6.
   Screen readers name every circle and toggle.
2. The router behind it: the sections in SCREENS_V1 §2.1's order, the sub-views, and state
   that survives process death. Draw and Space stay regions of one sheet. Remove the two
   corner buttons, the header room chip and the edge marks. Close OQ 56/82/89.
3. Every screen P0–P9 built (the sheet, Learn, Workbook) moves onto the deck: Learn's tab row
   becomes its toggle arc; Workbook gains This task · All tasks.
4. Home (S04) with the Specimen and Glyphs toggles, the ⋯ page menu (S20), and the Map
   (S24).
5. The theme system (SCREENS_V1 §3): theme JSON files, the built-ins, Settings (S21), the
   custom theme editor (S22) with every guardrail, and About (S23, which P18 completes).
   Every colour in ui/ reads from the theme roles. A test fails on any hard-coded colour
   outside the theme module.
6. C4: the bloom may dissolve only the grid and the grain. Metric lines, guides and
   construction geometry draw at full strength everywhere, with a Construction toggle in
   Draw. Screenshot-test that construction lines under the deck keep full opacity.

Sections not built yet (Capture and Trace until P16, Check until P14, Ship until P15) show
the law-4 stub state inside the real deck: they say what they will do and that it isn't
built. They're never hidden.

Acceptance: screenshot tests for every screen above at phone and desktop width, compared
against the mockups; a theme round-trip test; and a guardrail test (collision → save
blocked).
```

## P14. Check, Economy, and compiling for real [Opus spike, then Sonnet]

```
Read brief §3, §8, §12, UI_SPEC.md §3 (box plot rows, report items), the compile module,
and qa's LayerOneChecker. Open the explorer → Check and Economy.

A. Spike (Opus, time-boxed, report before building): on Android, can Chaquopy install
   fontmake, ufo2ft, cffsubr and skia-pathops (or a pure-Python overlap-removal fallback)
   for arm64-v8a? Measure APK size added and a cold compile of Hyle Deco. Do the same for
   fontc via JNI (cargo-ndk). Recommend one route, then stop for Madhav's D5 decision.
B. Desktop: make SystemPythonFontmakeBackend real. Find python3, check the imports, and name
   any missing package with the exact pip command. Run fontmake in a temp dir, stream
   progress, and show errors in plain language. Make LayerOneChecker real on desktop:
   fontspector if its binary is present, fontbakery otherwise, same parsing.
C. Android: implement the D5 route. Layer one on Android runs only if the spike shows
   fontbakery fits. Otherwise the Check report says "layer one needs the desktop app" (law
   4 honesty).
D. Web (per D4): no compile. Ship offers the UFO and repo folder as a zip, and says why.
E. Build the Check report (S14) and the Economy view (S15) exactly as ui/v1-screens/Check
   and Economy show them (P1 item 4, owed): worst-first rows, log axis 3→3,000, the open row with the 30 faces, style chips
   with n, the legend, the fence sentence, "What these checks do not measure", and the
   severity shapes.

Acceptance: golden-path steps 4 and 5 pass on desktop. On Android they pass in the owner's
device run. Compiled Hyle Deco matches fontmake's output on the same UFO within tolerance
(compare with fontTools ttx, ignoring timestamps).
```

## P15. The Ship room [Sonnet]

```
Read brief §12, the pipeline generators in qa, and the explorer → Ship.

Build Ship (S16) and its building state (S17) as ui/v1-screens/Ship and Building show
them, with the Google Fonts · Own licence toggle. A checklist that writes the Google Fonts repository
(OFL.txt with the copyright pattern, name table strings, version, STAT for statics, sources/,
fonts/ttf/, DESCRIPTION.en_us.html or ARTICLE per the brief §15 item 8 answer, upstream.yaml,
and the issue body) into a folder the user picks, or a zip on web. Block it while Check has
any fail, and name the fails. Re-run Check on the compiled binary, not only the sources.
Leave out GitHub push (post-V1). The UI says so in one line.

Acceptance: golden-path step 6. The produced folder passes fontbakery's googlefonts profile
with no FAIL for a font that passed Check in-app, on desktop.
```

## P16. Capture and Trace (P3's owed half) [Sonnet; Opus for fiducial geometry]

```
Read brief §7, docs/SCREENS_V1.md S07–S10, ui/v1-screens/Templates, Capture, Cells, Camera
and Trace, docs/TYPEWRIGHT_HANDOFF.md §4 M1, scripts/templates, and engine-trace.

1. Pure (engine-trace, no android.*): fiducial detection on the template sheets,
   homography and perspective correction, per-cell detection mapped to the template
   manifest (glyph names), and a stroke-width estimate. Implement from published
   descriptions, not OpenCV, so desktop and web get it too. If OpenCV is truly needed,
   isolate it in a new capture-android module per OQ 6.
2. Input: a file or gallery image on all targets. Camera capture on Android through CameraX
   in androidMain.
3. The Capture review grid and the Trace screen as the mockups show them: the raster
   underneath, the scrubber (3 stages visible by default, 5 behind "adjust"), the count
   collapsing on a log scale, the glyph's box plot in the header with the live point,
   per-stage parameters, and Accept / Adjust / Hand edit. Accept locks the glyph (law 1).
4. Wire the Workbook's "Trace again with Fit" button to this screen.
5. Templates (S07): generate the printable sheets (24 cells, four corner marks, A4 and
   Letter) from the template manifests, and export the existing SVG pack. SVG drawings
   imported through Capture, named by template, skip the raster stages and start at Fit.

Test input: photographs of printed Hyle Deco template sheets, including skewed and badly lit
ones. Add them as fixtures. Acceptance: golden-path steps 1–2 pass. At least 95% of the
cells on the fixture sheets map to the correct glyph name, and the report lists every miss.
```

## P17. Editing hardening [Sonnet]

```
Audit Draw and Space against brief §5 and §10's v1 must-haves, now running on real projects.
Fill any gaps: undo/redo everywhere through ProjectSession, numeric entry and `=`
expressions, and the Hold-object radial for contextual actions. Wire up every command-palette
entry left with `run = null`. Kerning groups and .fea must round-trip through UFO. Fix
anything that loses data when switching rooms or backgrounding the app.

Acceptance: a scripted edit session of 200 random operations, then undo-all, gives
byte-identical UFO output. A second session followed by save and reopen gives the same
state.
```

## P18. Release engineering [Sonnet]

```
Requires D2 (name clearance started). If it's missing, stop and say so.

- About / licences (S23, ui/v1-screens/About): every entry in THIRD_PARTY.md plus the
  Skia/FreeType/libpng notices Skiko requires (OQ 14), on all targets, and in the web bundle.
- Check that P10's licence work is complete: every file has its SPDX header, the
  dependency-direction check passes, and THIRD_PARTY.md covers every dependency.
- The support link (LICENSING.md §3): shown in About on desktop, web and GitHub-release
  builds, and absent from the Play build (a build flag, tested). There's no payment code in
  any build.
- Android: release signing from CI secrets (never committed), R8 with keep rules verified
  by a release-build smoke test, versionCode/versionName from a single version file, an
  AAB for Play, an APK for direct download. targetSdk stays 36 unless the owner has
  verified 37 on device (OQ 12).
- Linux: one packaged format (recommend AppImage or Flatpak; say which and why) with the JRE
  bundled through jpackage. Python/fontmake is detected, not bundled; the first-run check
  explains how to install it.
- Web: static build with the locale guard from OQ 13; a "Preview" banner stating what the
  web can't do (compile, layer one); deploy instructions for a static host.
- Privacy: a one-page policy stating that the app collects nothing (law 3), linked from
  About and usable for the Play listing's data-safety form. Crash handling is local only:
  on a crash, show a report the user may choose to share. Nothing is sent automatically.
- Store and landing copy drafts in docs/release/, marked for Madhav's edit.
- CHANGELOG.md and a v1.0.0-rc1 tag.
```

## P19. Release-candidate audit [Opus, fresh agent that has not built any of it]

```
You did not build this app. Read docs/V1_SCOPE.md and PROMPTS_V1.md §1 only, then use the
release artifacts from CI, not the source.

Run the golden path on the Linux build and the web preview as a first-time user would.
Start with the Hyle Deco template photos and, separately, by opening an OFL font from the
corpus. Record every step with screenshots. Try to break it: cancel mid-compile, kill the
app during autosave, open a malformed TTF, open a font whose licence the audit should
refuse, and use a locale with non-ASCII characters in the project path.

Write docs/release/RC_AUDIT.md: go / no-go per target, every defect with repro steps, and an
owner device checklist for Android (RedMagic 11 Pro) listing each golden-path step, the
expected result, and a blank for the owner's result. Do not fix anything in this prompt.
```

---

## Owner steps (not Claude Code)

1. Answer what's left of D2, D4, D5, D6, D8 and D9, and confirm the four points in
   SCREENS_V1 §8. Edit the Design canvas directly wherever a screen should change, then
   re-export the changed boards into `ui/v1-screens/` before the UI prompt that uses them.
2. Start the TYPEWRIGHT name clearance with Shiva (D2), and set up the merchant-of-record
   account for the support link (LICENSING §3).
3. Print the template sheets (P16 generates them) and draw the Hyle Deco redraw. It's the fixture for P16 and the
   dogfood for V1.
4. Install each CI APK on the phone and fill in the device checklist after P11, P14, P16 and
   P19.
5. Sign off RC_AUDIT.md, then tag v1.0.0.
