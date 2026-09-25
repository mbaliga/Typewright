# Decision register — 22–23 September 2026 design sessions

Decisions made by Madhav in conversation, recorded so the build does not re-ask them.

| # | question | decision |
|---|---|---|
| D1 | v1 platforms | Android, Linux (JVM desktop), Web (Kotlin/Wasm). Windows/macOS later on the same desktop target; iOS/iPadOS later, which is why compile sits behind an interface with fontc planned. |
| D2 | Node-economy judgement | A box-and-whisker per glyph against the top 30 fonts per style, the user's glyph as a point. Style classes from the Google Fonts taxonomy. Not an invented threshold. |
| D3 | Corpus ranking | Both, as a toggle: Google's Drawing quality score (default, shipped) and popularity via the Fonts API (needs a key at build time). |
| D4 | Style identification | Declared in the brief, inferred from measurable features, confirmed by the user. The inference is a lesson. |
| D5 | Lessons content | Scaffold now, marked SCAFFOLD; Madhav's Domestika material augments, improves and steers. |
| D6 | "Cinematic" means | Motion and scale only. No narration. Ambient sound optional, off by default, for staying in flow. Lessons are live geometry, never video. |
| D7 | Visual style | Minimal and brutalist: hard edges, ink blocks, opaque paper, no shadows or blur, whitespace and the bloom instead of boxes and rules. |
| D8 | Bloom | Canvas lines and grain fade as they approach any UI element; letters never fade. |
| D9 | Navigation | The room model, evolved: one sheet, one plane; the grid and content move, the glass stays. Rooms are the pipeline stages; zoom is depth; a map on pinch-out. |
| D10 | Selection | Ink blocks with paper text for anything selected; plain ink text for everything else. ("undeleted" read as "unselected" — [CONFIRM]) |
| D11 | Tools on a phone | A floating, pinned tool puck: swipe to cycle, tap to unfold a collapsible list, hold for a spinnable radial, drag the grip to re-pin. Contextual actions on a hold over the object, not in the puck. |
| D12 | Trace stages | The stage strip is a scrubber, not a row of steps. Three stages by default with the rest behind "adjust" is the lean; [CONFIRM]. |
| D13 | Editing tools | Tiered: spiro/Hobby curves, transforms, layers, kerning groups + .fea, guides, palette commands in v1; nib tools and components v1.5; interpolation v2; no manual hinting. |
| D14 | Shaping in v1 | HarfBuzz via the platform stacks (TextRunShaper, Skiko, browser), not JNI. |
| D15 | Fixtures | The shipped Hyle Deco TTFs are the regression suite, stated on-curve·off-curve·total (restated 2026-09-24, P0c, written reason: docs/ARCHITECTURE_REVIEW.md section 5 items 13–22): T 1,763·0·1,763 → 8·0·8, o 80·0·80 → 16·16·32, n 44·0·44 → 14·8·22, H 1,252·0·1,252 → 12·0·12. |
| D16 | Model routing | Opus for the architecture review, the fitter, construction geometry/spiro and the style-detector features; Sonnet for everything else; the chat app for UI iteration on the explorer. |
| D17 | Licence | FSL-1.1-ALv2 for the app, Apache-2.0 for the engine, CC0-1.0 for template sheets and data packs. Fonts people make with Typewright are theirs. Decided 25 Sep 2026 by Madhav; `docs/LICENSING.md`. |

Still open: the name, the vertical margin/proof axis, default texture, the bundled UI face, the
hosted endpoint's home.
