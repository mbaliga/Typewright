# Typewright: build brief

Version 2 · 23 September 2026 · supersedes `docs/TYPEWRIGHT_HANDOFF.md` wherever the two
differ. The handoff remains the reference for the trace chain, construction geometry,
multi-script plan, data model and Google Fonts pipeline; this brief adds the decisions made
in the 22–23 September design sessions, the UI that was designed live in
`ui/typewright-explorer.html`, the node-economy corpus, and the learn strands.

Status markers: [CANON] decided · [CONFIRM] needs Madhav · [SCAFFOLD] placeholder content
that ships marked as such until replaced.

Read order for a build agent: this file → `UI_SPEC.md` → `docs/KNOWLEDGE.md` →
`docs/RESEARCH_font_quality.md` → the relevant prompt in `PROMPTS_CLAUDE_CODE.md`.
Open `ui/typewright-explorer.html` in a browser before touching any UI code.

---

## 1. What this is

An app that takes hand-made letterforms — a scan, a photo, a drawing on the device — and
turns them into a font of Google Fonts submission quality, while teaching the user what
quality means. It covers the scripts the incumbents ignore (Devanagari and the other Indic
scripts, South East Asian scripts, Arabic, kana). Phone first, for a finger, a stylus, or
mouse and keyboard; the same app on Linux and the web.

Working name Typewright, pending registry clearance. Studio: A System of Cells. Icon: an
isometric Doric capital whose profile reads as a serifed T.

## 2. Principles [CANON]

The seven from the handoff stand (drawing is the source of truth; node economy is a
first-class metric; quality is taught; complex scripts are first class; minimal utilitarian
UI with monochrome ink; three input models over one geometry; plain files). Four are added:

8. **One sheet.** The whole app is one plane. Rooms are regions of a single sheet; the
   sheet moves, the glass does not. Nothing slides over anything. The letter lives on the
   paper; UI lives on the glass.
9. **Measured, not invented.** Every number the app judges a user by is a distribution
   measured from professional fonts, shown as a box-and-whisker with the user's glyph as a
   point. Where the app has no measurement it says so.
10. **Selection is ink.** A selected thing is a block of ink with paper-coloured text.
    Everything unselected is plain ink text. No outlines, no boxes, no colour for state.
11. **Lessons are live geometry.** No video. A lesson is a scene the canvas renders from
    data, so every example is a real font the learner can stop and touch.

## 3. Platforms and stack [CANON]

v1 targets: **Android, Linux (JVM desktop), Web (Kotlin/Wasm)**. Later: Windows and macOS
(the same JVM desktop target; no new UI work), iOS/iPadOS (Compose Multiplatform iOS; see
the compile note).

Kotlin Multiplatform with Compose Multiplatform. One UI tree, one geometry core. Module
layout per handoff §10, with these amendments:

- `compile` is defined behind a `CompileBackend` interface from day one. v1 backend:
  fontmake + fontTools in a Python runtime (Chaquopy on Android, system Python on Linux;
  Chaquopy is free of charge for all use since v14 [CONFIRM current licence text]). Web v1:
  a hosted build endpoint; the UI says so. Planned v2 backend: **fontc** (Rust, Apache-2.0)
  as a native library, which is what makes iOS possible and web offline. Do not let any
  caller depend on Python being present.
- `shape-preview` is HarfBuzz by platform stack, not by JNI, in v1: Android 12+
  `android.graphics.text.TextRunShaper` yields positioned glyphs shaped by HarfBuzz;
  desktop uses Skiko's shaper (Skia's SkShaper, HarfBuzz-backed); web uses the browser
  text engine through `FontFace`. Direct HarfBuzz bindings only if a script's preview needs
  cluster data the platform API withholds. "Shaping is HarfBuzz" stays canon; the binding
  work leaves the critical path.
- `qa` gains a `corpus` sub-module: the node-economy reference data (§8) and the style
  detector (§8.4).
- `learn` gains a `scenes` sub-module: the scene renderer and the lesson data (§9).
- `ui` follows `UI_SPEC.md` and reproduces `ui/typewright-explorer.html`. The explorer
  is the source of truth for look and interaction; native code must not quietly diverge
  from it. Where the explorer and this brief disagree, this brief wins and the explorer is
  updated.

Rejected again: Flutter; Rust core plus per-platform native UI. Reason unchanged.

