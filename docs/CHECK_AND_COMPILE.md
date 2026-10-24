<!-- SPDX-License-Identifier: FSL-1.1-ALv2 -->
# Check and compile: what runs where, and what each platform says

Check (S14 Report, S15 Economy) judges the open project twice. **Layer two** is Typewright's own fourteen checks
over the project's UFO, written in `qa` and run everywhere. **Layer one** is Google Fonts' own profile
(Fontspector, or Fontbakery) run on a *compiled binary*, which needs a compiler and a Python on the machine, so it
runs on a desktop and says so in words everywhere else. This page is the one place that says how each is found,
what it needs and what a person is told when it is missing. The code is `compile/`, `qa/` and `ui/check/`.

## Layer two: the report

`buildCheckReport(CheckInputs)` in `qa/report` is a pure function of the project's UFO, the style class it is judged
against, the corpus that holds that class's boxes, what the project records of where its outlines began and the names
of its other masters. It measures the UFO and never a binary (a compiler changes point counts, so measuring the
binary would judge the compiler, not the drawing). The same inputs give an equal report.

| Group | Check | Basis |
|---|---|---|
| Outlines | Node economy; curves are polygons; overshoot; alignment miss; extrema off the curve; collinear segments; jaggy turns; short segments; semi-vertical; anchors | node economy is **measured** against `qa/corpus` (shown as a box, a count and the fence; the fence's `0.25 x Q3` floor is **our heuristic** and says so); the small outline checks use Fontbakery's published thresholds or are marked **our heuristic** |
| Metrics | Vertical metrics | Google Fonts' vertical-metrics rules, **published** |
| Provenance | Source is a fossil | **our heuristic** (see below) |
| Names | Family and style names | **published** (Google Fonts name rules; the Reserved Font Names come from the same parser the importer uses) |
| Licence | Copyright line and OFL strings | **published** (the Open Font License route) |

**Order.** "Sorted by what a reviewer attacks first": groups run Outlines, Metrics, Provenance, Names, Licence; inside a
group what fails comes first, then what warns, then what could not be measured, then what passes; ties keep the order
above (`ReviewOrder`, tested). A check that had nothing to judge by is **not measured** (○), never a quiet pass.
Severity is always a shape and a word: ● fail, ◐ warn, ✓ pass. The counts and the severity bar are derived from the
items on every call and are never stored.

**Provenance.** The project records that it began from a font (`BriefSource.FONT`) and, per glyph, who approved the
outline (`ApprovalOrigin.IMPORT` for the import itself). It does not record whether that font was a compiled file or a
UFO folder. `importProvenanceOf` therefore reads the one trace a compiled TrueType font leaves in the outlines it was
imported with, quadratic curves, which a source drawn in Typewright (cubic) never has. A glyph counts as "still as
imported" while its approval came from the import and its outline is the one that was approved. That is a heuristic and
the item says so; an OTF (cubic) import reads as drawn here. See the open question on recording the import's origin.

**What the report does not measure** is the last section of the page and is written once in `NotMeasured`: a green
report means "will not break", not "is good"; tangent continuity, stroke consistency, spacing, terminal style, composite
completeness and "drawn rather than slanted" are not built and are not in the count.

## Compile: finding the compiler

`platformCompileBackend()` answers one of three backends. Each says, in `availability()`, whether it can run here and
why not in a sentence the UI shows as it is. `availability()` never throws and never installs anything.

| Platform | Backend | What it says when it cannot compile |
|---|---|---|
| Linux desktop | `SystemPythonFontmakeBackend` | `Python 3 was not found on this computer (looked at …). Install Python 3, then run: pip install fontmake`, or `Python 3.11 (python3) is missing fontmake, ufo2ft and fontTools. Install them with: pip install fontmake` (the missing packages are named, with the exact pip command, and the interpreter it looked at) |
| Android | `ChaquopyBackend` | "Compiling on this phone is not built in this version (the Android route is an owner decision, D5); build in the desktop app" |
| Web | `HostedEndpointBackend` | "The web preview does not compile (D4); export the UFO and Ship folder and build on the desktop app" (it is real only if an endpoint is configured; none is deployed) |

**Which Python.** `TYPEWRIGHT_PYTHON` (a path to an interpreter) first, then `python3` on the PATH. The first one that
runs is probed for `fontmake`, `ufo2ft`, `fontTools` and, optionally, `pathops` (skia-pathops). One command installs
all of it into a virtualenv: `pip install -r tools/requirements-golden-path.txt` (fontmake with pathops, and Fontbakery
with the `googlefonts` extras, versions pinned there).

**What a build does.** The request's project snapshot (a `ProjectFiles` map from `ProjectSession.buildSnapshot`, so an
autosave can never race a build) is written to a fresh temporary directory and fontmake is run on the chosen UFO
(`-o ttf`, overlaps removed with pathops when it imports, otherwise kept with a logged warning that says so). Output is
streamed line by line to `onLog`. `ttfautohint` is used only when it is on the PATH; otherwise the log says "not hinted:
ttfautohint not found" and the build goes on. Cancelling the calling coroutine, or a timeout, kills fontmake and
everything it started and deletes the temporary directory: **cancel leaves the project untouched**, because the
project's own files are never written to. A failure is one plain sentence in `CompileResult.Failure.reason` (Python
missing, a package missing, a glyph or segment fontmake cannot read, a timeout, or fontmake's own last error line); the
raw log is kept next to it.

