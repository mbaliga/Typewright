# Typewright V1: features, journeys and screen requirements

Status: V1 design spec, 25 Sep 2026. It sits beside `UI_SPEC.md` and the explorer, and it
replaces both wherever it says so (§1). The mockups are the Design canvas "Typewright V1
screens": one phone artboard per screen ID below. Their sources are in
`ui/v1-screens/*.dc.html`, where `Deck.dc.html` is the shared control deck. **Together with
this document they're the look source for law 6 for every V1 screen.** The explorer stays
the source only for what they don't show (the Lens, Overlay and Scrapbook tabs, and the
desktop frame). Every number in them comes from `fonts/HyleDeco-Regular.ttf` and
`data/node-economy-latin.json`.

Proposed UI faces (brief §15 item 6; OQ 9): **IBM Plex Sans and IBM Plex Mono** (OFL-1.1),
bundled. One family covers the sentences and the mono labels, and it fixes the web's
missing monospace. The mockups use them.

Application id: **`com.asoc.typewright`**.

---

## 1. Canon changes this spec makes

Record each of these in `TYPEWRIGHT_BUILD_BRIEF.md`, `UI_SPEC.md` and `CLAUDE.md`, in the
same PR, with this document as the written reason.

| # | Was | Now |
|---|---|---|
| C1 | Rooms switch by header swipe, edge marks and the map. The header carries the room name. | Rooms switch through the **control deck**'s section arc (§2). The header carries the page title only. Edge marks are removed. Two-finger swipe and ← → stay. |
| C2 | §6: "no rounded corners except the puck". | **Round means thumb instrument.** The puck, the two deck circles, the section arc and the toggle arc are round. Everything else stays square, flat and shadowless. |
| C3 | Law 8: never red as meaning. | **One red, one use.** Red (#D0342C on light grounds, #FF5A4E on dark) is only ever the fill of the deck's left circle in its **Close** state, with a white ✕. The ✕ shape carries the meaning; red reinforces it. Nothing else may be red, and themes can't reassign it (§3). |
| C4 | §5.3: grid, metric lines and grain dissolve near the glass. | **Construction stays visible.** Only the grid and the grain dissolve. Metric lines, guides and construction geometry (circles, offsets, skeletons, tangents, Hobby control polygons) are drawn at full strength everywhere, including under the glass. Draw's toggle arc has a Construction switch, on by default. |
| C5 | Meaning colours are fixed (violet selected, cyan snap). | The **roles** are fixed and the **values** are themeable (§3). Every role also has a shape, so colour is never the only signal. |
| C6 | Economy is a separate station. | Economy is a toggle inside Check (Report · Economy). |
| C7 | The command palette is on Ctrl/⌘ K only. | The palette is also the first item of every ⋯ menu, so touch users can reach it. |

---

## 2. The control deck

Every screen has the same deck at the bottom. From the bottom up:

```
            ( ‹ )   ◜ toggle · toggle · toggle ◝   ( ⋯ )      ← row 2: left circle · toggle arc · right circle
      ◜  Capture   Trace   [ DRAW ]   Space   Check  ◝         ← row 1: section arc
```

**Geometry.** The two arcs are concentric: one circle whose centre sits below the screen,
at radius ≈ 1.35 × screen width. The section arc spans the full width. The toggle arc is one
ring up (+58 dp) and spans only the space between the two circles. The circles sit on the
toggle arc's ring, at its ends. The circles are 52 dp. Both arcs are 44 dp tall bands. The
whole deck is 150 dp plus the bottom safe area. The bloom lies under the whole deck, so the
canvas dissolves behind it.

### 2.1 Section arc (row 1)

- The sections, in pipeline order: **Home · Capture · Trace · Draw · Space · Check · Ship ·
  Learn · Workbook**.
- The current section sits at the apex as an ink block with paper text (§5.4 of the brief).
  Its neighbours are plain ink text, fading with distance along the arc (100 / 70 / 40 %).
  About five labels show at once. Labels follow the arc's tangent.
- **Swipe along the arc** to rotate it. On release it settles, and the section at the apex
  opens (600 ms camera pan, the brief's curve). **Tap** a label to go there. **Long-press**
  opens the Map (S24).
- Keyboard: ← → move by section (unless the canvas has focus), M opens the Map.
- Draw and Space are regions of one sheet, so switching between them pans the sheet rather
  than changing screens.

### 2.2 Left circle: Back or Close

| State | Look | Used when | Does |
|---|---|---|---|
| **Back** | ink ring, ‹ glyph | the default | Goes up one level: a sub-view → its section. A section → Home. Home → Projects. Nothing is lost, because everything autosaves. |
| **Close** | red fill, white ✕ | a modal state, a flow that commits nothing until finished (new project, import audit, camera, theme editor with unsaved changes), or an edit mode | Leaves without committing. If there's something to lose, an ink-block confirmation appears on the glass first ("Discard 3 captured sheets?"), as §4.5 of the brief allows. |
| hidden | — | Projects (the root) | — |

System back (Android back, Esc, browser back) always does what the left circle does.

### 2.3 Right circle: ⋯ or the page's primary action

- By default it's **⋯**, which opens the page menu (S20): a list on the glass rising from
  the circle. The first item is always "Command…".
- It becomes the page's **primary action** where one action clearly dominates. The ⋯ items
  then move to a long-press on the circle.

| Screen | Right circle |
|---|---|
| Projects | **+** new project |
| New project | **→** next / **✓** create |
| Capture (sheets) | **+** add sheet (camera, gallery or file) |
| Camera | **shutter** (a filled ink ring). It arms itself once all four fiducials lock. |
| Trace | **✓** accept glyph, then go to the next one |
| Check | **↻** run again |
| Ship | **↑** build and write the repository |
| Theme editor | **✓** save theme |
| everything else | **⋯** |

The primary-action circle is filled ink with a paper glyph. The ⋯ circle is an ink ring. A
circle whose action is unavailable (Ship blocked by fails) stays visible at 35% and
explains itself on tap.

### 2.4 Toggle arc: page-level toggles only

- It holds only switches that change **how the whole page shows**. Never navigation, the
  heading, or one-off actions.
- A segmented group has 2–4 options, the selected one as an ink block. A binary switch is a
  word that becomes an ink block when on. Two groups at most, separated by a gap.
- If a page has no toggles, the arc shows as an empty hairline, so the deck's shape never
  changes.

| Screen | Toggles |
|---|---|
| Projects | Recent · All |
| Home | Specimen · Glyphs |
| Capture | Sheets · Cells |
| Trace | Simple · All stages ‖ Raster |
| Draw | Outline · Fill ‖ Construction |
| Space | Spacing · Kerning (‖ Before · After, in Kerning only) |
| Check | Report · Economy ‖ Plain · Raw (Raw shows only in Report) |
| Ship | Google Fonts · Own licence |
| Learn | Lineages · Overlay · Lens · Scrapbook |
| Workbook | This task · All tasks |
| Settings, About, Font info | (empty) |
| Theme editor | Light · Dark preview |

### 2.5 What stays on the glass above the deck

- **Header** (top-left): the page title at 15 sp semibold, with a mono subtitle beneath it
  (glyph, counts, file). No buttons.
- **Puck** (Draw, Trace hand-edit): pinned mid-height to the left or right edge. Never on the
  bottom edge now, because the deck owns it.
- **Inspector row** (Draw, Space): one row 12 dp above the deck.

### 2.6 Desktop and web width

At ≥ 900 dp the deck stays bottom-centre at a fixed 560 dp width. The tool rail and the
inspector column from UI_SPEC §4 stay. Keys: ← → sections, M map, Esc = left circle, Enter =
primary action where one exists, Ctrl/⌘ K palette.

---

## 3. Theme system (app-level)

**Settings → Appearance → Theme:** System (Paper by day, Black by night) · Paper · Vellum ·
Blueprint · Black · White · **Custom…**. The theme is app-level only, never per project.

### 3.1 Custom theme editor (S22)

A theme is a small JSON file (`themes/<name>.json` in the app's config), so you can export
it, import it and share it (law 7). What you can set:

| Group | Roles | Shape that always goes with the colour |
|---|---|---|
| Ground | canvas colour · grain (0–40%) · grid opacity · light or dark chrome (derived, overridable) | — |
| Ink | ink · muted | — |
| **Accent** | selection in live geometry, action links, the Trace scrubber thumb, the user's point on box plots | ink block for UI selection (never accent) |
| **Nodes** | on-curve corner · on-curve smooth · off-curve handle · handle line · selected node (defaults to accent) · snap target | square corner · round smooth · small hollow circle off-curve · thin handle line · selection ring · cyan-role crosshair |
| **Lines** | metric lines · guides · **construction lines** · grid | solid · long dash · short dash plus centre marks · dots |
| Comparison layers 1–4 | the Overlay and Space comparisons | solid · dashed · dotted · dash-dot |

The editor previews everything live on the Hyle Deco `o`, with its box plot, one check item
and the deck.

### 3.2 Guardrails (enforced, and explained in one line each)

- Text (ink and muted) must reach at least 4.5:1 against the canvas. Geometry colours must
  reach at least 3:1.
- Selected node, snap target and accent must differ from each other and from the node
  colours by at least 25 in OKLab lightness × 100 **or** ΔE ≥ 30. Otherwise the save button
  explains which two collide.
- No role may sit within ΔE 20 of the close red (C3). The editor offers the nearest
  allowed colour instead.
- "Reset role" and "Reset theme" always exist. Built-in themes are read-only: you
  duplicate one to edit it.

---

## 4. V1 feature inventory

| Area | Feature | Where | Built today? |
|---|---|---|---|
| Projects | New from drawings, open TTF/OTF/UFO, recent projects, autosave, reopen where you left | S01 S02 S03 | no |
| Projects | Import licence and attribution audit | S03 | no |
| Projects | Font info: names, version, vertical metrics, designer, licence | S06 | no |
| Capture | Print sheets with corner marks (new: 5 Latin sheets of 24). Export the existing SVG template pack (120 Latin, 112 Kana, 68 Devanagari, 49 Naskh) | S07 | the SVG pack exists; the sheets don't |
| Capture | Camera with fiducial lock (Android). Gallery, file or scan import (all targets). **SVG drawings named by template**, which skip the raster stages and start at Fit | S09 S08 | no |
| Capture | Perspective fix, cell detection, the uncertain-cell review grid | S08 | no (the chain after a clean raster exists) |
| Trace | The eight-stage chain as a scrubber, 3 stages by default. The count collapsing on a log scale with a live box plot. Accept locks the glyph | S10 | engine yes, screen no |
| Draw | Sheet, puck, radial, pen, Hobby curves, primitives, booleans, transforms with numeric entry, guides at any angle, layers, palette commands | S11 S12 | yes (the sheet) |
| Draw | Construction lines visible and toggleable | S11 | partly |
| Draw | Undo/redo everywhere. Unlock-to-edit with a stored diff (law 1) | S11 | no |
| Space | Sidebearings, kerning pairs, groups, .fea view, before/after | S13 | yes |
| Check | Layer two (always). Layer one where a runtime exists. A plain-language report with fixes | S14 | logic yes, screen no |
| Check | Economy box plots, worst-first, the 30 faces, style chips with n | S15 | logic yes, screen no |
| Ship | Compile to TTF locally. Write the Google Fonts repo folder, or a zip on web. Blocked while there are fails | S16 S17 | generators yes, compile no |
| Learn | Lineages, Overlay, Lens, Scrapbook on the open project | S18 | yes, but on Hyle Deco only |
| Workbook | 12 Latin tasks with real gates, reflections saved to the project; labelled **Early** | S19 | yes, in memory only |
| Scripts | Kana, Devanagari, Naskh in Draw and Space with shaping preview; labelled **Preview** | S04 S13 | yes |
| App | Settings, theme and custom theme, haptics, reduced motion, input hints | S21 S22 | no |
| App | About, licences, notices, privacy statement, support link | S23 | no |
| App | Map of sections, command palette | S24, palette | partly |

---

## 5. User journeys (V1)

Each step names the screen it happens on. A journey is done when a new user can finish it
on every V1 target without help.

**J1 · From paper to a font (the letterer).**
S01 → S02 (source: *Draw on paper*, brief: sans/display/geometric) → S07 print the Latin
sheets → draw them off-app → S08 **+** → S09 photograph, fiducials lock, shutter → S08 the
Cells view flags 2 uncertain cells, relabel them → S10 trace each glyph, scrub, **✓** →
S11 fix the `a` → S13 space `n o H O` → S14 run Check, 3 fails → fix them from their actions →
S16 **↑** build → S17 the build writes the repo folder.

**J2 · Rescue a font that failed review (the submitter; Hyle Deco's own story).**
S01 → open `HyleDeco-Regular.ttf` → S03 audit (OFL, reserved name none) → S04 the Specimen
shows 38 outliers → S14 Report: "Node economy · 38 of 52 basic glyphs are outliers" → S15
Economy: T 1,763 against a box of 8–12.5 → open the T row → "Tidy with live count" in S11
takes the T to 8 → S14 re-run → S16 build and write the repo.

**J3 · Learn why an `o` isn't good enough (the learner).**
S04 → the taskcard "Task 4 · Control characters" → S19 → the gate shows `o` failing →
"Compare" → S18 Overlay: your `o` against Jost and Work Sans with the redline probe → Lens
for the vocabulary → back ‹ → S11 on the `o` → S19 the gate passes and the reflection saves.

**J4 · Start a Devanagari font (the multi-script designer).**
S02 (scripts: Latin plus Devanagari, marked Preview) → S07 Devanagari sheets → capture and
trace → S13 shaping preview of conjuncts → Check reports layer-two items only, and says so.

**J5 · Come back tomorrow.**
Launch → S01 lists the project with its specimen strip in its own font → tap → it reopens
on the section and glyph where you left off.

**J6 · Make it yours (theming).**
S21 → Theme → Custom… → S22 duplicate Blueprint, set the accent to amber and construction
lines to dotted white, and the guardrail flags the snap colour as too close → pick the
offered colour → **✓** → the whole app re-themes live.

**J7 · Desktop power path.**
Ctrl K "open" → pick a UFO → V/P/S tool keys → `=xheight` in the inspector → Ctrl K "check" →
Enter builds.

---

## 6. Screen requirements

Each screen lists: **Purpose · Entry · Deck** (left / toggles / right, with the section
highlighted) **· Content · States · Actions · Data · Done when**. "Deck" rows take their
defaults from §2.

### S01 Projects
- **Purpose:** choose, open or start a project. The only screen without a section, and the
  app's root.
- **Entry:** first launch; Back from Home.
- **Deck:** left hidden; toggles Recent · All; right **+**. The section arc is dimmed to
  30% and inert until a project is open.
- **Content:** a header "Typewright" with the mono app version. Each project is a row with a
  specimen strip ("Hamburgefonstiv") rendered **in its own font**, then its name, the mono
  line "338 glyphs · 38 outliers · edited 2 h ago", and its folder path muted.
- **States:** empty (three ink-text choices: *Start from drawings* · *Open a font* · *Try
  the sample (Hyle Deco)*); missing folder ("moved or deleted, Locate…"); no storage
  permission yet on Android (asks through SAF, in one line).
- **Actions:** tap to open; long-press a row for a glass list (Reveal · Duplicate · Remove
  from list · Delete from disk, which is destructive and confirms).
- **Data:** the recent-projects list in the app config; each project's `typewright.json`.
- **Done when:** a project opens on the section and glyph where it was left (J5).

### S02 New project (3 steps on one screen)
- **Purpose:** create a project directory with a declared brief. This is Workbook Task 1
  done up front.
- **Entry:** the right **+** on S01; the S01 empty state.
- **Deck:** left **Close** (red ✕), which discards nothing on disk until Create; toggles
  empty; right **→** on steps 1–2, **✓** on step 3.
- **Content:**
  1. *Source:* Draw on paper · Open a font · Start blank.
  2. *Brief:* name; scripts (Latin, plus Kana · Devanagari · Naskh marked Preview); intent
     (serif / sans · text / display); the closest class as chips with their corpus n
     ("geometric sans · n 30"); an optional one-line note.
  3. *Metrics:* UPM 1000; x-height, cap, ascender and descender prefilled from the class
     median, each editable and each showing that median; the folder location.
- **States:** a name clash; a folder that isn't writable.
- **Data:** writes `<name>/` with `<name>-Regular.ufo`, `typewright.json` (the declared
  style) and empty `scrapbook/`, `lessons/` and `comparisons/` folders.
- **Done when:** the UFO passes ufoLib validation and the project opens on Capture (paper),
  S03 (a font) or Draw (blank).

### S03 Import audit (modal ink block)
- **Purpose:** the licence and attribution check on import (§4.5 of the brief). One of
  only two modal states in the app.
- **Deck:** left **Close** (cancels the import); toggles empty; right **✓** "Import".
- **Content:** the font's name, its licence as read (OFL 1.1 · Apache · proprietary ·
  unknown), its copyright line, any Reserved Font Name, its designer and vendor URL, then
  a verdict in words with a shape: ✓ fine to modify · ◐ modify but rename (a Reserved Font
  Name applies) · ● not licensed for modification (Import stays disabled, and the screen
  says why).
- **Done when:** the verdict for every OFL corpus face is ✓ or ◐ and correct, and a
  proprietary test font is refused.

### S04 Home (specimen)
- **Purpose:** the font as it is right now. The project's home.
- **Deck:** Home at the apex; left ‹ (to Projects); toggles **Specimen · Glyphs**; right ⋯
  (Font info · Templates · Export UFO · Project folder · Settings).
- **Content (Specimen):** the header (family, "Regular · 1.000"); script chips with
  coverage ("◑ Latin 116/324 · ◌ Kana"); the hero word at 54 sp in the user's font; a
  pangram; the counts line ("338 glyphs · 68,941 points · 0 curves · ● 38 outliers ◐ 7 above
  ✓ 7 in range"); the **taskcard** ("○ Task 4 · Control characters"); the "worst first"
  strip (the five heaviest glyphs with their counts); the pipeline line (Capture · Trace ·
  Check, each with its state).
- **Content (Glyphs):** a 6-column grid of glyph cells with each glyph's count and severity
  shape (UI_SPEC's specimen grid); filter chips (All · Outliers · Unfinished · Locked);
  search by character or name.
- **Actions:** tap a glyph to open Draw on it; tap the counts to open Check › Economy; tap
  the taskcard to open Workbook.
- **Done when:** every number comes from the open project, never the fixture.

### S05 (merged into S04's Glyphs toggle)

### S06 Font info
- **Entry:** Home ⋯.
- **Deck:** left ‹; toggles empty; right ⋯ (Reset to class medians).
- **Content:** family and style names; version; UPM; vertical metrics with a live
  line-spacing preview in the user's font; designer, URL, copyright; licence; the name-table
  preview ID 1/2/4/6/16/17. Mono labels, 15 sp values; each value opens numeric entry.
- **Done when:** the values round-trip through UFO fontinfo.plist and the compiled name
  table.

### S07 Templates
- **Entry:** New project (Draw on paper); Home ⋯; Capture ⋯.
- **Deck:** left ‹; toggles **Latin · Kana · Devanagari · Naskh**; right **↓** (export the
  sheets as a PDF).
- **Content:**
  - *Print sheets:* 24 cells a sheet with four corner marks, as thumbnails with their glyph
    ranges (Latin: 5 sheets); paper size (A4 · Letter); guides (light · none).
  - *SVG templates:* export the existing per-glyph pack (`scripts/templates`) for vector
    tools. Drawings come back through Capture › + › File.
  - A line saying the templates are CC0 and your drawings are yours.
- **Done when:** the printed sheets are detected by S09 at ≥ 95% cell accuracy (P16).

### S08 Capture
- **Deck:** Capture; left ‹; toggles **Sheets · Cells**; right **+** (Camera · Gallery ·
  File; on desktop and web, File only, plus drag and drop).
- **Content (Sheets):** one row per captured sheet (a thumbnail, "Sheet 1 · A–X", "2 need
  you · 22 read", and its source: photographed or a file). SVG drawings appear as one row per
  batch. Swipe left to remove, which confirms.
- **Content (Cells):** the 4-column cell grid (UI_SPEC's capture cells) with uncertain cells
  as ink blocks; tapping one opens a glass relabel list, sorted by how likely each glyph
  name is.
- **States:** no sheets (an ink-text hint plus the template link); a failed perspective
  fix, which names the missing fiducial and offers a retake.
- **Done when:** J1's capture steps pass on the fixture photos.

### S09 Camera (Android)
- **Deck:** section arc hidden; left **Close**; toggles **Flash** ‖ **Grid**; right
  **shutter**, disabled until all 4 fiducials lock. There's a manual override behind a
  long-press.
- **Content:** a full-bleed viewfinder; four corner marks that turn into ink blocks as each
  fiducial locks; a mono status line ("3 of 4 corners · hold steady").
- **Done when:** the owner-verified capture of a printed A4 sheet in room light succeeds.

### S10 Trace
- **Deck:** Trace; left ‹; toggles **Simple · All stages** ‖ **Raster**; right **✓**
  accept, then go to the next untraced glyph.
- **Content:** the header "T · 1,763 points", with the sheet and cell muted; the glyph's box
  plot, with the live count as the accent point; the raster underneath (Raster toggle);
  construction and metric lines at full strength; the scrubber (Clean · Fit · Review, or all
  eight), whose thumb is the accent; per-stage parameters (corner detection, fit tolerance,
  snap band) as inspector values; a one-sentence result per stage. "Hand edit" opens Draw on
  this glyph.
- **States:** a flat-topped round glyph (the brief's "overshoot does not apply" line); a
  locked glyph shows its lock and "Unlock to re-trace" (law 1).
- **Done when:** T goes 1,763 → 8, and o 80 → 16 on · 16 off, with the fixtures unchanged.

### S11 Draw
- **Deck:** Draw; left ‹ (or **Close** while an edit mode is active: knife, measure, or
  numeric entry); toggles **Outline · Fill** ‖ **Construction**; right ⋯ (Command… · Undo
  history · Unlock glyph · Layers · Guides · Add extremes · Tidy with live count · Reverse ·
  Correct direction).
- **Content:** the sheet with the grid (dissolving under the glass), metric lines with
  labels, **construction lines at full strength**, the glyph with its nodes in the node
  roles' colours and shapes, the puck pinned mid-left, and the inspector row above the deck
  (x · y · on · off · the selected node's type). Plus the glyph's live box plot as a thin
  strip under the header.
- **States:** a locked glyph (the outline muted, a lock in the header, and Unlock in the ⋯
  menu); an empty glyph ("Draw with the pen, or trace a cell").
- **Done when:** the P17 edit fuzz test passes, and the construction lines never fade.

### S12 Draw: the radial (a state of S11)
- The tool radial on a puck hold, or the object radial on a hold on a node or contour,
  exactly as UI_SPEC's radial dial. While it's open, the deck dims to 30%.

### S13 Space
- **Deck:** Space; left ‹; toggles **Spacing · Kerning** ‖ **Before · After**; right ⋯
  (Groups · .fea · Control strings · Shaping preview).
- **Content:** the control string in the user's font, with sidebearings as numbers under
  each glyph (Spacing) or pair values between glyphs (Kerning); the selected pair as an ink
  block; the inspector (left · right · kern · group).
- **Done when:** kerning groups and .fea round-trip through the UFO.

### S14 Check: Report
- **Deck:** Check; left ‹; toggles **Report · Economy** ‖ **Plain · Raw**; right **↻**.
- **Content:** the counts ("4 fail · 4 warn · 6 pass" of the 14 layer-two checks on
  Android and web; plus layer one's count on desktop), with a severity bar (solid, then a
  hatched pattern, never colour); groups (Outlines · Metrics · Provenance · Names · Licence); each item as a
  severity shape, a mono title, one sentence, the reviewer quote, and an accent action; a
  last section, "What these checks do not measure"; a runtime line ("layer one: Fontspector
  0.x on this computer", or "layer one needs the desktop app" on Android and web, in words).
- **Done when:** J2 passes, and the fail count matches a direct fontbakery run on desktop.

### S15 Check: Economy
- **Deck:** as S14, with Economy selected. Plain · Raw is hidden. Right **↻**.
- **Content:** style chips with n ("geometric sans · 30"); a sort order (Worst first ·
  A–Z) inside the page; a sticky log axis (3 → 3,000); rows as in UI_SPEC's box plot rows; an
  open row with the 30 faces and the fewest and most named; the legend and the fence
  sentence.
- **Done when:** the real sans-geometric data shows T 1,763 against 8 / 8 / 8 / 12.5 / 28
  (min / Q1 / median / Q3 / max) and the verdict "outlier".

### S16 Ship
- **Deck:** Ship; left ‹; toggles **Google Fonts · Own licence**; right **↑** build (at 35%
  while Check has fails).
- **Content:** a checklist of shapes and words: Check has no fails · names are unique ·
  OFL.txt with your copyright line · DESCRIPTION.en_us.html · the METADATA and upstream
  files · compiled on this device. The target folder, and a zip on web. The **Own licence**
  toggle drops the Google Fonts-only items and lets you pick a licence (OFL · proprietary ·
  other) and a vendor ID.
- **States:** blocked (lists the fails, each linking to Check); web preview ("builds need
  the desktop or Android app for now", with Export UFO + folder as a zip).
- **Done when:** the written folder passes fontbakery's googlefonts profile with no FAIL.

### S17 Ship: building (a state of S16)
- **Deck:** left **Close** (cancels the build safely); toggles empty; right hidden.
- **Content:** a streamed step list (make the UFO · fontmake · re-run Check on the binary ·
  write the repo), each ○ → ◐ → ✓ or ●; the elapsed time; errors in plain language with
  "Show fontmake output".
- **Done when:** cancelling leaves the project untouched, and a failure names its cause.

### S18 Learn
- **Deck:** Learn; left ‹; toggles **Lineages · Overlay · Lens · Scrapbook**; right ⋯.
- **Content:** P6's four tabs as they are today, but on the **open project**, and the
  tab row moves into the toggle arc. With no project open, Overlay and Lens say "open a
  project to compare your own letters". The mockup shows Lineages. Overlay, Lens and
  Scrapbook keep the explorer's look.
- **Done when:** J3 passes on a font other than Hyle Deco.

### S19 Workbook
- **Deck:** Workbook; left ‹; toggles **This task · All tasks**; right ⋯ (Reset task · About
  the lessons).
- **Content:** the header "Task 4 · Control characters" with an **Early** chip; why ·
  demonstration · task · gate (real) · reflection. "All tasks" is the progress list (done /
  current / todo). "Trace again with Fit" goes to S10.
- **Done when:** the reflections persist in `lessons/` across restarts.

### S20 The ⋯ menu (a state of any screen)
- A column rising from the right circle on the glass. Rows are mono labels with their
  shortcut letters; "Command…" comes first; destructive rows sit last, as ink text with a ●
  mark. Tap outside, or the left circle (now **Close**), dismisses it.

### S21 Settings
- **Entry:** Home ⋯; S01 ⋯ (long-press the +).
- **Deck:** the section arc keeps the last section, dimmed; left ‹; toggles empty; right ⋯
  (Export settings · Import settings).
- **Content, in groups:**
  - *Appearance:* Theme; Custom…; puck side (left / right); UI text size.
  - *Input:* haptics on or off; hold delay; the finger loupe on or off; stylus hover
    snapping.
  - *Motion:* reduced motion (follows the system, can be overridden); ambient room tone
    (off by default, [SCAFFOLD]).
  - *Building:* the compile runtime status (desktop: python3 and fontmake versions, found or
    missing with the pip command); layer-one runtime status.
  - *Storage:* the default projects folder.
  - *About & licences.*
- **Done when:** every setting persists and applies without a restart.

### S22 Theme editor
- **Deck:** left **Close** when there are unsaved changes, otherwise ‹; toggles **Light ·
  Dark** preview (for a theme duplicated from System); right **✓**.
- **Content:** a live preview at the top (the `o` with nodes and construction, a box plot
  row, one check item, a mini deck), then the role groups from §3.1 as rows (a swatch, a
  role name, a hex, and the paired shape shown). The guardrail messages appear inline under
  the offending row, and the ✓ circle stays dimmed until the collision is resolved.
- **Done when:** the guardrails block a collision, and an exported theme reimports
  identically.

### S23 About & licences
- **Deck:** left ‹; toggles empty; right ⋯ (Copy version info).
- **Content:** the name and version; "A System of Cells"; the Typewright licence in one
  sentence (FSL-1.1-ALv2 app; Apache-2.0 engine; your fonts are yours); the privacy
  statement ("Typewright collects nothing. The only network use is what you ask for:
  fetching a comparison font, and builds you start."); a support link, on desktop and web
  builds only (see LICENSING); third-party notices (every THIRD_PARTY.md entry plus the
  Skia and FreeType notices); fonts used and their licences.
- **Done when:** the notices are complete against THIRD_PARTY.md, and a CI check fails
  when a dependency lacks an entry.

### S24 Map (a state)
- **Entry:** long-press the section arc; pinch out past the specimen; M.
- **Content:** UI_SPEC's map, with the sections as 44 sp ink blocks. Tap one to fly there.
  The deck shows only the left **Close**.

---

## 7. Out of V1 (don't design yet)

GitHub push; hosted builds; nib tools; interpolation and masters; Derive (M6); the vertical
margin and proof axis; iOS; ambient sound content.

## 8. Small things for Madhav to confirm on the mockups

1. The section order: pipeline first, then Learn and Workbook at the far end of the arc.
2. Whether Home belongs on the arc, or only behind Back.
3. The exact close red: #D0342C (light) and #FF5A4E (dark) are proposals.
4. Whether the primary-action circle should be filled, to tell it apart from the ⋯ ring.