## 4. Information architecture: one sheet [CANON]

### 4.1 Rooms

The pipeline is a row of rooms on one sheet, in order:

**Capture · Trace · Draw · Space · Learn · Check · Ship**

Each room owns its region of the sheet. Moving between rooms pans the camera; the grid,
metric lines, letters and proofs travel together as one plane. The glass (header, puck,
inspector, edge marks) never moves.

Room switching: horizontal swipe on the header block, a two-finger horizontal swipe
anywhere, the edge marks, keyboard ← →, and the map. A one-finger horizontal drag on the
canvas never switches rooms: that gesture belongs to the letter.

### 4.2 Zoom is depth

Pinch out from a glyph → the word proof → the specimen (the project's home) → the map of
rooms. Pinch in reverses. On desktop: scroll-zoom and the `M` key for the map. This is the
one navigation model that works identically for finger, stylus and trackpad, and it is why
there are only seven rooms: sub-modes are zoom levels, not more rooms.

### 4.3 Margin and proof [CONFIRM]

Each room has a **margin** above it and a **proof** below it on the same sheet. Pull down
to see the margin: the workbook task for this room (why · demonstration · task · gate ·
reflection). Pull up to see the proof: the rendered result of this room (a control string,
a paragraph, a specimen). Horizontal = pipeline stage, vertical = context, zoom = detail.
Proposed; the explorer does not yet show the vertical axis.

### 4.4 The specimen is home

A project opens on the font as it is right now, rendered as a proof — never on a file list.
Point counts, gate status and the next task are marginalia around the letters. Economy
(§8) is a view inside Check, reached from the specimen's counts or from the Check report.

### 4.5 Modal states

With nothing layered, there is nowhere for a dialog to go. Modal states are ink blocks on
the glass and there are very few of them: destructive confirmations, and the licence
attribution audit on import. Everything else is a room, a margin, a proof, or an inspector
value.

## 5. Interaction [CANON]

### 5.1 The tool puck

A pinned circle on the glass, the one round object in the app (it is an instrument; the
rest is square). It shows the current tool and its name.

- **Swipe vertically** on the puck: cycle tools, one per 34 dp of travel, with a haptic tick
  (6 ms) and a roll animation of the icon.
- **Tap**: unfold the collapsible tool list next to the puck (icon, name, shortcut letter);
  tap a tool to select and collapse.
- **Hold (380 ms)**: open the radial dial around the puck. The ring rotates with the
  finger; a fixed marker at 12 o'clock selects; each detent ticks (5 ms). Release commits
  (10 ms). Release inside the hub cancels.
- **Drag the grip** (the short bar at the top of the puck): reposition; on release the
  puck pins to the nearest edge.
- Desktop: the puck also shows the shortcut letter and responds to the letter keys.
  Shortcuts follow Glyphs and RoboFont where sensible: V select, P pen, S primitives,
  B boolean, K stroke, M measure, A anchors, T metrics.

The puck holds **tools only**. Contextual actions on an object (smooth ↔ corner, delete,
add extremes here, split, reverse) are a hold on the object itself: the same radial,
different contents, centred on the object. Do not put actions into the tool puck.

### 5.2 Three hands over one geometry (handoff M3 stands)

Finger: large targets; two-finger pan and zoom always live; hold grabs a node and drags it
with a fixed offset so the finger never hides the point; a loupe above the finger shows
what the fingertip covers; precision comes from zoom. Stylus: pressure maps to nothing in
vector mode; hover shows snap candidates before the touch commits; barrel button constrains;
palm rejection on. Mouse and keyboard: modifiers constrain and snap, arrows nudge 1 unit and
shift 10, numeric entry for every value, `=` expressions (`=xheight`, `=o.rsb+4`), command
palette on Ctrl/⌘ K.

### 5.3 The bloom

Between the grid and the ink there is a quiet layer: soft paper-coloured zones under every
piece of glass (header, puck, inspector, edge marks, radial). Grid lines, metric lines and
paper grain dissolve as they approach the glass; letters and nodes never fade. This is what
lets the glass exist without a single box or rule. Implementation: a layer in the sheet's
coordinate space counter-translated so it stays fixed to the glass; radial gradients from
canvas colour to transparent; z-order grid < bloom < ink < glass.

### 5.4 Selection language

Selected: ink block, paper text. Unselected: plain ink text. This applies to tools, rooms,
inspector fields (the field being edited or snapped), stages, style chips, lesson eras,
and the outlier counts on the specimen. Meaning colours remain for live geometry only:
violet = selected node or contour, cyan = the thing you are snapping to. Never red or green
as meaning. Severity is a shape and a word (● fail · ◐ warn · ✓ pass · ○ info · dotted
circle = not started).

### 5.5 Gestures, summarised

| intent | finger | stylus | mouse + keys |
|---|---|---|---|
| pan / zoom canvas | two fingers | two fingers | scroll, ctrl-scroll |
| grab a node | hold, drag with offset + loupe | touch, drag | click, drag |
| snap preview | during drag (cyan) | on hover (cyan) | on hover (cyan) |
| change tool | swipe puck / tap puck / hold puck | same | letter keys, puck |
| object actions | hold object → radial | hold object → radial | right-click → radial |
| switch room | header swipe, two-finger swipe, edge marks | same | ← →, edge marks |
| map | tap room name, pinch out past specimen | same | M |
| margin / proof | pull down / pull up | same | ↑ ↓ |
| nudge | inspector ±1 / ±10 | same | arrows, shift-arrows |
| undo | two-finger tap | barrel double-click | Ctrl/⌘ Z |

## 6. Visual language [CANON]

Minimal and brutalist: hard edges, one weight of paper, ink blocks, no shadows, no blur, no
translucency, no rounded corners except the puck. Separation by whitespace and by the bloom.

- **Type**: one neutral sans for sentences (system UI stack; bundle Inter (OFL) for
  cross-platform identity [CONFIRM]); a monospace for labels, numbers and chips at 9.5–11 sp,
  uppercase, tracked 0.14 em; tabular numerals everywhere digits align. The user's letters
  are the only display type on screen.
- **Textures**: paper (warm, grained), vellum (warmer, more grain), blueprint (deep blue,
  white ink, fine grid), black (AMOLED), white, and user import. Chrome follows the texture:
  light chrome on light canvases, dark on dark. Default: paper [CONFIRM].
- **Meaning colours** with a light-ground and a dark-ground value each: violet #5F4BE0 /
  #8E7BFF, cyan #0A9D8E / #08FED5. Comparison layers add amber #B57A00 / #FFB300 and
  magenta #B8248F / #FF5FD2, always paired with a line pattern (solid, dashed, dotted,
  dash-dot). Provenance of cloud versus device follows the constellation rule.
