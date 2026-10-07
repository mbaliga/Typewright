# Audit: does Typewright match and beat the best in class?

Written 7 October 2026. Competitor facts below were gathered from public sources during the
audit and should be re-checked before they are quoted outside the repository.

## Verdict

Typewright's *thesis* (the user's drawing is the truth, and the app teaches what quality means) is
ahead of every tool surveyed. Its *breadth* is behind the desktop editors (Glyphs, FontLab,
RoboFont) and its *input* side is on par with the handwriting-to-font web tools only once Capture
and Trace are on a device. No tool in the field answers "what do you want this font to be?" before
the first letter is drawn, then keeps that answer beside the drawing. That is now built (the Brief,
docs/BRIEF.md), and it is the largest single gap the audit found that Typewright could close alone.

## Capability matrix (aggregated across the field)

"Field" is the best of: Calligraphr / YourFonts (handwriting to font), Glyphs 4 / FontLab / RoboFont
(editors), fontc + Fontspector + Font Bakery (build and QA), and the Google Fonts onboarding path.

| Capability | Best in the field | Typewright | Standing |
|---|---|---|---|
| Handwriting capture to glyphs | template scan, web | Capture + Trace (device verification owner-only, CLAUDE.md law 4) | par, unverified on device |
| Outline fitting | editors' tools, hand-tuned | cubic fitter targeted at fewer points, not yet meeting its target | behind |
| Drawing tools | Glyphs 4 / FontLab | Draw room with puck, construction visible | behind in depth, ahead in teaching |
| Spacing and kerning | Glyphs, HT Letterspacer, autokern | own autospacer and autokerner | par |
| Quality gate | Fontspector (also runs in WASM), Font Bakery | `qa` module with the Fontbakery result model; Fontspector CLI checker | par; Fontspector in WASM would put a full gate in the browser |
| Build | fontc 1.0 | `compile` (own pipeline, hosted opt-in on web) | gap: adopt fontc as the compiler where it fits |
| Language coverage | hyperglot / shaperglot | script templates | gap: language nudges from hyperglot data |
| Teaching what quality means | none | Learn + Workbook + measured distributions | **ahead** |
| Measured style knowledge | none | style atlas (613 faces, 23 classes), measured not invented | **ahead** |
| Finding the intent first | none | **Brief** (new) | **ahead** |
| Plain open files | UFO in editors | UFO 3 + JSON, opens in FontForge/Glyphs/RoboFont | par |
| No telemetry | varies | law 3 | ahead |
| Cross-platform | desktop-only editors; web-only scanners | Android, Linux, Web | ahead on reach |

## Findings that need action

Ranked by how much each moves Typewright toward the front of the field.

1. **Web cannot read data packs the Brief needs.** `loadStyleAtlas()` fails in a browser
   (OPEN_QUESTIONS item 131). Same root cause as other data packs on web. Highest-value fix: it
   unlocks the guide on the platform where most first users will land.
2. **Scorer accuracy.** The earlier style scorer got 36% top-1 on its own atlas; the k-NN reading
   used by the Brief gets 52% top-1 / 83% top-3 among corpus classes (40% / 69% across all). Replace
   the old scorer wherever it is user-facing, and keep showing neighbours.
3. **Detector bugs.** Storeys, contrast stress and two geometry helpers were wrong on real faces.
   Fixed here with tests (item 132). Re-run any stored measurements.
4. **Outline fitting.** The fitted-target counts in CLAUDE.md are a target, not a result
   (`FitPipelineHyleDecoValidationTest` prints but does not assert). Make it meet and assert them
   before claiming Google Fonts-quality outlines.
5. **Compiler.** fontc 1.0 is the compiler Google Fonts itself uses. Matching its output is the
   strongest quality claim available; evaluate it as the `compile` backend.
6. **Gate in the browser.** Fontspector runs in WASM; a web gate that needs no hosted build would
   honour law 3 better than the opt-in endpoint.
7. **Language coverage nudges.** Hyperglot/shaperglot data can say "with these letters you also
   cover Polish and Czech": cheap, motivating, measurable.
8. **Learn faces use default instances** for some variable fonts (item 133).
9. **Competition.** Glyphs 4 narrows the editor gap on price and polish. Typewright should not
   compete on tool depth; it should compete on intent, teaching and reach.

## Where Typewright already beats the field

- It treats the user's drawing as inviolable and shows a diff for every edit.
- It shows quality as distributions from a corpus, not a pass/fail from a rule list.
- It is the only tool that connects a stated intent to a number, then back to the drawing.
- It runs on a phone, a laptop and a browser from one codebase, with no telemetry.

## Prompts to close the gaps

Suggested in this order (Sonnet is the right model for each; they are iterative repo work, with
Opus only for the fontc-backend design):

1. *Web data packs:* load `data/*.json` on Wasm (fetch from the app's own origin), then enable the Brief atlas there.
2. *Fit to target:* make `FitPipelineHyleDecoValidationTest` assert the CLAUDE.md fitted targets.
3. *Language nudges:* ship a hyperglot-derived coverage list and show it in Check.
4. *Fontspector WASM:* run the gate in-browser behind the existing `qa` interface.
5. *fontc backend (Opus, design first):* decide how `compile` hands off to fontc and what stays ours.
6. *Brief in the golden path:* add a Brief step to `golden-path` so a brief survives a build.

## Is the Brief worth having?

Yes. It closes the gap between intent and measurement that none of the surveyed tools address,
reuses what is already built (the atlas, the detector, the Learn scenes), adds no network or
telemetry, and stays inside the laws: choices are stored, numbers are derived, everything shown is
either measured or labelled "our heuristic". Its risks are honest ones: genre reading is
52%/83% accurate and says so, the browser build cannot yet use it, and the screen has had no device
verification.
