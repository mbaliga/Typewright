# Typewright V1: interaction model

Status: V1 canon, 25 Sep 2026. It sits beside `docs/SCREENS_V1.md` and replaces UI_SPEC §3's
radial dial and the brief's §5.2 and §5.5 wherever they differ. The mockups are the boards
Draw, Radial · sweep (`DrawRadial`), Radial · lift and tap (`RadialTap`), Rich context
menu · touch (`DrawMenu`) and Desktop · Draw (`DesktopDraw`), in `ui/v1-screens/`.

Two principles lead, because most tools treat touch as a desktop with a fat cursor. Here a
finger, a stylus and a mouse with a keyboard each get the interaction that suits them,
running on one command set.

---

## 1. Principle T: touch-native, not touch-tolerated

| # | Rule | What it means in the app |
|---|---|---|
| T1 | **Never under the thumb.** Whatever shows the result of a touch appears away from the contact point. | The loupe sits above the finger and carries the x, y readout. The radial highlights the side away from the thumb (§3). Drag previews and snap labels are offset. Values being scrubbed read out above the finger. |
| T2 | **Every press finishes two ways.** Keep moving without lifting (one fluid gesture), or lift and choose (a deliberate tap). | The radial (§3); the puck (swipe, or tap to unfold); node grabs. |
| T3 | **Precision comes from motion, not tiny targets.** Targets are ≥ 44 dp. Fine control comes from zoom, offset grabs and relative movement. | The loupe, precision drag (§4.3), inspector scrubbing, and the tension handle (one big target instead of two handles). |
| T4 | **Two hands can chord.** The puck works as the modifier key. | Hold the puck and drag with the other hand to constrain. Hold the puck and drag on empty canvas for precision relative movement (§4.3). |
| T5 | **Every gesture has a visible teacher.** | Menus show each command's trigger for the input in use (§2.3), so "hold · sweep ↘" is learned the way ⌘Z is. |
| T6 | **The input is decided per event, not per device.** | On a tablet with a keyboard, or a Chromebook, the last pointer type (finger, stylus or mouse) decides the offsets, the loupe, the radial mapping and which trigger chips show. It switches live. |
| T7 | **Haptics confirm what the eye can't see.** | A snap engaging, radial detents, a commit. Never decoration, never for a change the user didn't cause. |

## 2. Principle S: surfaces, not boxes

| # | Rule |
|---|---|
| S1 | **No bounding boxes, rules, dividers or card outlines**, except the control deck's arcs. Separation comes from type (size, weight, case, mono against sans), tone (paper shifts, ink density), whitespace and the bloom. |
| S2 | **No global grid.** The grid belongs to the glyph's design space: it appears only around the glyph being edited, densest at the letter and dissolving radially into paper. Screens without a glyph under edit have no grid. |
| S3 | **Lines are materials.** Metric lines, guides and construction lines extend past the glyph and dissolve at their own ends, never cut off hard at the screen edge. They don't fade near glass (canon change C4); they fade only at their ends. |
| S4 | **One continuous sheet.** The paper grain carries across screens and transitions. A change of context shifts tone, not frame: the camera is a black field, a modal is an ink block. Transitions morph shared lines: Trace's x-height line *becomes* Draw's, and the specimen's baseline becomes Space's. |
| S5 | **A panel is a tonal field.** A menu or inspector sits on paper about 3% darker in L, with soft edges and no outline. Selection is always an ink block. |

These replace UI_SPEC §1's layer 2 (a 28 dp grid everywhere) and the map's room outlines.

---

## 3. The radial menu

**Opens:** a 380 ms hold on an object (a node, segment, contour, glyph or empty canvas),
or on the puck (tools). The ring centres on the touch point, shifted inward so the whole
ring clears the screen edges by 12 dp and the deck by 16 dp. The edge-pinned puck opens a
**half-ring** (a fan) facing into the screen.

**Contents:** the 8 most-used commands for that object type, from the command registry (§5).
Users can reorder them in Settings → Input.

### 3.1 Two ways to finish, both always available

**A. Sweep: keep the finger down.**
- Moving the finger past the **24 dp dead zone** around the hub selects the sector
  **diametrically opposite the finger**: finger angle θ selects the sector at θ + 180°. On
  a half-ring, it's the finger's angle mirrored across the fan's axis.
- The selected sector becomes an ink block at 106% scale. Its label and trigger chip are
  set outside the ring on the far side from the thumb. A hairline needle runs from the
  finger, through the hub, to the selection.
