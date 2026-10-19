# Brief prototype

The design source for the Brief's screens (S25–S27): one page that runs the real Brief engine,
compiled to JavaScript, over the real style atlas and cue faces, so every question, number and
piece of advice in it is the engine's own. `python3 ui/v1-screens/brief/build.py` writes
`build/brief-prototype.html`; it needs a JDK and downloads the Kotlin compiler at the repository's
Kotlin version unless `KOTLINC_HOME` points at one.

- `prototype.html` — the look and the interaction; `build.py` fills in the fonts, the atlas, the
  example letters and the compiled engine.
- `facade/BriefPrototype.kt` — the thin layer between the page and the engine: the screen's state
  and one JSON view of it.
- `facade/AtlasJson.kt` — decodes the style atlas with the browser's JSON parser, since
  kotlinx.serialization has no JavaScript build here.
