# CLAUDE.md — Typewright

Instructions for Claude Code sessions in this repository. Read `TYPEWRIGHT_BUILD_BRIEF.md`
first, then `UI_SPEC.md`, then the prompt you were given from `PROMPTS_CLAUDE_CODE.md`.

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
6. **The explorer is the UI.** `ui/typewright-explorer.html` defines look and interaction.
   Reproduce it; do not improvise a component's look. If a screen is missing from the
   explorer, stop and say so rather than inventing.
7. **Plain files.** A project is a directory of UFO 3 and JSON. No proprietary blob. Every
   file we write must open in FontForge, Glyphs or RoboFont.
8. **Colour is meaning only.** Violet = selected, cyan = snapping to; comparison layers get
   colour plus a line pattern; never red or green as meaning; severity is a shape and a word.

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
- Data packs (`qa/corpus`, `learn/scenes`) are JSON/YAML checked in under `data/` with a
  generator script beside them; never hand-edit generated data.
- Commit messages: imperative, one line of what and one of why; reference the milestone
  (M0–M6) and the prompt (P0–P8).
- Every source file carries `SPDX-License-Identifier: FSL-1.1-ALv2` (app modules) or
  `Apache-2.0` (engine modules). An Apache module may never depend on an FSL module; CI
  checks this (`tools/check_licences.py`, which also holds the directory-to-licence map).
  The split and its reasons are in `docs/LICENSING.md`.
- Third-party code: record every dependency with its licence in `THIRD_PARTY.md` as you
  add it. GPL code (Potrace, HT Letterspacer) is not linked; reimplement from published
  descriptions if needed and say so.

## When unsure

Stop and report rather than guess. The brief marks what is [CONFIRM]. Put open questions
in `docs/OPEN_QUESTIONS.md` with the prompt id and continue with the rest.
