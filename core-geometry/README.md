# core-geometry

Pure Kotlin geometry for Typewright: points, cubic and quadratic curves, contours, curve fitting, metrics (including anchor-to-anchor chords), node-economy counts, and the Palette's contour-editing commands (add extremes, harmonise curvature, tidy/simplify, reverse, correct direction, round, knife, close), all in font units with y up. Named mark-attachment anchors (`Anchor`) live here too, alongside `Glyph`. It has no platform dependencies and is where correctness lives, so every public function gets a unit test.
