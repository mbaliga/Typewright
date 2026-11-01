# Capture and trace (the pure engine)

What `engine-trace`'s `capture` package does, what it was tested on, and what that does and does not prove. The code is in
`engine-trace/src/commonMain/.../capture/`, the printable sheets in `scripts/.../sheets/`, and the photographs and judge the tests use in
`capture-testkit/`.

## Every photograph here is synthetic

No test, table or golden-path step in this repository has ever read a photograph of paper. Each "photograph" is made by
`capture-testkit`'s `SyntheticPhotos`: a real template sheet is rendered (corner marks, identity code, guides, labels), the repository's
own Hyle Deco outlines are filled into its cells (rings, for the scripts Hyle Deco does not cover), and the page is seen by a pinhole
camera at a chosen tilt (up to 25 degrees) and roll (including held sideways and upside down), lit unevenly (a gradient, a vignette, a
shadow, glare), blurred, noisy, JPEG-quantised, printed on tinted paper, with printed guides that are darker or lighter, wider or
narrower, or a quarter of a millimetre out of register against the template's. That is a lot of what a phone does to a page. It is not
printer black, paper grain, lens distortion, a phone's own sharpening, or a real pen or pencil. Real printed-sheet photographs are an
owner-supplied fixture that is still owed (open question 214), and **every threshold below is "our heuristic" until they are**.

## The pipeline

