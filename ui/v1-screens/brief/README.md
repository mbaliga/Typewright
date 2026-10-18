# Brief prototype

The design source for the Brief's screens (S25–S27): one page that runs the real Brief engine,
compiled to JavaScript, over the real style atlas and cue faces, so every question, number and
piece of advice in it is the engine's own. `python3 ui/v1-screens/brief/build.py` writes
`build/brief-prototype.html`; it needs a JDK and downloads the Kotlin compiler at the repository's
Kotlin version unless `KOTLINC_HOME` points at one.

- `prototype.html` — the look and the interaction; `build.py` fills in the fonts (IBM Plex from
  `../fonts/`, and the cue faces), the atlas, the example letters and the compiled engine. The look
  is the V1 screens canvas's (`../README.md`): Plex, the grained ground, the page frame, `chip`,
  underline inputs, violet action links and the canvas `Deck`, ported with its exact geometry. The
  canvas has no board for S25–S27 (`docs/OPEN_QUESTIONS.md` item 139), so what the screens hold is
  this prototype's.
- `facade/BriefPrototype.kt` — the thin layer between the page and the engine: the screen's state
  and one JSON view of it.
- `facade/AtlasJson.kt` — decodes the style atlas with the browser's JSON parser, since
  kotlinx.serialization has no JavaScript build here.
- `walk.js` — walks every door end to end (run after `build.py`); it fails on a dead end, a console
  error or a sideways scroll. Its selectors follow the page's markup; its checks are the contract.
