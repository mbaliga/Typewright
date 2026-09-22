> Original handoff (22 Sept 2026). Superseded where it differs by `../TYPEWRIGHT_BUILD_BRIEF.md` (platforms, UI, node economy, learn strands, tools). Still canon for the trace chain, construction geometry, multi-script plan, data model and pipeline.

# Typewright: build handoff

Working name Typewright, pending registry clearance (see the name-clearance report).
Icon: isometric Doric capital whose profile reads as a serifed T. Studio: A System of
Cells. Targets: Android, Linux, Web. Licence: section 9, decision pending.

Status markers: [CANON] decided, [CONFIRM] needs Madhav, [SCAFFOLD] placeholder.

## 1. What this is

An app that takes hand made letterforms, from a scan, a photo, or drawing on the device,
and turns them into a font of Google Fonts submission quality, while teaching the user
what quality means. The incumbents make a font. Typewright makes a good font and shows
you the difference. It covers scripts the incumbents ignore: Devanagari and the other
Indic scripts, South East Asian scripts, Arabic, kana. It is designed phone first, for a
finger, a stylus, or mouse and keyboard, and runs the same on Linux and the web.

## 2. Principles [CANON]

1. The user's drawing is the source of truth. Nothing regenerates their outlines. Every
   transform is reversible and shows what it changed.
2. Node economy is a first class metric, displayed everywhere, never hidden. A T is 8
   points. The app says so.
3. Quality is taught, not just enforced. Every check explains why it exists and what a
   reviewer would say.
4. Complex scripts are first class, not a plugin. Shaping is HarfBuzz. Glyph naming and
   feature generation follow the documented OpenType script models.
5. Minimal, utilitarian UI. Glyph ink is monochrome. Colour is for meaning only, and only
   high contrast, never red or green as meaning. Backgrounds and textures are the user's;
   the letter is sacred.
6. Phone first, not phone only. Touch, stylus, and mouse plus keyboard are three
   interaction models over one geometry, each designed, none emulated.
7. Plain files. The project is a directory of UFO and JSON. Openable in FontForge, Glyphs,
   RoboFont at any time. No lock in, ever.

## 3. Platform and stack [CONFIRM, proposed]

Kotlin Multiplatform with Compose Multiplatform. Android native, Linux via JVM desktop,
Web via Kotlin/Wasm. One UI tree, one geometry core.

- `core-geometry`: pure Kotlin, no platform deps. Bezier maths, booleans, curve fitting,
  metrics. Identical on all targets. Exhaustively unit tested; this is where correctness
  lives.
- `core-font`: pure Kotlin UFO 3 reader and writer, glyph naming, feature file
  generation, anchors, kerning.
- `engine-trace`: raster to vector chain (M1). Kotlin core; Rust/Wasm accelerator later
  only if profiling demands it.
- `engine-construct`: construction grammar and booleans (M2).
- `compile`: font compilation. Android and Linux call bundled fontmake and fontTools via
  a Python runtime (Chaquopy on Android, system Python on Linux). Web calls a hosted build
  endpoint in v1, with a Wasm fontc port as the v2 goal. This is the one place web is not
  fully offline in v1; the UI says so.
- `shape-preview`: HarfBuzz via JNI on Android and JVM, harfbuzzjs on web. Renders the
  user's font with real shaping so conjuncts and joins are seen correctly during design.
- `qa`: Fontbakery in the same Python runtime, plus Typewright's own checks that
  Fontbakery lacks (section 4, M7).

Alternatives considered and rejected: Flutter (weaker Linux desktop, thinner Dart
geometry ecosystem); Rust core plus per platform native UI (best performance, triple the
UI work, slowest to ship). KMP wins on one codebase for three targets and on prior KMP
experience from the haptics workbench.

## 4. Modules

### M1. Trace and Fit. This is the product. [CANON]

The chain. Every stage is inspectable, adjustable, and shows the raster underneath.

1. Capture. Camera, gallery import, or draw in app. Perspective correction on photos of
   template sheets using printed fiducials. Per glyph cell detection from the template
   grid, so a full sheet auto assigns glyphs and the user corrects misreads in a review
   grid. Bulk is a first class path, not an afterthought.
2. Clean. Adaptive threshold, despeckle, hole fill, stroke width estimate. The mask is
   shown, not hidden.