1. **Corners.** `findCornerMarks` finds the four black squares (adaptive threshold, connected components, shape and size tests). Fewer than
   four is `CaptureFailure.CornersMissing`, naming the missing one when exactly three are seen ("3 of 4 corners: the bottom right mark is
   missing").
2. **Which sheet, which way up.** The four marks are given each rotation and each paper size; for each the homography from page
   millimetres to photograph pixels is solved (`Homography`, normalised DLT) and the identity code is sampled through it. The hypothesis
   whose code passes its start pattern and CRC is the sheet; none is `IdentityUnreadable`.
3. **Quality.** Edge softness of the marks, angle, glare, shadow and mark contrast are measured. Each has a limit, a typed failure and a
   plain sentence; the edge softness failure `NoEdgeFound` is raised, instead of a made-up figure, when no mark has an edge that can be
   measured.
4. **Cells.** Each of the sheet's cells is cut out flat at 10 pixels a millimetre. The paper's brightness is divided out, the template's
   guides are divided out using a model of them pooled over the sheet (`PrintedGuides.coverage`, `estimateStrength`), and what that model
   misses (a printer's guides are not the template's) is taken out by `withoutGhosts`: a mark lying wholly within 1.8 mm of a guide, or of
   a guide's printed name, and thinner than 1.0 mm, is the guide's leftover, not a drawing. A pen line a millimetre or more across (a
   marker underscore on the descender guide) is kept even when it lies wholly inside the band. Then the cell is thresholded, cropped, measured and named.
5. **Light pencil.** A cell with nothing at the pen level is read again at darkness 0.12. What turns up counts as a drawing if it has at
   least 0.8 mm² of ink in strokes at least 0.4 mm wide; it is returned as an `Uncertain(FAINT)` cell with its darkness stretched, for the
   person to confirm. A light pencil drawing is not lost without a word.
6. **Status.** Each cell is `Read`, `Empty` or `Uncertain` (ink touching the edge of its box, or faint), with a relabel list ranked by shape
   similarity of the drawings on the sheet (or by position in the template when there is nothing to compare with); the basis is stated.

Which glyph a cell holds is never judged by the engine: it is the template's. What can go wrong is geometry, which is what the tests judge.

### The sheet identity pattern

A row of 22 square modules (3 mm each, black = 1) centred between the two upper corner marks, read left to right as the page is meant to be
read, so a photograph needs no OCR:

| bits | meaning |
|---|---|
| 0-3 | start pattern `1 1 0 1` (a reading that does not begin with it is not a sheet, or is upside down) |
| 4-6 | script (Latin 0, Kana 1, Devanagari 2, Naskh 3) |
| 7-10 | sheet number, counting from 0 |
| 11-14 | number of sheets in the script, minus 1 |
| 15 | paper: 0 = A4, 1 = Letter |
| 16-21 | CRC-6 (x^6 + x + 1, start value 0) of bits 4-15 |

A reading is accepted only when the start pattern and the CRC both hold, so a misread cannot name a sheet that is not there.

## Where each number comes from

Nothing below was measured on real photographs. Law 5 (CLAUDE.md) says a number the app judges a person by comes from `qa/corpus`;
none of these does, so every screen that shows one says "our heuristic".

| number | value | what it does | basis |
|---|---|---|---|
| corner-mark search: `MARK_DARKNESS`, `MARK_MEAN_DARKNESS`, `FAINT_MARK_*`, `MIN_ISOTROPY`, `CONFIDENT_ISOTROPY`, `MAX_BOX_ASPECT`, `MAX_AREA_RATIO`, `SIZE_CONSISTENCY` | 0.45, 0.65, 0.2 and 0.28, 0.22, 0.5, 3.0, 6.0, 0.35 | how dark, how square and how alike four blobs must be to be the marks | our heuristic |
| `maxEdgeWidthMm` | 1.0 mm | a softer 10-90% edge on the corner marks is "too blurry" | our heuristic |
| `maxPerspective` | 1.8 | longer over shorter opposite edge, as seen | our heuristic |
| `maxGlareFraction` | 0.08 | share of the sheet blown out to white | our heuristic |
| `maxShadowDepth` | 0.45 | darkest twentieth against the median paper | our heuristic |
| `minMarkContrast` | 0.45 | how much darker the marks are than the paper | our heuristic |
| `GUIDE_CEILING`, `PAPER_FLOOR` | 0.33, 0.28 | lowest darkness taken as pen ink; below this a cell's pixels are paper | our heuristic, set on ink from 0.25 to 0.65 darkness |
| `MIN_INK_PIXELS` | 40 | fewer than this is not a drawing | our heuristic |
| `FAINT_DARKNESS` | 0.5 | ink fainter than this is flagged faint | our heuristic |
| `FAINT_LEVEL`, `FAINT_MIN_INK_MM2`, `FAINT_MIN_STROKE_MM` | 0.12, 0.8 mm², 0.4 mm | the second reading for light pencil (areas and widths in millimetres, so they do not change with the pixels a millimetre) | our heuristic |
| `GHOST_BAND_MM`, `GHOST_MAX_THICKNESS_MM`, `GHOST_DARKNESS` | 1.8 mm, 1.0 mm, 0.12 | what counts as a guide's leftover: wholly inside the band and thinner than this (three times the template's 0.3 mm guide is the widest printed in the tests, 0.9 mm) | our heuristic |
| `PrintedGuides.MIN_STRENGTH` | 0.04 | guides fainter than this are no guides | our heuristic |
| `EDGE_WIDTH_PER_SIGMA` | 2.563 | 10-90% width of a Gaussian-blurred step edge, in standard deviations | mathematics, not a measurement |
| `NOISE_SCALE_UNITS`, `NOISE_FLOOR_UNITS`, `PULL_SCALE_UNITS` | 10, 0.8, 20 | the scale an edge's grain is separated from its shape at, the grain below which an edge is left alone, and the width at which the slow inward pull smoothing gives any curve is separated from grain, so a clean circle of radius 30 or more reads below the floor | our heuristic |
| `SMOOTHING_PER_NOISE`, `MAX_SMOOTHING_UNITS` | 8, 18 | how wide a Gaussian each side is smoothed by, per unit of grain | our heuristic |
| `LINE_TOLERANCE_WOBBLES`, `BOW_TOLERANCE_WOBBLES`, `TRIM_TOLERANCE_WOBBLES`, `CURVE_TOLERANCE_WOBBLES` | 4, 2.5, 2, 1.5 | how far the tests for a straight side and the curve fit widen with the edge's measured wobble | our heuristic |
| `LINE_SLOPE_FRACTION` | 0.01 | a side may drift a hundredth of its length and still be one line | our heuristic |
| `FILLET_SOFTNESS_LENGTHS`, `FILLET_DEVIATION_SOFTNESS` | 5, 1.0 | the size of rounding, in edge-softnesses, that is squared up into a corner | our heuristic |
| `WOBBLE_FLOOR` | 0.3 units | under this an edge is clean and none of the above applies | our heuristic |
| `TOLERANCE_PER_WOBBLE`, `MAX_ADAPTED_TOLERANCE` | 5, 8 units | the fit tolerance is raised to this many times the wobble, at most this | our heuristic |
| `TraceTargets.hardClaim` | per glyph | the claim for the hardest photographs (below): the most points each glyph got on the three of them | set after measuring; stated as such |

