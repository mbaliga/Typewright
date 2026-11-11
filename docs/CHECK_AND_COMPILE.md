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

The three records the project holds itself (Metrics, Names, Licence) are fixed on **Font info**, which is editable: each of
their actions opens that page on the value to change (the first of the finding's values that is not yet set), so a font drawn
from scratch can clear its Licence fail without leaving the app. See "Font info" below.

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

**A check that could not judge** is ○ not measured and carries no reviewer quote and no action: there is no verdict for a
reviewer's words to describe or for a fix to answer (`CheckItem` refuses to be built otherwise). When the project's class has
no box in `qa/corpus` (a hand-edited manifest, an older one), node economy says so in those words, and "Curves are polygons"
falls back to the round-letters list and says that list is our heuristic; the Economy page says the same.

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

**Which Python.** `TYPEWRIGHT_PYTHON` (a path to an interpreter) first, then `python3` on the PATH. A path with a directory
part (`.venv/bin/python`) is made absolute once, when it is found, because the build runs from a temporary directory where a
relative path would name another file. The first one that runs is probed for `fontmake`, `ufo2ft`, `fontTools` and,
optionally, `pathops` (skia-pathops). One command installs
all of it into a virtualenv: `pip install -r tools/requirements-golden-path.txt` (fontmake with pathops, and Fontbakery
with the `googlefonts` extras, versions pinned there).

**What a build does.** The request's project snapshot (a `ProjectFiles` map from `ProjectSession.buildSnapshot`, so an
autosave can never race a build) is written to a fresh temporary directory and fontmake is run on the chosen UFO
(`-o ttf`, overlaps removed with pathops when it imports, otherwise kept with a logged warning that says so). The copy and
the font fontmake writes have plain ASCII names (`source/source.ufo`, `out/font.ttf`, glyph files that are not ASCII renamed
`typewright-glyph-N.glif` with `contents.plist` rewritten to match) and the binary gets the project's own name back
(`HyléDeco-Regular.ufo` gives `HyléDeco-Regular.ttf`), so a family with an accent builds in a Java whose file-name encoding is
ASCII (a desktop launched with `LANG=C`); glyph and family names inside the files are untouched. A real build of that is in
`:compile:pythonIntegrationTest`, and a child JVM started with `LC_ALL=C` runs the same path against a stand-in interpreter
in `:compile:desktopTest`. Output is
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

`platformLayerOneChecker()` on a desktop looks for a `fontspector` binary on the PATH and uses it **only if its report can
be read**; otherwise it runs Fontbakery from the same Python as the compiler (`python -m fontbakery check-googlefonts
--skip-network --json …`) and the page says that Fontspector's report could not be read and Fontbakery checked the font
instead. `--skip-network` is law 3: a check the person started must not quietly use the network. Both give the same
`CheckOutcome`s (PASS, WARN, FAIL, SKIP and INFO; the tool's own check id and message are kept, `ERROR` and `FATAL` are
FAIL with the tool's word kept for the Raw view). **INFO is a note, never a warning**: it has its own count and its own
shape and word (`i`, note), the warn count is Fontbakery's WARN and no more, and the report's own tally matches. The
tools are read while they run (a long console report cannot fill a pipe and stall them), and a tool that runs past its time
is stopped with everything it started. The font is handed to the tool under an ASCII name (`Hyle.ttf` for `Hylé.ttf`). The
result is tested against a direct `fontbakery` run on the same file: the FAIL, WARN and INFO counts must equal it.

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
  each item as a shape, a mono title, one sentence that quotes the project's own numbers (node economy also draws its box,
  small, under the sentence), what a reviewer wrote when it went wrong, and an accent action that goes where the fix is
  (Economy, Draw on a glyph, Trace, Capture, Ship, or Font info on the value to change). A tool's INFO notes are listed apart
  as "Notes · not problems", and the checks that passed are counted and listed on request. Plain reads the sentences; **Raw** adds each item's basis (measured, published rule, or our heuristic), the
  note on what its thresholds rest on, and the raw lines, and prints layer one's results as the tool gave them.
- **Layer one on the page.** It runs as the page opens if the platform can run it and it has not already run on this
  exact drawing; **Run again** runs both layers whatever has run. While it runs the page shows two stages (compile with
  fontmake, check the compiled binary), elapsed time, **Cancel** (the project is untouched) and the compiler's output on
  request. A run that stops shows the cause in one sentence ("Layer one stopped while compiling the project with fontmake") and the
output; a cancelled run says nothing changed. If one of Typewright's own checks throws, layer two shows "Check stopped:
<cause>", says nothing in the project changed and offers Try again; it never escapes. Leaving
  the page cancels a run in progress. A finished result is kept for the drawing it ran on, so coming back does not compile
  again.
