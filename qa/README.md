# qa

The quality gate: Typewright's own checks (node economy, extrema, overshoot, anchors, provenance and the rest of brief §12) and the model for Fontbakery or Fontspector results, all reported in plain language. Any number it judges a user by comes from qa/corpus and is shown as a distribution.

## What P1-qa built

- `LayerOneChecker` (`LayerOneChecker.kt` + platform actuals): the bridge to a real googlefonts-profile
  tool. `jvmMain`'s `FontspectorCliChecker` shells out to a `fontspector` binary on `PATH`;
  `wasmJsMain`'s actual is always `Unavailable`, honestly labelled (no runtime for it on the web yet).
- `checkNodeEconomy` (`NodeEconomyCheck.kt`): per-glyph on/off-curve counts against a `qa:corpus`
  style-class box, with a plain-language rationale (brief §8.3's legend restated as a sentence).
- The geometric heuristics (`GeometricChecks.kt`): alignment-miss, collinear segments, jaggy turns,
  short segments and semi-vertical, at fontbakery's published thresholds
  (docs/RESEARCH_font_quality.md), plus extrema-on-curve and the off-curve ratio (informational).
- `checkOvershootPresence` (`OvershootCheck.kt`): flags "no overshoot" only, and exempts
  flat-topped/flat-bottomed round shapes (brief §7) -- both heuristics are documented in that
  file's own KDoc, per law 5.
- `checkAnchorsPresent` (`AnchorPresenceCheck.kt`): a best-effort, allow-list-based check over the
  52 base Latin letters -- see "Deferred checks" below for why it stops there.
- `dev.aarso.typewright.qa.ship` (`ShipPipeline.kt`): pure string generators for `OFL.txt`,
  `DESCRIPTION.en_us.html` and a `METADATA.pb`-shaped scaffold (not `upstream.yaml`, which
  `gftools` now treats as legacy -- docs/ARCHITECTURE_REVIEW.md section 5 item 52). No file I/O, no
  compile step, no Fontspector requirement.

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

Half-implementing any of these against ad hoc data would produce a number that looks measured but
is not (law 5); they are listed here, once, rather than scattered as `TODO`s through the checks
above.
