#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Build the node-economy reference corpus for Typewright.

For each style class in the Google Fonts taxonomy, take the top-N families by
Google's own /Quality/Drawing score (tie-break: tag score, then reverse family
name -- see the comment on the tie-break below), download the Regular face
from the google/fonts repository, count on-curve and off-curve points per
glyph, and write per-style, per-glyph distributions (min, q1, median, q3, max)
plus the raw per-family counts so the app can list the 30 fonts behind a box.

Counts are format-aware: TrueType quadratic off-curve counts are reported as
measured and labelled "quadratic". On-curve counts include TrueType's implied
on-curve points (see count_points below), which is what makes them comparable
to a cubic source's on-curve points after cu2qu -- cu2qu places an on-curve
point at every knot the outline actually has, implied ones included, and
compares only after the SAME overlap removal as the app applies (see
TYPEWRIGHT_BUILD_BRIEF.md 8.1 and docs/ARCHITECTURE_REVIEW.md section 5 item 15
for the caveats: this is not exact for variable fonts that keep their overlaps,
and it breaks under --drop-implied-oncurves).

Fixed 2026-09-24 (P0c, see docs/ARCHITECTURE_REVIEW.md section 3 ':qa:corpus'
and section 5 items 13-22): the previous version (a) double-counted the
closing point of every contour whose last segment curved back to the start,
(b) counted every composite glyph (accented letters and the like) as zero
points because it never decomposed `addComponent`, and (c) kept families that
have no Latin LETTER coverage at all (digit-only coverage was enough to slip
through), which silently shrank every letter box's n below the family count.
See count_points() and process() below.

Usage: python3 build_node_economy_corpus.py [--top 30] [--tags path/to/families.csv]
                                             [--out node-economy-latin.json]

