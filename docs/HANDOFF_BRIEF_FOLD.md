# Handoff: fold the declared brief into the Brief

For a Claude Code session (Sonnet is enough). Read `CLAUDE.md`, then `docs/BRIEF.md`, `docs/SCREENS_V1.md` S02 and S25–S27, and build brief §8.4 before touching code. Written 18 Oct 2026; revised 19 Oct 2026 to make the owner's V1 screens canvas the look source.

## The look source: the V1 screens canvas

The owner's design canvas, "Typewright V1 screens" (https://claude.ai/artifact/9WvaeXrcEkHYBJ25K1GM7m), holds the `*.dc.html` boards that CLAUDE.md law 6 and OQ 123 refer to and that the repository is missing: S01–S24, `Deck`, `DeckStates` and `DesktopDraw`. The first version of this handoff, and PR #5's Brief, were drawn without it. Where they differ, **the canvas wins**.

What the canvas fixes, which the Brief currently gets wrong:

| | Canvas | Brief today |
|---|---|---|
| Type | IBM Plex Sans (text) and IBM Plex Mono (labels, chips) | system sans and mono |
| Ground | #EFE9DC with an feTurbulence grain overlay; ink #17150F; muted #6F6A5E | flat paper, no grain |
| Page frame | title 15 px / 600 at left 22, top 56; content from top ~120; deck at top 672, 390 × 172 | its own header and spacing |
| Labels | mono 9–10 px, uppercase, 0.14 em tracking | larger mono labels |
| Choices | `chip`: mono 10 px, 0.12 em, padding 6 × 9, no border, transparent; **on** = ink block, paper text | ink-block rows (same idea, other metrics) |
| Inputs | underline, `rgba(23,21,15,0.35)` | underline (matches) |
| Action links | violet #5F4BE0 text (Workbook, Learn, Draw) | underlined ink text |
| Deck | the `Deck` component: section arc `M -19.3 153.6 A 527 527 0 0 1 409.3 153.6` with a 26 px ink band behind the current label; toggle arc `M 73.4 62.8 A 585 585 0 0 1 316.6 62.8` with a 22 px band; circles at (40, 71) and (350, 71), r 25 (26 filled); neighbour labels at 0.72 and 0.38 opacity | an approximation (R = 1.35 × width) |
| Sections | Home … Workbook; **there is no Brief section** | the prototype draws "Brief" as a section |

**Where the Brief lives, per the canvas.** S02 `NewProject` is "Step 2 of 3 · the brief": Name, Scripts chips, Intent chips (Serif / Sans, Text / Display), "Closest class · what you will be measured against" chips with corpus counts (Art deco · n 8, Geometric sans · n 30, …, Not sure), the line "Only 8 art deco families on Google Fonts qualify, so its boxes are wide. Typewright will also infer a class from your letters, show its evidence, and ask you to confirm.", and "One line about it · optional". The deck there is `section=Home, left=close, right=next`, sections dimmed. So the Brief is reached from S02 step 2 and from Workbook Task 1, never from the section arc. That board is WP2–WP4 of this handoff drawn out; build it as drawn.

**What the canvas does not show.** The doors, the questions, the wrap-up, the Brief tab and the Guide (S25–S27) have no board. Law 6 says to stop rather than invent: the prototype in `ui/v1-screens/brief/` stays their interaction source, but it is restyled to the canvas's tokens and components (WP0). Record in `OPEN_QUESTIONS.md` that S25–S27 need boards on the canvas, and ask the owner to approve the restyled screenshots before the PR merges.

## Where things stand

