# qa

The quality gate: Typewright's own checks (node economy, extrema, overshoot, anchors, provenance and the rest of brief §12) and the model for Fontbakery or Fontspector results, all reported in plain language. Any number it judges a user by comes from qa/corpus and is shown as a distribution.

## What P1-qa built

- `LayerOneChecker` (`LayerOneChecker.kt` + platform actuals): the bridge to a real googlefonts-profile
  tool. On the desktop, `DesktopLayerOneChecker` runs Fontspector when its binary is on `PATH` and
  otherwise Fontbakery (`python -m fontbakery check-googlefonts`) in the Python `TYPEWRIGHT_PYTHON`
  names; with neither it reports one SKIP that names the pip command. On Android and the web the actual
  is `Unavailable` and says "Layer one needs the desktop app". The desktop checker is handed out wrapped in
  `BackgroundLayerOneChecker`: every call runs on a background thread and cancelling the caller interrupts it,
  which stops the tool and everything it started, so a screen can call it from its own thread and cancel it.
  Where Fontbakery was found is remembered for the process (a miss never is). See "The Check report" below
  and `docs/CHECK_AND_COMPILE.md`.
- `checkNodeEconomy` (`NodeEconomyCheck.kt`): per-glyph on/off-curve counts against a `qa:corpus`
  style-class box, with a plain-language rationale (brief §8.3's legend restated as a sentence).
- The geometric heuristics (`GeometricChecks.kt`): alignment-miss, collinear segments, jaggy turns,
  short segments and semi-vertical, at fontbakery's published thresholds
  (docs/RESEARCH_font_quality.md), plus extrema-on-curve and the off-curve ratio (informational).
- `checkOvershootPresence` (`OvershootFinding.kt`): flags "no overshoot" only, and exempts
  flat-topped/flat-bottomed round shapes (brief §7) -- both heuristics are documented in that
  file's own KDoc, per law 5.
- `checkAnchorsPresent` (`AnchorPresenceCheck.kt`): a best-effort, allow-list-based check over the
  52 base Latin letters -- see "Deferred checks" below for why it stops there.
- `com.asoc.typewright.qa.ship` (`ShipPipeline.kt`): pure string generators for `OFL.txt`,
  `DESCRIPTION.en_us.html` and a `METADATA.pb`-shaped scaffold (not `upstream.yaml`, which
  `gftools` now treats as legacy -- docs/ARCHITECTURE_REVIEW.md section 5 item 52). No file I/O, no
  compile step, no Fontspector requirement.

## The Check report

`com.asoc.typewright.qa.report` turns the layer-two checks into the report the Check screen shows.
`buildCheckReport(CheckInputs)` is a pure function of a project's UFO and the node-economy corpus: it
runs fourteen checks in five groups (Outlines, Metrics, Provenance, Names, Licence), gives each a
severity that is a shape and a word (● fail, ◐ warn, ✓ pass, ○ not measured), and sorts them in a
documented order, the order a Google Fonts reviewer attacks a font in (`ReviewOrder`). The counts and the
severity bar are derived from the items every time, never stored. `economyRows` and `faceSpread` give
the Economy view its rows and the faces behind each box. `NotMeasured.PARAGRAPHS` is the text of "What
these checks do not measure". Layer one's results, when a tool ran, are attached with `withLayerOne`;
they are the tool's own words and are never merged into the fourteen. How the thresholds are chosen and
what is a heuristic is in `docs/CHECK_AND_COMPILE.md`.

`./gradlew :qa:jvmTest` needs no Python. `./gradlew :qa:pythonIntegrationTest` runs the real Fontbakery
and needs `TYPEWRIGHT_PYTHON` to name a Python with `tools/requirements-golden-path.txt` installed.

## Deferred checks

Brief §12 names several checks this task did not implement, because each needs infrastructure this
module does not have yet, not because they were forgotten:

- **Name-table uniqueness** -- needs a font/repo-wide name-table writer (`core-font` does not write
  `name` records yet; `SfntFont` only reads).
- **Composite completeness** -- needs a script/glyph-inventory model (which glyphs a font is
  expected to have for a given script/coverage level), which does not exist yet; `checkAnchorsPresent`
  above hits the same gap and works around it with a small hard-coded allow-list rather than waiting
  for the real model.
- **Spacing sanity** -- needs control-string proofing infrastructure (rendering a string of glyphs
  and measuring the result), which belongs nearer `shape-preview`/`learn` than here.
- **Source/italic provenance** -- these are workflow-level guarantees ("is this UFO really the
  origin of that binary", "was this italic drawn, not sheared") rather than a pure geometry check
  over one glyph; docs/RESEARCH_font_quality.md's provenance section is the reference for what a
  real check would need, but it needs the project's whole history, not a single `UfoProject`.
- **Terminal-style consistency** (flat/round/angled terminals, brief §8.4's classifier vocabulary)
  -- needs a terminal classifier, which is P1b/the style detector's territory, not this task's.
- **Tangent continuity** and **stroke consistency** -- docs/RESEARCH_font_quality.md's own tiering
  puts both in "a frontier where no automated check exists" (its tool-coverage table repeats
  "stroke consistency, curve smoothness" as a gap even Fontbakery/Fontspector leave to human eyes).
  Unlike the six fontbakery outline heuristics above, neither has a published threshold or
  reference implementation to match against, so building either now would invent a number law 5
  forbids calling measured; `checkCollinearSegments`/`checkJaggyTurns` bound *some* of what a human
  reads as a smooth join, but are a narrower, different thing (a chord-angle threshold at one
  joint, not tangent-vector agreement across a curve-to-curve or line-to-curve boundary, and not
  weight consistency across a font).
- **Licence and attribution audit on import** -- an import-time gate over a project's own licence
  files and OFL/name-table strings, not a per-glyph geometry check over a `Glyph` or `UfoProject`
  this module's check functions already take; it belongs with whatever reads a project in on
  import, not with layer two's outline/economy checks.

The checks above plus this list account for every item brief §12 names, except
"batch-transform preview" -- a `ui`-side preview feature, not a check, and out of this
data/logic-only task's scope by definition. Half-implementing any of the deferred ones against ad
hoc data would produce a number that looks measured but is not (law 5); they are listed here, once,
rather than scattered as `TODO`s through the checks above.
