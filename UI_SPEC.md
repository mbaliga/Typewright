# Typewright UI spec

Derived from `ui/typewright-explorer.html` (version 4, 23 Sept 2026). The explorer is the
source of truth for look and interaction; this document names what the explorer shows so a
build agent can reproduce it in Compose Multiplatform without guessing. Where a value here
and the explorer's CSS disagree, the explorer's CSS wins for visuals and this document wins
for behaviour. Never quietly diverge from either; change the explorer first.

The explorer's screens, by nav chip: Home (specimen) · Capture · Trace · Economy · Draw
(one sheet: Draw, Space, Learn rooms; the puck) · Space · Learn (Lineages, Overlay, Lens,
Scrapbook) · Check · Ship · Workbook · Desktop. Open the explorer's Notes for the design
rationale per screen.

## 1. Layers

From back to front, on every canvas surface:

1. **Paper** — the canvas texture (colour + grain, or the blueprint grid).
2. **Grid** — 28 dp square grid at ink 8.5% (white 9% on dark canvases). Part of the sheet;
   moves with it.
3. **Lines** — metric lines and guides. SVG in sheet space; 1 dp hairline at ink 20%;
   labels in mono 22 units of the glyph box (≈ 9.5 sp), right-aligned at the canvas edge.
4. **Bloom** — quiet zones: radial gradients from canvas colour (opaque to 38–45%) to
   transparent, positioned under every glass element. Counter-translated so it stays fixed
   to the glass while the sheet moves. Hidden in map mode.
5. **Ink** — glyph outlines, fills, nodes, handles, the loupe, dimension lines, proofs.
6. **Glass** — header, puck, unfolded tool list, radial, inspector, edge marks, hints.

Nothing on the glass has a background, border, shadow or blur. A glass element is text,
an ink block, or the puck.

## 2. Tokens

Canvas textures (`data-texture`): each defines canvas, ink, chrome foreground, muted,
line, grain opacity, and dark-or-light meaning colours.

| texture | canvas | ink | fg | muted | line | grain |
|---|---|---|---|---|---|---|
| paper (default) | #EFE9DC | #17150F | #1B1915 | #6F6A5E | ink 20% | 16% |
| vellum | #E6DCC1 | #1A160F | #1B1712 | #766D5A | ink 22% | 30% |
| blueprint | #17417C | #FFFFFF | #EAF1FF | #A6B8DA | white 38% | 0 (+ 24 dp grid at white 6%) |
| black | #000000 | #F3F3F0 | #ECECEA | #8E8E89 | white 22% | 0 |
| white | #FFFFFF | #000000 | #141414 | #6B6B6B | ink 18% | 0 |

Meaning colours: violet #5F4BE0 (light canvases) / #8E7BFF (dark, and #B3A6FF on
blueprint); cyan #0A9D8E / #08FED5; amber #B57A00 / #FFB300 (#FFC247 blueprint); magenta
#B8248F / #FF5FD2 (#FF7ADB blueprint). Grain: procedural 160 px noise tile, multiply blend.

Radii: 0 everywhere except the puck (circle) and the phone frame of the explorer (not part
of the app). Shadows: none. Blur: none.

Type:
- Sentences: system UI sans (Roboto on Android, system on desktop/web), 12.5–13 sp,
  line-height 1.45. Bundle Inter [CONFIRM] for cross-platform identity.
- Labels, numbers, chips: monospace (system mono), 9.5–11 sp, uppercase, letter-spacing
  0.14 em, tabular numerals.
- Headings inside rooms: 14–15 sp semibold; the specimen hero is the user's font at 54 sp.
- The user's font is the only display type on screen.

Spacing: 18 dp side gutter inside the phone; 14–22 dp vertical rhythm; the inspector row
at 16 dp from the bottom plus safe area.

## 3. Components and states

### Header (glass)
Room name as an ink block (mono, uppercase, 11 sp, padding 5×9); glyph info beside it (15 sp
semibold + mono small). Tap the room name → map. Horizontal swipe on the header (≥ 40 dp)
→ previous/next room. MAP text button top-right becomes an ink block when the map is open.

### Puck (glass)
68 dp circle, ink fill, canvas-coloured icon (26 dp, 1.6 stroke), name below in mono 9.5 sp
uppercase. Grip: 18×3 dp bar at the top inside the circle (canvas at 45%). States: rest;
rolling (icon slides up 6 dp and fades to 20% for 110 ms on tool change); holding (scale
0.94, label hidden); dragging (no transition, scale 1.06). Pinned position: 18 dp from an
edge, 40% down the canvas by default; re-pins to nearest edge on release.

Gestures: pointerdown starts a 380 ms hold timer. Movement > 6 dp before the timer cancels
it and starts a vertical swipe: every 34 dp of travel cycles one tool (haptic 6 ms). Tap
(no movement, released before 380 ms) toggles the unfolded list. Hold opens the radial.
Drag from the grip repositions.

### Unfolded tool list (glass)
A column beside the puck (on the side with room), canvas background, each row: 18 dp icon,
mono uppercase name, shortcut letter right-aligned in muted. Current tool row is an ink
block. Tap selects and collapses.

