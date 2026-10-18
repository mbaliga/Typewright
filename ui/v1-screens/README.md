# V1 screens

The look source for every V1 screen (CLAUDE.md law 6): the owner's design canvas, "Typewright V1
screens", exported as it stands. One `*.dc.html` board per screen ID in `docs/SCREENS_V1.md`
(S01–S24), plus `Deck` (the shared control deck), `DeckStates` and `DesktopDraw`; `canvas.json` is
the canvas's index (each board's frame, title and order).

- **Unchanged.** Each board is the canvas file as published, with one line added after the
  doctype, `<!-- SPDX-License-Identifier: FSL-1.1-ALv2 -->`. `canvas.json` is byte-identical to the
  canvas's (JSON has no comment syntax, so it carries no header). Do not redraw or tidy a board
  here; change the canvas, then re-export.
- **Source.** The canvas at https://claude.ai/artifact/9WvaeXrcEkHYBJ25K1GM7m, version
  `1792349960-7121`, exported 19 Oct 2026.
- **Opens in the canvas, not on its own.** Each board loads `./support.js` (the canvas runtime) and
  the Hyle Deco font by a `/_blob/…` address; neither is in this repository.
- **Not here.** The Brief's doors, questions, wrap-up, Brief tab and Guide (S25–S27) have no board
  on the canvas yet (`docs/OPEN_QUESTIONS.md`); their interaction source is `brief/`, restyled to
  the canvas's tokens.

## The canvas's tokens

| | |
|---|---|
| Type | IBM Plex Sans (text), IBM Plex Mono (labels, chips) |
| Ground | `#EFE9DC` under an `feTurbulence` grain; ink `#17150F`; muted `#6F6A5E`; action links `#5F4BE0` |
| Page frame | title 15 px / 600 at left 22, top 52–56; content from top ~104–122; the deck at top 672, 390 × 172 |
| Label | mono 9–10 px, uppercase, 0.14 em tracking |
| `chip` | mono 10 px, 0.12 em, uppercase, padding 6 × 9, no border, transparent; **on** = ink block, paper text |
| Input | underline, `rgba(23,21,15,0.35)` |

`brief/prototype.html` and `ui/…/brief/BriefScreen.kt` use these.