- The sectors under the thumb fade to 40%; they're covered anyway.
- 5 ms detent on each change. **Release commits** (10 ms). Moving back into the dead zone
  clears the selection, and releasing there cancels.
- Why: the thumb hides the half of the ring it points at. Selecting the far side keeps the
  choice in full view and turns the menu into a flick: "hold, sweep ↘" is always
  *Smooth ⇄ corner*, and it becomes muscle memory.

**B. Lift, then tap.**
- Releasing inside the dead zone without having swept leaves the ring **open and still**,
  with **direct** mapping: tap a sector to commit it.
- Each sector shows its label and its trigger chip. The hub becomes **More…**, which opens
  the rich menu (§5) for the same object.
- Tap outside, or the deck's left circle (now the red ✕ Close), to cancel. It never times
  out.

### 3.2 Other inputs

| Input | Behaviour |
|---|---|
| Stylus | Sweep uses the opposite mapping too, because the hand covers the ring. In the lifted state, hover highlights the sector under the tip. |
| Mouse | **Right-click opens the rich menu, not the radial.** A right-button hold and drag gives a marking menu with **direct** mapping, since a cursor covers nothing. Release commits. |
| Keyboard | With the ring open, keys 1–8 pick sectors clockwise from the top. Esc cancels. |
| Screen reader | The ring opens in the lifted state. Swipe moves between sectors, each announced with its trigger; double-tap commits. |

The rotating ring and fixed 12 o'clock marker (UI_SPEC §3) are retired.

---

## 4. Editing on a phone (best in class, V1)

### 4.1 Gesture vocabulary on the canvas

| Gesture | Does |
|---|---|
| Tap a node or segment | Select it. Tap empty canvas to deselect. |
| Double-tap a contour | Select the whole contour. Double-tap empty canvas: fit the glyph. |
| Hold a node, then drag | Grab it with a fixed offset so the finger never covers it. The loupe shows the point, the snap candidates and x, y. |
| **Drag a curve segment** | Bend the curve directly: handle angles stay, their lengths change. This is the biggest target on the screen, and usually the only one you need. |
| **Drag the tension point** | Each selected curve shows one tension point on its tension (Tunni) line. Dragging it changes both handles in proportion. Double-tap it to even the tension. One target in place of two. |
| One-finger drag on empty canvas | Lasso selection. |
| Two fingers | Pan and zoom, always live. |
| Two-finger tap / three-finger tap | Undo / redo. |
| Hold anything | The radial for that thing (§3). |

### 4.2 Snapping

Snapping is on by default. The target turns cyan with a crosshair. A 6 ms tick confirms the
snap. The snap label ("x-height 500") sits in the loupe, never under the finger. Dragging
past the snap band releases it. There's no hidden "hold still to escape".

### 4.3 Precision without tiny targets

- **The puck as a modifier (a two-hand chord):** put a thumb on the puck, then touch the
  canvas with the other hand within 380 ms. The second touch cancels the puck's radial
  timer. While the puck is held:
  - dragging a node or segment is constrained to horizontal, vertical or 45°;
  - dragging on empty canvas is **precision relative movement**: the selection moves at a
    quarter of the finger's speed, from wherever the finger is, like a trackpad.
- **Inspector scrubbing:** drag sideways on any value, 1 unit per 8 dp (a quarter of that
  while the puck is held). Tap it for a keypad with `=` expressions (`=xheight`,
  `=o.rsb+4`).
- **Nudge readout:** every move shows its delta ("Δx +3") above the finger.

### 4.4 What a phone never needs

Tiny handles, a hover state, a right-click, or text menus for tools. Tools live on the puck.
Object actions live in the radial and the rich menu.

---

## 5. Rich contextual menus

Every contextual surface uses the same menu. That's the radial's **More…**, the deck's ⋯
circle, a right-click on desktop, and the command palette.

