# shape-preview

Shaping preview through each platform's own HarfBuzz-backed stack: `TextRunShaper` on Android 12 and later, Skiko's shaper on desktop, and the browser text engine via `FontFace` on the web (brief §3). It exposes one `Shaper` interface returning positioned glyphs and clusters, and until each platform is built its actual is a stub that names its planned stack.