3. Contour. Sub pixel boundary extraction. Still a polygon at this point.
4. Corner detect. True corners versus curvature, tunable with live preview. Corners
   become on curve points with no smoothing across them.
5. Fit. Between corners, the fewest cubic Bezier segments that hold the shape within a
   tolerance. Schneider's algorithm as the base, tangent continuity enforced at smooth
   joins. This is the node economy step. Before and after counts are shown.
6. Snap. Near horizontal and near vertical tangents snapped exact. Extrema forced onto
   on curve points, which both TrueType and PostScript conventions require. Points near
   the script's metric lines snapped, with overshoot on round shapes preserved, never
   flattened.
7. Regularise. Optional stroke weight normalisation for monolinear designs. Optional
   per glyph symmetry. Both off by default, both explained.
8. Review. Node count, smoothness score, diff overlay against the raster. Accept, adjust
   a stage, or hand edit.

Rule: the 1,763 node T never happens because step 5 exists and step 8 shows the count.

Borrow: OpenCV for capture and cleanup on Android and Linux; VTracer (MIT) for initial
contour if it beats our own. Build: steps 4 through 8 entirely.

### M2. Construction geometry [CANON]

Booleans on closed contours: union, subtract, intersect, exclude. Offset, inflate or
deflate by a distance, which is how a bold is derived from a clean skeleton. Stroke to
outline: a centreline with width, cap style and join style becomes a filled shape,
which is how a monolinear letter is drawn in one move and how an oblique is made
correctly (shear the centreline, restroke, weight stays constant; see KNOWLEDGE.md).

Construction grammar, the AutoCAD idea applied to letters. Each primitive has several
entry methods; the user picks the one matching what they already know:

- Line: two points; point, angle, length; point, tangent to a curve.
- Arc: three points; start, centre, end; start, end, radius; start, end, bulge;
  tangent, tangent, radius (the fillet); centre, radius, start angle, sweep.
- Circle, ellipse, superellipse: centre radius; three points; tangent tangent radius;
  fit to points.
- Rectangle, rounded rectangle: two corners; centre plus size; per corner radius.
- Stem: a rectangle whose width is the font's stem value, grid snapped.
- Bowl: a superellipse with a stroke and a contrast axis; the o primitive.
- Serif and terminal primitives, once the font's serif style is set, derived from font
  wide parameters so every serif matches.

Every construction stays parametric until baked. Change the stem value and every stem
updates. This is what makes a consistent alphabet possible for a non expert.

Borrow: a robust polygon boolean (Clipper2 has a Kotlin path), or Bezier level booleans
via a sweep. Build: the grammar and its UI. Nobody has this.

### M3. Input modality [CANON]

Three models over one geometry, designed separately.

Finger. Large targets. Two finger pan and zoom always live. Long press grabs a node,
drag with an offset so the finger does not hide the point, a floating loupe at the touch.
Precision comes from zoom, not fine motor control.

Stylus. Pressure maps to nothing in vector mode; it is a pen, not a brush. Optional
pressure to width in sketch mode only. Tilt ignored. Palm rejection on. Hover shows snap
candidates before the touch commits. Barrel button toggles constrain.

Mouse and keyboard. The full professional model. Modifiers constrain and snap. Arrows
nudge one unit, shift ten. Numeric entry for every parameter. A command palette.
Shortcuts follow Glyphs and RoboFont conventions where sensible so a pro is not fighting
muscle memory.

Shared. Snap to metrics, points, tangent extensions, grid. An inspector with exact
coordinates that accepts typed values. Unlimited undo per glyph.

### M4. Learn: scrapbook, moodboard, comparison, anatomy [CANON]

Scrapbook plus moodboard per project. Photos of signage, screenshots of type, scans of
old books, sketches, notes. Tag, pin the ones driving the design. This is the reference
wall every type designer keeps, made part of the project file so it travels with the
font.

Comparison overlay. Type a word or pick an alphabet. Choose fonts from the device, from
Google Fonts, or from the project. Render them overlaid at matched cap height or x
height, each in one high contrast colour from the fixed palette, with a redline mode
that draws only the outline difference. Toggle layers, slide alignment, show metric
lines. Per glyph mode overlays one letter from many fonts. A stroke contrast probe shows
thick and thin. Put Helvetica over Arial over the user's own sans and invisible
differences become obvious. This is how a beginner learns to see.

