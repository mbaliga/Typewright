#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Derive the compact node-economy pack from the full one.

No generator for `data/node-economy-latin.compact.json` existed before this
(P0c, 2026-09-24): the file was committed as a derived artefact with nothing
in `data/scripts/` that produced it, which breaks the "never hand-edit
generated data" rule (CLAUDE.md conventions). This script reverse-engineers
the exact shape from the committed compact file and regenerates it from
`node-economy-latin.json`, byte-for-byte compatible with the pre-existing
structure (checked against the pre-P0c-fix compact file before it was
overwritten):

  {"glyphs": <same GLYPHS string as the full pack>,
   "styles": {
     "<style-key>": {
       "n": <family count>,
       "fams": [<family name>, ... in the SAME order as the full pack's
                "families" list, which is already rank order>],
       "dist": {"<glyph>": {min, q1, med, q3, max}},   // ON-CURVE only, no
                                                         // "off" and no "n"
                                                         // (n is style["n"])
       "per": {"<glyph>": [<on-curve count per family, aligned index-for-
                            index with "fams">]}
     }
   }}

This is strictly ON-CURVE (including TrueType-implied points, same as the
full pack): the compact pack is for the box-and-whisker UI (brief 8.3), which
plots on-curve equivalents; off-curve counts and per-glyph contour counts stay
in the full pack for anything that needs them (the per-family "counts"
triples, licence, file name, drawing score).

A family that does not cover a given glyph (rare after P0c's "drop families
with no Latin letters" fix -- see build_node_economy_corpus.py process())
has no entry for that glyph in the full pack's per-family "counts", so no
value to put in "per"[glyph] at that family's index either. Rather than
inventing a number (law 5: measured, not invented), the corresponding slot
is written as `null`, and "dist" for that glyph is computed from only the
families that do have it -- so "dist"[glyph] can have fewer non-null values
behind it than "n" families; that count is recoverable by the caller as
`len(per[glyph]) - per[glyph].count(None)` if it needs it.

Usage: python3 build_compact_corpus.py [node-economy-latin.json]
                                        [node-economy-latin.compact.json]
"""
import json
import sys


def quartiles(vals):
    v = sorted(vals)
    n = len(v)
    if n == 0:
        return None

    def q(p):
        k = (n - 1) * p
        f = int(k)
        c = min(f + 1, n - 1)
        return v[f] + (v[c] - v[f]) * (k - f)

    return {"min": v[0], "q1": q(0.25), "med": q(0.5), "q3": q(0.75), "max": v[-1]}


def build_compact(full):
    compact = {"glyphs": full["glyphs"], "styles": {}}
    for key, sty in full["styles"].items():
        fams = [f["family"] for f in sty["families"]]
        n = len(fams)
        dist = {}
        per = {}
        for ch in full["glyphs"]:
            row = [f["counts"].get(ch, [None])[0] for f in sty["families"]]
            per[ch] = row
            present = [v for v in row if v is not None]
            if present:
                dist[ch] = quartiles(present)
        compact["styles"][key] = {"n": n, "fams": fams, "dist": dist, "per": per}
    return compact


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "node-economy-latin.json"
    dst = sys.argv[2] if len(sys.argv) > 2 else "node-economy-latin.compact.json"
    with open(src) as f:
        full = json.load(f)
    compact = build_compact(full)
    with open(dst, "w") as f:
        json.dump(compact, f, separators=(",", ":"))
    print(f"wrote {dst}", file=sys.stderr)


if __name__ == "__main__":
    main()
