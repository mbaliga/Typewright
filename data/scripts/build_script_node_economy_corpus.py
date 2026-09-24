#!/usr/bin/env python3
"""Build the node-economy reference corpus for Typewright's non-Latin scripts.

Sibling to build_node_economy_corpus.py (Latin): same counting rule, same source
(google/fonts), same per-glyph box-plot quartiles plus raw per-family counts. Two things
differ, both because the real catalog is different, not because this script invents a new
rule:

1.  Glyph set. Latin's GLYPHS is a hand-typed ASCII string. Here it is extracted, at run
    time, from this build's own already-committed, already-reviewed glyph inventories --
    scripts/src/commonMain/kotlin/dev/aarso/typewright/scripts/devanagari/
    DevanagariGlyphInventory.kt (66 glyphs: independent vowels, anusvara, the 33 standard
    consonants, the 10 dependent vowel signs/matras, visarga, the 10 digits) and .../kana/
    HiraganaGlyphs.kt + KatakanaGlyphs.kt (55 + 57 glyphs: the 46 base gojuon syllables plus
    9 small kana in each script, plus katakana's prolonged-sound mark and middle dot, which
    have no Hiragana equivalent template). See extract_codepoints() -- this is read from the
    real files, not retyped from memory, so it cannot silently drift from the inventories the
    rest of this build already uses. `kana` covers both kana scripts in one pack because
    google/fonts ships them together: no font in the corpus below supports Hiragana without
    also supporting Katakana (both live under the single `japanese` metadata subset -- see
    fetch_gf_metadata()), so splitting them into two packs would just duplicate the same
    family list under two names.

2.  Style-class taxonomy. Latin's ten classes (STYLES in build_node_economy_corpus.py) come
    from google/fonts' own volunteer tag project (tags/all/families.csv, this repo's
    data/families.csv snapshot), which tags nearly every Latin family with a specific
    sub-style ("/Sans/Geometric", "/Serif/Transitional", ...). That richness does not carry
    over: of the google/fonts families that genuinely cover Devanagari, only 34/62 (55%)
    carry any of Latin's specific sub-style tags at a score >= 50, and for kana it is 24/68
    (35%) -- checked directly against data/families.csv before writing this script (see
    docs/OPEN_QUESTIONS.md for the exact command and counts). Grouping by those tags would
    silently drop roughly half of each script's real candidate pool into no class at all.
    Google Fonts' own `category` field (Sans Serif / Serif / Display / Handwriting /
    Monospace), fetched from fonts.google.com/metadata/fonts -- the public endpoint the
    Google Fonts website itself uses, no key required -- is populated for every family with no
    exceptions, so that is what this script classifies by: one style class per category that
    clears MIN_FAMILIES, which produces a small handful per script (not Latin's ten) rather
    than an invented finer taxonomy google/fonts' own catalog does not support for these
    scripts. This is the honest reading of this task's own instruction, not a shortcut: the
    thinness is real and disclosed, not smoothed over.

Selection within a class still ranks by the SAME /Quality/Drawing tag score as Latin
(data/families.csv covers every family in the catalog, not just Latin ones -- checked: all 62
Devanagari-subset and all 68 japanese-subset families carry a /Quality/Drawing score), with
the same tie-break (reverse-alphabetical family name) and the same "one face per superfamily"
dedupe rule, and the same per-family download/count path (process(), reused verbatim). A class
whose real candidate pool is under MIN_FAMILIES=5 is not built into a box at all -- printed as
skipped and left for docs/OPEN_QUESTIONS.md, per this task's own instruction and CLAUDE.md law
5 ("measured, not invented... shown as a distribution"): a box under 5 samples is not a
distribution, it is noise with quartile labels on it.

Usage: python3 build_script_node_economy_corpus.py --script devanagari [--top 30]
           [--tags ../families.csv] [--out ../node-economy-devanagari.json]
       python3 build_script_node_economy_corpus.py --script kana [--top 30]
           [--tags ../families.csv] [--out ../node-economy-kana.json]
"""
import argparse
import collections
import concurrent.futures as cf
import csv
import io
import json
import os
import re
import subprocess
import sys
import urllib.request
from datetime import date

