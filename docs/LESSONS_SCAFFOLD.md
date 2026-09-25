# Lessons scaffold [SCAFFOLD]

Everything in this file ships marked SCAFFOLD in the UI until Madhav's material replaces or
augments it. The scene format is canon; the content is a first draft written to be
overwritten.

## 1. Scene format [CANON]

A lesson is a sequence of scenes. A scene is data; the canvas renders it live. No video,
no images of type: every example is a real font loaded at build time and subset for the
scene's glyphs.

```yaml
# scenes/lineages/03-transitional.yaml
id: lineages.transitional
strand: lineages            # lineages | anatomy | craft | scripts | reading
title: Transitional
era: "1757"
duration: 40                # seconds at the default pace; scrubbing ignores it
faces:                      # OFL/Apache faces from the corpus, by family name
  - key: garalde
    family: EB Garamond
  - key: transitional
    family: Libre Baskerville
stage:
  sample: "ago"             # the letters on stage
  align: xheight            # xheight | capheight | baseline
  from: garalde             # crossfade endpoints
  to: transitional
  stress: [30, 12]          # degrees from vertical at from/to; null = no stress axis
caption:
  tool: "the engraver's burin on copper"
  text: >
    Baskerville. Sharper, higher contrast, the stress standing up.
    Printing catches up with engraving.
  tags: [near-vertical stress, higher contrast, finer serifs]
callouts:                   # optional labels on the stage, resolved on the 'to' face
  - glyph: a
    part: terminal
    label: "finer, but still bracketed"
exercise:                   # optional; identify-it
  word: Hamburgefonstiv
  face: Libre Baskerville   # must not appear earlier in the strand
  options: [Garalde, Transitional, Didone]
  answer: Transitional
  giveaway: >
    stress almost vertical, but the serifs are still bracketed and the contrast
    is moderate. Baskerville, 1757.
```

Renderer rules: scenes crossfade the `sample` between `from` and `to` at matched
`align`; the stress dial interpolates `stress`; callouts fade in when the scrubber passes
0.6; the exercise appears after the last scene of a strand block and never shows a face
used on stage in that block. Every scene is pausable and scrubbable. Ambient room tone, if
present, is a single loop per strand, off by default.

## 2. Lineages: ten eras [SCAFFOLD]

Sample on stage: "ago". Faces are the explorer's exemplars; any OFL face of the class may
be substituted.

| # | era | year | face | tool | stress | note | tags |
|---|---|---|---|---|---|---|---|
| 1 | Blackletter | c. 1450 | UnifrakturMaguntia | a broad nib held steep; then Gutenberg's metal | none | The first printed letters imitate the scribe: dense, vertical, compressed, every stroke a pen stroke. | broad-nib strokes · no round shapes · tight texture |
| 2 | Garalde | c. 1530 | EB Garamond | a broad nib, cut into steel punches | 30° | Garamond. The pen angle is still in the letter: oblique stress, a small x-height, bracketed serifs. | oblique stress · two-storey a and g · bracketed serifs |
| 3 | Transitional | 1757 | Libre Baskerville | the engraver's burin on copper | 12° | Baskerville. Sharper, higher contrast, the stress standing up. Printing catches up with engraving. | near-vertical stress · higher contrast · finer serifs |
| 4 | Didone | c. 1790 | Playfair Display | the pointed pen, then the compass | 0° | Bodoni and Didot. Vertical stress, hairline serifs, maximum contrast. Type stops imitating the hand. | vertical stress · hairline serifs · ball terminals |
| 5 | Slab | 1815 | Zilla Slab | wood type for the poster wall | 0° | The Egyptian. Serifs as thick as stems, made to shout from across a street. | square serifs · low contrast · even colour |
| 6 | Grotesque | c. 1830 | Work Sans | the same posters, serifs dropped | 6° | The first sans. A little contrast left, slightly awkward proportions; the name was an insult. | no serifs · slight contrast · narrow apertures |
| 7 | Art Deco | 1925 | Limelight | the draughtsman's set square | 0° | Geometric bones with theatrical proportions: waists high or low, inline stripes, extreme widths. | high or low crossbars · inline stripes · extreme widths |
| 8 | Geometric | 1927 | Jost | compass and ruler | 0° | Futura. Circle, triangle, square. The o is a circle, the a is one storey, the stroke is one weight. | circular o · single-storey a · monoline |
| 9 | Humanist sans | 1928 | Source Sans 3 | the pen, remembered | 10° | Gill Sans, later Frutiger. A sans that remembers the pen: open apertures, calligraphic proportions. | open apertures · pen proportions · two-storey a and g |
| 10 | Neo-grotesque | 1957 | Inter | the phototypesetter | 0° | Helvetica, Univers. The grotesque tidied into neutrality: horizontal terminals, even colour, closed apertures. | horizontal terminals · even colour · closed apertures |