- **Start from `feature/brief` (PR #5), not `main`.** It holds the Brief: the `:brief` engine, the Compose screen, the prototype in `ui/v1-screens/brief/`, and the docs. CI is green at `a6a9eb8`. Open a new branch from it, and a PR into `feature/brief` (or into `main` once PR #5 merges).
- **`main` already had a smaller version of the same idea, the "declared brief" (§8.4 and decision D4: declare, infer, confirm).**
  - **Record:** `project` `Brief`, holding `source`, `model` (serif/sans), `use` (text/display), `styleClass` (`declared` and `confirmed`) and `note`.
  - **Questions:** screen S02, step 2, which is specified but not built.
  - **Inference:** `qa:corpus` `StyleScorer.rankStyleClasses`, a hand-written scorer with no screen.
  - **Confirmation:** the `styleClass.confirmed` field, which nothing sets.
  - **Guidance:** Workbook Task 1, "Choose your reference".
- **PR #5 added `Brief.ethos` beside those fields without connecting them.** As a result:
  - The intent is stored twice, so the two copies can disagree.
  - There are two style readers. On the 242 corpus faces, `rankStyleClasses` gets 35% top-1 and 75% top-3; the Brief's `readAs` gets 51% top-1 and 83% top-3.
  - Task 1 and the Brief's References door don't know about each other.

**The owner's decision: one record, one reader.** The ethos is the source of truth, and the old fields are derived from it.

## Do not lose (check every item before you open the PR)

### Behaviour
- Six doors: Style, Use, References, Feeling, Mirror, Blank. Every door ends in the wrap-up. In order, the wrap-up shows:
  1. the sentence;
  2. "N targets · N things to settle";
  3. **Draw first**, or **Draw next** once letters exist;
  4. **See your guide** and **Read the brief**;
  5. up to three tensions, each with **Keep as signature** or **Change answer**;
  6. **Ask N more** and **Try another door**.
- Revisiting one answer (the `focus` dimension) works from tensions and from drift items. **Keep my answer** and Back leave without a change.
- The References door opens on `startingReferences`, each set in its own cue face, with search over every measured face.
- With nothing drawn, the Mirror door offers **Start drawing**.
- The Guide ends in **Start drawing**, which closes the Brief onto the sheet.
- `BriefStateKeeper` keeps answers, door and tab across a trip to Draw and back. A brief that already has answers opens on the Guide.
- Progress reads "question N", never "N of M".
- Step copy comes from the engine (`Step.prompt` and `Step.why`), never hard-coded in the UI.
- With no project open, the Brief says so ("Not saved: no project is open"). Where the atlas can't load (the web build), it says so too (OQ 131).

### Look (the canvas first; then owner's taste, SCREENS_V1 §3, D7)
- The canvas's tokens and components, as in the table above: IBM Plex, the grained ground, the page frame, mono labels, `chip`, underline inputs, the `Deck` component.
- Options are rows of type with whitespace, with no boxes and no rules between them. Short, mono answers (Intent, closest class, uses) are canvas `chip`s.
- A chosen answer is an **ink block with paper text**, which is what the canvas's `chip.on` is: never violet, never a border, no "CHOSEN" word.
- A tension is a 2 dp ink rule down its left edge, never a box.
- Text fields are an underline, never a box.
- Buttons are square and flat, with mono uppercase labels: primary is an ink block, secondary has a 1 dp ink outline. Tertiary actions follow the canvas: violet #5F4BE0 text.
- Every answer, door, reference and the sentence are set in the real cue face (`BriefCueFonts`); the cue faces are the one exception to IBM Plex. The sentence and the glyphs use the genre's face.
- Corpus counts stay beside class names ("Art deco · n 8"), as on S02: the user sees how wide each target is (law 5).
- Nothing is red. Colour is never the only signal.
- Only the deck is round. Use the canvas `Deck` geometry exactly (table above), not the prototype's approximation, and never a "Brief" section.
- Keep explanatory copy short. New UI text goes behind an info affordance, not onto the surface.

### Tests that must stay green
- In `brief`: `FlowCompletenessTest`, `ProgressTest`, `WrapUpTest`, `ReadingAccuracyTest` (floors), `AtlasConsistencyTest` and `BriefScenarioTest`.
- In `ui`: `BriefScreenStateTest` and `BriefScreenScreenshotTest`.
- The golden-path ratchet.
- The prototype walk: `python3 ui/v1-screens/brief/build.py`, then `node ui/v1-screens/brief/walk.js <abs path to build/brief-prototype.html>`. It must print "NO DEAD ENDS".

## Work packages

Each WP is one commit, with tests. Stop and report rather than guess (CLAUDE.md "When unsure").

**WP0. Bring the canvas in, and restyle the Brief to it.**
- Ask the owner to export the canvas boards (or attach the project folder). Check them into `ui/v1-screens/` as `*.dc.html` plus `canvas.json`, unchanged, with an SPDX header added only where the format allows a comment. This closes OQ 123. Do not redraw or "tidy" them.
- Prototype (`ui/v1-screens/brief/prototype.html`): switch to IBM Plex (embed the font files at build time, like the cue fonts, with licences in `THIRD_PARTY.md`), add the grain, use the canvas page frame and `chip` metrics, and replace the hand-drawn deck with the canvas `Deck` geometry. The deck shows `section=Home`, left `close`, right `next` on the doors, as on S02. Rebuild and re-run the walk; the walk's selectors may change, its checks may not.
- Compose (`BriefScreen.kt`): the same tokens. If the app's theme lacks IBM Plex or the grain, file an OQ and use what the app's other V1 screens use; do not add a second theme.
- Screenshot test: update the goldens, and put before/after screenshots in the PR for the owner.

**WP1. One record.**
- In `brief`, add `fun Brief.withEthos(ethos: Ethos): Brief`, which re-derives the old fields:
  - `model` comes from the serifs answer or the genre's group (SERIF / SANS / null);
  - `use` comes from the uses: `body-text` → TEXT, `headline`/`signage`/`wordmark`/`watch-face` → DISPLAY, otherwise null; list the mapping in KDoc;
  - `styleClass.declared` is `ethos.genre` when it's a corpus class (`CORPUS_GENRE_KEYS`), otherwise null;
  - `styleClass.confirmed` and `note` are left alone.
- Route every write through it: `BriefScreenUiState.persist` and the facade.
- Migration: a project with `declared` set and no ethos opens with `Ethos(genre = declared)`. Older projects decode as before.
- Tests: round-trip each field; nothing that reads `styleClass` changes behaviour (golden path, `ProjectSummary`).

**WP2. One reader.**
- `readAs` is the inference layer. Keep `rankStyleClasses` only if something outside the Brief needs it (grep for callers); otherwise deprecate it with a pointer to `readAs`.
- Keep the scorer's plain-language evidence idea: a reading shows the measured phrases (`levelsOf` → `Level.phrase`) beside the votes.
- Record the accuracy numbers in `docs/BRIEF.md`.

**WP3. Confirm.**
- In the Mirror door, and on the Brief tab when a drawing exists, offer **This is it: <genre>** under the reading.
- It sets `styleClass.confirmed`, for corpus classes only, through a new `Ethos`-adjacent edit. Show declared and confirmed as one mono line on the Brief tab.
- The style is never confirmed silently.

**WP4. S02 step 2 is the Brief.**
- `New project…` (`ProjectUi.blankNewProjectSpec`) creates the project, then opens the Brief on the doors.
- No new screen: the Style and Use doors *are* step 2, laid out as the canvas's `NewProject` board (Name, Scripts, Intent, Closest class with counts, the explanation line, One line about it), with the deck as drawn there.
- "Not sure" on Closest class opens the other doors (References, Feeling, Mirror, Blank).
- Step 3 (metrics from the class median) is optional here. If the atlas lacks cap, ascender or descender data, file an OQ and skip it; don't invent numbers (law 5).

**WP5. Task 1 meets the References door.**
- Workbook Task 1 gains an action, **Pick in the Brief**, which opens the References door.
- A reference chosen there can be added to the scrapbook with one tap, as a note naming it and its `describeFace` line.
- The gate stays manual; its "no automated check yet" wording stays honest.

**WP6. Docs.**
- `SCREENS_V1` S02: step 2 becomes "the Brief's Style and Use doors", pointing at the `NewProject` board; S25–S27 point at the prototype until they have boards.
- `docs/BRIEF.md`: a "One record" section with the derivation rules.
- `DECISIONS.md`: a D-entry for "ethos is the source of truth".
- `OPEN_QUESTIONS.md`: anything deferred.
- Update the prototype facade if any engine API changes, rebuild it, and re-run the walk.

## Rules
- CLAUDE.md laws and conventions.
- ktlint clean.
- SPDX headers: FSL for `ui`, `brief` and `ui/v1-screens`; Apache for `qa` and `project`. Run `python3 tools/check_licences.py`.
- KDoc says what and why, with no prompt numbers.
- Only the lead agent commits; one PR.
- End by running `./gradlew :golden-path:goldenPath` and pasting its summary into the PR.
- Nothing on a device is claimed as working (law 4).
- If Gradle can't reach Maven, the PR's CI is the compile check: push small and keep it green.