A clean render, whose edge shows less than `WOBBLE_FLOOR` of wobble and no softness, is traced with none of the allowances and gives the
fitter's own result, so the CLAUDE.md fixture counts do not depend on them.

## SVG per-glyph pack import

`SvgTemplateImport` reads one file of `scripts/templates/hyle-all-templates.zip` (`svg/<Script>/NNN_NAME.svg`). The file name is matched to
its template (by folder and name, then by name alone: a file in one of the pack's own folders is looked for in that folder only, so a
Nastaliq file is never taken for a Naskh drawing, and a file in any other folder, such as `my-drawings/`, is found by its name), and that
is how a drawing finds its glyph; the SVG's contents never decide it. The
drawing layer `DRAW_HERE` is read: paths (all commands, arcs included), rects, circles, ellipses and polygons, with transforms and styles;
the `guides` layer, the faint `reference_delete_me` glyph and the label are not drawings. y runs down from the baseline in the pack and is
flipped to the font's y-up; a file whose viewBox origin was moved is put back where the pack has it and the sentence says so. A drawing made
of strokes is refused with the instruction to run Outline Stroke. `traceOutlines` then runs Corner detect, Fit, Snap, Regularise and Review
on the drawing's own curves, flattened: no thresholding or boundary following happens, so the trace starts at Fit. Tests read all 398 real
files of the zip (349 blank templates matched to their glyphs, 49 Nastaliq files refused because Nastaliq has no sheet) and draw into the
real files: Hyle Deco's T, H, o and n put in the real Latin templates trace to 8-0, 12-0, 16-16 and 14-8 from the SVG alone. What the import
assumes is open question 217.

## Accuracy of the capture, per photograph and per setting

"Right ink" is `CellJudge`: the ink read from the photograph overlaps (intersection over union, at least 0.75) the glyph that belongs in
that cell, drawn from the font's outlines independently of the photograph; a cell nobody drew in must read empty. The threshold was set
before any result was seen. The judge can fail: drawings one cell off score 1 of 24, another sheet's drawings 0 of 24 (tests
`drawingsOneCellOffAreCaughtAsMisses`, `aPhotographOfAnotherSheetsDrawingsIsCaughtAsMisses`). Each photograph must clear 95% on its own,
the hardest included, and so must each setting. The golden path (step 1b) judges the same 23 photographs.

