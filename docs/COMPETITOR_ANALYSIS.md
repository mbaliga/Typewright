# Font-maker app: competitor analysis and build verdict

Prepared for Madhav. Working name used here: GlyphForge. Change freely.

## The one-line verdict

The idea is worth doing, but not as "handwriting to font," which is a crowded and
commoditised space. It is worth doing as the thing you personally discovered the
hard way this month: the gap between making a font and making a Google-Fonts-quality
font, extended to the scripts that the incumbents ignore. That is a real, defensible,
and socially valuable niche. Build the quality-and-coverage layer, not another tracer.

## What already exists, so you do not rebuild it

### The direct competitors (handwriting to font)

| tool | platform | licence | what it does | the gap it leaves |
| --- | --- | --- | --- | --- |
| Calligraphr | web | freemium, closed | template, fill, upload, TTF/OTF. The category leader. Editing after scan. | Latin-centric. No quality gate. No complex scripts. No Android. |
| YourFonts | web | freemium, closed | same template-to-font flow, older | same, plus dated UX |
| Scanahand | Windows | paid, closed | handwriting to TTF on desktop | Windows only, Latin, no quality layer |
| CopyMonkey | web + self-host | open source, MIT | ML handwriting mimicry | a toy, not a font-quality tool |
| Microsoft Font Maker | Windows | free, closed | pen input to font | Latin, Windows, no shaping |
| FontForge, BirdFont, Glyphr Studio | desktop, some web | FOSS | full manual font editors | not scanning tools; steep; no guided quality path |

Read: the scanning-to-font job is solved for Latin, several times over, and mostly
free. Competing there head-on is not worth your effort. Nobody there does quality
enforcement, and nobody there does Indic or Arabic seriously.

### The FOSS you stand on rather than build

- **FontForge** (GPL) and **fontmake / fontTools** (Apache/MIT): the actual font
  compilation, UFO handling, TTF/OTF export, feature compilation. Do not write a
  font compiler. Drive these.
- **HarfBuzz** (MIT): the shaping engine that already implements Indic, Arabic,
  Khmer, Myanmar, Thai, Hangul, and the Universal Shaping Engine. This is the single
  most important find. The hard part of Devanagari and Arabic, the contextual
  reordering and joining, is already solved by HarfBuzz. Your app generates the font
  with the right features and glyph names; HarfBuzz does the shaping at render time.
- **Potrace** (GPL) and **VTracer** (MIT): bitmap tracers. Useful, but see the
  warning below. VTracer is the more liberally licensed and more modern of the two.
- **Reference OFL fonts** (OFL): Lohit Devanagari, Noto family, and similar can be
  studied for glyph lists, naming, and feature code. OFL lets you learn from and
  even base work on these with attribution. This is how you get correct glyph
  inventories per script without deriving them yourself.
- **Glyphs app Devanagari tutorial and the n8willis opentype-shaping-documents**
  (public): the exact GSUB feature order for Indic (locl, nukt, akhn, rphf, blwf,
  half, pstf, vatu, cjct) is documented and free to implement.

### The critical technical caveat you already know in your bones

Every incumbent, and the Vercel-style demos you saw, lean on tracers like Potrace.
The research is blunt about the result: because tracers follow boundary evidence
pixel by pixel, they produce, in the words of one vectorization paper, fragmented
paths and redundant anchors. That is exactly the 1,763-node T that got your Google
Fonts entry criticised. **A tracer alone cannot make a good font.** This is not a
minor detail. It is the whole reason your app would be better than the demos, and it
is the hardest part to get right.

So the tracing step must be tracer plus **node reduction and curve fitting** tuned
for type: detect corners, fit the fewest Bezier segments that hold the shape, snap
near-horizontals and near-verticals, enforce consistent stroke weight, align to a
baseline and x-height grid. That fitted, minimal-node outline is also what makes
clean italic and bold derivation possible later, because you can offset a clean
skeleton but you cannot offset a pixel cloud. This module is your actual product.

## Is it worth doing at all

Yes, for three reasons that survive scrutiny:

