# CLAUDE.md — Typewright

Instructions for Claude Code sessions in this repository. Read `TYPEWRIGHT_BUILD_BRIEF.md`
first, then `UI_SPEC.md`, then the prompt you were given: P0–P9 are in `PROMPTS_CLAUDE_CODE.md`,
P10–P19 in `PROMPTS_V1.md`. For V1 work also read `docs/V1_SCOPE.md`, `docs/SCREENS_V1.md` and
`docs/INTERACTION_V1.md`.

## What this is

A Kotlin Multiplatform / Compose Multiplatform app that turns hand-made letterforms into
Google Fonts-quality fonts and teaches the user what quality means. Targets: Android, Linux
(JVM desktop), Web (Kotlin/Wasm). Studio: A System of Cells.

## Laws

1. **The user's drawing is the source of truth.** Never regenerate outlines the user
   approved. Approved glyphs are locked; edits require unlock and produce a diff.
2. **Pure-JVM-first.** Correctness-critical logic (`core-geometry`, `core-font`,
   `engine-trace`, `engine-construct`, `qa`) lives in modules with zero Android SDK
   imports, so it is testable off-device and CI stays SDK-free. `android.*` imports are
   banned there.
3. **No telemetry, no analytics, no phoning home, ever.** The only network calls are the
   ones the user asks for: fetching a Google Fonts family for comparison, pushing to their
   GitHub, and, on the web only, an opt-in hosted build that the UI names every time it's
   used and that keeps nothing.
4. **Environment honesty.** This container has no phone and no emulator. On-device
   behaviour is owner-verified only; never claim it works. Features that need the device
   are stubbed, not faked, and the stub says so in the UI.
5. **Measured, not invented.** Any number the app judges a user by comes from
   `qa/corpus` and is shown as a distribution. If a check has no measurement behind it,
   its UI says "our heuristic".
6. **The design is the UI.** For V1 screens, `docs/SCREENS_V1.md`, `docs/INTERACTION_V1.md`
   and the mockup sources in `ui/v1-screens/` define look and interaction. The explorer,
   `ui/typewright-explorer.html`, stays the source for what they don't show: the Lens,
   Overlay and Scrapbook tabs, and the desktop frame. Reproduce them; do not improvise a
   component's look. If a screen is missing from all of them, stop and say so rather than
   inventing.
7. **Plain files.** A project is a directory of UFO 3 and JSON. No proprietary blob. Every
   file we write must open in FontForge, Glyphs or RoboFont.
8. **Colour is meaning only.** The roles are fixed: violet = selected, cyan = snapping to.
   Their values are themeable (SCREENS_V1 §3), and every role also has a shape, so colour is
   never the only signal. Comparison layers get colour plus a line pattern; severity is a
   shape and a word; never green as meaning. **One red, one use:** red (#D0342C on light
   grounds, #FF5A4E on dark) is only ever the fill of the control deck's left circle in its
   Close state, with a white ✕. The ✕ carries the meaning. Nothing else may be red, and
   themes can't reassign it.

## Canon changes for V1 (SCREENS_V1 §1, 25 Sep 2026)

These replace the brief and UI_SPEC wherever they differ; SCREENS_V1 §1 is the written reason.

- **C1.** Rooms switch through the control deck's section arc (SCREENS_V1 §2). The header
  carries the page title only. Edge marks are gone; two-finger swipe and ← → stay.
- **C2.** Round means thumb instrument: the puck, the deck's two circles, the section arc and
  the toggle arc are round. Everything else stays square, flat and shadowless.
- **C3.** One red, one use (law 8).
- **C4.** Construction stays visible. Only the grid and the grain dissolve near the glass;
  metric lines, guides and construction geometry are drawn at full strength everywhere.
  Draw's toggle arc has a Construction switch, on by default.
- **C5.** Meaning-colour roles are fixed and their values are themeable (law 8).
- **C6.** Economy is a toggle inside Check (Report · Economy), not a separate station.
- **C7.** The command palette is also the first item of every ⋯ menu, as well as Ctrl/⌘ K.

`docs/INTERACTION_V1.md` replaces UI_SPEC §3's radial dial and the brief's §5.2 and §5.5
wherever they differ.

## Conventions

- Kotlin official style; `ktlint` clean; no wildcard imports.
- Modules per handoff §10. Each module has a README stating its purpose in two sentences,
  and every public function in `core-*` has a unit test.
- Geometry is in font units (1000 UPM default), y up, integers at rest; floats only inside
  algorithms. Contours: outer counter-clockwise, inner clockwise in cubic sources.
- Tests are the fixtures in `TYPEWRIGHT_BUILD_BRIEF.md` §7 and `fonts/HyleDeco-Regular.ttf`,
  stated as on-curve · off-curve · total (restated 2026-09-24, P0c, written reason:
  docs/ARCHITECTURE_REVIEW.md section 5 items 13–22 — the shipped-glyph numbers were already
  correct but unlabelled by on/off, and the fitted-target totals for o and n were on+off sums
  the fence actually compares as separate on-curve and off-curve counts):
  T 1,763·0·1,763 → 8·0·8, o 80·0·80 → 16·16·32, n 44·0·44 → 14·8·22, H 1,252·0·1,252 → 12·0·12,
  T stem foot y=1 → 0. These numbers do not move without a written reason in the commit.
  The shipped counts are asserted (`HyleDecoCrossCheckTest`). The fitted column is the target
  the fitter is measured against: `FitPipelineHyleDecoValidationTest` prints the fitter's
  output but does not yet meet or assert it.
- Generated data packs (`data/node-economy-*.json`, which `qa/corpus` syncs, and
  `data/learn-faces`) are checked in under `data/` with a generator script beside them; never
  hand-edit generated data. Lesson scenes (`learn/scenes` resources) and the workbook
  (`campaign` resources) are hand-written YAML.
- Commit messages: imperative, one line of what and one of why; reference the milestone
  (M0–M6) and the prompt (P0–P8).
- Every source file carries `SPDX-License-Identifier: FSL-1.1-ALv2` (app modules) or
  `Apache-2.0` (engine modules). An Apache module may never depend on an FSL module; CI
  checks this (`tools/check_licences.py`, which also holds the directory-to-licence map).
  The split and its reasons are in `docs/LICENSING.md`.
- Third-party code: record every dependency with its licence in `THIRD_PARTY.md` as you
  add it. GPL code (Potrace, HT Letterspacer) is not linked; reimplement from published
  descriptions if needed and say so.

## Standing rules for P10–P19 (PROMPTS_V1 §3)

- **Only the lead agent commits.** Sub-agents work in worktrees and hand back diffs.
- **One PR per prompt, into `main`.**
- **Every prompt ends by running the golden-path test**: `./gradlew :golden-path:goldenPath`,
  pasting its summary into the PR. Once a step passes it may not regress: list it in
  `golden-path/passing-steps.txt`, which CI's `goldenPathRatchet` enforces.
- **KDoc says what and why, not build history.** Prompt numbers, OQ cross-references and
  "Task P7:" narratives go in commit messages and `docs/OPEN_QUESTIONS.md`.
- **"Your font" means the open project.** A fixture is allowed only in tests.
- **Every screen follows the control deck** (SCREENS_V1 §2). No screen adds its own back
  button, tab row or title bar.

## When unsure

Stop and report rather than guess. The brief marks what is [CONFIRM]. Put open questions
in `docs/OPEN_QUESTIONS.md` with the prompt id and continue with the rest.