--tags is normally omitted: tags/all/families.csv is fetched fresh from the pinned google/fonts
commit on every run (never committed here -- google/fonts states no licence for tags/, so we
don't redistribute it, docs/OPEN_QUESTIONS.md item 121). Pass --tags for an offline run against
a local copy instead.
"""
import csv, io, json, re, subprocess, sys, argparse, collections, concurrent.futures as cf
import urllib.request
from datetime import date
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen

GOOGLE_FONTS_GIT = "https://github.com/google/fonts.git"
RAW_TEMPLATE = "https://raw.githubusercontent.com/google/fonts/{ref}/"
# Set for real by main() once the source commit is resolved (see
# resolve_source_commit); "main" here is only a fallback if that fails.
RAW = RAW_TEMPLATE.format(ref="main")

STYLES = {
    "sans-geometric":   "/Sans/Geometric",
    "sans-grotesque":   "/Sans/Grotesque",
    "sans-neogrotesque":"/Sans/Neo Grotesque",
    "sans-humanist":    "/Sans/Humanist",
    "serif-garalde":    "/Serif/Old Style Garalde",
    "serif-transitional":"/Serif/Transitional",
    "serif-didone":     "/Serif/Didone",
    "slab":             ("/Slab/Humanist", "/Slab/Geometric", "/Slab/Clarendon"),
    "display-artdeco":  "/Theme/Art Deco",
    "blackletter":      "/Theme/Blackletter",
}
GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

ON_CURVE = 0x01  # glyf simple-glyph flag bit 0 (OpenType spec, "Simple Glyph Flags")


def fetch(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "typewright-corpus/0.1"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def resolve_source_commit(repo=GOOGLE_FONTS_GIT, ref="refs/heads/main"):
    """Resolve google/fonts' current default-branch commit SHA, so the pack's
    'source' field is reproducible (P0c item 6; docs/OPEN_QUESTIONS.md item
    20/4). `git ls-remote` talks the git smart-HTTP protocol directly, which
    is reachable in sandboxes where the github.com web UI and the
    api.github.com REST API (the task's two suggested alternatives) are
    blocked -- it resolves the same ref the same way. Returns None (and lets
    the caller fall back to the unpinned "main" ref) if that fails for any
    reason; this must never hard-fail the whole build over network flakiness.
    """
    try:
        out = subprocess.run(
            ["git", "ls-remote", repo, ref],
            capture_output=True, text=True, timeout=30, check=True,
        )
        line = out.stdout.strip().splitlines()[0]
        sha = line.split()[0]
        if len(sha) == 40 and all(c in "0123456789abcdef" for c in sha):
            return sha
    except Exception as e:
        print("could not resolve google/fonts commit SHA:", e, file=sys.stderr)
    return None


def parse_tags(text):
    """Parse google/fonts' tags/all/families.csv text (family, ?, tag, score rows) into
    family -> {tag: score}. Split out from load_tags() so the parsing itself is unit-testable
    against a plain string, independent of where that text came from.
    """
    fam_tag = collections.defaultdict(dict)   # family -> {tag: score}
    for row in csv.reader(io.StringIO(text)):
        if len(row) < 4: continue
        fam, _, tag, score = row[0], row[1], row[2], row[3]
        try: fam_tag[fam][tag] = max(fam_tag[fam].get(tag, 0), int(score))
        except ValueError: pass
    return fam_tag


def load_tags(path):
    # `path` is None (the default, see main()) to fetch tags/all/families.csv fresh from RAW --
    # the pinned google/fonts commit, set by main() before this is called -- with the same
    # fetch() helper every other google/fonts read here uses, or a local file path for an
    # offline run (--tags path/to/families.csv). Note: google/fonts sets a licence per top-level
    # directory (ofl/, apache/, ufl/, ...), and tags/ is not one of those -- its licence is
    # unstated. Not a blocker here (this file is a build-time input, never shipped or committed),
    # but flagged: docs/OPEN_QUESTIONS.md item 121, Madhav's call.
    if path is None:
        text = fetch(f"{RAW}tags/all/families.csv").decode("utf-8")
    else:
        with open(path, newline="") as f:
            text = f.read()
    return parse_tags(text)


def dir_name(family):
    return re.sub(r"[^a-z0-9]", "", family.lower())


def regular_filename(metadata_pb):
    fonts = re.findall(r"fonts \{(.*?)\}", metadata_pb, re.S)
    best = None
    for blk in fonts:
        style = re.search(r'style: "(.*?)"', blk); weight = re.search(r"weight: (\d+)", blk)
        fn = re.search(r'filename: "(.*?)"', blk)
        if not fn: continue
        s = style.group(1) if style else "normal"; w = int(weight.group(1)) if weight else 400
        cand = (0 if s == "normal" else 1, abs(w - 400), fn.group(1))
        if best is None or cand < best: best = cand
    return best[2] if best else None


def _count_simple_contours(coords, end_pts, flags):
    """Apply the standard TrueType on-curve rule to already-decomposed
    per-point data (see count_points for how `glyf` composites get here).

    For each contour: on = points flagged on-curve; off = points flagged
    off-curve; then +1 implied on-curve point for every pair of two
    *consecutive* off-curve points, checked cyclically (the wrap from the
    last point of the contour back to the first counts too). This is the
    OpenType glyf spec's rule for reconstructing the curve ("if a run of
    off-curve points is encountered, on-curve points are assumed to exist at
    the midpoint of each successive pair") -- it is what a rasterizer
    actually draws through, so it is what "on-curve equivalents" means, and
    it is the general form of the "all-off-curve contour" special case the
    brief already named (TYPEWRIGHT_BUILD_BRIEF.md 8.1): a fully-off-curve
    contour is just the case where every point is part of such a run.

    Hand-traced against a real contour (Poppins-Regular 'o', outer contour,
    flags [off,off,off,on] x4 = 16 points): each "off,off,off,on" group sits
    between the previous group's on-curve anchor and its own, and decomposes
    into three quadratic segments -- anchor->C1->mid(C1,C2),
    mid(C1,C2)->C2->mid(C2,C3), mid(C2,C3)->C3->anchor -- so a run of 3
    off-curve points carries 2 implied on-curve points, matching (run
    length - 1) per run, which is exactly what counting consecutive
    off-curve pairs gives.
    """
    on = off = 0
    start = 0
    for end in end_pts:
        n = end - start + 1
        idx = range(start, end + 1)
        c_on = sum(1 for i in idx if flags[i] & ON_CURVE)
        c_off = n - c_on
        implied = 0
        pts = list(idx)
        for k in range(n):
            i, j = pts[k], pts[(k + 1) % n]  # cyclic: wraps last -> first
            if not (flags[i] & ON_CURVE) and not (flags[j] & ON_CURVE):
                implied += 1
        on += c_on + implied
        off += c_off
        start = end + 1
    return on, off, len(end_pts)


def count_points(font):
    """Count on-curve (incl. TrueType-implied) and off-curve points per
    requested glyph. Returns (format, {char: [on, off, contours]}).

    TrueType (`glyf`) fonts -- everything google/fonts ships in practice
    (docs/ARCHITECTURE_REVIEW.md section 3 `:core-font`) -- are counted
    straight from the glyf table's own per-point flags via
    Glyph.getCoordinates(glyfTable), which fontTools itself decomposes
    composite glyphs (component references, with their transforms) through
    recursively. This fixes both the closing-point double count and the
    composites-count-as-zero bug in one move, because both bugs came from
    going through a RecordingPen: a pen sees a composite's addComponent call
    and (with no addComponent handler here) silently drops it, and a pen
    sees a contour's closing segment as an ordinary drawing op whose target
    point happens to equal the moveTo point, without knowing they are the
    same point. Reading the raw flags sidesteps both: there is no pen, no
    addComponent to ignore, and no synthesized "closing" call, only the
    point list mapped to on/off/implied by the rule in
    _count_simple_contours. Verified against fontTools' own getCoordinates()
    output and against a from-scratch recursive decomposer, on
    fonts/HyleDeco-Regular.ttf and on Poppins-Regular.ttf (see the P0c
    commit and docs/OPEN_QUESTIONS.md).

    Non-TrueType (CFF/cubic) fonts have no glyf table and no implied points
    to begin with, so they keep the old pen-based path -- but with the same
    closing-point bug fixed by name (a closed cubic contour's last curveTo
    can also target the moveTo point again). google/fonts is TTF-only in
    practice (ARCHITECTURE_REVIEW.md section 3 `:core-font`: "No CFF in v1:
    google/fonts ships only TTF"), so this branch is an untested safety net,
    not a load-bearing path; it is kept only so the script does not silently
    miscount if that ever changes.
    """
    cmap = font.getBestCmap()
    out = {}
    if "glyf" in font:
        fmt = "quadratic"
        glyf = font["glyf"]
        for ch in GLYPHS:
            gname = cmap.get(ord(ch))
            if not gname:
                continue
            glyph = glyf[gname]
            coords, end_pts, flags = glyph.getCoordinates(glyf)
            if not end_pts:
                continue  # a genuinely empty glyph (e.g. .notdef-like); not a 0 to report
            on, off, contours = _count_simple_contours(coords, end_pts, flags)
            out[ch] = [on, off, contours]
    else:
        fmt = "cubic"
        gs = font.getGlyphSet()
        for ch in GLYPHS:
            gname = cmap.get(ord(ch))
            if not gname:
                continue
            pen = RecordingPen(); gs[gname].draw(pen)
            on = off = contours = 0
            start_pt = None
            for op, args in pen.value:
                if op == "moveTo":
                    start_pt = args[0]
                    on += 1; contours += 1
                elif op == "lineTo":
                    if args[0] == start_pt: continue  # implicit-close line back to start
                    on += 1
                elif op == "curveTo":
                    off += 2
                    if args[-1] == start_pt: continue  # explicit-close curve back to start
                    on += 1
                elif op == "qCurveTo":
                    # Unreachable while `fmt == "cubic"` means no glyf table, but kept
                    # correct in case a hybrid font ever reaches this branch.
                    if args[-1] is None:
                        off += len(args) - 1; on += len(args) - 1
                    else:
                        off += len(args) - 1
                        if args[-1] != start_pt: on += 1
            if contours:
                out[ch] = [on, off, contours]
    return fmt, out


def process(family):
    d = dir_name(family)
    for lic in ("ofl", "apache", "ufl"):
        try:
            meta = fetch(f"{RAW}{lic}/{d}/METADATA.pb").decode("utf-8", "replace")
        except Exception:
            continue
        fn = regular_filename(meta)
        if not fn: return None
        try:
            data = fetch(f"{RAW}{lic}/{d}/{fn}")
        except Exception:
            return None
        try:
            font = TTFont(io.BytesIO(data))
            fmt, counts = count_points(font)
            # Drop families with no Latin LETTER in the requested set at all --
            # e.g. a Khmer- or Devanagari-only face tagged into a Latin style
            # class. Checking only "counts is non-empty" is not enough: Content,
            # Khmer and Siemreap (tagged blackletter) cover the 10 digits (shared
            # Hindu-Arabic numerals) but zero of the 52 Latin letters, so `counts`
            # has 10 entries and is truthy. Counting them anyway would keep them
            # in the family list while every LETTER box's n silently drops below
            # it (docs/ARCHITECTURE_REVIEW.md section 5 item 13: this is exactly
            # why blackletter's letter boxes read n=15 while the family list read
            # n=18). Drop the family outright instead, so the reported class size
            # and every letter box's n agree.
            if not any(ch.isalpha() for ch in counts):
                print("skip (no Latin letters)", family, file=sys.stderr)
                return None
            upm = font["head"].unitsPerEm
            return {"family": family, "file": fn, "licence": lic, "format": fmt, "upm": upm, "counts": counts}
        except Exception as e:
            print("skip", family, e, file=sys.stderr); return None
    return None


def quartiles(vals):
    v = sorted(vals); n = len(v)
    if n == 0: return None
    def q(p):
        k = (n - 1) * p; f = int(k); c = min(f + 1, n - 1)
        return v[f] + (v[c] - v[f]) * (k - f)
    return {"min": v[0], "q1": q(.25), "med": q(.5), "q3": q(.75), "max": v[-1], "n": n}


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--top", type=int, default=30)
    ap.add_argument(
        "--tags", default=None,
        help="local families.csv for an offline run; omit to fetch tags/all/families.csv fresh from the pinned commit",
    )
    ap.add_argument("--out", default="node-economy-latin.json")
    ap.add_argument("--commit", default=None, help="pin to this google/fonts commit SHA instead of resolving main")
    a = ap.parse_args()

    global RAW
    sha = a.commit or resolve_source_commit()
    if sha:
        RAW = RAW_TEMPLATE.format(ref=sha)
        source = (
            f"google/fonts@{sha} (raw.githubusercontent.com/google/fonts/{sha}/"
            f"{{ofl,apache,ufl}}/<dir>/METADATA.pb + Regular TTF; tags/all/families.csv "
            f"from the same commit), fetched {date.today().isoformat()} by "
            f"data/scripts/build_node_economy_corpus.py. tags/ has no stated licence in "
            f"google/fonts (docs/OPEN_QUESTIONS.md item 121; Madhav's call, not blocking)."
        )
    else:
        # Network to git's smart-HTTP endpoint failed; fall back to the unpinned
        # ref rather than aborting, but say so plainly -- this run is not reproducible.
        source = (
            "google/fonts@main (commit SHA NOT resolved -- git ls-remote failed; "
            f"UNPINNED, not reproducible), fetched {date.today().isoformat()}. tags/ "
            "has no stated licence in google/fonts (docs/OPEN_QUESTIONS.md item 121)."
        )
        print("WARNING: source commit not pinned; pack will say so", file=sys.stderr)

    fam_tag = load_tags(a.tags)
    pack = {"source": source, "glyphs": GLYPHS, "styles": {}}
    for key, tags in STYLES.items():
        tags = tags if isinstance(tags, tuple) else (tags,)
        cands = []
        for fam, t in fam_tag.items():
            sc = max((t.get(x, 0) for x in tags), default=0)
            if sc >= 50:
                cands.append((t.get("/Quality/Drawing", 0), sc, fam))
        # Tie-break and "superfamily" rule (docs/ARCHITECTURE_REVIEW.md section 3
        # ':qa:corpus' and section 5 item 13): sort by (drawing score, tag score,
        # family name) descending, so equal-scoring families break ties by
        # REVERSE alphabetical family name. Then dedupe by "superfamily", defined
        # as the first word of the family name lowercased, keeping only the
        # first (i.e. highest-sorted) family per root. Reverse-alpha means a
        # later-alphabet variant name wins a tie over the plain family name --
        # e.g. "Playfair Display SC" (S > nothing) is kept and "Playfair
        # Display" is dropped when they tie. This is a real, debatable
        # consequence, not obviously a bug either way (arguably SC counts as a
        # different design, not a redundant duplicate) -- left as-is per the
        # instruction to only change behaviour found to be clearly wrong.
        cands.sort(reverse=True)
        seen = set(); dedup = []                       # one face per superfamily (first word)
        for c in cands:
            root = c[2].split()[0].lower()
            if root in seen: continue
            seen.add(root); dedup.append(c)
        cands = dedup
        chosen = [c[2] for c in cands[: a.top + 8]]   # a few spares for failed downloads
        with cf.ThreadPoolExecutor(12) as ex:
            results = [r for r in ex.map(process, chosen) if r]
        results = results[: a.top]
        dist = {}
        for ch in GLYPHS:
            on = [r["counts"][ch][0] for r in results if ch in r["counts"]]
            off = [r["counts"][ch][1] for r in results if ch in r["counts"]]
            if on: dist[ch] = {"on": quartiles(on), "off": quartiles(off)}
        pack["styles"][key] = {"tags": list(tags), "families": [{"family": r["family"], "file": r["file"], "format": r["format"], "drawing": next((c[0] for c in cands if c[2] == r["family"]), None), "counts": r["counts"]} for r in results], "dist": dist}
        print(f"{key}: {len(results)} fonts", file=sys.stderr)
    json.dump(pack, open(a.out, "w"), separators=(",", ":"))
    print("wrote", a.out, file=sys.stderr)


if __name__ == "__main__":
    main()