1. **The quality gap is real and unserved.** You lived it. The market turns a scan
   into "a font" and stops. Nobody turns it into a submittable, well-constructed
   font, and nobody teaches the user why theirs is not one yet. A guided checklist
   that encodes the Google Fonts bar, the thing that would have saved you this
   month, does not exist as a product.

2. **The script gap is real, unserved, and matters.** Calligraphr and friends are
   Latin-first. There is no accessible, mobile, guided tool for someone to digitise
   their own Devanagari, Tamil, Bengali, or a South or South-East Asian minority
   script. HarfBuzz has already done the rendering-side hard work for these scripts,
   so the remaining barrier is purely authoring, which is your app. This is genuine
   digital-preservation value, not a marketing line.

3. **Android and Linux first is itself a gap.** Every serious font tool is desktop
   Windows or Mac, or web. A touch-first Android tool, working with the Android
   desktop transition and Linux, reaches creators the incumbents structurally
   ignore, which is the same population most likely to hold an undigitised script.

The honest counterweights:

- Complex-script font engineering is hard even with HarfBuzz. Getting conjunct
  formation and matra reordering right in generated fonts is real work, and testing
  across renderers is tedious. Budget for it.
- The good-quality tracing-plus-fitting module is a research-grade problem. It is
  the make-or-break, and it is not a weekend.
- Monetisation is unclear against free incumbents. The answer is probably that the
  app is free or FOSS for the preservation mission, and any revenue comes from a
  hosted build service or a pro tier, not from charging for the core.

## Should you do it: effort versus value

Blunt read. The full vision, every Indic and South-East Asian script with complex
shaping, bulk auto-assignment, quality gating, italic and bold derivation, touch
and desktop, is a multi-quarter project, plausibly a year of focused work, and
closer to a small funded effort than a side project. That is the honest scope.

But it decomposes into a sequence where each stage ships something usable and each
stage teaches you whether to continue. That is the only sane way to build it, and it
is the same lesson as the font: ship one clean thing, then extend.

## Proposed build path, smallest useful thing first

**Stage 0, the checklist alone, no scanning.** A pure guided-checklist app that
encodes the Google Fonts bar: undertaking, manual versus GitHub route, glyph
inventory per script to upload against, the quality checks (node economy, consistent
metrics, real curves, anchors present). It could wrap Fontbakery. This is small,
genuinely useful the day it ships, and it is the part that would have saved you. It
also validates demand before you build anything hard.

**Stage 1, Latin scan to good font.** Add capture, the tracer-plus-fitting module
tuned for minimal nodes, per-glyph correction, metric alignment, TTF export. Compete
with Calligraphr only here, and only on quality. If the fitting module is good, that
is a real differentiator.

**Stage 2, one complex script done properly.** Pick Devanagari, since you read it.
Glyph inventory, drawing against guides, generate the font with the documented Indic
GSUB features and correct -deva glyph names, let HarfBuzz shape it. Prove the pattern
end to end on one script.

**Stage 3, generalise and derive.** More Indic and South-East Asian scripts on the
proven pattern. Add italic and bold derivation from the clean skeletons. Bulk upload
with auto-assignment and correction. This is where the original vision arrives, and
by now you know if it is worth finishing.

## What to borrow versus build, in one line each

- Compilation and export: **borrow** (fontTools, fontmake, FontForge).
- Shaping for complex scripts: **borrow** (HarfBuzz, already does the hard part).
- Glyph inventories and feature order per script: **borrow** (OFL fonts, public
  shaping docs).
- Raw bitmap tracing: **borrow the tracer** (VTracer, MIT), but
- Node reduction, curve fitting, metric snapping, quality gating: **build**. This is
  the product. This is the thing that does not exist and the thing you uniquely
  understand the need for.
- The guided checklist and the multi-script authoring UX, touch and desktop:
  **build**. This is the other half of the product.

## The sharpest way to frame the whole thing

The demos you saw make a font. You learned this month that making a font and making
a good font are different acts, and that the difference is invisible until an expert
tells you. GlyphForge is the app that makes the difference visible and then closes
it, for scripts the incumbents never cared about. That is worth building. Build the
checklist first.
