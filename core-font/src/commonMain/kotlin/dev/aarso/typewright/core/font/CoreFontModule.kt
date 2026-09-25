// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font

/**
 * Pure Kotlin font data for Typewright (CLAUDE.md law 2: no `java.*`/`android.*`), built on
 * `core-geometry`'s [dev.aarso.typewright.core.geometry.Contour]/[dev.aarso.typewright.core.geometry.Glyph]
 * types (docs/ARCHITECTURE_REVIEW.md section 3 `:core-font`, section 7 "What P1 and P2 need
 * first"). Two independent readers meet in the middle at `core-geometry`, one per curve
 * convention it already unifies:
 *
 * - [dev.aarso.typewright.core.font.sfnt.readSfntFont] reads a binary TrueType (`glyf`-outline)
 *   font — [dev.aarso.typewright.core.font.sfnt.ByteCursor] is a hand-written big-endian cursor,
 *   since neither `java.util.zip` nor `javax.xml` exist here
 *   (docs/ARCHITECTURE_REVIEW.md section 3 `:core-font` risk 2). It reads `head`, `hhea`, `maxp`,
 *   `loca`, `glyf` (simple and composite, the latter recursively decomposed and affine-transformed
 *   into plain [dev.aarso.typewright.core.geometry.Contour]s), `cmap` (formats 4 and 12), `hmtx`,
 *   `name`, `OS/2` and `post`. CFF-outline fonts are rejected outright: v1 reads TrueType only
 *   (google/fonts ships only TTF). No GDEF/GPOS yet.
 * - [dev.aarso.typewright.core.font.ufo.writeUfoProject]/[dev.aarso.typewright.core.font.ufo.readUfoProject]
 *   read and write a UFO 3 project (`metainfo.plist`, `fontinfo.plist`, `layercontents.plist`,
 *   `glyphs/contents.plist`, each glyph's `.glif` file) using `core-geometry`'s
 *   [dev.aarso.typewright.core.geometry.CurveFormat.CUBIC] contour representation — the format
 *   every glyph this app draws or edits (as opposed to a `glyf`-sourced one, which is
 *   [dev.aarso.typewright.core.geometry.CurveFormat.QUADRATIC]) eventually lives in. XML is parsed
 *   with xmlutil's pull reader; every writer (plist and `.glif`) is hand-written and byte-stable
 *   (the same input always serializes to the same bytes), matching this task's brief. Plain files,
 *   never a directory: `core-font` returns and accepts a path-to-content [Map], and a platform
 *   module or test is the one that actually touches a filesystem (CLAUDE.md law 2 again).
 *
 * Both readers are validated against `fonts/HyleDeco-Regular.ttf`
 * ([dev.aarso.typewright.core.font.sfnt.HyleDecoCrossCheckTest], commonTest): the sfnt reader's
 * counts, run back through `core-geometry`'s own node-economy functions, must equal an
 * independent, from-scratch recount of that exact file (see that test's KDoc for the method and
 * the numbers it checks against, and CLAUDE.md's fixtures section for where those numbers are
 * pinned).
 *
 * Booleans, offsetting, the Schneider fitter, and any quadratic-to-cubic conversion between the
 * two readers above are explicitly out of scope here — P2/P5a work (docs/ARCHITECTURE_REVIEW.md
 * section 7).
 *
 * [NAME] just names the module (kept for parity with every other module's placeholder object).
 */
object CoreFontModule {
    const val NAME: String = "core-font"
}