from fontTools.pens.recordingPen import RecordingPen
from fontTools.ttLib import TTFont

GOOGLE_FONTS_GIT = "https://github.com/google/fonts.git"
RAW_TEMPLATE = "https://raw.githubusercontent.com/google/fonts/{ref}/"
RAW = RAW_TEMPLATE.format(ref="main")  # set for real by main() once the source commit resolves
GF_METADATA_URL = "https://fonts.google.com/metadata/fonts"

ON_CURVE = 0x01  # glyf simple-glyph flag bit 0 (OpenType spec, "Simple Glyph Flags")

# Below this many real qualifying families, a style class is not built -- this task's own
# instruction ("fewer than 5 real qualifying families" is too thin for a meaningful box) and
# CLAUDE.md law 5.
MIN_FAMILIES = 5

# Google Fonts' own `category` values (fonts.google.com/metadata/fonts), in the order style
# classes are considered, and the slug each becomes in this pack's style keys.
CATEGORY_SLUGS = [
    ("Sans Serif", "sans"),
    ("Serif", "serif"),
    ("Display", "display"),
    ("Handwriting", "handwriting"),
    ("Monospace", "mono"),
]

REPO_ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SCRIPTS_MODULE = os.path.join(
    REPO_ROOT, "scripts", "src", "commonMain", "kotlin", "dev", "aarso", "typewright", "scripts"
)


def extract_codepoints(path, pattern):
    """Pulls every `codepoint` Unicode scalar value out of a Kotlin source file, in the order
    they appear in the file -- which, for the three files this script reads, is the same order
    the file's own `glyphs`/`HIRAGANA_TEMPLATES`/`KATAKANA_TEMPLATES` list assembles them in
    (each is a single top-to-bottom list literal or concatenation of list literals declared in
    that order; Kotlin has no reordering here). Reading the real, checked-in inventory this way
    -- rather than retyping the codepoints by hand -- is this task's own instruction: "read
    those real files first... rather than picking an arbitrary glyph subset yourself."
    """
    with open(path, encoding="utf-8") as f:
        text = f.read()
    cps = [int(m, 16) for m in re.findall(pattern, text)]
    if len(cps) != len(set(cps)):
        raise SystemExit(f"{path}: duplicate codepoint extracted, refusing to guess which is real")
    return cps


def devanagari_glyphs():
    # DevanagariGlyphInventory.kt calls GlyphSpec(name, unicodeName, codepoint) positionally --
    # no `codepoint =` label -- so the pattern matches the literal call shape instead.
    path = os.path.join(SCRIPTS_MODULE, "devanagari", "DevanagariGlyphInventory.kt")
    cps = extract_codepoints(path, r'GlyphSpec\(\s*"[^"]*",\s*"[^"]*",\s*0x([0-9A-Fa-f]+)\)')
    if len(cps) != 66:
        raise SystemExit(f"expected 66 Devanagari glyphs from {path}, got {len(cps)}")
    return "".join(chr(c) for c in cps)


def kana_glyphs():
    # HiraganaGlyphs.kt / KatakanaGlyphs.kt build KanaTemplate(..., codepoint = 0x...., ...)
    # with a named argument, so the label is part of the match here.
    hira_path = os.path.join(SCRIPTS_MODULE, "kana", "HiraganaGlyphs.kt")
    kata_path = os.path.join(SCRIPTS_MODULE, "kana", "KatakanaGlyphs.kt")
    pattern = r"codepoint\s*=\s*0x([0-9A-Fa-f]+)"
    hira = extract_codepoints(hira_path, pattern)
    kata = extract_codepoints(kata_path, pattern)
    if len(hira) != 55:
        raise SystemExit(f"expected 55 Hiragana glyphs from {hira_path}, got {len(hira)}")
    if len(kata) != 57:
        raise SystemExit(f"expected 57 Katakana glyphs from {kata_path}, got {len(kata)}")
    overlap = set(hira) & set(kata)
    if overlap:
        raise SystemExit(f"Hiragana/Katakana codepoints overlap unexpectedly: {overlap}")
    return "".join(chr(c) for c in hira + kata)


