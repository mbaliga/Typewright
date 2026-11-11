# Changelog

All notable changes to Typewright are listed here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the version numbers follow
[Semantic Versioning](https://semver.org/). The version is written once, in `version.properties`.

On-device behaviour (Android) and behaviour in a real browser were not run by whoever wrote this file; the
build environment has no phone, no emulator and no display (`CLAUDE.md` law 4). Items marked "owner-verified
only" are built and compile-checked and wait for the owner to try them.

## [Unreleased]

## [1.0.0-rc1] - not tagged yet

**This release candidate must not be tagged until the name "Typewright" has been cleared (decision D2;
`docs/release/RELEASE_CHECKLIST.md`).** No tag, store listing or GitHub release exists.

### Added

- **Open a font you bring** (TTF, OTF or a UFO folder) and work on it as a project. Its outlines are locked as
  approved until you unlock them (law 1).
- **Projects as plain files**: a project is a directory of UFO 3 and JSON, with autosave and undo and redo. It is
  written to be opened in FontForge, Glyphs and RoboFont (the UFO is checked with fontTools' own ufoLib in CI).
- **Capture and Trace**: print Typewright's template sheets (Latin, plus Devanagari, kana and Arabic Naskh
  packs), draw on paper, then photograph or scan them. Cells are found from the sheet's markers, flagged when
  uncertain, traced, fitted to curves and reviewed on a scrubber before you accept each glyph. SVG drawings can be
  imported too. The Android camera is compile-checked only (owner-verified only).
- **Draw**: pen, knife, tidy with a live node count, tangent and Hobby curves, primitives, measure, boolean,
  stroke, anchors, metric lines, layers and an economy strip.
- **Space**: sidebearings, kerning, kerning groups and the `.fea` file.
- **Check**: fourteen checks of the project's own sources (layer two) in a report, with node economy judged
  against a measured distribution; on Linux desktop with Python, Google Fonts' own profile on the compiled font
  (layer one). A check with no measurement behind it says "our heuristic".
- **Compile and Ship** (Linux desktop): build a TTF with fontmake and write a Google Fonts repository folder
  (OFL text, METADATA, description draft, sources, a ready-to-paste request). Ship stays blocked while Check has
  any fail. On Android the folder is written without the font; the page says so. On the web the folder is a zip.
- **Learn**: Lineages, the Anatomy Lens and the Scrapbook, with real fonts and real data.
- **Workbook** (Early, lessons in progress), **scripts** Devanagari, kana and Naskh in Draw and Space with
  shaping preview (Preview), and the **Brief** (partly built).
- **About and licences** on every target: the version, the licence in one sentence, the privacy statement and a
  link to the policy, a support link (not on Google Play) and every third-party notice, including those Skiko
  needs for Skia, FreeType and libpng. A script and a CI check keep the notices equal to `THIRD_PARTY.md`.
- **Themes**: five built-in grounds and a theme editor with guardrails (meaning colours stay distinct).
- **Release engineering**: one version file; R8-minified release build with keep rules, a smoke test of the
  release APK, signing read from environment variables; an AAB for Play and an APK for direct download; a Linux
  app image with a bundled JRE and a script that wraps it into an AppImage; a static web build with a Preview
  banner and a locale guard; a one-page privacy policy; store and landing copy drafts.
- Local crash report on Android: kept on the device and shown on the next launch for you to copy. Nothing is sent.

### Changed

- The web build's blank page on browsers that report a locale such as `en-US@posix` is guarded against.
- The Android manifest drops `ACCESS_NETWORK_STATE`, which a library added; the app declares only the camera.

### Not in this release

GitHub push, iOS, Windows, macOS, hosted builds, interpolation and masters, derived weights and obliques, nib
tools, Compile on Android, layer one on Android and the web. The web build is a Preview: it cannot compile a font
or run Google Fonts' own checks.

### Known

- Typewright makes **no network connections of its own** in this version. The Google Fonts comparison fetch is not
  built yet (`docs/OPEN_QUESTIONS.md`).
- The release APK and AAB are unsigned until the owner supplies signing secrets.
- A font from Typewright is not guaranteed to be accepted by Google Fonts; Check and Ship exist to find the
  problems a reviewer finds first.
