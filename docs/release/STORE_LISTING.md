# Google Play listing

> **Draft for Madhav's edit.** Nothing here is submitted. The store name, the descriptions and the screenshots are
> the owner's to approve. Claims are limited to what 1.0.0-rc1 builds (`CHANGELOG.md`); on-device behaviour has not
> been run by whoever wrote this draft.
>
> Every line about what the Android app does is a claim about a phone: keep it only after the owner has run it on
> the device (the P19 device checklist).
>
> **Open owner action (D2): the name "Typewright" is not cleared.** Play's app name and the application id
> (`com.asoc.typewright`) both carry it. Do not create the listing, and do not tag `v1.0.0-rc1`, until the
> trademark search through Shiva (Classes 9 and 42) has come back. If the name changes, the application id
> changes too, and a Play application id can never be changed once an app is published.

## Name and category

- **App name (30 characters max):** Typewright  _(pending D2)_
- **Category:** Art & Design (alternative: Education)
- **Tags:** typography, font design, creative tools
- **Contact:** website and email: _owner to fill in_. **Privacy policy:** the published `docs/release/PRIVACY.md`.

## Short description (80 characters max)

Turn hand-drawn letters into a font, and learn what makes a font good.  _(70)_

Alternatives: "Draw letters on paper, photograph them, build a font." _(54)_ · "A font studio that teaches while you draw." _(42)_

## Full description (4000 characters max)

Typewright turns letters you draw by hand into a font, and shows you what a font reviewer looks at.

Print a template sheet, draw your alphabet on paper, photograph it. Typewright finds each letter, traces it, fits
clean curves, and lets you check the result against your drawing before you accept it. The drawing is yours: once
you accept a letter, Typewright does not redraw it behind your back. Changing it takes an explicit unlock.

What is in this version

- Capture: printable template sheets, camera or file, with uncertain cells flagged for you to relabel.
- Trace: step through the trace and accept each letter.
- Draw: pen, knife, curve tools, boolean, stroke, anchors and metric lines, with a live count of the points in each
  letter compared with how published fonts are built.
- Space: sidebearings, kerning and kerning groups.
- Check: fourteen checks of your font's sources, shown with the numbers behind them. Where a check is our own rule
  of thumb, it says so.
- Learn: how letterforms are built, with real fonts.
- Your project is a folder of plain UFO and JSON files that open in FontForge, Glyphs and RoboFont.

Early and preview parts

- The Workbook (a course of drawing tasks) is Early: lessons are still being written.
- Devanagari, kana and Arabic Naskh are Preview.
- On Android you can capture, trace, draw, space and check. Building the font file itself, and Google Fonts' own
  profile check, need the Linux desktop version in this release. On Android, Ship writes the repository folder
  without the compiled font.

Private by design

- No account, no ads, no analytics, no tracking. Typewright collects nothing.
- The app has no internet permission. The camera is used only to photograph your sheet, and the photo stays on your
  device.
- Fonts you make are yours; Typewright claims nothing over them.

Typewright is fair-source software by A System of Cells. It does not guarantee that a font will be accepted by
Google Fonts or any foundry; it helps you find the problems a reviewer would find first.

_Not in this version: GitHub push, iOS, Windows, macOS._

## Keywords and search terms

font maker, make a font, font creator, typeface design, type design, lettering, calligraphy, hand lettering to font,
handwriting font, glyph, kerning, UFO, TTF, Google Fonts, Devanagari, kana, learn typography.

Play has no keyword field: these go naturally into the description. Do not stuff them.

## Screenshots to capture (owner, on a device)

Phone, portrait, 1080 x 1920 or larger, 2 to 8 images. Take them on the device from a release build; this repository
has renders from the test harness in `docs/screenshots/` that are not store screenshots and were not taken on a phone.

1. Home with the sample project open (the font Hyle Deco).
2. Capture's camera over a printed sheet, with the cells found.
3. Trace's scrubber on one letter.
4. Draw, with the economy strip showing point counts.
5. Space, with a kerning pair.
6. Check's report, showing a fail with its explanation.
7. Learn's Anatomy Lens.
8. About, showing licences and the privacy statement (the Play build has no support link).

Also: **icon** 512 x 512 PNG (`docs/branding/app-icon-light.png`, 1100 x 1100, to be exported), **feature graphic**
1024 x 500 (to be designed).

## Release notes (Play "What's new", 500 characters max)

First release candidate: capture and trace hand-drawn letters, draw, space and check them, and keep your project
as plain UFO files. The Workbook, Devanagari, kana and Naskh are Early or Preview. No account, no tracking.

## Not to say

Not "guaranteed Google Fonts quality", "AI", "professional-grade", "works on iPhone", "Windows", "Mac", "GitHub
push", or anything about speed or reliability on a device nobody has measured.
