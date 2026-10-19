# Amendment 1 to the Brief fold

For the Claude Code session that built PR #6 (`claude/fold-brief-ethos`, head `3ed1eda`). Sonnet is enough. Written 19 Oct 2026 from the owner's review.

**This amends; it doesn't restart.** Everything PR #6 built stays unless an item below names it:
- WP1–WP6: one record, one reader, confirm, S02 step 2, Task 1 meets References, docs;
- WP0a–c: the canvas boards checked in, and the prototype and the Compose screen restyled;
- `ControlDeck` and `DeckGeometry`.

Work on the same branch, one commit per item group (A–F), and keep every "Do not lose" item from `docs/HANDOFF_BRIEF_FOLD.md` and every test it lists green.

Read first: pack 1's `ui/typewright-explorer.html` (open its Notes per screen), `UI_SPEC.md`, `TYPEWRIGHT_BUILD_BRIEF.md` §2, §4–§6 and §8–§9, `docs/TYPEWRIGHT_HANDOFF.md` M4–M6, `docs/KNOWLEDGE.md` F1–F4, `docs/RESEARCH_font_quality.md` "Anatomy, critique and sequence", `docs/SCREENS_V1.md` §2. For the deck, also read the owner's Bocal repository (github.com/mbaliga/Bocal), files `web-source/app/context-seats.ts`, `context-scroll.ts`, `page.tsx` (the `mobile-dock`) and `globals.css` (`.dock-*`, `.arc-*`).

## What changed in the owner's direction

1. **The explorer is the look.** Pack 1 is what the owner has verified by eye. Where `ui/typewright-explorer.html` (and `UI_SPEC.md`, which describes it) and the V1 canvas boards disagree on how something looks, the explorer wins. The canvas still governs what only it shows: the S02 layout, the other V1 boards, and the deck's geometry. Those are drawn in the explorer's tokens. This reverses the "canvas wins" table that the previous revision added to the handoff; that table's other rows still stand.
2. **The arc deck stays, and it has to earn its place.** Each part of it has one job, and none of them is decoration:

   | | Left | Centre | Right |
   |---|---|---|---|
   | **Row 2: this page** | **leave**: Back up one level, or Close (red) only where leaving discards something | **toggle arc: which view of this page** (level 2) | **do**: the page's one dominant action, or ⋯ |
   | **Row 1: the app** | | **section arc: where you are in the app** (level 1), live | |

   Read bottom to top it goes app, then page. Read left to right it goes leave, look, act. If a slot has no job on a page it says so: it's hidden, or the arc drops to a hairline. It never shows a control that does nothing.

## A. The deck, made meaningful

Bocal solved the same problem. Its upper pill holds two seats per workspace, and every seat drives something the workspace already has. Borrow its rules, not its look: Bocal is soft, shaded and dark; Typewright stays flat, square and ink.