### Radial dial (glass)
260 dp square centred on the puck; 8 sectors between r = 50 and r = 120 dp, canvas fill with
a 2 dp canvas stroke as the gap; the current sector is an ink block with a canvas icon; a
fixed ink triangle marker at 12 o'clock; the current tool's name above the marker in mono.
The ring rotates with the pointer (incremental angle, unwrapped); the sector under the
marker is current; detents every 45° tick 5 ms; release commits (10 ms) and fades the ring
out in 150 ms. Release within r < 24 dp cancels.

### Inspector (glass, bottom)
A single row of label/value pairs: label mono 9.5 sp uppercase muted; value 15 sp semibold
tabular. The edited or snapped value is an ink block. Cyan value for the snap target;
amber for probe readings. Never more than six pairs; wrap is a failure of content, not
layout.

### Edge marks (glass)
‹ › at mid-height, 22 sp, ink at 30%; hidden at the ends of the row and in map mode.

### Map
World scaled to 0.3 and centred; each room outlined with a 2 dp ink 25% outline; room name
as a 44 sp ink block centred on the room; room contents at 55%; bloom hidden; puck at 25%.
Tap a room to fly to it (600 ms). Enter/exit: tap room name, MAP, pinch past the specimen,
or the `M` key.

### Stage scrubber (Trace)
Row of stage names (mono-free: sentence case, 12 sp; current as an ink block; passed at
60%; upcoming at 32%; horizontally scrollable with fading ends), a "▶ play" text button at
the end, and a range slider beneath: 2 dp track at ink 18%, 14 dp violet thumb. Scrubbing
sets the stage continuously; the count in the header and the point on the box plot
follow.

### Box plot rows (Economy, Check, Trace header)
Row: glyph in the user's font (19 sp), plot, count (mono 11 sp, right; ink block if outlier).
Plot: log axis 3–3,000, powers-of-two gridlines at ink 8%; whiskers 1 dp at ink 45% with
3 dp end ticks; box ink 16%, 10 dp tall (12 dp when open); median 1.5 dp ink; the user's
point r 4.5 violet with a 2 dp canvas ring. Open row: 64 dp tall, the 30 faces as 2.6 dp
points at ink 50% with a 17-step vertical jitter, fewest and most named in mono 9.5 sp.
Sticky axis at the top with tick labels. Legend line under the style chips.

### Specimen grid (Home)
Six columns; cell: glyph 28 sp in the user's font, count beneath in mono 9.5 sp with the
severity shape; outlier count as an ink block; in-range at 60%.

### Lineages (Learn)
Era row (mono, name semibold, year muted; current at 100%, neighbours 80%, rest 50%,
horizontally scrollable with a fading end); stage of 200 dp with the sample letters "ago"
at a common x-height (font-size scaled per face so x-heights match) crossfading by
scrubber position; stress dial 64 dp top-right (circle at line colour, violet needle,
label mono 9 sp: "stress 30°", "vertical stress", "no stress axis"); range scrubber; caption
(name 15 sp + year, one sentence, three feature tags in violet mono, "made with …" in
muted). Identify-it: a word in an unseen face at 34 sp, three options, the chosen one with
a check, the give-away sentence.

### Report items (Check)
Severity shape (11 dp) + title (mono 12 sp) + one sentence + optional reviewer quote in
italic muted + a violet action. No dividers; 8–10 dp between items; groups titled in mono
uppercase.

### Capture cells
4-column grid, 10×6 dp gaps; glyph 32 sp with a −3…+3° rotation for the hand-drawn feel;
label in mono 10 sp at 70%; the two uncertain cells are ink blocks with canvas text.

## 4. Desktop and web

Same tokens. Frame: 46 dp top bar, 210 dp tool rail (icon + name + shortcut letter, current
as an ink block), canvas, 320 dp inspector column, 72 dp proof strip at the bottom with the
live word in the user's font; command palette 520 dp wide centred at 70 dp from the top;
panels fade into the canvas (linear gradients to transparent), no borders. The puck is
optional on desktop [CONFIRM]; the tool rail carries the shortcut letters. Keyboard: room
← →, map M, tools by letter, nudge arrows/shift, palette Ctrl/⌘ K, undo Ctrl/⌘ Z.

Web (Kotlin/Wasm) renders the same tree; the compile step calls the hosted endpoint and the
Ship room says so in one line.

## 5. Motion

- Room pan: 600 ms cubic-bezier(.2,.8,.2,1); the bloom moves with the same curve so it
  reads as fixed.
- Map in/out: 600 ms, same curve.
- Puck pin: 350 ms; icon roll 110 ms; radial fade 150 ms.
- Count collapse: 1.4 s, cubic ease-out on a log scale.
- Screen change: 350 ms fade.
- Lineages crossfade: linear with scrubber position; stress needle 300 ms ease.
- prefers-reduced-motion: all of the above become instant.

## 6. Accessibility

- Never colour alone: severity and state are shapes and words; layers are colour + line
  pattern; selection is an ink block.
- Contrast: chrome text ≥ 4.5:1 on every texture (muted text is the floor; check on
  blueprint).
- Targets ≥ 44 dp for finger; the puck 68 dp; inspector values are tappable at 44 dp height.
- Every glass control has a name for screen readers; the radial announces the current
  tool on each detent.
- Reduced motion honoured; haptics can be turned off.