Annotations. Draw on the overlay, save to the scrapbook.

Anatomy lens. Tap any glyph and it labels the parts: stem, bowl, counter, aperture,
terminal, spur, ear, crossbar, serif type. Vocabulary is what makes critique possible.

### M5. Campaign, the workbook [SCAFFOLD, awaiting the Domestika lessons]

A guided sequence of tasks. Complete all of them and the output is a GF class font. The
sequence below is the standard method for building a typeface from an existing font as
reference. Madhav's Domestika lessons slot into and override it. [CONFIRM]

1. Choose your reference. A font you admire. Understand why. Scrapbook it.
2. Read it. Anatomy lens and comparison overlay on your reference against two
   neighbours. Write three sentences on what makes it itself.
3. Set metrics. Cap height, x height, ascender, descender, stem, overshoot. Each
   explained, effect shown live on a test word.
4. Control characters. n o H O first. They set proportion, stroke, contrast, and the
   round versus straight relationship for everything after. Gate: node economy and
   consistency pass before continuing.
5. Derive the family. b d p q from o and the stem. h m u from n. E F L T from H. C G Q
   from O. The app shows the derivation; the user copies then adjusts.
6. The hard letters. a e g s, the diagonals v w x y k, the numerals. Each with a short
   lesson on why it is hard.
7. Spacing. Sidebearings with optical spacing assist, then control strings: nnonnonon,
   HHOHOHOH.
8. Kerning. The essential pairs, overlay showing before and after.
9. Diacritics and anchors. Draw the marks once, place anchors, watch every composite
   build itself.
10. Extend a script, optional. Devanagari or kana on the same principles with the
    script's own control characters and metric system.
11. Test. Paragraph proofs, waterfall, the quality gate, Fontbakery.
12. Ship. Name, OFL licence, repo structure, DESCRIPTION, upstream.yaml, submission
    issue text. Push or hand off.

Each task has: a why, a demonstration on a sample font, the task itself, the gate, and a
reflection prompt that saves to the scrapbook.

### M6. UI [CANON]

Minimal, utilitarian, dense but calm. Glyph ink is always monochrome. Colour appears
only for meaning (selection, snap candidates, comparison layers, errors) from a fixed
high contrast palette that excludes red and green as meaning carriers, per the Hyle
rule. The user picks canvas background and texture: paper, vellum, blueprint, plain, or
imports their own. The letter stays sacred.

Layout: the canvas owns the screen; a collapsible tool rail; a bottom sheet inspector on
phone that becomes a side panel on desktop and web; a command palette everywhere. UI
type is one neutral sans, small, so the user's letters are the loudest thing on screen.

### M7. Quality gate and Google Fonts pipeline [CANON]

Layer one: Fontbakery, run locally, output translated from check IDs into plain
language with the rationale.

Layer two, Typewright's own checks that Fontbakery does not run:

- node economy per glyph with a threshold per glyph class
- extrema on curve
- tangent continuity at smooth joins
- stroke consistency for monolinear designs
- overshoot present on round glyphs
- anchors present for every mark and base
- composite completeness against the script's coverage list
- spacing sanity against control strings
- source provenance: the UFO is the origin of the TTF, not extracted from it
- italic provenance: warns when an italic is an uncorrected shear of the roman

Then the pipeline: OFL text, name table, version, STAT for statics, repo layout, UFO
source committed, DESCRIPTION, upstream.yaml, submission issue body. Two routes: manual
upload of the produced repo, or GitHub push from the app.

Everything the Hyle Deco submission got wrong is a check here. KNOWLEDGE.md is the list.

### M8. Multi script [CANON]

Per script: a metric system, a glyph inventory with correct names, a control character
set for the campaign, capture template sheets, OpenType feature generation for that
script's shaping model, HarfBuzz preview. Ship order: Latin; Hiragana and Katakana;
Devanagari; the other Indic scripts on the Devanagari pattern; Arabic Naskh; South East
Asian scripts. Nastaliq is out of scope for v1 and the app says so.

## 5. Data model