Vocabulary scene (after era 6): "gothic" means sans in American usage (Franklin Gothic,
News Gothic) and blackletter in European usage. The app uses the Google Fonts taxonomy
names; this is why.

Identify-it bank (faces not on stage): Libre Baskerville → Transitional (bracketed serifs,
near-vertical stress); Libre Bodoni → Didone (hairlines); Poppins → Geometric (circular o,
monoline); Libre Franklin → Grotesque (slight contrast, closed apertures); Roboto Slab →
Slab; Cormorant → Garalde (oblique stress, small x-height); Open Sans → Humanist sans
(open apertures, two-storey g); Josefin Sans → Art Deco (low crossbars, geometric bones).
Each exercise names the give-away and the year.

## 3. Anatomy [SCAFFOLD]

Lens scenes on the user's own letters, one part per scene, with the definition and the
glyphs where the part occurs (term-to-glyph graph from the research): stem, bowl, counter,
aperture, terminal, spur, ear, link, loop, crossbar, arm, leg, shoulder, spine, apex,
vertex, tail, tittle, serif (bracketed, hairline, slab), stress, contrast, x-height, cap
height, ascender, descender, overshoot. Font-level terms are guides, not labels.

## 4. Craft: KNOWLEDGE.md as scenes [SCAFFOLD]

Each entry in `KNOWLEDGE.md` becomes a before/after scene with the check it produced.

| scene | before | after | live measure |
|---|---|---|---|
| A1 Tracing destroys the drawing | the shipped T, 1,763 points, every point drawn | the 8-point T | the count collapsing; the box plot point travelling into the box |
| A2 The source was a fossil | UFO extracted from TTF | UFO drawn, TTF compiled | provenance arrow reversed |
| A3 Extrema off curve | a curve whose extreme falls between points | extremum on curve | rendering at 12 px, both |
| A4 Coincident edges | hood ending on the stem edge | overlap by 4 units, union | the sliver, magnified |
| A5 Mixed caps | round arm, flat descender | one style | terminal audit list |
| A6 Diagonal tips | endpoint on the line, corner 22 above | corner on the line | tip alignment readout |
| A7 Arches at the wrong height | arch at 404 | arch at 500 | ink bounds versus metric lines |
| A8 Overshoot is not an error | o flattened to 500 | o at 508 | the two side by side at 10 pt |
| B1 Inflating a bold | offset outline, counters collapsing | drawn bold | counter area versus stem growth |
| B2 Shearing an italic | the o sheared 9°, stroke 40→49 | centreline sheared, restroked, 44 ± 0.2 | stroke probe sweeping the o |
| C1 Baked composites | accents by bounding box | anchors: one mark, every base | composites rebuilding as the anchor moves |
| D1 Sidebearings by class | eyeballed | measured by class | control strings, before and after |
| E1 Colliding names | two fonts, one name table | unique names | Android cache story in one sentence |
| F1 Passing checks is not quality | Fontbakery green, T 1,763 | the same font in Economy | "what these checks do not measure" |
| F3 Measurement that does not look | numbers fine, letters broken | the rendered word | the proof, large |

Madhav's Domestika material augments these scenes and can add its own.

## 5. Scripts [SCAFFOLD]

- Devanagari: seven vertical levels, two fixed (headline, baseline); letters hang from the
  shirorekha; negative sidebearings join the headline; the first-glyph progression from
  Design With FontForge (पाव → किमीनुफू → भरसगदह); shaping is the engine's job, not the
  drawer's.
- Arabic Naskh: the joining line has weight; tooth, loop and eye heights replace x-height;
  exit and entry anchors at y = 0 on the sidebearings; four forms from one skeleton
  (behDotless); Nastaliq is out of scope and the scene says why.
- Kana: the virtual body, the letter face, centred not seated; the dakuten corner; ~200
  glyphs, not 92.

## 6. Reading [SCAFFOLD]

The proof sequence as scenes: running text at target size first (dark and light spots);
control strings with n/o and H/O; the sandwich permutations; every glyph large; Goldilocks
tracking proofs; interleaved family comparison; the paired paragraph; the focused crit
question. Test words with their reasons: Hamburgefonstiv, adhesion, videospian.

## 7. Content sources and licences

Written for the app, or adapted with attribution from: Wikipedia (CC BY-SA 4.0), Design
With FontForge (CC BY-SA 3.0 [CONFIRM]), Google Fonts Knowledge [CONFIRM licence]. Dates
and attributions in §2 are conventional (Gutenberg c. 1450; Garamond 1530s; Baskerville
1757; Didot/Bodoni 1780s–90s; Figgins's Egyptian 1815; Caslon's sans 1816 and Thorowgood's
"grotesque" 1832; the 1925 Paris exposition; Futura 1927; Gill Sans 1928; Helvetica 1957)
and should be checked against a type history reference before release.