**Anatomy, top to bottom:**
1. **Subject line:** what it acts on ("1 node · 427, 382 · smooth", or "o · outer contour ·
   8 on · 8 off").
2. **Find field:** type to filter. On touch it's there without opening the keyboard until
   tapped.
3. **Recent:** the three commands last used on this kind of object.
4. **Sections by scope:** This node · This contour · This glyph · Edit.
5. **Row:** an 18 dp icon where one exists; the verb; the **consequence** (the live point-count change,
   "−1 on · −2 off", or the effect, "smooth → corner", or "no change"); then the **trigger
   chips**, right-aligned.
6. **Disabled rows stay visible**, muted, with their reason ("already has its extremes").
7. **Destructive rows go last**, marked ●. They only confirm (with an ink block) when undo
   can't restore the result.

**Trigger chips follow the last input (T6):**
- finger: the gesture, e.g. `hold · ↘` (its radial sweep) or `2-finger tap`;
- stylus: the gesture, plus the barrel button where one is assigned;
- mouse or keyboard, or any time a hardware keyboard is attached: the key chord, e.g.
  `Ctrl ⇧ E`. Shown as ⌘ on macOS later.

When a keyboard is attached to a touch device, both chips show, with the gesture first.

**One registry drives all of it.** `CommandRegistry` holds each command's id, label, scope,
availability predicate with reason, consequence estimator (it runs the real operation on a
copy, so the point-count change is measured, not guessed: law 5), and bindings (a radial
slot, gestures, a key chord). The radial, the rich menu, the palette, the tooltips and the
shortcut sheet are all views of it.

---

## 6. With a mouse and keyboard (desktop and web)

| # | Rule |
|---|---|
| D1 | **Hover is information.** Nodes, segments and snap candidates highlight under the pointer. After 600 ms a tooltip gives the command and its shortcut. |
| D2 | **Direct pointing.** No offsets and no loupe: the cursor is exact. |
| D3 | **Right-click opens the rich menu; right-drag gives a marking menu** (direct mapping). |
| D4 | **Shortcuts are visible everywhere:** in menus, tooltips, the tool rail, the palette, and a shortcut sheet (`?`). |
| D5 | **Modifiers while dragging:** ⇧ constrains to axis and 45°; Alt breaks handle symmetry, or duplicates when dragging a contour; Ctrl is the temporary select tool; hold Space to pan. |
| D6 | **Numeric fields** take `=` expressions. ↑ ↓ step by 1 (⇧ 10); Tab moves between fields; Enter commits; Esc reverts. |
| D7 | **Layout:** the tool rail (icon, name, key) on the left, the canvas, the inspector column on the right, and the same deck, centred under the canvas. The puck is hidden by default on desktop, with a setting to show it. |

### 6.1 Key map (V1)

Tools follow the brief. Where Glyphs or RoboFont have a convention, the key map follows it.
It shows Ctrl on Linux, Windows, ChromeOS and Android keyboards, and ⌘ on macOS later.

| Command | Keys | | Command | Keys |
|---|---|---|---|---|
| Select | V | | Toggle smooth ⇄ corner | Enter |
| Pen | P | | Delete node, keeping the curve | Backspace |
| Hobby curve | H | | Add extremes | Ctrl ⇧ E |
| Primitives | S | | Tidy, with the live count | Ctrl ⇧ Y |
| Boolean | B | | Correct path direction | Ctrl ⇧ R |
| Stroke | K | | Reverse contour | Ctrl Alt R |
| Measure | M | | Split contour here | Ctrl ⇧ K |
| Anchors | A | | Next / previous node | Tab / ⇧ Tab |
| Metrics | T | | Nudge 1 / 10 / 100 | ← ↑ → ↓ / ⇧ / Ctrl |
| Command palette | Ctrl K | | Undo / redo | Ctrl Z / Ctrl ⇧ Z |
| Map | M, when no tool has focus; otherwise Ctrl M | | Fit glyph / 100% | Ctrl 0 / Ctrl 1 |
| Lock glyph | Ctrl L | | Duplicate contour | Ctrl D |
| Break handles (make them independent) | Ctrl ⇧ B | | Align node to a metric line | Ctrl ⇧ A |
| Make tangent | Ctrl ⇧ T | | Shortcut sheet | ? |

Measure (M) and Map clash. Map moves to **Ctrl M**, and SCREENS_V1 §2.6 changes with it.

---

## 7. One action, three hands

| Action | Finger | Stylus | Mouse + keys |
|---|---|---|---|
| Pick a tool | Swipe, tap or hold the puck | Same | Letter key, tool rail |
| Object actions | Hold → radial (sweep or tap) → More… | Same | Right-click → rich menu; right-drag → marking menu |
| Move a node | Hold and drag, offset, loupe | Touch and drag | Drag |
| Constrain | Hold the puck + drag | Barrel button + drag | ⇧ + drag |
| Fine move | Puck + drag on empty canvas (¼ speed); inspector scrub | Same | Arrows, ⇧ arrows |
| Bend a curve | Drag the segment, or the tension point | Same | Drag the segment; Alt-drag a handle |
| Snap preview | During the drag, in the loupe | On hover | On hover |
| Undo / redo | 2-finger / 3-finger tap | Barrel double-click | Ctrl Z / Ctrl ⇧ Z |
| Find any command | ⋯ → Command… | Same | Ctrl K |