Project equals one directory. Inside: one UFO 3 per master; `typewright.json` for
settings and campaign progress; `scrapbook/` with assets and a manifest; `comparisons/`
for saved overlays; `build/` for compiled fonts and reports. Plain files, git friendly,
openable elsewhere. No proprietary blob.

## 6. Borrow versus build

| component | decision | source |
| --- | --- | --- |
| font compile | borrow | fontmake, fontTools |
| shaping | borrow | HarfBuzz |
| QA baseline | borrow | Fontbakery |
| initial contour | borrow or build | VTracer, or own |
| capture and cleanup | borrow | OpenCV |
| polygon booleans | borrow | Clipper2, or own at Bezier level |
| glyph lists, feature templates | borrow | OFL reference fonts, public shaping docs |
| corner detect, fit, snap, regularise | build | the product |
| construction grammar | build | nobody has it |
| comparison overlay, anatomy lens | build | the learning layer |
| campaign engine and content | build | with the Domestika lessons |
| UI, three modalities | build | Compose Multiplatform |

## 7. Milestones

Each ships something usable. Stop and reassess after each.

- M0 Checklist. Quality gate and GF pipeline alone, on a TTF or UFO the user brings.
  Fontbakery plus own checks, every result explained, repo produced. Small. Validates
  demand. Would have saved Hyle Deco a month.
- M1 Latin trace. Capture, trace and fit, review, hand edit, UFO out, compile. Node
  economy visible. Where Typewright beats Calligraphr.
- M2 Draw. Construction geometry, booleans, the three input models. A font from nothing.
- M3 Learn. Scrapbook, moodboard, comparison overlay, anatomy lens.
- M4 Campaign. The Latin workbook end to end, ending in a submission ready font.
- M5 Scripts. Kana, then Devanagari with shaping preview, then generalise.
- M6 Derive. Oblique by skeleton shear plus restroke, bold by offset, from clean
  outlines only.

## 8. Model and tool routing

Sonnet in Claude Code for module implementation and iteration. Opus for the fit
algorithm, the construction grammar, and campaign content design. The chat app with
artifacts for UI iteration; the comparison overlay and the canvas are best felt live.

## 9. Licence and money [CONFIRM]

Honest framing first. Fair source (FSL, BSL) is source available with a timed
conversion to a true open licence, usually two years. OSI does not classify it as open
source. So the choice is real.

Option A, FOSS. AGPL 3 for the app. Money comes from services around the free thing: a
hosted build and QA service for web users who cannot run Python locally, per build or
subscription; a paid pro tier of campaign content and script packs; and, the most
credible lane for this mission, grants. NLnet's NGI Zero funds exactly this kind of open
infrastructure for underserved scripts. Google Fonts has commissioned script work. This
option is the one consistent with "so more languages and styles survive."

Option B, fair source. FSL with Apache conversion after two years. Readable and forkable
for non competing use; a competitor cannot sell it as a service against you for two
years. Sentry and GitButler use it. Preserves a commercial option without closing the
code. Disqualifies some grants; purists will not call it open source.

Option C, open core. AGPL geometry and font core, proprietary shell and campaign content.
Cleanest commercial story, most compromised mission story.

Recommendation: A, with the hosted build service as the paid product and grants pursued
early. The tracing engine and multi script authoring are the socially valuable parts and
should be unambiguously free. Revenue will be modest either way; the mission is the
point, and the mission is what makes the grant lane real.

## 10. Repo layout

```
typewright/
  core-geometry/       pure Kotlin, exhaustively tested
  core-font/           UFO, naming, features, anchors
  engine-trace/        capture to outline chain
  engine-construct/    construction grammar, booleans
  compile/             fontmake bridge per platform
  shape-preview/       HarfBuzz per platform
  qa/                  Fontbakery bridge plus own checks
  learn/               scrapbook, comparison, anatomy
  campaign/            workbook engine and content
  scripts/             per script metrics, inventories, templates
  ui/                  Compose Multiplatform, all modalities
  app-android/  app-desktop/  app-web/
  docs/                KNOWLEDGE.md, campaign content, this file
```

## 11. What Madhav owes the build

- The Domestika lessons, to replace the M5 scaffold.
- Name and licence decisions.
- The Hyle Deco redraw, which becomes the first real font through the whole pipeline
  and the first campaign playthrough.
