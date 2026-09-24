# core-font

Pure Kotlin font data for Typewright: a binary sfnt/TrueType reader (`glyf`, composites, `cmap`, `hmtx`, `name`, `OS/2`, `post`) and a UFO 3 project reader and writer (`fontinfo.plist` including `xHeight`/`capHeight`, and per-glyph mark-attachment anchors), plus (later) glyph naming, feature-file generation and kerning groups. Every file it writes must open in FontForge, Glyphs or RoboFont, and it has no platform dependencies.
