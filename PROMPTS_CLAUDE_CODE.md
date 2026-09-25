# Typewright: Claude Code prompts (v2)

Model routing: **Sonnet** for every prompt except **P0** (architecture review), **P2**
(fit algorithm), **P5a** (construction geometry and spiro) and **P1b** (style detector
feature design), which go to **Opus**. Each prompt is standalone. Unzip the pack into the
repo root first; every prompt begins by reading files from it.

Order: P0 → P1 → P1b → P2 → P3 → P4 → P5a → P5b → P6 → P7 → P8 → P9. P9 (web) can run any
time after P4.

---

## P0. Scaffold and architecture review [Opus]

```
Read CLAUDE.md, TYPEWRIGHT_BUILD_BRIEF.md, UI_SPEC.md, docs/TYPEWRIGHT_HANDOFF.md,
docs/KNOWLEDGE.md and docs/RESEARCH_font_quality.md in full. Open ui/typewright-explorer.html
in a browser and click through every screen, including the Draw sheet (swipe the puck, hold
it, open the map) and Economy.

Scaffold a Kotlin Multiplatform project named typewright with Compose Multiplatform
targeting Android, JVM desktop (Linux) and Kotlin/Wasm, using the module layout in the
handoff §10 with the amendments in the brief §3 (compile behind a CompileBackend
interface; shape-preview by platform stack; qa/corpus; learn/scenes). Every module gets a
build file, a README of two sentences, and one placeholder test. Do not implement features.

Then write docs/ARCHITECTURE_REVIEW.md: for each module, the three riskiest technical
decisions, the FOSS dependency you would pick with its licence, and any place where the
brief's plan will not work on one of the three targets. Review specifically: the one-sheet
UI as a single transformed layer in Compose (performance, hit testing on the glass), the
puck's gesture arbitration against canvas gestures, TextRunShaper/Skiko shaping for Indic
conjunct previews, Chaquopy packaging size, and Kotlin/Wasm readiness for the canvas. Be
blunt. Flag anything in the brief that overclaims. Stop and report.
```

## P1. M0: quality gate, the corpus, the box plots, the pipeline [Sonnet]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §8 and §12, UI_SPEC.md §3 (box plot rows, report items),
docs/KNOWLEDGE.md sections A, C, E, F, and the Google Fonts requirements and QA sections of
docs/RESEARCH_font_quality.md. Open ui/typewright-explorer.html → Economy and Check.

Implement for JVM desktop first:
1. qa/corpus: load data/node-economy-latin.json; expose per-style, per-glyph quartiles and
   per-family counts; implement the fence from brief §8.2 and the three verdict words
   (outlier / above / in range). Port data/scripts/build_node_economy_corpus.py to a Gradle
   task or keep it as the generator; add the popularity ranking as a second data pack when
   a GOOGLE_FONTS_API_KEY is present at build time (never at runtime).
2. qa: bridge to fontspector or fontbakery (whichever installs cleanly) with the googlefonts
   profile; parse results into a data class per check with id, status and rationale text.