# Per-script config: the metadata `subsets` entry that means "this family really covers this
# script" (fetch_gf_metadata()), and the glyph-string builder above.
SCRIPTS = {
    "devanagari": {"subset": "devanagari", "glyphs": devanagari_glyphs, "default_out": "node-economy-devanagari.json"},
    "kana": {"subset": "japanese", "glyphs": kana_glyphs, "default_out": "node-economy-kana.json"},
}

GLYPHS = ""  # set for real by main() once --script is known; count_points()/process() read it


def fetch(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "typewright-corpus/0.1"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def resolve_source_commit(repo=GOOGLE_FONTS_GIT, ref="refs/heads/main"):
    """Identical to build_node_economy_corpus.py's own function -- see its docstring."""
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


def fetch_gf_metadata():
    """Fetches Google Fonts' own public family metadata: every family's real `subsets`
    coverage and Google's own `category`. This is the source this script uses to know which
    tag-project families (data/families.csv) genuinely support the target script, and to
    define this script's style classes -- see the module docstring for why `category` and not
    the tag project's own style tags. No API key needed; this is the same endpoint
    fonts.google.com's own UI calls.
    """
    raw = fetch(GF_METADATA_URL, timeout=60)
    data = json.loads(raw.decode("utf-8"))
    return data["familyMetadataList"]


def load_tags(path):
    fam_tag = collections.defaultdict(dict)
    with open(path, newline="") as f:
        for row in csv.reader(f):
            if len(row) < 4: continue
            fam, _, tag, score = row[0], row[1], row[2], row[3]
            try: fam_tag[fam][tag] = max(fam_tag[fam].get(tag, 0), int(score))
            except ValueError: pass
    return fam_tag


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
    """Identical to build_node_economy_corpus.py's own function -- see its docstring for the
    on-curve/implied-point rule this applies."""
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
            i, j = pts[k], pts[(k + 1) % n]
            if not (flags[i] & ON_CURVE) and not (flags[j] & ON_CURVE):
                implied += 1
        on += c_on + implied
        off += c_off
        start = end + 1
    return on, off, len(end_pts)


def count_points(font):
    """Identical to build_node_economy_corpus.py's own function (same composite-decomposing,
    same closing-point fix), except it reads the module-level GLYPHS this script sets from
    --script instead of the Latin ASCII literal."""
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
                continue
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
                    if args[0] == start_pt: continue
                    on += 1
                elif op == "curveTo":
                    off += 2
                    if args[-1] == start_pt: continue
                    on += 1
                elif op == "qCurveTo":
                    if args[-1] is None:
                        off += len(args) - 1; on += len(args) - 1
                    else:
                        off += len(args) - 1
                        if args[-1] != start_pt: on += 1
            if contours:
                out[ch] = [on, off, contours]
    return fmt, out


def process(family):
    """Identical to build_node_economy_corpus.py's own function, including its "drop families
    with no real letter in the requested set" guard (here: no Devanagari letter / no kana
    letter -- str.isalpha() is Unicode-aware and is True for Devanagari/Hiragana/Katakana
    letters exactly as it is for Latin ones, False for the matras/anusvara/visarga/digits in
    the Devanagari set, so this is the same real check, not a Latin-only one)."""
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
            if not any(ch.isalpha() for ch in counts):
                print("skip (no real letters in the target script)", family, file=sys.stderr)
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


def candidates_for_class(families_meta, fam_tag, subset, category):
    """Every family that (a) really covers `subset` per Google's own metadata and (b) is in
    Google's own `category`, ranked by /Quality/Drawing score (default 0 if untagged -- none
    were, for either script, as of this run; see module docstring), tie-broken by
    reverse-alphabetical family name (same convention as Latin's STYLES loop -- see
    build_node_economy_corpus.py's own comment on this tie-break), then deduped to one face
    per superfamily (first word of the family name, lowercased), keeping the highest-ranked.
    Returns family names only, best first.
    """
    cands = []
    for fm in families_meta:
        if subset not in fm.get("subsets", ()): continue
        if fm.get("category") != category: continue
        fam = fm["family"]
        score = fam_tag.get(fam, {}).get("/Quality/Drawing", 0)
        cands.append((score, fam))
    cands.sort(reverse=True)
    seen = set(); dedup = []
    for score, fam in cands:
        root = fam.split()[0].lower()
        if root in seen: continue
        seen.add(root); dedup.append(fam)
    return dedup


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--script", required=True, choices=sorted(SCRIPTS.keys()))
    ap.add_argument("--top", type=int, default=30)
    ap.add_argument("--tags", default="families.csv")
    ap.add_argument("--out", default=None)
    ap.add_argument("--commit", default=None, help="pin to this google/fonts commit SHA instead of resolving main")
    a = ap.parse_args()

    cfg = SCRIPTS[a.script]
    global GLYPHS
    GLYPHS = cfg["glyphs"]()
    out_path = a.out or cfg["default_out"]

    global RAW
    sha = a.commit or resolve_source_commit()
    if sha:
        RAW = RAW_TEMPLATE.format(ref=sha)
        source = (
            f"google/fonts@{sha} (raw.githubusercontent.com/google/fonts/{sha}/"
            f"{{ofl,apache,ufl}}/<dir>/METADATA.pb + Regular TTF; tags/all/families.csv from "
            f"the same commit for /Quality/Drawing ranking; fonts.google.com/metadata/fonts, "
            f"fetched live, for real subset coverage and category), fetched "
            f"{date.today().isoformat()} by data/scripts/build_script_node_economy_corpus.py "
            f"--script {a.script}."
        )
    else:
        source = (
            f"google/fonts@main (commit SHA NOT resolved -- git ls-remote failed; UNPINNED, "
            f"not reproducible), fetched {date.today().isoformat()} by "
            f"data/scripts/build_script_node_economy_corpus.py --script {a.script}."
        )
        print("WARNING: source commit not pinned; pack will say so", file=sys.stderr)

    fam_tag = load_tags(a.tags)
    print(f"fetching Google Fonts metadata (subsets/category) for --script {a.script}...", file=sys.stderr)
    families_meta = fetch_gf_metadata()

    pack = {"source": source, "glyphs": GLYPHS, "styles": {}}
    skipped_too_thin = []
    for category, slug in CATEGORY_SLUGS:
        key = f"{a.script}-{slug}"
        cands = candidates_for_class(families_meta, fam_tag, cfg["subset"], category)
        if len(cands) < MIN_FAMILIES:
            print(f"{key}: only {len(cands)} real candidate family(ies) (< {MIN_FAMILIES}) -- SKIPPED, not built", file=sys.stderr)
            if cands:
                skipped_too_thin.append((key, cands))
            continue
        chosen = cands[: a.top + 8]  # a few spares for failed downloads, same as Latin
        with cf.ThreadPoolExecutor(12) as ex:
            results = [r for r in ex.map(process, chosen) if r]
        results = results[: a.top]
        if len(results) < MIN_FAMILIES:
            print(f"{key}: only {len(results)} family(ies) actually downloaded/counted (< {MIN_FAMILIES}) -- SKIPPED, not built", file=sys.stderr)
            if results:
                skipped_too_thin.append((key, [r["family"] for r in results]))
            continue
        dist = {}
        for ch in GLYPHS:
            on = [r["counts"][ch][0] for r in results if ch in r["counts"]]
            off = [r["counts"][ch][1] for r in results if ch in r["counts"]]
            if on: dist[ch] = {"on": quartiles(on), "off": quartiles(off)}
        pack["styles"][key] = {
            "tags": [category],
            "families": [
                {
                    "family": r["family"], "file": r["file"], "format": r["format"],
                    "drawing": fam_tag.get(r["family"], {}).get("/Quality/Drawing"),
                    "counts": r["counts"],
                }
                for r in results
            ],
            "dist": dist,
        }
        print(f"{key}: {len(results)} fonts", file=sys.stderr)

    if skipped_too_thin:
        print("SKIPPED (too thin, not written into the pack):", file=sys.stderr)
        for key, fams in skipped_too_thin:
            print(f"  {key}: {fams}", file=sys.stderr)

    json.dump(pack, open(out_path, "w"), separators=(",", ":"), ensure_ascii=False)
    print("wrote", out_path, file=sys.stderr)


if __name__ == "__main__":
    main()