**Quadratic outlines.** A font opened from a TTF keeps its outlines as quadratic `qcurve` points (OQ 125). fontmake's
default overlap remover cannot read them, so the *copy sent to fontmake* is rewritten as cubic by exact degree
elevation (lossless) and the log says how many glyphs were rewritten. The user's project is never changed. The real
flow is tested end to end: Hyle Deco opened through the importer, snapshotted, compiled, and read back with `core-font`;
glyph count, advances and units per em equal the project's and every glyph's box is within 2 font units of it.

## Layer one: finding the checker

`platformLayerOneChecker()` on a desktop looks for a `fontspector` binary on the PATH and, if there is none, runs
Fontbakery from the same Python as the compiler: `python -m fontbakery check-googlefonts --skip-network --json …`.
`--skip-network` is law 3: a check the person started must not quietly use the network. Both give the same
`CheckOutcome`s (PASS, WARN, FAIL, SKIP; the tool's own check id and message are kept, and Fontbakery's `INFO`, `ERROR`
and `FATAL` are folded into WARN or FAIL with the tool's word kept for the Raw view). The result is tested against a
direct `fontbakery` run on the same file: the FAIL count must equal it.

| Platform | Layer one says |
|---|---|
| Desktop, tool found | "Layer one: Fontbakery 0.12.10 on this computer · layer two here" (the page's subtitle) |
| Desktop, no tool | "Layer one is not available on this computer", and the body says why, naming the pip command |
| Android and web | "Layer one needs the desktop app · layer two here". Fontbakery and Fontspector run on a computer, not on this device or in a browser. |

With no tool at all, `check()` still returns one SKIP outcome that names the pip command, so a caller never has to
special-case "no report"; a skip is never a pass.

## The Check page

- **Deck.** Section Check; left Back; toggles **Report · Economy** and, in a second group, **Plain · Raw** (hidden in
  Economy); right circle **↻ Run again**, dimmed while a run is going.
- **Report.** Counts, the severity bar (solid for fail, hatched for warn, plain for the rest; never colour), the groups,
  each item as a shape, a mono title, one sentence that quotes the project's own numbers, what a reviewer wrote when it
  went wrong, and an accent action that goes where the fix is (Economy, Draw on a glyph, Trace, Capture, Ship, or Font
  info). Plain reads the sentences; **Raw** adds each item's basis (measured, published rule, or our heuristic), the
  note on what its thresholds rest on, and the raw lines, and prints layer one's results as the tool gave them.
- **Layer one on the page.** It runs as the page opens if the platform can run it and it has not already run on this
  exact drawing; **Run again** runs both layers whatever has run. While it runs the page shows two stages (compile with
  fontmake, check the compiled binary), elapsed time, **Cancel** (the project is untouched) and the compiler's output on
  request. A run that stops shows the cause in one sentence and the output; a cancelled run says nothing changed. Leaving
  the page cancels a run in progress. A finished result is kept for the drawing it ran on, so coming back does not compile
  again.
- **Economy.** Style chips with the number of faces behind each class (the project's own first; a different class can be
  chosen to compare against), a sort order (Worst first, A to Z), a log axis from 3 to 3,000 that stays while the rows
  scroll, and one row per glyph of the open project: the glyph drawn from the project's own outline, a box plot (middle
  half, median, whiskers over the full range, from the corpus) with the person's count as the accent point, the count,
  and the verdict as a shape and a word (● outlier, ◐ above, ✓ in range). A tap opens a row to show the faces behind its
  box as dots, with the fewest and the most named. The legend and the fence sentence say what the marks and the line
  between "above" and "outlier" mean, and that the quarter-of-its-value part is our heuristic. When the project has no
  class and our heuristic cannot read one, the page says to choose one; it never invents a number.
- **Empty states.** No project: "Open or start a project to see its check report" with a way to Projects. A project with
  no glyphs: it says there is nothing to check yet. The page never draws a stand-in font.

## Golden path

Steps 4 (Check) and 5 (Compile) run the real fontmake and Fontbakery, so they need a Python that has both:
`pip install -r tools/requirements-golden-path.txt` into a virtualenv and `TYPEWRIGHT_PYTHON=<venv>/bin/python`.
Without it they **fail with the backend's own sentence** (never skip), so the ratchet in `golden-path/passing-steps.txt`
can hold them; CI's golden-path job sets this up. `:compile:pythonIntegrationTest` and `:qa:pythonIntegrationTest` run
the same tools through the backends' code paths and are separate tasks so the core and Android jobs, which have no
such Python, stay green.

## Owner decisions and what only a device can show

- **D5, the Android compile route (not spiked here).** Chaquopy is MIT since 12.0.1. The risk is native wheels: it must
  install `fontmake`, `ufo2ft`, `cffsubr` and `skia-pathops` (or accept a pure-Python overlap remover) for `arm64-v8a`.
  What to measure on a device: the APK size added, a cold compile of Hyle Deco, peak memory during it, and whether
  Fontbakery's `googlefonts` profile imports at all. The alternative is `fontc` (Rust, Apache-2.0) over JNI with
  `cargo-ndk`. Until then `ChaquopyBackend` says what it says above, and layer one on a phone says it needs the desktop app.
- **Fontspector.** The binary could not be built or run in the development environment, so the Fontspector path is tested
  only against a fake `fontspector` script and its JSON shape is unverified. Fontbakery is what ran for real. If
  Fontspector's profile differs from Fontbakery's on the same file, the two layers would disagree by tool.
- **ttfautohint.** Builds are not hinted unless `ttfautohint` is on the PATH, and the log says so. Google Fonts expects a
  hinted TrueType; whether Ship blocks, warns or builds an unhinted font is a decision for the Ship room.