3. qa: Typewright's own checks over a UFO or TTF: node economy against the box; extrema on
   curve; collinear, short and jaggy segments using fontbakery's numbers (alignment miss 2
   units, short < 3 units or 0.6% of contour, collinear < 0.1 rad, jaggy < 0.25 rad,
   semi-vertical within 0.5°); off-curve ratio; overshoot present on round glyphs, with
   flat tops and bottoms exempt; anchors for every base and mark; name-table uniqueness;
   italic provenance (every glyph a pure shear of another font's glyphs); terminal-style
   consistency.
4. The Check report and the Economy view exactly as the explorer shows them: worst-first
   rows, log axis, open row with the 30 faces, style chips with n, legend, the fence
   sentence, and "What these checks do not measure".
5. The pipeline: OFL.txt with the copyright pattern, name table strings, version, STAT for
   statics, repo layout, DESCRIPTION.en_us.html scaffold, upstream.yaml, issue body, into a
   chosen folder. The Ship screen as the explorer shows it.

Test against fonts/HyleDeco-Regular.ttf: T must be an outlier against sans-geometric
(1,763 against a box of 8–9, max 28), o an outlier (80 against 10), H an outlier (1,252
against 12–13); the specimen must report the counts in brief §7. Report exact output.
```

## P1b. Style detector: features [Opus]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §8.4 and docs/RESEARCH_font_quality.md sections on anatomy
and classification vocabulary.

Design and implement in qa/corpus a feature extractor over a glyph set (o n a g e H T c s)
producing: contrast ratio, stress angle, serif presence and bracket, a and g storeys,
terminal style, aperture openness, o roundness (fitted superellipse exponent), x-height to
cap ratio, width class. Then a hand-written, explainable scorer mapping the feature vector
to ranked Google Fonts taxonomy classes with confidences. Validate on the corpus itself:
report how often the top class matches the family's tag class, per class. No ML in v1.
Write the feature definitions in plain language into learn/scenes as the Lineages
vocabulary.
```

## P2. M1: the fit algorithm [Opus]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §7 and docs/TYPEWRIGHT_HANDOFF.md §4 M1, plus the outline
quality and vectorisation section of docs/RESEARCH_font_quality.md in full.

Design and implement in core-geometry, pure Kotlin, exhaustively unit tested:
1. Corner detection on a dense polyline: curvature based with a tunable threshold.
2. Schneider cubic fitting between corners: chord-length parameterisation, least squares,
   Newton reparameterisation up to 5 iterations, split at max error, G1 at smooth joins.
3. Type constraints after fitting: insert on-curve points at extrema; snap tangents within
   0.5° of horizontal or vertical; snap points within 2 units of a supplied metric line
   except where overshoot is detected on a curved approach, in which case preserve it;
   flat approaches get no overshoot; round to integers; enforce path direction.
4. A construction classifier for the fitted contour: polygonal, elliptical,
   superelliptical, rounded-rectangle (brief §8.5).
5. A node-economy report: before and after counts, off-curve ratio.

Fixtures from brief §7: T 1,763 → 8; o 80 → 16+16 (rounded rect, r ≈ 118/74, flat top and
bottom, no overshoot); n 44 → 14+8; H 1,252 → 12; the T's stem foot 1 → 0; the T's
off-centre stem must survive untouched. A rasterised circle must fit to 8 on-curve + 16
off-curve within 1 unit. Report every count.
```

## P3. M1: capture and the trace chain on Android [Sonnet]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §7, UI_SPEC.md (layers, stage scrubber, box plot rows) and
docs/TYPEWRIGHT_HANDOFF.md §4 M1. Open ui/typewright-explorer.html → Capture and Trace.

Implement engine-trace capture and cleanup on Android with OpenCV: camera or gallery
import, perspective correction from the fiducials on the template sheets in
scripts/templates, per-cell detection, adaptive threshold, despeckle, hole fill, stroke
width estimate, sub-pixel contour extraction. Wire into the P2 fitter.

Build the Capture review grid and the Trace screen exactly as the explorer shows them: the
raster underneath, the eight stages as a scrubber (three visible by default, the rest
behind "adjust" — brief §7 [CONFIRM]), the count collapsing on a log scale, the glyph's box
plot in the header with the live point, per-stage parameters and one-sentence results,
Accept / Adjust / Hand edit. Use the Hyle Deco template sheets as the test input.
```

## P4. M2: the one sheet, rooms, the puck, the bloom [Sonnet]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §4, §5, §6 and UI_SPEC.md in full. Open
ui/typewright-explorer.html → Draw and use it for at least ten minutes on a touch device or
with a touch emulator: swipe the puck, tap it, hold it and spin, drag its grip, swipe the
header, open the map.

Implement the ui module's sheet: a single world layer holding rooms side by side on a
shared grid; a glass layer that never moves; the bloom as a counter-translated quiet layer
between grid and ink; room panning by header swipe, two-finger swipe, edge marks and
keyboard; the map (world scaled to 0.3, tap a room to fly); zoom as depth from glyph to
word to specimen to map. Implement the puck with its four gestures and the radial dial with
detents and haptics; the contextual radial on a hold over a node or contour; the inspector
row with ink-block selection and = expressions; finger, stylus and mouse handlers as three
separate input models over one geometry, none emulating another. Command palette on all
three. Reproduce the explorer's tokens; do not restyle.
```

## P5a. M2: construction geometry and spiro [Opus]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §10 and docs/TYPEWRIGHT_HANDOFF.md §4 M2.

Implement engine-construct: booleans (union, subtract, intersect, exclude) on closed cubic
contours; offset by distance; stroke to outline with cap and join styles; Spiro or Hobby
splines (pick one, document why) converted to cubics with node economy preserved; the
construction grammar with every primitive and entry method in the handoff, parametric
until baked; transformations with numeric entry; the oblique derivation as shear the
centreline then restroke, verifying stroke variation under 1 unit on a circle (KNOWLEDGE
B2). Exhaustive unit tests.
```

## P5b. M2: tools and layers [Sonnet]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §10 and UI_SPEC.md.

Wire P5a into the sheet: the primitives tool with entry methods as an unfolded list from
the puck; the Construct inspector (stem as a font-wide value, contrast, exponent, width;
"parametric · bake"); background and sketch layers; guides at any angle; kerning groups
and a .fea text editor in Space; the palette commands (add extremes, harmonise, tidy with
a live count, reverse, correct direction, round, knife, close/open). Keep the explorer's
look; add nothing it does not show without noting it in docs/OPEN_QUESTIONS.md.
```

## P6. M3: Learn — Lineages engine, overlay, lens, scrapbook [Sonnet]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §9, docs/LESSONS_SCAFFOLD.md in full, UI_SPEC.md (Lineages),
and the anatomy and critique sections of docs/RESEARCH_font_quality.md. Open
ui/typewright-explorer.html → Learn.

Implement learn/scenes: the YAML scene format, a renderer that crossfades sample letters at
matched x-height, the stress dial, captions, tags, callouts, the identify-it exercise, and
strand sequencing. Faces are fetched from the google/fonts repository at build time and
subset for each scene's glyphs; record licences in THIRD_PARTY.md. Ship the ten Lineages
scenes, the vocabulary scene, the exercise bank, and at least the A1, B1, B2 and C1 Craft
scenes from the scaffold, all marked SCAFFOLD in the UI. Then the comparison overlay
(layers with colour + line pattern, cap-height or x-height alignment, redline, stroke
probe), the anatomy lens with definitions, and the scrapbook and moodboard as files in the
project with a manifest.
```

## P7. M4: the workbook in the margin [Sonnet]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §4.3 [CONFIRM], §9 and docs/TYPEWRIGHT_HANDOFF.md §4 M5,
plus the pedagogy and proofing sections of docs/RESEARCH_font_quality.md.

Implement the campaign engine as data: tasks are YAML with why, demonstration, task, gate
and reflection. Gates call qa and the boxes. Implement the twelve-task Latin workbook with
the control strings and proofing sequences from the research. The workbook lives in the
margin above each room (pull down) and as a one-line next-task on the specimen; the
demonstration is a scene from learn/scenes. Mark the whole workbook SCAFFOLD until the
designer's lessons replace it.
```

## P8. M5: scripts [Sonnet]

```
Read docs/TYPEWRIGHT_HANDOFF.md §4 M8 and the multi-script section of
docs/RESEARCH_font_quality.md in full, and docs/LESSONS_SCAFFOLD.md §5.

Implement scripts/ as data: per-script metric system, glyph inventory with correct names
(-deva; -ar with .init .medi .fina; kana), control characters, template sheets from
scripts/templates, feature generation in the documented order for Indic and the joining
features for Arabic. Shaping preview through TextRunShaper on Android and Skiko on desktop;
fall back to HarfBuzz bindings only where cluster data is missing, and say so in
docs/OPEN_QUESTIONS.md. Ship Hiragana and Katakana first, then Devanagari. Extend the
node-economy corpus builder to Devanagari and kana faces from the repository.
```

## P9. Web target and the hosted build endpoint [Sonnet]

```
Read TYPEWRIGHT_BUILD_BRIEF.md §3 and CLAUDE.md law 3.

Bring the ui and core modules up on Kotlin/Wasm; shaping through the browser text engine
via FontFace; the compile step through a small hosted endpoint (fontmake in a container)
that the Ship room names every time it is used, with the user's project sent only on an
explicit build. Document the endpoint's contract and its zero-retention policy. Measure
canvas frame times on a mid-range phone browser and report them.
```