**A1. Level 1, the section arc, is live** (SCREENS_V1 §2.1, which PR #6 left as a drawing).
- Each section label is a hit target at least 44 dp tall, and a screen reader names it. Tapping a label goes to that section.
- Swiping along the arc rotates it. On release it settles, and the section at the apex opens. Long-press opens the Map (S24).
- Keys: ← → step between sections, digits 1–9 jump straight to one (as Bocal's dock does), and M opens the Map. These keys are ignored while a text field, a list or a radio group has focus, or anything else that uses the arrows itself. Bocal shipped a bug where its global arrow keys stole those (see the comment above its keyboard effect in `page.tsx`).
- The current section is an ink band with its label in paper colour. It is announced as the current page.
- Each label carries the station's status shape from the explorer's Home stations (● to refit, ○ not started, ✓ done), so this level also tells you where the work is.
- On Android, rotation ticks 5 ms at each section and 10 ms when it settles (UI_SPEC §3, radial detents). Reduced motion settles instantly.
- **The Brief sits under the section it was opened from**: Home, or Workbook when opened from Task 1. That section is current and live, not dimmed. Leaving to another section keeps the answers (`BriefStateKeeper`).
- Sections are dimmed only in a flow that commits nothing until it finishes. Here that is S02, new project, as the canvas draws it. A tap on a dimmed section says why, in one line on the glass ("Finish or close the new project first"). It doesn't silently do nothing.

**A2. Level 2, the toggle arc, holds this page's views, and its seats come from one place.**
- Add a pure Kotlin module, `ui/deck/DeckSeats.kt`, modelled on Bocal's `context-seats.ts`. It is the only place that says, for each page and state, which seats the arc shows and what the two circles are.
- **Each seat does one of three things:**
  - switches a view the page already has;
  - scrolls to a section the page already has, then moves focus to that section's heading (Bocal's `revealSection`);
  - opens another page (Bocal's `opens`), shown visibly as a seat that goes somewhere.
- **The rules:**
  - Seats come in at most two groups of 2–4.
  - Labels are 10 characters at most.
  - The first seat is the default.
  - Each page remembers its last seat. Anything stored that the page no longer has is dropped on load (Bocal's `sanitiseContextSeats`).
  - If the page is in a state that no seat names, no seat reads as pressed. That is the truth (Bocal's `resolveContextSeat`).
  - A page's own controls and the arc stay in step, both ways, without one overwriting the other on first show (Bocal's `useSeatSync`).
- Amend SCREENS_V1 §2.4 to match: "views of this page" are allowed; going to another page is not, except through an `opens` seat.
- **The Brief:** Ask · Brief · Guide, **always**, at the doors too, with Ask pressed. PR #6 drops the toggles at the doors, which changes the deck's shape and hides the other two views.
- **S02, new project:** the canvas board's three in-page step tabs (Source · Brief · Metrics) move onto the arc as seats. A later step can be chosen once the earlier ones are valid. The in-page tab row goes (CLAUDE.md: no screen adds its own tab row).
- Each toggle hit target is at least 44 dp tall. PR #6 uses 24 (`TOGGLE_HIT_HEIGHT`), below the floor in UI_SPEC §6.

**A3. The left circle leaves** (SCREENS_V1 §2.2).
- It is **Back** by default, also at the Brief's doors. Back goes to the previous question, then out to the section the Brief was opened from.
- It is **Close** (red, white ✕) only in S02, and anywhere else leaving discards something uncommitted. If something would be lost, an ink-block confirmation appears on the glass first (build brief §4.5).
- PR #6 shows Close at the doors, where leaving loses nothing, because the answers autosave. Change it to Back.
- System back (Android back, Esc, browser back) always does exactly what this circle does.

**A4. The right circle acts** (SCREENS_V1 §2.3). It becomes the step's dominant action, and that action is removed from the page, so there is one place for it:

| Brief state | Right circle | Removed from the page |
|---|---|---|
| Doors | ⋯ | — (PR #6's dimmed "Next" to the Guide goes: it skipped the doors) |
| Single-choice question | ⋯ (a tap on an answer already moves on) | — |
| Several-choice question (uses, feelings, references) | ✓ Done | the "Done" button |
| Wordmark | ✓ Set | "Set wordmark" |
| Mirror, after a reading | ✓ Use these answers | "Use these answers" |
| Mirror, nothing drawn | → Start drawing | "Start drawing" |
| Revisiting one answer (focus) | ⋯ (Back is "Keep my answer") | — |
| Wrap-up | → Draw first (or Draw next) | the Draw first/next action; the letters stay shown as the demonstration (C2) |
| Brief tab | ⋯ | — |
| Guide | → Start drawing | the last "Start drawing" |
| S02 | → Next, then ✓ Create on the last step | — |

- A filled circle means a primary action; a ring means ⋯. A primary action that can't run yet stays visible at 35%, and a tap explains why in one line.
- **⋯ opens the page menu.** Its first item is always **Command…** (C7; PR #6 leaves it out). Then, for the Brief: Try another door, Start over (with an ink-block confirmation), Read the brief, See your guide.
- When the circle is a primary action, the ⋯ items move to a long-press on it.

**A5. Bocal's finishing details.**
- A circle with no job is hidden. It never looks like a button that does nothing: Bocal had to restyle its lock circle as a flat badge for exactly this reason (the "IA fix" comment in `globals.css`).
- Every deck control gets a visible focus ring.
- The page fades out under the deck (PR #6 already does this). Keep it.

**A6. Tests.**
- `DeckSeatsTest` walks every Brief state and S02 step and checks:
  - the seats obey the rules above;
  - every seat's target exists;
  - the left circle is Close only where something can be lost;
  - the right circle's action is reachable and appears nowhere else on the page.
- `ControlDeckScreenshotTest` adds the live section band, a dimmed section's explanation, the ✓ and → circles, and the 35% state.
- The prototype gets the same deck behaviour. `walk.js` taps every section label, uses only deck circles for Done, Set, Use, Draw first and Start drawing, and still prints "NO DEAD ENDS".

## B. The explorer's look, where PR #6 follows the canvas or invents

Use the explorer's CSS values: the base sheet plus its "brutalist pass" block, the second `<style>` element.

| Element | PR #6 now | Change to (explorer) |
|---|---|---|
| UI type | IBM Plex in the prototype; the app's default elsewhere | System UI sans for sentences (13 px, line-height 1.45) and system mono for labels, numbers and chips (UI_SPEC §2). Inter stays [CONFIRM]. Keep the IBM Plex files only for the canvas boards. Close OQ 140 this way. |
| Header | `PageHeader`: 15 sp semibold title, upper-case mono subtitle, top 56 | The explorer's `.bar` without its buttons (the deck has them): `t1` 14/600, `t2` 11 px mono in sentence case, padding 12 · 14 · 16 below the status bar, fading into the canvas below. |
| Page body | 22 dp sides, scrolls behind the deck | `.scroll`: 18 dp gutter, padding 4 · 18 · 28, bottom edge fading over 30 px; the deck's own fade continues it. |
| Labels | `SectionLabel` | `.lbl`: mono 10 px, 0.14 em, upper case, muted. (Already close; check the values.) |
| Choices in a row (S02 Intent, the Brief's uses and feelings) | canvas `chip`: mono 10 px upper case | `.seg` / `.styles`: sans 12 px, muted. On = an ink block (padding 2 × 6, margin −2 × −6), semibold. A count is a muted `small`, which turns paper-coloured at 70% when on. |
| S02 Closest class | canvas chips | The explorer's Economy `.styles` row ("Geometric sans 30"), with the legend line below it. |
| S02 Scripts | canvas chips | The explorer's Home `.schips`: a severity shape plus the coverage ("◐ Latin 116 / 324", "○ Kana", "+ script"). |
| Buttons | mono upper-case labels; secondary has a 1 dp outline | `.btn.primary`: an ink block, square, padding 7 × 12, sans 12.5 / 600. `.btn`: violet text, 600. `.btn.quiet`: muted text. **No outlined buttons; no mono or upper case on buttons.** The primary action usually lives on the right circle now (A4). |
| Text fields (reference search, wordmark, S02 name) | an underline | `.fld` / `.wordf`: a mono label beside or above, the value in 15 px, no box and no rule; focus shows the 2 px violet outline. |
| Tensions (`TensionCard`) | a 2 dp left rule | A Check report item (`.it`): an 11 dp severity shape (○ info for a tension), a mono 12 px title, one sentence, then violet actions ("Keep as signature ›", "Change answer ›"). No rule, no box; 8–10 dp between items. |
| Reading (`ReadingSection`, Mirror) | prose | The explorer's Economy `.reads`: "Reads as <genre>" as an ink block, the confidence in muted 11.5 px ("0.74 · art deco 0.58"), then "measured: contrast 1.00 (monoline) · circular o · single-storey a · …" in muted 11.5 px. |
| Targets (`TargetsTable`, the Guide's drift items) | words | Box-plot rows (`.erow`): the glyph or feature name, the plot (box at ink 16%, 1 dp whiskers at 45%, median, your value as a violet point with a 2 dp paper ring), and the value on the right. The legend line goes above the rows. See C3. |
| Draw first / the letters | a glyph row | The workbook's `.demo`: the letters at 56 px with a muted 11 px caption beside them (C1). |
| Brief tab | its own layout | The workbook task frame (C5): `.lbl` "Task 1 of 12", an 18 / 600 heading, the 12-segment progress line (done = ink, current = violet), then `.wsec` sections. |
| Confirmations (Start over) | inline | An ink block on the glass (build brief §4.5). |
| Selection anywhere | ink block | Unchanged: an ink block with paper text (brief §2.10). |

- **Textures:** all five (paper, vellum, blueprint, black, white), with the meaning colours each one defines (blueprint violet is #B3A6FF). Screenshots and the prototype walk cover all five, not just light and dark. Check that muted text stays at 4.5:1 or better on blueprint.
- **Motion:** screens change with a 350 ms fade; reduced motion makes it instant.

## C. The build pack's behaviour, which the Brief still misses

These were in the handoff's "The build pack's canon" section and WP7, added after PR #6 started. They're restated here so this file stands alone.

- **C1. Draw n o H O first.** The control set comes first: the research's strongest consensus, and workbook Task 4.
  - The wrap-up's demonstration and the right circle's "Draw first" are always `n o H O`.
  - The genre's own letters go on a second line, "then what makes it <genre>: a A M".
  - "Draw next" follows the same order: control set, then the n- and o-families, then the distinctive letters, then the rest.
  - The change is in the engine (`Genres.drawFirst`, `WrapUp`, the Guide). `WrapUpTest` and `FlowCompletenessTest` assert it for every door.
- **C2. Ask what good means before building** (KNOWLEDGE F4). The wrap-up and the Brief tab each gain one line naming what a reviewer will check for this class: its node-economy box (with n) and Google Fonts' packaging rules. It links to Check; it adds no new prose.
- **C3. Targets are distributions** (brief §2.9, §8.3; law 5). Each drift item is a box-plot row (table B), not just a word. Point counts use the log axis from 3 to 3,000; features like contrast use a linear axis over the genre's faces. The Guide ends with the explorer's Check-style "What this does not measure" block: "on target" means "inside what professionals do", never "good" (KNOWLEDGE F1).
- **C4. One face per genre across the app.** Learn and the explorer show each class in its `data/exemplars.json` face. The Brief's cue faces disagree for three:
  - geometric: Poppins → Jost;
  - humanist: Open Sans → Source Sans 3;
  - didone: Libre Bodoni → Playfair Display.

  Change them through `data/scripts/build_brief_cues.py`, never by hand, and record the licences. The extra cue faces stay.
- **C5. The Brief is workbook Task 1** (handoff M5; brief §8.4). On the Brief tab, in the workbook frame (table B):
  - **Why**: one line, C2's.
  - **Demonstration · sample font**: the genre's exemplar face plus its measured line.
  - **Task**: the answers and references.
  - **Gate**: manual, worded as honestly as Task 1 already is.
  - **Reflection · saves to the scrapbook**: "three sentences on what makes it itself".

  The sentence, the chosen references and the reflection save to the project scrapbook as pins. A reference that drives the design gets the violet pin mark. Home's next-task line points at Task 1 while the brief is empty.
- **C6. Read the reference** (Task 2, M4). A reference picked in the References door opens Learn on its Overlay view, set against two neighbours from its genre:
  - each layer has a colour and a line pattern, plus its source (project, device or Google Fonts);
  - the layers align at cap height or x-height;
  - redline mode and the amber stroke probe are available.

  The Lens view is one seat away. Both are drawn as the explorer draws them. If either isn't built yet, open the Learn view that exists and file an OQ; don't draw a new one.
- **C7. Identifying your own font is the exam** (§8.4; the explorer's Economy note: "the inference is itself a lesson"). The reading's evidence uses the Lineages lesson's feature words (contrast, stress, aperture, storeys, terminals), and each one links to its Lineages scene. The stress question shows the Lineages stress dial: 64 dp, violet needle, labelled "stress 30°", "vertical stress" or "no stress axis". "Gothic" is explained where it comes up: sans in American usage, blackletter in European.
- **C8. Name the ranking.** Every best-first list (starting references, a genre's faces) says which ranking it uses. By default that's Google's Quality/Drawing score.
- **C9. Classify the construction first** (§8.5). The Brief quotes no per-letter point target. If one is ever added, it names the construction first: elliptical, superelliptical, rounded-rect or polygonal.

## D. Docs

- **`CLAUDE.md` law 6:** the explorer wins over the canvas boards on look (direction 1). The canvas governs the deck geometry and the screens only it shows.
- **`docs/DECISIONS.md`:** a D-entry dated 19 Oct 2026 for directions 1 and 2, each with its written reason.
- **`docs/SCREENS_V1.md`:**
  - §2.1: live sections, status shapes, digit keys, the dimmed-section explanation.
  - §2.3: the Brief's row of the table.
  - §2.4: views allowed, `DeckSeats`.
  - S02: steps on the arc.
- **`docs/BRIEF.md`:** a "Sources" section citing the pack-1 documents by section, and the C items.
- **`docs/OPEN_QUESTIONS.md`:**
  - the desktop and landscape Brief (the explorer's desktop frame doesn't show it; Bocal turns its dock into a side dock in landscape, with the bottom dock winning below 520 px of height);
  - anything deferred from C6.
- **`docs/HANDOFF_BRIEF_FOLD.md`:** one line at the top pointing here.

## E. Rules

As in the handoff: CLAUDE.md laws, ktlint clean, SPDX headers, KDoc that says what and why with no build history, nothing claimed on a device. Finish by running `./gradlew :golden-path:goldenPath` and pasting its summary into PR #6.

## F. Owner calls (each has a default, so nothing waits on them)

1. **Fonts:** explorer system UI sans and mono (default), or keep IBM Plex.
2. **The Brief's sentence** is set in the genre's face. UI_SPEC says the user's letters are the only display type on screen, but the Brief's answers and references are reference faces shown as specimens, as in Learn. Default: keep as built.
3. **Section order:** pack 1 puts Learn between Space and Check; V1 puts it after Ship. Default: V1's order.
4. **Sound:** pack 1 says no UI sounds. The owner has since mentioned wanting Nintendo-style sounds when moving between menu items. Default: none, with an off-by-default setting for a soft tick on the section arc's detents.
5. **S02's steps on the toggle arc** (A2). Default: yes.