| photograph | setting | cells | right ink | accuracy | flagged faint or edge (of them right) | read ms |
|---|---|---|---|---|---|---|
| straight-on, clean | straight on | 24 | 24 | 100.0% | 0 (0) | 1505 |
| tilt 10, roll 3 | tilted, lit unevenly | 24 | 24 | 100.0% | 0 (0) | 1485 |
| tilt 15 side-on, roll -4, light falloff 15% | tilted, lit unevenly | 24 | 24 | 100.0% | 0 (0) | 1560 |
| tilt 20, roll 6, falloff 25%, warm paper | tilted, lit unevenly | 24 | 24 | 100.0% | 0 (0) | 1482 |
| HARDEST A: tilt 25, roll 8, falloff 35%, blur 1.2, noise 6, q50, cool paper | hardest: tilt 24-25, blur, noise, heavy compression | 24 | 24 | 100.0% | 1 (1) | 1309 |
| HARDEST B: tilt 25, roll -8, falloff 30%, blur 1.6, noise 9, q35, pencil | hardest: tilt 24-25, blur, noise, heavy compression | 24 | 24 | 100.0% | 0 (0) | 1319 |
| HARDEST C: tilt 24, vignette 40%, falloff 20%, blur 1.4, noise 8, q40, green paper | hardest: tilt 24-25, blur, noise, heavy compression | 24 | 24 | 100.0% | 0 (0) | 1437 |
| HARDEST D: tilt 25, roll 5, blur 1.6, noise 9, q35, shadow, sheet 4 | hardest: tilt 24-25, blur, noise, heavy compression | 24 | 24 | 100.0% | 0 (0) | 1318 |
| held sideways (roll 90) | held sideways or upside down | 24 | 24 | 100.0% | 0 (0) | 1537 |
| upside down (roll 180) | held sideways or upside down | 24 | 24 | 100.0% | 0 (0) | 1446 |
| held sideways the other way (roll 270), noisy | held sideways or upside down | 24 | 24 | 100.0% | 1 (1) | 1314 |
| US Letter, tilt 14 | US Letter, no guides, bright desk | 24 | 24 | 100.0% | 0 (0) | 1323 |
| no printed guides | US Letter, no guides, bright desk | 24 | 24 | 100.0% | 0 (0) | 1414 |
| bright desk, tilt 18 | US Letter, no guides, bright desk | 24 | 24 | 100.0% | 0 (0) | 1399 |
| guides twice as dark and twice as wide, shifted 0.25 mm, tilt 15 | printer's guides differ from the template's | 24 | 24 | 100.0% | 0 (0) | 1535 |
| guides half as dark and half as wide, hard conditions | printer's guides differ from the template's | 24 | 24 | 100.0% | 0 (0) | 1436 |
| light pencil (darkness 0.33), tilt 12 | light pencil (darkness 0.22 and up) | 24 | 24 | 100.0% | 24 (24) | 1449 |
| very light pencil (darkness 0.25), tilt 8 | light pencil (darkness 0.22 and up) | 24 | 24 | 100.0% | 24 (24) | 1744 |
| faint pencil (darkness 0.22) with doubled guides | light pencil (darkness 0.22 and up) | 24 | 24 | 100.0% | 24 (24) | 1719 |
| barely there pencil (darkness 0.16), tilt 6 | fainter than the claim: darkness 0.16 (measured, no floor) | 24 | 20 | 83.3% | 24 (20) | 1805 |
| Kana sheet 2, rings, tilt 16 | kana, Devanagari, Naskh (ring drawings) | 24 | 24 | 100.0% | 0 (0) | 1348 |
| Devanagari sheet 3 (18 cells), rings, Letter | kana, Devanagari, Naskh (ring drawings) | 18 | 18 | 100.0% | 0 (0) | 999 |
| Naskh sheet 1, rings, tilt 20 | kana, Devanagari, Naskh (ring drawings) | 24 | 24 | 100.0% | 0 (0) | 1334 |

Over the 22 photographs in the claimed range, 522 of 522 cells hold the right ink (542 of 546 with the one limit photograph). Misses, all in
the limit photograph: cells 18 to 21 (`period`, `comma`, `colon`, `semicolon`) overlap by 72 to 74%. The cells flagged `Uncertain` are
counted in the table; on the pen-dark photographs one or none is flagged, and on the light-pencil ones every drawn cell is, with the right ink.
Light pencil's lower limit was not reached: at darkness 0.16 the drawings are still found and flagged, with 20 of 24 right.

**One sheet, timing.** A 1500 by 2000 photograph read (corners, identity, 24 cells) takes 1.0 to 1.8 seconds in the table above (a warm
JVM, a 4-core machine shared with other work; the first call in a fresh JVM is slower). The trace of one cell takes a few milliseconds.

## Trace quality against the fitted targets

CLAUDE.md fixes the targets, on-curve and off-curve: T 8-0, o 16-16, n 14-8, H 12-0 (T's stem foot lands on the baseline). They are held
here from a **raster**, through capture, not from the font's outline. Four photograph classes (straight on, tilt 10, tilt 20 with noise,
tilt 16 with blur and compression) must meet them **exactly**, all sixteen glyphs. The three hardest photographs (tilt 24 to 25, blur
1.2 to 1.6 photograph pixels, noise 6 to 9, JPEG quality 35 to 50) are measured and held to a stated claim: right contours, the ink box
within 2% of the em, and at most the most points each glyph got on the three of them when they were measured (`TraceTargets.hardClaim`:
T 9-2, H 12-0, o 22-28, n 18-16). **That claim was written after measuring them** and is not a quality bar; it is tight enough that a fitter
that gets worse on them fails. On them eight of twelve glyphs reach the targets exactly; four do not (open questions 215 and 220): `T` of the
pencil photograph is 8-2 against 8-0 and its `o` 22-28 against 16-16, and the green-paper photograph's `T` is 9-0 against 8-0 and its `n`
18-16 against 14-8. An earlier table had nine of twelve exact: the noise reading no longer takes the pull of a curve for grain (open
question 219), and the pencil photograph's `T` and `o` read a little rougher because of it. Nothing was tuned to bring them back.