- **Motion**: room pan 600 ms, cubic-bezier(.2,.8,.2,1); puck pin 350 ms; count collapse
  1.4 s log-eased; radial open 150 ms; nothing bounces. Reduced-motion honoured.
- **Sound**: none by default. Optional ambient room tone per room, off by default, for
  staying in flow. No UI sounds. [SCAFFOLD: tones not yet sourced]
- **Haptics**: Android only in v1; the puck's ticks, radial detents and commits; never for
  state changes the user did not cause.

Full tokens, sizes and component states: `UI_SPEC.md`.

## 7. The trace chain (handoff M1 stands) with amendments

- The eight stages are a **scrubber**: a hairline under the stage names; dragging along it
  moves through the stages continuously and the count and drawing follow the finger. The
  count in the header animates on a log scale (1,763 → 8 is the product's thesis).
- Default view shows three stages — Clean · Fit · Review — with the other five behind
  "adjust". The workbook introduces them one at a time. [CONFIRM: the explorer shows all
  eight]
- The header carries the glyph's box plot (§8) with the live count as the point.
- Corner detection, fit tolerance and the snap band are the only parameters visible by
  default. Regularise is off by default and explains itself when on.
- A flat-topped round glyph gets no overshoot and the app says so ("flat top and bottom:
  overshoot does not apply"). Overshoot is a property of curvature at the line, not of the
  letter.

Fixtures (regression tests, from the shipped Hyle Deco Regular, `fonts/`; per-glyph rows
independently re-verified 2026-09-24, P0c, against `fonts/HyleDeco-Regular.ttf`'s `glyf` table
directly — see docs/ARCHITECTURE_REVIEW.md section 5 items 13–22):

| glyph | shipped | fitted | note |
|---|---|---|---|
| T | 1,763 on · 0 off | 8 on · 0 off | stem foot y = 1 → 0 on snap; stem is off-centre by design (x 258–301 in a 12–442 bar): symmetry must not "fix" it |
| o | 80 on · 0 off | 16 on · 16 off | rounded rectangle, r ≈ 118 outer / 74 inner; flat top and bottom |
| n | 44 on · 0 off | 14 on · 8 off | arch top on x-height 500 |
| H | 1,252 on · 0 off | 12 on · 0 off | stems 43 and 45 units; monolinear within 2 units |
| whole font | 68,941 on · 0 off · 338 glyphs | — | every curve is a polygon; corrected 2026-09-24 from 25,991 — that older figure was itself miscounted the same way as the corpus script's bug 2 (composite glyphs, i.e. every accented letter, counted as zero instead of being decomposed): 161 of the font's 338 glyphs are composites, and decomposing them recursively (verified against fontTools' own `Glyph.getCoordinates`) adds 42,950 on-curve points that the old figure dropped |

## 8. Node economy: the box, not the threshold [CANON]

### 8.1 The corpus

For each style class in the Google Fonts taxonomy (`data/families.csv`, refreshed from
`google/fonts/tags/all/families.csv` at a pinned commit each regeneration — `tags/` has no
stated licence in google/fonts, an open question, not a blocker: docs/OPEN_QUESTIONS.md item
4/20), take families whose tag score is ≥ 50, rank them by `/Quality/Drawing` score (tie-break:
tag score, then reverse-alphabetical family name), keep one face per superfamily (first word
of the family name — documented, not changed, in the script), take the top 30, download the
Regular face from the google/fonts repository, count points per glyph with fontTools, and
store min, Q1, median, Q3, max per glyph plus the per-family counts.
`data/scripts/build_node_economy_corpus.py` does this and `data/scripts/build_compact_corpus.py`
derives the compact pack from its output (added 2026-09-24, P0c: no generator for the compact
pack existed before, which broke the "never hand-edit generated data" rule).
`data/node-economy-latin.json` is its output for ten classes, regenerated 2026-09-24 (P0c, fixing
the bugs in items 13–15 of docs/ARCHITECTURE_REVIEW.md section 5) and pinned to google/fonts
commit `b5efa9c32e8f9b63005f5cdb1ad5527a77d2cd04` (see the pack's own `source` field for the
exact fetch method):

sans-geometric 30 · sans-grotesque 30 · sans-neogrotesque 30 · sans-humanist 30 ·
serif-garalde 30 · serif-transitional 30 · serif-didone 15 · slab 30 · display-artdeco 8 ·
blackletter 15. The UI always shows n. serif-didone and blackletter come in under 30 because
their candidate pools (families tagged with a ≥ 50 score, one per superfamily, with Latin
letter coverage) genuinely run out that early, not from a bug — blackletter's pool included
three Khmer-script families (Content, Khmer, Siemreap) that cover the 10 shared digits but zero
Latin letters and are now correctly dropped rather than counted with an empty box.

Ranking is a **toggle**: Google's `/Quality/Drawing` score (default, what the shipped data
uses) or popularity from the Google Fonts Developer API (`sort=popularity`, needs an API key
at corpus-build time, never at runtime). Both rankings ship in the data pack when available.

Counting rules: on-curve equivalents = points flagged on-curve in the `glyf` table plus one
implied on-curve point for every pair of cyclically-consecutive off-curve points in a contour
(the general TrueType rule — an all-off-curve contour is just the case where the whole contour
is one such run); off-curve counts are reported with their format (quadratic for repository
TTFs). Composite glyphs (component references, e.g. every accented letter) are decomposed with
their transforms, recursively, before counting — they are never zero. A family with no Latin
letter among the requested glyphs is dropped from the class outright, not kept with an empty
box; a genuinely contourless glyph (rare) is skipped for that one glyph only, and n drops
accordingly only for that glyph's box. On-curve counts are comparable across cubic sources and
TrueType binaries only after the *same* overlap removal cu2qu's source and the compiled binary
both went through — this does not hold as stated for the corpus faces that are variable fonts
and keep their overlaps, and it breaks under `--drop-implied-oncurves`
(docs/ARCHITECTURE_REVIEW.md section 5 item 15).

### 8.2 The fence

A glyph is an **outlier** when its count exceeds Q3 + max(1.5 × IQR, 0.25 × Q3): Tukey's
fence with a minimum slack of a quarter, because several boxes have zero IQR (the geometric
T is 8–9). Above Q3 but inside the fence is **above**. The rest is **in range**. The words
"outlier / above / in range" are the UI's words; never "fail / warn / pass" for this check.

### 8.3 Presentation

Horizontal box-and-whisker per glyph on a log axis (3 → 3,000; ticks at powers of two).
Whiskers: full range. Box: middle half. Median line. The user's glyph: a violet point with
a paper ring. Sorted worst-first by default (log distance above Q3); alphabetical as the
alternative. Tapping a row opens it to show the 30 faces as points, with the fewest and the
most named. The same box, shrunk, sits in the trace header and inside the Check report.
Legend always visible: "middle half of the 30 faces · your glyph · whiskers: full range ·
log scale". A footer states the fence in one sentence.

### 8.4 Style identification

Three layers: the user **declares** the intent in the project brief (Task 1: serif or sans,
text or display, and the closest class); the app **infers** a class from measurable
features and shows its evidence; the user **confirms**. Features, all measurable from
outlines: contrast ratio (thin/thick stroke), stress angle (direction of thickening in o),
serif presence and bracket, a and g storeys, terminal style (flat/round/angled),
aperture openness (c e s), o roundness (superellipse exponent), x-height to cap ratio,
width class. Output: ranked classes with confidence, e.g. "geometric sans 0.74 · art deco
0.58". The inference uses the same feature vocabulary the Lineages lesson teaches, so
identifying your own font is the exam. Classifier: a small hand-written scorer over the
feature vector in v1 (no ML), reviewable and explainable.

Note on vocabulary: "gothic" means sans in American usage (Franklin Gothic) and blackletter
in European usage. The app uses the Google Fonts taxonomy names and teaches the ambiguity.

### 8.5 Per-construction thresholds

The research's "an o is 8 on-curve + 16 off" describes an elliptical o. Hyle Deco's o is a
rounded rectangle whose honest fit is 16 + 16. The gate compares against the style box,
which already reflects construction; but any per-glyph target the workbook quotes must be
classified by construction (elliptical, superelliptical, rounded-rect, polygonal) before it
judges count.

## 9. Learn [CANON structure · SCAFFOLD content]

Five strands, all delivered as scenes rendered live by the canvas (`docs/LESSONS_SCAFFOLD.md`
has the scene schema and the scaffold content):

1. **Lineages** — classification and history. Ten eras, ten real OFL faces, one scrubber:
   the letters crossfade in place at matched x-height, the stress dial turns, the caption
   names the tool that made the shape, feature tags appear; ends in identify-it exercises on
   faces the learner has not seen.
2. **Anatomy** — the lens: labels on the user's own letter, definitions one tap away.
3. **Craft** — `docs/KNOWLEDGE.md` as before/after scenes: the T collapsing from 1,763 to 8,
   counters closing in an inflated bold, the stroke wobble of a sheared italic measured
   live, anchors placing a mark on every base at once.
4. **Scripts** — the metric systems as scenes: Devanagari's headline, Arabic's joining line
   with weight, kana's virtual body and letter face.
5. **Reading** — how to proof and critique: control strings, the sandwich, the paragraph,
   the paired proof, the focused crit question.

Reference faces for lessons come from the same corpus as the boxes (OFL/Apache, fetched at
build time, subset for the lesson's glyphs). Exemplars used in the explorer: EB Garamond,
Libre Baskerville, Playfair Display, Zilla Slab, Work Sans, Limelight, Jost, Source Sans 3,
Inter, UnifrakturMaguntia.

Instruments the strands hand the learner: the comparison overlay (layers with colour +
line pattern, aligned at cap height or x-height, redline mode, stroke probe), the anatomy
lens, the scrapbook and moodboard (files in the project). Madhav's Domestika material
augments and steers Craft and the workbook; the scaffold ships marked SCAFFOLD until then.

Content licensing: history text is written for the app or adapted from CC BY-SA sources
(Wikipedia; Google Fonts Knowledge [CONFIRM licence]); Design With FontForge material under
its CC licence with attribution. No reproduction of non-free text.

## 10. Editing tools: what "sophisticated enough" means [CANON tiers]

The handoff's M2/M3 set is adequate for tracing and fixing. For "a font from nothing" at a
professional standard, add, in priority order:

**v1 must have**
1. Spiro / Hobby curves: draw a few points, get smooth curves with no handles; produces
   minimal nodes by construction. The most important addition for a finger.
2. Transformations with numeric entry: scale, rotate, skew, mirror about a point or axis;
   align and distribute.
3. Layers: background (scan or reference), sketch, and the master layer.
4. Kerning groups (`public.kern1/2` in UFO) and a `.fea` text escape hatch.
5. Guides at any angle, local and global, snappable.
6. Palette commands: add extremes, harmonise curvature, tidy/simplify with a live count,
   reverse contour, correct direction, round coordinates, cut/knife, close/open contour.

**v1.5**
7. Nib tools on stylus: broad nib (angle + width) and pointed pen (pressure) producing
   stroke-to-outline shapes; ties Lineages to Draw (the same nib that made the Garalde).
8. Components with edit-in-place; nested components; anchors-driven composites (from
   handoff).

**v2**
9. Interpolation between two drawn masters; designspace; compatibility check. The UFO +
   designspace data model must allow it from day one.

Never: manual hinting. The Google Fonts pipeline autohints.

## 11. Data model (handoff §5 stands) with additions

Project = one directory: one UFO 3 per master; `typewright.json` (settings, campaign
progress, declared and confirmed style); `scrapbook/` with a manifest; `comparisons/`;
`build/`; `lessons/` (reflections saved from the workbook). App-level, not per project:
`corpus/` (node-economy data packs by script) and `scenes/` (lesson data). Plain files,
git friendly, openable elsewhere.

## 12. Quality gate and pipeline (handoff M7 stands)

Layer one: Fontbakery or Fontspector, googlefonts profile, results translated to plain
language with the rationale. Layer two: Typewright's own checks, now with node economy as
the box (§8) plus extrema, tangent continuity, stroke consistency, overshoot presence,
anchors, composite completeness, spacing sanity, source provenance, italic provenance,
name-table uniqueness, licence and attribution audit on import, terminal-style
consistency, batch-transform preview. The report ends with "What these checks do not
measure". Pipeline and repo layout unchanged. The Ship room is a checklist that produces a
repository, blocked while Check has fails.

## 13. Milestones

Each ships something usable. Stop and reassess after each.

- **M0 Checklist + Economy.** Quality gate, the corpus and box plots, the GF pipeline, on a
  TTF or UFO the user brings. Desktop first. Would have saved Hyle Deco a month.
- **M1 Latin trace.** Capture, the chain with the scrubber, review, hand edit, UFO out,
  compile. Where Typewright beats Calligraphr.
- **M2 Draw.** One sheet, rooms, puck, bloom; construction geometry; spiro; transformations;
  layers; guides; the three hands.
- **M3 Learn.** Lineages engine and scaffold content, overlay, lens, scrapbook; Craft
  scenes.
- **M4 Workbook.** The margin, the twelve Latin tasks, gates on the boxes, reflections to
  the scrapbook.
- **M5 Scripts.** Kana, then Devanagari with shaping preview, then generalise.
- **M6 Derive.** Oblique by skeleton shear and restroke; bold by offset from a clean
  skeleton with counters re-solved; masters and interpolation.
- **M-web.** Kotlin/Wasm target and the hosted build endpoint; can run alongside M1–M2.

## 14. Model and tool routing

Opus in Claude Code: P0 architecture review, the fit algorithm (P2), construction geometry
and spiro (P5-geometry), the style detector's feature design. Sonnet: everything else,
including all UI work, which reproduces the explorer rather than inventing. The chat app
with artifacts: UI iteration; the explorer is republished there and copied into `ui/`.

## 15. Open items [CONFIRM]

1. Licence: handoff §9 recommends AGPL-3 with a hosted build service as the paid product
   and grants pursued early. Undecided; no LICENSE file until decided.
2. Name clearance for Typewright.
3. Margin and proof as the vertical axis (§4.3).
4. Three stages by default versus eight (§7).
5. Default texture (§6).
6. Inter as the bundled UI face.
7. Chaquopy licence text; Google Fonts Knowledge text licence.
8. Whether `ARTICLE.en_us.html` or `DESCRIPTION.en_us.html` is canonical for submissions in
   2026 (handoff research).
9. The Domestika material for Craft and the workbook.

## 16. What Madhav owes the build

The Domestika lessons; name and licence decisions; the Hyle Deco redraw as the first font
through the whole pipeline and the first workbook playthrough; a Google Fonts API key if the
popularity ranking is to be built.
