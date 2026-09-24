# engine-construct

Construction geometry for drawing a font from nothing: booleans, offsets, stroke to outline, Hobby splines, transformations, and the construction grammar's own primitives (line, arc, circle, ellipse, superellipse, rectangle, rounded rectangle, stem, bowl), each a parametric data type plus a pure `realize()` recomputed fresh from its own parameters — "baked" is simply a caller keeping one `realize()` result as plain geometry (handoff M2, brief §10). It is pure Kotlin with no platform dependencies and is exhaustively unit tested.