Every stage keeps its contours, node count, one plain sentence and its parameters. The count never rises from Contour to Fit (a fit with
more points than the boundary it fits is replaced by the boundary's corners), and golden-path step 2 asserts the fit is under a fifth of
the boundary's points. The deviation column is the final outline's greatest distance from the traced boundary, measured both ways
(`outlineDeviation`); it includes what squaring up blurred corners and smoothing cost, which is why it reaches 19.5 units on the hardest
photographs (open question 216).

| photograph | glyph | contours | fit on/off | final on/off | target on/off | box off (units) | deviation (units) | redrawn mismatch |
|---|---|---|---|---|---|---|---|---|
| straight-on | T | 1 | 8/0 | 8/0 | 8/0 | 1 | 3.7 | 0.2% |
| straight-on | H | 1 | 12/0 | 12/0 | 12/0 | 0 | 3.8 | 0.1% |
| straight-on | o | 2 | 16/16 | 16/16 | 16/16 | 0 | 1.7 | 1.4% |
| straight-on | n | 1 | 14/8 | 14/8 | 14/8 | 0 | 3.0 | 0.9% |
| tilt 10, roll 3 | T | 1 | 8/0 | 8/0 | 8/0 | 2 | 4.8 | 0.1% |
| tilt 10, roll 3 | H | 1 | 12/0 | 12/0 | 12/0 | 0 | 3.2 | 0.1% |
| tilt 10, roll 3 | o | 2 | 16/16 | 16/16 | 16/16 | 0 | 1.8 | 2.9% |
| tilt 10, roll 3 | n | 1 | 14/8 | 14/8 | 14/8 | 0 | 3.3 | 1.6% |
| tilt 20, roll 6, falloff 25%, warm paper, noise 4 | T | 1 | 8/0 | 8/0 | 8/0 | 2 | 4.9 | 0.2% |
| tilt 20, roll 6, falloff 25%, warm paper, noise 4 | H | 1 | 12/0 | 12/0 | 12/0 | 1 | 3.6 | 0.7% |
| tilt 20, roll 6, falloff 25%, warm paper, noise 4 | o | 2 | 16/16 | 16/16 | 16/16 | 1 | 2.3 | 1.9% |
| tilt 20, roll 6, falloff 25%, warm paper, noise 4 | n | 1 | 14/8 | 14/8 | 14/8 | 1 | 4.2 | 1.8% |
| tilt 16, blur 0.9, noise 5, q70 | T | 1 | 8/0 | 8/0 | 8/0 | 1 | 4.7 | 0.3% |
| tilt 16, blur 0.9, noise 5, q70 | H | 1 | 12/0 | 12/0 | 12/0 | 1 | 5.2 | 0.3% |
| tilt 16, blur 0.9, noise 5, q70 | o | 2 | 16/16 | 16/16 | 16/16 | 1 | 2.6 | 2.4% |
| tilt 16, blur 0.9, noise 5, q70 | n | 1 | 14/8 | 14/8 | 14/8 | 1 | 4.0 | 2.7% |
| HARDEST A: tilt 25, falloff 35%, blur 1.2, noise 6, q50 | T | 1 | 8/0 | 8/0 | 8/0 | 2 | 5.9 | 3.1% |
| HARDEST A: tilt 25, falloff 35%, blur 1.2, noise 6, q50 | H | 1 | 12/0 | 12/0 | 12/0 | 9 | 10.8 | 4.5% |
| HARDEST A: tilt 25, falloff 35%, blur 1.2, noise 6, q50 | o | 2 | 16/16 | 16/16 | 16/16 | 3 | 3.3 | 2.7% |
| HARDEST A: tilt 25, falloff 35%, blur 1.2, noise 6, q50 | n | 1 | 14/8 | 14/8 | 14/8 | 4 | 8.5 | 2.6% |
| HARDEST B: tilt 25, blur 1.6, noise 9, q35, pencil | T | 1 | 8/2 | 8/2 | 8/0 | 3 | 8.7 | 5.2% |
| HARDEST B: tilt 25, blur 1.6, noise 9, q35, pencil | H | 1 | 12/0 | 12/0 | 12/0 | 8 | 19.5 | 8.2% |
| HARDEST B: tilt 25, blur 1.6, noise 9, q35, pencil | o | 2 | 20/24 | 22/28 | 16/16 | 2 | 8.6 | 4.8% |
| HARDEST B: tilt 25, blur 1.6, noise 9, q35, pencil | n | 1 | 14/8 | 14/8 | 14/8 | 5 | 9.5 | 6.5% |
| HARDEST C: tilt 24, vignette 40%, blur 1.4, noise 8, q40 | T | 1 | 9/0 | 9/0 | 8/0 | 8 | 8.5 | 1.9% |
| HARDEST C: tilt 24, vignette 40%, blur 1.4, noise 8, q40 | H | 1 | 12/0 | 12/0 | 12/0 | 8 | 10.4 | 3.3% |
| HARDEST C: tilt 24, vignette 40%, blur 1.4, noise 8, q40 | o | 2 | 16/16 | 16/16 | 16/16 | 4 | 4.7 | 2.7% |
| HARDEST C: tilt 24, vignette 40%, blur 1.4, noise 8, q40 | n | 1 | 17/14 | 18/16 | 14/8 | 11 | 15.5 | 3.4% |

## What the fitter does for a rough edge

The fitter was changed only where the photographs showed it was wrong, and only through allowances that a clean edge never triggers:

- **Wobble.** The edge's wobble (`measureEdgeRoughness`, and `estimateBoundaryNoise` for the grain finer than 10 units) widens the tests that
  decide whether a stretch is a straight side, and the fit tolerance follows it, so a side a camera shook by a unit is still one line.
- **Per contour.** Each contour is judged on its own edge: the wobble, and so the fit tolerance and the minimum straight length, are the
  contour's, not the glyph's worst, so a rough stem does not coarsen a clean dot beside it.
- **Denoise.** When the grain is over 0.8 units, corners are found on a copy smoothed by a Gaussian, with a look distance that grows with the
  smoothing, and each side between two corners is then smoothed on its own with its ends held, so a side straightens without a corner
  being rounded (`detectOutlineCornersDenoised`).
- **Square up blurred corners.** A photograph blurs every corner, so a short curve between two straight sides that stays within the edge's
  softness of a sharp corner is made one (or, for the flat of a narrow stem top, two); the softness is measured on the printed corner marks
  (`CapturedCell.edgeSoftnessUnits`) or, failing that, from the ink's own edges (`estimateEdgeSoftnessPx`, a lower bound). The Fit sentence
  says how many were squared up and that the setting can be turned off.
- **Joining.** Two straight sides the corner finder cut in two, or with a nick of curve between them, are one side.

Each is a setting on the Trace screen's "adjust" (`fit.denoise`, `fit.adapt`, `fit.square`).

## Golden path cost

The golden path runs steps 1b and 2 and the scan chain over 23 photographs and 28 traces. With the repo's settings (the Gradle daemon at
`-Xmx3g`, the test JVM at `maxHeapSize = "2g"` in `golden-path/build.gradle.kts`) it took 6 min 24 s for `goldenPath` and
`goldenPathRatchet` together on this shared 4-core machine, from a warm build (the test run itself 256 s: chain-scan 112 s, step 1b 51 s,
step 2 38 s, chain-open 28 s). The same run with the test heap cut to 1g also passed (4 min 42 s), so 2g leaves headroom and 1g is about the
floor; the cell rasters are not kept after they are judged. CI's golden-path job has 45 minutes and a 7 GB runner, which is enough; nothing
in `.github/workflows/ci.yml` changed. Not run on a CI runner from here: owner-verified only.

## What is not claimed

Nothing here says the engine reads a real printed sheet. It says: on 23 synthetic photographs including the hardest this generator makes,
the capture puts the right ink in the right cell in 522 of 522 cells in its claimed range; the trace meets CLAUDE.md's fitted counts exactly
on four classes of photograph and eight of twelve glyphs on the three hardest; and the SVG import reads every file of the real pack. Device
behaviour (the camera, the live Camera status line) is not built here and is owner-verified only.