- **Economy.** Style chips with the number of faces behind each class (the project's own first; a different class can be
  chosen to compare against), a sort order (Worst first, A to Z), a log axis from 3 to 3,000 that stays while the rows
  scroll, and one row per glyph of the open project: the glyph drawn from the project's own outline, a box plot (middle
  half, median, whiskers over the full range, from the corpus) with the person's count as the accent point, the count,
  and the verdict as a shape and a word (● outlier, ◐ above, ✓ in range). A glyph the corpus has no box for is not left
  out: its row says "no box for this glyph in the corpus" with its count and ○ no box, and a sentence under the chips says
  how many of the project's glyphs are judged ("52 of 338 glyphs are judged against this class's faces. The other 286
  have no box in the corpus…"). A tap opens a row to show the faces behind its box as dots, with the fewest and the most
  named. The sort order is at the right of the chip line and the legend and the fence sentence stand just above the deck,
  as Economy.dc.html places them; they say what the marks and the line between "above" and "outlier" mean, and that the
  quarter-of-its-value part is our heuristic. When the project has no
  class and our heuristic cannot read one, the page says to choose one; it never invents a number.
- **Empty states.** No project: "Open or start a project to see its check report" with a way to Projects. A project with
  no glyphs: it says there is nothing to check yet. The page never draws a stand-in font.

## Font info

Font info (S06, reached from Home's ⋯ menu and from Check's Metrics, Names and Licence actions) edits `fontinfo.plist` of the
open project. Each value is a mono label above a value; a tap opens it (Enter keeps what was typed, Esc puts the old value
back, a refused value stays open under its reason), and every change is one `UpdateFontInfo` through the session, so one undo
step, and a locked glyph never matters (font info is not a glyph). What can be changed: family, style, version (`1.024`: a
number, a dot, three digits), designer, designer URL, copyright, the licence, the vendor ID and the vertical metrics
(ascender, cap height, x-height, descender, line gap) as numbers or `=` and an expression over the font's own names
(`=xheight`, `=capheight/2`, `=n.rsb+4`), the same field and the same expression reader the Draw inspector uses. An ascender or
descender also moves the hhea and typo values the font already states, so Google Fonts' "typo equals hhea" holds. Units per em
is fixed when a project begins.

Nothing is invented: the designer, the copyright holder and the licence start empty and are shown as "◐ Not set · add one". The
licence kinds are OFL and Apache, which write the standard name-table lines (IDs 13 and 14) from Google Fonts' requirements,
and Proprietary and Other, which open the text for the person's own words. A value that would write a control character, a
lone surrogate or any other character a plist cannot carry is refused (law 7), as are a web address that does not start with
http(s)://, a vendor ID that is not up to four printable ASCII characters, and a metric that makes no sense (a descender above
the baseline). The rules are `FontInfoRules` (no UI) and the session wiring is `FontInfoEditor`; a blank project clears its
Licence fail through them in `FontInfoEditorTest`.

## Golden path

Steps 4 (Check) and 5 (Compile) run the real fontmake and Fontbakery, so they need a Python that has both:
`pip install -r tools/requirements-golden-path.txt` into a virtualenv and `TYPEWRIGHT_PYTHON=<venv>/bin/python`.
Without it they **fail with the backend's own sentence** (never skip), so the ratchet in `golden-path/passing-steps.txt`
can hold them; CI's golden-path job sets this up. Step 4 compares layer one's FAIL count with a direct Fontbakery run on the
same file even when the platform picked Fontspector (it then runs Fontbakery for the comparison). `:compile:pythonIntegrationTest`,
`:qa:pythonIntegrationTest` and `:ui:pythonIntegrationTest` run the same tools through the backends' code paths and the
Check page's controller (including Hyle Deco opened through the importer, compiled, checked, and its FAIL count set against a
direct `fontbakery` run); they fail with the pip command when `TYPEWRIGHT_PYTHON` is unset, and are separate tasks so the core
and Android jobs, which have no such Python and run `:ui:desktopTest`, stay green. Measured on a warm machine: the virtualenv
and pip install 19 s, the golden path 39 s, `:compile:pythonIntegrationTest` 16 s, `:qa:pythonIntegrationTest` 22 s,
`:ui:pythonIntegrationTest` 44 s.

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
  hinted TrueType. Ship builds an unhinted font rather than blocking: the desktop backend gives it the `gasp` table and the `prep`
  program Google Fonts' profile asks of an unhinted font (docs/SHIP.md, "Google Fonts fix-ups at compile"), and the log says
  "not hinted". Whether Google Fonts' reviewers accept an unhinted upload is for the owner to ask them.
